package org.lwjglb.engine.graph;

import org.lwjgl.system.MemoryUtil;
import org.lwjglb.engine.scene.*;
import org.tinylog.Logger;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL43.*;

public class SceneRender {

    public static final int MAX_DRAW_ELEMENTS = 100;
    public static final int MAX_ENTITIES = 50;
    private static final int COMMAND_SIZE = 5 * 4;
    private static final int MAX_MATERIALS = 20;
    private static final int MAX_TEXTURES = 16;
    private ShaderProgram shaderProgram;
    private UniformsMap uniformsMap;

    public SceneRender() {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData("resources/shaders/scene.vert", GL_VERTEX_SHADER));
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData("resources/shaders/scene.frag", GL_FRAGMENT_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);
        createUniforms();
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
                    uniformsMap.setUniform(name + ".materialIdx", meshDrawData.materialIdx());
                    drawElement++;
                }
            }
        }
        commandBuffer.flip();

        return commandBuffer;
    }

    public void cleanup() {
        shaderProgram.cleanup();
    }

    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramId());
        uniformsMap.createUniform("projectionMatrix");
        uniformsMap.createUniform("viewMatrix");

        for (int i = 0; i < MAX_TEXTURES; i++) {
            uniformsMap.createUniform("txtSampler[" + i + "]");
        }

        for (int i = 0; i < MAX_MATERIALS; i++) {
            String name = "materials[" + i + "]";
            uniformsMap.createUniform(name + ".diffuse");
            uniformsMap.createUniform(name + ".specular");
            uniformsMap.createUniform(name + ".reflectance");
            uniformsMap.createUniform(name + ".normalMapIdx");
            uniformsMap.createUniform(name + ".textureIdx");
        }

        for (int i = 0; i < MAX_DRAW_ELEMENTS; i++) {
            String name = "drawElements[" + i + "]";
            uniformsMap.createUniform(name + ".modelMatrixIdx");
            uniformsMap.createUniform(name + ".materialIdx");
        }

        for (int i = 0; i < MAX_ENTITIES; i++) {
            uniformsMap.createUniform("modelMatrices[" + i + "]");
        }
    }

    public void render(Scene scene, RenderBuffers globalBuffer, GBuffer gBuffer) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, gBuffer.getGBufferId());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, gBuffer.getWidth(), gBuffer.getHeight());
        glDisable(GL_BLEND);

        shaderProgram.bind();

        uniformsMap.setUniform("projectionMatrix", scene.getProjection().getProjMatrix());
        uniformsMap.setUniform("viewMatrix", scene.getCamera().getViewMatrix());

        TextureCache textureCache = scene.getTextureCache();
        List<Texture> textures = textureCache.getAll().stream().toList();
        int numTextures = textures.size();
        if (numTextures > MAX_TEXTURES) {
            Logger.warn("Only " + MAX_TEXTURES + " textures can be used");
        }
        for (int i = 0; i < Math.min(MAX_TEXTURES, numTextures); i++) {
            uniformsMap.setUniform("txtSampler[" + i + "]", i);
            Texture texture = textures.get(i);
            glActiveTexture(GL_TEXTURE0 + i);
            texture.bind();
        }

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

        renderStaticMeshes(scene, globalBuffer, entitiesIdxMap);

        glEnable(GL_BLEND);
        shaderProgram.unbind();
    }

    private void renderStaticMeshes(Scene scene, RenderBuffers globalBuffer, Map<String, Integer> entitiesIdxMap) {
        List<Model> modelList = scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();

        ByteBuffer commandBuffer = buildStaticCommandBuffer(modelList, entitiesIdxMap);
        int drawCount = commandBuffer.remaining() / COMMAND_SIZE;
        int bufferHandle = glGenBuffers();
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, bufferHandle);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_DYNAMIC_DRAW);

        glBindVertexArray(globalBuffer.getStaticVaoId());
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, drawCount, 0);
        glBindVertexArray(0);

        MemoryUtil.memFree(commandBuffer);
        glDeleteBuffers(bufferHandle);
    }

    public void setupMaterialsUniform(TextureCache textureCache, MaterialCache materialCache) {
        List<Texture> textures = textureCache.getAll().stream().toList();
        int numTextures = textures.size();
        if (numTextures > MAX_TEXTURES) {
            Logger.warn("Only " + MAX_TEXTURES + " textures can be used");
        }
        Map<String, Integer> texturePosMap = new HashMap<>();
        for (int i = 0; i < Math.min(MAX_TEXTURES, numTextures); i++) {
            texturePosMap.put(textures.get(i).getTexturePath(), i);
        }

        shaderProgram.bind();
        List<Material> materialList = materialCache.getMaterialsList();
        int numMaterials = materialList.size();
        for (int i = 0; i < numMaterials; i++) {
            Material material = materialCache.getMaterial(i);
            String name = "materials[" + i + "]";
            uniformsMap.setUniform(name + ".diffuse", material.getDiffuseColor());
            uniformsMap.setUniform(name + ".specular", material.getSpecularColor());
            uniformsMap.setUniform(name + ".reflectance", material.getReflectance());
            String normalMapPath = material.getNormalMapPath();
            int idx = 0;
            if (normalMapPath != null) {
                idx = texturePosMap.computeIfAbsent(normalMapPath, k -> 0);
            }
            uniformsMap.setUniform(name + ".normalMapIdx", idx);
            Texture texture = textureCache.getTexture(material.getTexturePath());
            idx = texturePosMap.computeIfAbsent(texture.getTexturePath(), k -> 0);
            uniformsMap.setUniform(name + ".textureIdx", idx);
        }
        shaderProgram.unbind();
    }
}