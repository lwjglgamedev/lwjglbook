package org.lwjglb.engine.graph;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    public static final int MAX_WEIGHTS = 4;
    private Vector3f aabbMax;
    private Vector3f aabbMin;
    private int numVertices;
    private int vaoId;
    private List<Integer> vboIdList;

    public Mesh(MeshData meshData) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.aabbMin = meshData.getAabbMin();
            this.aabbMax = meshData.getAabbMax();
            numVertices = meshData.getIndices().length;
            vboIdList = new ArrayList<>();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            // Positions VBO
            int vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer positionsBuffer = stack.callocFloat(meshData.getPositions().length);
            positionsBuffer.put(0, meshData.getPositions());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            // Normals VBO
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer normalsBuffer = stack.callocFloat(meshData.getNormals().length);
            normalsBuffer.put(0, meshData.getNormals());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);

            // Tangents VBO
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer tangentsBuffer = stack.callocFloat(meshData.getTangents().length);
            tangentsBuffer.put(0, meshData.getTangents());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, tangentsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0);

            // Bitangents VBO
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer bitangentsBuffer = stack.callocFloat(meshData.getBitangents().length);
            bitangentsBuffer.put(0, meshData.getBitangents());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, bitangentsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, 0, 0);

            // Texture coordinates VBO
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer textCoordsBuffer = MemoryUtil.memAllocFloat(meshData.getTextCoords().length);
            textCoordsBuffer.put(0, meshData.getTextCoords());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, textCoordsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(4);
            glVertexAttribPointer(4, 2, GL_FLOAT, false, 0, 0);

            // Bone weights
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            FloatBuffer weightsBuffer = MemoryUtil.memAllocFloat(meshData.getWeights().length);
            weightsBuffer.put(meshData.getWeights()).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, weightsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(5);
            glVertexAttribPointer(5, 4, GL_FLOAT, false, 0, 0);

            // Bone indices
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            IntBuffer boneIndicesBuffer = MemoryUtil.memAllocInt(meshData.getBoneIndices().length);
            boneIndicesBuffer.put(meshData.getBoneIndices()).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, boneIndicesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(6);
            glVertexAttribPointer(6, 4, GL_FLOAT, false, 0, 0);

            // Index VBO
            vboId = glGenBuffers();
            vboIdList.add(vboId);
            IntBuffer indicesBuffer = stack.callocInt(meshData.getIndices().length);
            indicesBuffer.put(0, meshData.getIndices());
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
    }

    public void cleanup() {
        vboIdList.forEach(GL30::glDeleteBuffers);
        glDeleteVertexArrays(vaoId);
    }

    public Vector3f getAabbMax() {
        return aabbMax;
    }

    public Vector3f getAabbMin() {
        return aabbMin;
    }

    public int getNumVertices() {
        return numVertices;
    }

    public final int getVaoId() {
        return vaoId;
    }
}