// See NanoHalChime.h. Drive the vendor audio HAL directly (HIDL, via libaudiohal) to sound the
// boot chime before audioserver's AudioPolicyManager finishes (it blocks ~11.7s on ActivityManager
// on this SPRD/Unisoc board). The HAL is a self-contained legacy audio HAL wrapped by
// android.hardware.audio.service (up ~7.3s); opening its primary output to the speaker and writing
// PCM triggers the AGDSP boot + aw87 smart-PA on the first write. We refcount-share the module with
// audioserver (which opens it ~20s later) and RELEASE well before then.

#include "NanoHalChime.h"

#include <media/audiohal/DevicesFactoryHalInterface.h>
#include <media/audiohal/DeviceHalInterface.h>
#include <media/audiohal/StreamHalInterface.h>

#include <system/audio.h>
#include <tinyalsa/asoundlib.h>
#include <cutils/properties.h>
#include <utils/String8.h>
#include <utils/StrongPointer.h>
#include <utils/SystemClock.h>
#include <android/log.h>

#include <cstdio>
#include <cstring>
#include <cstdint>
#include <cmath>
#include <vector>
#include <unistd.h>

#define NHC_TAG "GammaOSNano"
#define NHC_I(...) __android_log_print(ANDROID_LOG_INFO, NHC_TAG, __VA_ARGS__)
#define NHC_W(...) __android_log_print(ANDROID_LOG_WARN, NHC_TAG, __VA_ARGS__)

namespace android {
namespace {

// Minimal WAV -> interleaved S16 loader (same RIFF-walk rules as NanoBootChime: the data chunk
// is not necessarily adjacent to fmt).
struct HcWav { std::vector<int16_t> pcm; uint32_t rate = 0; uint16_t ch = 0; bool ok = false; };
inline uint32_t rd32(const uint8_t* p){ return (uint32_t)p[0] | ((uint32_t)p[1]<<8) | ((uint32_t)p[2]<<16) | ((uint32_t)p[3]<<24); }
inline uint16_t rd16(const uint8_t* p){ return (uint16_t)((uint16_t)p[0] | ((uint16_t)p[1]<<8)); }
HcWav loadWav(const std::string& path){
    HcWav w; FILE* f = fopen(path.c_str(), "rb"); if (!f) return w;
    uint8_t hdr[12];
    if (fread(hdr,1,12,f)!=12 || memcmp(hdr,"RIFF",4)!=0 || memcmp(hdr+8,"WAVE",4)!=0) { fclose(f); return w; }
    bool haveFmt=false; uint16_t bits=0; long dataOff=0; uint32_t dataLen=0;
    for (;;) {
        uint8_t ch[8]; if (fread(ch,1,8,f)!=8) break; uint32_t sz = rd32(ch+4);
        if (memcmp(ch,"fmt ",4)==0) {
            uint8_t fmt[16]; uint32_t n = sz<16?sz:16; if (fread(fmt,1,n,f)!=n) break;
            uint16_t tag=rd16(fmt); w.ch=rd16(fmt+2); w.rate=rd32(fmt+4); bits=rd16(fmt+14); haveFmt=(tag==1);
            if (sz>n) fseek(f,(long)(sz-n),SEEK_CUR);
        } else if (memcmp(ch,"data",4)==0) { dataOff=ftell(f); dataLen=sz; break; }
        else fseek(f,(long)((sz+1)&~1u),SEEK_CUR);
    }
    if (haveFmt && dataOff>0 && dataLen>0 && bits==16 && w.ch>0 && w.rate>0) {
        fseek(f,dataOff,SEEK_SET); w.pcm.resize(dataLen/2);
        size_t g=fread(w.pcm.data(),1,dataLen,f); fclose(f); w.pcm.resize(g/2); w.ok=!w.pcm.empty();
        return w;
    }
    fclose(f); return w;
}

}  // namespace

// Force the codec DAC playback volume up. On this SPRD/Unisoc board the vendor HAL, this early,
// fails to grab the "DAC Gain DAC Playback Volume" mixer handle (it logs
// "Open [DAC Gain DAC Playback Volume] Failed"), so the codec DAC stays at 0 (min = muted) even
// though every other node in the route (aw87 amp, speaker mute, HP mixer/pin, FE->BE switches,
// HP gain) is set correctly. audioserver's later open succeeds and sets it, which is why post-boot
// playback is audible. We set it ourselves once the route is up. Range is 0..2; 2 = full.
static void nanoHalChimeUnmuteDac() {
    struct mixer* mx = mixer_open(0);
    if (!mx) { NHC_W("halchime: mixer_open(0) failed, cannot unmute DAC"); return; }
    struct mixer_ctl* c = mixer_get_ctl_by_name(mx, "DAC Gain DAC Playback Volume");
    if (c) {
        int n = mixer_ctl_get_num_values(c);
        int maxv = mixer_ctl_get_range_max(c);
        if (maxv <= 0 || maxv > 8) maxv = 2;   // this codec's DAC gain is 0..2; guard odd ranges
        for (int i = 0; i < n; i++) mixer_ctl_set_value(c, i, maxv);
        NHC_I("halchime: set DAC Gain DAC Playback Volume=%d (%d vals) to un-mute codec DAC", maxv, n);
    } else {
        NHC_W("halchime: 'DAC Gain DAC Playback Volume' control not found");
    }
    mixer_close(mx);
}

// The deterministic "DSP VBC Profile Select" value for this board's MUSIC->SPEAKER path
// (Music/Handsfree/Playback). Confirmed two ways: observed from the vendor HAL during normal Android
// playback, AND documented in the RG Rotate mainline-Linux bring-up as the exact value a plain static
// mixer write uses to make the speaker play. It is a fixed AGDSP address (0x394A0000), not a per-boot
// handle, so it is safe to write directly once the profile blob has been uploaded to the AGDSP.
static constexpr int kDspVbcSpeakerProfile = 961216512;

bool nanoHalChimePlay(const std::string& wavPath, float gain) {
    HcWav w = loadWav(wavPath);
    if (!w.ok) { NHC_W("halchime: unusable wav %s", wavPath.c_str()); return false; }

    // out->setVolume() is a no-op for the SPRD primary output stream (the HAL only honours
    // per-stream volume on its HIFI path), so the boot-chime loudness has to be baked into the
    // samples. Scale by the volume-model gain, saturating on overflow.
    if (gain < 0.0f) gain = 0.0f; else if (gain > 1.0f) gain = 1.0f;
    if (gain < 0.999f) {
        for (int16_t& s : w.pcm) {
            int v = (int)lrintf((float)s * gain);
            if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
            s = (int16_t)v;
        }
    }

    // The SPRD HAL's adev_open reads persist.vendor.audio.boot_completed; when it is 1 the HAL
    // treats boot as done and connects the real output device immediately instead of deferring to
    // its internal bootup timer. It is normally already 1; set it defensively for a fresh device so
    // the very-early open still routes to the loudspeaker.
    if (!property_get_bool("persist.vendor.audio.boot_completed", false))
        property_set("persist.vendor.audio.boot_completed", "1");

    // The audio HAL service registers android.hardware.audio@7.0::IDevicesFactory ~7.3s. create()
    // returns null until then; poll briefly.
    sp<DevicesFactoryHalInterface> factory;
    for (int i = 0; i < 400; i++) {
        factory = DevicesFactoryHalInterface::create();
        if (factory != nullptr) break;
        usleep(25 * 1000);
    }
    if (factory == nullptr) { NHC_W("halchime: audio HAL factory unavailable -> AAudio fallback"); return false; }

    sp<DeviceHalInterface> dev;
    if (factory->openDevice("primary", &dev) != OK || dev == nullptr) {
        NHC_W("halchime: openDevice(primary) failed -> AAudio fallback"); return false;
    }
    dev->initCheck();

    struct audio_config cfg;
    memset(&cfg, 0, sizeof(cfg));
    cfg.sample_rate  = w.rate;
    cfg.format       = AUDIO_FORMAT_PCM_16_BIT;
    cfg.channel_mask = (w.ch >= 2) ? AUDIO_CHANNEL_OUT_STEREO : AUDIO_CHANNEL_OUT_MONO;

    sp<StreamOutHalInterface> out;
    status_t st = dev->openOutputStream((audio_io_handle_t)77, AUDIO_DEVICE_OUT_SPEAKER,
                                        AUDIO_OUTPUT_FLAG_PRIMARY, &cfg, "", &out);
    if (st != OK || out == nullptr) {
        NHC_W("halchime: openOutputStream(speaker) failed st=%d -> AAudio fallback", st);
        dev.clear(); return false;
    }
    out->setVolume(1.0f, 1.0f);   // no-op on the SPRD primary path; gain is baked into the samples above

    // SPRD gate (the real reason the early chime was silent): the AGDSP will not route audio to the
    // codec until a DSP "VBC profile" is uploaded to AGDSP memory (the HAL's async load, kicked by
    // openDevice above - the kernel finishes vbc_turning_profile_loading ~1s after open on this board)
    // AND its handle is written to "DSP VBC Profile Select". First give the HAL a short window to make
    // that SELECT itself on a route change (self-verifying: the handle becomes a large value). Its
    // set_audioparam is not reliably triggered this early though, so if the handle has not appeared,
    // write the deterministic speaker-profile handle DIRECTLY - by then the upload is done, so the
    // profile is live. AUDIO_DEVICE_OUT_SPEAKER = 0x2.
    struct mixer* mx = mixer_open(0);
    struct mixer_ctl* sel = mx ? mixer_get_ctl_by_name(mx, "DSP VBC Profile Select") : nullptr;
    if (!sel) {
        NHC_W("halchime: 'DSP VBC Profile Select' ctl missing -> release for AAudio fallback");
        if (mx) mixer_close(mx);
        out->standby(); out.clear(); dev.clear();
        return false;
    }
    bool halSelected = false;
    const int64_t p0 = uptimeMillis();
    while (uptimeMillis() - p0 < 2500) {
        out->setParameters(String8("routing=0"));
        out->setParameters(String8("routing=2"));
        if (mixer_ctl_get_value(sel, 0) > 1000) { halSelected = true; break; }
        usleep(150 * 1000);
    }
    if (halSelected) {
        NHC_I("halchime: HAL selected DSP VBC profile (handle=%d)", mixer_ctl_get_value(sel, 0));
    } else {
        mixer_ctl_set_value(sel, 0, kDspVbcSpeakerProfile);
        out->setParameters(String8("routing=2"));   // re-assert the speaker route with the profile live
        NHC_I("halchime: direct-set DSP VBC Profile Select=%d (readback=%d)",
              kDspVbcSpeakerProfile, mixer_ctl_get_value(sel, 0));
    }
    mixer_close(mx);

    NHC_I("halchime: vendor HAL open (%uHz x%u), DSP profile active -> writing coldboot", w.rate, w.ch);

    const uint8_t* p = (const uint8_t*)w.pcm.data();
    const size_t total = w.pcm.size() * 2;
    size_t off = 0;
    bool dacUnmuted = false;
    const int64_t startMs = uptimeMillis();
    while (off < total) {
        // Hard deadline: never hold the primary output past ~15s so we release well before
        // audioserver opens it (~20s). A stalled HAL write must not create contention.
        if (uptimeMillis() - startMs > 15000) { NHC_W("halchime: play deadline hit, releasing"); break; }
        size_t n = total - off; if (n > 16384) n = 16384;
        size_t wr = 0;
        status_t r = out->write(p + off, n, &wr);
        if (r != OK) { NHC_W("halchime: write err %d at %zu/%zu", r, off, total); break; }
        if (wr == 0) { usleep(2 * 1000); continue; }
        off += wr;
        if (!dacUnmuted) {
            // The first write ran the HAL's start_output_stream, so the codec route now exists.
            // Un-mute the DAC that the HAL could not set this early.
            nanoHalChimeUnmuteDac();
            dacUnmuted = true;
        }
    }

    NHC_I("halchime: chime done (%zu/%zu bytes), releasing HAL stream", off, total);
    out->standby();
    out.clear();          // StreamOutHalHidl dtor closes the HAL stream
    dev.clear();          // drops the adev refcount so audioserver opens cleanly later
    return true;
}

}  // namespace android
