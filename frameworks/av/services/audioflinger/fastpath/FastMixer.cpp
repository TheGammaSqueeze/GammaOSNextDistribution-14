/*
 * Copyright (C) 2012 The Android Open Source Project
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

// <IMPORTANT_WARNING>
// Design rules for threadLoop() are given in the comments at section "Fast mixer thread" of
// StateQueue.h.  In particular, avoid library and system calls except at well-known points.
// The design rules are only for threadLoop(), and don't apply to FastMixerDumpState methods.
// </IMPORTANT_WARNING>

#define LOG_TAG "FastMixer"
//#define LOG_NDEBUG 0

#define ATRACE_TAG ATRACE_TAG_AUDIO

#include "Configuration.h"
#include <time.h>
#include <utils/Log.h>
#include <utils/Trace.h>
#include <system/audio.h>
#ifdef FAST_THREAD_STATISTICS
#include <audio_utils/Statistics.h>
#ifdef CPU_FREQUENCY_STATISTICS
#include <cpustats/ThreadCpuUsage.h>
#endif
#endif
#include <audio_utils/channels.h>
#include <audio_utils/format.h>
#include <audio_utils/mono_blend.h>
#include <cutils/bitops.h>
#include <media/AudioMixer.h>
#include "FastMixer.h"
#include <afutils/TypedLogger.h>
// --- GammaEQ: fast-path helpers
#include <utils/Timers.h>
#include <cutils/properties.h>
#include <audio_utils/primitives.h>
#include <vector>
#include <atomic>
#include <math.h>
#include <algorithm>

// -----------------------------------------------------------------------------
/* GammaEQ speaker-only gating (fast-path safe) */
static inline bool gammaeqSpeakerOnlyEnabled() {
    return property_get_bool("persist.sys.gammaeq.spk_only", true);
}

static inline bool gammaeqForceAllOutputs() {
    return property_get_bool("persist.sys.gammaeq.force", false);
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
        inline void reset() { z1L=z2L=z1R=z2R=0.f; }
        inline void process(float* x, size_t frames) {
            for (size_t i=0;i<frames;++i) {
                const float xl = x[2*i+0], xr = x[2*i+1];
                const float yl = b0*xl + z1L;
                const float yr = b0*xr + z1R;
                z1L = b1*xl - a1*yl + z2L; z1R = b1*xr - a1*yr + z2R;
                z2L = b2*xl - a2*yl;       z2R = b2*xr - a2*yr;
                x[2*i+0] = yl; x[2*i+1] = yr;
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
        for (size_t i=0;i<n;++i) {
            float v = x[i];
            if (v >  t) v = t + (v - t) * 0.25f;
            if (v < -t) v = -t + (v + t) * 0.25f;
            x[i] = v;
        }
    }

    inline void process(float* interleaved, size_t frames, int ch) {
        if (!enabled || ch < 2) return;
        const float pre = pregain;
        const float inv = pre > 0.f ? 1.f / pre : 1.f;
        if (pre != 1.0f) for (size_t i=0;i<frames*ch;++i) interleaved[i] *= pre;
        s1.process(interleaved, frames);
        if (s2enabled) s2.process(interleaved, frames);
        softLimit(interleaved, frames*ch);
        if (!keepHeadroom && pre != 1.0f) for (size_t i=0;i<frames*ch;++i) interleaved[i] *= inv;
    }
};

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
    return powf(10.f, db * (1.f/20.f));
}

static inline void loadSpeakerPEQFromProps(SpeakerPEQ& s) {
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
    s.s1.reset();

    s.s2.b0 = propFloat("persist.sys.spk.peq2.b0", 1.f);
    s.s2.b1 = propFloat("persist.sys.spk.peq2.b1", 0.f);
    s.s2.b2 = propFloat("persist.sys.spk.peq2.b2", 0.f);
    s.s2.a1 = propFloat("persist.sys.spk.peq2.a1", 0.f);
    s.s2.a2 = propFloat("persist.sys.spk.peq2.a2", 0.f);
    s.s2.reset();
}

static inline void maybeReloadPEQ(SpeakerPEQ& s) {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    const bool due = (now - s.lastCheckNs) > seconds(1);
    // Consider bumps on BOTH seq props (A13 parity for hot-reload).
    int cur = s.seq;
    if (due) {
        const int seq1 = propInt("persist.sys.spk.peq.seq",  s.seq);
        const int seq2 = propInt("persist.sys.spk.peq2.seq", seq1);
        cur = std::max(seq1, seq2);
    }
    if (due || cur != s.seq) {
        loadSpeakerPEQFromProps(s);
        // Track combined sequence so either bump retriggers immediately next time.
        const int seq1 = propInt("persist.sys.spk.peq.seq",  s.seq);
        const int seq2 = propInt("persist.sys.spk.peq2.seq", seq1);
        s.seq = std::max(seq1, seq2);
        s.lastCheckNs = now;
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
        const float f   = std::min(std::max(fc.load(), 20.0f), srf * 0.45f);
        const float x   = expf(-2.f * (float)M_PI * f / srf);
        a = x; b = 1.f - x;
    }

    inline void process(float* interleaved, size_t frames, int ch) {
        if (!enabled.load() || ch < 2) return;
        const float m  = mix.load();       // wet/dry
        const float amt = amount.load();   // side gain scaler (new; default 1.0 keeps parity)
        const float aa = a, bb = b;        // one-pole LP for side; HP = S - LP(S)
        for (size_t i = 0; i < frames; ++i) {
            const float L = interleaved[2*i + 0];
            const float R = interleaved[2*i + 1];
            // Mid/Side
            const float M = 0.5f * (L + R);
            const float S = 0.5f * (L - R);
            // Side HPF
            sLP = aa * sLP + bb * S;
            const float Sh = (S - sLP) * amt;
            // Widened L/R from M and high-passed side
            const float Lw = M + Sh;
            const float Rw = M - Sh;
            const float dry = 1.f - m, wet = m;
            interleaved[2*i + 0] = dry * L + wet * Lw;
            interleaved[2*i + 1] = dry * R + wet * Rw;
        }
    }
};

static inline void wideLoad(StereoWidenerHB& w) {
    w.enabled.store(property_get_bool("persist.sys.spk.wide", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf=[&](const char* k, float d)->float{
        return property_get(k,v,nullptr)>0 ? (float)atof(v) : d;
    };
    w.amount.store(rf("persist.sys.spk.wide.amount", 1.0f));  // not used by A13 algo
    w.mix.store   (rf("persist.sys.spk.wide.mix",    0.35f));
    w.pregain.store(rf("persist.sys.spk.wide.pre",   1.00f));
    w.limit.store (rf("persist.sys.spk.wide.limit",  1.00f));
    // Support both A14 fc and legacy A13 hpf properties; prefer fc if set.
    w.fc.store    (rf("persist.sys.spk.wide.fc",
                      rf("persist.sys.spk.wide.hpf", 2500.f)));
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

// ---- CrystalizerLite (A13 parity): high-band enhancer with pre/post gain & HPF corner ----
struct CrystalizerLite {
    std::atomic<bool> enabled{false};
    std::atomic<float> amount{0.5f};          // how much high band to add
    std::atomic<float> mix{1.0f};             // wet/dry
    std::atomic<float> pregain_db{-6.0f};     // headroom before enhancement
    std::atomic<float> postgain_db{-6.0f};    // bring back down after
    std::atomic<float> fc{3500.0f};           // HPF corner for high-band extraction
    std::atomic<float> limit{1.0f};           // soft limit for added high-band (1.0 = off)
    int seq{0};
    int64_t lastCheckNs{0};
    float a{0}, b{0}, lpL{0}, lpR{0};
    void updateCoef(uint32_t sr) {
        const float srf = (float)((sr < 8000u) ? 8000u : sr);
        const float f   = std::min(std::max(fc.load(), 20.0f), srf * 0.45f);
        const float x   = expf(-2.f * (float)M_PI * f / srf);
        a = x; b = 1.f - x;
    }

    inline void process(float* interleaved, size_t frames, int ch) {
        if (!enabled.load() || ch < 2) return;
        const float m  = mix.load();
        const float dry = 1.f - m, wet = m;
        const float pre  = db2lin(pregain_db.load());
        const float post = db2lin(postgain_db.load());
        const float lim  = limit.load();   // linear threshold for added HB; 1.0 means disabled
        const float aa = a, bb = b;
        for (size_t i=0;i<frames;++i) {
            float L = interleaved[2*i+0] * pre;
            float R = interleaved[2*i+1] * pre;
            // high-band = x - lowpass(x)
            lpL = aa*lpL + bb*L; const float hL = L - lpL;
            lpR = aa*lpR + bb*R; const float hR = R - lpR;
            // add high band with an optional soft ceiling on the *added* component
            float addL = amount.load() * hL;
            float addR = amount.load() * hR;
            if (lim < 1.0f) {
                addL = lim * tanhf(addL / lim);
                addR = lim * tanhf(addR / lim);
            }
            const float Lc = L + addL;
            const float Rc = R + addR;
            const float Lo = dry * (L) + wet * (Lc);
            const float Ro = dry * (R) + wet * (Rc);
            interleaved[2*i+0] = Lo * post;
            interleaved[2*i+1] = Ro * post;
        }
    }
};

static inline void crystLoad(CrystalizerLite& c) {
    c.enabled.store(property_get_bool("persist.sys.spk.cryst", false));
    char v[PROPERTY_VALUE_MAX] = {};
    auto rf=[&](const char* k, float d)->float{
        return property_get(k,v,nullptr)>0 ? (float)atof(v) : d;
    };
    c.amount.store     (rf("persist.sys.spk.cryst.amount",      0.5f));
    c.mix.store        (rf("persist.sys.spk.cryst.mix",         1.0f));
    c.pregain_db.store (rf("persist.sys.spk.cryst.pregain_db", -6.0f));
    c.postgain_db.store(rf("persist.sys.spk.cryst.postgain_db",-6.0f));
    c.fc.store         (rf("persist.sys.spk.cryst.hz",         3500.f));
    c.limit.store      (rf("persist.sys.spk.cryst.limit",       1.0f));
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

// ---- Optional global pre-attenuator (dB) to avoid tripping HAL speaker protection ----
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

namespace android {

/*static*/ const FastMixerState FastMixer::sInitial;

static audio_channel_mask_t getChannelMaskFromCount(size_t count) {
    const audio_channel_mask_t mask = audio_channel_out_mask_from_count(count);
    if (mask == AUDIO_CHANNEL_INVALID) {
        // some counts have no positional masks. TODO: Update this to return index count?
        return audio_channel_mask_for_index_assignment_from_count(count);
    }
    return mask;
}

FastMixer::FastMixer(audio_io_handle_t parentIoHandle)
    : FastThread("cycle_ms", "load_us"),
    // mFastTrackNames
    // mGenerations
    // timestamp
    mThreadIoHandle(parentIoHandle)
{
    // FIXME pass sInitial as parameter to base class constructor, and make it static local
    mPrevious = &sInitial;
    mCurrent = &sInitial;
    mDummyDumpState = &mDummyFastMixerDumpState;

    // TODO: Add channel mask to NBAIO_Format.
    // We assume that the channel mask must be a valid positional channel mask.
    mSinkChannelMask = getChannelMaskFromCount(mSinkChannelCount);
    mBalance.setChannelMask(mSinkChannelMask);

#ifdef FAST_THREAD_STATISTICS
    mOldLoad.tv_sec = 0;
    mOldLoad.tv_nsec = 0;
#endif
}

FastMixerStateQueue* FastMixer::sq()
{
    return &mSQ;
}

const FastThreadState *FastMixer::poll()
{
    return mSQ.poll();
}

void FastMixer::setNBLogWriter(NBLog::Writer *logWriter __unused)
{
}

void FastMixer::onIdle()
{
    mPreIdle = *(const FastMixerState *)mCurrent;
    mCurrent = &mPreIdle;
}

void FastMixer::onExit()
{
    delete mMixer;
    free(mMixerBuffer);
    free(mSinkBuffer);
}

bool FastMixer::isSubClassCommand(FastThreadState::Command command)
{
    switch ((FastMixerState::Command) command) {
    case FastMixerState::MIX:
    case FastMixerState::WRITE:
    case FastMixerState::MIX_WRITE:
        return true;
    default:
        return false;
    }
}

void FastMixer::updateMixerTrack(int index, Reason reason) {
    const FastMixerState * const current = (const FastMixerState *) mCurrent;
    const FastTrack * const fastTrack = &current->mFastTracks[index];

    // check and update generation
    if (reason == REASON_MODIFY && mGenerations[index] == fastTrack->mGeneration) {
        return; // no change on an already configured track.
    }
    mGenerations[index] = fastTrack->mGeneration;

    // mMixer == nullptr on configuration failure (check done after generation update).
    if (mMixer == nullptr) {
        return;
    }

    switch (reason) {
    case REASON_REMOVE:
        mMixer->destroy(index);
        break;
    case REASON_ADD: {
        const status_t status = mMixer->create(
                index, fastTrack->mChannelMask, fastTrack->mFormat, AUDIO_SESSION_OUTPUT_MIX);
        LOG_ALWAYS_FATAL_IF(status != NO_ERROR,
                "%s: cannot create fast track index"
                " %d, mask %#x, format %#x in AudioMixer",
                __func__, index, fastTrack->mChannelMask, fastTrack->mFormat);
    }
        [[fallthrough]];  // now fallthrough to update the newly created track.
    case REASON_MODIFY:
        mMixer->setBufferProvider(index, fastTrack->mBufferProvider);

        float vlf, vrf;
        if (fastTrack->mVolumeProvider != nullptr) {
            const gain_minifloat_packed_t vlr = fastTrack->mVolumeProvider->getVolumeLR();
            vlf = float_from_gain(gain_minifloat_unpack_left(vlr));
            vrf = float_from_gain(gain_minifloat_unpack_right(vlr));
        } else {
            vlf = vrf = AudioMixer::UNITY_GAIN_FLOAT;
        }

        // set volume to avoid ramp whenever the track is updated (or created).
        // Note: this does not distinguish from starting fresh or
        // resuming from a paused state.
        mMixer->setParameter(index, AudioMixer::VOLUME, AudioMixer::VOLUME0, &vlf);
        mMixer->setParameter(index, AudioMixer::VOLUME, AudioMixer::VOLUME1, &vrf);

        mMixer->setParameter(index, AudioMixer::RESAMPLE, AudioMixer::REMOVE, nullptr);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::MAIN_BUFFER,
                (void *)mMixerBuffer);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::MIXER_FORMAT,
                (void *)(uintptr_t)mMixerBufferFormat);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::FORMAT,
                (void *)(uintptr_t)fastTrack->mFormat);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::CHANNEL_MASK,
                (void *)(uintptr_t)fastTrack->mChannelMask);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::MIXER_CHANNEL_MASK,
                (void *)(uintptr_t)mSinkChannelMask);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::HAPTIC_ENABLED,
                (void *)(uintptr_t)fastTrack->mHapticPlaybackEnabled);
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::HAPTIC_SCALE,
                (void *)(&(fastTrack->mHapticScale)));
        mMixer->setParameter(index, AudioMixer::TRACK, AudioMixer::HAPTIC_MAX_AMPLITUDE,
                (void *)(&(fastTrack->mHapticMaxAmplitude)));

        mMixer->enable(index);
        break;
    default:
        LOG_ALWAYS_FATAL("%s: invalid update reason %d", __func__, reason);
    }
}

void FastMixer::onStateChange()
{
    const FastMixerState * const current = (const FastMixerState *) mCurrent;
    const FastMixerState * const previous = (const FastMixerState *) mPrevious;
    FastMixerDumpState * const dumpState = (FastMixerDumpState *) mDumpState;
    const size_t frameCount = current->mFrameCount;

    // update boottime offset, in case it has changed
    mTimestamp.mTimebaseOffset[ExtendedTimestamp::TIMEBASE_BOOTTIME] =
            mBoottimeOffset.load();

    // handle state change here, but since we want to diff the state,
    // we're prepared for previous == &sInitial the first time through
    unsigned previousTrackMask;

    // check for change in output HAL configuration
    const NBAIO_Format previousFormat = mFormat;
    if (current->mOutputSinkGen != mOutputSinkGen) {
        mOutputSink = current->mOutputSink;
        mOutputSinkGen = current->mOutputSinkGen;
        mSinkChannelMask = current->mSinkChannelMask;
        mBalance.setChannelMask(mSinkChannelMask);
        if (mOutputSink == nullptr) {
            mFormat = Format_Invalid;
            mSampleRate = 0;
            mSinkChannelCount = 0;
            mSinkChannelMask = AUDIO_CHANNEL_NONE;
            mAudioChannelCount = 0;
        } else {
            mFormat = mOutputSink->format();
            mSampleRate = Format_sampleRate(mFormat);
            mSinkChannelCount = Format_channelCount(mFormat);
            LOG_ALWAYS_FATAL_IF(mSinkChannelCount > AudioMixer::MAX_NUM_CHANNELS);

            if (mSinkChannelMask == AUDIO_CHANNEL_NONE) {
                mSinkChannelMask = getChannelMaskFromCount(mSinkChannelCount);
            }
            mAudioChannelCount = mSinkChannelCount - audio_channel_count_from_out_mask(
                    mSinkChannelMask & AUDIO_CHANNEL_HAPTIC_ALL);
        }
        dumpState->mSampleRate = mSampleRate;
    }

    if ((!Format_isEqual(mFormat, previousFormat)) || (frameCount != previous->mFrameCount)) {
        // FIXME to avoid priority inversion, don't delete here
        delete mMixer;
        mMixer = nullptr;
        free(mMixerBuffer);
        mMixerBuffer = nullptr;
        free(mSinkBuffer);
        mSinkBuffer = nullptr;
        if (frameCount > 0 && mSampleRate > 0) {
            // FIXME new may block for unbounded time at internal mutex of the heap
            //       implementation; it would be better to have normal mixer allocate for us
            //       to avoid blocking here and to prevent possible priority inversion
            mMixer = new AudioMixer(frameCount, mSampleRate);
            // FIXME See the other FIXME at FastMixer::setNBLogWriter()
            NBLog::thread_params_t params;
            params.frameCount = frameCount;
            params.sampleRate = mSampleRate;
            LOG_THREAD_PARAMS(params);
            const size_t mixerFrameSize = mSinkChannelCount
                    * audio_bytes_per_sample(mMixerBufferFormat);
            mMixerBufferSize = mixerFrameSize * frameCount;
            (void)posix_memalign(&mMixerBuffer, 32, mMixerBufferSize);
            const size_t sinkFrameSize = mSinkChannelCount
                    * audio_bytes_per_sample(mFormat.mFormat);
            if (sinkFrameSize > mixerFrameSize) { // need a sink buffer
                mSinkBufferSize = sinkFrameSize * frameCount;
                (void)posix_memalign(&mSinkBuffer, 32, mSinkBufferSize);
            }
            mPeriodNs = (frameCount * 1000000000LL) / mSampleRate;    // 1.00
            mUnderrunNs = (frameCount * 1750000000LL) / mSampleRate;  // 1.75
            mOverrunNs = (frameCount * 500000000LL) / mSampleRate;    // 0.50
            mForceNs = (frameCount * 950000000LL) / mSampleRate;      // 0.95
            mWarmupNsMin = (frameCount * 750000000LL) / mSampleRate;  // 0.75
            mWarmupNsMax = (frameCount * 1250000000LL) / mSampleRate; // 1.25
        } else {
            mPeriodNs = 0;
            mUnderrunNs = 0;
            mOverrunNs = 0;
            mForceNs = 0;
            mWarmupNsMin = 0;
            mWarmupNsMax = LONG_MAX;
        }
        mMixerBufferState = UNDEFINED;
        // we need to reconfigure all active tracks
        previousTrackMask = 0;
        mFastTracksGen = current->mFastTracksGen - 1;
        dumpState->mFrameCount = frameCount;
#ifdef TEE_SINK
        mTee.set(mFormat, NBAIO_Tee::TEE_FLAG_OUTPUT_THREAD);
        mTee.setId(std::string("_") + std::to_string(mThreadIoHandle) + "_F");
#endif
    } else {
        previousTrackMask = previous->mTrackMask;
    }

    // check for change in active track set
    const unsigned currentTrackMask = current->mTrackMask;
    dumpState->mTrackMask = currentTrackMask;
    dumpState->mNumTracks = popcount(currentTrackMask);
    if (current->mFastTracksGen != mFastTracksGen) {

        // process removed tracks first to avoid running out of track names
        unsigned removedTracks = previousTrackMask & ~currentTrackMask;
        while (removedTracks != 0) {
            const int i = __builtin_ctz(removedTracks);
            removedTracks &= ~(1 << i);
            updateMixerTrack(i, REASON_REMOVE);
            // don't reset track dump state, since other side is ignoring it
        }

        // now process added tracks
        unsigned addedTracks = currentTrackMask & ~previousTrackMask;
        while (addedTracks != 0) {
            const int i = __builtin_ctz(addedTracks);
            addedTracks &= ~(1 << i);
            updateMixerTrack(i, REASON_ADD);
        }

        // finally process (potentially) modified tracks; these use the same slot
        // but may have a different buffer provider or volume provider
        unsigned modifiedTracks = currentTrackMask & previousTrackMask;
        while (modifiedTracks != 0) {
            const int i = __builtin_ctz(modifiedTracks);
            modifiedTracks &= ~(1 << i);
            updateMixerTrack(i, REASON_MODIFY);
        }

        mFastTracksGen = current->mFastTracksGen;
    }
}

void FastMixer::onWork()
{
    // TODO: pass an ID parameter to indicate which time series we want to write to in NBLog.cpp
    // Or: pass both of these into a single call with a boolean
    const FastMixerState * const current = (const FastMixerState *) mCurrent;
    FastMixerDumpState * const dumpState = (FastMixerDumpState *) mDumpState;

    if (mIsWarm) {
        // Logging timestamps for FastMixer is currently disabled to make memory room for logging
        // other statistics in FastMixer.
        // To re-enable, delete the #ifdef FASTMIXER_LOG_HIST_TS lines (and the #endif lines).
#ifdef FASTMIXER_LOG_HIST_TS
        LOG_HIST_TS();
#endif
        //ALOGD("Eric FastMixer::onWork() mIsWarm");
    } else {
        dumpState->mTimestampVerifier.discontinuity(
            dumpState->mTimestampVerifier.DISCONTINUITY_MODE_CONTINUOUS);
        // See comment in if block.
#ifdef FASTMIXER_LOG_HIST_TS
        LOG_AUDIO_STATE();
#endif
    }
    const FastMixerState::Command command = mCommand;
    const size_t frameCount = current->mFrameCount;

    if ((command & FastMixerState::MIX) && (mMixer != nullptr) && mIsWarm) {
        ALOG_ASSERT(mMixerBuffer != nullptr);

        // AudioMixer::mState.enabledTracks is undefined if mState.hook == process__validate,
        // so we keep a side copy of enabledTracks
        bool anyEnabledTracks = false;

        // for each track, update volume and check for underrun
        unsigned currentTrackMask = current->mTrackMask;
        while (currentTrackMask != 0) {
            const int i = __builtin_ctz(currentTrackMask);
            currentTrackMask &= ~(1 << i);
            const FastTrack* fastTrack = &current->mFastTracks[i];

            const int64_t trackFramesWrittenButNotPresented =
                mNativeFramesWrittenButNotPresented;
            const int64_t trackFramesWritten = fastTrack->mBufferProvider->framesReleased();
            ExtendedTimestamp perTrackTimestamp(mTimestamp);

            // Can't provide an ExtendedTimestamp before first frame presented.
            // Also, timestamp may not go to very last frame on stop().
            if (trackFramesWritten >= trackFramesWrittenButNotPresented &&
                    perTrackTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL] > 0) {
                perTrackTimestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL] =
                        trackFramesWritten - trackFramesWrittenButNotPresented;
            } else {
                perTrackTimestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL] = 0;
                perTrackTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL] = -1;
            }
            perTrackTimestamp.mPosition[ExtendedTimestamp::LOCATION_SERVER] = trackFramesWritten;
            fastTrack->mBufferProvider->onTimestamp(perTrackTimestamp);

            const int name = i;
            if (fastTrack->mVolumeProvider != nullptr) {
                const gain_minifloat_packed_t vlr = fastTrack->mVolumeProvider->getVolumeLR();
                float vlf = float_from_gain(gain_minifloat_unpack_left(vlr));
                float vrf = float_from_gain(gain_minifloat_unpack_right(vlr));

                mMixer->setParameter(name, AudioMixer::RAMP_VOLUME, AudioMixer::VOLUME0, &vlf);
                mMixer->setParameter(name, AudioMixer::RAMP_VOLUME, AudioMixer::VOLUME1, &vrf);
            }
            // FIXME The current implementation of framesReady() for fast tracks
            // takes a tryLock, which can block
            // up to 1 ms.  If enough active tracks all blocked in sequence, this would result
            // in the overall fast mix cycle being delayed.  Should use a non-blocking FIFO.
            const size_t framesReady = fastTrack->mBufferProvider->framesReady();
            if (ATRACE_ENABLED()) {
                // I wish we had formatted trace names
                char traceName[16];
                strcpy(traceName, "fRdy");
                traceName[4] = i + (i < 10 ? '0' : 'A' - 10);
                traceName[5] = '\0';
                ATRACE_INT(traceName, framesReady);
            }
            FastTrackDump *ftDump = &dumpState->mTracks[i];
            FastTrackUnderruns underruns = ftDump->mUnderruns;
            if (framesReady < frameCount) {
                if (framesReady == 0) {
                    underruns.mBitFields.mEmpty++;
                    underruns.mBitFields.mMostRecent = UNDERRUN_EMPTY;
                    mMixer->disable(name);
                } else {
                    // allow mixing partial buffer
                    underruns.mBitFields.mPartial++;
                    underruns.mBitFields.mMostRecent = UNDERRUN_PARTIAL;
                    mMixer->enable(name);
                    anyEnabledTracks = true;
                }
            } else {
                underruns.mBitFields.mFull++;
                underruns.mBitFields.mMostRecent = UNDERRUN_FULL;
                mMixer->enable(name);
                anyEnabledTracks = true;
            }
            ftDump->mUnderruns = underruns;
            ftDump->mFramesReady = framesReady;
            ftDump->mFramesWritten = trackFramesWritten;
        }

        if (anyEnabledTracks) {
            // process() is CPU-bound
            mMixer->process();
            mMixerBufferState = MIXED;
        } else if (mMixerBufferState != ZEROED) {
            mMixerBufferState = UNDEFINED;
        }

    } else if (mMixerBufferState == MIXED) {
        mMixerBufferState = UNDEFINED;
    }
    //bool didFullWrite = false;    // dumpsys could display a count of partial writes
    if ((command & FastMixerState::WRITE)
            && (mOutputSink != nullptr) && (mMixerBuffer != nullptr)) {
        if (mMixerBufferState == UNDEFINED) {
            memset(mMixerBuffer, 0, mMixerBufferSize);
            mMixerBufferState = ZEROED;
        }

        if (mMasterMono.load()) {  // memory_order_seq_cst
            mono_blend(mMixerBuffer, mMixerBufferFormat, Format_channelCount(mFormat), frameCount,
                    true /*limit*/);
        }

        // Balance must take effect after mono conversion.
        // mBalance detects zero balance within the class for speed (not needed here).
        mBalance.setBalance(mMasterBalance.load());
        mBalance.process((float *)mMixerBuffer, frameCount);

        // prepare the buffer used to write to sink
        void *buffer = mSinkBuffer != nullptr ? mSinkBuffer : mMixerBuffer;
        if (mFormat.mFormat != mMixerBufferFormat) { // sink format not the same as mixer format
            memcpy_by_audio_format(buffer, mFormat.mFormat, mMixerBuffer, mMixerBufferFormat,
                    frameCount * Format_channelCount(mFormat));
        }
        if (mSinkChannelMask & AUDIO_CHANNEL_HAPTIC_ALL) {
            // When there are haptic channels, the sample data is partially interleaved.
            // Make the sample data fully interleaved here.
            adjust_channels_non_destructive(buffer, mAudioChannelCount, buffer, mSinkChannelCount,
                    audio_bytes_per_sample(mFormat.mFormat),
                    frameCount * audio_bytes_per_frame(mAudioChannelCount, mFormat.mFormat));
        }
        // if non-nullptr, then duplicate write() to this non-blocking sink
#ifdef TEE_SINK
        mTee.write(buffer, frameCount);
#endif
        // FIXME write() is non-blocking and lock-free for a properly implemented NBAIO sink,
        //       but this code should be modified to handle both non-blocking and blocking sinks
        dumpState->mWriteSequence++;
        ATRACE_BEGIN("write");
        // ---- GammaEQ processing (fast-path friendly) -------------------------
        do {
            static SpeakerPEQ sPEQ;
            static StereoWidenerHB sWide;
            static CrystalizerLite sCryst;
            maybeReloadPEQ(sPEQ);
            wideMaybeReload(sWide);
            crystMaybeReload(sCryst);
            // Only process when speaker is routed (unless forced) and feature enabled.
            const bool forceAll = gammaeqForceAllOutputs();
            const bool spkOnly  = gammaeqSpeakerOnlyEnabled();
            if (!forceAll && spkOnly && !isSpeakerRoutedNow()) break;
            // If nothing is enabled, do nothing at all (A13 intent).
            if (!sPEQ.enabled && !sWide.enabled && !sCryst.enabled) break;

            const audio_format_t fmt = mFormat.mFormat;
            const int ch = mAudioChannelCount;
            const size_t sampCount = frameCount * (size_t)ch;
            if (ch < 2) break; // only stereo processing for now
            const float preamp = getGlobalPreampLin(); // linear gain (<=1 to add headroom)
            switch (fmt) {
                case AUDIO_FORMAT_PCM_16_BIT: {
                    static thread_local std::vector<float> tmp;
                    tmp.resize(sampCount);
                    memcpy_to_float_from_i16(tmp.data(),
                            reinterpret_cast<const int16_t*>(buffer), sampCount);
                    if (preamp != 1.0f) for (size_t i=0;i<sampCount;++i) tmp[i] *= preamp;
                    sPEQ.process(tmp.data(), frameCount, ch);
                    sCryst.updateCoef(mSampleRate);
                    sCryst.process(tmp.data(), frameCount, ch);
                    sWide.updateCoef(mSampleRate);
                    sWide.process(tmp.data(), frameCount, ch);
                    memcpy_to_i16_from_float(reinterpret_cast<int16_t*>(buffer),
                            tmp.data(), sampCount);
                    break;
                }
                case AUDIO_FORMAT_PCM_8_24_BIT: {
                    static thread_local std::vector<float> tmp;
                    tmp.resize(sampCount);
                    memcpy_to_float_from_q4_27(tmp.data(),
                            reinterpret_cast<const int32_t*>(buffer), sampCount);
                    if (preamp != 1.0f) for (size_t i=0;i<sampCount;++i) tmp[i] *= preamp;
                    sPEQ.process(tmp.data(), frameCount, ch);
                    sCryst.updateCoef(mSampleRate);
                    sCryst.process(tmp.data(), frameCount, ch);
                    sWide.updateCoef(mSampleRate);
                    sWide.process(tmp.data(), frameCount, ch);
                    memcpy_to_q4_27_from_float(reinterpret_cast<int32_t*>(buffer),
                            tmp.data(), sampCount);
                    break;
                }
                case AUDIO_FORMAT_PCM_24_BIT_PACKED: {
                    static thread_local std::vector<float> tmp;
                    tmp.resize(sampCount);
                    memcpy_to_float_from_p24(tmp.data(),
                            reinterpret_cast<const uint8_t*>(buffer), sampCount);
                    if (preamp != 1.0f) for (size_t i=0;i<sampCount;++i) tmp[i] *= preamp;
                    sPEQ.process(tmp.data(), frameCount, ch);
                    sCryst.updateCoef(mSampleRate);
                    sCryst.process(tmp.data(), frameCount, ch);
                    sWide.updateCoef(mSampleRate);
                    sWide.process(tmp.data(), frameCount, ch);
                    memcpy_to_p24_from_float(reinterpret_cast<uint8_t*>(buffer),
                            tmp.data(), sampCount);
                    break;
                }
                case AUDIO_FORMAT_PCM_FLOAT: {
                    float* fbuf = reinterpret_cast<float*>(buffer);
                    if (preamp != 1.0f) for (size_t i=0;i<sampCount;++i) fbuf[i] *= preamp;
                    sPEQ.process(fbuf, frameCount, ch);
                    sCryst.updateCoef(mSampleRate);
                    sCryst.process(fbuf, frameCount, ch);
                    sWide.updateCoef(mSampleRate);
                    sWide.process(fbuf, frameCount, ch);
                    break;
                }
                case AUDIO_FORMAT_PCM_32_BIT: {
                    static thread_local std::vector<float> tmp;
                    tmp.resize(sampCount);
                    memcpy_to_float_from_i32(tmp.data(),
                            reinterpret_cast<const int32_t*>(buffer), sampCount);
                    if (preamp != 1.0f) for (size_t i=0;i<sampCount;++i) tmp[i] *= preamp;
                    sPEQ.process(tmp.data(), frameCount, ch);
                    sCryst.updateCoef(mSampleRate);
                    sCryst.process(tmp.data(), frameCount, ch);
                    sWide.updateCoef(mSampleRate);
                    sWide.process(tmp.data(), frameCount, ch);
                    memcpy_to_i32_from_float(reinterpret_cast<int32_t*>(buffer),
                            tmp.data(), sampCount);
                    break;
                }
                default:
                    break;
            }
        } while (0);
        // ----------------------------------------------------------------------
        const ssize_t framesWritten = mOutputSink->write(buffer, frameCount);
        ATRACE_END();
        dumpState->mWriteSequence++;
        if (framesWritten >= 0) {
            ALOG_ASSERT((size_t) framesWritten <= frameCount);
            mTotalNativeFramesWritten += framesWritten;
            dumpState->mFramesWritten = mTotalNativeFramesWritten;
            //if ((size_t) framesWritten == frameCount) {
            //    didFullWrite = true;
            //}
        } else {
            dumpState->mWriteErrors++;
        }
        mAttemptedWrite = true;
        // FIXME count # of writes blocked excessively, CPU usage, etc. for dump

        if (mIsWarm) {
            ExtendedTimestamp timestamp; // local
            status_t status = mOutputSink->getTimestamp(timestamp);
            if (status == NO_ERROR) {
                dumpState->mTimestampVerifier.add(
                        timestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL],
                        timestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL],
                        mSampleRate);
                const int64_t totalNativeFramesPresented =
                        timestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL];
                if (totalNativeFramesPresented <= mTotalNativeFramesWritten) {
                    mNativeFramesWrittenButNotPresented =
                        mTotalNativeFramesWritten - totalNativeFramesPresented;
                    mTimestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL] =
                            timestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL];
                    mTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL] =
                            timestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL];
                    // We don't compensate for server - kernel time difference and
                    // only update latency if we have valid info.
                    const double latencyMs =
                            (double)mNativeFramesWrittenButNotPresented * 1000 / mSampleRate;
                    dumpState->mLatencyMs = latencyMs;
                    LOG_LATENCY(latencyMs);
                } else {
                    // HAL reported that more frames were presented than were written
                    mNativeFramesWrittenButNotPresented = 0;
                    status = INVALID_OPERATION;
                }
            } else {
                dumpState->mTimestampVerifier.error();
            }
            if (status == NO_ERROR) {
                mTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_SERVER] =
                        mTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL];
            } else {
                // fetch server time if we can't get timestamp
                mTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_SERVER] =
                        systemTime(SYSTEM_TIME_MONOTONIC);
                // clear out kernel cached position as this may get rapidly stale
                // if we never get a new valid timestamp
                mTimestamp.mPosition[ExtendedTimestamp::LOCATION_KERNEL] = 0;
                mTimestamp.mTimeNs[ExtendedTimestamp::LOCATION_KERNEL] = -1;
            }
        }
    }
}

}   // namespace android
