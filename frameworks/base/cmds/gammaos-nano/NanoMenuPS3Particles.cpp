/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "GammaOSNano"

#include "NanoMenuPS3Particles.h"
#include "NanoMenuPS3ParticleCloud.h"
#include "NanoMenuPS3Bg.h"   // ps3bg::waveDisplacementAt - couple the glitter to the real silk surface

#include <math.h>
#include <stdint.h>
#include <vector>

#include <GLES2/gl2.h>
#include <utils/Log.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace android {
namespace ps3part {

// ---- firmware constants (index.html FW_PART / PART_PROJ) --------------------
static const int   kNumParticles = 1400;
static const float kProjFx = 1.12820041f, kProjFy = 2.00568986f;   // PART_PROJ
static const float kSpecPower = 35.2904f, kSpecCoeff = 74.74f;
static const float kFresnel = 1.33319f, kExposure = 0.0390458f;
static const float kSizeNear = 0.0772033f, kGlare = 0.159705f;
static const float kSpinTimeScale = 2.74f, kDeltaTime = 0.0088883f;
static const float kAgingSpeed = 0.00285223f, kAgingVar = 0.493003f;
static const float kIridescentExp = 1.0f;
// Glitter <-> wave coupling strength. NEGATIVE: the wave's clip-space Y (the LUT's raw y/w
// deviation) is inverted relative to the particle projection's ndcY, so the surface displacement
// must be negated for the glitter to ride the silk right-side-up (the old sinusoid coupling carried
// the same negative sign). Magnitude 1.0 = ride the silk 1:1 (the particle shader applies the same
// uYFlip*uScaleY as the wave, so the vertical motion matches the surface exactly).
static const float kWaveCouple = -1.0f;
// normalized spot-light dir (eye space) from spotPos (4.16, 2.63, -7.6)
static float sLx, sLy, sLz;

// ---- tiny PRNG (xorshift32), [0,1) ------------------------------------------
static uint32_t sRng = 0x1234567u;
static inline float frand() {
    sRng ^= sRng << 13; sRng ^= sRng >> 17; sRng ^= sRng << 5;
    return (float)(sRng & 0x00ffffffu) / (float)0x01000000u;
}
// gaussian-ish jitter (sum of three uniforms), matches the web _gJ()
static inline float gJ() { return (frand() + frand() + frand() - 1.5f) * 0.08f; }

// ---- particle state ---------------------------------------------------------
struct Particle {
    float ex, ey, ez;          // live eye-space position
    float hx, hy, hz;          // eye-space home
    float vx, vy, vz;          // velocity (friction-damped)
    float age;
    float sx0, sx1, rr0, rr1;  // spinning normal phases + rates
    float tintR, tintG, tintB; // edge-reflection tint
    float bigScale;
    bool  edge;
};
static std::vector<Particle> sParts;
static std::vector<Particle> sPartsExtra;   // music "XMB Waves" doubled pool (lazy, kept warm)
static std::vector<float> sBuf;     // GPU buffer scratch: 8 floats/particle (sized 2x once extras exist)
static double sAccumMs = 0.0;
static float sMvBlend = 0.0f;       // music-vis morph: extra-pool brightness fade (0 = menu only)

// ---- GL ---------------------------------------------------------------------
static GLuint sProg = 0, sVBO = 0;
static GLint  sLocPos = -1, sLocSize = -1, sLocBright = -1, sLocColor = -1, sLocSpark = -1;
static GLint  sLocScaleX = -1, sLocScaleY = -1, sLocYFlip = -1, sLocFrameN = -1, sLocRot = -1;
static bool   sReady = false, sTried = false;

static const char* VS_PART =
    "attribute vec2 aPos;\n"        // projectEye NDC (16:9 firmware projection)
    "attribute float aSize;\n"
    "attribute float aBright;\n"
    "attribute vec3 aColor;\n"
    "attribute float aSpark;\n"
    "uniform float uScaleX;\n"
    "uniform float uScaleY;\n"
    "uniform float uYFlip;\n"
    "uniform vec4 uFrameN;\n"       // frame rect in panel NDC: (nx0, ny_bot, nx1, ny_top)
    "uniform mat2 uRotation;\n"
    "varying float vBright;\n"
    "varying vec3 vColor;\n"
    "varying float vSpark;\n"
    "void main(){\n"
    "  vBright = aBright; vColor = aColor; vSpark = aSpark;\n"
    "  vec2 p = aPos;\n"
    "  p.y *= uYFlip * uScaleY;\n"   // align with the wave (same VS_WAVECAP transform)
    "  p.x *= uScaleX;\n"
    "  vec2 uv = p * 0.5 + 0.5;\n"   // work-space 0..1 (uv.y 0=bottom)
    "  vec2 pn = vec2(mix(uFrameN.x, uFrameN.z, uv.x), mix(uFrameN.y, uFrameN.w, uv.y));\n"
    "  gl_Position = vec4(uRotation * pn, 0.0, 1.0);\n"
    "  gl_PointSize = aSize;\n"
    "}\n";

static const char* FS_PART =
    "precision mediump float;\n"
    "varying float vBright;\n"
    "varying vec3 vColor;\n"
    "varying float vSpark;\n"
    "void main(){\n"
    "  vec2 pc = gl_PointCoord * 2.0 - 1.0;\n"
    "  float d2 = dot(pc, pc);\n"
    "  if (d2 > 1.0) discard;\n"
    "  float core = exp(-d2 * 9.0);\n"
    "  float glow = exp(-d2 * 1.8) * 0.40;\n"
    "  float cross = (exp(-pc.y*pc.y*70.0) + exp(-pc.x*pc.x*70.0)) * exp(-d2*1.2);\n"
    "  float spark = cross * vSpark * 0.7;\n"
    "  float a = (core + glow + spark) * vBright;\n"
    "  float hot = clamp(vBright * 0.6 - 0.6, 0.0, 1.0);\n"
    "  vec3 col = mix(vColor, vec3(1.0), hot);\n"
    "  gl_FragColor = vec4(col * a, a);\n"   // premultiplied; drawn additive
    "}\n";

static GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof(log), nullptr, log);
               ALOGE("ps3part: shader compile failed: %s", log); glDeleteShader(s); return 0; }
    return s;
}

// ---- spawn / step (index.html spawnParticle / stepParticles) ----------------
static void spawn(Particle& p) {
    p.edge = frand() < 0.12f;
    if (p.edge) {
        float side = (frand() < 0.5f) ? -1.0f : 1.0f;
        p.hx = (side < 0.0f) ? (-5.6f - frand() * 1.1f) : (5.1f + frand() * 1.1f);
        p.hy = (frand() - 0.5f) * 0.7f;
        p.hz = -4.8f - frand() * 1.4f;
        float r = frand();
        p.tintR = (r < 0.34f) ? 1.00f : (r < 0.67f ? 0.86f : 1.00f);   // gold/silver/white
        p.tintG = (r < 0.34f) ? 0.80f : (r < 0.67f ? 0.89f : 1.00f);
        p.tintB = (r < 0.34f) ? 0.38f : (r < 0.67f ? 0.97f : 1.00f);
        p.bigScale = 1.9f + frand() * 1.5f;
    } else {
        int k = (int)(frand() * (float)kPartCloudCount);
        if (k >= kPartCloudCount) k = kPartCloudCount - 1;
        int b = k * 3;
        p.hx = kPartCloud[b]     + (frand() - 0.5f) * 1.6f;
        // Web-exact cloud Y band (index.html spawnParticle): the *0.45 centre +
        // 0.16 jitter place the projected band ON the baked wave crest. Lowering
        // the multiplier (the old 0.30) raised the band off the wave; combined
        // with the removed extra scaleY=0.8 in the render call, the band now sits
        // on the crest exactly as in the source app.
        p.hy = kPartCloud[b + 1] * 0.45f + (frand() - 0.5f) * 0.16f;
        p.hz = kPartCloud[b + 2] + (frand() - 0.5f) * 1.4f;
        p.tintR = p.tintG = p.tintB = 1.0f;
        p.bigScale = 1.0f;
    }
    p.ex = p.hx; p.ey = p.hy; p.ez = p.hz;
    p.vx = p.vy = p.vz = 0.0f;
    p.age = frand();
    p.sx0 = frand() * 2.0f * (float)M_PI;
    p.sx1 = frand() * 2.0f * (float)M_PI;
    p.rr0 = (0.5f + frand()) * kSpinTimeScale;
    p.rr1 = (0.5f + frand()) * kSpinTimeScale;
}

static inline void stepOne(Particle& p) {
    float w = -p.ez + 2.0f;
    float imp = 0.013f * (w / 8.0f);
    p.vx += gJ() * imp; p.vy += gJ() * imp; p.vz += gJ() * imp;
    p.vx *= 0.969f; p.vy *= 0.969f; p.vz *= 0.969f;
    p.ex += p.vx + (p.hx - p.ex) * 0.012f;
    p.ey += p.vy + (p.hy - p.ey) * 0.012f;
    p.ez += p.vz + (p.hz - p.ez) * 0.012f;
    p.age += kAgingSpeed * 0.9f * (1.0f + (frand() - 0.5f) * kAgingVar);
    p.sx0 += p.rr0 * kDeltaTime * 0.7f;
    p.sx1 += p.rr1 * kDeltaTime * 0.7f;
    if (p.age > 1.0f) { spawn(p); p.age = 0.0f; }
}

static void step() {
    for (Particle& p : sParts) stepOne(p);
    // Only animate the doubled music pool while the morph is active. Once it settles
    // back to 0 the extras are invisible (brightScale 0), so stepping them every frame
    // for the rest of the session (the pool stays warm) is pure waste - this kept ~1400
    // extra particles updating forever on the home menu after the first track played.
    // They thaw from their last positions on the next morph (unseen while faded out).
    if (sMvBlend > 0.0001f) for (Particle& p : sPartsExtra) stepOne(p);
}

// (The old coarse 2-sinusoid waveMotionDeltaY was replaced by ps3bg::waveDisplacementAt, which
// samples the real keyframe silk surface so the glitter follows the actual undulation.)

// ---- public API -------------------------------------------------------------
bool init() {
    if (sReady) return true;
    if (!sTried) {
        sTried = true;
        float l0x = 4.16f, l0y = 2.63f, l0z = -7.6f;
        float ll = sqrtf(l0x*l0x + l0y*l0y + l0z*l0z);
        sLx = l0x/ll; sLy = l0y/ll; sLz = l0z/ll;
        GLuint vs = compile(GL_VERTEX_SHADER, VS_PART);
        GLuint fs = compile(GL_FRAGMENT_SHADER, FS_PART);
        if (vs && fs) {
            sProg = glCreateProgram();
            glAttachShader(sProg, vs); glAttachShader(sProg, fs);
            glLinkProgram(sProg);
            glDeleteShader(vs); glDeleteShader(fs);
            GLint ok = 0; glGetProgramiv(sProg, GL_LINK_STATUS, &ok);
            if (!ok) { char log[512]; glGetProgramInfoLog(sProg, sizeof(log), nullptr, log);
                       ALOGE("ps3part: link failed: %s", log); glDeleteProgram(sProg); sProg = 0; }
        } else { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); }
        if (sProg) {
            sLocPos    = glGetAttribLocation(sProg, "aPos");
            sLocSize   = glGetAttribLocation(sProg, "aSize");
            sLocBright = glGetAttribLocation(sProg, "aBright");
            sLocColor  = glGetAttribLocation(sProg, "aColor");
            sLocSpark  = glGetAttribLocation(sProg, "aSpark");
            sLocScaleX = glGetUniformLocation(sProg, "uScaleX");
            sLocScaleY = glGetUniformLocation(sProg, "uScaleY");
            sLocYFlip  = glGetUniformLocation(sProg, "uYFlip");
            sLocFrameN = glGetUniformLocation(sProg, "uFrameN");
            sLocRot    = glGetUniformLocation(sProg, "uRotation");
            glGenBuffers(1, &sVBO);
            sParts.resize(kNumParticles);
            for (Particle& p : sParts) spawn(p);
            sBuf.resize((size_t)kNumParticles * 8);
            sReady = true;
            ALOGI("ps3part: ready (%d particles, %d-pt cloud)", kNumParticles, kPartCloudCount);
        }
    }
    return sReady;
}

void update(float dt) {
    if (!sReady) return;
    sAccumMs += (double)dt * 1000.0;
    const double PART_DT = 1000.0 / 60.0;
    int n = 0;
    while (sAccumMs >= PART_DT && n < 2) { sAccumMs -= PART_DT; step(); n++; }
    if (sAccumMs > PART_DT) sAccumMs = 0.0;
}

void render(float scaleX, float scaleY, float yFlip, float frameH,
            float nightBlend, float waveT,
            const float frameNdc[4], const float rotMat[4]) {
    (void)waveT;   // superseded by ps3bg::waveDisplacementAt (real silk coupling); kept for ABI stability
    if (!sReady && !init()) return;

    float nb = nightBlend; if (nb < 0.0f) nb = 0.0f; if (nb > 1.0f) nb = 1.0f;
    float bodyLevel = 0.05f + 0.11f * nb;
    float glintExpo = kExposure * (0.7f + 0.9f * nb);
    float Hs = frameH / 720.0f;
    const float FOCUS_Z = 5.8f;

    // Music "XMB Waves" doubled pool: lazily spawn the extra field (same size as the
    // menu field, kept warm) the first time the morph runs, and double sBuf so both
    // pools fit (index.html activeParticlePool).
    bool useExtra = (sMvBlend > 0.0001f);
    if (useExtra && sPartsExtra.empty()) {
        sPartsExtra.resize(kNumParticles);
        for (Particle& p : sPartsExtra) spawn(p);
        sBuf.resize((size_t)kNumParticles * 2 * 8);
    } else if (!useExtra && !sPartsExtra.empty()) {
        // The music "XMB Waves" morph has fully settled back to the wallpaper: drop the
        // doubled pool and its half of the upload buffer so nothing extra is held at idle
        // (the next morph re-spawns it lazily above, unseen while it fades in from 0).
        // The blend ramp is monotonic per transition, so this fires once on leave, not
        // in a spawn/free churn.
        std::vector<Particle>().swap(sPartsExtra);
        std::vector<float>((size_t)kNumParticles * 8).swap(sBuf);
    }

    // Re-project the particle cloud at ~30Hz (every other frame): the loop below
    // runs several transcendentals per particle plus a 45KB upload, and a 30Hz
    // glint refresh over the animating wave is imperceptible. update() still
    // advances the motion every frame; the off-frames redraw the cached VBO. Force
    // a rebuild when the extra pool toggles so the VBO matches the draw count.
    static unsigned sPartFrame = 0;
    static bool sLastUseExtra = false;
    static int sDrawCount = kNumParticles;
    bool rebuild = ((sPartFrame++ & 1u) == 0u) || (useExtra != sLastUseExtra);
    static const float kRotIdent[4] = {1.f, 0.f, 0.f, 1.f};
    const float* R = rotMat ? rotMat : kRotIdent;   // wave-coupling rotation (same as the shader's)
    // Project one particle into dst[0..7]. brightScale dims the doubled music pool so
    // it fades in/out with the wallpaper morph (index.html buildParticleData _mvExtra).
    auto project = [&](const Particle& p, float brightScale, float* dst) {
        float w = -p.ez + 2.0f;
        if (w <= 0.001f) { dst[3] = 0.0f; return; }
        float ndcX = kProjFx * p.ex / w;
        float ndcY = kProjFy * p.ey / w;
        // Wave-undulation coupling, orientation-correct on 0/90/180/270. The
        // (ndcX,ndcY) point is frame-remapped then rotated by rotMat in the
        // shader, so the wave's VISUAL vertical motion D must be injected through
        // that rotation, not added straight to ndcY. visualX is the x AFTER the
        // rotation (so D samples the wave at the right column); D is then mapped
        // back to a pre-rotation offset (m1*D, m3*D) - rotMat is orthonormal so
        // its inverse is its transpose. On a 180-degree panel this flips both the
        // sign and the x-phase, which is what stops the glitter mirroring the
        // wave; on 0 degrees it reduces to the old ndcY += D.
        float visualX = R[0] * ndcX + R[2] * ndcY;
        // Ride the REAL silk surface: sample the wave's per-column vertical displacement (from its
        // temporal mean) at this screen column, so the glitter follows the actual keyframe undulation
        // instead of a coarse sinusoid. Returned in the shared pre-transform NDC-Y space (the particle
        // shader then applies the same uYFlip*uScaleY as the wave), so they move in lockstep.
        float D = ps3bg::waveDisplacementAt(visualX) * kWaveCouple;
        ndcX += R[1] * D;
        ndcY += R[3] * D;
        if (fabsf(ndcX) > 1.15f || fabsf(ndcY) > 1.15f) { dst[3] = 0.0f; return; }
        float pl = sqrtf(p.ex*p.ex + p.ey*p.ey + p.ez*p.ez); if (pl < 1e-4f) pl = 1.0f;
        float vx = -p.ex/pl, vy = -p.ey/pl, vz = -p.ez/pl;
        float hxv = sLx + vx, hyv = sLy + vy, hzv = sLz + vz;
        float hl = sqrtf(hxv*hxv + hyv*hyv + hzv*hzv); if (hl < 1e-4f) hl = 1.0f;
        hxv /= hl; hyv /= hl; hzv /= hl;
        // Spinning normal: sin(sx0)*cos(sx1), sin(sx0)*sin(sx1), cos(sx0). Compute
        // each angle's sin+cos in one sincosf call instead of four separate sinf/
        // cosf - same values, half the trig calls (this projection runs per
        // particle every reproject, so the trig is a measured hot path).
        float s0, c0, s1, c1;
        sincosf(p.sx0, &s0, &c0);
        sincosf(p.sx1, &s1, &c1);
        float nx = s0 * c1, ny = s0 * s1, nz = c0;
        float ndh = nx*hxv + ny*hyv + nz*hzv; if (ndh < 0.0f) ndh = 0.0f;
        float ndv = nx*vx + ny*vy + nz*vz; if (ndv < 0.0f) ndv = 0.0f;
        float spec = powf(ndh, kSpecPower) * kSpecCoeff;
        float fres = powf(1.0f - ndv, kFresnel);
        float a = p.age;
        float ageF = (a < 0.12f) ? (a / 0.12f) : (a > 0.75f ? (1.0f - a) / 0.25f : 1.0f);
        float depthDist = -p.ez;
        float dd = depthDist - FOCUS_Z;
        float defocus = (dd >= 0.0f) ? dd / 1.2f : -dd / 1.6f;
        if (defocus > 1.0f) defocus = 1.0f;
        float dofDark = 1.0f / (1.0f + defocus * defocus * 2.4f);
        float glintGain = p.edge ? 2.4f : 1.0f;
        float bodyGain  = p.edge ? 2.2f : 1.0f;
        float bright = (bodyLevel * bodyGain + fres * 0.04f + spec * glintExpo * glintGain)
                       * ageF * dofDark;
        if (bright < 0.0f) bright = 0.0f;
        bright *= brightScale;   // doubled music pool fades with the morph blend
        float sizePx = (kSizeNear * 90.0f / w) * Hs * p.bigScale;
        sizePx *= (1.0f + defocus * 1.5f);
        if (sizePx < 1.0f) sizePx = 1.0f;
        float gl = (bright < 2.0f ? bright : 2.0f) * (kGlare * 2.0f);
        sizePx += gl;
        float cap = p.edge ? 22.0f : 12.0f;
        if (sizePx > cap) sizePx = cap;
        float spark = (sizePx - 3.0f) / 5.0f; if (spark > 1.0f) spark = 1.0f; if (spark < 0.0f) spark = 0.0f;
        float b2 = bright * 0.5f; if (b2 > 1.0f) b2 = 1.0f;
        spark = spark * 0.6f + b2 * 0.7f;
        if (p.edge) spark += 0.4f;
        if (spark < 0.0f) spark = 0.0f; else if (spark > 1.4f) spark = 1.4f;
        float r, g, b;
        if (p.edge) {
            float reflv = 0.5f + (spec * glintExpo * 0.6f < 1.0f ? spec * glintExpo * 0.6f : 1.0f);
            r = p.tintR * reflv; g = p.tintG * reflv; b = p.tintB * reflv;
        } else {
            // Iridescent rainbow: cos(t), cos(t+120deg), cos(t+240deg). The two phase-
            // shifted cosines are derived from a single sincosf via the angle-addition
            // identity (cos(t+p)=cos t cos p - sin t sin p) - exact same values, one
            // transcendental instead of three (this runs per non-edge particle, a
            // measured hot path during the doubled-pool morph).
            float t = ndh * 6.2832f * kIridescentExp;
            float st, ct; sincosf(t, &st, &ct);
            float cosA = ct;                                  // cos(t)
            float cosB = -0.5f * ct - 0.8660254f * st;       // cos(t + 2.0944)
            float cosC = -0.5f * ct + 0.8660254f * st;       // cos(t + 4.1888)
            r = 0.86f + 0.14f * (0.5f + 0.5f * cosA);
            g = 0.88f + 0.12f * (0.5f + 0.5f * cosB);
            b = 0.92f + 0.08f * (0.5f + 0.5f * cosC);
        }
        dst[0] = ndcX; dst[1] = ndcY; dst[2] = sizePx; dst[3] = bright;
        dst[4] = r; dst[5] = g; dst[6] = b; dst[7] = spark;
    };
    if (rebuild) {
        float* d = sBuf.data();
        int o = 0;
        for (const Particle& p : sParts) { project(p, 1.0f, d + o); o += 8; }
        // Doubled music pool: project a COUNT proportional to the morph blend, not the
        // full pool. The extra particles fade with brightScale=sMvBlend anyway, so
        // dropping their count as the blend ramps (most visibly during the leave, where
        // it plays out over the full XMB menu) cuts the trig-heavy projection cost in
        // step with the fade - the look is the same fade, just cheaper.
        if (useExtra) {
            int nExtra = (int)((float)kNumParticles * sMvBlend + 0.5f);
            if (nExtra > kNumParticles) nExtra = kNumParticles;
            for (int k = 0; k < nExtra; k++) { project(sPartsExtra[(size_t)k], sMvBlend, d + o); o += 8; }
        }
        sDrawCount = o / 8;
        sLastUseExtra = useExtra;
    }

    glUseProgram(sProg);
    glUniform1f(sLocScaleX, scaleX);
    glUniform1f(sLocScaleY, scaleY);
    glUniform1f(sLocYFlip, yFlip);
    glUniform4f(sLocFrameN, frameNdc[0], frameNdc[1], frameNdc[2], frameNdc[3]);
    static const float kIdentity[4] = {1.f, 0.f, 0.f, 1.f};
    glUniformMatrix2fv(sLocRot, 1, GL_FALSE, rotMat ? rotMat : kIdentity);
    glBindBuffer(GL_ARRAY_BUFFER, sVBO);
    if (rebuild)
        glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)((size_t)sDrawCount * 8 * sizeof(float)), sBuf.data(), GL_DYNAMIC_DRAW);
    const GLsizei ST = 8 * sizeof(float);
    glEnableVertexAttribArray(sLocPos);    glVertexAttribPointer(sLocPos,    2, GL_FLOAT, GL_FALSE, ST, (const void*)0);
    glEnableVertexAttribArray(sLocSize);   glVertexAttribPointer(sLocSize,   1, GL_FLOAT, GL_FALSE, ST, (const void*)(2*sizeof(float)));
    glEnableVertexAttribArray(sLocBright); glVertexAttribPointer(sLocBright, 1, GL_FLOAT, GL_FALSE, ST, (const void*)(3*sizeof(float)));
    glEnableVertexAttribArray(sLocColor);  glVertexAttribPointer(sLocColor,  3, GL_FLOAT, GL_FALSE, ST, (const void*)(4*sizeof(float)));
    if (sLocSpark >= 0) { glEnableVertexAttribArray(sLocSpark); glVertexAttribPointer(sLocSpark, 1, GL_FLOAT, GL_FALSE, ST, (const void*)(7*sizeof(float))); }

    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE);             // premultiplied additive HDR glints
    glDisable(GL_DEPTH_TEST);
    glDrawArrays(GL_POINTS, 0, sDrawCount);   // kNumParticles, or 2x while the music pool fades

    glDisableVertexAttribArray(sLocPos);
    glDisableVertexAttribArray(sLocSize);
    glDisableVertexAttribArray(sLocBright);
    glDisableVertexAttribArray(sLocColor);
    if (sLocSpark >= 0) glDisableVertexAttribArray(sLocSpark);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);   // restore standard blend
}

void setMusicVisBlend(float blend) {
    sMvBlend = (blend < 0.0f) ? 0.0f : (blend > 1.0f ? 1.0f : blend);
}

void shutdown() {
    if (sProg) glDeleteProgram(sProg);
    if (sVBO) glDeleteBuffers(1, &sVBO);
    sProg = 0; sVBO = 0; sReady = false; sTried = false;
    sParts.clear(); sPartsExtra.clear(); sBuf.clear(); sAccumMs = 0.0; sMvBlend = 0.0f;
}

} // namespace ps3part
} // namespace android
