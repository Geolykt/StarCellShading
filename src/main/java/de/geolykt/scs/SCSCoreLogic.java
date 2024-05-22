package de.geolykt.scs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.danilopianini.util.FlexibleQuadTree;
import org.jetbrains.annotations.NotNull;
import org.jglrxavpok.jlsl.BytecodeDecoder;
import org.jglrxavpok.jlsl.JLSLContext;
import org.jglrxavpok.jlsl.glsl.GLSLEncoder;
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

import de.geolykt.scs.rendercache.DeferredGlobalRenderObject;
import de.geolykt.scs.shaders.StarRegionBlitFragmentShader;
import de.geolykt.scs.shaders.StarRegionBlitVertexShader;
import de.geolykt.scs.shaders.StarRegionExplodeFragmentShader;
import de.geolykt.scs.shaders.StarRegionExplodeVertexShader;
import de.geolykt.starloader.api.CoordinateGrid;
import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.empire.Star;
import de.geolykt.starloader.api.gui.Drawing;
import de.geolykt.starloader.api.gui.MapMode;
import de.geolykt.starloader.api.registry.RegistryKeys;
import de.geolykt.starloader.impl.registry.SLMapMode;

public class SCSCoreLogic {
    private static final VertexAttribute ATTRIBUTE_CENTER_POSITION = new VertexAttribute(Usage.Generic, 2, GL20.GL_FLOAT, false, "a_centerpos");
    private static final VertexAttribute ATTRIBUTE_VERTEX_POSITION = new VertexAttribute(Usage.Position, 2, GL20.GL_FLOAT, false, ShaderProgram.POSITION_ATTRIBUTE);
    private static ShaderProgram blitShader;

    private static ShaderProgram explodeShader;
    private static final float GRANULARITY_FACTOR = 0.035F;
    private static final int BOX_SIZE = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger(SCSCoreLogic.class);
    private static final float REGION_SIZE = GRANULARITY_FACTOR * 16;
    private static final int MAX_INDICES = 0x1000;
    private static final int MAX_INDICES_MASK = 0x0FFF;

    public static void disposeBlitShader() {
        ShaderProgram shader = SCSCoreLogic.blitShader;
        if (shader == null) {
            SCSCoreLogic.LOGGER.warn("Blit shader not yet initialized, yet it should be disposed.");
            return;
        }
        SCSCoreLogic.blitShader = null;
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
        for (Star s : Galimulator.getStarList()) {
            quadTree.insert(s, s.getX(), s.getY());
        }

        Drawing.getRendercacheUtils().getDrawingState().pushObject(new DeferredGlobalRenderObject(() -> {
            SCSCoreLogic.drawRegionsDirect(quadTree);
        }));
    }

    public static void drawRegionsDirect(FlexibleQuadTree<Star> quadTree) {
        SpriteBatch batch = Drawing.getDrawingBatch();

        ShaderProgram explodeShader = SCSCoreLogic.explodeShader;
        if (explodeShader == null) {
            SCSCoreLogic.LOGGER.warn("Explode shader program wasn't yet initialized. Doing it now");
            explodeShader = SCSCoreLogic.initializeExplodeShader();
        }

        ShaderProgram blitShader = SCSCoreLogic.blitShader;
        if (blitShader == null) {
            SCSCoreLogic.LOGGER.warn("Blit shader program wasn't yet initialized. Doing it now");
            blitShader = SCSCoreLogic.initializeBlitShader();
        }

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        Vector3 minCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, 0, screenH);
        Vector3 maxCoords = Drawing.convertCoordinates(CoordinateGrid.SCREEN, CoordinateGrid.BOARD, screenW, 0);

        minCoords.sub(REGION_SIZE * 2);
        maxCoords.add(REGION_SIZE * 2);

        List<Star> stars = quadTree.query(minCoords.x, minCoords.y, maxCoords.x, maxCoords.y);

        boolean drawing;
        if (drawing = batch.isDrawing()) {
            drawing = false;
            batch.flush();
        }

        IntMap<List<Star>> empires = new IntMap<>();
        int maxlen = 0;
        for (Star star : stars) {
            int empireUID = star.getAssignedEmpireUID();
            List<Star> empire = empires.get(empireUID);
            if (empire == null) {
                empire = new ArrayList<>();
                empires.put(empireUID, empire);
            }
            empire.add(star);
            maxlen = Math.max(maxlen, empire.size());
        }

        maxlen = Math.min(MAX_INDICES, maxlen);
        float[] vertices = new float[maxlen * 16];
        Mesh mesh = new Mesh(false, maxlen * 4, maxlen * 5, ATTRIBUTE_VERTEX_POSITION, ATTRIBUTE_CENTER_POSITION);

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
        primaryBlitBatch.setProjectionMatrix(new Matrix4().translate(-1F, 1F, 0).scale(2, -2, 0));

        try {
            for (List<Star> empire : empires.values()) {

                secondaryFB.begin();
                Gdx.gl20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);
                Gdx.gl20.glEnable(GL20.GL_BLEND);
                Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
                Gdx.gl20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

                explodeShader.bind();
                Matrix4 projectedTransformationMatrix = batch.getProjectionMatrix().cpy().mul(batch.getTransformMatrix());
                explodeShader.setUniformMatrix("u_projTrans", projectedTransformationMatrix);

                int i;
                int empireSize = i = empire.size();
                while (i-- != 0) {
                    Star s = empire.get(i);
                    int baseAddress = (i & MAX_INDICES_MASK) * 16;
                    float x = s.getX();
                    float y = s.getY();

                    vertices[baseAddress] = x - BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 1] = y - BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 2] = x;
                    vertices[baseAddress + 3] = y;

                    vertices[baseAddress + 4] = x + BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 5] = y - BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 6] = x;
                    vertices[baseAddress + 7] = y;

                    vertices[baseAddress + 8] = x - BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 9] = y + BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 10] = x;
                    vertices[baseAddress + 11] = y;

                    vertices[baseAddress + 12] = x + BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 13] = y + BOX_SIZE * GRANULARITY_FACTOR;
                    vertices[baseAddress + 14] = x;
                    vertices[baseAddress + 15] = y;

                    if ((i & MAX_INDICES_MASK) == 0) {
                        mesh.setVertices(vertices, 0, Math.min(empireSize - i, MAX_INDICES) * 16);
                        mesh.render(explodeShader, GL20.GL_TRIANGLE_STRIP, 0, Math.min(empireSize - i, MAX_INDICES) * 5, true);
                    }
                }

                secondaryFB.end();
                tertiaryFB.begin();
                secondaryBlitBatch.setColor(empire.get(0).getAssignedEmpire().getGDXColor());
                secondaryBlitBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                secondaryBlitBatch.begin();
                secondaryBlitBatch.draw(secondaryFB.getColorBufferTexture(), 0, 0, 1, 1);
                secondaryBlitBatch.end();
                tertiaryFB.end();
            }

            primaryBlitBatch.begin();
            primaryBlitBatch.draw(tertiaryFB.getColorBufferTexture(), 0, 0, 1, 1);
            primaryBlitBatch.end();

            if (!explodeShader.getLog().isEmpty()) {
                LOGGER.info("Shader logs (pre dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    LOGGER.info(ln);
                }
            }

            mesh.dispose();
            Gdx.gl20.glBlendEquation(GL20.GL_FUNC_ADD);
            Gdx.gl20.glDisable(org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART);
            batch.getShader().bind();

            if (!explodeShader.getLog().isEmpty()) {
                LOGGER.info("Shader logs (post dispose):");
                for (String ln : explodeShader.getLog().split("\n")) {
                    LOGGER.info(ln);
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

    public static float getStarColorFloat(@NotNull Star star) {
        MapMode mapMode = Galimulator.getActiveMapmode();
        if (mapMode instanceof SLMapMode) {
            Function<@NotNull Star, Color> fun = ((SLMapMode) mapMode).getStarOverlayRegionColorFunction();
            if (fun != null) {
                return fun.apply(star).toFloatBits();
            }
        } else if (mapMode.getRegistryKey().equals(RegistryKeys.GALIMULATOR_DEFAULT_MAPMODE)) {
            return star.getAssignedEmpire().getGDXColor().toFloatBits();
        }

        return Color.WHITE_FLOAT_BITS;
    }

    @NotNull
    public static ShaderProgram initializeBlitShader() {
        ShaderProgram shader = SCSCoreLogic.blitShader;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Blit shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources("star-cell-blit-shader.vert");
        String frag = SCSCoreLogic.readStringFromResources("star-cell-blit-shader.frag");

        if (vert.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionBlitVertexShader.class, new PrintWriter(writer));
            vert = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following blit vertex shader:\n{}", vert);
        }

        if (frag.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionBlitFragmentShader.class, new PrintWriter(writer));
            frag = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following blit fragment shader:\n{}", frag);
        }

        SCSCoreLogic.blitShader = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.blitShader = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                LOGGER.warn("Unable to dispose blit shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    public static ShaderProgram initializeExplodeShader() {
        ShaderProgram shader = SCSCoreLogic.explodeShader;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Explode shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources("star-cell-explode-shader.vert");
        String frag = SCSCoreLogic.readStringFromResources("star-cell-explode-shader.frag");

        if (vert.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionExplodeVertexShader.class, new PrintWriter(writer));
            vert = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following explode vertex shader:\n{}", vert);
        }

        if (frag.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionExplodeFragmentShader.class, new PrintWriter(writer));
            frag = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following explode fragment shader:\n{}", frag);
        }

        SCSCoreLogic.explodeShader = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.explodeShader = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                LOGGER.warn("Unable to dispose explode shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    private static String readStringFromResources(@NotNull String filepath) {
        try (InputStream is = SCSCoreLogic.class.getClassLoader().getResourceAsStream(filepath)) {
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read = is.read(buffer); read != -1; read = is.read(buffer)) {
                baos.write(buffer, 0, read);
            }

            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read string from jar", e);
        }
    }
}
