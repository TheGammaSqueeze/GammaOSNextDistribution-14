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
    std::atomic<bool> holdOpen{false};     // keep the PCM open (writing silence) when idle, so one-shots
                                           // mix in without a re-open (PS3 early-boot card0 hold)
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

// Enable the codec's speaker output route for the direct PCM. The vendor audio HAL normally programs
// the route when it opens a stream, but the direct path runs BEFORE the audio server is up, so the DAC
// route + speaker switch can be off and the PCM plays into a disabled output (silent). Apply a
// best-effort set of common output-enable controls - only the ones that actually EXIST on this card -
// so the early audio is audible on the rk817 (RG DS) AND the Allwinner sun50iw10codec (TrimUI Brick),
// and a graceful no-op on other codecs (they keep whatever init/default route they have). Device-
// agnostic where possible; the audio server re-programs its own route once it takes the card back.
void directEnableSpeaker() {
    struct mixer* mx = mixer_open(0);
    if (!mx) return;
    std::string set;
    auto setEnum = [&](const char* name, const char* val) -> bool {
        struct mixer_ctl* c = mixer_get_ctl_by_name(mx, name);
        if (c && mixer_ctl_get_type(c) == MIXER_CTL_TYPE_ENUM &&
            mixer_ctl_set_enum_by_string(c, val) == 0) { set += name; set += "; "; return true; }
        return false;
    };
    auto setInt = [&](const char* name, int v) {
        struct mixer_ctl* c = mixer_get_ctl_by_name(mx, name);
        if (!c) return;
        unsigned n = mixer_ctl_get_num_values(c);
        for (unsigned i = 0; i < n; i++) mixer_ctl_set_value(c, i, v);
        if (n) { set += name; set += "; "; }
    };
    // Device-agnostic route-enable: try every known output-route control; each is guarded by its
    // own existence check, so only the controls a given codec actually has take effect. On rk817
    // (RG DS) that lands as [Playback Path=SPK; Headphone Switch; Speaker Switch] (the AIF/DAC/HP
    // Allwinner controls are absent and no-op); on the Allwinner sun50iw10codec (TrimUI Brick) it
    // lands as [DAC volume; Headphone Switch; HpSpeaker Switch] (Playback Path is absent). This is
    // the exact configuration the user confirmed working on both devices - do NOT gate the discrete
    // chain behind Playback Path (that variant is unverified on rk817).
    setEnum("Playback Path", "SPK");
    setEnum("AIF1IN0R Mux", "AIF1_DA0R");
    setEnum("AIF1IN0L Mux", "AIF1_DA0L");
    setInt ("DACR Mixer AIF1DA0R Switch", 1);
    setInt ("DACL Mixer AIF1DA0L Switch", 1);
    setEnum("HP_R Mux", "DACR_HPR_Switch");
    setEnum("HP_L Mux", "DACL_HPL_Switch");
    setInt ("DAC volume", 160);
    setInt ("headphone volume", 60);
    setInt ("Headphone Switch", 1);
    setInt ("HpSpeaker Switch", 1);
    setInt ("Speaker Switch", 1);
    // ES8388 (rockchip-es8388, e.g. RG Vita Pro RK3576): the codec Speaker/Headphone Switch above
    // only gate the codec output enable; the DAC still has to be routed through the output mixer to
    // the OUT1/OUT2 stages and the external amp GPIOs, and the output amp volumes default to 0 (min,
    // ~-45 dB). Enable the whole DAC -> mixer -> output -> amp playback path so the direct PCM is
    // audible before the HAL takes the card. Device-confirmed on the RG Vita Pro; guarded by
    // existence so it is a no-op on the rk817 / Allwinner codecs.
    setInt ("Left Mixer Left Playback Switch", 1);   // DAC L -> left output mixer
    setInt ("Right Mixer Right Playback Switch", 1); // DAC R -> right output mixer
    setInt ("OUT1 Switch", 1);                        // enable Output 1 (headphone)
    setInt ("OUT2 Switch", 1);                        // enable Output 2 (speaker)
    setInt ("Output 1 Playback Volume", 30);          // ~0 dB (range 0..33)
    setInt ("Output 2 Playback Volume", 30);
    setInt ("spk switch", 1);                         // external speaker amp enable
    setInt ("hp switch", 1);                          // external headphone amp enable

    // MediaTek MT6789 / MT6366 (mt6358-family, e.g. Helio G99 handhelds): audio routing is a DAPM
    // interconnect MATRIX, not a set of simple output switches. The DL memif (card0 device0 = DL1) has
    // NO backend wired at rest, so pcm_write fails "cannot prepare channel: Invalid argument" (the DPCM
    // frontend finds no connected backend to prepare) and nothing in the analog chain powers. Connect
    // DL1 into the internal DAC (ADDA) and point the codec output muxes at the loudspeaker path; the
    // AFE/codec supplies, clocks, charge pump and the external speaker amp are all DAPM-powered the
    // moment the PCM starts running. Device-verified on an MT6789 board (Ext_Speaker_Amp powers On,
    // user-confirmed audible). Existence-guarded, so a no-op on the rk817 / Allwinner / ES8388 codecs.
    setInt ("ADDA_DL_CH1 DL1_CH1", 1);                // DL1 L -> internal DAC left
    setInt ("ADDA_DL_CH2 DL1_CH2", 1);                // DL1 R -> internal DAC right
    setEnum("DAC In Mux", "Normal Path");             // DAC fed from the DL path, not the internal sine-gen
    setEnum("HPL Mux", "LoudSPK Playback");           // HP buffer L -> loudspeaker (external amp) path
    setEnum("HPR Mux", "LoudSPK Playback");           // HP buffer R -> loudspeaker (external amp) path

    // Generalized best-effort output-route enable, so a NEW codec that follows the same pattern (a
    // playback route left disabled at rest, as on the ES8388) can also get early audio without an
    // explicit list. ADDITIVE + conservative: walk every control and (a) turn ON output-route BOOLEAN
    // switches (speaker / headphone / lineout / OUTn / *playback switch / *mixer*playback), and (b)
    // lift any ANALOG OUTPUT volume that is parked at its range MINIMUM (a disabled output) up to its
    // maximum - the direct path applies its own per-sample gain, so a full-scale output stage is
    // correct. NEVER touches capture / mic / jack-detect / loopback / bypass / digital DAC-PCM
    // controls. For the known codecs above it just re-confirms the same output switches (harmless), and
    // the min-only volume guard means it does not disturb an output whose volume was already set.
    {
        auto lc = [](const char* s){ std::string o; for (; s && *s; ++s) { char c = *s; o.push_back((c >= 'A' && c <= 'Z') ? (char)(c + 32) : c); } return o; };
        auto has = [](const std::string& h, const char* n){ return h.find(n) != std::string::npos; };
        unsigned nctl = mixer_get_num_ctls(mx);
        for (unsigned i = 0; i < nctl; i++) {
            struct mixer_ctl* c = mixer_get_ctl(mx, i);
            if (!c) continue;
            const char* raw = mixer_ctl_get_name(c);
            if (!raw) continue;
            std::string nm = lc(raw);
            if (has(nm,"capture") || has(nm,"mic") || has(nm,"adc") || has(nm,"loopback") ||
                has(nm,"bypass") || has(nm,"jack") || has(nm,"sidetone") || has(nm,"monitor") ||
                has(nm,"detect") || has(nm,"mute")) continue;                 // never touch input / detect / MUTE controls
                                                                              // (a "*Mute" is active-high: setting it to 1 would MUTE the output)
            int type = mixer_ctl_get_type(c);
            unsigned nv = mixer_ctl_get_num_values(c);
            if (nv == 0) continue;
            if (type == MIXER_CTL_TYPE_BOOL) {
                bool out = has(nm,"speaker") || has(nm,"spk") || has(nm,"headphone") || has(nm,"hpout") ||
                           has(nm,"hp switch") || has(nm,"lineout") || has(nm,"line out") ||
                           has(nm,"receiver") || has(nm,"earpiece") || has(nm,"playback switch") ||
                           has(nm,"out1") || has(nm,"out2") || has(nm,"out3") || has(nm,"out4") ||
                           (has(nm,"mixer") && has(nm,"playback"));
                if (!out) continue;
                bool changed = false;
                for (unsigned v = 0; v < nv; v++) if (mixer_ctl_get_value(c, v) != 1) { mixer_ctl_set_value(c, v, 1); changed = true; }
                if (changed) { set += raw; set += "; "; }
            } else if (type == MIXER_CTL_TYPE_INT) {
                bool outvol = has(nm,"playback volume") &&
                    (has(nm,"output") || has(nm,"speaker") || has(nm,"spk") || has(nm,"headphone") ||
                     has(nm,"hpout") || has(nm,"lineout") || has(nm,"line out") || has(nm,"receiver") ||
                     has(nm,"earpiece")) &&
                    !has(nm,"dac") && !has(nm,"pcm") && !has(nm,"digital");   // leave digital DAC vols alone (often inverted)
                if (!outvol) continue;
                int mn = mixer_ctl_get_range_min(c), mxv = mixer_ctl_get_range_max(c);
                if (mxv <= mn) continue;
                bool atMin = true;
                for (unsigned v = 0; v < nv; v++) if (mixer_ctl_get_value(c, v) != mn) { atMin = false; break; }
                if (!atMin) continue;                                          // only lift a fully-min (disabled) output
                for (unsigned v = 0; v < nv; v++) mixer_ctl_set_value(c, v, mxv);
                set += raw; set += "(vol->max); ";
            }
        }
    }
    mixer_close(mx);
    NBC_I("direct: route-enable set [%s]", set.empty() ? "(none - unknown codec)" : set.c_str());
}

void directWorker() {
    struct pcm* pcm = nullptr;
    auto closePcm = [&]() { if (pcm) { pcm_close(pcm); pcm = nullptr; } gEng.pcmOwned.store(false); };
    std::vector<int16_t> period(960 * 2);

    // Worker-local MIX state: the one looping bed (BGM) plus any concurrent one-shots (chime / SFX).
    // The engine used to PREEMPT the loop for a one-shot (the BGM cut out); it now MIXES them - every
    // active voice is summed into the period and clipped to S16 - so the DSi carousel music and its
    // nav / enter / back / launch effects sound together during the pre-boot-complete window, 1:1 with
    // the web app. The loop plays continuously (no restart on each effect).
    DVoice loopV; bool loopActive = false; size_t loopPos = 0; int64_t loopStartMs = 0;
    struct Shot { DVoice v; size_t pos; };
    std::vector<Shot> shots;
    int64_t holdSilenceStart = 0;   // when a voice-less hold-open period began (wall-capped below)

    for (;;) {
        {
            std::unique_lock<std::mutex> lk(gEng.m);
            if (gEng.shutdown) break;
            // Admit every pending one-shot into the active mix (no longer preempting the loop).
            while (!gEng.queue.empty()) { shots.push_back({ std::move(gEng.queue.front()), 0 }); gEng.queue.pop_front(); }
            // Sync the loop bed to the engine's current request.
            if (gEng.haveLoop && !loopActive)      { loopV = gEng.loopVoice; loopActive = true; loopPos = 0; loopStartMs = monoMs(); }
            else if (!gEng.haveLoop && loopActive) { loopActive = false; loopV.pcm.clear(); }
            if (!loopActive && shots.empty() && !gEng.holdOpen.load()) {   // nothing to play + no hold: free the card and park
                closePcm();
                gEng.cv.wait(lk, [] { return gEng.shutdown || !gEng.queue.empty() || gEng.haveLoop || gEng.holdOpen.load(); });
                if (gEng.shutdown) break;
                continue;
            }
            // holdOpen with nothing to play: fall through and write a SILENT period so the PCM
            // (card0) stays open and one-shots can mix in without a re-open (PS3 early-boot hold).
        }

        if (!pcm) {                                    // (re)open the PCM
            directEnableSpeaker();                      // device-agnostic route-enable (rk817 + Allwinner + ...)
            struct pcm_config cfg; memset(&cfg, 0, sizeof(cfg));
            cfg.channels = 2; cfg.rate = 48000; cfg.format = PCM_FORMAT_S16_LE;
            cfg.period_size = 960; cfg.period_count = 4;
            pcm = pcm_open(0, 0, PCM_OUT, &cfg);
            if (!pcm || !pcm_is_ready(pcm)) {          // EBUSY / unsupported -> this device can't do direct
                NBC_W("direct: pcm_open failed: %s -> fall back to AAudio", pcm ? pcm_get_error(pcm) : "(null)");
                if (pcm) { pcm_close(pcm); pcm = nullptr; }
                for (auto& s : shots) if (s.v.inFlight) s.v.inFlight->store(false);
                shots.clear(); loopActive = false;
                gEng.failed.store(true);               // callers use the AAudio path from now on
                std::lock_guard<std::mutex> lk(gEng.m); gEng.queue.clear(); gEng.haveLoop = false;
                break;                                 // exit the worker; a later play respawns it (and re-checks)
            }
            gEng.pcmOwned.store(true);
        }

        // Mix one 2ch period: the loop bed + every active one-shot, per-voice gain, summed, clipped to
        // S16. The loop reads its live gain once per period (lock-free); each one-shot uses its fixed gain.
        int lg = 0;
        if (loopActive) { float gf = gEng.liveLoopGain.load(); lg = (int)(gf * 32768.0f + 0.5f); if (lg < 0) lg = 0; else if (lg > 32768) lg = 32768; }
        const size_t loopLen  = loopActive ? loopV.pcm.size() : 0;
        const size_t loopStep = (loopActive && loopV.channels == 1) ? 1 : 2;
        for (size_t i = 0; i < 960; ++i) {
            int sl = 0, sr = 0;
            if (loopActive && loopLen) {
                int16_t l = loopV.pcm[loopPos];
                int16_t r = (loopStep == 2 && loopPos + 1 < loopLen) ? loopV.pcm[loopPos + 1] : l;
                sl += ((int)l * lg) >> 15;
                sr += ((int)r * lg) >> 15;
                loopPos += loopStep; if (loopPos >= loopLen) loopPos = 0;
            }
            for (Shot& s : shots) {
                const size_t len = s.v.pcm.size();
                if (s.pos >= len) continue;
                int g = (int)(s.v.gain * 32768.0f + 0.5f); if (g < 0) g = 0; else if (g > 32768) g = 32768;
                const size_t step = (s.v.channels == 1) ? 1 : 2;
                int16_t l = s.v.pcm[s.pos];
                int16_t r = (step == 2 && s.pos + 1 < len) ? s.v.pcm[s.pos + 1] : l;
                sl += ((int)l * g) >> 15;
                sr += ((int)r * g) >> 15;
                s.pos += step;
            }
            if (sl > 32767) sl = 32767; else if (sl < -32768) sl = -32768;
            if (sr > 32767) sr = 32767; else if (sr < -32768) sr = -32768;
            period[i * 2] = (int16_t)sl; period[i * 2 + 1] = (int16_t)sr;
        }

        if (pcm_write(pcm, period.data(), (unsigned)(period.size() * 2)) != 0) {
            NBC_W("direct: pcm_write error %s -> yield", pcm_get_error(pcm));
            break;                                     // someone else wants the card / hw error: yield
        }

        // Retire finished one-shots (clear their in-flight flags).
        for (size_t k = 0; k < shots.size(); ) {
            if (shots[k].pos >= shots[k].v.pcm.size()) {
                if (shots[k].v.inFlight) shots[k].v.inFlight->store(false);
                shots.erase(shots.begin() + k);
            } else ++k;
        }
        if (loopActive && (monoMs() - loopStartMs) > kDirectMaxLoopMs) {
            NBC_W("direct: loop wall-cap hit -> release");
            break;
        }
        // Silent hold-open safety: if we are ONLY holding the card open (no loop, no shots) past the
        // wall cap, release it so a stuck hold (e.g. the render loop stopped before boot_completed)
        // cannot own card0 forever and block the audio HAL. A real voice resets the timer.
        if (gEng.holdOpen.load() && !loopActive && shots.empty()) {
            if (holdSilenceStart == 0) holdSilenceStart = monoMs();
            else if (monoMs() - holdSilenceStart > kDirectMaxLoopMs) { NBC_W("direct: hold-open wall-cap -> release"); break; }
        } else {
            holdSilenceStart = 0;
        }
    }
    for (auto& s : shots) if (s.v.inFlight) s.v.inFlight->store(false);
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

// MediaTek MT6357/58/59/66 PMIC codec (e.g. the mt6789-mt6366 machine on an MT6789 board). Its
// loudspeaker is an external AW87xxx smart-PA that only amplifies once the vendor audio HAL has run,
// and on this platform that HAL comes up at almost the same instant nano would play the early boot
// chime (device-measured: HAL init and the chime queue within ~0.5s of each other, ~14s in). So the
// pre-boot-complete direct-ALSA window is not usable here: the DAC plays into a powered-down amp
// (silent), and driving the amp from nano contends with the HAL's concurrent codec init and stalls
// the audio worker. Detect it so early-boot audio takes the AAudio path instead, which plays the
// moment the audio server is up (a little late, but audible), rather than the silent direct path.
bool nanoBootChimeIsMtkDevice() {
    static int cached = -1;
    if (cached >= 0) return cached == 1;
    cached = 0;
    FILE* f = fopen("/proc/asound/card0/id", "rb");
    if (f) {
        char id[64] = {};
        size_t n = fread(id, 1, sizeof(id) - 1, f);
        fclose(f);
        for (size_t i = 0; i < n; i++) if (id[i] >= 'A' && id[i] <= 'Z') id[i] = (char)(id[i] + 32);
        if (strstr(id, "mt6366") || strstr(id, "mt6358") || strstr(id, "mt6359") || strstr(id, "mt6357"))
            cached = 1;
    }
    return cached == 1;
}

// Unisoc/Spreadtrum SPRD codec (e.g. sprdphone-sc2730 on a UMS512/SharkL5 board). The
// playback path (VBC front-end -> internal DAC -> aw87xxx smart-PA) is pumped by the
// SPRD audio DSP (AGDSP) through the vendor HAL's proprietary DSP IPC, NOT by the ALSA
// PCM alone: a raw pcm_open(0,0) reaches state RUNNING but the DMA never drains (hw_ptr
// stalls, dmesg spins on "agdsp_access_enable") so the direct-ALSA path is silent, and
// there is no direct-DAC / non-DSP playback node to bypass it. Device-verified on an
// UMS512 board. Detect it so early-boot audio takes the AAudio path instead, which
// plays through the full vendor HAL (DSP + route + amp) once the audio server is up.
bool nanoBootChimeIsSprdDevice() {
    static int cached = -1;
    if (cached >= 0) return cached == 1;
    cached = 0;
    FILE* f = fopen("/proc/asound/card0/id", "rb");
    if (f) {
        char id[64] = {};
        size_t n = fread(id, 1, sizeof(id) - 1, f);
        fclose(f);
        for (size_t i = 0; i < n; i++) if (id[i] >= 'A' && id[i] <= 'Z') id[i] = (char)(id[i] + 32);
        if (strstr(id, "sprdphone") || strstr(id, "sc2730") || strstr(id, "sc9863") ||
            strstr(id, "ums512") || strstr(id, "sprd"))
            cached = 1;
    }
    return cached == 1;
}

bool nanoDirectAudioUsable() {
    if (gEng.failed.load()) return false;              // a prior direct open failed -> AAudio only
    if (nanoBootChimeIsMtkDevice()) return false;      // no usable pre-HAL window here -> AAudio path (plays once the audio server is up)
    if (nanoBootChimeIsSprdDevice()) return false;     // SPRD VBC/AGDSP: raw PCM DMA never drains without the vendor DSP protocol -> AAudio-late
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

// Keep the direct PCM (card0) held open while `on`, even with no voice playing, so interactive
// one-shots (PS3 XMB nav SFX) mix into an already-open substream instead of re-opening card0 - a
// re-open collides with the initialising audio HAL and permanently latches gEng.failed. The PS3
// theme requests this through the pre-boot-complete window and drops it (+ nanoDirectShutdown) at
// boot_completed, mirroring the DSi menu_ambiance hold. No-op-safe to call every frame.
void nanoDirectHoldOpen(bool on) {
    std::lock_guard<std::mutex> lk(gEng.m);
    gEng.holdOpen.store(on);
    if (on) ensureWorkerLocked();   // wake / respawn the parked worker so it opens + holds the PCM
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
