#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding = 0) uniform sampler2D texSampler;
layout(binding = 1) uniform UniformBufferObject {
    mat4 posTransform;
    mat4 texcoordTransform;
    uvec4 mode;
    vec4 alpha;
    vec4 color;
} ubo;

layout(location = 0) in vec2 fragTexCoord;

layout(location = 0) out vec4 outColor;

void main() {
    if (ubo.mode.x == 2) {
        outColor = ubo.alpha * texture(texSampler, fragTexCoord);
    } else if (ubo.mode.x == 3) {
        outColor = ubo.alpha * ubo.color;
    } else {
        outColor = vec4(0.0, 1.0, 0.0, 1.0);
    }
}
