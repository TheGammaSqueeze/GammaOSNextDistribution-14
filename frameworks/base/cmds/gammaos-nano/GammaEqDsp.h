// GammaEQ processing for the drastic-nano AAudio sink.
//
// The chain below is a verbatim copy of the GammaEQ modules in
// frameworks/av/services/audioflinger/Threads.cpp (speaker PEQ, low band protector, mid
// protector, CrystalizerLite, StereoWidenerHB, their property reloads and the global gains).
// AudioFlinger applies them on its normal mixer write; an AAudio MMAP stream never passes
// through that mixer, so the sink applies the same chain itself, in the same order and at
// the same 48 kHz, driven by the same persist.sys.spk.* / persist.sys.gammaeq.* properties.
// Keep the two copies identical: a change to the AudioFlinger chain belongs here too.
#pragma once
#include <cutils/properties.h>
#include <utils/Timers.h>
#include <log/log.h>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace gammaeq {
using std::min; using std::max;
/* GammaEQ speaker-only gating (fast-path safe) */
static inline bool gammaeqSpeakerOnlyEnabled() {
    return property_get_bool("persist.sys.gammaeq.spk_only", true);
}

static inline bool gammaeqForceAllOutputs() {
    return property_get_bool("persist.sys.gammaeq.force", false);
}

// Master enable for the whole GammaEQ chain (OFF by default).
// Nothing will run unless you explicitly set persist.sys.gammaeq.enable=1.
static inline bool gammaeqMasterEnabled() {
    return property_get_bool("persist.sys.gammaeq.enable", false);
}

static inline bool isSpeakerRoutedNow() {
    // Property maintained by Threads.cpp when SPEAKER route is active.
    return property_get_bool("sys.gammaeq.route.spk", true);
}

// ---- Simple 2-stage stereo PEQ with soft limiter (properties-driven) --------
struct SpeakerPEQ {
    struct BQ {
        float b0{1.f}, b1{0.f}, b2{0.f}, a1{0.f}, a2{0.f};
        float z1L{0.f}, z2L{0.f}, z1R{0.f}, z2R{0.f};
        inline void reset() { z1L = z2L = z1R = z2R = 0.f; }
        inline void process(float* x, size_t frames) {
            for (size_t i = 0; i < frames; ++i) {
                const float xl = x[2 * i + 0], xr = x[2 * i + 1];
                const float yl = b0 * xl + z1L;
                const float yr = b0 * xr + z1R;
                z1L = b1 * xl - a1 * yl + z2L; z1R = b1 * xr - a1 * yr + z2R;
                z2L = b2 * xl - a2 * yl;       z2R = b2 * xr - a2 * yr;
                x[2 * i + 0] = yl; x[2 * i + 1] = yr;
            }
        }
    };
    bool enabled{false};
    bool s2enabled{false};
    float pregain{1.0f};
    bool  keepHeadroom{true};
    float limiter{0.0f}; // 0 = off
    int   seq{0};
    int64_t lastCheckNs{0};
    BQ s1, s2;

    inline void softLimit(float* x, size_t n) const {
        if (limiter <= 0.f) return;
        const float t = limiter;
        for (size_t i = 0; i < n; ++i) {
            float v = x[i];
            if (v > t)  v = t + (v - t) * 0.25f;
            if (v < -t) v = -t + (v + t) * 0.25f;
            x[i] = v;
        }
    }

    inline void process(float* interleaved, size_t frames, int channels) {
        if (!enabled || channels < 2) return;
        const size_t n = frames * (size_t)channels;
        if (pregain != 1.0f) {
            for (size_t i = 0; i < n; ++i) interleaved[i] *= pregain;
        }
        s1.process(interleaved, frames);
        if (s2enabled) {
            s2.process(interleaved, frames);
        }
        if (!keepHeadroom) softLimit(interleaved, n);
    }
};

// --- GammaEQ property helpers ------------------------------------------------
static inline float propFloat(const char* k, float d) {
    char v[PROPERTY_VALUE_MAX] = {};
    return property_get(k, v, nullptr) > 0 ? (float)atof(v) : d;
}

static inline int propInt(const char* k, int d) {
    char v[PROPERTY_VALUE_MAX] = {};
    return property_get(k, v, nullptr) > 0 ? atoi(v) : d;
}

// dB to linear helper (CrystalizerLite)
static inline float db2lin(float db) {
    return powf(10.f, db * (1.f / 20.f));
}

// Fills coefficients and switches from the properties. The biquad state is
// deliberately left alone: this runs once a second from the mixer thread, and
// zeroing the delay lines each time put a discontinuity (an audible tick,
// measured with a microphone on the RG DS Plus speaker) into the output every
// second. A coefficient change with the state kept is a smooth transition; the
// state is reset only when the stage is switched on from off.
static inline void loadSpeakerPEQFromProps(SpeakerPEQ& s) {
    const bool wasEnabled = s.enabled;
    s.enabled   = property_get_bool("persist.sys.spk.peq", false);
    s.s2enabled = property_get_bool("persist.sys.spk.peq2", false);
    s.pregain   = propFloat("persist.sys.spk.peq.pregain", 1.f);
    s.keepHeadroom = property_get_bool("persist.sys.spk.peq.keepheadroom", true);
    s.limiter   = propFloat("persist.sys.spk.peq.limit",   0.f);
    s.seq       = propInt  ("persist.sys.spk.peq.seq",     0);

    s.s1.b0 = propFloat("persist.sys.spk.peq.b0", 1.f);
    s.s1.b1 = propFloat("persist.sys.spk.peq.b1", 0.f);
    s.s1.b2 = propFloat("persist.sys.spk.peq.b2", 0.f);
    s.s1.a1 = propFloat("persist.sys.spk.peq.a1", 0.f);
    s.s1.a2 = propFloat("persist.sys.spk.peq.a2", 0.f);

    s.s2.b0 = propFloat("persist.sys.spk.peq2.b0", 1.f);
    s.s2.b1 = propFloat("persist.sys.spk.peq2.b1", 0.f);
    s.s2.b2 = propFloat("persist.sys.spk.peq2.b2", 0.f);
    s.s2.a1 = propFloat("persist.sys.spk.peq2.a1", 0.f);
    s.s2.a2 = propFloat("persist.sys.spk.peq2.a2", 0.f);
    if (s.enabled && !wasEnabled) { s.s1.reset(); s.s2.reset(); }
}

static inline void maybeReloadPEQ(SpeakerPEQ& s) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    const bool due = (now - s.lastCheckNs) > seconds(1);
    // Consider bumps on BOTH seq props (A13 parity for hot-reload).
    int cur = s.seq;
    if (due) {
        const int seq1 = propInt("persist.sys.spk.peq.seq",  s.seq);
        const int seq2 = propInt("persist.sys.spk.peq2.seq", s.seq);
        cur = max(seq1, seq2);
    }
    if (!due && cur == s.seq) return;
    loadSpeakerPEQFromProps(s);
    s.lastCheckNs = now;
}

// ---- Low-Band Protector (LBP): tame bass peaks without dulling mids/highs ----
struct LowBandProtector {
    std::atomic<bool>  enabled{false};
    std::atomic<float> fc{120.f};
    std::atomic<float> thr{0.85f};
    std::atomic<float> atk_ms{4.f}, rel_ms{60.f};
    int seq{0};
    int64_t lastCheckNs{0};
    float zL{0}, zR{0};
    float envL{0}, envR{0};
    float aAtk{0}, aRel{0};

    void updateCoef(uint32_t sampleRate) {
        const float sr = (float)((sampleRate < 8000u) ? 8000u : sampleRate);
        const float f  = min(max(fc.load(), 20.f), 400.f);
        const float x  = expf(-2.f * (float)M_PI * f / sr);
        const float b  = 1.f - x;
        // Simple one-pole HP: y[n] = x[n] - lp(x[n])
        // We'll use zL/zR as lp(x) state with coef b/x.
        // Attack/release envelope coefficients.
        const float atk  = max(atk_ms.load(),  1.f);
        const float rel  = max(rel_ms.load(), 10.f);
        aAtk = expf(-1.f / (atk * 0.001f * sr));
        aRel = expf(-1.f / (rel * 0.001f * sr));
        // reuse zL/zR for HPF state, envL/envR for envelope.
        (void)b; // b used implicitly in process.
    }

    inline void process(float* x, size_t frames, int ch) {
        if (!enabled || ch < 2) return;
        const float t = thr.load();
        const float atk = aAtk;
        const float rel = aRel;
        float lpL = zL, lpR = zR;
        float eL = envL, eR = envR;
        for (size_t i = 0; i < frames; ++i) {
            float L = x[2 * i + 0];
            float R = x[2 * i + 1];
            // crude HP on L/R via subtracting LP
            lpL += (L - lpL) * (1.f - atk); // reuse atk as LP coef
            lpR += (R - lpR) * (1.f - atk);
            const float hL = L - lpL;
            const float hR = R - lpR;
            const float aL = fabsf(hL);
            const float aR = fabsf(hR);
            eL = (aL > eL) ? (atk * eL + (1.f - atk) * aL)
                           : (rel * eL + (1.f - rel) * aL);
            eR = (aR > eR) ? (atk * eR + (1.f - atk) * aR)
                           : (rel * eR + (1.f - rel) * aR);
            const float gL = eL > t ? t / (eL + 1e-6f) : 1.f;
            const float gR = eR > t ? t / (eR + 1e-6f) : 1.f;
            x[2 * i + 0] = hL * gL + lpL;
            x[2 * i + 1] = hR * gR + lpR;
        }
        zL = lpL; zR = lpR;
        envL = eL; envR = eR;
    }
};

static inline void lbpLoad(LowBandProtector& c) {
    c.enabled.store(property_get_bool("persist.sys.spk.lbp", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf = [&](const char* k, float d)->float {
        return property_get(k, v, nullptr) > 0 ? (float)atof(v) : d;
    };
    c.fc.store     (rf("persist.sys.spk.lbp.fc",   120.f));
    c.thr.store    (rf("persist.sys.spk.lbp.thr",  0.85f));
    c.atk_ms.store (rf("persist.sys.spk.lbp.atk",  4.f));
    c.rel_ms.store (rf("persist.sys.spk.lbp.rel", 60.f));
    c.seq = property_get_int32("persist.sys.spk.lbp.seq", 0);
}

static inline void lbpMaybeReload(LowBandProtector& c) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - c.lastCheckNs > seconds(1) ||
            property_get_int32("persist.sys.spk.lbp.seq", c.seq) != c.seq) {
        lbpLoad(c);
        c.lastCheckNs = now;
    }
}

// ---- Mid Protector (MP): limit *center* (Mid L+R) but keep side spacious ----
struct MidProtector {
    std::atomic<bool>  enabled{false};
    std::atomic<float> hpf{120.f}, lpf{6000.f};
    std::atomic<float> thr{0.90f};
    std::atomic<float> atk_ms{3.f}, rel_ms{80.f};
    int seq{0};
    int64_t lastCheckNs{0};
    float zLpL{0}, zLpR{0};
    float env{0};
    float aAtk{0}, aRel{0};

    void updateCoef(uint32_t sampleRate) {
        const float sr = (float)((sampleRate < 8000u) ? 8000u : sampleRate);
        // LPF coef
        const float fL  = min(max(lpf.load(), 300.f), 10000.f);
        const float xL  = expf(-2.f * (float)M_PI * fL / sr);
        const float bL  = 1.f - xL;
        (void)bL; // used implicitly in process via (1 - xL)
        // HPF coef (for mid)
        const float fH  = min(max(hpf.load(), 20.f), 2000.f);
        const float xH  = expf(-2.f * (float)M_PI * fH / sr);
        (void)xH;

        const float atk  = max(atk_ms.load(),  1.f);
        const float rel  = max(rel_ms.load(), 10.f);
        aAtk = expf(-1.f / (atk * 0.001f * sr));
        aRel = expf(-1.f / (rel * 0.001f * sr));
    }

    inline void process(float* x, size_t frames, int ch) {
        if (!enabled || ch < 2) return;
        const float t   = thr.load();
        const float atk = aAtk;
        const float rel = aRel;
        float lpL = zLpL, lpR = zLpR;
        float e = env;
        for (size_t i = 0; i < frames; ++i) {
            float L = x[2 * i + 0];
            float R = x[2 * i + 1];
            // mid/side
            float M = 0.5f * (L + R);
            float S = 0.5f * (L - R);
            // bandpass mid via HPF/LPF style; here simplified.
            lpL += (M - lpL) * (1.f - atk);
            const float bandM = M - lpL;
            const float a = fabsf(bandM);
            e = (a > e) ? (atk * e + (1.f - atk) * a)
                        : (rel * e + (1.f - rel) * a);
            const float g = e > t ? t / (e + 1e-6f) : 1.f;
            M = bandM * g + lpL;
            // back to L/R
            L = M + S;
            R = M - S;
            x[2 * i + 0] = L;
            x[2 * i + 1] = R;
        }
        zLpL = lpL; zLpR = lpR;
        env = e;
    }
};

static inline void mpLoad(MidProtector& c) {
    c.enabled.store(property_get_bool("persist.sys.spk.mp", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf = [&](const char* k, float d)->float {
        return property_get(k, v, nullptr) > 0 ? (float)atof(v) : d;
    };
    c.hpf.store (rf("persist.sys.spk.mp.hpf",  200.f));
    c.lpf.store (rf("persist.sys.spk.mp.lpf", 6000.f));
    c.thr.store (rf("persist.sys.spk.mp.thr",  0.90f));
    c.atk_ms.store(rf("persist.sys.spk.mp.atk",  3.f));
    c.rel_ms.store(rf("persist.sys.spk.mp.rel", 80.f));
    c.seq = property_get_int32("persist.sys.spk.mp.seq", 0);
}

static inline void mpMaybeReload(MidProtector& c) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - c.lastCheckNs > seconds(1) ||
            property_get_int32("persist.sys.spk.mp.seq", c.seq) != c.seq) {
        mpLoad(c);
        c.lastCheckNs = now;
    }
}

// ---- CrystalizerLite (A13 parity): high-band enhancer with pre/post gain & HPF corner ----
struct CrystalizerLite {
    std::atomic<bool>  enabled{false};
    std::atomic<float> amount{3.f}, mix{0.9f};
    std::atomic<float> pregain_db{-6.f}, postgain_db{0.f};
    std::atomic<float> cornerHz{9000.f};
    int seq{0};
    int64_t lastCheckNs{0};
    float zL{0}, zR{0};
    float a{0}, b{0};

    void updateCoef(uint32_t sr) {
        const float srf = (float)((sr < 8000u) ? 8000u : sr);
        const float f   = min(max(cornerHz.load(), 2000.0f), srf * 0.45f);
        const float x   = expf(-2.f * (float)M_PI * f / srf);
        b = 1.f - x;
        a = x;
    }

    inline void process(float* interleaved, size_t frames, int ch) {
        if (!enabled || ch < 2) return;
        const float amt = amount.load();
        const float mx  = mix.load();
        const float pre = db2lin(pregain_db.load());
        const float post = db2lin(postgain_db.load());
        float hpL = zL, hpR = zR;
        for (size_t i = 0; i < frames; ++i) {
            float L = interleaved[2 * i + 0] * pre;
            float R = interleaved[2 * i + 1] * pre;
            // high-pass via y = x - lp(x)
            hpL = b * L + a * hpL;
            hpR = b * R + a * hpR;
            const float hL = L - hpL;
            const float hR = R - hpR;
            // soft nonlinearity
            const float eL = hL * amt;
            const float eR = hR * amt;
            const float nL = eL / (1.f + fabsf(eL));
            const float nR = eR / (1.f + fabsf(eR));
            // wet/dry mix
            const float outL = (1.f - mx) * L + mx * (L + nL);
            const float outR = (1.f - mx) * R + mx * (R + nR);
            interleaved[2 * i + 0] = outL * post;
            interleaved[2 * i + 1] = outR * post;
        }
        zL = hpL; zR = hpR;
    }
};

static inline void crystLoad(CrystalizerLite& c) {
    c.enabled.store(property_get_bool("persist.sys.spk.cryst", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf = [&](const char* k, float d)->float {
        return property_get(k, v, nullptr) > 0 ? (float)atof(v) : d;
    };
    c.amount.store   (rf("persist.sys.spk.cryst.amount", 4.0f));
    c.mix.store      (rf("persist.sys.spk.cryst.mix",    0.95f));
    c.pregain_db.store(rf("persist.sys.spk.cryst.pregain_db", -8.f));
    c.postgain_db.store(rf("persist.sys.spk.cryst.postgain_db",  0.f));
    c.cornerHz.store (rf("persist.sys.spk.cryst.hz",     9000.f));
    c.seq = property_get_int32("persist.sys.spk.cryst.seq", 0);
}

static inline void crystMaybeReload(CrystalizerLite& c) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - c.lastCheckNs > seconds(1) ||
            property_get_int32("persist.sys.spk.cryst.seq", c.seq) != c.seq) {
        crystLoad(c);
        c.lastCheckNs = now;
    }
}

// ---- Stereo widener: M/S with side HPF, wet/dry mix, no pregain/limiter ----
struct StereoWidenerHB {
    std::atomic<bool>  enabled{false};
    std::atomic<float> amount{1.0f}, mix{0.35f}, pregain{1.0f}, limit{1.0f}, fc{2500.0f};
    int seq{0}; int64_t lastCheckNs{0};
    float zL{0}, zR{0}, a{0}, b{0};
    float sLP{0};
    void updateCoef(uint32_t sr) {
        const float srf = (float)((sr < 8000u) ? 8000u : sr);
        const float f   = min(max(fc.load(), 20.0f), srf * 0.45f);
        const float x   = expf(-2.f * (float)M_PI * f / srf);
        b = 1.f - x;
        a = x;
    }

    inline void process(float* interleaved, size_t frames, int ch) {
        if (!enabled || ch < 2) return;
        const float wet   = mix.load();
        const float dry   = 1.f - wet;
        const float pre   = pregain.load();
        const float limitVal = limit.load();
        float lpS  = sLP;
        for (size_t i = 0; i < frames; ++i) {
            float L = interleaved[2 * i + 0] * pre;
            float R = interleaved[2 * i + 1] * pre;
            float M = 0.5f * (L + R);
            float S = 0.5f * (L - R);
            // HPF-ish on S: remove some low-mid.
            lpS += (S - lpS) * b;
            const float Sh = S - lpS;
            // widen by boosting side with soft limit
            float Sw = Sh * amount.load();
            if (limitVal < 1.f) {
                const float aL = fabsf(Sw);
                if (aL > limitVal && aL > 1e-6f) {
                    Sw = Sw * (limitVal / aL);
                }
            }
            const float Lw = M + Sw;
            const float Rw = M - Sw;
            interleaved[2 * i + 0] = dry * L + wet * Lw;
            interleaved[2 * i + 1] = dry * R + wet * Rw;
        }
        sLP = lpS;
    }
};

static inline void wideLoad(StereoWidenerHB& w) {
    w.enabled.store(property_get_bool("persist.sys.spk.wide", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf = [&](const char* k, float d)->float {
        return property_get(k, v, nullptr) > 0 ? (float)atof(v) : d;
    };
    w.amount.store(rf("persist.sys.spk.wide.amount", 1.0f));  // not used by A13 algo
    w.mix.store   (rf("persist.sys.spk.wide.mix",    0.35f));
    w.pregain.store(rf("persist.sys.spk.wide.pre",   1.00f));
    w.limit.store (rf("persist.sys.spk.wide.limit",  1.00f));
    w.fc.store    (rf("persist.sys.spk.wide.hpf",  2500.f));
    w.seq = property_get_int32("persist.sys.spk.wide.seq", 0);
}

static inline void wideMaybeReload(StereoWidenerHB& w) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - w.lastCheckNs > seconds(1) ||
            property_get_int32("persist.sys.spk.wide.seq", w.seq) != w.seq) {
        wideLoad(w);
        w.lastCheckNs = now;
    }
}

// Optional global post-gain (after chain) to restore loudness safely.
static inline float getGlobalPostampLin() {
    static float sLin = 1.0f;
    static int64_t sLast = 0;
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - sLast > seconds(1)) {
        char v[PROPERTY_VALUE_MAX] = {};
        const float db = (property_get("persist.sys.gammaeq.postgain_db", v, nullptr) > 0)
                         ? (float)atof(v) : 0.0f;
        sLin = db2lin(db);
        sLast = now;
    }
    return sLin;
}

// Optional global pre-attenuator (dB) to avoid tripping HAL speaker protection
static inline float getGlobalPreampLin() {
    static float sLin = 1.0f;
    static int64_t sLast = 0;
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (now - sLast > seconds(1)) {
        char v[PROPERTY_VALUE_MAX] = {};
        const float db = (property_get("persist.sys.gammaeq.preamp_db", v, nullptr) > 0)
                         ? (float)atof(v) : 0.0f;
        sLin = db2lin(db);
        sLast = now;
    }
    return sLin;
}
}  // namespace gammaeq
