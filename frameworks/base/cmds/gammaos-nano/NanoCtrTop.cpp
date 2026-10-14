// NanoCtrTop - see NanoCtrTop.h.
#include "NanoCtrTop.h"
#include "NanoCtrArt.h"
#include "NanoCtrHome.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

namespace {

// scene3d.ts / banner.ts constants
const int SHOW_DELAY_MENU = 14;
const int SHOW_DELAY_TITLE = 31;
const int BOOT_DELAY = 29;
const float WALL_PHASE = 2;
const int TURN_FRAMES = 600;
const float SCALE_BASE = 0.8f, SCALE_STEP = 0.25f * 0.2f;
const int SCALE_STEPS = 4;
const float TOP_CLEAR[3] = {232 / 255.0f, 232 / 255.0f, 238 / 255.0f};

enum Type { TitleT = 0, DS = 3, DSiWare = 4, Gift = 5, Default = 7, Banner2D = 8, FolderT = 9,
            AppletFriend = 0x0e, AppletMemo = 0x0f, AppletNews = 0x10, AppletWeb = 0x11, AppletMvs = 0x12 };

const char* kBlitVs = R"(
attribute vec2 a_pos;
attribute vec2 a_uv;
uniform mat4 u_proj;
varying vec2 v_uv;
void main() { v_uv = a_uv; gl_Position = u_proj * vec4(a_pos, 0.0, 1.0); }
)";
const char* kBlitFs = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 v_uv;
uniform sampler2D u_tex;
void main() { gl_FragColor = texture2D(u_tex, v_uv); }   // opaque unless the wall is hidden
)";

GLuint compileBlit(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { glDeleteShader(s); return 0; }
    return s;
}

}  // namespace

void CtrTop::Banner::update() {
    if (spec.still) {
        model->visible = shown;
        scaleStep = shown ? SCALE_STEPS : 0;
        return;
    }
    int step;
    if (shown) {
        step = scaleStep;
        scaleStep = std::min(scaleStep + 1, SCALE_STEPS);
    } else {
        scaleStep = std::max(scaleStep - 1, 0);
        step = scaleStep;
    }
    model->visible = shown || scaleStep > 0;
    turnFrame = (turnFrame + 1) % TURN_FRAMES;
    const float angle = spec.turn ? -turnFrame * (float)M_PI * 2 / TURN_FRAMES : 0.0f;
    const float scale = SCALE_BASE + step * SCALE_STEP;
    mat4Transform(model->transform, {scale, scale, scale}, {0, angle, 0}, {0, 0, 0});
    if (extra) {
        extra->visible = model->visible;
        extra->transform = model->transform;
    }
}

CtrTop::CtrTop(Assets& assets) : mAssets(assets) {}

CtrTop::~CtrTop() { releaseGl(); }

void CtrTop::releaseGl() {
    mRenderer.releaseGl();
    mBanner.reset();
    mLeaving.clear();
    mWall.reset();
    for (auto& r : mResources) if (r.second) r.second->releaseGl();
    mResources.clear();
    if (mBlitProgram) glDeleteProgram(mBlitProgram);
    mBlitProgram = 0;
    mPlate.reset();
}

std::shared_ptr<ModelResource> CtrTop::resource(const std::string& name) {
    auto it = mResources.find(name);
    if (it != mResources.end()) return it->second;
    std::shared_ptr<ModelResource> r;
    Pack* p = mAssets.pack("model_" + name + ".cpk");
    if (p) {
        r = std::make_shared<ModelResource>();
        if (!r->load(*p, mAssets.pack("model_Textures.cpk"))) r.reset();
    }
    if (!r) ALOGW("model %s missing", name.c_str());
    mResources[name] = r;
    return r;
}

bool CtrTop::load() {
    if (Pack* cp = mAssets.pack("model_BannerCamera.cpk")) {
        if (const BjDoc* d = cp->doc("cgfx")) {
            BjValue c = d->root()["cameras"][0u];
            if (c.isObject()) {
                mCamera.eye = {c["translation"][0u].asFloat(), c["translation"][1u].asFloat(1), c["translation"][2u].asFloat(44.786f)};
                BjValue t = c["view"]["targetPosition"];
                mCamera.target = {t[0u].asFloat(), t[1u].asFloat(1), t[2u].asFloat()};
                mCamera.twist = c["view"]["twist"].asFloat();
                BjValue pr = c["projection"];
                mCamera.fovY = pr["fovY"].asFloat(0.5235988f);
                mBaseFovY = mCamera.fovY;
                mCamera.aspect = pr["aspectRatio"].asFloat(5.0f / 3.0f);
                mCamera.near = pr["near"].asFloat(26.5f);
                mCamera.far = pr["far"].asFloat(1000);
            }
        }
    }
    mCamera.update();
    auto bg = resource("BannerBG");
    if (!bg) return false;
    mWall = std::make_unique<ModelInstance>(bg);
    mWall->layer = 0;
    themeWall();
    mWall->play("BannerBG_Loop", 1, 1, WALL_PHASE);
    return true;
}

void CtrTop::sceneIn() {
    if (!mWall) return;
    mWall->play("BannerBG_SceneIn");
    // launcher:shown (scene3d.ts syncWall): the loop is started again in phase with the menu's
    // first drawn frame, which makes it the newest player (it wins on shared channels)
    mWall->play("BannerBG_Loop", 1, 1, WALL_PHASE);
}

void CtrTop::focus(const TopFocus& f) {
    BannerSpec spec;
    bool show = true;
    switch (f.kind) {
        case TopFocus::Empty:
            spec.type = Default; spec.model = "BannerDef"; spec.animations = {"BannerDef_anim00", "BannerDef"};
            break;
        case TopFocus::Folder:
            spec.type = FolderT; spec.model = "BannerFolder"; spec.animations = {"BannerFolder"};
            spec.meshes = {"Folder_00", "Text"}; spec.nameScale = 0.74f;
            break;
        case TopFocus::Title:
            if (f.cbmd) {
                // the title's own CBMD model, COMMON played in repeat mode, facing the camera
                // (titlebanner.ts)
                spec.type = TitleT; spec.model = "rom:" + f.cbmd->rom; spec.animations = {"COMMON"};
                spec.loop = true; spec.turn = false;
            } else if (f.icon && f.dsIcon) {
                spec.type = DS; spec.model = "BannerDS"; spec.animations = {"BannerDS"};
            } else if (f.icon) {
                spec.type = DSiWare; spec.model = "BannerDSiWare"; spec.animations = {"BannerDSiWare"};
            } else {
                spec.type = Banner2D; spec.model = "Banner2D"; spec.nameTexture = "Dmy_00";
            }
            break;
        case TopFocus::Bar: {
            static const struct { int type; const char* model; } kBar[6] = {
                {-1, ""}, {AppletMemo, "BannerAppletMemo"}, {AppletFriend, "BannerAppletFriend"},
                {AppletNews, "BannerAppletNews"}, {AppletWeb, "BannerAppletWeb"}, {AppletMvs, "BannerAppletMvs"}};
            if (f.bar < 1 || f.bar > 5) { show = false; break; }
            spec.type = kBar[f.bar].type;
            spec.model = kBar[f.bar].model;
            spec.animations = {spec.model};
            break;
        }
        default:
            show = false;
            break;
    }
    const std::string key = show ? spec.model + "|" + f.key + "|" + f.plate + "|" + std::to_string(f.icon) : std::string();
    if (key == mWantedKey) {
        if (mBanner) mBanner->shown = true;
        return;
    }
    mWantedKey = key;
    if (onBannerLeave) onBannerLeave();
    if (mBanner) {
        mBanner->shown = false;
        mLeaving.push_back(std::move(mBanner));
    }
    mHasPending = show;
    if (!show) return;
    mPendingFocus = f;
    mPendingSpec = spec;
    const bool boot = !mAttachedOnce;
    mPendingLeft = boot ? BOOT_DELAY : (spec.type == TitleT ? SHOW_DELAY_TITLE : SHOW_DELAY_MENU);
}

// nametex.ts: black cbf_std glyphs centred on a white 256x64 plate, stored upside down.
// The name plate's pixels (RGBA, upside down as the model samples it) at a constant font size:
// a line wider than the plate starts at its left margin shifted by `offsets[line]` (the marquee);
// `overflow` gets how far each line runs past the plate (0 when it fits, centred).
void CtrTop::rasterPlate(const std::string& text, float size, const std::vector<float>& offsets,
                         std::vector<uint8_t>* outPx, std::vector<float>* overflow) {
    const int W = kPlateW, H = kPlateH;
    std::shared_ptr<Font> font = mAssets.font("cbf_std");
    // the console draws a title's name into the plate through BnrDsTitle_00's T_Title_00: font
    // size 15.5 x 18.6 against the font's own width and height (its natural proportions; the
    // 0.84 x 0.65 measured on the applets' baked placeholders drew titles a quarter too wide)
    const float SX = font && font->width > 0 ? 15.5f / font->width : 0.6f;
    const float SY = font && font->height > 0 ? 18.6f / font->height : 0.62f;
    std::vector<uint8_t> ink((size_t)W * H, 0);
    if (font) {
        std::vector<std::u32string> lines;
        std::u32string all = utf8ToU32(text), cur;
        for (char32_t c : all) {
            if (c == U'\n') { lines.push_back(cur); cur.clear(); } else cur.push_back(c);
        }
        lines.push_back(cur);
        std::vector<float> adv;
        const float scale = size * kPlateRes;   // one size for every name (a long one scrolls)
        if (overflow) overflow->clear();
        for (const auto& l : lines) {
            float a = 0;
            for (char32_t c : l) if (const Glyph* g = font->glyph(c)) a += g->charWidth;
            adv.push_back(a * SX);
            if (overflow) overflow->push_back(std::max(0.0f, a * SX * scale - (W - 2 * kPlateMargin)));
        }
        const float lineHeight = font->cellHeight * SY * scale;
        float top = (H - lineHeight * lines.size()) / 2;
        // glyphs from their distance fields (Font::cellSdf at kPlateSdf), so the plate is sharp at
        // its doubled size and when the 3D pass magnifies it
        const int cw = (font->cellWidth + 2) * kPlateSdf, ch = (font->cellHeight + 2) * kPlateSdf;
        const float texelsPerPixel = font->cellHeight * kPlateSdf / std::max(lineHeight, 1.0f);
        if (mPlateSdf.size() > 256) mPlateSdf.clear();
        for (size_t li = 0; li < lines.size(); li++) {
            const bool fits = adv[li] * scale <= W - 2 * kPlateMargin;
            float x = fits ? (W - adv[li] * scale) / 2 : kPlateMargin - (li < offsets.size() ? offsets[li] : 0.0f);
            for (char32_t c : lines[li]) {
                const Glyph* g = font->glyph(c);
                if (!g) continue;
                const float gw = g->glyphWidth * SX * scale;
                const float gx = x + g->left * SX * scale;
                auto found = mPlateSdf.find(g);
                if (found == mPlateSdf.end()) {
                    std::vector<uint8_t> f;
                    if (!font->cellSdf(g, kPlateSdf, f)) f.clear();
                    found = mPlateSdf.emplace(g, std::move(f)).first;
                }
                const std::vector<uint8_t>& cell = found->second;
                if (g->glyphWidth > 0 && !cell.empty()) {
                    // the glyph's distance field over the box, one screen pixel of edge
                    const int x0 = std::max(0, (int)floorf(gx)), x1 = std::min(W, (int)ceilf(gx + gw));
                    const int y0 = std::max(0, (int)floorf(top)), y1 = std::min(H, (int)ceilf(top + lineHeight));
                    for (int py = y0; py < y1; py++) {
                        const float v = ((py + 0.5f) - top) / lineHeight * font->cellHeight - 0.5f + 1;
                        for (int px = x0; px < x1; px++) {
                            const float u0 = ((px + 0.5f) - gx) / gw * g->glyphWidth - 0.5f + 1;
                            const float u = (u0 + 0.5f) * kPlateSdf - 0.5f, vv = (v + 0.5f) * kPlateSdf - 0.5f;
                            const int iu = (int)floorf(u), iv = (int)floorf(vv);
                            const float fu = u - iu, fv = vv - iv;
                            auto at = [&](int xx, int yy) -> float {
                                xx = std::clamp(xx, 0, cw - 1);
                                yy = std::clamp(yy, 0, ch - 1);
                                return cell[(size_t)yy * cw + xx];
                            };
                            const float f = (at(iu, iv) * (1 - fu) + at(iu + 1, iv) * fu) * (1 - fv) +
                                            (at(iu, iv + 1) * (1 - fu) + at(iu + 1, iv + 1) * fu) * fv;
                            const float sd = (f - 128) / 127 * Font::kSdfRange / texelsPerPixel;
                            const float a = 255 * std::clamp(0.5f + sd, 0.0f, 1.0f);
                            uint8_t& d = ink[(size_t)py * W + px];
                            d = (uint8_t)std::min(255.0f, d + a * (1 - d / 255.0f));
                        }
                    }
                }
                x += g->charWidth * SX * scale;
            }
            top += lineHeight;
        }
    }
    std::vector<uint8_t>& px = *outPx;
    px.assign((size_t)W * H * 4, 0);
    for (int y = 0; y < H; y++) {
        const int sy = H - 1 - y;   // upside down
        for (int x = 0; x < W; x++) {
            const float a = ink[(size_t)sy * W + x] / 255.0f;
            const uint8_t c = (uint8_t)lroundf(255 * (1 - a));
            uint8_t* p = &px[((size_t)y * W + x) * 4];
            p[0] = p[1] = p[2] = c;
            p[3] = 255;
        }
    }
}

std::shared_ptr<GpuTex> CtrTop::plateTexture(const std::string& text, float size) {
    std::vector<uint8_t> px;
    mPlateText = text;
    mPlateSize = size;
    mPlateFrame = 0;
    mPlateOffsets.clear();
    rasterPlate(text, size, mPlateOffsets, &px, &mPlateOverflow);
    auto t = std::make_shared<GpuTex>();
    glGenTextures(1, &t->id);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, kPlateW, kPlateH, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    t->width = kPlateW;
    t->height = kPlateH;
    return t;
}

// The marquee of a name too long for its plate: it holds at the start, scrolls to the end, holds,
// and scrolls back (bounce), at a steady speed; the plate is drawn again only when it moves.
void CtrTop::updatePlate() {
    if (!mPlate || !mPlate->id) return;
    bool any = false;
    for (float o : mPlateOverflow) any = any || o > 0;
    if (!any) return;
    const int frame = mPlateFrame++;
    std::vector<float> offsets(mPlateOverflow.size(), 0.0f);
    for (size_t i = 0; i < mPlateOverflow.size(); i++) {
        const float ov = mPlateOverflow[i];
        if (ov <= 0) continue;
        const float run = ov / kMarqueeSpeed;
        const float period = 2 * kMarqueeHold + 2 * run;
        const float t = fmodf((float)frame, period);
        float o;
        if (t < kMarqueeHold) o = 0;
        else if (t < kMarqueeHold + run) o = (t - kMarqueeHold) * kMarqueeSpeed;
        else if (t < 2 * kMarqueeHold + run) o = ov;
        else o = ov - (t - 2 * kMarqueeHold - run) * kMarqueeSpeed;
        offsets[i] = roundf(o * 4) / 4;   // quarter pixels: a still hold draws nothing
    }
    if (offsets == mPlateOffsets) return;
    mPlateOffsets = offsets;
    std::vector<uint8_t> px;
    rasterPlate(mPlateText, mPlateSize, mPlateOffsets, &px, nullptr);
    glBindTexture(GL_TEXTURE_2D, mPlate->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, kPlateW, kPlateH, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
}

std::shared_ptr<ModelResource> CtrTop::packResource(const std::vector<uint8_t>& bytes, const std::string& what) {
    auto pack = std::make_shared<Pack>();
    if (!pack->openMemory(bytes)) return nullptr;
    auto r = std::make_shared<ModelResource>();
    if (!r->load(*pack, pack.get())) {
        ALOGW("banner model of %s failed to load", what.c_str());
        return nullptr;
    }
    r->keepAlive = pack;   // the resource reads the pack while it lives
    return r;
}

std::shared_ptr<ModelResource> CtrTop::romResource(const std::shared_ptr<const RomBanner>& b) {
    if (!b || b->modelPack.empty()) return nullptr;
    if (mRomRes && mRomResKey == b->rom) return mRomRes;
    auto r = packResource(b->modelPack, b->rom);
    if (!r) return nullptr;
    mRomRes = r;           // a leaving banner keeps its own resource through its instance
    mRomResKey = b->rom;
    return r;
}

void CtrTop::attach() {
    mHasPending = false;
    auto res = mPendingFocus.cbmd ? romResource(mPendingFocus.cbmd) : resource(mPendingSpec.model);
    if (!res) return;
    if (mPendingFocus.icon && res->hasTexture("DmyIcon_00")) {
        auto t = std::make_shared<GpuTex>();
        t->id = mPendingFocus.icon;
        t->width = mPendingFocus.iconW;
        t->height = mPendingFocus.iconH;
        t->owned = false;   // the home owns the icon texture
        res->setTexture("DmyIcon_00", t);
    }
    if (mPendingFocus.cbmd && onBannerSound) onBannerSound(mPendingFocus.cbmd);
    if (!mPendingFocus.plate.empty() && res->hasTexture(mPendingSpec.nameTexture)) {
        mPlate = plateTexture(mPendingFocus.plate, mPendingSpec.nameScale);
        res->setTexture(mPendingSpec.nameTexture, mPlate);
    }
    auto b = std::make_unique<Banner>();
    b->spec = mPendingSpec;
    b->model = std::make_unique<ModelInstance>(res);
    b->model->layer = 1;
    themeBanner(*b->model, b->spec.type);
    for (const std::string& a : b->spec.animations) b->model->play(a, b->spec.loop ? 1 : -1, b->spec.paused ? 0.0f : 1.0f);
    if (!b->spec.meshes.empty()) {
        for (size_t i = 0; i < res->meshes.size(); i++)
            if (std::find(b->spec.meshes.begin(), b->spec.meshes.end(), res->meshes[i].node) == b->spec.meshes.end())
                b->model->hiddenMeshes.insert((int)i);
    }
    // the language slot's own model, its animations in repeat mode like COMMON
    if (mPendingFocus.cbmd && !mPendingFocus.cbmd->languagePack.empty()) {
        if (auto lr = packResource(mPendingFocus.cbmd->languagePack, mPendingFocus.cbmd->rom + " (language)")) {
            b->extra = std::make_unique<ModelInstance>(lr);
            b->extra->layer = 1;
            for (const auto& a : lr->skeletal) b->extra->play(a.name, 1);
            for (const auto& a : lr->material) b->extra->play(a.name, 1);
        }
    }
    mBanner = std::move(b);
    mAttachedOnce = true;
}

// Material colour indices: emission ambient diffuse specular0 specular1 constant0..5.
namespace {
const int kConstant0 = 5;

V4 fileColor(const MaterialState& m, int index, float alpha) {
    V4 c = m.source->colors[index];
    c[3] = alpha;
    return c;
}
}  // namespace

void CtrTop::setTheme(const ThemeBody* body) {
    mTheme = body;   // always applied: a body may be loaded again into the same storage
    themeWall();
    if (mBanner) themeBanner(*mBanner->model, mBanner->spec.type);
}

// scene3d.ts setTheme: the top colour block of a colour theme on the wall material mt_BG (rgb
// and 1.0 to constant 3, the opacity byte to the alpha of constant 1, the gradient byte to
// constant 2, 1.0 to constant 5; colorTexture themes give the last two alphas as bytes 5 and 6).
void CtrTop::themeWall() {
    MaterialState* m = mWall ? mWall->findMaterial("mt_BG") : nullptr;
    if (!m || !m->source) return;
    m->overrides.clear();
    const ThemeTop* top = mTheme ? &mTheme->top : nullptr;
    if (top && top->hasColor && (top->drawType == "color" || top->drawType == "colorTexture")) {
        const bool textured = top->drawType == "colorTexture";
        const float alpha5 = textured ? top->unknown05 * 0.01f : 1.0f;
        const float alpha3 = textured ? top->unknown06 * 0.01f : 1.0f;
        m->overrides[kConstant0 + 3] = {top->rgb[0] * 0.01f, top->rgb[1] * 0.01f, top->rgb[2] * 0.01f, alpha3};
        m->overrides[kConstant0 + 1] = fileColor(*m, kConstant0 + 1, top->opacity * 0.01f);
        m->overrides[kConstant0 + 2] = fileColor(*m, kConstant0 + 2, top->gradient * 0.01f);
        m->overrides[kConstant0 + 5] = fileColor(*m, kConstant0 + 5, alpha5);
    }
    // scene3d.ts setPatterns: a colorTexture theme's two 64x64 L8 patterns replace the wall's
    // mappers 1 and 2 (BG_64_00); a mapper without one keeps the file texture
    m->textureOverrides.clear();
    for (int i = 0; i < 2; i++) {   // the previous theme's patterns
        auto& texs = mWall->resource->textures;
        auto old = texs.find("theme:pattern" + std::to_string(i));
        if (old == texs.end()) continue;
        if (old->second && old->second->owned && old->second->id) glDeleteTextures(1, &old->second->id);
        texs.erase(old);
    }
    if (top && top->drawType == "colorTexture") {
        const ThemeImage* patterns[2] = {&mTheme->topPattern, &mTheme->topPatternExtra};
        for (int i = 0; i < 2; i++) {
            const ThemeImage& img = *patterns[i];
            if (img.empty()) continue;
            auto t = std::make_shared<GpuTex>();
            glGenTextures(1, &t->id);
            glBindTexture(GL_TEXTURE_2D, t->id);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (GLsizei)img.texW, (GLsizei)img.texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, img.rgba.data());
            glGenerateMipmap(GL_TEXTURE_2D);   // the mapper may sample with mipmaps
            t->width = (int)img.texW;
            t->height = (int)img.texH;
            t->owned = true;
            const std::string name = "theme:pattern" + std::to_string(i);
            mWall->resource->setTexture(name, t);
            m->textureOverrides[i + 1] = name;
        }
    }
    m->reset();
}

// scene3d.ts themeBanner (FUN_0024b9d8): a folder banner takes the theme's folder colours (dark
// into constant 0, main into constant 1 of mt_folder_00, scaled by 1/255, alpha 1).
void CtrTop::themeBanner(ModelInstance& model, int type) {
    if (type != FolderT) return;
    MaterialState* m = model.findMaterial("mt_folder_00");
    if (!m || !m->source) return;
    m->overrides.clear();
    const ThemeBlock* folder = mTheme ? &mTheme->blocks[TB_FOLDER] : nullptr;
    if (folder && folder->present) {
        auto rgb = [](const std::array<uint8_t, 3>& v) { return V4{v[0] / 255.0f, v[1] / 255.0f, v[2] / 255.0f, 1.0f}; };
        if (const auto* d = folder->field("dark")) m->overrides[kConstant0 + 0] = rgb(*d);
        if (const auto* mn = folder->field("main")) m->overrides[kConstant0 + 1] = rgb(*mn);
    }
    m->reset();
}

void CtrTop::update() {
    if (mHasPending && --mPendingLeft <= 0) attach();
    updatePlate();
    if (mWall) mWall->update(1);
    for (auto& b : mLeaving) { b->update(); b->model->update(1); if (b->extra) b->extra->update(1); }
    if (mBanner) { mBanner->update(); mBanner->model->update(1); if (mBanner->extra) mBanner->extra->update(1); }
    mLeaving.erase(std::remove_if(mLeaving.begin(), mLeaving.end(), [](const std::unique_ptr<Banner>& b) { return b->gone(); }),
                   mLeaving.end());
}

void CtrTop::draw(const float proj[16], int vpX, int vpY, int vpW, int vpH, float width, float height) {
    (void)vpX; (void)vpY;
    if (!mWall) return;
    // wider screens see more of the wall sideways (the aspect), taller ones more of it up and
    // down: the vertical field of view grows with the height, the horizontal one is kept
    const float aspect = width / height;
    const float fov = 2 * atanf(tanf(mBaseFovY / 2) * height / 240.0f);
    if (fabsf(mCamera.aspect - aspect) > 1e-4f || fabsf(mCamera.fovY - fov) > 1e-6f) {
        mCamera.aspect = aspect;
        mCamera.fovY = fov;
        mCamera.update();
    }
    // pixel size of the screen on the panel (rotation aware), capped
    const float wx = hypotf(proj[0] * vpW * 0.5f, proj[1] * vpH * 0.5f) * width;
    const float wy = hypotf(proj[4] * vpW * 0.5f, proj[5] * vpH * 0.5f) * height;
    const float s = std::min({wx / width, wy / height, (float)mMaxScale});
    const int w = std::max(1, (int)lroundf(width * std::max(1.0f, s))), h = std::max(1, (int)lroundf(height * std::max(1.0f, s)));
    std::vector<ModelInstance*> models;
    if (!mWallHidden) models.push_back(mWall.get());
    if (!mBannerHidden) {
        for (auto& b : mLeaving) {
            models.push_back(b->model.get());
            if (b->extra) models.push_back(b->extra.get());
        }
        if (mBanner) {
            models.push_back(mBanner->model.get());
            if (mBanner->extra) models.push_back(mBanner->extra.get());
        }
    }
    std::function<void(int, int)> under;
    if (mWallHidden && underlay) {
        // the layout space of the screen onto the target (y up, as the blit samples it)
        const float lp[16] = {2 / width, 0, 0, 0, 0, 2 / height, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        under = [this, lp](int tw, int th) { underlay(tw, th, lp); };
    }
    const GLuint tex = mRenderer.render(mCamera, models, w, h, TOP_CLEAR, under);
    if (CtrHome::perfMark) CtrHome::perfMark("top3dBlit");
    // composite into the top screen rectangle
    if (!mBlitProgram) {
        GLuint vs = compileBlit(GL_VERTEX_SHADER, kBlitVs), fs = compileBlit(GL_FRAGMENT_SHADER, kBlitFs);
        mBlitProgram = glCreateProgram();
        glAttachShader(mBlitProgram, vs);
        glAttachShader(mBlitProgram, fs);
        glBindAttribLocation(mBlitProgram, 0, "a_pos");
        glBindAttribLocation(mBlitProgram, 1, "a_uv");
        glLinkProgram(mBlitProgram);
        glDeleteShader(vs);
        glDeleteShader(fs);
        mBlitProj = glGetUniformLocation(mBlitProgram, "u_proj");
        glUseProgram(mBlitProgram);
        glUniform1i(glGetUniformLocation(mBlitProgram, "u_tex"), 0);
    }
    const float hw = width / 2, hh = height / 2;
    const float quad[] = {-hw, -hh, 0, 0, hw, -hh, 1, 0, -hw, hh, 0, 1, hw, hh, 1, 1};
    glUseProgram(mBlitProgram);
    glUniformMatrix4fv(mBlitProj, 1, GL_FALSE, proj);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glDisable(GL_BLEND);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 16, quad);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 16, quad + 2);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glUseProgram(0);
}

}  // namespace ctr
}  // namespace android
