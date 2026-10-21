// NanoCtrTop - see NanoCtrTop.h.
#include "NanoCtrTop.h"

#include <cstdio>
#include "NanoCtrArt.h"
#include "NanoCtrHome.h"

#include <algorithm>
#include <atomic>
#include <thread>
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
// the wall texture that receives the suspended title's upper screen (mt_BG texture 0)
const char* const APP_TEXTURE = "BG_DmyApp_00";
const int TURN_FRAMES = 600;
// How much a turning banner's spin picks up with the grid's scroll speed (layout units a frame):
// a hard flick of ~30 a frame spins it ~5x, settling with the inertia.
const float SPIN_GAIN = 0.15f;
// The most frames a banner waits for its folder glyph before showing anyway (the build is ~5).
const int GLYPH_WAIT_FRAMES = 30;
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
uniform vec4 u_tint;
void main() { gl_FragColor = texture2D(u_tex, v_uv) * u_tint; }   // opaque unless the wall is hidden
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

void CtrTop::Banner::update(float turnStep) {
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
    turnFrame = fmodf(turnFrame + turnStep, (float)TURN_FRAMES);
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
    mGlyphs.clear();   // their GL objects go with them
    mGlyphJobs.clear();   // a result landing after this would upload into a gone context
    GlyphMesh::releaseProgram();
    mWall.reset();
    mWallOut = false;
    mAppEnding.clear();
    mAppTex.reset();
    mAppTexOriginal.reset();
    mAppTexSaved = false;
    for (auto& r : mResources) if (r.second) r.second->releaseGl();
    mResources.clear();
    if (mBlitProgram) glDeleteProgram(mBlitProgram);
    mBlitProgram = 0;
    mPlate.reset();
}

void CtrTop::parkGl() { mRenderer.releaseGl(); }

void CtrTop::setAppImage(GLuint texture, int width, int height) {
    auto bg = resource("BannerBG");
    if (!bg) return;
    if (!mAppTexSaved) {
        auto it = bg->textures.find(APP_TEXTURE);
        mAppTexOriginal = it == bg->textures.end() ? nullptr : it->second;
        mAppTexSaved = true;
    }
    mAppTex = std::make_shared<GpuTex>();
    mAppTex->id = texture;
    mAppTex->width = width;
    mAppTex->height = height;
    mAppTex->owned = false;   // the caller's texture
    bg->setTexture(APP_TEXTURE, mAppTex);
}

void CtrTop::setInfoArt(unsigned box, int boxW, int boxH, unsigned fan, int fanW, int fanH) {
    mInfoBox = box; mInfoBoxW = boxW; mInfoBoxH = boxH;
    mInfoFan = fan; mInfoFanW = fanW; mInfoFanH = fanH;
}

void CtrTop::clearAppImage() {
    if (!mAppTexSaved) return;
    if (auto bg = resource("BannerBG")) {
        if (mAppTexOriginal) bg->setTexture(APP_TEXTURE, mAppTexOriginal);
        else bg->textures.erase(APP_TEXTURE);
    }
    mAppTex.reset();
    mAppTexOriginal.reset();
    mAppTexSaved = false;
}

void CtrTop::setWallOut() {
    if (!mWall || mWallOut) return;
    mWall->play("BannerBG_SceneOut", 0, 1, 100000);   // its last pose at once
    mWallOut = true;
}

void CtrTop::resetApp() {
    if (!mWall) return;
    for (const char* n : {"BannerBG_AppPause", "BannerBG_AppRestart", "BannerBG_AppQuit"}) mWall->stop(n);
    mAppEnding.clear();
    if (mWallOut) {
        mWall->play("BannerBG_SceneIn", 0, 1, 100000);   // its last pose at once
        mWallOut = false;
    }
}

// scene3d.ts followApp (FUN_00286b28)
void CtrTop::followApp(AppState state, AppBanner banner) {
    if (!mWall) return;
    static const char* kNames[4] = {"", "BannerBG_AppPause", "BannerBG_AppRestart", "BannerBG_AppQuit"};
    if (banner == AppBanner::Pause) {
        mWall->stop(kNames[2]);
        mWall->stop(kNames[3]);
        mAppEnding.clear();
    } else if (banner != AppBanner::None) {
        mAppEnding = kNames[(int)banner];
    }
    if (banner != AppBanner::None) mWall->play(kNames[(int)banner], 0);
    const bool out = state == AppState::Running || state == AppState::Resumed;
    if (out != mWallOut) mWall->play(out ? "BannerBG_SceneOut" : "BannerBG_SceneIn", 0);
    mWallOut = out;
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
            // a game system's folder: its icon as a solid in place of the folder (the name plate,
            // the folder's motion and its colours stay)
            if (f.glyph) spec.meshes = {"Text"};
            break;
        case TopFocus::Title:
            if (std::find(mSuspendedKeys.begin(), mSuspendedKeys.end(), f.key) != mSuspendedKeys.end()) {
                // the suspended title shows no banner: the wall holds its screen (banner.md)
                show = false;
            } else if (f.cbmd) {
                // the title's own CBMD model, COMMON played in repeat mode, facing the camera
                // (titlebanner.ts)
                spec.type = TitleT; spec.model = "rom:" + f.cbmd->rom; spec.animations = {"COMMON"};
                spec.loop = true; spec.turn = false;
            } else if (f.icon && f.dsIcon) {
                spec.type = DS; spec.model = "BannerDS"; spec.animations = {"BannerDS"};
            } else if (f.icon) {
                spec.type = DSiWare; spec.model = "BannerDSiWare"; spec.animations = {"BannerDSiWare"};
            } else {
                spec.type = Banner2D; spec.model = "Banner2D"; spec.nameTexture = "Dmy_00"; spec.plateAspect = 2;
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
            // nano's category icon as a solid inside the applet's glass cube, in place of the
            // console's applet object
            // (mt_Backface is the white plate in the object's silhouette behind it: it goes too)
            if (f.glyph) spec.hideMaterials = {"mt_Body", "mt_Backface"};
            break;
        }
        default:
            show = false;
            break;
    }
    char tintKey[48] = "";
    if (f.glyphTinted) snprintf(tintKey, sizeof(tintKey), "|%.3f,%.3f,%.3f", f.glyphTint[0], f.glyphTint[1], f.glyphTint[2]);
    const std::string key = show ? spec.model + "|" + f.key + "|" + f.plate + "|" + std::to_string(f.icon) + "|" +
                                       std::to_string(f.glyph) + (f.glyphColor ? "c" : "") + tintKey
                                 : std::string();
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
    mPendingGlyphKey.clear();
    if (!show) return;
    mPendingFocus = f;
    mPendingSpec = spec;
    const bool boot = !mAttachedOnce;
    mPendingLeft = boot ? BOOT_DELAY : (spec.type == TitleT ? SHOW_DELAY_TITLE : SHOW_DELAY_MENU);
    // A game system's folder shows its icon as a solid in place of the folder. Start building it
    // now, during the show delay, and let attach wait for it (update): on a first visit the
    // console's folder, with its placeholder texture, used to show until the glyph landed.
    if (spec.type == FolderT && f.glyph) {
        const std::string gk = f.key + "|" + std::to_string(f.glyph) + (f.glyphColor ? "c" : "");
        if (!mGlyphs.count(gk) &&
            startGlyphJob(gk, f.glyph, f.glyphW, f.glyphH, 11.0f, 9.6f, 0.0f, 2.9f, 2.6f, f.glyphColor))
            mPendingGlyphKey = gk;
    }
}

// nametex.ts: black cbf_std glyphs centred on a white 256x64 plate, stored upside down.
// The name plate (DmyText_00 / Dmy_00) at a constant font size: every line is drawn once into a
// strip of its own ink; a line wider than the plate keeps a strip as wide as the line and scrolls
// through it (the marquee), so a moving name only copies and uploads its own rows.
void CtrTop::layoutPlate(const std::string& text, float size) {
    const int W = kPlateW, H = kPlateH;
    mPlateLines.clear();
    std::shared_ptr<Font> font = mAssets.font("cbf_std");
    if (!font) return;
    // the console draws a title's name into the plate through BnrDsTitle_00's T_Title_00: font
    // size 15.5 x 18.6 against the font's own width and height (its natural proportions; the
    // 0.84 x 0.65 measured on the applets' baked placeholders drew titles a quarter too wide),
    // widened for a quad narrower than the texture (mPlateStretch)
    const float SX = (font->width > 0 ? 15.5f / font->width : 0.6f) * mPlateStretch;
    const float SY = font->height > 0 ? 18.6f / font->height : 0.62f;
    std::vector<std::u32string> lines;
    std::u32string all = utf8ToU32(text), cur;
    for (char32_t c : all) {
        if (c == U'\n') { lines.push_back(cur); cur.clear(); } else cur.push_back(c);
    }
    lines.push_back(cur);
    const float scale = size * kPlateRes;   // one size for every name (a long one scrolls)
    const float lineHeight = font->cellHeight * SY * scale;
    float top = (H - lineHeight * lines.size()) / 2;
    // glyphs from their distance fields (Font::cellSdf at kPlateSdf): sharp at the doubled size and
    // when the 3D pass magnifies the plate
    const int cw = (font->cellWidth + 2) * kPlateSdf, ch = (font->cellHeight + 2) * kPlateSdf;
    const float texelsPerPixel = font->cellHeight * kPlateSdf / std::max(lineHeight, 1.0f);
    if (mPlateSdf.size() > 256) mPlateSdf.clear();
    for (const std::u32string& line : lines) {
        float adv = 0;
        for (char32_t c : line) if (const Glyph* g = font->glyph(c)) adv += g->charWidth;
        adv *= SX * scale;
        PlateLine pl;
        pl.fits = adv <= W - 2 * kPlateMargin;
        pl.overflow = pl.fits ? 0 : adv - (W - 2 * kPlateMargin);
        pl.w = pl.fits ? W : (int)ceilf(adv + 2 * kPlateMargin);
        pl.y0 = std::max(0, (int)floorf(top));
        pl.h = std::max(0, std::min(H, (int)ceilf(top + lineHeight)) - pl.y0);
        pl.ink.assign((size_t)pl.w * std::max(pl.h, 1), 0);
        float x = pl.fits ? (W - adv) / 2 : kPlateMargin;
        for (char32_t c : line) {
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
            if (g->glyphWidth > 0 && !cell.empty() && gw > 0) {
                // a vector glyph's outline may reach quadPad() cell pixels past the bitmap width
                const float pad = font->quadPad() * SX * scale;
                const int x0 = std::max(0, (int)floorf(gx - pad)), x1 = std::min(pl.w, (int)ceilf(gx + gw + pad));
                for (int py = 0; py < pl.h; py++) {
                    const float yy = pl.y0 + py;
                    const float v = ((yy + 0.5f) - top) / lineHeight * font->cellHeight - 0.5f + 1;
                    const float vv = (v + 0.5f) * kPlateSdf - 0.5f;
                    const int iv = (int)floorf(vv);
                    const float fv = vv - iv;
                    for (int px = x0; px < x1; px++) {
                        const float u0 = ((px + 0.5f) - gx) / gw * g->glyphWidth - 0.5f + 1;
                        const float u = (u0 + 0.5f) * kPlateSdf - 0.5f;
                        const int iu = (int)floorf(u);
                        const float fu = u - iu;
                        auto at = [&](int xx, int yy2) -> float {
                            xx = std::clamp(xx, 0, cw - 1);
                            yy2 = std::clamp(yy2, 0, ch - 1);
                            return cell[(size_t)yy2 * cw + xx];
                        };
                        const float f = (at(iu, iv) * (1 - fu) + at(iu + 1, iv) * fu) * (1 - fv) +
                                        (at(iu, iv + 1) * (1 - fu) + at(iu + 1, iv + 1) * fu) * fv;
                        const float sd = (f - 128) / 127 * Font::kSdfRange / texelsPerPixel;
                        const float a = 255 * std::clamp(0.5f + sd, 0.0f, 1.0f);
                        uint8_t& d = pl.ink[(size_t)py * pl.w + px];
                        d = (uint8_t)std::min(255.0f, d + a * (1 - d / 255.0f));
                    }
                }
            }
            x += g->charWidth * SX * scale;
        }
        mPlateLines.push_back(std::move(pl));
        top += lineHeight;
    }
}

// Plate rows [y0, y0 + h) (plate space, top down) as one luminance byte per pixel, black ink
// on white, from the lines' strips at their marquee offsets; `out` gets them in upload order
// (the texture is upside down). Lines own separate rows, so each row is one span copy.
void CtrTop::composePlate(int y0, int h, std::vector<uint8_t>* out) const {
    const int W = kPlateW;
    out->assign((size_t)W * h, 255);
    for (size_t li = 0; li < mPlateLines.size(); li++) {
        const PlateLine& pl = mPlateLines[li];
        const int off = pl.fits ? 0 : (int)lroundf(li < mPlateOffsets.size() ? mPlateOffsets[li] : 0.0f);
        // destination columns [x0, x1) read source columns [x0 + off, x1 + off)
        const int x0 = std::max(0, -off), x1 = std::min(W, pl.w - off);
        if (x1 <= x0) continue;
        for (int py = 0; py < pl.h; py++) {
            const int y = pl.y0 + py - y0;
            if (y < 0 || y >= h) continue;
            uint8_t* d = out->data() + (size_t)(h - 1 - y) * W;   // upside down
            const uint8_t* src = pl.ink.data() + (size_t)py * pl.w + off;
            for (int x = x0; x < x1; x++) {
                const uint8_t c = (uint8_t)(255 - src[x]);
                if (c < d[x]) d[x] = c;   // overlapping ink keeps the darker
            }
        }
    }
}

std::shared_ptr<GpuTex> CtrTop::plateTexture(const std::string& text, float size) {
    mPlateFrame = 0;
    mPlateOffsets.clear();
    layoutPlate(text, size);
    std::vector<uint8_t> px;
    composePlate(0, kPlateH, &px);
    auto t = std::make_shared<GpuTex>();
    glGenTextures(1, &t->id);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    // one byte a pixel: it samples as (l, l, l, 1) like the grey RGBA it replaces
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, kPlateW, kPlateH, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, px.data());
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    t->width = kPlateW;
    t->height = kPlateH;
    return t;
}

// The marquee of a name too long for its plate: it holds at the start, scrolls to the end, holds,
// and scrolls back (bounce) at a steady speed; only the scrolling lines' rows are copied and
// uploaded, and only when they moved a whole pixel.
void CtrTop::updatePlate() {
    if (!mPlate || !mPlate->id) return;
    bool any = false;
    for (const PlateLine& pl : mPlateLines) any = any || !pl.fits;
    if (!any) return;
    const int frame = mPlateFrame++;
    std::vector<float>& offsets = mPlateNext;
    offsets.assign(mPlateLines.size(), 0.0f);
    int rowTop = kPlateH, rowBottom = 0;
    for (size_t i = 0; i < mPlateLines.size(); i++) {
        const PlateLine& pl = mPlateLines[i];
        if (pl.fits) continue;
        const float ov = pl.overflow;
        const float run = ov / kMarqueeSpeed;
        const float period = 2 * kMarqueeHold + 2 * run;
        const float t = fmodf((float)frame, period);
        float o;
        if (t < kMarqueeHold) o = 0;
        else if (t < kMarqueeHold + run) o = (t - kMarqueeHold) * kMarqueeSpeed;
        else if (t < 2 * kMarqueeHold + run) o = ov;
        else o = ov - (t - 2 * kMarqueeHold - run) * kMarqueeSpeed;
        offsets[i] = roundf(o);   // whole pixels: a still hold draws nothing
        rowTop = std::min(rowTop, pl.y0);
        rowBottom = std::max(rowBottom, pl.y0 + pl.h);
    }
    if (offsets == mPlateOffsets || rowBottom <= rowTop) return;
    mPlateOffsets = offsets;
    composePlate(rowTop, rowBottom - rowTop, &mPlateRows);   // reused: no allocation a frame
    glBindTexture(GL_TEXTURE_2D, mPlate->id);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    // plate rows [rowTop, rowBottom) are texture rows counted from the bottom
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, kPlateH - rowBottom, kPlateW, rowBottom - rowTop, GL_LUMINANCE, GL_UNSIGNED_BYTE, mPlateRows.data());
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
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
    mPendingGlyphKey.clear();
    auto res = mPendingFocus.cbmd ? romResource(mPendingFocus.cbmd) : resource(mPendingSpec.model);
    if (!res) return;
    if (mPendingFocus.icon && res->hasTexture("DmyIcon_00")) {
        auto t = std::make_shared<GpuTex>();
        t->id = mPendingFocus.icon;
        t->width = mPendingFocus.iconW;
        t->height = mPendingFocus.iconH;
        t->owned = false;   // the home owns the icon texture
        t->pixelArt = mPendingFocus.pixelArt;   // a DS icon's pixels stay square
        res->setTexture("DmyIcon_00", t);
    }
    if (mPendingFocus.cbmd && onBannerSound) onBannerSound(mPendingFocus.cbmd);
    if (!mPendingFocus.plate.empty() && res->hasTexture(mPendingSpec.nameTexture)) {
        mPlateStretch = 4.0f / std::max(0.5f, mPendingSpec.plateAspect);
        mPlate = plateTexture(mPendingFocus.plate, mPendingSpec.nameScale);
        res->setTexture(mPendingSpec.nameTexture, mPlate);
    }
    // the empty slot banner: nano's category pictograms on its plates (applet buttons 1..5 are the
    // Game Notes, Friend List, Notifications, Internet Browser and Miiverse plates)
    static const char* const kDefTex[6] = {nullptr, "DefBnrMemo64x64", "DefBnrFrd64x64", "DefBnrNews64x64",
                                           "DefBnrWeb64x64", "DefBnrOlv64x64"};
    static const char* const kDefMat[6] = {nullptr, "mt_01", "mt_02", "mt_03", "mt_04", "mt_00"};
    const bool defIcons = mPendingSpec.type == Default;
    if (defIcons) {
        for (int i = 1; i <= 5; i++) {
            const DefIcon& d = mDefIcons[i];
            if (!d.tex || !res->hasTexture(kDefTex[i])) continue;
            auto t = std::make_shared<GpuTex>();
            t->id = d.tex;
            t->width = d.w;
            t->height = d.h;
            t->owned = false;   // the home owns it
            res->setTexture(kDefTex[i], t);
        }
    }
    auto b = std::make_unique<Banner>();
    b->spec = mPendingSpec;
    b->model = std::make_unique<ModelInstance>(res);
    b->model->layer = 1;
    themeBanner(*b->model, b->spec.type);
    if (defIcons) {
        for (int i = 1; i <= 5; i++) {
            const DefIcon& d = mDefIcons[i];
            MaterialState* m = d.tex ? b->model->findMaterial(kDefMat[i]) : nullptr;
            if (!m) continue;
            // constant 0 is the plate's ink colour (the applet's colour on the console)
            // (pinned: BannerDef's material animation sets mt_00's constant 0 to Miiverse green)
            m->pinned[5] = V4{d.ink[0], d.ink[1], d.ink[2], m->colors[5][3]};
            m->reset();
        }
    }
    bool played = false;
    for (const std::string& a : b->spec.animations)
        played |= b->model->play(a, b->spec.loop ? 1 : -1, b->spec.paused ? 0.0f : 1.0f);
    // A title's CBMD that carries no animation at all (Mario Kart 7: a still model in the file)
    // turns on the console like the menu's own banners; one with a COMMON animation plays it
    // facing the camera (titlebanner.ts only models the latter).
    if (b->spec.type == TitleT && !played) b->spec.turn = true;
    if (!b->spec.meshes.empty()) {
        for (size_t i = 0; i < res->meshes.size(); i++)
            if (std::find(b->spec.meshes.begin(), b->spec.meshes.end(), res->meshes[i].node) == b->spec.meshes.end())
                b->model->hiddenMeshes.insert((int)i);
    }
    // a game system's folder: the system's icon built into a solid (once per folder), fitted to the
    // folder body (Folder_00: 11.8 x 10 units around y 2.9, 3.4 thick) and carried by its bone
    if (mPendingFocus.glyph && b->spec.type == FolderT) {
        for (const auto& p : res->primitives)
            if (p.mesh >= 0 && p.mesh < (int)res->meshes.size() && res->meshes[p.mesh].node == "Folder_00") b->glyphBone = p.bone;
        const std::string gk = mPendingFocus.key + "|" + std::to_string(mPendingFocus.glyph) + (mPendingFocus.glyphColor ? "c" : "");
        auto it = mGlyphs.find(gk);
        if (it != mGlyphs.end()) {
            b->glyph = it->second;
            if (!b->glyph) b->model->hiddenMeshes.clear();   // nothing built: the folder after all
        } else {
            // built off the GL thread: the folder shows as itself until the glyph lands
            b->glyphHide = b->model->hiddenMeshes;
            b->model->hiddenMeshes.clear();
            if (startGlyphJob(gk, mPendingFocus.glyph, mPendingFocus.glyphW, mPendingFocus.glyphH, 11.0f, 9.6f, 0.0f, 2.9f,
                              2.6f, mPendingFocus.glyphColor))
                b->glyphKey = gk;
        }
        b->tinted = mPendingFocus.glyphTinted;
        for (int i = 0; i < 3; i++) b->tint[i] = mPendingFocus.glyphTint[i];
    }
    // an applet banner with nano's icon: the applet object (mt_Body) and its white backing plate
    // (mt_Backface) go, the icon is built into a solid fitted to the object's bounds and carried by
    // its bone
    if (mPendingFocus.glyph && !b->spec.hideMaterials.empty()) {
        for (size_t i = 0; i < res->meshes.size(); i++) {
            const int mat = res->meshes[i].material;
            if (mat >= 0 && mat < (int)res->materials.size() &&
                std::find(b->spec.hideMaterials.begin(), b->spec.hideMaterials.end(), res->materials[mat].name) !=
                    b->spec.hideMaterials.end())
                b->model->hiddenMeshes.insert((int)i);
        }
        V3 centre{0, 0, 0}, size{0, 0, 0};
        for (const auto& p : res->primitives) {
            if (p.mesh < 0 || p.mesh >= (int)res->meshes.size()) continue;
            const auto& me = res->meshes[p.mesh];
            if (me.material < 0 || me.material >= (int)res->materials.size() || res->materials[me.material].name != "mt_Body")
                continue;
            b->glyphBone = p.bone;
            if (me.shape >= 0 && me.shape < (int)res->shapeCenters.size()) {
                centre = res->shapeCenters[me.shape];
                size = res->shapeSizes[me.shape];
            }
            break;
        }
        if (b->glyphBone >= 0 && size[0] > 0 && size[1] > 0) {
            // about the size of the object it replaces (its long side: Game Notes is a thin tilted pad)
            const float box = std::max(size[0], size[1]) * 0.8f;
            const float depth = std::max(0.8f, std::min(size[2] * 0.6f, box * 0.22f));
            const std::string gk = "bar|" + std::to_string(mPendingFocus.glyph) + "|" + b->spec.model;
            auto it = mGlyphs.find(gk);
            if (it != mGlyphs.end()) {
                b->glyph = it->second;
            } else if (startGlyphJob(gk, mPendingFocus.glyph, mPendingFocus.glyphW, mPendingFocus.glyphH, box, box, centre[0],
                                     centre[1], depth, false)) {
                b->glyphKey = gk;   // the console's object shows until the glyph lands
            }
            ALOGI("ctr top: applet glyph in %s body %.1fx%.1fx%.1f at %.1f,%.1f,%.1f bone %d", b->spec.model.c_str(), size[0],
                  size[1], size[2], centre[0], centre[1], centre[2], b->glyphBone);
        }
        if (!b->glyph) {
            if (!b->glyphKey.empty()) b->glyphHide = b->model->hiddenMeshes;   // hidden again once the glyph lands
            b->model->hiddenMeshes.clear();   // nothing built (yet): the console's object after all
        }
        b->tinted = mPendingFocus.glyphTinted;
        for (int i = 0; i < 3; i++) b->tint[i] = mPendingFocus.glyphTint[i];
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

void CtrTop::setDefaultIcon(int button, unsigned texture, int w, int h, const float ink[3]) {
    if (button < 1 || button > 5) return;
    DefIcon& d = mDefIcons[button];
    d.tex = texture;
    d.w = w;
    d.h = h;
    for (int k = 0; k < 3; k++) d.ink[k] = ink[k];
}

void CtrTop::setFolderBlock(const ThemeBlock* block) {
    if (!block && !mFolderOn) return;
    mFolderOn = block != nullptr;
    mFolder = block ? *block : ThemeBlock();
    if (mBanner) themeBanner(*mBanner->model, mBanner->spec.type);
    for (auto& b : mLeaving) themeBanner(*b->model, b->spec.type);
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
    const ThemeBlock* folder = mFolderOn ? &mFolder : mTheme ? &mTheme->blocks[TB_FOLDER] : nullptr;
    if (folder && folder->present) {
        auto rgb = [](const std::array<uint8_t, 3>& v) { return V4{v[0] / 255.0f, v[1] / 255.0f, v[2] / 255.0f, 1.0f}; };
        if (const auto* d = folder->field("dark")) m->overrides[kConstant0 + 0] = rgb(*d);
        if (const auto* mn = folder->field("main")) m->overrides[kConstant0 + 1] = rgb(*mn);
    }
    m->reset();
}

void CtrTop::drawGlyphs(const Camera3d& cam) {
    auto one = [&](Banner& b) {
        if (!b.glyph || !b.model || !b.model->visible) return;
        M4 mv;
        mat4Mul(mv, cam.view, b.model->boneMatrix(b.glyphBone));
        // the folder's colours (the theme's, else the file's): faces in its main colour, walls in
        // its dark one
        float face[3] = {0.45f, 0.78f, 0.93f}, side[3] = {0.25f, 0.55f, 0.75f};
        if (b.glyph->coloured()) {
            // the icon's own colours: the faces as they are, the walls a shade darker
            for (int i = 0; i < 3; i++) {
                face[i] = 1.0f;
                side[i] = 0.62f;
            }
        } else if (b.tinted) {
            // the system's own colour (Game Systems tint): faces in it, walls a darker shade
            for (int i = 0; i < 3; i++) {
                face[i] = b.tint[i];
                side[i] = b.tint[i] * 0.55f;
            }
        } else if (MaterialState* m = b.model->findMaterial("mt_folder_00")) {
            for (int i = 0; i < 3; i++) {
                face[i] = m->colors[kConstant0 + 1][i];
                side[i] = m->colors[kConstant0 + 0][i];
            }
        }
        // a dark body (the Black theme, a dark folder colour) would hide a glyph in its own
        // colours: light faces and mid grey walls instead
        if (!b.glyph->coloured() && 0.2126f * face[0] + 0.7152f * face[1] + 0.0722f * face[2] < 0.4f) {
            for (int i = 0; i < 3; i++) {
                face[i] = 0.92f;
                side[i] = 0.58f;
            }
        }
        // the folder model's light (Light_Folder_00: straight down and away), in view space
        const float lw[3] = {0, -0.7071f, -0.7071f};
        float light[3];
        for (int r = 0; r < 3; r++) light[r] = cam.view.m[r] * lw[0] + cam.view.m[4 + r] * lw[1] + cam.view.m[8 + r] * lw[2];
        b.glyph->draw(mv.m, cam.projection.m, face, side, light);
    };
    for (auto& b : mLeaving) one(*b);
    if (mBanner) one(*mBanner);
}

bool CtrTop::startGlyphJob(const std::string& key, unsigned texture, int w, int h, float boxW, float boxH, float cx, float cy,
                           float depth, bool colour) {
    for (const GlyphJob& j : mGlyphJobs) if (j.key == key) return true;   // already on its way
    auto rgba = std::make_shared<std::vector<uint8_t>>();
    if (!GlyphMesh::readback(texture, w, h, rgba.get())) {
        mGlyphs[key] = nullptr;   // not readable: the folder or object stays, like a failed build
        return false;
    }
    GlyphJob job;
    job.key = key;
    job.built = std::make_shared<GlyphMesh::Built>();
    job.done = std::make_shared<std::atomic<bool>>(false);
    auto built = job.built;
    auto done = job.done;
    std::thread([rgba, built, done, w, h, boxW, boxH, cx, cy, depth, colour]() {
        *built = GlyphMesh::compute(*rgba, w, h, boxW, boxH, cx, cy, depth, colour);
        done->store(true, std::memory_order_release);
    }).detach();
    mGlyphJobs.push_back(std::move(job));
    return true;
}

// A finished glyph lands: uploaded here on the GL thread, cached, and handed to the banner that
// waits for it (the meshes it stands in for hidden now).
void CtrTop::pollGlyphJobs() {
    for (size_t i = 0; i < mGlyphJobs.size();) {
        if (!mGlyphJobs[i].done->load(std::memory_order_acquire)) {
            i++;
            continue;
        }
        const std::string key = mGlyphJobs[i].key;
        auto mesh = std::make_shared<GlyphMesh>();
        if (!mesh->finish(*mGlyphJobs[i].built)) mesh.reset();
        mGlyphJobs.erase(mGlyphJobs.begin() + i);
        mGlyphs[key] = mesh;
        auto land = [&](Banner* b) {
            if (!b || b->glyphKey != key) return;
            b->glyphKey.clear();
            b->glyph = mesh;
            if (mesh && b->model) b->model->hiddenMeshes = b->glyphHide;
            b->glyphHide.clear();
        };
        land(mBanner.get());
        for (auto& b : mLeaving) land(b.get());
    }
}

void CtrTop::update() {
    pollGlyphJobs();
    if (mHasPending && --mPendingLeft <= 0) {
        // a game system's folder whose glyph is still building: hold the banner for it (bounded)
        // rather than show the folder itself first
        const bool glyphReady = mPendingGlyphKey.empty() || mGlyphs.count(mPendingGlyphKey) > 0;
        if (glyphReady || mPendingLeft <= -GLYPH_WAIT_FRAMES) attach();
    }
    updatePlate();
    if (mWall) mWall->update(1);
    if (!mAppEnding.empty() && mWall && !mWall->isPlaying(mAppEnding)) {
        // the app animations are dropped when the resume or close has played (scene3d.ts step)
        for (const char* n : {"BannerBG_AppPause", "BannerBG_AppRestart", "BannerBG_AppQuit"}) mWall->stop(n);
        mAppEnding.clear();
    }
    // the turn: one frame a frame at rest, faster the harder the grid is being flicked, easing
    // back as the grid's inertia dies (the velocity is the scroller's, decaying 5% a frame)
    const float turnStep = 1.0f + SPIN_GAIN * fabsf(mScrollVelocity);
    for (auto& b : mLeaving) { b->update(turnStep); b->model->update(1); if (b->extra) b->extra->update(1); }
    if (mBanner) { mBanner->update(turnStep); mBanner->model->update(1); if (mBanner->extra) mBanner->extra->update(1); }
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
    const float s = std::min({wx / width, wy / height, mMaxScale});
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
    std::function<void(const Camera3d&)> over;
    if (!mBannerHidden) {
        bool glyphs = mBanner && mBanner->glyph;
        for (auto& b : mLeaving) glyphs |= b->glyph != nullptr;
        if (glyphs) over = [this](const Camera3d& c) { drawGlyphs(c); };
    }
    const GLuint tex = mRenderer.render(mCamera, models, w, h, TOP_CLEAR, under, over);
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
        mBlitTint = glGetUniformLocation(mBlitProgram, "u_tint");
        glUseProgram(mBlitProgram);
        glUniform1i(glGetUniformLocation(mBlitProgram, "u_tex"), 0);
    }
    const float hw = width / 2, hh = height / 2;
    const float quad[] = {-hw, -hh, 0, 0, hw, -hh, 1, 0, -hw, hh, 0, 1, hw, hh, 1, 1};
    glUseProgram(mBlitProgram);
    glUniformMatrix4fv(mBlitProj, 1, GL_FALSE, proj);
    glUniform4f(mBlitTint, 1, 1, 1, 1);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glDisable(GL_BLEND);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 16, quad);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 16, quad + 2);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    if (mBannerHidden && (mInfoBox || mInfoFan)) drawInfoArt(width, height);
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glUseProgram(0);
}

// The Information page's art, between the status bar and the panel's title band: the fanart
// filling that strip (cropped to its aspect, dimmed so the box art reads), the box art fitted
// into it with a margin. Drawn with the blit program, which is current.
void CtrTop::drawInfoArt(float width, float height) {
    const float hw = width / 2, hh = height / 2;
    const float top = hh - 34, bottom = -hh + 56;   // the HUD bar above, the title band below
    const float areaW = width, areaH = top - bottom, cy = (top + bottom) / 2;
    auto quadFor = [&](float w, float h, float u0, float v0, float u1, float v1, float* q) {
        const float l = -w / 2, r = w / 2, b = cy - h / 2, t = cy + h / 2;
        const float v[] = {l, b, u0, v0, r, b, u1, v0, l, t, u0, v1, r, t, u1, v1};
        std::copy(v, v + 16, q);
    };
    float q[16];
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);   // straight alpha, as the scraper decodes them
    if (mInfoFan && mInfoFanW > 0 && mInfoFanH > 0) {
        // cover the strip: crop whichever axis overflows (textures are top-down: v flipped)
        const float ta = (float)mInfoFanW / mInfoFanH, aa = areaW / areaH;
        float u0 = 0, u1 = 1, v0 = 1, v1 = 0;
        if (ta > aa) { const float k = aa / ta; u0 = 0.5f - k / 2; u1 = 0.5f + k / 2; }
        else { const float k = ta / aa; v0 = 0.5f + k / 2; v1 = 0.5f - k / 2; }
        quadFor(areaW, areaH, u0, v0, u1, v1, q);
        glBindTexture(GL_TEXTURE_2D, mInfoFan);
        glUniform4f(mBlitTint, 0.35f, 0.35f, 0.35f, 1);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 16, q);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 16, q + 2);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    if (mInfoBox && mInfoBoxW > 0 && mInfoBoxH > 0) {
        const float maxH = areaH * 0.88f, maxW = areaW * 0.6f;
        float h = maxH, w = h * mInfoBoxW / mInfoBoxH;
        if (w > maxW) { w = maxW; h = w * mInfoBoxH / mInfoBoxW; }
        quadFor(w, h, 0, 1, 1, 0, q);
        glBindTexture(GL_TEXTURE_2D, mInfoBox);
        glUniform4f(mBlitTint, 1, 1, 1, 1);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 16, q);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 16, q + 2);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    glDisable(GL_BLEND);
}

}  // namespace ctr
}  // namespace android
