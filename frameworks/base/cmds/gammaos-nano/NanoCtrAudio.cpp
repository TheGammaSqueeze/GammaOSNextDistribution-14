// NanoCtrAudio - see NanoCtrAudio.h.
#include "NanoCtrAudio.h"

#include <aaudio/AAudio.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <thread>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <log/log.h>
#include <sys/system_properties.h>

extern "C" uint32_t __system_property_serial(const prop_info* __pi);

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

static uint32_t rd32le(const uint8_t* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
static uint16_t rd16le(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }

CtrAudio::CtrAudio(std::string soundDir) : mDir(std::move(soundDir)) { mVoices.resize(16); }

CtrAudio::~CtrAudio() {
    mShutdown.store(true);
    release();
}

CtrAudio::Clip::~Clip() {
    if (map) munmap(map, mapSize);
}

// RIFF WAV, 16 bit PCM mono or stereo, with an optional "smpl" loop (ctr_render_sounds.py).
std::shared_ptr<CtrAudio::Clip> CtrAudio::clip(const std::string& name) {
    std::lock_guard<std::mutex> lk(mClipM);
    auto it = mClips.find(name);
    if (it != mClips.end()) return it->second;
    std::shared_ptr<Clip> c;
    const int fd = ::open((mDir + "/" + name + ".wav").c_str(), O_RDONLY | O_CLOEXEC);
    struct stat st;
    if (fd >= 0 && fstat(fd, &st) == 0 && st.st_size > 44) {
        void* m = mmap(nullptr, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
        if (m != MAP_FAILED) {
            c = std::make_shared<Clip>();
            c->map = m;
            c->mapSize = (size_t)st.st_size;
            // read ahead so the mixer callback does not fault the samples in from storage
            madvise(m, c->mapSize, MADV_WILLNEED);
        }
    }
    if (fd >= 0) close(fd);
    if (c) {
        const uint8_t* d = (const uint8_t*)c->map;
        const size_t n = c->mapSize;
        bool ok = !memcmp(d, "RIFF", 4) && !memcmp(d + 8, "WAVE", 4);
        size_t p = 12;
        while (ok && p + 8 <= n) {
            const uint32_t sz = rd32le(d + p + 4);
            const uint8_t* body = d + p + 8;
            if (p + 8 + (size_t)sz > n) break;
            if (!memcmp(d + p, "fmt ", 4) && sz >= 16) {
                c->channels = rd16le(body + 2);
                c->rate = (int)rd32le(body + 4);
                ok = rd16le(body) == 1 && rd16le(body + 14) == 16 && (c->channels == 1 || c->channels == 2);
            } else if (!memcmp(d + p, "data", 4) && ((uintptr_t)body & 1) == 0) {
                c->pcm = (const int16_t*)body;
                c->frames = (int64_t)(sz / 2 / c->channels);
            } else if (!memcmp(d + p, "smpl", 4) && sz >= 36 + 24) {
                if (rd32le(body + 28) > 0) {
                    c->loop = true;
                    c->loopStart = rd32le(body + 36 + 8);
                    c->loopEnd = (int64_t)rd32le(body + 36 + 12) + 1;
                }
            }
            p += 8 + sz + (sz & 1);
        }
        c->bgm = name.rfind("BGM_", 0) == 0;
        c->name = name;
        if (c->loop && (c->loopEnd <= c->loopStart || c->loopEnd > c->frames)) c->loop = false;
        if (!ok || !c->pcm || c->frames == 0) c.reset();
    }
    if (!c) ALOGW("sound %s missing", name.c_str());
    mClips[name] = c;
    return c;
}

void CtrAudio::preload(const std::string& name) { (void)clip(name); }

int CtrAudio::play(const std::string& name, float volume, float pitch) {
    if (mShutdown.load()) return 0;
    std::shared_ptr<Clip> c = clip(name);
    if (!c) return 0;
    int handle;
    {
        std::lock_guard<std::mutex> lk(mVoiceM);
        Voice* slot = nullptr;
        for (Voice& v : mVoices) if (!v.active) { slot = &v; break; }
        if (!slot) {   // steal the oldest effect, never the BGM
            for (Voice& v : mVoices) if (!v.clip->bgm && (!slot || v.handle < slot->handle)) slot = &v;
        }
        if (!slot) return 0;
        handle = mNextHandle++;
        slot->handle = handle;
        slot->clip = c;
        slot->pos = 0;
        slot->pitch = pitch;
        slot->step = (double)c->rate / mOut[0].rate * pitch;
        slot->gain = volume * (c->bgm ? mBgmMaster : mSeMaster);
        slot->fadeTotal = slot->fadeLeft = 0;
        slot->active = true;
    }
    ensureRunning(0);
    return handle;
}

void CtrAudio::ensureRunning(int which) {
    Out& o = mOut[which];
    if (o.running.load() || o.starting.exchange(true)) return;
    std::thread([this, which]() {
        Out& o = mOut[which];
        std::lock_guard<std::mutex> lk(mStreamM);
        if (!mShutdown.load()) {
            if (!o.stream) openLocked(which);
            if (o.stream && AAudioStream_requestStart((AAudioStream*)o.stream) == AAUDIO_OK) o.running.store(true);
        }
        o.starting.store(false);
    }).detach();
}

void CtrAudio::stop(int handle, int fadeFrames) {
    std::lock_guard<std::mutex> lk(mVoiceM);
    for (Voice& v : mVoices) {
        if (!v.active || v.handle != handle) continue;
        if (fadeFrames <= 0) v.active = false;
        else { v.fadeTotal = v.fadeLeft = fadeFrames * mOut[0].rate / 60; }
    }
}

void CtrAudio::setPitch(int handle, float pitch) {
    std::lock_guard<std::mutex> lk(mVoiceM);
    for (Voice& v : mVoices)
        if (v.active && v.handle == handle) {
            v.pitch = pitch;
            v.step = (double)v.clip->rate / mOut[0].rate * pitch;
        }
}

void CtrAudio::stopAll(int fadeFrames) {
    std::lock_guard<std::mutex> lk(mVoiceM);
    for (Voice& v : mVoices) {
        if (!v.active) continue;
        if (fadeFrames <= 0) v.active = false;
        else if (!v.fadeTotal) { v.fadeTotal = v.fadeLeft = fadeFrames * mOut[0].rate / 60; }
    }
}

bool CtrAudio::playing(int handle) {
    std::lock_guard<std::mutex> lk(mVoiceM);
    for (Voice& v : mVoices) if (v.active && v.handle == handle) return true;
    return false;
}

int32_t CtrAudio::fill(int which, int16_t* out, int32_t frames) {
    Out& o = mOut[which];
    if (o.acc.size() < (size_t)frames * 2) o.acc.resize((size_t)frames * 2);
    int32_t* acc = o.acc.data();
    std::fill(acc, acc + (size_t)frames * 2, 0);
    bool any = false;
    {
        std::lock_guard<std::mutex> lk(mVoiceM);
        for (Voice& v : mVoices) {
            if (!v.active) continue;
            any = true;
            const Clip& c = *v.clip;
            const int16_t* pcm = c.pcm;
            const int ch = c.channels, rc = ch - 1;   // mono: both sides read sample 0
            for (int32_t i = 0; i < frames; i++) {
                int64_t i0 = (int64_t)v.pos;
                if (i0 >= c.frames) {
                    if (c.loop) { v.pos -= (double)(c.loopEnd - c.loopStart); i0 = (int64_t)v.pos; }
                    else { v.active = false; break; }
                }
                int64_t i1 = i0 + 1;
                if (c.loop && i1 >= c.loopEnd) i1 = c.loopStart + (i1 - c.loopEnd);
                if (i1 >= c.frames) i1 = c.frames - 1;
                const float f = (float)(v.pos - (double)i0);
                float g = v.gain;
                if (v.fadeTotal) {
                    g *= (float)v.fadeLeft / v.fadeTotal;
                    if (--v.fadeLeft <= 0) { v.active = false; break; }
                }
                const float l = pcm[i0 * ch] + (pcm[i1 * ch] - pcm[i0 * ch]) * f;
                const float r = pcm[i0 * ch + rc] + (pcm[i1 * ch + rc] - pcm[i0 * ch + rc]) * f;
                acc[i * 2] += (int32_t)(l * g);
                acc[i * 2 + 1] += (int32_t)(r * g);
                v.pos += v.step;
                if (c.loop && v.pos >= (double)c.loopEnd) v.pos -= (double)(c.loopEnd - c.loopStart);
            }
        }
    }
    // soft limiter above 3/4 of full scale: transparent below, never wraps or hard clips above
    for (int32_t i = 0; i < frames * 2; i++) {
        int32_t v = acc[i];
        const int32_t a = v < 0 ? -v : v;
        if (a > 24576) {
            const float over = (float)(a - 24576), room = 32767.0f - 24576.0f;
            const int32_t lim = 24576 + (int32_t)(over / (1.0f + over / room));
            v = v < 0 ? -lim : lim;
        }
        out[i] = (int16_t)v;
    }
    // level probe (sys.gammaos.nano.ctr.audiolog): the voices and the output peak every 2 s
    {
        static const prop_info* pi = nullptr;
        static uint32_t serial = ~0u;
        static bool on = false;
        if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.audiolog");
        if (pi && __system_property_serial(pi) != serial) {   // read only when it changes
            serial = __system_property_serial(pi);
            char v[PROP_VALUE_MAX] = {};
            on = __system_property_read(pi, nullptr, v) > 0 && v[0] == '1';
        }
        if (on) {
            for (int32_t i = 0; i < frames * 2; i++) mProbePeak = std::max(mProbePeak, std::abs((int)out[i]));
            if ((mProbeFrames += frames) >= (int64_t)o.rate * 2) {
                std::string list;
                std::lock_guard<std::mutex> lk(mVoiceM);
                for (const Voice& v2 : mVoices)
                    if (v2.active) {
                        char b[128];
                        snprintf(b, sizeof(b), " %s@%.0f/%lld g%.2f", v2.clip->name.c_str(), v2.pos, (long long)v2.clip->frames, v2.gain);
                        list += b;
                    }
                ALOGI("ctr audio: rate %d peak %d voices%s", o.rate, mProbePeak, list.c_str());
                mProbeFrames = 0;
                mProbePeak = 0;
            }
        }
    }
    // idle stop: after 1.5 s of silence the stream stops (play() restarts it)
    if (any) o.idleFrames = 0;
    else if ((o.idleFrames += frames) > (int64_t)o.rate * 3 / 2) {
        o.idleFrames = 0;
        o.running.store(false);
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool CtrAudio::openLocked(int which) {
    Out& o = mOut[which];
    if (o.stream) return true;
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK || !b) return false;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    // menu sounds do not need low latency: the power saving path keeps the fast mixer asleep
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_POWER_SAVING);
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(b, [](AAudioStream*, void* u, void* data, int32_t n) {
        return (aaudio_data_callback_result_t)static_cast<CtrAudio*>(u)->fill(0, (int16_t*)data, n);
    }, this);
    AAudioStreamBuilder_setErrorCallback(b, [](AAudioStream*, void* u, aaudio_result_t err) {
        // any error ends the stream (a route change disconnects it): reopen it
        (void)err;
        CtrAudio* self = static_cast<CtrAudio*>(u);
        if (self->mShutdown.load()) return;
        std::thread(&CtrAudio::recover, self, 0).detach();
    }, this);
    AAudioStream* st = nullptr;
    const aaudio_result_t r = AAudioStreamBuilder_openStream(b, &st);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || !st) { ALOGW("ctr audio: open failed (%d)", (int)r); return false; }
    const int rate = AAudioStream_getSampleRate(st);
    if (rate != o.rate) {
        // voices already queued on this output step at the old rate
        std::lock_guard<std::mutex> lk(mVoiceM);
        for (Voice& v : mVoices)
            if (v.active) v.step = (double)v.clip->rate / rate * v.pitch;
        o.rate = rate;
    }
    o.stream = st;
    return true;
}

void CtrAudio::recover(int which) {
    Out& o = mOut[which];
    std::lock_guard<std::mutex> lk(mStreamM);
    if (mShutdown.load()) return;
    bool was = o.running.load();
    {
        std::lock_guard<std::mutex> vl(mVoiceM);
        for (const Voice& v : mVoices) if (v.active) was = true;   // something still wants to sound
    }
    if (o.stream) {
        AAudioStream_requestStop((AAudioStream*)o.stream);
        AAudioStream_close((AAudioStream*)o.stream);
        o.stream = nullptr;
    }
    o.running.store(false);
    if (openLocked(which) && was && AAudioStream_requestStart((AAudioStream*)o.stream) == AAUDIO_OK) o.running.store(true);
}

void CtrAudio::release() {
    std::lock_guard<std::mutex> lk(mStreamM);
    for (Out& o : mOut) {
        if (o.stream) {
            AAudioStream_requestStop((AAudioStream*)o.stream);
            AAudioStream_close((AAudioStream*)o.stream);
            o.stream = nullptr;
        }
        o.running.store(false);
    }
}

}  // namespace ctr
}  // namespace android
