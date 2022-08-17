package org.lwjglb.engine.graph;

import org.lwjgl.system.MemoryUtil;
import org.lwjglb.engine.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

public class ShadowRender {

    private static final int COMMAND_SIZE = 5 * 4;
    private ArrayList<CascadeShadow> cascadeShadows;
    private ShaderProgram shaderProgram;
    private ShadowBuffer shadowBuffer;
    private UniformsMap uniformsMap;

    public ShadowRender() {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData("resources/shaders/shadow.vert", GL_VERTEX_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);

        shadowBuffer = new ShadowBuffer();

        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; i++) {
            CascadeShadow cascadeShadow = new CascadeShadow();
            cascadeShadows.add(cascadeShadow);
        }

        createUniforms();
    }

    private ByteBuffer buildAnimCommandBuffer(List<Model> modelList, Map<String, Integer> entitiesIdxMap) {
        int numMeshes = 0;
        for (Model model : modelList) {
            int numEntities = model.getEntitiesList().size();
            numMeshes += numEntities * model.getMeshDrawDataList().size();
        }

        int firstIndex = 0;
        int baseInstance = 0;
        int drawElement = 0;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * COMMAND_SIZE);
        for (Model model : modelList) {
            for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                RenderBuffers.AnimMeshDrawData animMeshDrawData = meshDrawData.animMeshDrawData();
                Entity entity = animMeshDrawData.entity();
                // count
                commandBuffer.putInt(meshDrawData.vertices());
                // instanceCount
                commandBuffer.putInt(1);
                commandBuffer.putInt(firstIndex);
                // baseVertex
                commandBuffer.putInt(meshDrawData.offset());
                commandBuffer.putInt(baseInstance);

                firstIndex += meshDrawData.vertices();
                baseInstance++;

                String name = "drawElements[" + drawElement + "]";
                uniformsMap.setUniform(name + ".modelMatrixIdx", entitiesIdxMap.get(entity.getId()));
                drawElement++;
            }
        }
        commandBuffer.flip();

        return commandBuffer;
    }

    private ByteBuffer buildStaticCommandBuffer(List<Model> modelList, Map<String, Integer> entitiesIdxMap) {
        int numMeshes = 0;
        for (Model model : modelList) {
            numMeshes += model.getMeshDrawDataList().size();
        }

        int firstIndex = 0;
        int baseInstance = 0;
        int drawElement = 0;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * COMMAND_SIZE);
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            int numEntities = entities.size();
            for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                // count
                commandBuffer.putInt(meshDrawData.vertices());
                // instanceCount
                commandBuffer.putInt(numEntities);
                commandBuffer.putInt(firstIndex);
                // baseVertex
                commandBuffer.putInt(meshDrawData.offset());
                commandBuffer.putInt(baseInstance);

                firstIndex += meshDrawData.vertices();
                baseInstance += entities.size();

                for (Entity entity : entities) {
                    String name = "drawElements[" + drawElement + "]";
                    uniformsMap.setUniform(name + ".modelMatrixIdx", entitiesIdxMap.get(entity.getId()));
                    drawElement++;
                }
            }
        }
        commandBuffer.flip();

        return commandBuffer;
    }

    public void cleanup() {
        shaderProgram.cleanup();
        shadowBuffer.cleanup();
    }

    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramId());
        uniformsMap.createUniform("projViewMatrix");

        for (int i = 0; i < SceneRender.MAX_DRAW_ELEMENTS; i++) {
            String name = "drawElements[" + i + "]";
            uniformsMap.createUniform(name + ".modelMatrixIdx");
        }

        for (int i = 0; i < SceneRender.MAX_ENTITIES; i++) {
            uniformsMap.createUniform("modelMatrices[" + i + "]");
        }
    }

    public List<CascadeShadow> getCascadeShadows() {
        return cascadeShadows;
    }

    public ShadowBuffer getShadowBuffer() {
        return shadowBuffer;
    }

    public void render(Scene scene, RenderBuffers globalBuffer) {
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene);

        glBindFramebuffer(GL_FRAMEBUFFER, shadowBuffer.getDepthMapFBO());
        glViewport(0, 0, ShadowBuffer.SHADOW_MAP_WIDTH, ShadowBuffer.SHADOW_MAP_HEIGHT);

        shaderProgram.bind();

        Map<String, Integer> entitiesIdxMap = new HashMap<>();
        int entityIdx = 0;
        for (Model model : scene.getModelMap().values()) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entitiesIdxMap.put(entity.getId(), entityIdx);
                uniformsMap.setUniform("modelMatrices[" + entityIdx + "]", entity.getModelMatrix());
                entityIdx++;
            }
        }

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; i++) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowBuffer.getDepthMapTexture().getIds()[i], 0);
            glClear(GL_DEPTH_BUFFER_BIT);
        }

        renderStaticMeshes(scene, globalBuffer, entitiesIdxMap);
        renderAnimMeshes(scene, globalBuffer, entitiesIdxMap);

        shaderProgram.unbind();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void renderAnimMeshes(Scene scene, RenderBuffers globalBuffer, Map<String, Integer> entitiesIdxMap) {
        List<Model> modelList = scene.getModelMap().values().stream().filter(Model::isAnimated).toList();

        ByteBuffer commandBuffer = buildAnimCommandBuffer(modelList, entitiesIdxMap);
        int drawCount = commandBuffer.remaining() / COMMAND_SIZE;
        int bufferHandle = glGenBuffers();
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, bufferHandle);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_DYNAMIC_DRAW);

        glBindVertexArray(globalBuffer.getAnimVaoId());

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; i++) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowBuffer.getDepthMapTexture().getIds()[i], 0);

            CascadeShadow shadowCascade = cascadeShadows.get(i);
            uniformsMap.setUniform("projViewMatrix", shadowCascade.getProjViewMatrix());

            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, drawCount, 0);
        }

        glBindVertexArray(0);

        MemoryUtil.memFree(commandBuffer);
        glDeleteBuffers(bufferHandle);
    }

    private void renderStaticMeshes(Scene scene, RenderBuffers renderBuffers, Map<String, Integer> entitiesIdxMap) {
        List<Model> modelList = scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();

        ByteBuffer commandBuffer = buildStaticCommandBuffer(modelList, entitiesIdxMap);
        int drawCount = commandBuffer.remaining() / COMMAND_SIZE;
        int bufferHandle = glGenBuffers();
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, bufferHandle);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_DYNAMIC_DRAW);

        glBindVertexArray(renderBuffers.getStaticVaoId());

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; i++) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowBuffer.getDepthMapTexture().getIds()[i], 0);

            CascadeShadow shadowCascade = cascadeShadows.get(i);
            uniformsMap.setUniform("projViewMatrix", shadowCascade.getProjViewMatrix());

            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, drawCount, 0);
        }

        glBindVertexArray(0);

        MemoryUtil.memFree(commandBuffer);
        glDeleteBuffers(bufferHandle);
    }
}