// NanoCtr3d - see NanoCtr3d.h. Port of /work/3ds/web/app/src/gfx3d (resource.ts, model.ts,
// animation.ts, curve.ts, material.ts, shader.ts, renderer3d.ts, camera.ts, math.ts).
#include "NanoCtr3d.h"

#include <EGL/egl.h>

#include <GLES2/gl2ext.h>

#include <algorithm>
#include <cmath>
#include <cstring>

#include <log/log.h>

#include "stb_image.h"

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

// ================================================================== math

void mat4Mul(M4& out, const M4& a, const M4& b) {
    float r[16];
    for (int c = 0; c < 4; c++)
        for (int row = 0; row < 4; row++) {
            float s = 0;
            for (int k = 0; k < 4; k++) s += a.m[k * 4 + row] * b.m[c * 4 + k];
            r[c * 4 + row] = s;
        }
    memcpy(out.m, r, sizeof(r));
}

void mat4Transform(M4& out, const V3& s, const V3& r, const V3& t) {
    const float cx = cosf(r[0]), sx = sinf(r[0]), cy = cosf(r[1]), sy = sinf(r[1]), cz = cosf(r[2]), sz = sinf(r[2]);
    float* o = out.m;
    o[0] = cy * cz * s[0]; o[1] = cy * sz * s[0]; o[2] = -sy * s[0]; o[3] = 0;
    o[4] = (sx * sy * cz - cx * sz) * s[1]; o[5] = (sx * sy * sz + cx * cz) * s[1]; o[6] = sx * cy * s[1]; o[7] = 0;
    o[8] = (cx * sy * cz + sx * sz) * s[2]; o[9] = (cx * sy * sz - sx * cz) * s[2]; o[10] = cx * cy * s[2]; o[11] = 0;
    o[12] = t[0]; o[13] = t[1]; o[14] = t[2]; o[15] = 1;
}

static V3 transformPoint(const M4& m, const V3& p) {
    return {m.m[0] * p[0] + m.m[4] * p[1] + m.m[8] * p[2] + m.m[12],
            m.m[1] * p[0] + m.m[5] * p[1] + m.m[9] * p[2] + m.m[13],
            m.m[2] * p[0] + m.m[6] * p[1] + m.m[10] * p[2] + m.m[14]};
}

static V3 vsub(const V3& a, const V3& b) { return {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
static float vdot(const V3& a, const V3& b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
static V3 vcross(const V3& a, const V3& b) { return {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
static V3 vnorm(const V3& a) {
    const float l = sqrtf(vdot(a, a));
    return l == 0 ? V3{0, 0, 0} : V3{a[0] / l, a[1] / l, a[2] / l};
}

void Camera3d::update() {
    const V3 z = vnorm(vsub(eye, target));
    const V3 up{-sinf(twist), cosf(twist), 0};
    const V3 x = vnorm(vcross(up, z));
    const V3 y = vcross(z, x);
    float* o = view.m;
    o[0] = x[0]; o[4] = x[1]; o[8] = x[2];
    o[1] = y[0]; o[5] = y[1]; o[9] = y[2];
    o[2] = z[0]; o[6] = z[1]; o[10] = z[2];
    o[3] = o[7] = o[11] = 0;
    o[12] = -vdot(x, eye); o[13] = -vdot(y, eye); o[14] = -vdot(z, eye); o[15] = 1;
    const float f = 1.0f / tanf(fovY / 2);
    float* p = projection.m;
    memset(p, 0, sizeof(projection.m));
    p[0] = f / aspect;
    p[5] = f;
    p[10] = (far + near) / (near - far);
    p[11] = -1;
    p[14] = (2 * far * near) / (near - far);
}

static void normalMatrix(float out[9], const M4& mm) {
    const float* m = mm.m;
    const float a00 = m[0], a01 = m[1], a02 = m[2], a10 = m[4], a11 = m[5], a12 = m[6], a20 = m[8], a21 = m[9], a22 = m[10];
    const float b01 = a22 * a11 - a12 * a21, b11 = -a22 * a10 + a12 * a20, b21 = a21 * a10 - a11 * a20;
    const float det = a00 * b01 + a01 * b11 + a02 * b21;
    const float inv = det == 0 ? 0 : 1 / det;
    out[0] = b01 * inv; out[1] = b11 * inv; out[2] = b21 * inv;
    out[3] = (-a22 * a01 + a02 * a21) * inv; out[4] = (a22 * a00 - a02 * a20) * inv; out[5] = (-a21 * a00 + a01 * a20) * inv;
    out[6] = (a12 * a01 - a02 * a11) * inv; out[7] = (-a12 * a00 + a02 * a10) * inv; out[8] = (a11 * a00 - a01 * a10) * inv;
}

// ================================================================== curves (curve.ts)

static float wrapFrame(float frame, float start, float end, const std::string& mode) {
    const float length = end - start;
    if (length <= 0 || (frame >= start && frame <= end)) return frame;
    if (mode == "repeat" || mode == "relativeRepeat") {
        float off = fmodf(frame - start, length);
        if (off < 0) off += length;
        return start + off;
    }
    if (mode == "mirror") {
        float off = fmodf(frame - start, 2 * length);
        if (off < 0) off += 2 * length;
        return start + (off > length ? 2 * length - off : off);
    }
    return std::clamp(frame, start, end);
}

static float hermite(float v0, float s0, float v1, float s1, float d, float o) {
    const float t = o / d, t2 = t * t, t3 = t2 * t;
    return v0 * (2 * t3 - 3 * t2 + 1) + v1 * (-2 * t3 + 3 * t2) + s0 * d * (t3 - 2 * t2 + t) + s1 * d * (t3 - t2);
}

float CgfxCurve::evaluate(float frame) const {
    if (hasConstant) return constant;
    if (segments.empty()) return 0;
    frame = wrapFrame(frame, startFrame, endFrame, frame < startFrame ? preRepeat : postRepeat);
    const Segment* seg = &segments[0];
    for (const Segment& s : segments) if (frame >= s.startFrame) seg = &s;
    if (seg->hasConstant) return seg->constant;
    const auto& keys = seg->keys;
    if (keys.empty()) return 0;
    if (frame <= keys[0][0]) return keys[0].size() > 1 ? keys[0][1] : 0;
    const auto& last = keys.back();
    if (frame >= last[0]) return last.size() > 1 ? last[1] : 0;
    int lo = 0, hi = (int)keys.size() - 2;
    while (lo < hi) {
        const int mid = (lo + hi + 1) >> 1;
        if (keys[mid][0] <= frame) lo = mid; else hi = mid - 1;
    }
    const auto& k0 = keys[lo];
    const auto& k1 = keys[lo + 1];
    const float f0 = k0[0], f1 = k1[0], v0 = k0[1], v1 = k1[1];
    if (seg->interpolation == 0) return v0;
    if (seg->interpolation == 1) return v0 + (v1 - v0) * (frame - f0) / (f1 - f0);
    const float outSlope = k0.size() >= 4 ? k0[3] : (k0.size() > 2 ? k0[2] : 0);
    const float inSlope = k1.size() > 2 ? k1[2] : 0;
    return hermite(v0, outSlope, v1, inSlope, f1 - f0, frame - f0);
}

// ================================================================== parsing

static V4 v4(const BjValue& v, V4 def = {0, 0, 0, 0}) {
    if (!v.isArray()) return def;
    return {v[0u].asFloat(def[0]), v[1u].asFloat(def[1]), v[2u].asFloat(def[2]), v[3u].asFloat(def[3])};
}
static V3 v3(const BjValue& v, V3 def = {0, 0, 0}) {
    if (!v.isArray()) return def;
    return {v[0u].asFloat(def[0]), v[1u].asFloat(def[1]), v[2u].asFloat(def[2])};
}

static CgfxCurve parseCurve(const BjValue& c) {
    CgfxCurve out;
    if (c["constant"].isNumber()) { out.hasConstant = true; out.constant = c["constant"].asFloat(); return out; }
    for (BjValue s : c["segments"]) {
        CgfxCurve::Segment seg;
        seg.startFrame = s["startFrame"].asFloat();
        seg.endFrame = s["endFrame"].asFloat();
        std::string_view in = s["interpolation"].asString();
        seg.interpolation = in == "step" ? 0 : in == "linear" ? 1 : 2;
        if (s["constant"].isNumber()) { seg.hasConstant = true; seg.constant = s["constant"].asFloat(); }
        for (BjValue k : s["keys"]) {
            std::vector<float> key;
            for (BjValue x : k) key.push_back(x.asFloat());
            seg.keys.push_back(std::move(key));
        }
        out.segments.push_back(std::move(seg));
    }
    out.startFrame = c["startFrame"].isNumber() ? c["startFrame"].asFloat() : (out.segments.empty() ? 0 : out.segments.front().startFrame);
    out.endFrame = c["endFrame"].isNumber() ? c["endFrame"].asFloat() : (out.segments.empty() ? 0 : out.segments.back().endFrame);
    out.preRepeat = c["preRepeat"].str();
    out.postRepeat = c["postRepeat"].str();
    return out;
}

static CgfxAnimation parseAnimation(const BjValue& a) {
    CgfxAnimation out;
    out.name = a["name"].str();
    out.loop = a["loopMode"].asString() == "loop";
    out.frameCount = a["frameCount"].asFloat();
    for (BjValue e : a["elements"]) {
        CgfxAnimElement el;
        el.name = e["name"].str();
        el.target = e["target"].str();
        el.type = e["type"].str();
        BjValue curves = e["curves"];
        if (curves.isObject())
            for (auto it = curves.begin(); it != curves.end(); ++it) el.curves[std::string(it.key())] = parseCurve(*it);
        if (e["curve"].isObject()) { el.hasCurve = true; el.curve = parseCurve(e["curve"]); }
        for (BjValue t : e["textures"]) el.textures.push_back(t.str());
        out.elements.push_back(std::move(el));
    }
    return out;
}

static GLenum compareFn(std::string_view n) {
    if (n == "never") return GL_NEVER;
    if (n == "equal") return GL_EQUAL;
    if (n == "notEqual") return GL_NOTEQUAL;
    if (n == "less") return GL_LESS;
    if (n == "lessOrEqual") return GL_LEQUAL;
    if (n == "greater") return GL_GREATER;
    if (n == "greaterOrEqual") return GL_GEQUAL;
    return GL_ALWAYS;
}

static GLenum stencilOp(std::string_view n) {
    if (n == "zero") return GL_ZERO;
    if (n == "replace") return GL_REPLACE;
    if (n == "increment") return GL_INCR;
    if (n == "decrement") return GL_DECR;
    if (n == "invert") return GL_INVERT;
    if (n == "incrementWrap") return GL_INCR_WRAP;
    if (n == "decrementWrap") return GL_DECR_WRAP;
    return GL_KEEP;
}

static GLenum blendEq(std::string_view n) {
    if (n == "subtract") return GL_FUNC_SUBTRACT;
    if (n == "reverseSubtract") return GL_FUNC_REVERSE_SUBTRACT;
    if (n == "min") return GL_MIN_EXT;
    if (n == "max") return GL_MAX_EXT;
    return GL_FUNC_ADD;
}

static GLenum blendFn(std::string_view n) {
    if (n == "zero") return GL_ZERO;
    if (n == "srcColor") return GL_SRC_COLOR;
    if (n == "oneMinusSrcColor") return GL_ONE_MINUS_SRC_COLOR;
    if (n == "dstColor") return GL_DST_COLOR;
    if (n == "oneMinusDstColor") return GL_ONE_MINUS_DST_COLOR;
    if (n == "srcAlpha") return GL_SRC_ALPHA;
    if (n == "oneMinusSrcAlpha") return GL_ONE_MINUS_SRC_ALPHA;
    if (n == "dstAlpha") return GL_DST_ALPHA;
    if (n == "oneMinusDstAlpha") return GL_ONE_MINUS_DST_ALPHA;
    if (n == "constantColor") return GL_CONSTANT_COLOR;
    if (n == "oneMinusConstantColor") return GL_ONE_MINUS_CONSTANT_COLOR;
    if (n == "constantAlpha") return GL_CONSTANT_ALPHA;
    if (n == "oneMinusConstantAlpha") return GL_ONE_MINUS_CONSTANT_ALPHA;
    if (n == "srcAlphaSaturate") return GL_SRC_ALPHA_SATURATE;
    return GL_ONE;
}

static GLint wrapMode(std::string_view n) {
    if (n == "repeat") return GL_REPEAT;
    if (n == "mirroredRepeat") return GL_MIRRORED_REPEAT;
    return GL_CLAMP_TO_EDGE;
}

static const char* kColorNames[11] = {"emission", "ambient", "diffuse", "specular0", "specular1",
                                      "constant0", "constant1", "constant2", "constant3", "constant4", "constant5"};
static const char* kLutSlots[6] = {"distribution0", "distribution1", "fresnel", "reflectanceR", "reflectanceG", "reflectanceB"};

static bool lutSupported(int config, int slot) {
    switch (slot) {
        case 0: return config != 1;
        case 1: return config != 0 && config != 1 && config != 5;
        case 2: return config != 0 && config != 2 && config != 4;
        case 3: return config != 3;
        default: return config == 4 || config == 5 || config == 7;
    }
}

static bool lutActive(const CgfxMaterialDef& m, int slot) {
    if (!m.lighting) return false;
    bool enabled = false;
    switch (slot) {
        case 0: enabled = m.flags.count("distribution0") != 0; break;
        case 1: enabled = m.flags.count("distribution1") != 0; break;
        case 2: enabled = m.fresnel != "none" && !m.fresnel.empty(); break;
        default: enabled = m.flags.count("reflection") != 0; break;
    }
    return enabled && lutSupported(m.layerConfig, slot) && m.luts[slot].present && !m.luts[slot].sampler.empty();
}

static CgfxMaterialDef parseMaterial(const BjValue& j) {
    CgfxMaterialDef m;
    m.name = j["name"].str();
    for (BjValue f : j["flags"]) if (f.asString() == "fragmentLighting") m.lighting = true;
    m.texCoordConfig = j["texCoordConfig"].str();
    std::string_view tr = j["translucency"].asString();
    m.translucency = tr == "translucent" ? 1 : tr == "subtractive" ? 2 : tr == "additive" ? 3 : 0;
    for (int i = 0; i < 11; i++) m.colors[i] = v4(j["colors"][kColorNames[i]]);
    std::string_view cull = j["rasterization"]["cullMode"].asString();
    m.cull = cull == "none" ? 0 : cull == "front" ? 2 : 1;
    BjValue fo = j["fragmentOperation"];
    m.depthFlags = fo["depth"]["flags"].asInt();
    m.depthFunc = compareFn(fo["depth"]["function"].asString());
    BjValue bl = fo["blend"];
    std::string_view mode = bl["mode"].asString();
    m.blendOn = !(mode == "none" || mode == "logicOp" || !bl["enabled"].asBool());
    m.eqColor = blendEq(bl["colorEquation"].asString());
    m.eqAlpha = blendEq(bl["alphaEquation"].asString());
    m.srcColor = blendFn(bl["colorSrc"].asString());
    m.dstColor = blendFn(bl["colorDst"].asString());
    m.srcAlpha = blendFn(bl["alphaSrc"].asString());
    m.dstAlpha = blendFn(bl["alphaDst"].asString());
    V4 cc = v4(bl["constantColor"], {0, 0, 0, 255});
    m.blendConstant = {cc[0] / 255, cc[1] / 255, cc[2] / 255, cc[3] / 255};
    BjValue st = fo["stencil"];
    m.stencilOn = st["testEnabled"].asBool();
    m.stencilFunc = compareFn(st["function"].asString());
    m.stencilRef = st["reference"].asInt();
    m.stencilMask = st["mask"].asInt();
    m.stencilWriteMask = st["writeMask"].asInt();
    m.sFail = stencilOp(st["failOp"].asString());
    m.sZFail = stencilOp(st["zFailOp"].asString());
    m.sZPass = stencilOp(st["zPassOp"].asString());
    for (BjValue c : j["textureCoordinators"]) {
        CgfxCoordinator co;
        co.sourceIndex = c["sourceIndex"].asInt();
        co.uv = c["mapping"].asString() == "uv";
        co.maya = c["transformType"].asString() == "maya";
        co.sx = c["scale"][0u].asFloat(1);
        co.sy = c["scale"][1u].asFloat(1);
        co.rotation = c["rotation"].asFloat();
        co.tx = c["translation"][0u].asFloat();
        co.ty = c["translation"][1u].asFloat();
        m.coordinators.push_back(co);
    }
    BjValue mappers = j["textureMappers"];
    for (uint32_t i = 0; i < 3; i++) {
        BjValue mp = mappers[i];
        if (!mp.isObject()) continue;
        CgfxMapper& o = m.mappers[i];
        o.valid = true;
        o.texture = mp["texture"].str();
        o.wrapS = wrapMode(mp["wrapS"].asString());
        o.wrapT = wrapMode(mp["wrapT"].asString());
        o.borderS = mp["wrapS"].asString() == "clampToBorder";
        o.borderT = mp["wrapT"].asString() == "clampToBorder";
        o.mag = mp["magFilter"].asString() == "nearest" ? GL_NEAREST : GL_LINEAR;
        o.min = mp["minFilter"].asString() == "nearest" ? GL_NEAREST : GL_LINEAR;
        V4 b = v4(mp["borderColor"], {0, 0, 0, 255});
        o.border = {b[0] / 255, b[1] / 255, b[2] / 255, b[3] / 255};
    }
    BjValue fs = j["fragmentShader"];
    for (BjValue f : fs["flags"]) m.flags.insert(f.str());
    m.layerConfig = fs["layerConfig"].asInt();
    m.fresnel = fs["fresnel"].str();
    for (int s = 0; s < 6; s++) {
        BjValue l = fs["lookupTables"][kLutSlots[s]];
        if (!l.isObject()) continue;
        m.luts[s].present = true;
        m.luts[s].input = l["input"].str();
        m.luts[s].scale = l["scale"].asFloat(1);
        m.luts[s].table = l["table"].isString() ? l["table"].str() : std::string();
        m.luts[s].sampler = l["sampler"].isString() ? l["sampler"].str() : std::string();
    }
    for (BjValue s : fs["stages"]) {
        CgfxMaterialDef::Stage stg;
        stg.constant = s["constant"].asInt();
        for (uint32_t i = 0; i < 3; i++) {
            stg.colorSources[i] = s["colorSources"][i].str();
            stg.alphaSources[i] = s["alphaSources"][i].str();
            stg.colorOperands[i] = s["colorOperands"][i].str();
            stg.alphaOperands[i] = s["alphaOperands"][i].str();
        }
        stg.colorCombiner = s["colorCombiner"].str();
        stg.alphaCombiner = s["alphaCombiner"].str();
        stg.colorScale = s["colorScale"].asFloat(1);
        stg.alphaScale = s["alphaScale"].asFloat(1);
        m.stages.push_back(std::move(stg));
    }
    m.alphaTest = fs["alphaTest"]["enabled"].asBool();
    m.alphaFunction = fs["alphaTest"]["function"].str();
    m.alphaReference = fs["alphaTest"]["reference"].asFloat() / 255.0f;
    BjValue bi = fs["bufferInput"];
    for (uint32_t i = 0; i < 6; i++) {
        m.bufferColorIn[i] = bi[i]["color"].asBool();
        m.bufferAlphaIn[i] = bi[i]["alpha"].asBool();
    }
    if (fs["bufferColorBytes"].isArray()) {
        V4 b = v4(fs["bufferColorBytes"]);
        m.bufferColor = {b[0] / 255, b[1] / 255, b[2] / 255, b[3] / 255};
    } else {
        m.bufferColor = v4(fs["bufferColor"]);
    }
    // shader key: everything that changes the generated code
    std::string key = m.lighting ? "L" : "U";
    for (int s = 0; s < 6; s++) if (lutActive(m, s)) key += "l" + std::to_string(s) + m.luts[s].input;
    key += "|" + m.fresnel + "|";
    for (const std::string& f : m.flags) if (f.rfind("geometric", 0) == 0 || f == "clampHighlight") key += f + ",";
    for (const auto& s : m.stages) {
        key += "#" + std::to_string(s.constant);
        for (int i = 0; i < 3; i++) key += s.colorSources[i] + s.colorOperands[i] + s.alphaSources[i] + s.alphaOperands[i];
        key += s.colorCombiner + s.alphaCombiner + std::to_string(s.colorScale) + std::to_string(s.alphaScale);
    }
    for (int i = 0; i < 6; i++) key += m.bufferColorIn[i] ? "c" : "-", key += m.bufferAlphaIn[i] ? "a" : "-";
    if (m.alphaTest) key += "T" + m.alphaFunction;
    for (int u = 0; u < 3; u++) key += m.mappers[u].valid && m.mappers[u].borderS ? "S" : "s", key += m.mappers[u].valid && m.mappers[u].borderT ? "T" : "t";
    m.shaderKey = key;
    return m;
}

// ================================================================== resource (resource.ts)

ModelResource::~ModelResource() { releaseGl(); }

void ModelResource::releaseGl() {
    if (!mBuffers.empty()) glDeleteBuffers((GLsizei)mBuffers.size(), mBuffers.data());
    mBuffers.clear();
    for (auto& t : textures)
        if (t.second && t.second->owned && t.second->id) { glDeleteTextures(1, &t.second->id); t.second->id = 0; }
    textures.clear();
    if (lutTexture) glDeleteTextures(1, &lutTexture);
    lutTexture = 0;
    primitives.clear();
}

static std::shared_ptr<GpuTex> uploadPng(const uint8_t* d, size_t n) {
    int w = 0, h = 0, c = 0;
    stbi_uc* px = stbi_load_from_memory(d, (int)n, &w, &h, &c, 4);
    if (!px) return nullptr;
    auto t = std::make_shared<GpuTex>();
    glGenTextures(1, &t->id);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    stbi_image_free(px);
    t->width = w;
    t->height = h;
    return t;
}

bool ModelResource::load(Pack& pack, Pack* shared) {
    const BjDoc* doc = pack.doc("cgfx");
    if (!doc) return false;
    BjValue root = doc->root();
    BjValue model = root["models"][0u];
    for (BjValue a : root["skeletalAnimations"]) skeletal.push_back(parseAnimation(a));
    for (BjValue a : root["materialAnimations"]) material.push_back(parseAnimation(a));
    for (BjValue l : root["lights"]) {
        if (!l["isEnabled"].asBool()) continue;
        light.present = true;
        light.direction = v3(l["direction"], {0, 0, -1});
        V4 a = v4(l["colors"]["ambient"]), d = v4(l["colors"]["diffuse"]);
        V4 s0 = v4(l["colors"]["specular0"]), s1 = v4(l["colors"]["specular1"]);
        light.ambient = {a[0], a[1], a[2]};
        light.diffuse = {d[0], d[1], d[2]};
        light.specular0 = {s0[0], s0[1], s0[2]};
        light.specular1 = {s1[0], s1[1], s1[2]};
        break;
    }
    if (!model.isObject()) return true;   // a camera or texture-only file
    name = model["name"].str();
    for (BjValue m : model["materials"]) materials.push_back(parseMaterial(m));
    for (BjValue m : model["meshes"]) {
        Mesh me;
        me.shape = m["shapeIndex"].asInt();
        me.material = m["materialIndex"].asInt();
        me.visible = m["isVisible"].asBool(true);
        me.priority = m["renderPriority"].asInt();
        me.node = m["meshNodeName"].isString() ? m["meshNodeName"].str() : std::string();
        meshes.push_back(me);
    }
    for (BjValue s : model["shapes"]) shapeCenters.push_back(v3(s["boundingBox"]["center"]));
    for (BjValue b : model["skeleton"]["bones"]) {
        CgfxBone bone;
        bone.name = b["name"].str();
        bone.index = b["index"].asInt();
        bone.parent = b["parentIndex"].asInt(-1);
        bone.scale = v3(b["scale"], {1, 1, 1});
        bone.rotation = v3(b["rotation"]);
        bone.translation = v3(b["translation"]);
        std::string_view bb = b["billboard"].asString();
        bone.billboard = (bb.empty() || bb == "off") ? 0 : (bb == "yAxial" || bb == "yAxialViewpoint") ? 2 : 1;
        bones.push_back(bone);
    }
    // textures (own, then the shared Textures file for the missing ones)
    for (BjValue t : root["textures"]) {
        const std::string tn = t["name"].str();
        const uint8_t* d; size_t n;
        if (pack.blob("tex/" + tn, &d, &n))
            if (auto g = uploadPng(d, n)) textures[tn] = g;
    }
    if (shared) {
        for (const CgfxMaterialDef& m : materials)
            for (const CgfxMapper& mp : m.mappers) {
                if (!mp.valid || textures.count(mp.texture)) continue;
                const uint8_t* d; size_t n;
                if (shared->blob("tex/" + mp.texture, &d, &n))
                    if (auto g = uploadPng(d, n)) textures[mp.texture] = g;
            }
    }
    // LUTs: one row of 256 entries per sampler, 16 bit values in red (high) and green (low)
    std::vector<std::vector<float>> rows;
    for (BjValue set : root["lookupTables"]) {
        const std::string setName = set["name"].str();
        for (BjValue s : set["samplers"]) {
            std::vector<float> vals;
            for (BjValue v : s["values"]) { if (vals.size() >= 256) break; vals.push_back(v.asFloat()); }
            vals.resize(256, 0.0f);
            lutRows[setName + "/" + s["name"].str()] = (int)rows.size();
            rows.push_back(std::move(vals));
        }
    }
    if (!rows.empty()) {
        lutCount = (int)rows.size();
        std::vector<uint8_t> px((size_t)256 * rows.size() * 4, 0);
        for (size_t r = 0; r < rows.size(); r++)
            for (int i = 0; i < 256; i++) {
                const float v = std::clamp(rows[r][i], 0.0f, 1.0f);
                const int q = (int)lroundf(v * 65535.0f);
                uint8_t* p = &px[(r * 256 + i) * 4];
                p[0] = (uint8_t)(q >> 8);
                p[1] = (uint8_t)(q & 255);
                p[2] = 0;
                p[3] = 255;
            }
        glGenTextures(1, &lutTexture);
        glBindTexture(GL_TEXTURE_2D, lutTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, (GLsizei)rows.size(), 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    }
    // geometry from the glTF export
    const BjDoc* gdoc = pack.doc("gltf");
    if (!gdoc) return true;
    BjValue g = gdoc->root();
    std::vector<std::pair<const uint8_t*, size_t>> bins;
    for (uint32_t i = 0; i < g["buffers"].size(); i++) {
        const uint8_t* d = nullptr; size_t n = 0;
        pack.blob("bin/" + std::to_string(i), &d, &n);
        bins.push_back({d, n});
    }
    std::map<std::string, int> boneByName;
    for (const CgfxBone& b : bones) boneByName[b.name] = b.index;
    BjValue nodes = g["nodes"];
    std::map<uint32_t, uint32_t> parentOf;
    for (uint32_t i = 0; i < nodes.size(); i++)
        for (BjValue c : nodes[i]["children"]) parentOf[(uint32_t)c.asInt()] = i;
    auto makeBuffer = [&](uint32_t accessorIndex, GLenum target, GLenum* type, int* comps, int* count) -> GLuint {
        BjValue acc = g["accessors"][accessorIndex];
        BjValue view = g["bufferViews"][(uint32_t)acc["bufferView"].asInt()];
        const auto& bin = bins[(size_t)view["buffer"].asInt()];
        if (!bin.first) return 0;
        const size_t start = (size_t)view["byteOffset"].asInt() + (size_t)acc["byteOffset"].asInt();
        const size_t len = (size_t)view["byteLength"].asInt() - (size_t)acc["byteOffset"].asInt();
        if (start + len > bin.second) return 0;
        GLuint b;
        glGenBuffers(1, &b);
        glBindBuffer(target, b);
        glBufferData(target, (GLsizeiptr)len, bin.first + start, GL_STATIC_DRAW);
        mBuffers.push_back(b);
        if (type) *type = (GLenum)acc["componentType"].asInt();
        if (comps) {
            std::string_view t = acc["type"].asString();
            *comps = t == "VEC2" ? 2 : t == "VEC3" ? 3 : t == "VEC4" ? 4 : 1;
        }
        if (count) *count = acc["count"].asInt();
        return b;
    };
    for (uint32_t ni = 0; ni < nodes.size(); ni++) {
        BjValue node = nodes[ni];
        if (!node["mesh"].isNumber()) continue;
        const std::string nodeName = node["name"].str();
        // mesh nodes are named <material>_<mesh>_<submesh>
        int meshIndex = -1;
        {
            std::vector<std::string> parts;
            size_t p = 0;
            while (true) {
                size_t q = nodeName.find('_', p);
                parts.push_back(nodeName.substr(p, q == std::string::npos ? std::string::npos : q - p));
                if (q == std::string::npos) break;
                p = q + 1;
            }
            if (parts.size() >= 2) meshIndex = atoi(parts[parts.size() - 2].c_str());
        }
        if (meshIndex < 0 || meshIndex >= (int)meshes.size()) continue;
        int boneIndex = -1;
        auto pit = parentOf.find(ni);
        if (pit != parentOf.end()) {
            auto bit = boneByName.find(nodes[pit->second]["name"].str());
            if (bit != boneByName.end()) boneIndex = bit->second;
        }
        for (BjValue prim : g["meshes"][(uint32_t)node["mesh"].asInt()]["primitives"]) {
            Primitive pr;
            pr.mesh = meshIndex;
            pr.material = meshes[meshIndex].material;
            pr.bone = boneIndex;
            static const char* kAttr[6] = {"POSITION", "NORMAL", "COLOR_0", "TEXCOORD_0", "TEXCOORD_1", "TEXCOORD_2"};
            for (int a = 0; a < 6; a++) {
                BjValue ai = prim["attributes"][kAttr[a]];
                if (!ai.isNumber()) continue;
                pr.buffers[a] = makeBuffer((uint32_t)ai.asInt(), GL_ARRAY_BUFFER, &pr.types[a], &pr.components[a], nullptr);
            }
            if (prim["indices"].isNumber()) {
                pr.index = makeBuffer((uint32_t)prim["indices"].asInt(), GL_ELEMENT_ARRAY_BUFFER, &pr.indexType, nullptr, &pr.indexCount);
            }
            primitives.push_back(pr);
        }
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    return true;
}

// ================================================================== material state (material.ts)

void MaterialState::reset() {
    for (int i = 0; i < 11; i++) colors[i] = source->colors[i];
    for (auto& o : overrides) if (o.first >= 0 && o.first < 11) colors[o.first] = o.second;
    coords.clear();
    for (const CgfxCoordinator& c : source->coordinators) coords.push_back({c.sx, c.sy, c.rotation, c.tx, c.ty});
    for (int i = 0; i < 3; i++) textures[i] = source->mappers[i].valid ? source->mappers[i].texture : std::string();
}

void MaterialState::textureMatrix(int index, float out[8]) const {
    if (index < 0 || index >= (int)coords.size() || index >= (int)source->coordinators.size()) {
        const float id[8] = {1, 0, 0, 0, 0, 1, 0, 0};
        memcpy(out, id, sizeof(id));
        return;
    }
    const Coord& c = coords[index];
    const float sn = sinf(c.rotation), cs = cosf(c.rotation);
    float ox, oy;
    if (source->coordinators[index].maya) {
        ox = c.sx * (0.5f * sn - 0.5f * cs + 0.5f - c.tx);
        oy = c.sy * (-0.5f * sn - 0.5f * cs + 0.5f - c.ty);
    } else {
        ox = c.sx * (-cs * (c.tx + 0.5f) + sn * (c.ty - 0.5f)) + 0.5f;
        oy = c.sy * (sn * (c.tx + 0.5f) + cs * (c.ty - 0.5f)) + 0.5f;
    }
    const float r[8] = {c.sx * cs, -c.sx * sn, 0, ox, c.sy * sn, c.sy * cs, 0, oy};
    memcpy(out, r, sizeof(r));
}

// ================================================================== model instance (model.ts, animation.ts)

ModelInstance::ModelInstance(std::shared_ptr<ModelResource> res) : resource(std::move(res)) {
    boneWorld.assign(resource->bones.size(), M4::identity());
    materials.resize(resource->materials.size());
    for (size_t i = 0; i < materials.size(); i++) {
        materials[i].source = &resource->materials[i];
        materials[i].reset();
    }
    resetPoses();
    computeBones();
}

MaterialState* ModelInstance::findMaterial(const std::string& n) {
    for (MaterialState& m : materials) if (m.source->name == n) return &m;
    return nullptr;
}

bool ModelInstance::play(const std::string& n, int loop, float speed, float frame) {
    bool found = false;
    auto start = [&](std::vector<Player>& list, const std::vector<CgfxAnimation>& anims) {
        for (const CgfxAnimation& a : anims) {
            if (a.name != n) continue;
            for (size_t i = 0; i < list.size(); i++)
                if (list[i].anim->name == n) { list.erase(list.begin() + i); break; }
            list.push_back({&a, frame, speed, loop < 0 ? a.loop : loop != 0});
            found = true;
        }
    };
    start(mSkeletal, resource->skeletal);
    start(mMaterial, resource->material);
    return found;
}

void ModelInstance::stop(const std::string& n) {
    for (auto* list : {&mSkeletal, &mMaterial})
        for (int i = (int)list->size() - 1; i >= 0; i--)
            if ((*list)[i].anim->name == n) list->erase(list->begin() + i);
}

bool ModelInstance::isPlaying(const std::string& n) const {
    for (const auto* list : {&mSkeletal, &mMaterial})
        for (const Player& p : *list)
            if (p.anim->name == n && (p.loop || p.frame < p.anim->frameCount)) return true;
    return false;
}

void ModelInstance::resetPoses() {
    mPoses.resize(resource->bones.size());
    for (size_t i = 0; i < resource->bones.size(); i++) {
        const CgfxBone& b = resource->bones[i];
        mPoses[i] = {b.scale, b.rotation, b.translation};
    }
}

void ModelInstance::computeBones() {
    M4 local;
    for (size_t i = 0; i < resource->bones.size(); i++) {
        const CgfxBone& b = resource->bones[i];
        const Pose& p = mPoses[i];
        mat4Transform(local, p.scale, p.rotation, p.translation);
        const M4& parent = (b.parent >= 0 && b.parent < (int)boneWorld.size()) ? boneWorld[b.parent] : transform;
        mat4Mul(boneWorld[i], parent, local);
    }
}

static const char* kTransformChannels[9] = {"scaleX", "scaleY", "scaleZ", "rotationX", "rotationY", "rotationZ",
                                           "translationX", "translationY", "translationZ"};

void ModelInstance::update(float frames) {
    resetPoses();
    for (const Player& p : mSkeletal) {
        for (const CgfxAnimElement& e : p.anim->elements) {
            if (e.type != "transform" || e.curves.empty()) continue;
            int bi = -1;
            for (size_t i = 0; i < resource->bones.size(); i++) if (resource->bones[i].name == e.name) { bi = (int)i; break; }
            if (bi < 0) continue;
            Pose& pose = mPoses[bi];
            for (int c = 0; c < 9; c++) {
                auto it = e.curves.find(kTransformChannels[c]);
                if (it == e.curves.end()) continue;
                V3& f = c < 3 ? pose.scale : c < 6 ? pose.rotation : pose.translation;
                f[c % 3] = it->second.evaluate(p.frame);
            }
        }
    }
    for (MaterialState& m : materials) m.reset();
    for (const Player& p : mMaterial) {
        for (const CgfxAnimElement& e : p.anim->elements) {
            MaterialState* st = findMaterial(e.target);
            if (!st) continue;
            const std::string& n = e.name;
            // .MaterialColor.<Name> | .TextureCoordinators[i].<Field> | .TextureMappers[i].Texture
            size_t mc = n.rfind(".MaterialColor.");
            size_t tc = n.rfind(".TextureCoordinators[");
            size_t tm = n.rfind(".TextureMappers[");
            if (mc != std::string::npos) {
                std::string cn = n.substr(mc + 15);
                if (!cn.empty()) cn[0] = (char)tolower(cn[0]);
                for (int ci = 0; ci < 11; ci++) {
                    if (cn != kColorNames[ci]) continue;
                    static const char* rgba[4] = {"r", "g", "b", "a"};
                    for (int k = 0; k < 4; k++) {
                        auto it = e.curves.find(rgba[k]);
                        if (it != e.curves.end()) st->colors[ci][k] = it->second.evaluate(p.frame);
                    }
                }
            } else if (tc != std::string::npos) {
                const int idx = atoi(n.c_str() + tc + 21);
                const size_t dot = n.find("].", tc);
                if (idx < 0 || idx >= (int)st->coords.size() || dot == std::string::npos) continue;
                const std::string field = n.substr(dot + 2);
                MaterialState::Coord& c = st->coords[idx];
                if (field == "Rotate") {
                    auto it = e.curves.find("value");
                    if (it != e.curves.end()) c.rotation = it->second.evaluate(p.frame);
                    else if (e.hasCurve) c.rotation = e.curve.evaluate(p.frame);
                } else {
                    const bool scale = field == "Scale";
                    auto ix = e.curves.find("x");
                    auto iy = e.curves.find("y");
                    if (ix != e.curves.end()) (scale ? c.sx : c.tx) = ix->second.evaluate(p.frame);
                    if (iy != e.curves.end()) (scale ? c.sy : c.ty) = iy->second.evaluate(p.frame);
                }
            } else if (tm != std::string::npos) {
                const int unit = atoi(n.c_str() + tm + 16);
                if (unit < 0 || unit > 2 || e.textures.empty()) continue;
                const int i = e.hasCurve ? (int)lroundf(e.curve.evaluate(p.frame)) : 0;
                st->textures[unit] = e.textures[std::clamp(i, 0, (int)e.textures.size() - 1)];
            }
        }
    }
    for (auto* list : {&mSkeletal, &mMaterial})
        for (Player& p : *list) {
            const float end = p.anim->frameCount;
            p.frame += p.speed * frames;
            if (p.loop && end > 0) p.frame = fmodf(p.frame, end);
            else if (p.frame > end) p.frame = end;
        }
    computeBones();
}

// ================================================================== shaders (shader.ts)

static const char* kVertex3d = R"(
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;
attribute vec2 a_uv0;
attribute vec2 a_uv1;
attribute vec2 a_uv2;
uniform mat4 u_modelView;
uniform mat4 u_projection;
uniform mat3 u_normalMatrix;
uniform vec4 u_texMatrix[6];
uniform vec3 u_texSource;
varying vec3 v_viewPosition;
varying vec3 v_viewNormal;
varying vec4 v_color;
varying vec2 v_tex0;
varying vec2 v_tex1;
varying vec2 v_tex2;
vec2 sourceCoord(float source, vec3 normal) {
  // glTF UVs were flipped (v' = 1 - v) on export; PICA coordinates are restored here.
  if (source < 0.5) return vec2(a_uv0.x, 1.0 - a_uv0.y);
  if (source < 1.5) return vec2(a_uv1.x, 1.0 - a_uv1.y);
  if (source < 2.5) return vec2(a_uv2.x, 1.0 - a_uv2.y);
  return normal.xy * 0.5 + 0.5;
}
vec2 coord(int unit, float source, vec3 normal) {
  vec4 p = vec4(sourceCoord(source, normal), 0.0, 1.0);
  return vec2(dot(u_texMatrix[unit * 2], p), dot(u_texMatrix[unit * 2 + 1], p));
}
void main() {
  vec4 viewPosition = u_modelView * vec4(a_position, 1.0);
  vec3 normal = normalize(u_normalMatrix * a_normal);
  v_viewPosition = viewPosition.xyz;
  v_viewNormal = normal;
  v_color = a_color;
  v_tex0 = coord(0, u_texSource.x, normal);
  v_tex1 = coord(1, u_texSource.y, normal);
  v_tex2 = coord(2, u_texSource.z, normal);
  gl_Position = u_projection * viewPosition;
}
)";

static std::string lutInputExpr(const std::string& in) {
    if (in == "vh") return "dot(viewVector, halfVector)";
    if (in == "nv") return "dot(normal, viewVector)";
    if (in == "ln") return "dot(lightVector, normal)";
    if (in == "sp") return "dot(-lightVector, vec3(0.0, 0.0, -1.0))";
    if (in == "cp") return "0.0";
    return "dot(normal, halfVector)";
}

static std::string src3d(const std::string& n, int constant) {
    if (n == "primaryColor") return "v_color";
    if (n == "fragmentPrimaryColor") return "primaryLit";
    if (n == "fragmentSecondaryColor") return "secondaryLit";
    if (n == "texture0") return "tex0";
    if (n == "texture1") return "tex1";
    if (n == "texture2") return "tex2";
    if (n == "previousBuffer") return "buf";
    if (n == "constant") return "u_konst[" + std::to_string(constant) + "]";
    if (n == "previous") return "previous";
    return "vec4(0.0)";
}

static std::string colorOp3d(const std::string& v, const std::string& op) {
    if (op == "oneMinusColor") return "(1.0 - " + v + ".rgb)";
    if (op == "alpha") return "vec3(" + v + ".a)";
    if (op == "oneMinusAlpha") return "vec3(1.0 - " + v + ".a)";
    if (op == "red") return "vec3(" + v + ".r)";
    if (op == "oneMinusRed") return "vec3(1.0 - " + v + ".r)";
    if (op == "green") return "vec3(" + v + ".g)";
    if (op == "oneMinusGreen") return "vec3(1.0 - " + v + ".g)";
    if (op == "blue") return "vec3(" + v + ".b)";
    if (op == "oneMinusBlue") return "vec3(1.0 - " + v + ".b)";
    return v + ".rgb";
}

static std::string alphaOp3d(const std::string& v, const std::string& op) {
    if (op == "oneMinusAlpha") return "(1.0 - " + v + ".a)";
    if (op == "red") return v + ".r";
    if (op == "oneMinusRed") return "(1.0 - " + v + ".r)";
    if (op == "green") return v + ".g";
    if (op == "oneMinusGreen") return "(1.0 - " + v + ".g)";
    if (op == "blue") return v + ".b";
    if (op == "oneMinusBlue") return "(1.0 - " + v + ".b)";
    return v + ".a";
}

static std::string combine3d(const std::string& mode, const std::string* a, const std::string& one) {
    if (mode == "modulate") return a[0] + " * " + a[1];
    if (mode == "add") return a[0] + " + " + a[1];
    if (mode == "addSigned") return a[0] + " + " + a[1] + " - " + one + " * 0.5";
    if (mode == "interpolate") return "mix(" + a[1] + ", " + a[0] + ", " + a[2] + ")";
    if (mode == "subtract") return a[0] + " - " + a[1];
    if (mode == "multAdd") return a[0] + " * " + a[1] + " + " + a[2];
    if (mode == "addMult") return "min(" + a[0] + " + " + a[1] + ", " + one + ") * " + a[2];
    if (mode == "dot3Rgb" || mode == "dot3Rgba") {
        if (one == "1.0") return "(4.0 * (" + a[0] + " - 0.5) * (" + a[1] + " - 0.5))";
        return one + " * (4.0 * dot(" + a[0] + " - 0.5, " + a[1] + " - 0.5))";
    }
    return a[0];
}

// The fragment program is specialised to the material: only the textures and lighting terms the
// combiner stages read are computed, border clamping is emitted only for samplers that use it, and
// LUT slots index their uniforms with constants (low end GPUs pay for every fetch and branch).
static std::string fragment3d(const CgfxMaterialDef& m) {
    std::string L;
    char buf[64];
    bool usesTex[3] = {false, false, false}, usesLit = false;
    for (const auto& st : m.stages)
        for (int k = 0; k < 3; k++)
            for (const std::string* n : {&st.colorSources[k], &st.alphaSources[k]}) {
                if (*n == "texture0") usesTex[0] = true;
                else if (*n == "texture1") usesTex[1] = true;
                else if (*n == "texture2") usesTex[2] = true;
                else if (*n == "fragmentPrimaryColor" || *n == "fragmentSecondaryColor") usesLit = true;
            }
    if (m.lighting && usesLit) {
        auto value = [&](int slot) {
            return "lut(u_lutRow[" + std::to_string(slot) + "], u_lutScale[" + std::to_string(slot) + "], " +
                   lutInputExpr(m.luts[slot].input) + ")";
        };
        const std::string d0 = lutActive(m, 0) ? value(0) : "1.0";
        const std::string d1 = lutActive(m, 1) ? value(1) : "1.0";
        const std::string rr = lutActive(m, 3) ? value(3) : "1.0";
        const std::string rg = lutActive(m, 4) ? value(4) : "reflection.r";
        const std::string rb = lutActive(m, 5) ? value(5) : "reflection.r";
        const bool g0 = m.flags.count("geometricFactor0") != 0, g1 = m.flags.count("geometricFactor1") != 0;
        const bool clampH = m.flags.count("clampHighlight") != 0;
        L += "  HP vec3 normal = normalize(gl_FrontFacing ? v_viewNormal : -v_viewNormal);\n";
        L += "  HP vec3 viewVector = normalize(-v_viewPosition);\n";
        L += "  HP vec3 lightVector = normalize(u_lightVector);\n";
        L += "  HP vec3 halfVector = normalize(viewVector + lightVector);\n";
        L += "  HP float lightDot = max(dot(lightVector, normal), 0.0);\n";
        L += "  HP float geometric = dot(halfVector, halfVector);\n";
        L += "  geometric = geometric == 0.0 ? 0.0 : min(lightDot / geometric, 1.0);\n";
        L += "  vec3 reflection = vec3(0.0);\n";
        L += "  reflection.r = " + rr + ";\n";
        L += "  reflection.g = " + rg + ";\n";
        L += "  reflection.b = " + rb + ";\n";
        L += "  vec3 specular0 = " + d0 + " * u_lightSpecular0" + (g0 ? " * geometric" : "") + ";\n";
        L += "  vec3 specular1 = " + d1 + " * reflection * u_lightSpecular1" + (g1 ? " * geometric" : "") + ";\n";
        L += std::string("  float highlight = ") + (clampH ? "sign(dot(lightVector, normal))" : "1.0") + ";\n";
        L += "  vec4 primaryLit = vec4(u_lightDiffuse * lightDot + u_lightAmbient + u_globalAmbient, 1.0);\n";
        L += "  vec4 secondaryLit = vec4((specular0 + specular1) * highlight, 1.0);\n";
        if (lutActive(m, 2)) {
            L += "  float fresnel = " + value(2) + ";\n";
            if (m.fresnel == "primary" || m.fresnel == "both") L += "  primaryLit.a = fresnel;\n";
            if (m.fresnel == "secondary" || m.fresnel == "both") L += "  secondaryLit.a = fresnel;\n";
        }
        L += "  primaryLit = clamp(primaryLit, 0.0, 1.0);\n  secondaryLit = clamp(secondaryLit, 0.0, 1.0);\n";
    } else {
        L += "  vec4 primaryLit = vec4(0.0);\n  vec4 secondaryLit = vec4(0.0);\n";
    }
    for (int u = 0; u < 3; u++) {
        const std::string n = std::to_string(u);
        if (!usesTex[u]) continue;
        const CgfxMapper& mp = m.mappers[u];
        const bool bs = mp.valid && mp.borderS, bt = mp.valid && mp.borderT;
        L += "  vec4 tex" + n + " = texture2D(u_tex" + n + ", v_tex" + n + ");\n";
        if (bs || bt) {
            std::string out;
            if (bs) out = "v_tex" + n + ".x < 0.0 || v_tex" + n + ".x > 1.0";
            if (bt) out += std::string(bs ? " || " : "") + "v_tex" + n + ".y < 0.0 || v_tex" + n + ".y > 1.0";
            L += "  if (" + out + ") tex" + n + " = u_border[" + n + "];\n";
        }
    }
    L += "  vec4 previous = vec4(0.0);\n  vec4 buf = vec4(0.0);\n  vec4 nextBuffer = u_bufferColor;\n";
    for (size_t i = 0; i < m.stages.size(); i++) {
        const auto& s = m.stages[i];
        std::string ca[3], aa[3];
        for (int k = 0; k < 3; k++) {
            ca[k] = colorOp3d(src3d(s.colorSources[k], s.constant), s.colorOperands[k]);
            aa[k] = alphaOp3d(src3d(s.alphaSources[k], s.constant), s.alphaOperands[k]);
        }
        snprintf(buf, sizeof(buf), "%.1f", s.colorScale);
        const std::string color = "clamp((" + combine3d(s.colorCombiner, ca, "vec3(1.0)") + ") * " + buf + ", 0.0, 1.0)";
        snprintf(buf, sizeof(buf), "%.1f", s.alphaScale);
        const std::string si = std::to_string(i);
        const std::string alpha = s.colorCombiner == "dot3Rgba" ? "stage" + si + ".r"
                                : "clamp((" + combine3d(s.alphaCombiner, aa, "1.0") + ") * " + buf + ", 0.0, 1.0)";
        L += "  vec3 stage" + si + " = " + color + ";\n";
        L += "  previous = vec4(stage" + si + ", " + alpha + ");\n";
        L += "  buf = nextBuffer;\n";
        if (i < 4 && m.bufferColorIn[i]) L += "  nextBuffer.rgb = previous.rgb;\n";
        if (i < 4 && m.bufferAlphaIn[i]) L += "  nextBuffer.a = previous.a;\n";
    }
    if (m.alphaTest) {
        const std::string& f = m.alphaFunction;
        std::string pass = "true";
        if (f == "never") pass = "false";
        else if (f == "equal") pass = "previous.a == u_alphaReference";
        else if (f == "notEqual") pass = "previous.a != u_alphaReference";
        else if (f == "less") pass = "previous.a < u_alphaReference";
        else if (f == "lessOrEqual") pass = "previous.a <= u_alphaReference";
        else if (f == "greater") pass = "previous.a > u_alphaReference";
        else if (f == "greaterOrEqual") pass = "previous.a >= u_alphaReference";
        L += "  if (!(" + pass + ")) discard;\n";
    }
    std::string s =
        // colour math at mediump (8 bit combiners), coordinates and geometry at highp
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\n#define HP highp\n#else\n#define HP mediump\n#endif\nprecision mediump float;\n"
        "varying HP vec3 v_viewPosition;\nvarying HP vec3 v_viewNormal;\nvarying vec4 v_color;\n"
        "varying HP vec2 v_tex0;\nvarying HP vec2 v_tex1;\nvarying HP vec2 v_tex2;\n"
        "uniform sampler2D u_tex0;\nuniform sampler2D u_tex1;\nuniform sampler2D u_tex2;\nuniform sampler2D u_lut;\n"
        "uniform vec4 u_konst[6];\nuniform vec4 u_bufferColor;\nuniform float u_alphaReference;\n"
        "uniform vec4 u_border[3];\n"
        "uniform HP vec3 u_lightVector;\nuniform vec3 u_lightAmbient;\nuniform vec3 u_lightDiffuse;\n"
        "uniform vec3 u_lightSpecular0;\nuniform vec3 u_lightSpecular1;\nuniform vec3 u_globalAmbient;\n"
        "uniform HP float u_lutRow[6];\nuniform HP float u_lutScale[6];\nuniform HP float u_lutRows;\n"

        "HP float lutAt(HP float row, HP float i) {\n"
        "  HP vec4 t = texture2D(u_lut, vec2((i + 0.5) / 256.0, (row + 0.5) / u_lutRows));\n"
        "  return (t.r * 255.0 * 256.0 + t.g * 255.0) / 65535.0;\n}\n"
        "HP float lut(HP float row, HP float scale, HP float x) {\n"
        "  HP float position = clamp(x, -1.0, 1.0) * 128.0;\n"
        "  HP float index = clamp(floor(position), -128.0, 127.0);\n"
        "  HP float delta = position - index;\n"
        "  HP float i = index + 128.0;\n"
        "  HP float next = min(i + 1.0, 255.0);\n"

        "  return mix(lutAt(row, i), lutAt(row, next), delta) * scale;\n}\n"
        "void main() {\n" + L + "  gl_FragColor = previous;\n}\n";
    return s;
}

// ================================================================== renderer (renderer3d.ts)

struct Renderer3d::Program {
    GLuint id = 0;
    std::map<std::string, GLint> u;
    GLint loc(const char* n) {
        auto it = u.find(n);
        if (it != u.end()) return it->second;
        const GLint l = glGetUniformLocation(id, n);
        u[n] = l;
        return l;
    }
};

static GLuint compile3d(GLenum type, const std::string& src) {
    GLuint sh = glCreateShader(type);
    const char* p = src.c_str();
    glShaderSource(sh, 1, &p, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024] = {0};
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        ALOGE("3d shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

Renderer3d::Renderer3d() = default;
Renderer3d::~Renderer3d() { releaseGl(); }

void Renderer3d::releaseGl() {
    for (auto& p : mPrograms) if (p.second && p.second->id) glDeleteProgram(p.second->id);
    mPrograms.clear();
    glDeleteFramebuffers(2, mFbos);
    glDeleteTextures(2, mColors);
    if (mDepth) glDeleteRenderbuffers(1, &mDepth);
    if (mStencil) glDeleteRenderbuffers(1, &mStencil);
    if (mWhite) glDeleteTextures(1, &mWhite);
    mFbos[0] = mFbos[1] = mColors[0] = mColors[1] = mDepth = mStencil = mWhite = 0;
    mW = mH = 0;
}

Renderer3d::Program* Renderer3d::program(const CgfxMaterialDef& m) {
    auto it = mPrograms.find(m.shaderKey);
    if (it != mPrograms.end()) return it->second.get();
    auto p = std::make_unique<Program>();
    GLuint vs = compile3d(GL_VERTEX_SHADER, kVertex3d), fs = compile3d(GL_FRAGMENT_SHADER, fragment3d(m));
    if (vs && fs) {
        p->id = glCreateProgram();
        glAttachShader(p->id, vs);
        glAttachShader(p->id, fs);
        static const char* kA[6] = {"a_position", "a_normal", "a_color", "a_uv0", "a_uv1", "a_uv2"};
        for (int i = 0; i < 6; i++) glBindAttribLocation(p->id, i, kA[i]);
        glLinkProgram(p->id);
        GLint ok = 0;
        glGetProgramiv(p->id, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[1024] = {0};
            glGetProgramInfoLog(p->id, sizeof(log), nullptr, log);
            ALOGE("3d program link failed (%s): %s", m.name.c_str(), log);
            glDeleteProgram(p->id);
            p->id = 0;
        } else {
            glUseProgram(p->id);
            glUniform1i(glGetUniformLocation(p->id, "u_tex0"), 0);
            glUniform1i(glGetUniformLocation(p->id, "u_tex1"), 1);
            glUniform1i(glGetUniformLocation(p->id, "u_tex2"), 2);
            glUniform1i(glGetUniformLocation(p->id, "u_lut"), 3);
        }
    }
    if (vs) glDeleteShader(vs);
    if (fs) glDeleteShader(fs);
    Program* out = p.get();
    mPrograms.emplace(m.shaderKey, std::move(p));
    return out;
}

// Two colour targets used in turn: a frame renders into the one the previous frame's composite
// is not reading, so the GPU never waits on (or copies) a texture still in use. Depth and stencil
// are shared and discarded after each pass.
void Renderer3d::ensureTarget(int w, int h) {
    if (mFbos[0] && w == mW && h == mH) return;
    glDeleteFramebuffers(2, mFbos);
    glDeleteTextures(2, mColors);
    if (mDepth) glDeleteRenderbuffers(1, &mDepth);
    if (mStencil) glDeleteRenderbuffers(1, &mStencil);
    mDepth = mStencil = 0;
    const char* ext = (const char*)glGetString(GL_EXTENSIONS);
    mPacked = ext && strstr(ext, "GL_OES_packed_depth_stencil");
    if (ext && strstr(ext, "GL_EXT_discard_framebuffer"))
        mDiscard = (PFNGLDISCARDFRAMEBUFFEREXTPROC)eglGetProcAddress("glDiscardFramebufferEXT");
    glGenRenderbuffers(1, &mDepth);
    glBindRenderbuffer(GL_RENDERBUFFER, mDepth);
    glRenderbufferStorage(GL_RENDERBUFFER, mPacked ? GL_DEPTH24_STENCIL8_OES : GL_DEPTH_COMPONENT16, w, h);
    if (!mPacked) {
        glGenRenderbuffers(1, &mStencil);
        glBindRenderbuffer(GL_RENDERBUFFER, mStencil);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, w, h);
    }
    glGenTextures(2, mColors);
    glGenFramebuffers(2, mFbos);
    for (int k = 0; k < 2; k++) {
        glBindTexture(GL_TEXTURE_2D, mColors[k]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, mFbos[k]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mColors[k], 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, mDepth);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, mPacked ? mDepth : mStencil);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) ALOGE("3d target incomplete");
    }
    glBindRenderbuffer(GL_RENDERBUFFER, 0);
    if (!mWhite) {
        glGenTextures(1, &mWhite);
        glBindTexture(GL_TEXTURE_2D, mWhite);
        const uint8_t w1[4] = {255, 255, 255, 255};
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, w1);
    }
    mW = w;
    mH = h;
}

static void billboard(M4& mm, int mode) {
    float* m = mm.m;
    const float sx = sqrtf(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    const float sy = sqrtf(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
    const float sz = sqrtf(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);
    V3 x{1, 0, 0}, y{0, 1, 0}, z{0, 0, 1};
    if (mode == 2) {
        const float len = sqrtf(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
        const float l = len == 0 ? 1 : len;
        y = {m[4] / l, m[5] / l, m[6] / l};
        const float zl = hypotf(y[0], y[1]) == 0 ? 1
                        : sqrtf((-y[0] * y[2]) * (-y[0] * y[2]) + (-y[1] * y[2]) * (-y[1] * y[2]) + (1 - y[2] * y[2]) * (1 - y[2] * y[2]));
        z = {(-y[0] * y[2]) / zl, (-y[1] * y[2]) / zl, (1 - y[2] * y[2]) / zl};
        x = vcross(y, z);
    }
    m[0] = x[0] * sx; m[1] = x[1] * sx; m[2] = x[2] * sx;
    m[4] = y[0] * sy; m[5] = y[1] * sy; m[6] = y[2] * sy;
    m[8] = z[0] * sz; m[9] = z[1] * sz; m[10] = z[2] * sz;
}

GLuint Renderer3d::render(const Camera3d& cam, const std::vector<ModelInstance*>& models, int w, int h, const float clear[3],
                          const std::function<void(int w, int h)>& underlay) {
    struct Item { ModelInstance* model; const ModelResource::Primitive* prim; MaterialState* state; int order, priority; float depth; int seq; };
    std::vector<Item> items;
    int seq = 0;
    for (ModelInstance* mi : models) {
        if (!mi->visible) continue;
        ModelResource& r = *mi->resource;
        for (const auto& p : r.primitives) {
            if (p.mesh < 0 || p.mesh >= (int)r.meshes.size() || p.material < 0 || p.material >= (int)mi->materials.size()) continue;
            const auto& mesh = r.meshes[p.mesh];
            if (!mesh.visible || mi->hiddenMeshes.count(p.mesh)) continue;
            const M4& world = mi->boneMatrix(p.bone);
            const V3 center = mesh.shape < (int)r.shapeCenters.size() ? r.shapeCenters[mesh.shape] : V3{0, 0, 0};
            const V3 vc = transformPoint(cam.view, transformPoint(world, center));
            MaterialState* st = &mi->materials[p.material];
            items.push_back({mi, &p, st, st->source->translucency, mesh.priority, vc[2], seq++});
        }
    }
    std::stable_sort(items.begin(), items.end(), [](const Item& a, const Item& b) {
        if (a.model->layer != b.model->layer) return a.model->layer < b.model->layer;
        if (a.order != b.order) return a.order < b.order;
        if (a.priority != b.priority) return a.priority < b.priority;
        if (a.depth != b.depth) return a.order == 0 ? b.depth < a.depth : a.depth < b.depth;
        return a.seq < b.seq;
    });
    GLint prevFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4];
    glGetIntegerv(GL_VIEWPORT, prevVp);
    ensureTarget(w, h);
    mCur ^= 1;
    glBindFramebuffer(GL_FRAMEBUFFER, mFbos[mCur]);
    glViewport(0, 0, w, h);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDepthMask(GL_TRUE);
    glStencilMask(0xff);
    glClearColor(clear[0], clear[1], clear[2], 1);
    glClearDepthf(1);
    glClearStencil(0);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    if (underlay) {
        underlay(w, h);
        // the 2D pass leaves its own state: the target, viewport and masks come back
        glBindFramebuffer(GL_FRAMEBUFFER, mFbos[mCur]);
        glViewport(0, 0, w, h);
        glDisable(GL_SCISSOR_TEST);
        glDepthMask(GL_TRUE);
        glStencilMask(0xff);
    }
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_FALSE);
    for (const Item& it : items) {
        const CgfxMaterialDef& m = *it.state->source;
        Program* prog = program(m);
        if (!prog || !prog->id) continue;
        glUseProgram(prog->id);
        ModelResource& r = *it.model->resource;
        M4 mv;
        mat4Mul(mv, cam.view, it.model->boneMatrix(it.prim->bone));
        if (it.prim->bone >= 0 && it.prim->bone < (int)r.bones.size() && r.bones[it.prim->bone].billboard)
            billboard(mv, r.bones[it.prim->bone].billboard);
        glUniformMatrix4fv(prog->loc("u_modelView"), 1, GL_FALSE, mv.m);
        glUniformMatrix4fv(prog->loc("u_projection"), 1, GL_FALSE, cam.projection.m);
        float nm[9];
        normalMatrix(nm, mv);
        glUniformMatrix3fv(prog->loc("u_normalMatrix"), 1, GL_FALSE, nm);
        // textures
        float mats[24], sources[3], borders[12];
        for (int unit = 0; unit < 3; unit++) {
            const int ci = unit < (int)m.texCoordConfig.size() ? m.texCoordConfig[unit] - '0' : unit;
            it.state->textureMatrix(ci, &mats[unit * 8]);
            const bool hasCoord = ci >= 0 && ci < (int)m.coordinators.size();
            sources[unit] = hasCoord ? (m.coordinators[ci].uv ? (float)m.coordinators[ci].sourceIndex : 3.0f) : 0.0f;
            const CgfxMapper& mp = m.mappers[unit];
            const std::string& tn = it.state->texture(unit);
            auto tex = tn.empty() ? r.textures.end() : r.textures.find(tn);
            glActiveTexture(GL_TEXTURE0 + unit);
            if (tex != r.textures.end() && tex->second && tex->second->id) {
                glBindTexture(GL_TEXTURE_2D, tex->second->id);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, mp.valid ? mp.wrapS : GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, mp.valid ? mp.wrapT : GL_CLAMP_TO_EDGE);
                const bool px = tex->second->pixelArt;
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, px ? GL_NEAREST : mp.valid ? mp.mag : GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, px ? GL_NEAREST : mp.valid ? mp.min : GL_LINEAR);
            } else {
                glBindTexture(GL_TEXTURE_2D, mWhite);
            }
            const V4 b = mp.valid ? mp.border : V4{0, 0, 0, 1};
            memcpy(&borders[unit * 4], b.data(), 16);
        }
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, r.lutTexture ? r.lutTexture : mWhite);
        glActiveTexture(GL_TEXTURE0);
        glUniform4fv(prog->loc("u_texMatrix"), 6, mats);
        glUniform3fv(prog->loc("u_texSource"), 1, sources);
        glUniform4fv(prog->loc("u_border"), 3, borders);
        float konst[24];
        for (int i = 0; i < 6; i++) memcpy(&konst[i * 4], it.state->colors[5 + i].data(), 16);
        glUniform4fv(prog->loc("u_konst"), 6, konst);
        glUniform4fv(prog->loc("u_bufferColor"), 1, m.bufferColor.data());
        glUniform1f(prog->loc("u_alphaReference"), m.alphaReference);
        // lighting
        const auto& light = r.light;
        const V3 dir = light.present ? light.direction : V3{0, 0, -1};
        const float* v = cam.view.m;
        float lv[3];
        for (int row = 0; row < 3; row++) lv[row] = -(v[row] * dir[0] + v[4 + row] * dir[1] + v[8 + row] * dir[2]);
        glUniform3fv(prog->loc("u_lightVector"), 1, lv);
        auto prod = [&](const V3& a, const V4& b, float* out) { for (int i = 0; i < 3; i++) out[i] = (light.present ? a[i] : 0) * b[i]; };
        float t3[3];
        prod(light.ambient, it.state->colors[1], t3); glUniform3fv(prog->loc("u_lightAmbient"), 1, t3);
        prod(light.diffuse, it.state->colors[2], t3); glUniform3fv(prog->loc("u_lightDiffuse"), 1, t3);
        prod(light.specular0, it.state->colors[3], t3); glUniform3fv(prog->loc("u_lightSpecular0"), 1, t3);
        prod(light.specular1, it.state->colors[4], t3); glUniform3fv(prog->loc("u_lightSpecular1"), 1, t3);
        glUniform3fv(prog->loc("u_globalAmbient"), 1, it.state->colors[0].data());
        float rows[6], scales[6];
        for (int s = 0; s < 6; s++) {
            const auto& l = m.luts[s];
            rows[s] = 0;
            if (l.present && !l.table.empty() && !l.sampler.empty()) {
                auto f = r.lutRows.find(l.table + "/" + l.sampler);
                if (f != r.lutRows.end()) rows[s] = (float)f->second;
            }
            scales[s] = l.present ? l.scale : 1.0f;
        }
        glUniform1fv(prog->loc("u_lutRow"), 6, rows);
        glUniform1fv(prog->loc("u_lutScale"), 6, scales);
        glUniform1f(prog->loc("u_lutRows"), (float)std::max(1, r.lutCount));
        // fixed function state
        if (m.cull == 0) glDisable(GL_CULL_FACE);
        else { glEnable(GL_CULL_FACE); glCullFace(m.cull == 2 ? GL_FRONT : GL_BACK); }
        if (m.depthFlags & 1) { glEnable(GL_DEPTH_TEST); glDepthFunc(m.depthFunc); }
        else glDisable(GL_DEPTH_TEST);
        glDepthMask((m.depthFlags & 2) ? GL_TRUE : GL_FALSE);
        if (!m.blendOn) glDisable(GL_BLEND);
        else {
            glEnable(GL_BLEND);
            glBlendEquationSeparate(m.eqColor, m.eqAlpha);
            glBlendFuncSeparate(m.srcColor, m.dstColor, m.srcAlpha, m.dstAlpha);
            glBlendColor(m.blendConstant[0], m.blendConstant[1], m.blendConstant[2], m.blendConstant[3]);
        }
        if (m.stencilOn) {
            glEnable(GL_STENCIL_TEST);
            glStencilFunc(m.stencilFunc, m.stencilRef, (GLuint)m.stencilMask);
            glStencilOp(m.sFail, m.sZFail, m.sZPass);
            glStencilMask((GLuint)m.stencilWriteMask);
        } else glDisable(GL_STENCIL_TEST);
        // geometry
        const auto& p = *it.prim;
        for (int a = 0; a < 6; a++) {
            if (p.buffers[a]) {
                glBindBuffer(GL_ARRAY_BUFFER, p.buffers[a]);
                glEnableVertexAttribArray(a);
                glVertexAttribPointer(a, p.components[a], p.types[a], GL_FALSE, 0, nullptr);
            } else {
                glDisableVertexAttribArray(a);
                if (a == 1) glVertexAttrib3f(1, 0, 0, 1);
                else if (a == 2) glVertexAttrib4f(2, 1, 1, 1, 1);
                else glVertexAttrib4f(a, 0, 0, 0, 1);
            }
        }
        if (p.index) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, p.index);
            glDrawElements(GL_TRIANGLES, p.indexCount, p.indexType, nullptr);
        }
    }
    // depth and stencil are not needed after the pass: a tiled GPU then never writes them out
    if (mDiscard) {
        const GLenum att[2] = {GL_DEPTH_ATTACHMENT, GL_STENCIL_ATTACHMENT};
        mDiscard(GL_FRAMEBUFFER, 2, att);
    }
    for (int a = 0; a < 6; a++) glDisableVertexAttribArray(a);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glDepthMask(GL_TRUE);
    glStencilMask(0xff);
    glBlendEquation(GL_FUNC_ADD);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    for (int u = 0; u < 4; u++) { glActiveTexture(GL_TEXTURE0 + u); glBindTexture(GL_TEXTURE_2D, 0); }
    glActiveTexture(GL_TEXTURE0);
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    return mColors[mCur];
}

}  // namespace ctr
}  // namespace android
