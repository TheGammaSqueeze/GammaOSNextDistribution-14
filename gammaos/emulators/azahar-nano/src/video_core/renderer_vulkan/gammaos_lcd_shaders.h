// Copyright 2026 GammaOS
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <array>
#include <string_view>

namespace Vulkan::GammaLcd {

// LCD screen shaders for the present pass, ported from the drastic-nano set (RetroArch
// handheld shaders). Each is one texture fetch and a few ALU ops, drawn in the existing
// present draw: no extra pass or render target.
//
// Scaling: the pattern is keyed to the 3DS screen's own pixels (400x240 top, 320x240 bottom)
// whatever the internal resolution, and the display orientation does not matter (lcd_map
// turns the texture coordinate into a 3DS screen pixel position). The effect fades out when a
// 3DS pixel covers fewer than about 3 output pixels, where any grid would only alias.

constexpr std::string_view kCommon = R"(
#version 450 core
#extension GL_ARB_separate_shader_objects : enable

layout (location = 0) in vec2 frag_tex_coord;
layout (location = 0) out vec4 color;

layout (push_constant, std140) uniform DrawInfo {
    mat4 modelview_matrix;
    vec4 i_resolution;
    vec4 o_resolution;
    int screen_id_l;
    int screen_id_r;
    int layer;
    int reverse_interlaced;
    // 3DS screen pixel = (tex.y * x + y, tex.x * z + w)
    vec4 lcd_map;
};

layout (set = 0, binding = 0) uniform sampler2D screen_textures[3];

vec2 ScreenPos(vec2 tc) {
    return vec2(tc.y * lcd_map.x + lcd_map.y, tc.x * lcd_map.z + lcd_map.w);
}

vec2 TexCoordAt(vec2 p) {
    return vec2((p.y - lcd_map.w) / lcd_map.z, (p.x - lcd_map.y) / lcd_map.x);
}

vec3 Fetch(vec2 tc) {
#ifdef ARRAY_DYNAMIC_INDEX
    return texture(screen_textures[screen_id_l], tc).rgb;
#else
    switch (screen_id_l) {
    case 0:
        return texture(screen_textures[0], tc).rgb;
    case 1:
        return texture(screen_textures[1], tc).rgb;
    default:
        return texture(screen_textures[2], tc).rgb;
    }
#endif
}

// Nearest sampling whatever the sampler: the LCD cells stay sharp with Linear Filtering on.
vec3 FetchNearest(vec2 tc) {
    ivec2 size;
#ifdef ARRAY_DYNAMIC_INDEX
    size = textureSize(screen_textures[screen_id_l], 0);
#else
    switch (screen_id_l) {
    case 0:
        size = textureSize(screen_textures[0], 0);
        break;
    case 1:
        size = textureSize(screen_textures[1], 0);
        break;
    default:
        size = textureSize(screen_textures[2], 0);
        break;
    }
#endif
    const vec2 s = vec2(size);
    return Fetch((floor(tc * s) + 0.5) / s);
}

// Strength of the effect from the scale: 0 at 1.5 output pixels per 3DS pixel or less, full
// from 3. ps is one output pixel measured in 3DS pixels.
float Strength(vec2 ps) {
    return clamp((1.0 / max(ps.x, ps.y) - 1.5) / 1.5, 0.0, 1.0);
}

const float PI = 3.141592654;
)";

// lcd1x (Gigaherz, tweaked by jdgleaver): scanlines between the pixels, no colour separation.
constexpr std::string_view kLcd1x = R"(
const float BRIGHTEN_SCANLINES = 16.0;
const float BRIGHTEN_LCD = 4.0;

void main() {
    const vec2 p = ScreenPos(frag_tex_coord);
    const float k = Strength(fwidth(p));
    const vec2 angle = 2.0 * PI * (p - 0.25);
    const float yfactor = (BRIGHTEN_SCANLINES + sin(angle.y)) / (BRIGHTEN_SCANLINES + 1.0);
    const float xfactor = (BRIGHTEN_LCD + sin(angle.x)) / (BRIGHTEN_LCD + 1.0);
    color = vec4(FetchNearest(frag_tex_coord) * mix(1.0, yfactor * xfactor, k), 1.0);
}
)";

// lcd3x (Gigaherz): scanlines plus red, green and blue subpixel columns.
constexpr std::string_view kLcd3x = R"(
const float BRIGHTEN_SCANLINES = 16.0;
const float BRIGHTEN_LCD = 4.0;
const vec3 OFFSETS = vec3(PI) * vec3(1.0 / 2.0, 1.0 / 2.0 - 2.0 / 3.0, 1.0 / 2.0 - 4.0 / 3.0);

void main() {
    const vec2 p = ScreenPos(frag_tex_coord);
    const float k = Strength(fwidth(p));
    const vec2 angle = 2.0 * PI * p;
    const float yfactor = (BRIGHTEN_SCANLINES + sin(angle.y)) / (BRIGHTEN_SCANLINES + 1.0);
    const vec3 xfactors = (BRIGHTEN_LCD + sin(angle.x + OFFSETS)) / (BRIGHTEN_LCD + 1.0);
    color = vec4(FetchNearest(frag_tex_coord) * mix(vec3(1.0), yfactor * xfactors, k), 1.0);
}
)";

// zfast-lcd (SoltanGris42): a dark border around every pixel, colour from the pixel centre.
constexpr std::string_view kZfastLcd = R"(
const float BORDERMULT = 14.0;

void main() {
    const vec2 p = ScreenPos(frag_tex_coord);
    const float k = Strength(fwidth(p));
    const vec2 centre = floor(p) + 0.5;
    const vec2 dist = abs(centre - p);
    float y = max(dist.x, dist.y);
    y = y * y;
    const float yy = y * y;
    const float weight = 1.0 - BORDERMULT * (yy - 2.7 * yy * y);
    vec3 c = Fetch(TexCoordAt(centre)) * mix(1.0, weight, k);
    // The original's fake gamma ("GBA gamma hack", on in its preset).
    c *= 0.6 + 0.4 * c;
    color = vec4(c, 1.0);
}
)";

// retro-v2 (Hyllian): each pixel a square with a thin dark gap, antialiased to the output.
// Gamma 2.0 (square and square root) stands in for the original's pow 2.4 / 1/2.2: pow is
// the expensive part on Adreno 6xx and the difference does not show.
constexpr std::string_view kRetro = R"(
const float RETRO_PIXEL_SIZE = 0.84;

void main() {
    const vec2 p = ScreenPos(frag_tex_coord);
    const vec2 ps = fwidth(p);
    const float k = Strength(ps);
    const vec3 c = FetchNearest(frag_tex_coord);
    const vec3 e = c * c;
    const vec2 fp = fract(p);
    const vec2 f = clamp(clamp(fp + 0.5 * ps, 0.0, 1.0) - RETRO_PIXEL_SIZE, vec2(0.0), ps) / ps;
    const float m = max(f.x, f.y) * k;
    const vec3 res = e * mix(mix(1.0, 1.04 + fp.x * fp.y, k), 0.36, m);
    color = vec4(sqrt(clamp(res, 0.0, 1.0)), 1.0);
}
)";

// bevel (Hyllian): every pixel lit from the top left like a raised tile. Gamma 2.0 as in
// Retro.
constexpr std::string_view kBevel = R"(
const float BEVEL_LEVEL = 0.2;

void main() {
    const vec2 p = ScreenPos(frag_tex_coord);
    const float k = Strength(fwidth(p));
    const vec2 pos = fract(p);
    const vec3 s = FetchNearest(frag_tex_coord);
    vec3 c = s * s;
    const float r = sqrt(pos.x + pos.y);
    const vec3 delta = mix(vec3(BEVEL_LEVEL), vec3(1.0 - BEVEL_LEVEL), c);
    c = clamp(c + delta * (1.0 - r) * k, 0.0, 1.0);
    color = vec4(sqrt(c), 1.0);
}
)";

// Sharp bilinear (themaister's sharp-bilinear): nearest-neighbour up to the largest whole
// multiple, bilinear only across the one-output-pixel seam between texels. Needs the linear
// sampler (bound for every shader here).
constexpr std::string_view kSharpCommon = R"(
ivec2 ScreenSize() {
#ifdef ARRAY_DYNAMIC_INDEX
    return textureSize(screen_textures[screen_id_l], 0);
#else
    switch (screen_id_l) {
    case 0:
        return textureSize(screen_textures[0], 0);
    case 1:
        return textureSize(screen_textures[1], 0);
    default:
        return textureSize(screen_textures[2], 0);
    }
#endif
}

// Texture coordinate to sample for sharp bilinear at tc.
vec2 SharpCoord(vec2 tc, vec2 size) {
    const vec2 texel = tc * size;
    const vec2 scale = max(floor(1.0 / fwidth(texel)), vec2(1.0));
    const vec2 texel_floored = floor(texel);
    const vec2 s = fract(texel);
    const vec2 region_range = 0.5 - 0.5 / scale;
    const vec2 centre_dist = s - 0.5;
    const vec2 f = (centre_dist - clamp(centre_dist, -region_range, region_range)) * scale + 0.5;
    return (texel_floored + f) / size;
}
)";

constexpr std::string_view kSharp = R"(
void main() {
    const vec2 size = vec2(ScreenSize());
    color = vec4(Fetch(SharpCoord(frag_tex_coord, size)), 1.0);
}
)";

// Sharp bilinear with FXAA (Timothy Lottes' FXAA, the compact PC variant) worked out on the
// emulated image's own texels: edges are smoothed at the 3DS resolution, then scaled sharp.
// Flat areas leave after five reads.
constexpr std::string_view kSharpAa = R"(
// Tuned gentle for the 3DS's 240-line image, where much detail is 1 to 2 pixels wide: a
// higher edge threshold and a short search span keep small icons and text intact while
// curves and diagonals still smooth (compared side by side on Kirby: Planet Robobot).
const float FXAA_REDUCE_MIN = 1.0 / 128.0;
const float FXAA_REDUCE_MUL = 1.0 / 8.0;
const float FXAA_SPAN_MAX = 2.0;
const float FXAA_EDGE_MIN = 1.0 / 12.0;
const float FXAA_EDGE_REL = 1.0 / 4.0;
const vec3 LUMA = vec3(0.299, 0.587, 0.114);

void main() {
    const vec2 size = vec2(ScreenSize());
    const vec2 rcp = 1.0 / size;
    const vec2 pos = SharpCoord(frag_tex_coord, size);
    const vec3 rgbM = Fetch(pos);
    const float lumaNW = dot(Fetch(pos + vec2(-1.0, -1.0) * rcp), LUMA);
    const float lumaNE = dot(Fetch(pos + vec2(1.0, -1.0) * rcp), LUMA);
    const float lumaSW = dot(Fetch(pos + vec2(-1.0, 1.0) * rcp), LUMA);
    const float lumaSE = dot(Fetch(pos + vec2(1.0, 1.0) * rcp), LUMA);
    const float lumaM = dot(rgbM, LUMA);
    const float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    const float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
    if (lumaMax - lumaMin < max(FXAA_EDGE_MIN, lumaMax * FXAA_EDGE_REL)) {
        color = vec4(rgbM, 1.0);
        return;
    }
    vec2 dir = vec2(-((lumaNW + lumaNE) - (lumaSW + lumaSE)), (lumaNW + lumaSW) - (lumaNE + lumaSE));
    const float dirReduce =
        max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL), FXAA_REDUCE_MIN);
    const float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin, vec2(-FXAA_SPAN_MAX), vec2(FXAA_SPAN_MAX)) * rcp;
    const vec3 rgbA = 0.5 * (Fetch(pos + dir * (1.0 / 3.0 - 0.5)) + Fetch(pos + dir * (2.0 / 3.0 - 0.5)));
    const vec3 rgbB = rgbA * 0.5 + 0.25 * (Fetch(pos - dir * 0.5) + Fetch(pos + dir * 0.5));
    const float lumaB = dot(rgbB, LUMA);
    color = vec4((lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB, 1.0);
}
)";

// Index 0 is "off" (the stock present shader); 1.. are these. The sharp ones need
// kSharpCommon after kCommon.
constexpr std::array<std::string_view, 7> kShaders = {kLcd1x, kLcd3x, kZfastLcd, kRetro,
                                                      kBevel, kSharp, kSharpAa};
constexpr std::size_t kFirstSharp = 5;

} // namespace Vulkan::GammaLcd
