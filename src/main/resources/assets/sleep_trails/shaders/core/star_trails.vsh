#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec3 cloudRay;

void main() {
    // ModelOffset.x is the arc the stars turned through over the exposure, in radians; UV0.x is how far back along it
    // this corner lies. Vanilla turns its stars about the buffer's X axis, so the past is a turn back about the same axis.
    // A corner at 0 is the star itself and goes through vanilla's own expression, so the trail meets vanilla's quad exactly.
    vec3 position = Position;
    if (UV0.x > 0.0) {
        float angle = -ModelOffset.x * UV0.x;
        float c = cos(angle);
        float s = sin(angle);
        position = vec3(Position.x, c * Position.y - s * Position.z, s * Position.y + c * Position.z);
    }
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    cloudRay = mat3(TextureMat) * position;
}
