#version 330

in vec2 outTextCoord;

out vec4 fragColor;

struct Material
{
    vec4 diffuse;
};

uniform sampler2D txtSampler;
uniform Material material;

void main()
{
    fragColor = texture(txtSampler, outTextCoord) + material.diffuse;
}