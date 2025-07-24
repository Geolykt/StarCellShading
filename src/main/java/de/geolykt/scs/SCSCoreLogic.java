package de.geolykt.scs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.danilopianini.util.FlexibleQuadTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ShortArray;

import de.geolykt.scs.SCSConfig.CellStyle;
import de.geolykt.starloader.api.CoordinateGrid;
import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.empire.Alliance;
import de.geolykt.starloader.api.empire.Star;
import de.geolykt.starloader.api.gui.AsyncRenderer;
import de.geolykt.starloader.api.gui.Drawing;
import de.geolykt.starloader.api.gui.MapMode;
import de.geolykt.starloader.api.registry.RegistryKeys;
import de.geolykt.starloader.api.resource.DataFolderProvider;
import de.geolykt.starloader.impl.registry.SLMapMode;

import snoddasmannen.galimulator.GalFX;
import snoddasmannen.galimulator.Settings.EnumSettings;

import be.humphreys.simplevoronoi.GraphEdge;
import be.humphreys.simplevoronoi.Voronoi;

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
    private static final float REGION_SIZE = SCSCoreLogic.GRANULARITY_FACTOR * 16;

    @Nullable
    private static FrameBuffer fboAux0;
    @Nullable
    private static FrameBuffer fboAux1;

    public static void discardFBOs() {
        FrameBuffer fbo = SCSCoreLogic.fboAux0;
        if (fbo != null) {
            fbo.dispose();
            SCSCoreLogic.fboAux0 = null;
        }

        fbo = SCSCoreLogic.fboAux1;
        if (fbo != null) {
            fbo.dispose();
            SCSCoreLogic.fboAux1 = null;
        }
    }

    public static void disposeBlitShader() {
        ShaderProgram shader = SCSCoreLogic.blitShader;
        if (shader == null) {
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
            return;
        }
        SCSCoreLogic.explodeShader = null;
        shader.dispose();
    }

    public static void drawRegionsAsync() {
        FlexibleQuadTree<@NotNull Star> quadTree = new FlexibleQuadTree<>(64);
        for (Star s : Galimulator.getUniverse().getStarsView()) {
            quadTree.insert(s, s.getX(), s.getY());
        }

        CellStyle currentStyle = CellStyle.getCurrentStyle();

        float w = Galimulator.getMap().getWidth();
        float h = Galimulator.getMap().getWidth();

        AsyncRenderer.postRunnableRenderObject(() -> {
            if (currentStyle != SCSCoreLogic.lastStyle) {
                SCSCoreLogic.discardFBOs();
                SCSCoreLogic.disposeBlitShader();
                SCSCoreLogic.disposeExplodeShader();
                SCSCoreLogic.disposeEdgeShader();

                if (currentStyle.hasShaders()) {
                    SCSCoreLogic.initializeBlitShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                    SCSCoreLogic.initializeExplodeShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                    if (currentStyle == CellStyle.FLAT) {
                        SCSCoreLogic.initializeEdgeShader(currentStyle.toString().toLowerCase(Locale.ROOT) + "");
                    }
                }

                SCSCoreLogic.lastStyle = currentStyle;
            }

            if (currentStyle == CellStyle.BLOOM) {
                SCSCoreLogic.drawRegionsDirectBloom(quadTree);
            } else if (currentStyle == CellStyle.FLAT) {
                SCSCoreLogic.drawRegionsDirectFlat(quadTree);
            } else if (currentStyle == CellStyle.VORONOI_BEZIER) {
                SCSCoreLogic.drawRegionsVorBez(quadTree);
            } else {
                Galimulator.panic("Unimplemented cell shading style: " + currentStyle + "\n[RED]This is a bug in star-cell-shading. Consider reporting it.[]", true);
            }
        }, new Rectangle(w / -2, h / -2, w, h), Drawing.getBoardCamera());
    }

    public static void drawRegionsDirectBloom(@NotNull FlexibleQuadTree<Star> quadTree) {
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
            batch.getShader().bind();
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

        FrameBuffer secondaryFB = SCSCoreLogic.fboAux0;
        FrameBuffer tertiaryFB = SCSCoreLogic.fboAux1;

        if (secondaryFB != null
                && (secondaryFB.getWidth() != Gdx.graphics.getBackBufferWidth() || secondaryFB.getHeight() != Gdx.graphics.getBackBufferHeight())) {
            secondaryFB.dispose();
            secondaryFB = null;
        }

        if (tertiaryFB != null
                && (tertiaryFB.getWidth() != Gdx.graphics.getBackBufferWidth() || tertiaryFB.getHeight() != Gdx.graphics.getBackBufferHeight())) {
            tertiaryFB.dispose();
            tertiaryFB = null;
        }

        if (secondaryFB == null) {
            secondaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
            SCSCoreLogic.fboAux0 = secondaryFB;
        }

        if (tertiaryFB == null) {
            tertiaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
            SCSCoreLogic.fboAux1 = tertiaryFB;
        }

        SpriteBatch secondaryBlitBatch = new SpriteBatch(1, blitShader);
        SpriteBatch primaryBlitBatch = new SpriteBatch(1);
        secondaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        secondaryBlitBatch.setBlendFunction(GL20.GL_SRC_ALPHA_SATURATE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        primaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        primaryBlitBatch.setColor(1F, 1F, 1F, SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue());

        tertiaryFB.bind();
        Gdx.gl20.glClearColor(0F, 0F, 0F, 0F);
        Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);

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
            mesh.dispose();
            primaryBlitBatch.dispose();
            secondaryBlitBatch.dispose();
        }

        batch.getShader().bind();
        if (drawing) {
            batch.begin();
        }
    }

    public static void drawRegionsDirectFlat(@NotNull FlexibleQuadTree<Star> quadTree) {
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
            batch.getShader().bind();
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

        FrameBuffer secondaryFB = SCSCoreLogic.fboAux0;
        FrameBuffer tertiaryFB = SCSCoreLogic.fboAux1;

        if (secondaryFB != null
                && (secondaryFB.getWidth() != Gdx.graphics.getBackBufferWidth() || secondaryFB.getHeight() != Gdx.graphics.getBackBufferHeight())) {
            secondaryFB.dispose();
            secondaryFB = null;
        }

        if (tertiaryFB != null
                && (tertiaryFB.getWidth() != Gdx.graphics.getBackBufferWidth() || tertiaryFB.getHeight() != Gdx.graphics.getBackBufferHeight())) {
            tertiaryFB.dispose();
            tertiaryFB = null;
        }

        if (secondaryFB == null) {
            GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            builder.addColorTextureAttachment(GL30.GL_RED, GL30.GL_RED, GL20.GL_FLOAT);
            secondaryFB = builder.build();
            SCSCoreLogic.fboAux0 = secondaryFB;
        }

        if (tertiaryFB == null) {
            tertiaryFB = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
            SCSCoreLogic.fboAux1 = tertiaryFB;
        }

        SpriteBatch secondaryBlitBatch = new SpriteBatch(1, blitShader);
        SpriteBatch primaryBlitBatch = new SpriteBatch(1, edgeShader);
        secondaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        secondaryBlitBatch.disableBlending();
        primaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));
        primaryBlitBatch.setColor(1F, 1F, 1F, SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue());

        secondaryFB.bind();
        Gdx.gl20.glClearColor(0F, 0F, 0F, 0F);
        Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);

        try {
            float explodeFactor = SCSConfig.EXPLODE_FACTOR.getValue();
            float explodeDecay = SCSConfig.EXPLODE_DECAY.getValue();
            float explodeFloor = SCSConfig.EXPLODE_FLOOR.getValue();

            Gdx.gl20.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl20.glDepthMask(true);
            Gdx.gl20.glClearDepthf(1F);
            tertiaryFB.bind();
            Gdx.gl20.glClear(GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_COLOR_BUFFER_BIT);
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
                Gdx.gl20.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);

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
                Gdx.gl20.glDepthRangef(0.001F, 1.0F);
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
            mesh.dispose();
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

    public static void drawRegionsVorBez(@NotNull FlexibleQuadTree<@NotNull Star> quadTree) {
        SpriteBatch batch = Drawing.getDrawingBatch();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        Vector3 minCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, 0, screenH);
        Vector3 maxCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, screenW, 0);

        minCoords.sub(SCSCoreLogic.REGION_SIZE * 2);
        maxCoords.add(SCSCoreLogic.REGION_SIZE * 2);

        List<@NotNull Star> stars = quadTree.query(minCoords.x, minCoords.y, maxCoords.x, maxCoords.y);

        if (stars.size() == 0) {
            return; // Nothing to do
        }

        IntMap<List<@NotNull Star>> empires = new IntMap<>();
        int[] starToEmpireUID = new int[stars.size()];
        double[] starPositionsX = new double[stars.size()];
        double[] starPositionsY = new double[stars.size()];
        int maxlen = 0;

        {
            int starPositionIndex = 0;
            for (Star star : stars) {
                int empireUID = SCSCoreLogic.getStarColor(star).toIntBits();
                starToEmpireUID[starPositionIndex] = empireUID;
                starPositionsX[starPositionIndex] = star.getX();
                starPositionsY[starPositionIndex++] = star.getY();
                List<Star> empire = empires.get(empireUID);
                if (empire == null) {
                    empire = new ArrayList<>();
                    empires.put(empireUID, empire);
                }
                empire.add(star);
                maxlen = Math.max(maxlen, empire.size());
            }
        }

        Voronoi voronoiGen = new Voronoi(1e-7);
        List<GraphEdge> edges = voronoiGen.generateVoronoi(starPositionsX, starPositionsY, minCoords.x, maxCoords.x, minCoords.y, maxCoords.y);

        int[] edgeCount = new int[stars.size()];
        boolean[] frontierStar = new boolean[stars.size()];
        Set<Long> frontierVertices = new HashSet<>();

        for (GraphEdge edge : edges) {
            if (starToEmpireUID[edge.site1] != starToEmpireUID[edge.site2]) {
                frontierStar[edge.site1] = true;
                frontierStar[edge.site2] = true;
                frontierVertices.add(FloatHashing.positionalRawHash((float) edge.x1, (float) edge.y1));
                frontierVertices.add(FloatHashing.positionalRawHash((float) edge.x2, (float) edge.y2));
            }
            edgeCount[edge.site1]++;
            edgeCount[edge.site2]++;
        }

        float[][] polyPoints = new float[stars.size()][];
        for (int i = stars.size() - 1; i >= 0; i--) {
            polyPoints[i] = new float[edgeCount[i] * 4];
            edgeCount[i] = 0;
        }

        for (GraphEdge edge : edges) {
            int site1Idx = edgeCount[edge.site1]++;
            int site2Idx = edgeCount[edge.site2]++;
            float[] site1Positions = polyPoints[edge.site1];
            float[] site2Positions = polyPoints[edge.site2];
            site2Positions[site2Idx * 4 + 0] = site1Positions[site1Idx * 4 + 0] = (float) edge.x1;
            site2Positions[site2Idx * 4 + 1] = site1Positions[site1Idx * 4 + 1] = (float) edge.y1;
            site2Positions[site2Idx * 4 + 2] = site1Positions[site1Idx * 4 + 2] = (float) edge.x2;
            site2Positions[site2Idx * 4 + 3] = site1Positions[site1Idx * 4 + 3] = (float) edge.y2;
        }

        ConvexHull hullGenerator = new ConvexHull();
        EarClippingTriangulator triangulator = new EarClippingTriangulator();
        TextureRegion fillRegion = Drawing.getTextureProvider().getSinglePixelSquare();
        GalFX.a(Drawing.getBoardCamera()); // TODO Implement this method in SLAPI

        int noOverlapCount = 0;

        for (int i = stars.size() - 1; i >= 0; i--) {
            float[] poly = polyPoints[i];
            if (poly.length < 6) {
                continue;
            }
            FloatArray floatArray = hullGenerator.computePolygon(poly, false);
            poly = floatArray.toArray();
            Polygon voronoiPolygon = new Polygon(poly);
            float centerX = (float) starPositionsX[i], centerY = (float) starPositionsY[i];
            Polygon octagon = new Polygon(new float[] {
                centerX + SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerY + SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerX + SCSCoreLogic.GRANULARITY_FACTOR * 5F,
                centerY,
                centerX + SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerY - SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerX,
                centerY - SCSCoreLogic.GRANULARITY_FACTOR * 5F,
                centerX - SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerY - SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerX - SCSCoreLogic.GRANULARITY_FACTOR * 5F,
                centerY,
                centerX - SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerY + SCSCoreLogic.GRANULARITY_FACTOR * 5F * 0.7F,
                centerX,
                centerY + SCSCoreLogic.GRANULARITY_FACTOR * 5F,
            });

            Polygon outputPolygon = new Polygon();

            boolean overlap;
            try {
                overlap = Intersector.intersectPolygons(voronoiPolygon, octagon, outputPolygon);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                SCSCoreLogic.LOGGER.debug("Failed to intersect polygons", e);
                continue;
            }

            poly = outputPolygon.getVertices();

            if (!overlap) {
                noOverlapCount++;
                poly = voronoiPolygon.getVertices();
            }

            Color fillColor = getStarColor(stars.get(i));
            int intColor = ((int)(255 * fillColor.a * SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue()) << 24) | ((int)(255 * fillColor.b) << 16) | ((int)(255 * fillColor.g) << 8) | ((int)(255 * fillColor.r));
            float floatColor = NumberUtils.intToFloatColor(intColor);

            if (frontierStar[i]) {
                for (int j = poly.length; j > 0; j -= 2) {
                    float vertexAx = poly[(poly.length - j + 0) % poly.length];
                    float vertexAy = poly[(poly.length - j + 1) % poly.length];
                    float vertexBx = poly[(poly.length - j + 2) % poly.length];
                    float vertexBy = poly[(poly.length - j + 3) % poly.length];

                    if (frontierVertices.contains(FloatHashing.positionalRawHash(vertexAx, vertexAy))) {
                        vertexAx = (centerX + vertexAx * 4) / 5;
                        vertexAy = (centerY + vertexAy * 4) / 5;
                    }

                    if (frontierVertices.contains(FloatHashing.positionalRawHash(vertexBx, vertexBy))) {
                        vertexBx = (centerX + vertexBx * 4) / 5;
                        vertexBy = (centerY + vertexBy * 4) / 5;
                    }

                    batch.draw(fillRegion.getTexture(), new float[] {
                        centerX,
                        centerY,
                        floatColor,
                        fillRegion.getU(),
                        fillRegion.getV(),
                        centerX,
                        centerY,
                        floatColor,
                        fillRegion.getU(),
                        fillRegion.getV(),
                        vertexAx,
                        vertexAy,
                        floatColor,
                        fillRegion.getU2(),
                        fillRegion.getV2(),
                        vertexBx,
                        vertexBy,
                        floatColor,
                        fillRegion.getU2(),
                        fillRegion.getV2()
                    }, 0, 20);
                }
            } else {
                ShortArray indices = triangulator.computeTriangles(poly, 0, poly.length);
                for (int j = indices.size - 3; j >= 0; j -= 3) {
                    batch.draw(fillRegion.getTexture(), new float[] {
                            poly[indices.items[j] * 2],
                            poly[indices.items[j] * 2 + 1],
                            floatColor,
                            fillRegion.getU(),
                            fillRegion.getV(),
                            poly[indices.items[j] * 2],
                            poly[indices.items[j] * 2 + 1],
                            floatColor,
                            fillRegion.getU(),
                            fillRegion.getV(),
                            poly[indices.items[j + 1] * 2],
                            poly[indices.items[j + 1] * 2 + 1],
                            floatColor,
                            fillRegion.getU2(),
                            fillRegion.getV2(),
                            poly[indices.items[j + 2] * 2],
                            poly[indices.items[j + 2] * 2 + 1],
                            floatColor,
                            fillRegion.getU2(),
                            fillRegion.getV2()
                    }, 0, 20);
                }
            }
        }

        if (noOverlapCount != 0) {
            LoggerFactory.getLogger(SCSCoreLogic.class).debug("Vorbez: {} regions do not have an overlap (incorrect voronoi regions?).", noOverlapCount);
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
            if (star.getEmpire() == Galimulator.getUniverse().getNeutralEmpire()) {
                if (EnumSettings.DRAW_NEUTRAL_STARS.getValue() == Boolean.FALSE) {
                    return Color.CLEAR;
                }
                Color c = star.getEmpire().getGDXColor();
                return new Color(c.r,c.g, c.b, c.a * 0.3F);
            }
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
