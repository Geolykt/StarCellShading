package de.geolykt.scs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.danilopianini.util.FlexibleQuadTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;

import de.geolykt.scs.SCSConfig.CellStyle;
import de.geolykt.scs.rendercache.DeferredGlobalRenderObject;
import de.geolykt.starloader.api.CoordinateGrid;
import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.empire.Alliance;
import de.geolykt.starloader.api.empire.Star;
import de.geolykt.starloader.api.gui.Drawing;
import de.geolykt.starloader.api.gui.MapMode;
import de.geolykt.starloader.api.registry.RegistryKeys;
import de.geolykt.starloader.api.resource.DataFolderProvider;
import de.geolykt.starloader.impl.registry.SLMapMode;

import snoddasmannen.galimulator.GalFX;

public class SCSCoreLogic {
    private static final VertexAttribute ATTRIBUTE_CENTER_POSITION = new VertexAttribute(Usage.Generic, 2, GL20.GL_FLOAT, false, "a_centerpos");
    private static final VertexAttribute ATTRIBUTE_VERTEX_POSITION = new VertexAttribute(Usage.Position, 2, GL20.GL_FLOAT, false, ShaderProgram.POSITION_ATTRIBUTE);
    private static ShaderProgram blitShader;
    private static ShaderProgram edgeShader;
    private static ShaderProgram explodeShader;
    private static final float GRANULARITY_FACTOR = 0.035F;
    @Nullable
    private static CellStyle lastStyle = null;

    private static final Logger LOGGER = LoggerFactory.getLogger(SCSCoreLogic.class);
    private static final int MAX_INDICES = 0x1000;
    private static final int MAX_INDICES_MASK = 0x0FFF;
    private static final float REGION_SIZE = GRANULARITY_FACTOR * 16;

    public static void disposeBlitShader() {
        ShaderProgram shader = SCSCoreLogic.blitShader;
        if (shader == null) {
            SCSCoreLogic.LOGGER.warn("Blit shader not yet initialized, yet it should be disposed.");
            return;
        }
        SCSCoreLogic.blitShader = null;
        shader.dispose();
    }

    public static void disposeEdgeShader() {
        ShaderProgram shader = SCSCoreLogic.edgeShader;
        if (shader == null) {
            return;
        }
        SCSCoreLogic.edgeShader = null;
        shader.dispose();
    }

    public static void disposeExplodeShader() {
        ShaderProgram shader = SCSCoreLogic.explodeShader;
        if (shader == null) {
            SCSCoreLogic.LOGGER.warn("Explode shader not yet initialized, yet it should be disposed.");
            return;
        }
        SCSCoreLogic.explodeShader = null;
        shader.dispose();
    }

    public static void drawRegionsAsync() {
        FlexibleQuadTree<Star> quadTree = new FlexibleQuadTree<>(64);
        for (Star s : Galimulator.getUniverse().getStarsView()) {
            quadTree.insert(s, s.getX(), s.getY());
        }

        CellStyle currentStyle = CellStyle.getCurrentStyle();
        Drawing.getRendercacheUtils().getDrawingState().pushObject(new DeferredGlobalRenderObject(() -> {
            if (currentStyle != SCSCoreLogic.lastStyle) {
                if (SCSCoreLogic.blitShader != null) {
                    SCSCoreLogic.disposeBlitShader();
                }
                if (SCSCoreLogic.explodeShader != null) {
                    SCSCoreLogic.disposeExplodeShader();
                }
                if (SCSCoreLogic.edgeShader != null) {
                    SCSCoreLogic.disposeEdgeShader();
                }
                SCSCoreLogic.initializeBlitShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                SCSCoreLogic.initializeExplodeShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                if (currentStyle == CellStyle.FLAT) {
                    SCSCoreLogic.initializeEdgeShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                }
                SCSCoreLogic.lastStyle = currentStyle;
            }

            if (currentStyle == CellStyle.BLOOM) {
                SCSCoreLogic.drawRegionsDirectBloom(quadTree);
            } else if (currentStyle == CellStyle.FLAT) {
                SCSCoreLogic.drawRegionsDirectFlat(quadTree);
            }
        }));
    }

    public static void drawRegionsDirectBloom(FlexibleQuadTree<Star> quadTree) {
        SpriteBatch batch = Drawing.getDrawingBatch();

        ShaderProgram explodeShader = SCSCoreLogic.explodeShader;
        if (explodeShader == null) {
            SCSCoreLogic.LOGGER.warn("Explode shader program wasn't yet initialized. Doing it now");
            explodeShader = SCSCoreLogic.initializeExplodeShader("bloom");
        }

        ShaderProgram blitShader = SCSCoreLogic.blitShader;
        if (blitShader == null) {
            SCSCoreLogic.LOGGER.warn("Blit shader program wasn't yet initialized. Doing it now");
            blitShader = SCSCoreLogic.initializeBlitShader("bloom");
        }

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        Vector3 minCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, 0, screenH);
        Vector3 maxCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, screenW, 0);

        minCoords.sub(SCSCoreLogic.REGION_SIZE * 2);
        maxCoords.add(SCSCoreLogic.REGION_SIZE * 2);

        List<Star> stars = quadTree.query(minCoords.x, minCoords.y, maxCoords.x, maxCoords.y);

        boolean drawing;
        if (drawing = batch.isDrawing()) {
            drawing = false;
            batch.flush();
        }

        IntMap<List<@NotNull Star>> empires = new IntMap<>();
        int maxlen = 0;
        for (Star star : stars) {
            assert star != null;
            int empireUID = SCSCoreLogic.getStarColor(star).toIntBits();
            List<Star> empire = empires.get(empireUID);
            if (empire == null) {
                empire = new ArrayList<>();
                empires.put(empireUID, empire);
            }
            empire.add(star);
            maxlen = Math.max(maxlen, empire.size());
        }

        maxlen = Math.min(SCSCoreLogic.MAX_INDICES, maxlen);
        float[] vertices = new float[maxlen * 16];
        Mesh mesh = new Mesh(false, maxlen * 4, maxlen * 5, SCSCoreLogic.ATTRIBUTE_VERTEX_POSITION, SCSCoreLogic.ATTRIBUTE_CENTER_POSITION);

        short[] indices = new short[maxlen * 5];
        // 0, 1, 2, 3, <RESET>, 4, 5, 6, 7, <RESET>, 8, 9, [...]
        for (int i = maxlen; i-- != 0;) {
            int baseAddrW = i * 5;
            int baseAddrR = i * 4;
            indices[baseAddrW] = (short) (baseAddrR);
            indices[baseAddrW + 1] = (short) (baseAddrR + 1);
            indices[baseAddrW + 2] = (short) (baseAddrR + 2);
            indices[baseAddrW + 3] = (short) (baseAddrR + 3);
            indices[baseAddrW + 4] = (short) (0xFFFF);
        }
        mesh.setIndices(indices);

        org.lwjgl.opengl.GL31.glPrimitiveRestartIndex(0xFFFF);
        Gdx.gl20.glEnable(org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART);

        FrameBuffer secondaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
        FrameBuffer tertiaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
        SpriteBatch secondaryBlitBatch = new SpriteBatch(1, blitShader);
        SpriteBatch primaryBlitBatch = new SpriteBatch(1);
        secondaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        secondaryBlitBatch.setBlendFunction(GL20.GL_SRC_ALPHA_SATURATE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        primaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        primaryBlitBatch.setColor(1F, 1F, 1F, SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue());

        try {
            float explodeFactor = SCSConfig.EXPLODE_FACTOR.getValue();
            float explodeDecay = SCSConfig.EXPLODE_DECAY.getValue();
            float explodeFloor = SCSConfig.EXPLODE_FLOOR.getValue();

            float boxSize;
            if (explodeFactor == 0F) {
                boxSize = 0F;
            } else {
                boxSize = (float) (Math.sqrt(2) / explodeDecay);
            }

            Matrix4 projectedTransformationMatrix = GalFX.get_m().combined.cpy().mul(batch.getTransformMatrix());

            for (List<@NotNull Star> empire : empires.values()) {
                Color empireColor = SCSCoreLogic.getStarColor(empire.get(0));
                if (empireColor == Color.CLEAR) {
                    continue; // Skip rendering (e.g. for the neutral territories without a faction, alliance, etc.)
                }

                secondaryFB.begin();
                Gdx.gl20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);
                Gdx.gl20.glEnable(GL20.GL_BLEND);
                Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
                Gdx.gl20.glBlendFunc(GL20.GL_SRC_ALPHA_SATURATE, GL20.GL_ONE_MINUS_SRC_ALPHA);

                explodeShader.bind();
                explodeShader.setUniformMatrix("u_projTrans", projectedTransformationMatrix);
                explodeShader.setUniformf("u_explodeFactor", explodeFactor);
                explodeShader.setUniformf("u_explodeDecay", explodeDecay);
                explodeShader.setUniformf("u_explodeFloor", explodeFloor);

                int i;
                int empireSize = i = empire.size();
                while (i-- != 0) {
                    Star s = empire.get(i);
                    int baseAddress = (i & SCSCoreLogic.MAX_INDICES_MASK) * 16;
                    float x = s.getX();
                    float y = s.getY();

                    vertices[baseAddress] = x - boxSize;
                    vertices[baseAddress + 1] = y - boxSize;
                    vertices[baseAddress + 2] = x;
                    vertices[baseAddress + 3] = y;

                    vertices[baseAddress + 4] = x + boxSize;
                    vertices[baseAddress + 5] = y - boxSize;
                    vertices[baseAddress + 6] = x;
                    vertices[baseAddress + 7] = y;

                    vertices[baseAddress + 8] = x - boxSize;
                    vertices[baseAddress + 9] = y + boxSize;
                    vertices[baseAddress + 10] = x;
                    vertices[baseAddress + 11] = y;

                    vertices[baseAddress + 12] = x + boxSize;
                    vertices[baseAddress + 13] = y + boxSize;
                    vertices[baseAddress + 14] = x;
                    vertices[baseAddress + 15] = y;

                    if ((i & SCSCoreLogic.MAX_INDICES_MASK) == 0) {
                        mesh.setVertices(vertices, 0, Math.min(empireSize - i, SCSCoreLogic.MAX_INDICES) * 16);
                        mesh.render(explodeShader, GL20.GL_TRIANGLE_STRIP, 0, Math.min(empireSize - i, SCSCoreLogic.MAX_INDICES) * 5, true);
                    }
                }

                secondaryFB.end();
                tertiaryFB.begin();
                secondaryBlitBatch.setPackedColor(empireColor.toFloatBits());
                secondaryBlitBatch.begin();
                secondaryBlitBatch.draw(secondaryFB.getColorBufferTexture(), 0, 0, 1, 1);
                secondaryBlitBatch.end();
                tertiaryFB.end();
            }

            primaryBlitBatch.begin();
            primaryBlitBatch.draw(tertiaryFB.getColorBufferTexture(), 0, 0, 1, 1);
            primaryBlitBatch.end();

            if (!explodeShader.getLog().isEmpty()) {
                SCSCoreLogic.LOGGER.info("Shader logs (pre dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    SCSCoreLogic.LOGGER.info(ln);
                }
            }

            mesh.dispose();
            Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
            Gdx.gl20.glDisable(org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART);
            batch.getShader().bind();

            if (!explodeShader.getLog().isEmpty()) {
                SCSCoreLogic.LOGGER.info("Shader logs (post dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    SCSCoreLogic.LOGGER.info(ln);
                }
            }
        } finally {
            secondaryFB.dispose();
            tertiaryFB.dispose();
            primaryBlitBatch.dispose();
            secondaryBlitBatch.dispose();
        }

        batch.getShader().bind();
        if (drawing) {
            batch.begin();
        }
    }

    public static void drawRegionsDirectFlat(FlexibleQuadTree<Star> quadTree) {
        SpriteBatch batch = Drawing.getDrawingBatch();

        ShaderProgram explodeShader = SCSCoreLogic.explodeShader;
        if (explodeShader == null) {
            SCSCoreLogic.LOGGER.warn("Explode shader program wasn't yet initialized. Doing it now");
            explodeShader = SCSCoreLogic.initializeExplodeShader("flat");
        }

        ShaderProgram blitShader = SCSCoreLogic.blitShader;
        if (blitShader == null) {
            SCSCoreLogic.LOGGER.warn("Blit shader program wasn't yet initialized. Doing it now");
            blitShader = SCSCoreLogic.initializeBlitShader("flat");
        }

        ShaderProgram edgeShader = SCSCoreLogic.edgeShader;
        if (edgeShader == null) {
            SCSCoreLogic.LOGGER.warn("Edge shader program wasn't yet initialized. Doing it now");
            edgeShader = SCSCoreLogic.initializeBlitShader("flat");
        }

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        Vector3 minCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, 0, screenH);
        Vector3 maxCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, screenW, 0);

        minCoords.sub(SCSCoreLogic.REGION_SIZE * 2);
        maxCoords.add(SCSCoreLogic.REGION_SIZE * 2);

        List<Star> stars = quadTree.query(minCoords.x, minCoords.y, maxCoords.x, maxCoords.y);

        boolean drawing;
        if (drawing = batch.isDrawing()) {
            drawing = false;
            batch.flush();
        }

        IntMap<List<@NotNull Star>> empires = new IntMap<>();
        int maxlen = 0;
        for (Star star : stars) {
            assert star != null;
            int empireUID = SCSCoreLogic.getStarColor(star).toIntBits();
            List<Star> empire = empires.get(empireUID);
            if (empire == null) {
                empire = new ArrayList<>();
                empires.put(empireUID, empire);
            }
            empire.add(star);
            maxlen = Math.max(maxlen, empire.size());
        }

        maxlen = Math.min(SCSCoreLogic.MAX_INDICES, maxlen);
        float[] vertices = new float[maxlen * 16];
        Mesh mesh = new Mesh(false, maxlen * 4, maxlen * 5, SCSCoreLogic.ATTRIBUTE_VERTEX_POSITION, SCSCoreLogic.ATTRIBUTE_CENTER_POSITION);

        short[] indices = new short[maxlen * 5];
        // 0, 1, 2, 3, <RESET>, 4, 5, 6, 7, <RESET>, 8, 9, [...]
        for (int i = maxlen; i-- != 0;) {
            int baseAddrW = i * 5;
            int baseAddrR = i * 4;
            indices[baseAddrW] = (short) (baseAddrR);
            indices[baseAddrW + 1] = (short) (baseAddrR + 1);
            indices[baseAddrW + 2] = (short) (baseAddrR + 2);
            indices[baseAddrW + 3] = (short) (baseAddrR + 3);
            indices[baseAddrW + 4] = (short) (0xFFFF);
        }
        mesh.setIndices(indices);

        org.lwjgl.opengl.GL31.glPrimitiveRestartIndex(0xFFFF);
        Gdx.gl20.glEnable(org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART);

        FrameBuffer secondaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
        FrameBuffer tertiaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
        SpriteBatch secondaryBlitBatch = new SpriteBatch(1, blitShader);
        SpriteBatch primaryBlitBatch = new SpriteBatch(1, edgeShader);
        secondaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        secondaryBlitBatch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        primaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        primaryBlitBatch.setColor(1F, 1F, 1F, SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue());

        try {
            float explodeFactor = SCSConfig.EXPLODE_FACTOR.getValue();
            float explodeDecay = SCSConfig.EXPLODE_DECAY.getValue();
            float explodeFloor = SCSConfig.EXPLODE_FLOOR.getValue();

            Gdx.gl20.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl20.glDepthMask(true);
            Gdx.gl20.glClearDepthf(1F);
            tertiaryFB.bind();
            Gdx.gl20.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            Gdx.gl20.glDisable(GL20.GL_DEPTH_TEST);

            float boxSize;
            if (explodeFactor == 0F) {
                boxSize = 0F;
            } else {
                boxSize = (float) (Math.sqrt(2) / explodeDecay);
            }

            Matrix4 projectedTransformationMatrix = GalFX.get_m().combined.cpy().mul(batch.getTransformMatrix());

            for (List<@NotNull Star> empire : empires.values()) {
                Color empireColor = SCSCoreLogic.getStarColor(empire.get(0));
                if (empireColor == Color.CLEAR) {
                    continue; // Skip rendering (e.g. for the neutral territories without a faction, alliance, etc.)
                }

                secondaryFB.begin();
                Gdx.gl20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);

                Gdx.gl20.glEnable(GL20.GL_BLEND);
                Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
                Gdx.gl20.glBlendFunc(GL20.GL_SRC_ALPHA_SATURATE, GL20.GL_ONE_MINUS_SRC_ALPHA);

                explodeShader.bind();
                explodeShader.setUniformMatrix("u_projTrans", projectedTransformationMatrix);
                explodeShader.setUniformf("u_explodeFactor", explodeFactor);
                explodeShader.setUniformf("u_explodeDecay", explodeDecay);
                explodeShader.setUniformf("u_explodeFloor", explodeFloor);

                int i;
                int empireSize = i = empire.size();
                while (i-- != 0) {
                    Star s = empire.get(i);
                    int baseAddress = (i & SCSCoreLogic.MAX_INDICES_MASK) * 16;
                    float x = s.getX();
                    float y = s.getY();

                    vertices[baseAddress] = x - boxSize;
                    vertices[baseAddress + 1] = y - boxSize;
                    vertices[baseAddress + 2] = x;
                    vertices[baseAddress + 3] = y;

                    vertices[baseAddress + 4] = x + boxSize;
                    vertices[baseAddress + 5] = y - boxSize;
                    vertices[baseAddress + 6] = x;
                    vertices[baseAddress + 7] = y;

                    vertices[baseAddress + 8] = x - boxSize;
                    vertices[baseAddress + 9] = y + boxSize;
                    vertices[baseAddress + 10] = x;
                    vertices[baseAddress + 11] = y;

                    vertices[baseAddress + 12] = x + boxSize;
                    vertices[baseAddress + 13] = y + boxSize;
                    vertices[baseAddress + 14] = x;
                    vertices[baseAddress + 15] = y;

                    if ((i & SCSCoreLogic.MAX_INDICES_MASK) == 0) {
                        mesh.setVertices(vertices, 0, Math.min(empireSize - i, SCSCoreLogic.MAX_INDICES) * 16);
                        mesh.render(explodeShader, GL20.GL_TRIANGLE_STRIP, 0, Math.min(empireSize - i, SCSCoreLogic.MAX_INDICES) * 5, true);
                    }
                }

                secondaryFB.end();
                tertiaryFB.begin();
                Gdx.gl20.glEnable(GL20.GL_DEPTH_TEST);
                Gdx.gl20.glDepthFunc(GL20.GL_LESS);
                Gdx.gl20.glDepthRangef(0.1F, 1.0F);
                secondaryBlitBatch.setPackedColor(empireColor.toFloatBits());
                secondaryBlitBatch.begin();
                Gdx.gl20.glDepthMask(true); // WARNING: This method MUST be called after #begin.
                secondaryBlitBatch.draw(secondaryFB.getColorBufferTexture(), 0, 0, 1, 1);
                secondaryBlitBatch.end();
                Gdx.gl20.glDisable(GL20.GL_DEPTH_TEST);
                tertiaryFB.end();
            }

            edgeShader.bind();
            edgeShader.setUniform2fv("u_pixelSize", new float[] {SCSConfig.EMPIRE_BORDER_SIZE.getValue() / Gdx.graphics.getBackBufferWidth(), SCSConfig.EMPIRE_BORDER_SIZE.getValue() / Gdx.graphics.getBackBufferHeight()}, 0, 2);
            primaryBlitBatch.begin();
            primaryBlitBatch.draw(tertiaryFB.getColorBufferTexture(), 0, 0, 1, 1);
            primaryBlitBatch.end();

            if (!explodeShader.getLog().isEmpty()) {
                SCSCoreLogic.LOGGER.info("Shader logs (pre dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    SCSCoreLogic.LOGGER.info(ln);
                }
            }

            mesh.dispose();
            Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
            Gdx.gl20.glDisable(org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART);
            batch.getShader().bind();

            if (!explodeShader.getLog().isEmpty()) {
                SCSCoreLogic.LOGGER.info("Shader logs (post dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    SCSCoreLogic.LOGGER.info(ln);
                }
            }
        } finally {
            secondaryFB.dispose();
            tertiaryFB.dispose();
            primaryBlitBatch.dispose();
            secondaryBlitBatch.dispose();
            Gdx.gl20.glDepthRangef(0.0F, 1.0F);
            Gdx.gl20.glDisable(GL20.GL_DEPTH_TEST);
        }

        batch.getShader().bind();
        if (drawing) {
            batch.begin();
        }
    }

    @SuppressWarnings("null")
    @NotNull
    public static Color getStarColor(@NotNull Star star) {
        MapMode mapMode = Galimulator.getActiveMapmode();
        if (mapMode instanceof SLMapMode) {
            Function<@NotNull Star, Color> fun = ((SLMapMode) mapMode).getStarOverlayRegionColorFunction();
            if (fun != null) {
                return fun.apply(star);
            }
        } else if (mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_DEFAULT_MAPMODE)
                || mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_HEAT_MAPMODE)
                || mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_WEALTH_MAPMODE)) {
            return star.getEmpire().getGDXColor();
        } else if (mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_ALLIANCES_MAPMODE)) {
            Alliance a = star.getEmpire().getAlliance();
            if (a == null) {
                return Color.CLEAR;
            }
            return a.getGDXColor();
        } else if (mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_CULTURE_MAPMODE)) {
            snoddasmannen.galimulator.Culture culture = ((snoddasmannen.galimulator.Star) star).M();
            if (culture == null) {
                return Color.CLEAR;
            }
            return culture.getColor().getGDXColor();
        }

        return Color.CLEAR;
    }

    @NotNull
    public static ShaderProgram initializeBlitShader(@NotNull String category) {
        ShaderProgram shader = SCSCoreLogic.blitShader;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Blit shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources(category + "-blit.vert");
        String frag = SCSCoreLogic.readStringFromResources(category + "-blit.frag");

        SCSCoreLogic.blitShader = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.blitShader = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                SCSCoreLogic.LOGGER.warn("Unable to dispose blit shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    public static ShaderProgram initializeEdgeShader(@NotNull String category) {
        if (!CellStyle.FLAT.toString().equalsIgnoreCase(category)) {
            throw new UnsupportedOperationException();
        }

        ShaderProgram shader = SCSCoreLogic.edgeShader;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Edge shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources(category + "-edge.vert");
        String frag = SCSCoreLogic.readStringFromResources(category + "-edge.frag");

        SCSCoreLogic.edgeShader = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.edgeShader = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                SCSCoreLogic.LOGGER.warn("Unable to dispose edge shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    public static ShaderProgram initializeExplodeShader(@NotNull String category) {
        ShaderProgram shader = SCSCoreLogic.explodeShader;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Explode shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources(category + "-explode.vert");
        String frag = SCSCoreLogic.readStringFromResources(category + "-explode.frag");

        SCSCoreLogic.explodeShader = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.explodeShader = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                SCSCoreLogic.LOGGER.warn("Unable to dispose explode shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    private static String readStringFromResources(@NotNull String filepath) {
        try {
            Path resourceLocation = DataFolderProvider.getProvider().provideAsPath().resolve("mods/star-cell-shading").resolve(filepath);
            return new String(Files.readAllBytes(resourceLocation), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LoggerFactory.getLogger(SCSConfig.class).info("Using internally bundled resource '{}', it was not found in the data directory", filepath);
            try (InputStream is = SCSCoreLogic.class.getClassLoader().getResourceAsStream(filepath)) {
                if (is == null) {
                    throw new IOException("Resource '" + filepath + "' is not located within the mod's classpath.");
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int read = is.read(buffer); read != -1; read = is.read(buffer)) {
                    baos.write(buffer, 0, read);
                }

                return new String(baos.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                e2.addSuppressed(e);
                throw new UncheckedIOException("Unable to read string from jar", e2);
            }
        }
    }
}
