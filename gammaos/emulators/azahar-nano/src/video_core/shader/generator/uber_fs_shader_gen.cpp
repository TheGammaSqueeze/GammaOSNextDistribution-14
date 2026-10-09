// GammaOS: fragment ubershader for azahar-nano (see uber_fs_shader_gen.h).

#include <cstdlib>
#include <sys/system_properties.h>

#include <fmt/format.h>

#include "common/settings.h"
#include "video_core/shader/generator/profile.h"
#include "video_core/shader/generator/uber_fs_shader_gen.h"

namespace Pica::Shader::Generator::Uber {

namespace {

// Same block as glsl_fs_shader_gen.cpp FSUniformBlockDef, plus the FSConfig
// words at the end (FSUniformData::uber_config).
constexpr std::string_view UniformBlock = R"(
#define NUM_TEV_STAGES 6
#define NUM_LIGHTS 8
#define NUM_LIGHTING_SAMPLERS 24
struct LightSrc {
    vec3 specular_0;
    vec3 specular_1;
    vec3 diffuse;
    vec3 ambient;
    vec3 position;
    vec3 spot_direction;
    float dist_atten_bias;
    float dist_atten_scale;
};
layout (binding = 2, std140) uniform fs_data {
    int framebuffer_scale;
    int alphatest_ref;
    float depth_scale;
    float depth_offset;
    float shadow_bias_constant;
    float shadow_bias_linear;
    int scissor_x1;
    int scissor_y1;
    int scissor_x2;
    int scissor_y2;
    int fog_lut_offset;
    int proctex_noise_lut_offset;
    int proctex_color_map_offset;
    int proctex_alpha_map_offset;
    int proctex_lut_offset;
    int proctex_diff_lut_offset;
    float proctex_bias;
    int shadow_texture_bias;
    ivec4 lighting_lut_offset[NUM_LIGHTING_SAMPLERS / 4];
    vec3 fog_color;
    vec2 proctex_noise_f;
    vec2 proctex_noise_a;
    vec2 proctex_noise_p;
    vec3 lighting_global_ambient;
    LightSrc light_src[NUM_LIGHTS];
    vec4 const_color[NUM_TEV_STAGES];
    vec4 tev_combiner_buffer_color;
    vec3 tex_lod_bias;
    vec4 tex_border_color[3];
    vec4 blend_color;
    uvec4 uber_config[17];
};
// Packed combiner stages: words 2s (sources and ops) and 2s + 1 (modifiers, scales,
// buffer updates, active) for stage s. See TevPack in vk_rasterizer.cpp.
layout(push_constant) uniform UberTev {
    uvec4 w[4];
} tevpc;

)";

// Config accessors. Word layout of Pica::Shader::FSConfig (272 bytes):
//   0      framebuffer raw     1-7  requested blends / logic op (unused here)
//   8      texture raw         9-11 border enables      12-35 tev stages (4 words each)
//   36-41  wrap (unused)       42   lighting raw        43-56 LUTs (raw, scale) x7
//   57-60  lights (u16 each)   61   proctex raw         62 lut_width
//   63-66  lut offsets 0-3     67   lod_min | lod_max << 16
constexpr std::string_view Body = R"(
uint cfg(int i) { return uber_config[i >> 2][i & 3]; }
uint bits(uint w, int off, int n) { return bitfieldExtract(w, off, n); }

// framebuffer
uint fb_raw() { return cfg(0); }
uint alpha_test_func() { return bits(fb_raw(), 0, 3); }
uint scissor_mode() { return bits(fb_raw(), 3, 2); }
bool w_buffering() { return bits(fb_raw(), 5, 1) == 0u; }
uint logic_op() { return bits(fb_raw(), 6, 4); }
bool hw_scissor_on() { return bits(fb_raw(), 13, 1) != 0u; }

// texture
uint tex_raw() { return cfg(8); }
uint texture0_type() { return bits(tex_raw(), 0, 3); }
bool texture2_use_coord1() { return bits(tex_raw(), 3, 1) != 0u; }
uint combiner_buffer_input() { return bits(tex_raw(), 4, 8); }
uint fog_mode() { return bits(tex_raw(), 12, 3); }
bool fog_flip() { return bits(tex_raw(), 15, 1) != 0u; }
bool shadow_texture_orthographic() { return bits(tex_raw(), 16, 1) != 0u; }
bool border_s(int unit) { return bits(cfg(9 + unit), 0, 1) != 0u; }
bool border_t(int unit) { return bits(cfg(9 + unit), 1, 1) != 0u; }

// lighting
uint light_raw() { return cfg(42); }
bool lighting_enable() { return bits(light_raw(), 0, 1) != 0u; }
int light_src_num() { return int(bits(light_raw(), 1, 4)); }
uint bump_mode() { return bits(light_raw(), 5, 2); }
int bump_selector() { return int(bits(light_raw(), 7, 2)); }
bool bump_renorm() { return bits(light_raw(), 9, 1) != 0u; }
bool clamp_highlights_on() { return bits(light_raw(), 10, 1) != 0u; }
uint light_config() { return bits(light_raw(), 11, 4); }
bool enable_primary_alpha() { return bits(light_raw(), 15, 1) != 0u; }
bool enable_secondary_alpha() { return bits(light_raw(), 16, 1) != 0u; }
bool enable_shadow() { return bits(light_raw(), 17, 1) != 0u; }
bool shadow_primary() { return bits(light_raw(), 18, 1) != 0u; }
bool shadow_secondary() { return bits(light_raw(), 19, 1) != 0u; }
bool shadow_invert() { return bits(light_raw(), 20, 1) != 0u; }
bool shadow_alpha() { return bits(light_raw(), 21, 1) != 0u; }
int shadow_selector() { return int(bits(light_raw(), 22, 2)); }
// LUT slots: 0 d0, 1 d1, 2 sp, 3 fr, 4 rr, 5 rg, 6 rb
bool lut_enable(int l) { return bits(cfg(43 + 2 * l), 0, 1) != 0u; }
bool lut_abs(int l) { return bits(cfg(43 + 2 * l), 1, 1) != 0u; }
uint lut_type(int l) { return bits(cfg(43 + 2 * l), 2, 3); }
float lut_scale(int l) { return uintBitsToFloat(cfg(44 + 2 * l)); }
uint light_word(int i) { return bits(cfg(57 + (i >> 1)), (i & 1) * 16, 16); }
int light_num(int i) { return int(bits(light_word(i), 0, 3)); }
bool light_directional(int i) { return bits(light_word(i), 3, 1) != 0u; }
bool light_two_sided(int i) { return bits(light_word(i), 4, 1) != 0u; }
bool light_dist_atten(int i) { return bits(light_word(i), 5, 1) != 0u; }
bool light_spot_atten(int i) { return bits(light_word(i), 6, 1) != 0u; }
bool light_geo0(int i) { return bits(light_word(i), 7, 1) != 0u; }
bool light_geo1(int i) { return bits(light_word(i), 8, 1) != 0u; }
bool light_shadow(int i) { return bits(light_word(i), 9, 1) != 0u; }

// procedural texture
uint pt_raw() { return cfg(61); }
bool proctex_enable() { return bits(pt_raw(), 0, 1) != 0u; }
)";

constexpr std::string_view Helpers = R"(
vec3 quaternion_rotate(vec4 q, vec3 v) {
    return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
}
float byteround(float x) { return round(x * 255.0) * (1.0 / 255.0); }
vec2 byteround(vec2 x) { return round(x * 255.0) * (1.0 / 255.0); }
vec3 byteround(vec3 x) { return round(x * 255.0) * (1.0 / 255.0); }
vec4 byteround(vec4 x) { return round(x * 255.0) * (1.0 / 255.0); }
float getLod(vec2 coord) {
    vec2 d = max(abs(dFdx(coord)), abs(dFdy(coord)));
    return log2(max(d.x, d.y));
}
uvec2 DecodeShadow(uint pixel) { return uvec2(pixel >> 8, pixel & 0xFFu); }

float LookupLightingLUT(int lut_index, int index, float delta) {
    vec2 entry = texelFetch(texture_buffer_lut_lf, lighting_lut_offset[lut_index >> 2][lut_index & 3] + index).rg;
    return entry.r + entry.g * delta;
}
float LookupLightingLUTUnsigned(int lut_index, float pos) {
    int index = int(clamp(floor(pos * 256.0), 0.f, 255.f));
    float delta = pos * 256.0 - float(index);
    return LookupLightingLUT(lut_index, index, delta);
}
float LookupLightingLUTSigned(int lut_index, float pos) {
    int index = int(clamp(floor(pos * 128.0), -128.f, 127.f));
    float delta = pos * 128.0 - float(index);
    if (index < 0) index += 256;
    return LookupLightingLUT(lut_index, index, delta);
}
)";

constexpr std::string_view ShadowRenderHelpers = R"(
uint EncodeShadow(uvec2 pixel) { return (pixel.x << 8) | pixel.y; }
uint UpdateShadow(uint pixel, uint d, uint s) {
    uvec2 ref = DecodeShadow(pixel);
    if (d < ref.x) {
        if (s == 0u) {
            ref.x = d;
        } else {
            s = uint(float(s) / (shadow_bias_constant + shadow_bias_linear * float(d) / float(ref.x)));
            ref.y = min(s, ref.y);
        }
    }
    return EncodeShadow(ref);
}
)";

constexpr std::string_view ShadowCompareHelpers = R"(
float CompareShadow(uint pixel, uint z) {
    uvec2 p = DecodeShadow(pixel);
    return mix(float(p.y) * (1.0 / 255.0), 0.0, p.x <= z);
}
float mix2(vec4 s, vec2 a) {
    vec2 t = mix(s.xy, s.zw, a.yy);
    return mix(t.x, t.y, a.x);
}
)";

constexpr std::string_view Shadow2DSampler = R"(
float SampleShadow2D(ivec2 uv, uint z) {
    if (any(bvec4(lessThan(uv, ivec2(0)), greaterThanEqual(uv, textureSize(tex0, 0)))))
        return 1.0;
    return CompareShadow(texelFetch(tex0, uv, 0).x, z);
}
vec4 shadowTexture(vec2 uv, float w) {
    if (!shadow_texture_orthographic()) uv /= w;
    uint z = uint(max(0, int(min(abs(w), 1.0) * float(0xFFFFFF)) - shadow_texture_bias));
    vec2 coord = vec2(textureSize(tex0, 0)) * uv - vec2(0.5);
    vec2 coord_floor = floor(coord);
    vec2 f = coord - coord_floor;
    ivec2 i = ivec2(coord_floor);
    vec4 s = vec4(
        SampleShadow2D(i              , z),
        SampleShadow2D(i + ivec2(1, 0), z),
        SampleShadow2D(i + ivec2(0, 1), z),
        SampleShadow2D(i + ivec2(1, 1), z));
    return vec4(mix2(s, f));
}
)";

constexpr std::string_view ShadowCubeSampler = R"(
uvec4 SampleShadowCube(int face, ivec2 i00, ivec2 i10, ivec2 i01, ivec2 i11) {
    return uvec4(
        texelFetch(tex0[face], i00, 0).r,
        texelFetch(tex0[face], i10, 0).r,
        texelFetch(tex0[face], i01, 0).r,
        texelFetch(tex0[face], i11, 0).r);
}
vec4 shadowTextureCube(vec2 uv, float w) {
    ivec2 size = textureSize(tex0[0], 0);
    vec3 c = vec3(uv, w);
    vec3 a = abs(c);
    if (a.x > a.y && a.x > a.z) {
        w = a.x;
        uv = -c.zy;
        if (c.x < 0.0) uv.x = -uv.x;
    } else if (a.y > a.z) {
        w = a.y;
        uv = c.xz;
        if (c.y < 0.0) uv.y = -uv.y;
    } else {
        w = a.z;
        uv = -c.xy;
        if (c.z > 0.0) uv.x = -uv.x;
    }
    uint z = uint(max(0, int(min(w, 1.0) * float(0xFFFFFF)) - shadow_texture_bias));
    vec2 coord = vec2(size) * (uv / w * vec2(0.5) + vec2(0.5)) - vec2(0.5);
    vec2 coord_floor = floor(coord);
    vec2 f = coord - coord_floor;
    ivec2 i00 = ivec2(coord_floor);
    ivec2 i10 = i00 + ivec2(1, 0);
    ivec2 i01 = i00 + ivec2(0, 1);
    ivec2 i11 = i00 + ivec2(1, 1);
    ivec2 cmin = ivec2(0), cmax = size - ivec2(1, 1);
    i00 = clamp(i00, cmin, cmax);
    i10 = clamp(i10, cmin, cmax);
    i01 = clamp(i01, cmin, cmax);
    i11 = clamp(i11, cmin, cmax);
    uvec4 pixels;
    if (a.x > a.y && a.x > a.z) {
        if (c.x > 0.0) pixels = SampleShadowCube(0, i00, i10, i01, i11);
        else pixels = SampleShadowCube(1, i00, i10, i01, i11);
    } else if (a.y > a.z) {
        if (c.y > 0.0) pixels = SampleShadowCube(2, i00, i10, i01, i11);
        else pixels = SampleShadowCube(3, i00, i10, i01, i11);
    } else {
        if (c.z > 0.0) pixels = SampleShadowCube(4, i00, i10, i01, i11);
        else pixels = SampleShadowCube(5, i00, i10, i01, i11);
    }
    vec4 s = vec4(
        CompareShadow(pixels.x, z),
        CompareShadow(pixels.y, z),
        CompareShadow(pixels.z, z),
        CompareShadow(pixels.w, z));
    return vec4(mix2(s, f));
}
)";

// Procedural texture (glsl_fs_shader_gen.cpp DefineProcTexSampler), every
// switch on the config made dynamic.
constexpr std::string_view ProcTex = R"(
float ProcTexLookupLUT(int offset, float coord) {
    coord *= 128.0;
    float index_i = clamp(floor(coord), 0.0, 127.0);
    float index_f = coord - index_i;
    vec2 entry = texelFetch(texture_buffer_lut_rg, int(index_i) + offset).rg;
    return clamp(entry.r + entry.g * index_f, 0.0, 1.0);
}
int ProcTexNoiseRand1D(int v) {
    const int table[] = int[](0,4,10,8,4,9,7,12,5,15,13,14,11,15,2,11);
    return ((v % 9 + 2) * 3 & 0xF) ^ table[(v / 9) & 0xF];
}
float ProcTexNoiseRand2D(vec2 point) {
    const int table[] = int[](10,2,15,8,0,7,4,5,5,13,2,6,13,9,3,14);
    int u2 = ProcTexNoiseRand1D(int(point.x));
    int v2 = ProcTexNoiseRand1D(int(point.y));
    v2 += ((u2 & 3) == 1) ? 4 : 0;
    v2 ^= (u2 & 1) * 6;
    v2 += 10 + u2;
    v2 &= 0xF;
    v2 ^= table[u2];
    return -1.0 + float(v2) * (2.0/15.0);
}
float ProcTexNoiseCoef(vec2 x) {
    vec2 grid  = 9.0 * proctex_noise_f * abs(x + proctex_noise_p);
    vec2 point = floor(grid);
    vec2 frac  = grid - point;
    float g0 = ProcTexNoiseRand2D(point) * (frac.x + frac.y);
    float g1 = ProcTexNoiseRand2D(point + vec2(1.0, 0.0)) * (frac.x + frac.y - 1.0);
    float g2 = ProcTexNoiseRand2D(point + vec2(0.0, 1.0)) * (frac.x + frac.y - 1.0);
    float g3 = ProcTexNoiseRand2D(point + vec2(1.0, 1.0)) * (frac.x + frac.y - 2.0);
    float x_noise = ProcTexLookupLUT(proctex_noise_lut_offset, frac.x);
    float y_noise = ProcTexLookupLUT(proctex_noise_lut_offset, frac.y);
    float x0 = mix(g0, g1, x_noise);
    float x1 = mix(g2, g3, x_noise);
    return mix(x0, x1, y_noise);
}
float ProcTexShiftOffset(float v, uint mode, uint clamp_mode) {
    float offset = clamp_mode == 3u ? 1.0 : 0.5;
    if (mode == 1u) return offset * float((int(v) / 2) % 2);
    if (mode == 2u) return offset * float(((int(v) + 1) / 2) % 2);
    return 0.0;
}
float ProcTexClampValue(float x, uint mode) {
    if (mode == 0u) return x > 1.0 ? 0.0 : x;
    if (mode == 2u) return fract(x);
    if (mode == 3u) return int(x) % 2 == 0 ? fract(x) : 1.0 - fract(x);
    if (mode == 4u) return x > 0.5 ? 1.0 : 0.0;
    return min(x, 1.0);
}
float ProcTexCombine(uint combiner, float u, float v) {
    switch (combiner) {
    case 0u: return u;
    case 1u: return (u * u);
    case 2u: return v;
    case 3u: return (v * v);
    case 4u: return ((u + v) * 0.5);
    case 5u: return ((u * u + v * v) * 0.5);
    case 6u: return min(sqrt(u * u + v * v), 1.0);
    case 7u: return min(u, v);
    case 8u: return max(u, v);
    case 9u: return min(((u + v) * 0.5 + sqrt(u * u + v * v)) * 0.5, 1.0);
    }
    return 0.0;
}
vec4 SampleProcTexColor(float lut_coord, int level) {
    int lut_width = int(cfg(62)) >> level;
    int lut_offsets[8] = int[](int(cfg(63)), int(cfg(64)), int(cfg(65)), int(cfg(66)), 0xF0, 0xF8, 0xFC, 0xFE);
    int lut_offset = lut_offsets[level];
    lut_coord *= float(lut_width - 1);
    uint filter_mode = bits(pt_raw(), 17, 3);
    if (filter_mode == 1u || filter_mode == 5u || filter_mode == 3u) {
        int lut_index_i = int(lut_coord) + lut_offset;
        float lut_index_f = fract(lut_coord);
        return texelFetch(texture_buffer_lut_rgba, lut_index_i + proctex_lut_offset) +
               lut_index_f * texelFetch(texture_buffer_lut_rgba, lut_index_i + proctex_diff_lut_offset);
    }
    lut_coord += float(lut_offset);
    return texelFetch(texture_buffer_lut_rgba, int(round(lut_coord)) + proctex_lut_offset);
}
vec4 ProcTex() {
    uint raw = pt_raw();
    uint coord = bits(raw, 1, 2);
    vec2 uv = abs(coord == 1u ? texcoord1 : coord == 2u ? texcoord2 : texcoord0);
    vec2 duv = max(abs(dFdx(uv)), abs(dFdy(uv)));
    float lod = log2(abs(float(int(cfg(62))) * proctex_bias) * (duv.x + duv.y));
    if (proctex_bias == 0.0) lod = 0.0;
    uint lod_word = cfg(67);
    lod = clamp(lod, max(0.0, float(lod_word & 0xFFFFu)), min(7.0, float(lod_word >> 16)));
    uint u_clamp = bits(raw, 3, 3);
    uint v_clamp = bits(raw, 6, 3);
    float u_shift = ProcTexShiftOffset(uv.y, bits(raw, 22, 2), u_clamp);
    float v_shift = ProcTexShiftOffset(uv.x, bits(raw, 24, 2), v_clamp);
    if (bits(raw, 21, 1) != 0u) {
        uv += proctex_noise_a * ProcTexNoiseCoef(uv);
        uv = abs(uv);
    }
    float u = uv.x + u_shift;
    float v = uv.y + v_shift;
    u = ProcTexClampValue(u, u_clamp);
    v = ProcTexClampValue(v, v_clamp);
    float lut_coord = ProcTexLookupLUT(proctex_color_map_offset, ProcTexCombine(bits(raw, 9, 4), u, v));
    uint filter_mode = bits(raw, 17, 3);
    vec4 final_color;
    if (filter_mode == 0u || filter_mode == 1u) {
        final_color = SampleProcTexColor(lut_coord, 0);
    } else if (filter_mode == 2u || filter_mode == 3u) {
        final_color = SampleProcTexColor(lut_coord, int(round(lod)));
    } else {
        int lod_i = int(lod);
        float lod_f = fract(lod);
        final_color = mix(SampleProcTexColor(lut_coord, lod_i), SampleProcTexColor(lut_coord, lod_i + 1), lod_f);
    }
    if (bits(raw, 20, 1) != 0u) {
        float final_alpha = ProcTexLookupLUT(proctex_alpha_map_offset, ProcTexCombine(bits(raw, 13, 4), u, v));
        return vec4(final_color.xyz, final_alpha);
    }
    return final_color;
}
)";

// Texture unit samplers (DefineTexUnitSampler).
constexpr std::string_view TexUnits = R"(
vec2 unit_coord(int unit) {
    if (unit == 0) return texcoord0;
    if (unit == 1) return texcoord1;
    return texture2_use_coord1() ? texcoord1 : texcoord2;
}
bool outside_border(int unit) {
    vec2 c = unit_coord(unit);
    if (border_s(unit) && (c.x < 0 || c.x > 1)) return true;
    if (border_t(unit) && (c.y < 0 || c.y > 1)) return true;
    return false;
}
vec4 sampleTexUnit0() {
    uint type = texture0_type();
    if (type == 5u) return vec4(0.0);
    if (outside_border(0)) return tex_border_color[0];
    SAMPLE_TEX0
}
vec4 sampleTexUnit1() {
    if (outside_border(1)) return tex_border_color[1];
    return textureLod(tex1, texcoord1, getLod(texcoord1 * vec2(textureSize(tex1, 0))) + tex_lod_bias[1]);
}
vec4 sampleTexUnit2() {
    if (outside_border(2)) return tex_border_color[2];
    vec2 c = texture2_use_coord1() ? texcoord1 : texcoord2;
    return textureLod(tex2, c, getLod(c * vec2(textureSize(tex2, 0))) + tex_lod_bias[2]);
}
vec4 sampleTexUnit3() {
#if UBER_PROCTEX && !STRIP_PROCTEX
    if (proctex_enable()) return ProcTex();
#endif
    return vec4(0.0);
}

)";

// TEV (WriteTevStage, GetSource, Append*Modifier, Append*Combiner).
constexpr std::string_view Tev = R"(
// Source selection: a uniform switch (the index comes from push constants).
mediump vec4 select_src(uint s, mediump vec4 prim, mediump vec4 pfc, mediump vec4 sfc,
                        mediump vec4 t0, mediump vec4 t1, mediump vec4 t2, mediump vec4 t3,
                        mediump vec4 buf, mediump vec4 k, mediump vec4 prev) {
    switch (s) {
    case 0u: return prim;
    case 1u: return pfc;
    case 2u: return sfc;
    case 3u: return t0;
    case 4u: return t1;
    case 5u: return t2;
    case 6u: return t3;
    case 13u: return buf;
    case 14u: return k;
    case 15u: return prev;
    }
    return vec4(0.0);
}
// ColorModifier: bit 0 inverts; the rest picks rgb (0), alpha (1), red (2), green (4),
// blue (6); other values give zero.
mediump vec3 cmod(uint m, mediump vec4 c) {
    uint sel = m >> 1;
    vec4 w = vec4(equal(uvec4(sel), uvec4(2u, 4u, 6u, 1u)));
    float valid = float(sel == 0u || sel == 1u || sel == 2u || sel == 4u || sel == 6u);
    mediump vec3 x = mix(vec3(dot(c, w)), c.rgb, float(sel == 0u));
    float inv = float(m & 1u);
    return valid * (inv + (1.0 - 2.0 * inv) * x);
}
// AlphaModifier: bit 0 inverts; the rest picks alpha (0), red (1), green (2), blue (3).
mediump float amod(uint m, mediump vec4 c) {
    uint ch = m >> 1;
    vec4 w = vec4(equal(uvec4(ch), uvec4(1u, 2u, 3u, 0u)));
    float inv = float(m & 1u);
    return inv + (1.0 - 2.0 * inv) * dot(c, w);
}
// Combiner operations as one formula; AddThenMultiply and Dot3 picked by select.
mediump vec3 combine3(uint op, mediump vec3 r1, mediump vec3 r2, mediump vec3 r3) {
    float a = float(op == 0u || op == 2u || op == 3u || op == 5u);
    float b = float(op == 2u || op == 3u || op == 4u) - float(op == 5u);
    float c = float(op == 8u);
    float d = float(op == 1u || op == 8u);
    float e = float(op == 4u);
    float g = -0.5 * float(op == 3u);
    mediump vec3 lin = a * r1 + b * r2 + c * r3 + d * r1 * r2 + e * (r1 * r3 - r2 * r3) + g;
    mediump vec3 atm = min(r1 + r2, vec3(1.0)) * r3;
    mediump vec3 dt = vec3(dot(r1 - vec3(0.5), r2 - vec3(0.5)) * 4.0);
    mediump vec3 res = op == 9u ? atm : lin;
    res = (op == 6u || op == 7u) ? dt : res;
    return op > 9u ? vec3(0.0) : res;
}
mediump float combine1(uint op, mediump float r1, mediump float r2, mediump float r3) {
    float a = float(op == 0u || op == 2u || op == 3u || op == 5u);
    float b = float(op == 2u || op == 3u || op == 4u) - float(op == 5u);
    float c = float(op == 8u);
    float d = float(op == 1u || op == 8u);
    float e = float(op == 4u);
    float g = -0.5 * float(op == 3u);
    mediump float lin = a * r1 + b * r2 + c * r3 + d * r1 * r2 + e * (r1 * r3 - r2 * r3) + g;
    mediump float res = op == 9u ? min(r1 + r2, 1.0) * r3 : lin;
    return (op == 6u || op == 7u || op > 9u) ? 0.0 : res;
}
)";

// Fragment lighting (WriteLighting).
constexpr std::string_view Lighting = R"(
bool sampler_supported(uint config, int smp) {
    switch (smp) {
    case 0: return config != 1u;
    case 1: return config != 0u && config != 1u && config != 5u;
    case 8: return config != 2u && config != 3u;
    case 3: return config != 0u && config != 2u && config != 4u;
    case 6: return config != 3u;
    case 5:
    case 4: return config == 4u || config == 5u || config == 8u;
    }
    return false;
}
// light_slot: the generator reads the two-sided flag of lights[light number],
// so this takes the hardware light number, not the loop index.
float lut_value(int smp, int light_slot, int lut, vec3 normal, vec3 tangent,
                vec3 light_vector, vec3 spot_dir, vec3 half_vector) {
    uint input_type = lut_type(lut);
    float index;
    switch (input_type) {
    case 0u: index = dot(normal, normalize(half_vector)); break;
    case 1u: index = dot(normalize(view), normalize(half_vector)); break;
    case 2u: index = dot(normal, normalize(view)); break;
    case 3u: index = dot(light_vector, normal); break;
    case 4u: index = dot(light_vector, spot_dir); break;
    case 5u:
        if (light_config() == 8u) {
            index = dot(normalize(half_vector) - normal * dot(normal, normalize(half_vector)), tangent);
        } else {
            index = 0.0;
        }
        break;
    default: index = 0.0; break;
    }
    if (lut_abs(lut)) {
        index = light_two_sided(light_slot) ? abs(index) : max(index, 0.0);
        return LookupLightingLUTUnsigned(smp, index);
    }
    return LookupLightingLUTSigned(smp, index);
}
void do_lighting(vec4 tex_bump, vec4 tex_shadow, out vec4 primary_out, out vec4 secondary_out) {
    vec4 diffuse_sum = vec4(0.0, 0.0, 0.0, 1.0);
    vec4 specular_sum = vec4(0.0, 0.0, 0.0, 1.0);
    vec3 light_vector = vec3(0.0);
    float light_distance = 0.0;
    vec3 refl_value = vec3(0.0);
    vec3 spot_dir = vec3(0.0);
    vec3 half_vector = vec3(0.0);
    float dot_product = 0.0;
    float clamp_highlights = 1.0;
    float geo_factor = 1.0;
    vec3 surface_normal;
    vec3 surface_tangent;
    uint bmode = bump_mode();
    if (bmode == 1u) {
        surface_normal = 2.0 * tex_bump.rgb - 1.0;
        if (bump_renorm()) {
            surface_normal.z = sqrt(max((1.0 - (surface_normal.x*surface_normal.x + surface_normal.y*surface_normal.y)), 0.0));
        }
        surface_tangent = vec3(1.0, 0.0, 0.0);
    } else if (bmode == 2u) {
        surface_tangent = 2.0 * tex_bump.rgb - 1.0;
        surface_normal = vec3(0.0, 0.0, 1.0);
    } else {
        surface_normal = vec3(0.0, 0.0, 1.0);
        surface_tangent = vec3(1.0, 0.0, 0.0);
    }
    vec4 normalized_normquat = normalize(normquat);
    vec3 normal = quaternion_rotate(normalized_normquat, surface_normal);
    vec3 tangent = quaternion_rotate(normalized_normquat, surface_tangent);
    vec4 shadow;
    if (enable_shadow()) {
        vec4 shadow_texture = tex_shadow;
        shadow = shadow_invert() ? vec4(1.0) - shadow_texture : shadow_texture;
    } else {
        shadow = vec4(1.0);
    }
    uint config = light_config();
    int count = light_src_num();
    for (int li = 0; li < count; ++li) {
        int num = light_num(li);
        if (light_directional(li)) {
            light_vector = light_src[num].position;
        } else {
            light_vector = light_src[num].position + view;
        }
        light_distance = length(light_vector);
        light_vector = normalize(light_vector);
        spot_dir = light_src[num].spot_direction;
        half_vector = normalize(view) + light_vector;
        dot_product = light_two_sided(li) ? abs(dot(light_vector, normal))
                                          : max(dot(light_vector, normal), 0.0);
        if (clamp_highlights_on()) {
            clamp_highlights = sign(dot_product);
        }
        float spot_atten = 1.0;
        if (light_spot_atten(li) && sampler_supported(config, 8)) {
            spot_atten = (lut_scale(2) * lut_value(8 + num, num, 2, normal, tangent, light_vector, spot_dir, half_vector));
        }
        float dist_atten = 1.0;
        if (light_dist_atten(li)) {
            dist_atten = LookupLightingLUTUnsigned(16 + num,
                clamp(light_src[num].dist_atten_scale * light_distance + light_src[num].dist_atten_bias, 0.0, 1.0));
        }
        if (light_geo0(li) || light_geo1(li)) {
            geo_factor = dot(half_vector, half_vector);
            geo_factor = geo_factor == 0.0 ? 0.0 : min(dot_product / geo_factor, 1.0);
        }
        float d0_lut_value = 1.0;
        if (lut_enable(0) && sampler_supported(config, 0)) {
            d0_lut_value = (lut_scale(0) * lut_value(0, num, 0, normal, tangent, light_vector, spot_dir, half_vector));
        }
        vec3 specular_0 = (d0_lut_value * light_src[num].specular_0);
        if (light_geo0(li)) specular_0 = (specular_0 * geo_factor);
        if (lut_enable(4) && sampler_supported(config, 6)) {
            refl_value.r = (lut_scale(4) * lut_value(6, num, 4, normal, tangent, light_vector, spot_dir, half_vector));
        } else {
            refl_value.r = 1.0;
        }
        if (lut_enable(5) && sampler_supported(config, 5)) {
            refl_value.g = (lut_scale(5) * lut_value(5, num, 5, normal, tangent, light_vector, spot_dir, half_vector));
        } else {
            refl_value.g = refl_value.r;
        }
        if (lut_enable(6) && sampler_supported(config, 4)) {
            refl_value.b = (lut_scale(6) * lut_value(4, num, 6, normal, tangent, light_vector, spot_dir, half_vector));
        } else {
            refl_value.b = refl_value.r;
        }
        float d1_lut_value = 1.0;
        if (lut_enable(1) && sampler_supported(config, 1)) {
            d1_lut_value = (lut_scale(1) * lut_value(1, num, 1, normal, tangent, light_vector, spot_dir, half_vector));
        }
        vec3 specular_1 = (d1_lut_value * refl_value * light_src[num].specular_1);
        if (light_geo1(li)) specular_1 = (specular_1 * geo_factor);
        if (li == count - 1 && lut_enable(3) && sampler_supported(config, 3)) {
            float value = (lut_scale(3) * lut_value(3, num, 3, normal, tangent, light_vector, spot_dir, half_vector));
            if (enable_primary_alpha()) diffuse_sum.a = value;
            if (enable_secondary_alpha()) specular_sum.a = value;
        }
        bool shadow_primary_enable = shadow_primary() && light_shadow(li);
        bool shadow_secondary_enable = shadow_secondary() && light_shadow(li);
        if (shadow_primary_enable) {
            diffuse_sum.rgb += ((light_src[num].diffuse * dot_product * shadow.rgb) + light_src[num].ambient) * dist_atten * spot_atten;
        } else {
            diffuse_sum.rgb += ((light_src[num].diffuse * dot_product) + light_src[num].ambient) * dist_atten * spot_atten;
        }
        if (shadow_secondary_enable) {
            specular_sum.rgb += (specular_0 + specular_1) * clamp_highlights * dist_atten * spot_atten * shadow.rgb;
        } else {
            specular_sum.rgb += (specular_0 + specular_1) * clamp_highlights * dist_atten * spot_atten;
        }
    }
    if (shadow_alpha()) {
        if (enable_primary_alpha()) diffuse_sum.a *= shadow.a;
        if (enable_secondary_alpha()) specular_sum.a *= shadow.a;
    }
    diffuse_sum.rgb += lighting_global_ambient;
    primary_out = clamp(diffuse_sum, vec4(0.0), vec4(1.0));
    secondary_out = clamp(specular_sum, vec4(0.0), vec4(1.0));
}
)";

constexpr std::string_view MainStart = R"(
mediump vec4 pick(uint source, mediump vec4 prim, mediump vec4 pfc, mediump vec4 sfc,
                  mediump vec4 t0, mediump vec4 t1, mediump vec4 t2, mediump vec4 t3,
                  mediump vec4 buf, mediump vec4 k, mediump vec4 prev) {
#if DBG_NOPICK
    return t0 * prim;
#endif
    if (source == 0xfu) return prev;
    if (source < 0x3u) return source == 0x0u ? prim : (source == 0x1u ? pfc : sfc);
    if (source < 0x7u) return source == 0x3u ? t0 : (source == 0x4u ? t1 : (source == 0x5u ? t2 : t3));
    if (source == 0xdu) return buf;
    if (source == 0xeu) return k;
    return vec4(0.0);
}

void main() {
    mediump vec4 prim = byteround(primary_color);
    mediump vec4 pfc = vec4(0.0);
    mediump vec4 sfc = vec4(0.0);
    uint atf = alpha_test_func();
    if (atf == 0u) {
        discard;
    }
#if !UBER_HW_SCISSOR
    uint smode = scissor_mode();
    if (smode != 0u && !hw_scissor_on()) {
        bool inside = (gl_FragCoord.x >= float(scissor_x1) && gl_FragCoord.y >= float(scissor_y1) &&
                       gl_FragCoord.x < float(scissor_x2) && gl_FragCoord.y < float(scissor_y2));
        if (smode == 3u ? !inside : inside) discard;
    }
#else
    uint smode = scissor_mode();
    if (smode == 1u) {
        bool inside = (gl_FragCoord.x >= float(scissor_x1) && gl_FragCoord.y >= float(scissor_y1) &&
                       gl_FragCoord.x < float(scissor_x2) && gl_FragCoord.y < float(scissor_y2));
        if (inside) discard;
    }
#endif
    mediump vec4 t0 = sampleTexUnit0();
#if !STRIP_UNITS
    mediump vec4 t1 = sampleTexUnit1();
    mediump vec4 t2 = sampleTexUnit2();
    mediump vec4 t3 = sampleTexUnit3();
#else
    mediump vec4 t1 = vec4(0.0), t2 = vec4(0.0), t3 = vec4(0.0);
#endif
    float z_over_w = Z_OVER_W;
    float depth = z_over_w * depth_scale + depth_offset;
    if (w_buffering()) {
        depth /= gl_FragCoord.w;
    }
#if UBER_LIGHTING && !STRIP_LIGHTING
    if (lighting_enable()) {
        int bs = bump_selector();
        int ss = shadow_selector();
        vec4 tex_bump = bs == 0 ? t0 : (bs == 1 ? t1 : (bs == 2 ? t2 : t3));
        vec4 tex_shadow = ss == 0 ? t0 : (ss == 1 ? t1 : (ss == 2 ? t2 : t3));
        do_lighting(tex_bump, tex_shadow, pfc, sfc);
    }
#endif
    mediump vec4 combiner_buffer = vec4(0.0);
    mediump vec4 next_combiner_buffer = tev_combiner_buffer_color;
    mediump vec4 combiner_output = vec4(0.0);
#if !STRIP_TEV
    // Word 36 (the wrap modes slot, unused here) holds the number of stages up to the
    // last one that is not a pass-through, filled by the CPU.
TEV_STAGES
#else
    combiner_output = prim * t0;
#endif
    if (atf != 1u) {
        int a = int(combiner_output.a * 255.0);
        bool fail;
        switch (atf) {
        case 2u: fail = a != alphatest_ref; break;
        case 3u: fail = a == alphatest_ref; break;
        case 4u: fail = a >= alphatest_ref; break;
        case 5u: fail = a > alphatest_ref; break;
        case 6u: fail = a <= alphatest_ref; break;
        case 7u: fail = a < alphatest_ref; break;
        default: fail = false; break;
        }
        if (fail) discard;
    }
    uint fmode = fog_mode();
    if (STRIP_FOG != 0) fmode = 0u;
    if (fmode == 5u) {
        float fog_index = fog_flip() ? (1.0 - float(depth)) * 128.0 : depth * 128.0;
        float fog_i = clamp(floor(fog_index), 0.0, 127.0);
        float fog_f = fog_index - fog_i;
        vec2 fog_lut_entry = texelFetch(texture_buffer_lut_lf, int(fog_i) + fog_lut_offset).rg;
        float fog_factor = fog_lut_entry.r + fog_lut_entry.g * fog_f;
        fog_factor = clamp(fog_factor, 0.0, 1.0);
        combiner_output.rgb = mix(fog_color.rgb, combiner_output.rgb, fog_factor);
    } else if (fmode == 7u) {
        color = vec4(0.0);
        WRITE_DEPTH_GAS
        return;
    }
)";

} // namespace

std::string GenerateFragmentShader(const Variant& variant, const Profile& profile,
                                   const std::array<u32, 12>* baked_tev) {
    std::string out;
    out.reserve(64 * 1024);
    out += "#extension GL_ARB_separate_shader_objects : enable\n";
    out += "#extension GL_EXT_control_flow_attributes : enable\n";
    if (variant.shadow_rendering && profile.has_fragment_shader_interlock) {
        out += "#extension GL_ARB_fragment_shader_interlock : enable\n";
    }
    {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.uber_strip", v);
        if (Settings::values.gammaos_fs_mediump || (atoi(v) & 128)) {
            out += "precision mediump float;\n";
        }
    }
    out += fmt::format("#define UBER_HW_SCISSOR {}\n", profile.hw_scissor ? 1 : 0);
    {
        // Debug: persist.gammaos.azahar_nano.uber_strip bit mask removes sections
        // (1 lighting, 2 procedural texture, 4 fog, 8 TEV stages).
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.uber_strip", v);
        const int strip = atoi(v);
        out += fmt::format("#define STRIP_LIGHTING {}\n#define STRIP_PROCTEX {}\n"
                           "#define STRIP_FOG {}\n#define STRIP_TEV {}\n",
                           strip & 1 ? 1 : 0, strip & 2 ? 1 : 0, strip & 4 ? 1 : 0,
                           strip & 8 ? 1 : 0);
        out += fmt::format("#define STRIP_STAGES {}\n#define STRIP_UNITS {}\n", strip & 16 ? 1 : 0,
                           strip & 32 ? 1 : 0);
        out += fmt::format("#define DBG_NOPICK {}\n#define DBG_NOCOMBINE {}\n#define DBG_NOLOOP {}\n",
                           strip & 1024 ? 1 : 0, strip & 2048 ? 1 : 0, strip & 4096 ? 1 : 0);
        out += fmt::format("#define UBER_LIGHTING {}\n#define UBER_PROCTEX {}\n",
                           variant.lighting ? 1 : 0, variant.proctex ? 1 : 0);
    }
    out += fmt::format("#define Z_OVER_W {}\n", profile.has_minus_one_to_one_range
                                                    ? "(-2.0 * gl_FragCoord.z + 1.0)"
                                                    : "(-gl_FragCoord.z)");
    // Interface (DefineInterface; locations follow the Semantic enum).
    out += "layout (location = 1) in vec4 primary_color;\n"
           "layout (location = 2) in vec2 texcoord0;\n"
           "layout (location = 3) in vec2 texcoord1;\n"
           "layout (location = 4) in vec2 texcoord2;\n"
           "layout (location = 5) in float texcoord0_w;\n"
           "layout (location = 6) in vec4 normquat;\n"
           "layout (location = 7) in vec3 view;\n"
           "layout (location = 0) out vec4 color;\n";
    // Bindings (DefineBindingsVK).
    out += UniformBlock;
    out += "layout(set = 0, binding = 3) uniform samplerBuffer texture_buffer_lut_lf;\n"
           "layout(set = 0, binding = 4) uniform samplerBuffer texture_buffer_lut_rg;\n"
           "layout(set = 0, binding = 5) uniform samplerBuffer texture_buffer_lut_rgba;\n";
    switch (variant.tex0) {
    case Tex0Kind::Flat:
        out += "layout(set = 1, binding = 0) uniform sampler2D tex0;\n";
        break;
    case Tex0Kind::Cube:
        out += "layout(set = 1, binding = 0) uniform samplerCube tex0;\n";
        break;
    case Tex0Kind::Shadow2D:
        out += "layout(set = 1, binding = 0) uniform usampler2D tex0;\n";
        break;
    case Tex0Kind::ShadowCube:
        out += "layout(set = 1, binding = 0) uniform usampler2D tex0[6];\n";
        break;
    }
    out += "layout(set = 1, binding = 1) uniform sampler2D tex1;\n"
           "layout(set = 1, binding = 2) uniform sampler2D tex2;\n";
    if (variant.shadow_rendering) {
        out += "layout(set = 2, binding = 0, r32ui) uniform uimage2D shadow_buffer;\n";
    }
    out += Body;
    out += Helpers;
    if (variant.shadow_rendering) {
        out += ShadowRenderHelpers;
    }
    if (variant.tex0 == Tex0Kind::Shadow2D || variant.tex0 == Tex0Kind::ShadowCube) {
        out += ShadowCompareHelpers;
        if (variant.tex0 == Tex0Kind::Shadow2D) {
            out += Shadow2DSampler;
        } else {
            out += ShadowCubeSampler;
        }
    }
    if (variant.proctex) {
        out += ProcTex;
    }
    std::string units{TexUnits};
    const char* sample0 = "";
    switch (variant.tex0) {
    case Tex0Kind::Flat:
        sample0 = "if (type == 3u) return textureProj(tex0, vec3(texcoord0, texcoord0_w));\n"
                  "    return textureLod(tex0, texcoord0, getLod(texcoord0 * "
                  "vec2(textureSize(tex0, 0))) + tex_lod_bias[0]);";
        break;
    case Tex0Kind::Cube:
        sample0 = "return texture(tex0, vec3(texcoord0, texcoord0_w));";
        break;
    case Tex0Kind::Shadow2D:
        sample0 = "return shadowTexture(texcoord0, texcoord0_w);";
        break;
    case Tex0Kind::ShadowCube:
        sample0 = "return shadowTextureCube(texcoord0, texcoord0_w);";
        break;
    }
    units.replace(units.find("SAMPLE_TEX0"), 11, sample0);
    out += units;
    out += Tev;
    if (variant.lighting) {
        out += Lighting;
    }
    std::string main_start{MainStart};
    {
        // Each stage: decode its two packed words (constant push constant indices) and run
        // straight-line math. Source and channel selections are arithmetic weights.
        std::string stages = "    // Branchless combiner on packed push constant stages.\n";
        stages += R"(    for (int s = 0; s < 6; ++s) {
        uint wa = TEV_WORD(2 * s);
        uint wb = TEV_WORD(2 * s + 1);
        if (bitfieldExtract(wb, 27, 1) != 0u) {
            mediump vec4 k = const_color[s];
            mediump vec4 sc1 = select_src(bitfieldExtract(wa, 0, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec4 sc2 = select_src(bitfieldExtract(wa, 4, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec4 sc3 = select_src(bitfieldExtract(wa, 8, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec4 sa1 = select_src(bitfieldExtract(wa, 12, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec4 sa2 = select_src(bitfieldExtract(wa, 16, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec4 sa3 = select_src(bitfieldExtract(wa, 20, 4), prim, pfc, sfc, t0, t1, t2, t3, combiner_buffer, k, combiner_output);
            mediump vec3 c1 = cmod(bitfieldExtract(wb, 0, 4), sc1);
            mediump vec3 c2 = cmod(bitfieldExtract(wb, 4, 4), sc2);
            mediump vec3 c3 = cmod(bitfieldExtract(wb, 8, 4), sc3);
            mediump float a1 = amod(bitfieldExtract(wb, 12, 3), sa1);
            mediump float a2 = amod(bitfieldExtract(wb, 15, 3), sa2);
            mediump float a3 = amod(bitfieldExtract(wb, 18, 3), sa3);
            uint cop = bitfieldExtract(wa, 24, 4);
            uint aop = bitfieldExtract(wa, 28, 4);
            mediump vec3 color_output = byteround(clamp(combine3(cop, c1, c2, c3), vec3(0.0), vec3(1.0)));
            mediump float alpha_output = byteround(clamp(combine1(aop, a1, a2, a3), 0.0, 1.0));
            if (cop == 7u) alpha_output = color_output.r;
            uint cs = bitfieldExtract(wb, 21, 2);
            uint as_ = bitfieldExtract(wb, 23, 2);
            float cmul = cs < 3u ? float(1u << cs) : 1.0;
            float amul = as_ < 3u ? float(1u << as_) : 1.0;
            combiner_output = vec4(clamp(color_output * cmul, vec3(0.0), vec3(1.0)),
                                   clamp(alpha_output * amul, 0.0, 1.0));
        }
        combiner_buffer = next_combiner_buffer;
        // Buffer update flags always come from the push constants (scene state, not part
        // of a baked shader's identity).
        uint wbuf = tevpc.w[(2 * s + 1) >> 2][(2 * s + 1) & 3];
        next_combiner_buffer = vec4(mix(next_combiner_buffer.rgb, combiner_output.rgb, float(bitfieldExtract(wbuf, 25, 1))),
                                    mix(next_combiner_buffer.a, combiner_output.a, float(bitfieldExtract(wbuf, 26, 1))));
    }
)";
        if (baked_tev) {
            std::string table = "const uint TEVW[12] = uint[](";
            for (int n = 0; n < 12; ++n) {
                table += fmt::format("{}u{}", (*baked_tev)[n], n < 11 ? "," : ");\n");
            }
            table += "#define TEV_WORD(i) TEVW[i]\n";
            main_start = table + main_start;
            // Unroll so every stage's words are constants and the unused code folds away.
            const auto loop = stages.find("for (int s = 0; s < 6; ++s) {");
            if (loop != std::string::npos) {
                stages.insert(loop, "[[unroll]] ");
            }
        } else {
            main_start = "#define TEV_WORD(i) tevpc.w[(i) >> 2][(i) & 3]\n" + main_start;
        }
        main_start.replace(main_start.find("TEV_STAGES"), 10, stages);
    }
    main_start.replace(main_start.find("WRITE_DEPTH_GAS"), 15,
                       variant.write_depth && !variant.shadow_rendering ? "gl_FragDepth = depth;"
                                                                        : "");
    out += main_start;
    if (variant.shadow_rendering) {
        out += R"(
    uint d = uint(clamp(depth, 0.0, 1.0) * float(0xFFFFFF));
    uint s = uint(combiner_output.g * float(0xFF));
    ivec2 image_coord = ivec2(gl_FragCoord.xy);
)";
        if (profile.has_fragment_shader_interlock) {
            out += R"(
    beginInvocationInterlockARB();
    uint old_shadow = imageLoad(shadow_buffer, image_coord).x;
    uint new_shadow = UpdateShadow(old_shadow, d, s);
    imageStore(shadow_buffer, image_coord, uvec4(new_shadow));
    endInvocationInterlockARB();
)";
        } else {
            out += R"(
    uint old = imageLoad(shadow_buffer, image_coord).x;
    uint new1;
    uint old2;
    do {
        old2 = old;
        new1 = UpdateShadow(old, d, s);
    } while ((old = imageAtomicCompSwap(shadow_buffer, image_coord, old, new1)) != old2);
)";
        }
    } else {
        if (variant.write_depth) {
            out += "    gl_FragDepth = depth;\n";
        }
        out += "    combiner_output = byteround(combiner_output);\n"
               "    color = combiner_output;\n";
    }
    // Logic op (WriteLogicOp): only Clear/Set/Copy/CopyInverted are emulated.
    out += R"(
    uint lop = logic_op();
    if (lop == 0u) color = vec4(0);
    else if (lop == 4u) color = vec4(1);
    else if (lop == 5u) color = vec4(1.0) - color;
}
)";
    return out;
}

} // namespace Pica::Shader::Generator::Uber
