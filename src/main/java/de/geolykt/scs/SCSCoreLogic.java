package de.geolykt.scs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.danilopianini.util.FlexibleQuadTree;
import org.jetbrains.annotations.NotNull;
import org.jglrxavpok.jlsl.BytecodeDecoder;
import org.jglrxavpok.jlsl.JLSLContext;
import org.jglrxavpok.jlsl.glsl.GLSLEncoder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;

import de.geolykt.scs.rendercache.DeferredGlobalRenderObject;
import de.geolykt.scs.shaders.StarRegionFragmentShader;
import de.geolykt.scs.shaders.StarRegionVertexShader;
import de.geolykt.starloader.api.CoordinateGrid;
import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.empire.Star;
import de.geolykt.starloader.api.gui.Drawing;
import de.geolykt.starloader.api.gui.MapMode;
import de.geolykt.starloader.api.registry.RegistryKeys;
import de.geolykt.starloader.impl.registry.SLMapMode;

public class SCSCoreLogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(SCSCoreLogic.class);
    private static ShaderProgram program;

    private static final float GRANULARITY_FACTOR = 0.035F;
    private static final float REGION_SIZE = GRANULARITY_FACTOR * 16;

    private static final VertexAttribute ATTRIBUTE_VERTEX_POSITION = new VertexAttribute(Usage.Position, 2, GL20.GL_FLOAT, false, ShaderProgram.POSITION_ATTRIBUTE);
    private static final VertexAttribute ATTRIBUTE_VERTEX_COLOR = VertexAttribute.ColorPacked();
    private static final VertexAttribute ATTRIBUTE_VERTEX_OWNER = new VertexAttribute(Usage.Generic, 1, GL20.GL_INT, false, "a_owner");

    public static void disposeShader() {
        ShaderProgram shader = SCSCoreLogic.program;
        if (shader == null) {
            SCSCoreLogic.LOGGER.warn("Shader not yet initialized, yet it should be disposed.");
            return;
        }
        SCSCoreLogic.program = null;
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

        ShaderProgram newShader = SCSCoreLogic.program;
        if (newShader == null) {
            SCSCoreLogic.LOGGER.warn("Shader program wasn't yet initialized. Doing it now");
            newShader = SCSCoreLogic.initializeShader();
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

        Mesh mesh = new Mesh(false, 32, 0, ATTRIBUTE_VERTEX_POSITION, ATTRIBUTE_VERTEX_COLOR);
        float[] vertArray = new float[32 * 3];
        newShader.bind();
        newShader.setUniformMatrix("u_projTrans", batch.getProjectionMatrix().cpy().mul(batch.getTransformMatrix()));

        for (Star star : stars) {
            float x = star.getX();
            float y = star.getY();
            vertArray[0] = x;
            vertArray[1] = y;

            float starColorOpaque = getStarColorFloat(star);
//            int starColorOpaqueBits = Float.floatToRawIntBits(starColorOpaque);
//            int starColorTranslucentBits = (starColorOpaqueBits & 0xFF000000) / 16 | (starColorOpaqueBits & 0xFFFFFF);
//            float starColorTranslucent = Float.intBitsToFloat(starColorTranslucentBits);
//            float starColorTranslucent = Color.CLEAR.toFloatBits();
            float starColorTranslucent = starColorOpaque;

            vertArray[2] = starColorOpaque;

            float[] vertexPositionData;
            try {
                vertexPositionData = ((StarAccess) star).starCellShading$getStarRegionVertices();
            } catch (NullPointerException npe) {
                continue;
            }

            int outerVertices = vertexPositionData.length / 2;
            int allVertices = outerVertices + 2;
            for (int i = 0; i < outerVertices; i++) {
                vertArray[(i + 1) * 3 + 0] = vertexPositionData[i * 2];
                vertArray[(i + 1) * 3 + 1] = vertexPositionData[i * 2 + 1];
                vertArray[(i + 1) * 3 + 2] = starColorTranslucent;
            }

            vertArray[outerVertices * 3 + 3] = vertexPositionData[0];
            vertArray[outerVertices * 3 + 4] = vertexPositionData[1];
            vertArray[outerVertices * 3 + 5] = starColorTranslucent;

            mesh.setVertices(vertArray, 0, allVertices * 3);
            newShader.setUniform2fv("u_ccoords", vertArray, 0, 2);
            mesh.render(newShader, GL20.GL_TRIANGLE_FAN, 0, allVertices, true);
            Arrays.fill(vertArray, 0F);
        }

        if (!newShader.getLog().isEmpty()) {
            LOGGER.info("Shader logs (pre dispose):");
            for (String ln : newShader.getLog().split("\n")) {
                LOGGER.info(ln);
            }
        }

        mesh.dispose();

        if (!newShader.getLog().isEmpty()) {
            LOGGER.info("Shader logs (post dispose):");
            for (String ln : newShader.getLog().split("\n")) {
                LOGGER.info(ln);
            }
        }

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
    public static ShaderProgram initializeShader() {
        ShaderProgram shader = SCSCoreLogic.program;
        if (shader != null) {
            SCSCoreLogic.LOGGER.warn("Shader already initialized");
            return shader;
        }

        String vert = SCSCoreLogic.readStringFromResources("star-cell-shader.vert");
        String frag = SCSCoreLogic.readStringFromResources("star-cell-shader.frag");

        if (vert.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionVertexShader.class, new PrintWriter(writer));
            vert = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following vertex shader:\n{}", vert);
        }

        if (frag.isEmpty()) {
            StringWriter writer = new StringWriter();
            JLSLContext context = new JLSLContext(new BytecodeDecoder(), new GLSLEncoder(330));
            context.execute(StarRegionFragmentShader.class, new PrintWriter(writer));
            frag = writer.toString();
            SCSCoreLogic.LOGGER.info("Using following fragment shader:\n{}", frag);
        }

        SCSCoreLogic.program = shader = new ShaderProgram(vert, frag);

        if (!shader.isCompiled()) {
            SCSCoreLogic.program = null;
            try {
                shader.dispose();
            } catch (Exception e) {
                LOGGER.warn("Unable to dispose shader after failing to compile it", e);
            } finally {
                Galimulator.panic("Unable to compile shaders (incompatible drivers?).\n\t  ShaderProgram managed status: " + ShaderProgram.getManagedStatus() + "\n\t  Shader logs:\n" + shader.getLog(), false, new RuntimeException("Failed to compile shaders").fillInStackTrace());
            }
        }

        return shader;
    }

    @NotNull
    private static ClassNode getClassNodeFromClass(@NotNull Class<?> cl) {
        try (InputStream is = cl.getClassLoader().getResourceAsStream(cl.getName().replace('.', '/') + ".class")) {
            ClassReader reader = new ClassReader(is);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read classnode from jar for class " + cl, e);
        }
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
