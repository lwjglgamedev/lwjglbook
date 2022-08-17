package org.lwjglb.engine.graph;

import org.lwjglb.engine.scene.*;

import java.util.*;

import static org.lwjgl.opengl.GL43.*;

public class AnimationRender {

    private ShaderProgram shaderProgram;
    private UniformsMap uniformsMap;

    public AnimationRender() {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData("resources/shaders/anim.comp", GL_COMPUTE_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);
        createUniforms();
    }

    public void cleanup() {
        shaderProgram.cleanup();
    }

    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramId());
        uniformsMap.createUniform("drawParameters.srcOffset");
        uniformsMap.createUniform("drawParameters.srcSize");
        uniformsMap.createUniform("drawParameters.weightsOffset");
        uniformsMap.createUniform("drawParameters.bonesMatricesOffset");
        uniformsMap.createUniform("drawParameters.dstOffset");
    }

    public void render(Scene scene, RenderBuffers globalBuffer) {
        shaderProgram.bind();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, globalBuffer.getBindingPosesBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, globalBuffer.getBonesIndicesWeightsBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, globalBuffer.getBonesMatricesBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, globalBuffer.getDestAnimationBuffer());

        int dstOffset = 0;
        for (Model model : scene.getModelMap().values()) {
            if (model.isAnimated()) {
                for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                    RenderBuffers.AnimMeshDrawData animMeshDrawData = meshDrawData.animMeshDrawData();
                    Entity entity = animMeshDrawData.entity();
                    Model.AnimatedFrame frame = entity.getAnimationData().getCurrentFrame();
                    int groupSize = (int) Math.ceil((float) meshDrawData.sizeInBytes() / (14 * 4));
                    uniformsMap.setUniform("drawParameters.srcOffset", animMeshDrawData.bindingPoseOffset());
                    uniformsMap.setUniform("drawParameters.srcSize", meshDrawData.sizeInBytes() / 4);
                    uniformsMap.setUniform("drawParameters.weightsOffset", animMeshDrawData.weightsOffset());
                    uniformsMap.setUniform("drawParameters.bonesMatricesOffset", frame.getOffset());
                    uniformsMap.setUniform("drawParameters.dstOffset", dstOffset);
                    glDispatchCompute(groupSize, 1, 1);
                    dstOffset += meshDrawData.sizeInBytes() / 4;
                }
            }
        }

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        shaderProgram.unbind();
    }
}