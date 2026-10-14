// NanoCtrLayout - layout templates, pane trees, animation, text layout and fonts.
// Port of /work/3ds/web/app/src/layout (see NanoCtrLayout.h).
#include "NanoCtrLayout.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

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

static void matIdentity(Mat4& o) {
    memset(o.m, 0, sizeof(o.m));
    o.m[0] = o.m[5] = o.m[10] = o.m[15] = 1;
}

// out = a * b (column major)
static void matMul(float* out, const float* a, const float* b) {
    float r[16];
    for (int c = 0; c < 4; c++)
        for (int row = 0; row < 4; row++) {
            float s = 0;
            for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
            r[c * 4 + row] = s;
        }
    memcpy(out, r, sizeof(r));
}

// NW4C pane matrix: T * Rz * Ry * Rx * S (MTX34RotXYZDeg), angles in degrees.
static void matPaneSrt(Mat4& o, const float* t, const float* rot, const float* s) {
    const float D = (float)M_PI / 180.0f;
    const float sx = sinf(rot[0] * D), cx = cosf(rot[0] * D);
    const float sy = sinf(rot[1] * D), cy = cosf(rot[1] * D);
    const float sz = sinf(rot[2] * D), cz = cosf(rot[2] * D);
    const float r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
    const float r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
    const float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
    float* m = o.m;
    m[0] = r00 * s[0]; m[1] = r10 * s[0]; m[2] = r20 * s[0]; m[3] = 0;
    m[4] = r01 * s[1]; m[5] = r11 * s[1]; m[6] = r21 * s[1]; m[7] = 0;
    m[8] = r02; m[9] = r12; m[10] = r22; m[11] = 0;
    m[12] = t[0]; m[13] = t[1]; m[14] = t[2]; m[15] = 1;
}

// ================================================================== templates

struct PaneProto {
    Pane pane;               // values (pointers unused)
    int parent = -1;
    std::vector<int> children;
};

struct GroupProto {
    std::string name;
    std::vector<std::string> panes;
    std::vector<int> children;
};

struct LayoutTemplate {
    float width = 0, height = 0;
    std::vector<std::string> textures;   // stems
    std::vector<std::string> fonts;
    std::vector<Material> materials;
    std::vector<PaneProto> panes;        // panes[0] is the root
    std::vector<GroupProto> groups;
};

struct AnimCurve {
    uint8_t type = 0;          // 0 constant 1 step 2 hermite
    std::vector<float> keys;   // frame, value, slope triples (slope 0 for step)
};

struct AnimTarget {
    int index = 0;
    int targetId = 0;
    AnimCurve curve;
};

struct AnimInfo {
    char tag[5] = {0};
    std::vector<AnimTarget> targets;
};

struct AnimContent {
    std::string name;
    bool material = false;
    std::vector<AnimInfo> infos;
};

struct AnimTemplate {
    std::string name;
    int frameSize = 1;
    bool loop = false;
    std::vector<std::string> textures;
    std::vector<AnimContent> contents;
    std::vector<std::string> groups;   // pat1
    bool childBinding = false;
};

static Rgba rgbaOf(const BjValue& v, Rgba def = {255, 255, 255, 255}) {
    if (!v.isArray() || v.size() < 4) return def;
    return {v[0u].asFloat(), v[1u].asFloat(), v[2u].asFloat(), v[3u].asFloat()};
}

static std::string stem(std::string_view s) {
    const size_t dot = s.rfind('.');
    return std::string(dot == std::string_view::npos ? s : s.substr(0, dot));
}

static TevSrc srcOf(std::string_view s) {
    if (s == "texture0") return TevSrc::Tex0;
    if (s == "texture1") return TevSrc::Tex1;
    if (s == "texture2") return TevSrc::Tex2;
    if (s == "texture3") return TevSrc::Tex3;
    if (s == "constant") return TevSrc::Constant;
    if (s == "primary") return TevSrc::Primary;
    if (s == "previous") return TevSrc::Previous;
    return TevSrc::Register;
}

static uint8_t rgbOpOf(std::string_view s) {
    static const char* k[] = {"rgb", "invRgb", "alpha", "invAlpha", "rrr", "invRrr", "ggg", "invGgg", "bbb", "invBbb"};
    for (uint8_t i = 0; i < 10; i++) if (s == k[i]) return i;
    return 0;
}

static uint8_t alphaOpOf(std::string_view s) {
    static const char* k[] = {"alpha", "invAlpha", "r", "invR", "g", "invG", "b", "invB"};
    for (uint8_t i = 0; i < 8; i++) if (s == k[i]) return i;
    return 0;
}

static TevCombiner combinerOf(const BjValue& j, uint32_t raw) {
    TevCombiner c;
    BjValue src = j["sources"], ops = j["operands"];
    for (uint32_t i = 0; i < 3; i++) c.src[i] = srcOf(src[i].asString());
    c.mode = (TevMode)std::min<uint32_t>((raw >> 24) & 0xf, 9);
    std::string_view sc = j["scale"].asString();
    c.scale = sc == "v4" ? 4 : sc == "v2" ? 2 : 1;
    c.saveToBuffer = j["savePrevReg"].asBool();
    // operands are decoded by the caller (rgb vs alpha tables)
    for (uint32_t i = 0; i < 3; i++) c.op[i] = 0;
    (void)ops;
    return c;
}

static GLint wrapOf(std::string_view s) {
    if (s == "repeat") return GL_REPEAT;
    if (s == "mirror") return GL_MIRRORED_REPEAT;
    return GL_CLAMP_TO_EDGE;
}

static AlphaFn alphaFnOf(std::string_view s) {
    static const char* k[] = {"never", "less", "lequal", "equal", "nequal", "gequal", "greater", "always"};
    for (uint8_t i = 0; i < 8; i++) if (s == k[i]) return (AlphaFn)i;
    return AlphaFn::Always;
}

static BlendOp blendOpOf(std::string_view s) {
    static const char* k[] = {"disable", "add", "subtract", "reverseSubtract", "selectMin", "selectMax"};
    for (uint8_t i = 0; i < 6; i++) if (s == k[i]) return (BlendOp)i;
    return BlendOp::Add;
}

static BlendFactor blendFactorOf(std::string_view s) {
    static const char* k[] = {"zero", "one", "dstColor", "invDstColor", "srcAlpha", "invSrcAlpha",
                              "dstAlpha", "invDstAlpha", "srcColor", "invSrcColor"};
    for (uint8_t i = 0; i < 10; i++) if (s == k[i]) return (BlendFactor)i;
    return BlendFactor::SrcAlpha;
}

static uint64_t fnv(const std::string& s) {
    uint64_t h = 1469598103934665603ull;
    for (unsigned char c : s) { h ^= c; h *= 1099511628211ull; }
    return h;
}

static Material materialOf(const BjValue& j, const std::vector<std::string>& textures) {
    Material m;
    m.name = j["name"].str();
    m.bufferColor = rgbaOf(j["tevColor"], {0, 0, 0, 0});
    BjValue kc = j["tevConstantColors"];
    for (uint32_t i = 0; i < 6; i++) m.konst[i] = rgbaOf(kc[i]);
    for (BjValue t : j["texMaps"]) {
        TexMap tm;
        const int idx = t["textureIndex"].asInt();
        tm.name = (idx >= 0 && idx < (int)textures.size()) ? textures[idx] : std::string();
        tm.wrapS = wrapOf(t["wrapS"].asString());
        tm.wrapT = wrapOf(t["wrapT"].asString());
        tm.minF = t["minFilter"].asString() == "near" ? GL_NEAREST : GL_LINEAR;
        tm.magF = t["magFilter"].asString() == "near" ? GL_NEAREST : GL_LINEAR;
        m.texMaps.push_back(std::move(tm));
    }
    for (BjValue t : j["texMatrices"]) {
        TexSrt s;
        s.tx = t["translate"][0u].asFloat();
        s.ty = t["translate"][1u].asFloat();
        s.rotate = t["rotate"].asFloat();
        s.sx = t["scale"][0u].asFloat(1);
        s.sy = t["scale"][1u].asFloat(1);
        m.texSrts.push_back(s);
    }
    BjValue gens = j["texCoordGens"];
    for (size_t i = 0; i < m.texMaps.size(); i++) {
        std::string_view src = gens[(uint32_t)i]["source"].asString();
        int set = (int)i;
        if (src.size() == 4 && src.substr(0, 3) == "tex" && src[3] >= '0' && src[3] <= '9') set = src[3] - '0';
        m.texCoordSources.push_back(set);
    }
    for (BjValue s : j["tevStages"]) {
        TevStage st;
        BjValue raw = s["raw"];
        st.rgb = combinerOf(s["rgb"], (uint32_t)raw[0u].asInt());
        st.alpha = combinerOf(s["alpha"], (uint32_t)raw[1u].asInt());
        for (uint32_t i = 0; i < 3; i++) {
            st.rgb.op[i] = rgbOpOf(s["rgb"]["operands"][i].asString());
            st.alpha.op[i] = alphaOpOf(s["alpha"]["operands"][i].asString());
        }
        st.konstColor = (uint8_t)std::clamp(s["konstColorSelect"].asInt() - 1, 0, 5);
        st.konstAlpha = (uint8_t)std::clamp(s["konstAlphaSelect"].asInt() - 1, 0, 5);
        m.stages.push_back(st);
    }
    BjValue ac = j["alphaCompare"];
    if (ac.isObject()) {
        m.alphaFn = alphaFnOf(ac["function"].asString());
        m.alphaRef = ac["reference"].asFloat();
    }
    BjValue bm = j["blendMode"];
    if (bm.isObject()) {
        m.blendOp = blendOpOf(bm["operation"].asString());
        m.blendSrc = blendFactorOf(bm["source"].asString());
        m.blendDst = blendFactorOf(bm["destination"].asString());
    }
    m.textureOnly = j["flags"]["textureOnly"].asBool();
    // shader key: texture count, alpha function, combiners
    std::string key = std::to_string(m.texMaps.size()) + "|" + std::to_string((int)m.alphaFn) + "|";
    for (const TevStage& st : m.stages) {
        auto ck = [&](const TevCombiner& c) {
            key += std::to_string((int)c.mode) + ":";
            for (int i = 0; i < 3; i++) key += std::to_string((int)c.src[i]) + "," + std::to_string(c.op[i]) + ";";
            key += std::to_string(c.scale) + (c.saveToBuffer ? "s" : "n") + "/";
        };
        ck(st.rgb);
        ck(st.alpha);
        key += "k" + std::to_string(st.konstColor) + std::to_string(st.konstAlpha) + ";";
    }
    m.shaderKeyText = key;
    m.shaderKey = fnv(key);
    return m;
}

static uint8_t hposOf(std::string_view s) { return s == "left" ? 0 : s == "right" ? 2 : 1; }
static uint8_t vposOf(std::string_view s) { return s == "top" ? 0 : s == "bottom" ? 2 : 1; }

static QuadUv quadOf(const BjValue& q) {
    QuadUv u{};
    for (uint32_t i = 0; i < 4; i++) u[i] = {q[i][0u].asFloat(), q[i][1u].asFloat()};
    return u;
}

static int buildPane(LayoutTemplate& t, const BjValue& j, int parent) {
    PaneProto proto;
    Pane& p = proto.pane;
    std::string_view type = j["type"].asString();
    if (type == "picture") p.kind = PaneKind::Picture;
    else if (type == "text") p.kind = PaneKind::Text;
    else if (type == "window") p.kind = PaneKind::Window;
    else if (type == "bounding") p.kind = PaneKind::Bounding;
    else if (type == "parts") p.kind = PaneKind::Parts;
    else p.kind = PaneKind::Null;
    p.name = j["name"].str();
    p.userData = j["userData"].str();
    for (uint32_t i = 0; i < 3; i++) p.translate[i] = j["translate"][i].asFloat();
    for (uint32_t i = 0; i < 3; i++) p.rotate[i] = j["rotate"][i].asFloat();
    for (uint32_t i = 0; i < 2; i++) p.scale[i] = j["scale"][i].asFloat(1);
    for (uint32_t i = 0; i < 2; i++) p.size[i] = j["size"][i].asFloat();
    p.alpha = (float)j["alpha"].asInt(255);
    BjValue flags = j["flags"];
    p.visible = flags["visible"].asBool(true);
    p.influencedAlpha = flags["influencedAlpha"].asBool();
    p.originX = hposOf(j["origin"]["x"].asString());
    p.originY = vposOf(j["origin"]["y"].asString());
    if (p.kind == PaneKind::Picture) {
        BjValue vc = j["vertexColors"];
        for (uint32_t i = 0; i < 4; i++) p.vertexColors[i] = rgbaOf(vc[i]);
        p.material = j["materialIndex"].asInt();
        for (BjValue q : j["texCoords"]) p.texCoords.push_back(quadOf(q));
    } else if (p.kind == PaneKind::Window) {
        BjValue c = j["content"];
        BjValue vc = c["vertexColors"];
        for (uint32_t i = 0; i < 4; i++) p.vertexColors[i] = rgbaOf(vc[i]);
        p.material = c["materialIndex"].asInt();
        for (BjValue q : c["texCoords"]) p.texCoords.push_back(quadOf(q));
        BjValue ov = j["contentOverflow"];
        p.overflowL = ov["left"].asFloat();
        p.overflowR = ov["right"].asFloat();
        p.overflowT = ov["top"].asFloat();
        p.overflowB = ov["bottom"].asFloat();
        for (BjValue f : j["frames"]) {
            WindowFrame wf;
            wf.material = f["materialIndex"].asInt();
            std::string_view fl = f["textureFlip"].asString();
            wf.flip = fl == "flipH" ? 1 : fl == "flipV" ? 2 : fl == "rotate90" ? 3 : fl == "rotate180" ? 4 : fl == "rotate270" ? 5 : 0;
            p.frames.push_back(wf);
        }
    } else if (p.kind == PaneKind::Text) {
        p.material = j["materialIndex"].asInt();
        const int fi = j["fontIndex"].asInt();
        p.fontResource = (fi >= 0 && fi < (int)t.fonts.size()) ? t.fonts[fi] : std::string("cbf_std.bcfnt");
        p.fontSize[0] = j["fontSize"][0u].asFloat();
        p.fontSize[1] = j["fontSize"][1u].asFloat();
        p.charSpace = j["charSpace"].asFloat();
        p.lineSpace = j["lineSpace"].asFloat();
        p.textPosX = hposOf(j["textPosition"]["x"].asString());
        p.textPosY = vposOf(j["textPosition"]["y"].asString());
        std::string_view al = j["textAlignment"].asString();
        p.alignment = al == "left" ? 1 : al == "center" ? 2 : al == "right" ? 3 : 0;
        Rgba top = rgbaOf(j["fontColors"]["top"]), bottom = rgbaOf(j["fontColors"]["bottom"]);
        p.vertexColors = {top, top, bottom, bottom};
        p.runs = plainRuns(j["text"].asString());
    }
    const int index = (int)t.panes.size();
    proto.parent = parent;
    t.panes.push_back(std::move(proto));
    for (BjValue c : j["children"]) {
        const int ci = buildPane(t, c, index);
        t.panes[index].children.push_back(ci);
    }
    return index;
}

static int buildGroup(LayoutTemplate& t, const BjValue& j) {
    GroupProto g;
    g.name = j["name"].str();
    for (BjValue p : j["panes"]) g.panes.push_back(p.str());
    const int index = (int)t.groups.size();
    t.groups.push_back(std::move(g));
    for (BjValue c : j["children"]) {
        const int ci = buildGroup(t, c);
        t.groups[index].children.push_back(ci);
    }
    return index;
}

static std::unique_ptr<LayoutTemplate> parseLayout(const BjValue& j) {
    auto t = std::make_unique<LayoutTemplate>();
    t->width = j["layout"]["size"][0u].asFloat();
    t->height = j["layout"]["size"][1u].asFloat();
    for (BjValue s : j["textures"]) t->textures.push_back(stem(s.asString()));
    for (BjValue s : j["fonts"]) t->fonts.push_back(s.str());
    for (BjValue m : j["materials"]) t->materials.push_back(materialOf(m, t->textures));
    buildPane(*t, j["rootPane"], -1);
    if (j["rootGroup"].isObject()) buildGroup(*t, j["rootGroup"]);
    return t;
}

static std::shared_ptr<AnimTemplate> parseAnimation(const std::string& name, const BjValue& j) {
    auto a = std::make_shared<AnimTemplate>();
    a->name = name;
    BjValue pai = j["pai1"];
    a->frameSize = std::max(1, pai["frameSize"].asInt(1));
    a->loop = pai["loop"].asBool();
    for (BjValue s : pai["textures"]) a->textures.push_back(stem(s.asString()));
    BjValue pat = j["pat1"];
    if (pat.isObject()) {
        for (BjValue g : pat["groups"]) a->groups.push_back(g.str());
        a->childBinding = pat["childBinding"].asBool();
    }
    for (BjValue c : pai["contents"]) {
        AnimContent ac;
        ac.name = c["name"].str();
        ac.material = c["type"].asString() == "material";
        for (BjValue info : c["animInfos"]) {
            AnimInfo ai;
            std::string_view tag = info["tag"].asString();
            memcpy(ai.tag, tag.data(), std::min<size_t>(4, tag.size()));
            for (BjValue tg : info["targets"]) {
                AnimTarget at;
                at.index = tg["index"].asInt();
                at.targetId = tg["targetId"].asInt();
                std::string_view ct = tg["curveType"].asString();
                at.curve.type = ct == "constant" ? 0 : ct == "step" ? 1 : 2;
                for (BjValue k : tg["keys"]) {
                    const uint32_t n = k.size();
                    at.curve.keys.push_back(k[0u].asFloat());
                    at.curve.keys.push_back(n > 1 ? k[1u].asFloat() : k[0u].asFloat());
                    at.curve.keys.push_back(n > 2 ? k[2u].asFloat() : 0.0f);
                }
                ai.targets.push_back(std::move(at));
            }
            ac.infos.push_back(std::move(ai));
        }
        a->contents.push_back(std::move(ac));
    }
    return a;
}

// ================================================================== Layout

Layout::Layout(const LayoutTemplate& t, Assets& assets, const std::string& archive) {
    width = t.width;
    height = t.height;
    materials = t.materials;
    fileColors.reserve(materials.size());
    for (const Material& m : materials) fileColors.push_back({m.bufferColor, m.konst});
    for (Material& m : materials)
        for (TexMap& tm : m.texMaps)
            if (!tm.name.empty()) tm.texture = assets.layoutTexture(archive, tm.name);
    mPanes.reserve(t.panes.size());
    for (const PaneProto& pp : t.panes) {
        auto p = std::make_unique<Pane>(pp.pane);
        p->parent = nullptr;
        p->children.clear();
        mPanes.push_back(std::move(p));
    }
    for (size_t i = 0; i < t.panes.size(); i++) {
        Pane* p = mPanes[i].get();
        if (t.panes[i].parent >= 0) p->parent = mPanes[t.panes[i].parent].get();
        for (int c : t.panes[i].children) p->children.push_back(mPanes[c].get());
        if (!mByName.count(p->name)) mByName.emplace(p->name, p);
        if (p->kind == PaneKind::Text) p->font = assets.font(p->fontResource);
    }
    root = mPanes.empty() ? nullptr : mPanes[0].get();
    for (const GroupProto& gp : t.groups) {
        Group g;
        g.name = gp.name;
        for (const std::string& n : gp.panes) {
            auto it = mByName.find(n);
            if (it != mByName.end()) g.panes.push_back(it->second);
        }
        g.children = gp.children;
        groups.push_back(std::move(g));
    }
    calculate();
}

Layout::~Layout() = default;

Pane* Layout::findPane(std::string_view name) const {
    auto it = mByName.find(std::string(name));
    return it == mByName.end() ? nullptr : it->second;
}

const Group* Layout::findGroup(std::string_view name) const {
    for (const Group& g : groups) if (g.name == name) return &g;
    return nullptr;
}

Material* Layout::findMaterial(std::string_view name) {
    for (Material& m : materials) if (m.name == name) return &m;
    return nullptr;
}

std::vector<Material*> Layout::materialsNamed(std::string_view name) {
    std::vector<Material*> out;
    for (Material& m : materials) if (m.name == name) out.push_back(&m);
    return out;
}

void Layout::calculate() {
    if (!root) return;
    Mat4 id;
    matIdentity(id);
    const Pane* host = root->parent;
    calcPane(root, host ? host->global : id, host ? host->childAlpha : 255.0f);
}

void Layout::adaptWidth(float w, Adapt policy) {
    if (w == adaptedWidth || !root) return;
    adaptedWidth = w;
    const float extra = std::max(0.0f, w - width);
    for (auto& p : mPanes) { p->shiftX = 0; p->growW = 0; }
    if (extra <= 0) { calculate(); return; }
    // only panes directly under containers at the centre are classified; their subtree moves with them
    std::function<void(Pane*)> visit = [&](Pane* p) {
        for (Pane* c : p->children) {
            const float x = c->translate[0];
            const bool full = c->size[0] >= width * 0.9f && fabsf(x) < width * 0.1f &&
                              (c->kind == PaneKind::Picture || c->kind == PaneKind::Window || c->kind == PaneKind::Bounding);
            if (full) { c->growW = extra; continue; }
            bool left = false, right = false;
            if (policy == Adapt::Edge) { left = x < -width * 0.35f; right = x > width * 0.35f; }
            else if (policy == Adapt::Split) { left = x < -width * 0.05f; right = x > width * 0.05f; }
            if (left) { c->shiftX = -extra / 2; continue; }
            if (right) { c->shiftX = extra / 2; continue; }
            if (c->kind == PaneKind::Null) visit(c);   // a centred container: classify inside
        }
    };
    visit(root);
    calculate();
}

void Layout::adaptHeight(float h, Adapt policy) {
    if (h == adaptedHeight || !root) return;
    adaptedHeight = h;
    const float extra = std::max(0.0f, h - height);
    for (auto& p : mPanes) { p->shiftY = 0; p->growH = 0; }
    if (extra <= 0) { calculate(); return; }
    std::function<void(Pane*)> visit = [&](Pane* p) {
        for (Pane* c : p->children) {
            const float y = c->translate[1];
            const bool full = c->size[1] >= height * 0.9f && fabsf(y) < height * 0.1f &&
                              (c->kind == PaneKind::Picture || c->kind == PaneKind::Window || c->kind == PaneKind::Bounding);
            if (full) { c->growH = extra; continue; }
            bool up = false, down = false;
            if (policy == Adapt::Edge) { up = y > height * 0.35f; down = y < -height * 0.35f; }
            else if (policy == Adapt::Split) { up = y > height * 0.05f; down = y < -height * 0.05f; }
            if (up) { c->shiftY = extra / 2; continue; }
            if (down) { c->shiftY = -extra / 2; continue; }
            if (c->kind == PaneKind::Null) visit(c);   // a centred container: classify inside
        }
    };
    visit(root);
    calculate();
}

void Layout::calcPane(Pane* p, const Mat4& parent, float inherited) {
    if (p->shiftX != 0 || p->shiftY != 0) {
        const float t[3] = {p->translate[0] + p->shiftX, p->translate[1] + p->shiftY, p->translate[2]};
        matPaneSrt(p->global, t, p->rotate, p->scale);
    } else {
        matPaneSrt(p->global, p->translate, p->rotate, p->scale);
    }
    matMul(p->global.m, parent.m, p->global.m);
    const float a = p->alpha * inherited / 255.0f;
    p->globalAlpha = a * opacity / 255.0f;
    const float child = p->influencedAlpha ? a : inherited;
    p->childAlpha = child;
    for (Pane* c : p->children) calcPane(c, p->global, child);
}

// ================================================================== animation

float evaluateCurve(const AnimCurve& c, float frame) {
    const std::vector<float>& k = c.keys;
    const size_t n = k.size() / 3;
    if (n == 0) return 0;
    if (c.type == 0 || n == 1) return k[1];
    if (frame <= k[0]) return k[1];
    if (frame >= k[(n - 1) * 3]) return k[(n - 1) * 3 + 1];
    size_t lo = 0, hi = n - 1;
    while (lo < hi) {
        const size_t mid = (lo + hi + 1) >> 1;
        if (k[mid * 3] <= frame) lo = mid; else hi = mid - 1;
    }
    const float* k0 = &k[lo * 3];
    if (c.type == 1) return k0[1];
    const float* k1 = &k[(lo + 1) * 3];
    const float d = k1[0] - k0[0];
    if (d <= 0) return k1[1];
    const float t = (frame - k0[0]) / d, t2 = t * t, t3 = t2 * t;
    return (2 * t3 - 3 * t2 + 1) * k0[1] + (-2 * t3 + 3 * t2) * k1[1] + (t3 - 2 * t2 + t) * d * k0[2] + (t3 - t2) * d * k1[2];
}

void Animator::advance(float frames) {
    if (!playing || frames == 0) return;
    float f = frame + frames * speed;
    if (loop) {
        f = fmodf(f, (float)frameSize);
        if (f < 0) f += frameSize;
    } else if (speed >= 0 && f >= lastFrame()) {
        f = lastFrame();
        frame = f;
        finish();
        return;
    } else if (speed < 0 && f <= 0) {
        f = 0;
        frame = f;
        finish();
        return;
    }
    frame = f;
}

void Animator::seek(float f) {
    if (loop) {
        f = fmodf(f, (float)frameSize);
        if (f < 0) f += frameSize;
        frame = f;
    } else {
        frame = std::clamp(f, 0.0f, lastFrame());
    }
}

void Animator::apply() {
    for (Track& t : tracks) t.set(evaluateCurve(*t.curve, frame), weight);
}

void Animator::stop() {
    playing = false;
    finish();
}

void Animator::finish() {
    playing = false;
    if (!finished) {
        finished = true;
        if (onFinish) {
            auto cb = std::move(onFinish);
            onFinish = nullptr;
            cb();
        }
    }
}

static inline float blendTo(float cur, float v, float w) { return w >= 1 ? v : cur + (v - cur) * w; }

// ================================================================== instance

LayoutInstance::LayoutInstance(Assets& assets, std::string archive, std::string name, std::unique_ptr<Layout> layout)
    : mAssets(assets), mArchive(std::move(archive)), mName(std::move(name)), mLayout(std::move(layout)) {}

LayoutInstance::~LayoutInstance() {
    if (mAssets.onDestroy) mAssets.onDestroy(*this);
    for (auto& a : animators) { a->onFinish = nullptr; a->stop(); }
}

std::string LayoutInstance::resolveAnimation(std::string_view name) const {
    std::string n(name);
    if (mAssets.archiveHasAnim(mArchive, n)) return n;
    std::string full = mName + "_" + n;
    if (mAssets.archiveHasAnim(mArchive, full)) return full;
    return std::string();
}

bool LayoutInstance::hasAnimation(std::string_view name) const { return !resolveAnimation(name).empty(); }

Animator* LayoutInstance::play(std::string_view name, const PlayOptions& opt) {
    const std::string file = resolveAnimation(name);
    if (file.empty()) {
        ALOGW("animation %.*s not found for %s/%s", (int)name.size(), name.data(), mArchive.c_str(), mName.c_str());
        return nullptr;
    }
    std::shared_ptr<const AnimTemplate> at = mAssets.animation(mArchive, file);
    if (!at) return nullptr;
    stop(opt.id.empty() ? file : opt.id);

    auto anim = std::make_unique<Animator>();
    Animator& A = *anim;
    A.name = file;
    A.id = opt.id.empty() ? file : opt.id;
    A.source = at;
    A.frameSize = at->frameSize;
    A.loop = opt.loop < 0 ? at->loop : opt.loop != 0;
    A.speed = opt.speed;
    A.weight = opt.weight;
    A.frame = opt.frame >= 0 ? opt.frame : (A.speed < 0 ? A.lastFrame() : 0.0f);
    A.playing = !opt.paused;

    // pattern textures
    std::vector<TexturePtr> patterns;
    for (const std::string& t : at->textures) patterns.push_back(mAssets.layoutTexture(mArchive, t));

    // binding scope
    Layout& L = *mLayout;
    bool scoped = opt.groups != 0;
    std::vector<Pane*> scopePanes;
    std::vector<Material*> scopeMats;
    if (scoped) {
        const std::vector<std::string>& names = opt.groups == 1 ? at->groups : opt.groupNames;
        std::function<void(Pane*)> addTree = [&](Pane* p) {
            scopePanes.push_back(p);
            for (Pane* c : p->children) addTree(c);
        };
        for (const std::string& gn : names) {
            const Group* g = L.findGroup(gn);
            if (!g) continue;
            for (Pane* p : g->panes) {
                if (at->childBinding) addTree(p); else scopePanes.push_back(p);
            }
        }
        for (Pane* p : scopePanes) {
            if (p->material >= 0) scopeMats.push_back(&L.materials[p->material]);
            for (const WindowFrame& f : p->frames) if (f.material >= 0) scopeMats.push_back(&L.materials[f.material]);
        }
    }
    auto inScope = [&](Pane* p) { return !scoped || std::find(scopePanes.begin(), scopePanes.end(), p) != scopePanes.end(); };
    auto matInScope = [&](Material* m) { return !scoped || std::find(scopeMats.begin(), scopeMats.end(), m) != scopeMats.end(); };

    for (const AnimContent& content : at->contents) {
        std::vector<std::string> names{content.name};
        for (const auto& ba : opt.bindAs) if (ba.second == content.name && ba.first != content.name) names.push_back(ba.first);
        for (const std::string& bindName : names) {
            if (!content.material) {
                Pane* p = L.findPane(bindName);
                if (!p || !inScope(p)) continue;
                for (const AnimInfo& info : content.infos) {
                    for (const AnimTarget& tg : info.targets) {
                        const int id = tg.targetId;
                        std::function<void(float, float)> set;
                        if (!memcmp(info.tag, "CLPA", 4)) {
                            float* arr[10] = {&p->translate[0], &p->translate[1], &p->translate[2], &p->rotate[0], &p->rotate[1],
                                              &p->rotate[2], &p->scale[0], &p->scale[1], &p->size[0], &p->size[1]};
                            if (id >= 0 && id < 10) {
                                float* dst = arr[id];
                                const bool sizeChange = id >= 8;
                                set = [dst, p, sizeChange](float v, float w) {
                                    const float nv = blendTo(*dst, v, w);
                                    if (sizeChange && nv != *dst) p->textRevision++;
                                    *dst = nv;
                                };
                            }
                        } else if (!memcmp(info.tag, "CLVC", 4)) {
                            if (id == 16) {
                                set = [p](float v, float w) { p->alpha = blendTo(p->alpha, v, w); };
                            } else if (id >= 0 && id < 16 && p->kind != PaneKind::Null && p->kind != PaneKind::Bounding) {
                                const int corner = id >> 2, ch = id & 3;
                                if (p->kind == PaneKind::Text) {
                                    if (corner % 2 == 1) continue;
                                    // text: lt/rt share the top colour, lb/rb the bottom colour
                                    const int a = corner < 2 ? 0 : 2, b = corner < 2 ? 1 : 3;
                                    set = [p, a, b, ch](float v, float w) {
                                        p->vertexColors[a][ch] = blendTo(p->vertexColors[a][ch], v, w);
                                        p->vertexColors[b][ch] = p->vertexColors[a][ch];
                                    };
                                } else {
                                    set = [p, corner, ch](float v, float w) {
                                        p->vertexColors[corner][ch] = blendTo(p->vertexColors[corner][ch], v, w);
                                    };
                                }
                            }
                        } else if (!memcmp(info.tag, "CLVI", 4)) {
                            set = [p](float v, float w) { if (w >= 0.5f) p->visible = v != 0; };
                        }
                        if (set) A.tracks.push_back({&tg.curve, std::move(set)});
                    }
                }
            } else {
                for (Material* m : L.materialsNamed(bindName)) {
                    if (!matInScope(m)) continue;
                    for (const AnimInfo& info : content.infos) {
                        for (const AnimTarget& tg : info.targets) {
                            const int id = tg.targetId;
                            std::function<void(float, float)> set;
                            if (!memcmp(info.tag, "CLMC", 4)) {
                                float* dst = nullptr;
                                if (id < 4) dst = &m->bufferColor[id];
                                else if (((id - 4) >> 2) < 6) dst = &m->konst[(id - 4) >> 2][(id - 4) & 3];
                                if (dst) set = [dst](float v, float w) { *dst = blendTo(*dst, v, w); };
                            } else if (!memcmp(info.tag, "CLTS", 4)) {
                                if (tg.index >= 0 && tg.index < (int)m->texSrts.size()) {
                                    TexSrt* s = &m->texSrts[tg.index];
                                    float* fields[5] = {&s->tx, &s->ty, &s->rotate, &s->sx, &s->sy};
                                    if (id >= 0 && id < 5) {
                                        float* dst = fields[id];
                                        set = [dst](float v, float w) { *dst = blendTo(*dst, v, w); };
                                    }
                                }
                            } else if (!memcmp(info.tag, "CLTP", 4)) {
                                if (tg.index >= 0 && tg.index < (int)m->texMaps.size()) {
                                    TexMap* map = &m->texMaps[tg.index];
                                    auto pats = patterns;
                                    set = [map, pats](float v, float w) {
                                        if (w < 0.5f) return;
                                        const int i = (int)lroundf(v);
                                        if (i >= 0 && i < (int)pats.size() && pats[i]) map->texture = pats[i];
                                    };
                                }
                            }
                            if (set) A.tracks.push_back({&tg.curve, std::move(set)});
                        }
                    }
                }
            }
        }
    }
    A.apply();
    L.calculate();
    animators.push_back(std::move(anim));
    return animators.back().get();
}

void LayoutInstance::stop(std::string_view idOrName) {
    const std::string full = mName + "_" + std::string(idOrName);
    for (size_t i = 0; i < animators.size(); i++) {
        Animator* a = animators[i].get();
        if (a->id == idOrName || a->id == full) {
            std::unique_ptr<Animator> keep = std::move(animators[i]);
            animators.erase(animators.begin() + i);
            keep->stop();
            mDead.push_back(std::move(keep));
            if (!mUpdating) mDead.clear();
            return;
        }
    }
}

Animator* LayoutInstance::find(std::string_view name) {
    const std::string full = mName + "_" + std::string(name);
    for (auto& a : animators) if (a->name == name || a->name == full) return a.get();
    return nullptr;
}

void LayoutInstance::update(float frames) {
    // onFinish callbacks may play or stop animations: iterate over a snapshot
    std::vector<Animator*> snap;
    snap.reserve(animators.size());
    for (auto& a : animators) snap.push_back(a.get());
    mUpdating++;
    for (Animator* a : snap) {
        bool alive = false;
        for (auto& b : animators) if (b.get() == a) { alive = true; break; }
        if (!alive) continue;
        a->advance(frames);
        // a finish callback may have unbound it: values it applied stay, it is not applied again
        alive = false;
        for (auto& b : animators) if (b.get() == a) { alive = true; break; }
        if (alive) a->apply();
    }
    mUpdating--;
    if (!mUpdating) mDead.clear();
    mLayout->calculate();
}

bool LayoutInstance::anyPlaying() const {
    for (const auto& a : animators) if (a->playing) return true;
    return false;
}

void LayoutInstance::setVisible(std::string_view n, bool v) {
    if (Pane* p = pane(n)) p->visible = v;
}

void LayoutInstance::setAlpha(std::string_view n, float a) {
    if (Pane* p = pane(n)) p->alpha = a;
}

void LayoutInstance::setText(std::string_view n, std::string_view text, bool markup) {
    Pane* p = pane(n);
    if (!p || p->kind != PaneKind::Text) return;
    p->runs = markup ? parseMarkup(text) : plainRuns(text);
    p->hasFontScale = false;
    p->textRevision++;
}

void LayoutInstance::setTextU32(std::string_view n, const std::u32string& text) {
    Pane* p = pane(n);
    if (!p || p->kind != PaneKind::Text) return;
    p->runs.clear();
    if (!text.empty()) {
        TextRun r;
        r.text = text;
        p->runs.push_back(std::move(r));
    }
    p->hasFontScale = false;
    p->textRevision++;
}

bool LayoutInstance::setMessage(std::string_view n, std::string_view label) {
    std::string markup;
    MessageStyle style;
    if (!mAssets.message(label, &markup, &style)) return false;
    Pane* p = pane(n);
    if (!p || p->kind != PaneKind::Text) return false;
    p->runs = parseMarkup(markup);
    p->hasFontScale = style.valid;
    p->fontScale[0] = style.fontScaleX;
    p->fontScale[1] = style.fontScaleY;
    p->textRevision++;
    return true;
}

void LayoutInstance::replaceTexture(std::string_view n, TexturePtr tex, int mapIndex) {
    Pane* p = pane(n);
    if (!p || p->material < 0) return;
    Material& m = mLayout->materials[p->material];
    if (mapIndex < 0 || mapIndex >= (int)m.texMaps.size()) return;
    m.texMaps[mapIndex].texture = std::move(tex);
}

// ================================================================== text

std::u32string utf8ToU32(std::string_view s) {
    std::u32string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = (unsigned char)s[i];
        uint32_t cp;
        int n;
        if (c < 0x80) { cp = c; n = 1; }
        else if ((c >> 5) == 6) { cp = c & 0x1f; n = 2; }
        else if ((c >> 4) == 14) { cp = c & 0x0f; n = 3; }
        else if ((c >> 3) == 30) { cp = c & 0x07; n = 4; }
        else { i++; continue; }
        if (i + n > s.size()) break;
        for (int k = 1; k < n; k++) cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3f);
        out.push_back(cp);
        i += n;
    }
    return out;
}

std::vector<TextRun> plainRuns(std::string_view text) {
    std::vector<TextRun> runs;
    if (!text.empty()) {
        TextRun r;
        r.text = utf8ToU32(text);
        runs.push_back(std::move(r));
    }
    return runs;
}

// {tag:color:r,g,b,a}, {tag:1.0:percent} ... {/tag:1.0}, literal braces doubled.
std::vector<TextRun> parseMarkup(std::string_view m) {
    std::vector<TextRun> runs;
    std::vector<float> scales{1.0f};
    bool hasColor = false;
    Rgba color{};
    std::string text;
    auto flush = [&]() {
        if (text.empty()) return;
        TextRun r;
        r.text = utf8ToU32(text);
        r.hasColor = hasColor;
        r.color = color;
        r.scale = scales.back();
        runs.push_back(std::move(r));
        text.clear();
    };
    for (size_t i = 0; i < m.size();) {
        const char ch = m[i];
        if ((ch == '{' || ch == '}') && i + 1 < m.size() && m[i + 1] == ch) {
            text += ch;
            i += 2;
            continue;
        }
        if (ch == '{') {
            const size_t end = m.find('}', i);
            std::string_view body = end == std::string_view::npos ? std::string_view() : m.substr(i + 1, end - i - 1);
            bool closing = false;
            if (!body.empty() && body[0] == '/') { closing = true; body.remove_prefix(1); }
            if (body.substr(0, 4) == "tag:") {
                body.remove_prefix(4);
                const size_t c = body.find(':');
                std::string_view name = body.substr(0, c);
                std::string_view params = c == std::string_view::npos ? std::string_view() : body.substr(c + 1);
                flush();
                if (name == "color" && !closing && !params.empty()) {
                    float v[4];
                    int n = 0;
                    size_t p = 0;
                    while (n < 4 && p <= params.size()) {
                        const size_t q = params.find(',', p);
                        std::string part(params.substr(p, q == std::string_view::npos ? std::string_view::npos : q - p));
                        v[n++] = (float)atof(part.c_str());
                        if (q == std::string_view::npos) break;
                        p = q + 1;
                    }
                    if (n == 4) { hasColor = true; color = {v[0], v[1], v[2], v[3]}; }
                } else if (name == "1.0") {
                    if (closing) {
                        if (scales.size() > 1) scales.pop_back();
                    } else {
                        const float pc = (float)atof(std::string(params).c_str());
                        scales.push_back(pc > 0 ? pc / 100.0f : 1.0f);
                    }
                }
                i = end + 1;
                continue;
            }
        }
        text += ch;
        i++;
    }
    flush();
    return runs;
}

// ================================================================== fonts

Font::~Font() {
    releaseGl();
    if (mMap) munmap((void*)mMap, mMapSize);
}

void Font::releaseGl() {
    if (mAtlas) glDeleteTextures(1, &mAtlas);
    gTextureEpoch++;
    mAtlas = 0;
    mSlots.clear();
    mNext = 0;
    mGeneration++;
}

bool Font::open(const std::string& path, const std::string& name) {
    mName = name;
    int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < 32) { close(fd); return false; }
    void* p = mmap(nullptr, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (p == MAP_FAILED) return false;
    mMap = (const uint8_t*)p;
    mMapSize = (size_t)st.st_size;
    if (memcmp(mMap, "CFN1", 4) != 0) return false;
    uint16_t h[7];
    memcpy(h, mMap + 4, 14);
    lineFeed = h[0]; height = h[1]; width = h[2]; ascent = h[3]; baseline = h[4]; cellWidth = h[5]; cellHeight = h[6];
    mFormat = mMap[18];
    memcpy(&mCount, mMap + 20, 4);
    memcpy(&mAlt, mMap + 24, 4);
    mTable = mMap + 28;
    mBitmaps = mTable + (size_t)mCount * 8;
    const size_t n = (size_t)(cellWidth + 2) * (cellHeight + 2);   // cell + 1 texel clamp border
    mBitmapStride = mFormat == 0 ? (n + 1) / 2 : mFormat == 1 ? n * 2 : n;
    if (mBitmaps + mBitmapStride * mCount > mMap + mMapSize) return false;
    mGlyphs.resize(mCount);
    for (uint32_t i = 0; i < mCount; i++) {
        const uint8_t* e = mTable + i * 8;
        Glyph& g = mGlyphs[i];
        memcpy(&g.code, e, 4);
        memcpy(&g.left, e + 4, 2);
        g.glyphWidth = e[6];
        g.charWidth = e[7];
    }
    return true;
}

const Glyph* Font::find(uint32_t code) const {
    auto it = std::lower_bound(mGlyphs.begin(), mGlyphs.end(), code, [](const Glyph& g, uint32_t c) { return g.code < c; });
    return it != mGlyphs.end() && it->code == code ? &*it : nullptr;
}

const Glyph* Font::glyph(uint32_t code) const {
    if (const Glyph* g = find(code)) return g;
    auto im = mImported.find(code);
    if (im != mImported.end()) return &im->second.g;
    for (const auto& fb : mFallbacks) {
        const Glyph* src = fb->find(code);
        if (!src || fb->cellWidth != cellWidth || fb->baseline > baseline) continue;
        // pad the fallback's cell rows down to this font's baseline (same horizontal metrics)
        std::vector<uint8_t> fc;
        if (!fb->cellAlpha(src, fc)) continue;
        const int w = cellWidth + 2, h = cellHeight + 2, fh = fb->cellHeight + 2;
        const int dy = baseline - fb->baseline;
        Imported e;
        e.g = *src;
        e.cell.assign((size_t)w * h, 0);
        for (int y = 0; y < fh && y + dy < h; y++)
            memcpy(&e.cell[(size_t)(y + dy) * w], &fc[(size_t)y * w], (size_t)w);
        auto& slot = mImported[code] = std::move(e);
        return &slot.g;
    }
    return mAlt < mGlyphs.size() ? &mGlyphs[mAlt] : (mGlyphs.empty() ? nullptr : &mGlyphs[0]);
}

bool Font::has(uint32_t code) const {
    if (find(code) || mImported.count(code)) return true;
    for (const auto& fb : mFallbacks)
        if (fb->cellWidth == cellWidth && fb->baseline <= baseline && fb->find(code)) return true;
    return false;
}

const Font::Imported* Font::imported(const Glyph* g) const {
    if (!g || (g >= mGlyphs.data() && g < mGlyphs.data() + mGlyphs.size())) return nullptr;
    auto it = mImported.find(g->code);
    return it != mImported.end() && &it->second.g == g ? &it->second : nullptr;
}

bool Font::cellAlpha(const Glyph* g, std::vector<uint8_t>& out) const {
    if (!g || mGlyphs.empty()) return false;
    if (const Imported* im = imported(g)) { out = im->cell; return true; }
    const int index = (int)(g - mGlyphs.data());
    const int w = cellWidth + 2, h = cellHeight + 2;
    out.assign((size_t)w * h, 0);
    const uint8_t* src = mBitmaps + mBitmapStride * index;
    for (int i = 0; i < w * h; i++) {
        if (mFormat == 0) {
            const uint8_t b = src[i >> 1];
            out[i] = (uint8_t)(((i & 1) ? (b >> 4) : (b & 15)) * 17);
        } else if (mFormat == 1) {
            out[i] = src[i * 2 + 1];
        } else {
            out[i] = src[i];
        }
    }
    return true;
}

// The distance field of a coverage image: upsampled `scale` times (bilinear), the 50 % contour
// as the edge, distances by a two pass 3-4 chamfer (within a few percent of Euclidean), mapped to
// 0..255 with 128 on the edge and Font::kSdfRange upsampled pixels to either end.
static void glyphSdf(const uint8_t* cov, int w, int h, int scale, std::vector<uint8_t>* out) {
    const int W = w * scale, H = h * scale;
    std::vector<uint8_t> up((size_t)W * H);
    for (int y = 0; y < H; y++) {
        const float sy = (y + 0.5f) / scale - 0.5f;
        const int y0 = std::clamp((int)floorf(sy), 0, h - 1), y1 = std::min(y0 + 1, h - 1);
        const float fy = std::clamp(sy - y0, 0.0f, 1.0f);
        for (int x = 0; x < W; x++) {
            const float sx = (x + 0.5f) / scale - 0.5f;
            const int x0 = std::clamp((int)floorf(sx), 0, w - 1), x1 = std::min(x0 + 1, w - 1);
            const float fx = std::clamp(sx - x0, 0.0f, 1.0f);
            const float a = (cov[y0 * w + x0] * (1 - fx) + cov[y0 * w + x1] * fx) * (1 - fy) +
                            (cov[y1 * w + x0] * (1 - fx) + cov[y1 * w + x1] * fx) * fy;
            up[(size_t)y * W + x] = (uint8_t)lroundf(a);
        }
    }
    const int INF = 1 << 20;
    // dist[0]: to the nearest outside pixel (for inside ones), dist[1]: to the nearest inside one
    std::vector<int> dist[2] = {std::vector<int>((size_t)W * H), std::vector<int>((size_t)W * H)};
    for (int k = 0; k < 2; k++) {
        std::vector<int>& d = dist[k];
        for (int i = 0; i < W * H; i++) {
            const bool inside = up[i] >= 128;
            d[i] = (inside == (k == 1)) ? 0 : INF;   // k 0: outside pixels are the targets
        }
        auto relax = [&](int x, int y, int dx, int dy, int c) {
            const int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= W || ny >= H) return;
            int& v = d[(size_t)y * W + x];
            const int n = d[(size_t)ny * W + nx] + c;
            if (n < v) v = n;
        };
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                relax(x, y, -1, 0, 3); relax(x, y, -1, -1, 4); relax(x, y, 0, -1, 3); relax(x, y, 1, -1, 4);
            }
        for (int y = H - 1; y >= 0; y--)
            for (int x = W - 1; x >= 0; x--) {
                relax(x, y, 1, 0, 3); relax(x, y, 1, 1, 4); relax(x, y, 0, 1, 3); relax(x, y, -1, 1, 4);
            }
    }
    out->resize((size_t)W * H);
    for (int i = 0; i < W * H; i++) {
        const bool inside = up[i] >= 128;
        const float sd = inside ? dist[0][i] / 3.0f - 0.5f : -(dist[1][i] / 3.0f - 0.5f);
        (*out)[i] = (uint8_t)std::clamp(lroundf(128 + sd * 127 / Font::kSdfRange), 0L, 255L);
    }
}

bool Font::cellSdf(const Glyph* g, int scale, std::vector<uint8_t>& out) const {
    std::vector<uint8_t> cov;
    if (!cellAlpha(g, cov)) return false;
    glyphSdf(cov.data(), cellWidth + 2, cellHeight + 2, scale, &out);
    return true;
}

bool Font::place(const Glyph* g, float* u0, float* v0, float* u1, float* v1) {
    if (!g || mGlyphs.empty()) return false;
    const Imported* im = imported(g);
    // atlas key: the table index, or the code point (high bit set) for a borrowed glyph
    const uint32_t key = im ? (0x80000000u | g->code) : (uint32_t)(g - mGlyphs.data());
    if (!mAtlas) {
        mSlotW = (cellWidth + 2) * kSdfScale;
        mSlotH = (cellHeight + 2) * kSdfScale;
        // enough slots for a few hundred distinct glyphs; small fonts get a small atlas
        mAtlasSize = mCount <= 128 ? 512 : 1024;
        mCols = mAtlasSize / mSlotW;
        mRows = mAtlasSize / mSlotH;
        glGenTextures(1, &mAtlas);
        glBindTexture(GL_TEXTURE_2D, mAtlas);
        std::vector<uint8_t> zero((size_t)mAtlasSize * mAtlasSize * 2, 0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE_ALPHA, mAtlasSize, mAtlasSize, 0, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, zero.data());
        gTextureEpoch++;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        mSlots.clear();
        mNext = 0;
    }
    int slot;
    auto it = mSlots.find(key);
    if (it != mSlots.end()) {
        slot = it->second;
    } else {
        if (mNext >= mCols * mRows) {
            // full: start over (the caller rebuilds text geometry on the generation change)
            std::vector<uint8_t> zero((size_t)mAtlasSize * mAtlasSize * 2, 0);
            glBindTexture(GL_TEXTURE_2D, mAtlas);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mAtlasSize, mAtlasSize, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, zero.data());
            gTextureEpoch++;
            mSlots.clear();
            mNext = 0;
            mGeneration++;
        }
        slot = mNext++;
        mSlots.emplace(key, slot);
        // decode the bordered cell (the packer stored its sheet surround) into L, A pairs
        const int w = cellWidth + 2, h = cellHeight + 2;
        mScratch.assign((size_t)w * h * 2, 0);
        const uint8_t* src = im ? nullptr : mBitmaps + mBitmapStride * key;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                const int i = y * w + x;
                uint8_t L = 255, A;
                if (im) {
                    A = im->cell[i];
                } else if (mFormat == 0) {
                    const uint8_t b = src[i >> 1];
                    A = (uint8_t)(((i & 1) ? (b >> 4) : (b & 15)) * 17);
                } else if (mFormat == 1) {
                    L = src[i * 2];
                    A = src[i * 2 + 1];
                } else {
                    A = src[i];
                }
                uint8_t* d = &mScratch[((size_t)y * w + x) * 2];
                d[0] = L;
                d[1] = A;
            }
        }
        // the alpha becomes the cell's distance field at kSdfScale; the luminance is scaled along
        std::vector<uint8_t> cov((size_t)w * h), field;
        for (int i = 0; i < w * h; i++) cov[i] = mScratch[(size_t)i * 2 + 1];
        glyphSdf(cov.data(), w, h, kSdfScale, &field);
        const int W = w * kSdfScale, H = h * kSdfScale;
        std::vector<uint8_t> la((size_t)W * H * 2);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                const size_t o = (size_t)y * W + x;
                la[o * 2] = mScratch[((size_t)(y / kSdfScale) * w + x / kSdfScale) * 2];
                la[o * 2 + 1] = field[o];
            }
        const int sx = (slot % mCols) * mSlotW, sy = (slot / mCols) * mSlotH;
        glBindTexture(GL_TEXTURE_2D, mAtlas);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, sx, sy, W, H, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, la.data());
        gTextureEpoch++;
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    }
    const int sx = (slot % mCols) * mSlotW + kSdfScale, sy = (slot / mCols) * mSlotH + kSdfScale;
    const float inv = 1.0f / mAtlasSize;
    *u0 = sx * inv;
    *v0 = sy * inv;
    *u1 = (sx + g->glyphWidth * kSdfScale) * inv;
    *v1 = (sy + cellHeight * kSdfScale) * inv;
    return true;
}

// ================================================================== assets

Assets::Assets(std::string root) : mRoot(std::move(root)) {
    mRenderer = std::make_unique<Renderer>(*this);
}

Assets::~Assets() {
    releaseGl();
}

bool Assets::ok() { return pack("lyt_launcher.cpk") != nullptr; }

Pack* Assets::pack(const std::string& file) {
    auto it = mPacks.find(file);
    if (it != mPacks.end()) return it->second.get();
    auto p = std::make_unique<Pack>();
    if (!p->open(mRoot + "/" + file)) {
        ALOGW("pack %s/%s missing", mRoot.c_str(), file.c_str());
        p.reset();
    }
    Pack* out = p.get();
    mPacks.emplace(file, std::move(p));
    return out;
}

const LayoutTemplate* Assets::layoutTemplate(const std::string& archive, const std::string& name) {
    const std::string key = archive + "/" + name;
    auto it = mLayouts.find(key);
    if (it != mLayouts.end()) return it->second.get();
    std::unique_ptr<LayoutTemplate> t;
    if (Pack* p = pack("lyt_" + archive + ".cpk")) {
        if (const BjDoc* d = p->doc("blyt/" + name)) t = parseLayout(d->root());
    }
    if (!t) ALOGW("layout %s missing", key.c_str());
    const LayoutTemplate* out = t.get();
    mLayouts.emplace(key, std::move(t));
    return out;
}

std::shared_ptr<const AnimTemplate> Assets::animation(const std::string& archive, const std::string& name) {
    const std::string key = archive + "/" + name;
    auto it = mAnims.find(key);
    if (it != mAnims.end()) return it->second;
    std::shared_ptr<const AnimTemplate> a;
    if (Pack* p = pack("lyt_" + archive + ".cpk")) {
        if (const BjDoc* d = p->doc("anim/" + name)) a = parseAnimation(name, d->root());
    }
    mAnims.emplace(key, a);
    return a;
}

bool Assets::archiveHasAnim(const std::string& archive, std::string_view name) {
    Pack* p = pack("lyt_" + archive + ".cpk");
    if (!p) return false;
    const uint8_t* d;
    size_t n;
    return p->blob("anim/" + std::string(name), &d, &n);
}

TexturePtr Assets::pngTexture(const uint8_t* data, size_t size, bool flipY) {
    int w = 0, h = 0, comp = 0;
    stbi_set_flip_vertically_on_load(flipY ? 1 : 0);
    stbi_uc* px = stbi_load_from_memory(data, (int)size, &w, &h, &comp, 4);
    stbi_set_flip_vertically_on_load(0);
    if (!px) return nullptr;
    TexturePtr t = rgbaTexture(px, w, h);
    stbi_image_free(px);
    return t;
}

TexturePtr Assets::rgbaTexture(const uint8_t* rgba, int w, int h) {
    auto t = std::shared_ptr<Texture>(new Texture(), [](Texture* x) {
        if (x->id) glDeleteTextures(1, &x->id);
        gTextureEpoch++;
        delete x;
    });
    glGenTextures(1, &t->id);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    gTextureEpoch++;
    t->width = t->texWidth = w;
    t->height = t->texHeight = h;
    return t;
}

TexturePtr Assets::layoutTexture(const std::string& archive, const std::string& name) {
    const std::string key = archive + "/" + name;
    auto it = mTextures.find(key);
    if (it != mTextures.end()) {
        if (TexturePtr t = it->second.lock()) return t;
    }
    Pack* p = pack("lyt_" + archive + ".cpk");
    if (!p) return nullptr;
    const uint8_t* d;
    size_t n;
    if (!p->blob("timg/" + name, &d, &n)) {
        ALOGW("texture %s missing", key.c_str());
        return nullptr;
    }
    // the packer stored the PNG flipped and padded to the hardware size
    TexturePtr t = pngTexture(d, n, false);
    if (!t) return nullptr;
    if (const BjDoc* idx = p->doc("timg/index")) {
        BjValue info = idx->root()[name];
        if (info.isObject()) {
            t->width = info["width"].asInt(t->width);
            t->height = info["height"].asInt(t->height);
        }
    }
    mTextures[key] = t;
    return t;
}

std::shared_ptr<Font> Assets::font(const std::string& resourceName) {
    std::string s = stem(resourceName);
    // Hud.bcfnt is the region font Hud_JP; cbf_std is the shared font; unknown names fall back
    static const char* kKnown[] = {"Hud_JP", "cbf_std", "cbf_ko-Hang-KR", "cbf_zh-Hans-CN", "cbf_zh-Hant-TW"};
    std::string resolved = "cbf_std";
    for (const char* k : kKnown) {
        if (s == k) { resolved = k; break; }
        if (strncmp(k, s.c_str(), s.size()) == 0 && k[s.size()] == '_') { resolved = k; break; }
    }
    auto it = mFonts.find(resolved);
    if (it != mFonts.end()) return it->second;
    auto f = std::make_shared<Font>();
    if (!f->open(mRoot + "/font_" + resolved + ".cfn", resolved)) {
        ALOGW("font %s missing", resolved.c_str());
        f.reset();
    }
    mFonts.emplace(resolved, f);
    // the shared font borrows what it lacks from the regional system fonts (hangul, simplified
    // hanzi): nano's lists show names in every language (mapped, read only when a glyph is needed)
    if (f && resolved == "cbf_std") {
        for (const char* fb : {"cbf_ko-Hang-KR", "cbf_zh-Hans-CN"})
            if (auto g = font(fb)) f->addFallback(g);
    }
    return f;
}

void Assets::setLanguage(const std::string& language) {
    mLanguage = language;
    Pack* p = pack("msg.cpk");
    const char* keys[4] = {"menu", "hud", "menuStyles", "hudStyles"};
    for (int i = 0; i < 4; i++) {
        mMsg[i] = p ? p->doc(language + "/" + keys[i]) : nullptr;
        if (!mMsg[i] && p) mMsg[i] = p->doc(std::string("English/") + keys[i]);
    }
}

bool Assets::message(std::string_view label, std::string* markup, MessageStyle* style) {
    if (!mMsg[0]) setLanguage(mLanguage);
    for (int f = 0; f < 2; f++) {
        if (!mMsg[f]) continue;
        BjValue m = mMsg[f]->root()[label];
        if (!m.isObject()) continue;
        if (markup) *markup = m["markup"].str();
        if (style) {
            style->valid = false;
            BjValue st = m["style"];
            if (st.isNumber() && mMsg[f + 2]) {
                BjValue s = mMsg[f + 2]->root()["styles"][(uint32_t)st.asInt()];
                if (s.isObject()) {
                    style->valid = true;
                    style->fontScaleX = s["fontScale"][0u].asFloat(1);
                    style->fontScaleY = s["fontScale"][1u].asFloat(1);
                }
            }
        }
        return true;
    }
    return false;
}

std::string Assets::messageText(std::string_view label) {
    if (!mMsg[0]) setLanguage(mLanguage);
    for (int f = 0; f < 2; f++) {
        if (!mMsg[f]) continue;
        BjValue m = mMsg[f]->root()[label];
        if (m.isObject()) return m["text"].str();
    }
    return std::string();
}

std::unique_ptr<LayoutInstance> Assets::create(const std::string& archive, const std::string& name) {
    const LayoutTemplate* t = layoutTemplate(archive, name);
    if (!t) return nullptr;
    auto layout = std::make_unique<Layout>(*t, *this, archive);
    auto instance = std::make_unique<LayoutInstance>(*this, archive, name, std::move(layout));
    if (onCreate) onCreate(*instance);
    return instance;
}

void Assets::trim() {
    for (auto it = mTextures.begin(); it != mTextures.end();) {
        if (it->second.expired()) it = mTextures.erase(it); else ++it;
    }
}

void Assets::releaseGl() {
    if (mRenderer) mRenderer->releaseGl();
    for (auto& f : mFonts) if (f.second) f.second->releaseGl();
    mTextures.clear();
}

}  // namespace ctr
}  // namespace android
