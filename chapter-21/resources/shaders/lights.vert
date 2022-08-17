#version 330

layout (location=0) in vec3 inPos;
layout (location=1) in vec2 inCoord;

out vec2 outTextCoord;

void main()
{
    outTextCoord = inCoord;
    gl_Position = vec4(inPos, 1.0f);
}