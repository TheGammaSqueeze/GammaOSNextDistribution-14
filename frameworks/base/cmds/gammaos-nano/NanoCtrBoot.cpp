// NanoCtrBoot - the 3DS theme's cold boot splash (see NanoCtrBoot.h).
#define LOG_TAG "NanoCtr"

#include "NanoCtrBoot.h"

#include <algorithm>
#include <cmath>
#include <log/log.h>

namespace android {
namespace ctr {

static const char* kArchive = "logo";
static const char* kTopLayout = "NintendoLogo_U_00";
static const char* const kSceneNames[] = {"SceneOutA", "SceneOutB", "SceneOutC"};
// the logo textures are drawn at 8 times the layout size; mipmaps keep them smooth on panels that
// show the splash smaller than that
static const char* const kLogoTextures[] = {"3dsLogo_00", "3dsLogo_02", "3dsLogo_03"};
static const char* const kSweepPanes[] = {"P_Red_00", "P_Red_01", "P_Red_02", "P_Blk_00", "P_Blk_01", "P_Blk_02"};
static const char* kLogoGroup = "N_W_00";   // the sweeps (P_Red_0x with their P_Blk) and the logo
// The sweeps are six full screen layers of up to four combiner stages and three texture reads
// each: drawn at 1080p they took the Duo Lite's GPU from 40 % to 80 % busy (at a raised clock)
// and dropped frames. They are soft gradients, so they are drawn into a buffer at this fraction of
// the screen's size and scaled up once (measured: 0.5 79 %, 0.33 61 %, 0.25 58 %, no visible
// difference); the logo is drawn over them at full resolution.
static constexpr float kSweepScale = 0.25f;

static const char* kBlitVs =
    "attribute vec2 a_pos;\n"
    "uniform mat4 u_proj;\n"
    "uniform vec2 u_half;\n"
    "varying vec2 v_uv;\n"
    "void main() {\n"
    "    v_uv = a_pos * 0.5 + 0.5;\n"
    "    gl_Position = u_proj * vec4(a_pos * u_half, 0.0, 1.0);\n"
    "}\n";
static const char* kBlitFs =
    "precision mediump float;\n"
    "uniform sampler2D u_tex;\n"
    "varying vec2 v_uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(u_tex, v_uv).rgb, 1.0); }\n";

CtrBootSplash::CtrBootSplash(std::string assetRoot) : mAssets(std::make_unique<Assets>(std::move(assetRoot))) {}

CtrBootSplash::~CtrBootSplash() {
    mAnim = nullptr;
    mTop.reset();   // the layout before the assets it draws from
    mAssets.reset();
}

bool CtrBootSplash::load() {
    if (mTop) return true;
    mTop = mAssets->create(kArchive, kTopLayout);
    if (!mTop) {
        ALOGE("boot splash: %s/%s missing", kArchive, kTopLayout);
        return false;
    }
    for (const char* name : kLogoTextures) {
        TexturePtr t = mAssets->layoutTexture(kArchive, name);
        if (!t || !t->id || t->mipmapped) continue;
        glBindTexture(GL_TEXTURE_2D, t->id);
        glGenerateMipmap(GL_TEXTURE_2D);
        t->mipmapped = true;
    }
    glBindTexture(GL_TEXTURE_2D, 0);
    for (const char* name : kSweepPanes) {
        if (Pane* p = mTop->pane(name)) mSweeps.push_back({p, p->texCoords});
    }
    // what N_W_00 draws, in order: the sweep subtrees go into the low resolution pass
    if (Pane* g = mTop->pane(kLogoGroup)) {
        for (Pane* c : g->children) {
            if (c->name.rfind("P_Red_", 0) == 0) mSweepRoots.push_back(c);
            else mLogoPanes.push_back(c);
        }
    }
    return true;
}

void CtrBootSplash::enter(Scene s) {
    if (s == mScene) return;
    if (mAnim) {
        // the next scene starts from where this one ended (SceneOutB keys only the sweeps and
        // the wordmark, SceneOutC only the fade)
        if (mScene == SceneA) {
            mAnim->seek(mAnim->lastFrame());
            mAnim->apply();
        }
        mTop->stop(mAnim->id);
        mAnim = nullptr;
    }
    mScene = s;
    if (s == None) return;
    PlayOptions opt;
    opt.paused = true;   // the boot clock seeks it
    mAnim = mTop->play(kSceneNames[s], opt);
}

bool CtrBootSplash::pose(double f, double leaveAt) {
    if (!mTop) return false;
    const double lf = f - kBootLogoStart;
    Scene s;
    double at;
    if (leaveAt >= 0) {
        s = SceneC;
        at = f - leaveAt;
        if (mScene == SceneA) enter(SceneB);   // a dismissal is only taken in the hold
    } else if (lf < 0) {
        return false;
    } else if (lf < kBootSceneA) {
        s = SceneA;
        at = lf;
    } else {
        s = SceneB;
        at = fmod(lf - kBootSceneA, kBootSceneB);
    }
    enter(s);
    if (!mAnim) return false;
    mAnim->seek((float)at);
    mTop->refresh();   // applies the posed animator and the pane matrices
    return true;
}

// The sweeps are full screen panes mapping a gradient across 400 x 240. A wider (or taller) screen
// grows them to the screen and extends their texture coordinates by the same factor about the
// centre, so the arcs keep their shape and continue past the design edges instead of stretching.
void CtrBootSplash::widen(float lw, float lh) {
    if (lw == mLw && lh == mLh) return;
    mLw = lw;
    mLh = lh;
    const float ku = lw / 400.0f, kv = lh / 240.0f;
    for (Sweep& s : mSweeps) {
        s.pane->growW = lw - s.pane->size[0];
        s.pane->growH = lh - s.pane->size[1];
        s.pane->texCoords = s.uv;
        for (QuadUv& q : s.pane->texCoords) {
            const float cu = (q[0][0] + q[1][0]) * 0.5f, cv = (q[0][1] + q[2][1]) * 0.5f;
            for (Vec2& v : q) {
                v[0] = cu + (v[0] - cu) * ku;
                v[1] = cv + (v[1] - cv) * kv;
            }
        }
    }
}

bool CtrBootSplash::sweepBuffer(int w, int h) {
    if (mFbo && w == mFboW && h == mFboH) return true;
    if (!mFboTex) glGenTextures(1, &mFboTex);
    glBindTexture(GL_TEXTURE_2D, mFboTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (!mFbo) glGenFramebuffers(1, &mFbo);
    GLint prev = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prev);
    glBindFramebuffer(GL_FRAMEBUFFER, mFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mFboTex, 0);
    const bool ok = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prev);
    if (!ok) {
        ALOGE("boot splash: sweep buffer %dx%d incomplete", w, h);
        return false;
    }
    mFboW = w;
    mFboH = h;
    if (!mBlitProgram) {
        static const char* const kAttribs[] = {"a_pos"};
        mBlitProgram = buildProgram(kBlitVs, kBlitFs, kAttribs, 1);
        if (mBlitProgram) {
            mBlitProj = glGetUniformLocation(mBlitProgram, "u_proj");
            mBlitHalf = glGetUniformLocation(mBlitProgram, "u_half");
            glUseProgram(mBlitProgram);
            glUniform1i(glGetUniformLocation(mBlitProgram, "u_tex"), 0);
            glUseProgram(0);
        }
    }
    return mBlitProgram != 0;
}

void CtrBootSplash::draw(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh,
                         float screenW, float screenH) {
    if (!mTop || mScene == None) return;
    widen(lw, lh);
    Renderer& r = mAssets->renderer();
    mTop->layout().calculate();
    // 1. the sweeps into the low resolution buffer (cleared to the splash's black)
    const int fw = std::max(1, (int)lroundf(screenW * kSweepScale));
    const int fh = std::max(1, (int)lroundf(screenH * kSweepScale));
    const bool lowRes = !mSweepRoots.empty() && sweepBuffer(fw, fh);
    if (lowRes) {
        GLint prevFbo = 0, prevVp[4];
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
        glGetIntegerv(GL_VIEWPORT, prevVp);
        glBindFramebuffer(GL_FRAMEBUFFER, mFbo);
        glViewport(0, 0, fw, fh);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        // layout space onto the whole buffer, y up (row 0 of the texture is the screen's bottom);
        // depth flattened like CtrHome::projection (the sweeps sit at z -11 to -40)
        float fp[16] = {2.0f / lw, 0, 0, 0, 0, 2.0f / lh, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        r.setViewport(0, 0, fw, fh);
        r.beginWith(fp);
        for (Pane* p : mSweepRoots) r.drawPane(mTop->layout(), p);
        r.end();
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
        // 2. scaled up over the screen, opaque (the splash is black under it anyway)
        static const float kQuad[] = {-1, -1, 1, -1, -1, 1, 1, 1};
        glDisable(GL_BLEND);
        glUseProgram(mBlitProgram);
        glUniformMatrix4fv(mBlitProj, 1, GL_FALSE, proj);
        glUniform2f(mBlitHalf, lw * 0.5f, lh * 0.5f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mFboTex);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, kQuad);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
        glEnable(GL_BLEND);
    }
    // 3. the logo (or everything, when the buffer is unavailable) at full resolution
    r.setViewport(vpX, vpY, vpW, vpH);
    r.beginWith(proj);
    r.setClip(-lw / 2, -lh / 2, lw, lh);
    if (lowRes) {
        for (Pane* p : mLogoPanes) r.drawPane(mTop->layout(), p);
    } else {
        r.drawLayout(mTop->layout());
    }
    r.clearClip();
    r.end();
}

void CtrBootSplash::releaseGl() {
    if (mFbo) glDeleteFramebuffers(1, &mFbo);
    if (mFboTex) glDeleteTextures(1, &mFboTex);
    if (mBlitProgram) glDeleteProgram(mBlitProgram);
    mFbo = mFboTex = mBlitProgram = 0;
    mFboW = mFboH = 0;
    mAnim = nullptr;
    mScene = None;
    mSweeps.clear();
    mSweepRoots.clear();
    mLogoPanes.clear();
    mTop.reset();
    if (mAssets) mAssets->releaseGl();
}

}  // namespace ctr
}  // namespace android
