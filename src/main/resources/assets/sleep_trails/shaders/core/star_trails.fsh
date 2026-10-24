#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>

uniform sampler2D Sampler0;
layout(location = 0) in vec3 cloudRay;
layout(location = 0) out vec4 fragColor;

bool cloudCell(ivec2 cell, ivec2 center, int radius) {
    ivec2 relative = cell - center;
    if (dot(vec2(relative), vec2(relative)) > float(radius * radius)) return false;
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 wrapped = ivec2(mod(vec2(cell), vec2(size)));
    return texelFetch(Sampler0, wrapped, 0).a >= 10.0 / 255.0;
}

float visibility() {
    if (ModelOffset.y < 0.5 || ModelOffset.z <= 0.0) return 1.0;
    vec3 ray = normalize(cloudRay);
    vec3 origin = TextureMat[3].xyz;
    float start;
    float finish;
    if (ModelOffset.y < 1.5) {
        if (abs(ray.y) < 0.000001) return 1.0;
        start = -origin.y / ray.y;
        finish = start;
        if (start < 0.0 || start >= FogCloudsEnd) return 1.0;
    } else {
        if (abs(ray.y) < 0.000001) {
            if (origin.y < 0.0 || origin.y > 4.0) return 1.0;
            start = 0.0;
            finish = FogCloudsEnd;
        } else {
            float bottom = -origin.y / ray.y;
            float top = (4.0 - origin.y) / ray.y;
            start = max(0.0, min(bottom, top));
            finish = min(FogCloudsEnd, max(bottom, top));
            if (finish < start) return 1.0;
        }
    }
    vec2 at = (origin.xz + ray.xz * start) / 12.0;
    ivec2 cell = ivec2(floor(at));
    ivec2 center = ivec2(floor(origin.xz / 12.0));
    int radius = int(TextureMat[3].w);
    vec2 velocity = ray.xz / 12.0;
    ivec2 stepCell = ivec2(sign(velocity));
    vec2 delta = vec2(1e30);
    if (abs(velocity.x) > 0.00000001) delta.x = abs(1.0 / velocity.x);
    if (abs(velocity.y) > 0.00000001) delta.y = abs(1.0 / velocity.y);
    vec2 boundary = vec2(cell) + max(vec2(stepCell), vec2(0.0));
    vec2 next = vec2(1e30);
    if (stepCell.x != 0) next.x = start + (boundary.x - at.x) / velocity.x;
    if (stepCell.y != 0) next.y = start + (boundary.y - at.y) / velocity.y;
    ivec2 endCell = ivec2(floor((origin.xz + ray.xz * finish) / 12.0));
    ivec2 cells = abs(endCell - cell);
    int count = cells.x + cells.y + 1;
    float distance = start;
    // Visit every intersected cell, including the side faces of Fancy clouds. No fixed step limit or density reduction.
    for (int i = 0; i < count; i++) {
        if (cloudCell(cell, center, radius)) {
            float coverage = ModelOffset.z * (1.0 - linear_fog_value(distance, 0.0, FogCloudsEnd));
            return 1.0 - smoothstep(0.0, 0.15, coverage);
        }
        if (next.x < next.y) {
            distance = next.x; next.x += delta.x; cell.x += stepCell.x;
        } else {
            distance = next.y; next.y += delta.y; cell.y += stepCell.y;
        }
        if (distance > finish) break;
    }
    return 1.0;
}

void main() {
    fragColor = ColorModulator * visibility();
}
