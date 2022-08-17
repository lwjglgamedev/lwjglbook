#version 330

layout (location=0) in vec2 inPos;
layout (location=1) in vec2 inTextCoords;
layout (location=2) in vec4 inColor;

out vec2 frgTextCoords;
out vec4 frgColor;

uniform vec2 scale;

void main()
{
    frgTextCoords = inTextCoords;
    frgColor = inColor;
    gl_Position = vec4(inPos * scale + vec2(-1.0, 1.0), 0.0, 1.0);
}