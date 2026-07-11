// See NanoBootChime.h. Direct-to-ALSA boot chime (RG DS / rk817), bypassing AudioFlinger so it
// sounds on its 1.45s mark before the audio server is up.

#include "NanoBootChime.h"

#include <tinyalsa/asoundlib.h>

#include <android/log.h>
#include <time.h>
#include <unistd.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <atomic>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define NBC_TAG "GammaOSNano"
#define NBC_I(...) __android_log_print(ANDROID_LOG_INFO, NBC_TAG, __VA_ARGS__)
#define NBC_W(...) __android_log_print(ANDROID_LOG_WARN, NBC_TAG, __VA_ARGS__)

namespace {

struct WavInfo {
    uint32_t rate = 0;
    uint16_t channels = 0;
    uint16_t bits = 0;
    long     dataOff = 0;
    uint32_t dataLen = 0;
    bool     ok = false;
};

inline uint32_t rd32(const unsigned char* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
inline uint16_t rd16(const unsigned char* p) {
    return (uint16_t)((uint16_t)p[0] | ((uint16_t)p[1] << 8));
}

// Walk the RIFF chunks. The `data` chunk is NOT necessarily adjacent to `fmt ` (this file has a
// LIST/INFO between them), so we must skip unknown chunks by their size rather than assume order.
WavInfo parseWav(FILE* f) {
    WavInfo w;
    unsigned char hdr[12];
    if (fread(hdr, 1, 12, f) != 12) return w;
    if (memcmp(hdr, "RIFF", 4) != 0 || memcmp(hdr + 8, "WAVE", 4) != 0) return w;
    bool haveFmt = false;
    for (;;) {
        unsigned char ch[8];
        if (fread(ch, 1, 8, f) != 8) break;
        uint32_t sz = rd32(ch + 4);
        if (memcmp(ch, "fmt ", 4) == 0) {
            unsigned char fmt[16];
            uint32_t n = sz < 16 ? sz : 16;
            if (fread(fmt, 1, n, f) != n) break;
            uint16_t tag = rd16(fmt);
            w.channels = rd16(fmt + 2);
            w.rate     = rd32(fmt + 4);
            w.bits     = rd16(fmt + 14);
            haveFmt    = (tag == 1);   // PCM
            if (sz > n) fseek(f, (long)(sz - n), SEEK_CUR);
        } else if (memcmp(ch, "data", 4) == 0) {
            w.dataOff = ftell(f);
            w.dataLen = sz;
            break;
        } else {
            fseek(f, (long)((sz + 1) & ~1u), SEEK_CUR);   // skip, word-aligned
        }
    }
    w.ok = haveFmt && w.dataOff > 0 && w.dataLen > 0 && w.bits == 16 && w.channels > 0 && w.rate > 0;
    return w;
}

inline int64_t monoMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// ---- Sequential direct-PCM engine -------------------------------------------------------------
// One persistent worker thread owns the single rk817 substream. It parks (closing the PCM so the
// audio server can have it) when there is nothing to play, and wakes to play queued one-shots or
// the one looping voice. Files are S16/48k/2ch (verified); we always emit 2ch.

struct DVoice {
    std::vector<int16_t> pcm;              // interleaved S16
    unsigned channels = 2;
    bool  loop = false;
    float gain = 1.0f;
    std::atomic<bool>* inFlight = nullptr; // one-shot completion flag (chime compat); null otherwise
};

struct DEngine {
    std::mutex m;
    std::condition_variable cv;
    std::deque<DVoice> queue;              // pending one-shots (priority over the loop)
    DVoice loopVoice;
    bool   haveLoop = false;
    std::atomic<float> liveLoopGain{1.0f}; // lock-free live gain for the loop
    bool   shutdown = false;
    bool   workerRunning = false;
    std::atomic<bool> pcmOwned{false};     // true while the worker holds the substream
    std::atomic<bool> failed{false};       // set on the first pcm_open failure -> callers fall back to AAudio
};
DEngine gEng;

// Fully read a 16-bit PCM WAV into an interleaved S16 vector. False on any problem.
bool loadDVoice(const std::string& path, DVoice& out) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { NBC_W("direct: cannot open %s", path.c_str()); return false; }
    WavInfo w = parseWav(f);
    if (!w.ok) { NBC_W("direct: unsupported wav %s", path.c_str()); fclose(f); return false; }
    fseek(f, w.dataOff, SEEK_SET);
    out.channels = w.channels;
    out.pcm.resize(w.dataLen / 2);
    size_t got = fread(out.pcm.data(), 1, w.dataLen, f);
    fclose(f);
    out.pcm.resize(got / 2);
    return !out.pcm.empty();
}

const int64_t kDirectMaxLoopMs = 60000;    // hard cap: never hold the card past the free window

void directWorker() {
    struct pcm* pcm = nullptr;
    auto closePcm = [&]() { if (pcm) { pcm_close(pcm); pcm = nullptr; } gEng.pcmOwned.store(false); };
    DVoice cur; bool haveCur = false; size_t pos = 0; int64_t loopStartMs = 0;
    std::vector<int16_t> period(960 * 2);

    for (;;) {
        {
            std::unique_lock<std::mutex> lk(gEng.m);
            if (gEng.shutdown) break;
            if (haveCur && cur.loop && !gEng.queue.empty()) haveCur = false;   // preempt loop for a one-shot
            if (!haveCur) {
                if (!gEng.queue.empty()) {
                    cur = std::move(gEng.queue.front()); gEng.queue.pop_front();
                    cur.loop = false; haveCur = true; pos = 0;
                } else if (gEng.haveLoop) {
                    cur = gEng.loopVoice; cur.loop = true; haveCur = true; pos = 0; loopStartMs = monoMs();
                } else {
                    closePcm();                        // nothing to play: free the card and park
                    gEng.cv.wait(lk, [] { return gEng.shutdown || !gEng.queue.empty() || gEng.haveLoop; });
                    if (gEng.shutdown) break;
                    continue;
                }
            }
        }

        if (!pcm) {                                    // (re)open the PCM for this voice
            if (struct mixer* mx = mixer_open(0)) {
                struct mixer_ctl* c = mixer_get_ctl_by_name(mx, "Playback Path");
                if (c && mixer_ctl_get_type(c) == MIXER_CTL_TYPE_ENUM)
                    mixer_ctl_set_enum_by_string(c, "SPK");
                mixer_close(mx);
            }
            struct pcm_config cfg; memset(&cfg, 0, sizeof(cfg));
            cfg.channels = 2; cfg.rate = 48000; cfg.format = PCM_FORMAT_S16_LE;
            cfg.period_size = 960; cfg.period_count = 4;
            pcm = pcm_open(0, 0, PCM_OUT, &cfg);
            if (!pcm || !pcm_is_ready(pcm)) {          // EBUSY / unsupported -> this device can't do direct
                NBC_W("direct: pcm_open failed: %s -> fall back to AAudio", pcm ? pcm_get_error(pcm) : "(null)");
                if (pcm) { pcm_close(pcm); pcm = nullptr; }
                if (haveCur && cur.inFlight) cur.inFlight->store(false);
                haveCur = false;
                gEng.failed.store(true);               // callers use the AAudio path from now on
                std::lock_guard<std::mutex> lk(gEng.m); gEng.queue.clear(); gEng.haveLoop = false;
                break;                                 // exit the worker; a later play respawns it (and re-checks)
            }
            gEng.pcmOwned.store(true);
        }

        // Fill one 2ch period from cur.pcm at `pos`, applying gain; wrap (loop) / zero-pad (one-shot).
        float gf = cur.loop ? gEng.liveLoopGain.load() : cur.gain;
        int   g  = (int)(gf * 32768.0f + 0.5f); if (g < 0) g = 0; else if (g > 32768) g = 32768;
        const size_t len = cur.pcm.size();
        const size_t step = (cur.channels == 1) ? 1 : 2;
        for (size_t i = 0; i < 960; ++i) {
            int16_t l = 0, r = 0;
            if (pos < len) { l = cur.pcm[pos]; r = (step == 2 && pos + 1 < len) ? cur.pcm[pos + 1] : l; }
            if (g != 32768) {
                int vl = ((int)l * g) >> 15; if (vl > 32767) vl = 32767; else if (vl < -32768) vl = -32768; l = (int16_t)vl;
                int vr = ((int)r * g) >> 15; if (vr > 32767) vr = 32767; else if (vr < -32768) vr = -32768; r = (int16_t)vr;
            }
            period[i * 2] = l; period[i * 2 + 1] = r;
            pos += step;
            if (pos >= len && cur.loop) pos = 0;
        }

        if (pcm_write(pcm, period.data(), (unsigned)(period.size() * 2)) != 0) {
            NBC_W("direct: pcm_write error %s -> yield", pcm_get_error(pcm));
            break;                                     // someone else wants the card / hw error: yield
        }

        if (!cur.loop && pos >= len) {                 // one-shot finished
            if (cur.inFlight) cur.inFlight->store(false);
            haveCur = false;
        }
        if (cur.loop && (monoMs() - loopStartMs) > kDirectMaxLoopMs) {
            NBC_W("direct: loop wall-cap hit -> release");
            break;
        }
    }
    if (haveCur && cur.inFlight) cur.inFlight->store(false);
    closePcm();
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.workerRunning = false;
}

void ensureWorkerLocked() {                            // caller holds gEng.m
    if (gEng.workerRunning) return;
    gEng.workerRunning = true;
    gEng.shutdown = false;
    std::thread(directWorker).detach();
}

}  // namespace

bool nanoBootChimeIsRkDevice() {
    static int cached = -1;
    if (cached >= 0) return cached == 1;
    cached = 0;
    FILE* f = fopen("/proc/asound/card0/id", "rb");
    if (f) {
        char id[64] = {};
        size_t n = fread(id, 1, sizeof(id) - 1, f);
        fclose(f);
        while (n > 0 && (id[n - 1] == '\n' || id[n - 1] == '\r' || id[n - 1] == ' ')) id[--n] = 0;
        if (strcmp(id, "rockchiprk817") == 0) cached = 1;
    }
    return cached == 1;
}

bool nanoDirectAudioUsable() {
    if (gEng.failed.load()) return false;              // a prior direct open failed -> AAudio only
    static int hasNode = -1;                           // card 0 playback PCM present? (cheap, cached)
    if (hasNode < 0) hasNode = (access("/dev/snd/pcmC0D0p", W_OK) == 0) ? 1 : 0;
    return hasNode == 1;
}

// Back-compat shim for the shipped chime call-site: queue the chime as the first one-shot, still
// wired to the caller's in-flight flag. card/device are ignored (the engine is fixed to card 0).
void nanoDirectChimePlay(const std::string& wavPath, unsigned /*card*/, unsigned /*device*/,
                         float gain, std::atomic<bool>* inFlight) {
    if (gain <= 0.0009f) { if (inFlight) inFlight->store(false); return; }   // muted
    DVoice v; v.gain = gain; v.inFlight = inFlight;
    if (!loadDVoice(wavPath, v)) { if (inFlight) inFlight->store(false); return; }
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.queue.push_back(std::move(v));
    ensureWorkerLocked();
    gEng.cv.notify_one();
    NBC_I("direct: queued chime %s gain=%.3f", wavPath.c_str(), gain);
}

void nanoDirectPlayOneShot(const std::string& wavPath, float gain) {
    if (gain <= 0.0009f) return;                       // muted -> nothing
    DVoice v; v.gain = gain;
    if (!loadDVoice(wavPath, v)) return;
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.queue.push_back(std::move(v));
    ensureWorkerLocked();
    gEng.cv.notify_one();
    NBC_I("direct: queued one-shot %s gain=%.3f", wavPath.c_str(), gain);
}

void nanoDirectStartLoop(const std::string& wavPath, float gain) {
    DVoice v; v.gain = gain; v.loop = true;
    if (!loadDVoice(wavPath, v)) return;
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.loopVoice = std::move(v);
    gEng.haveLoop = true;
    gEng.liveLoopGain.store(gain <= 0.0009f ? 0.0f : gain);
    ensureWorkerLocked();
    gEng.cv.notify_one();
    NBC_I("direct: started loop %s gain=%.3f", wavPath.c_str(), gain);
}

void nanoDirectSetGain(float gain) {
    gEng.liveLoopGain.store(gain < 0.0f ? 0.0f : gain);   // lock-free; worker reads it each period
}

void nanoDirectStopLoop() {
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.haveLoop = false;
    gEng.loopVoice.pcm.clear();
    gEng.cv.notify_one();
}

void nanoDirectShutdown() {
    { std::lock_guard<std::mutex> lk(gEng.m);
      gEng.shutdown = true; gEng.haveLoop = false; gEng.queue.clear(); gEng.cv.notify_one(); }
    // Bounded wait (<=250ms) for the worker to close the PCM, so an AAudio open cannot EBUSY on us.
    for (int i = 0; i < 50 && gEng.pcmOwned.load(); ++i) {
        struct timespec ts { 0, 5 * 1000 * 1000 }; nanosleep(&ts, nullptr);
    }
    NBC_I("direct: shutdown (pcmOwned=%d)", gEng.pcmOwned.load() ? 1 : 0);
}

bool nanoDirectActive() { return gEng.pcmOwned.load(); }
