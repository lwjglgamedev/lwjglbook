#version 330

in vec2 outTextCoord;
out vec4 fragColor;

uniform vec4 diffuse;
uniform sampler2D txtSampler;
uniform int hasTexture;

void main()
{
    if (hasTexture == 1) {
        fragColor = texture(txtSampler, outTextCoord);
    } else {
        fragColor = diffuse;
    }
}