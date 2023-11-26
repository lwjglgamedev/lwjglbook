#version 460

const int MAX_DRAW_ELEMENTS = 100;
const int MAX_ENTITIES = 50;

layout (location=0) in vec3 position;
layout (location=1) in vec3 normal;
layout (location=2) in vec3 tangent;
layout (location=3) in vec3 bitangent;
layout (location=4) in vec2 texCoord;

out vec3 outNormal;
out vec3 outTangent;
out vec3 outBitangent;
out vec2 outTextCoord;
out vec4 outViewPosition;
out vec4 outWorldPosition;
flat out uint outMaterialIdx;

struct DrawElement
{
    int modelMatrixIdx;
    int materialIdx;
};

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform DrawElement drawElements[MAX_DRAW_ELEMENTS];
uniform mat4 modelMatrices[MAX_ENTITIES];

void main()
{
    vec4 initPos = vec4(position, 1.0);
    vec4 initNormal = vec4(normal, 0.0);
    vec4 initTangent = vec4(tangent, 0.0);
    vec4 initBitangent = vec4(bitangent, 0.0);

    uint idx = gl_BaseInstance + gl_InstanceID;
    DrawElement drawElement = drawElements[idx];
    outMaterialIdx = drawElement.materialIdx;
    mat4 modelMatrix =  modelMatrices[drawElement.modelMatrixIdx];
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * initPos;
    outViewPosition  = viewMatrix * outWorldPosition;
    gl_Position   = projectionMatrix * outViewPosition;
    outNormal     = normalize(modelViewMatrix * initNormal).xyz;
    outTangent    = normalize(modelViewMatrix * initTangent).xyz;
    outBitangent  = normalize(modelViewMatrix * initBitangent).xyz;
    outTextCoord  = texCoord;
}