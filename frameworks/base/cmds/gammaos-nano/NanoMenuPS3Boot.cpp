/*
 * GammaOS Nano - PS3 XMB cold-boot intro.
 *
 * A 1:1 port of the web app's cold-boot sequence (index.html boot timeline):
 * the wave/gradient revealing from black, the white PS3 logo + footer plate
 * appearing with an L->R wipe and fading out, the photosensitivity-epilepsy
 * warning, and finally the hand-off to the live XMB where the category icons /
 * item list / clock fade and pop in.
 *
 * Design notes:
 *  - The whole sequence is a pure function of a monotonic boot clock
 *    (mPs3BootElapsedMs), accumulated from the CLAMPED per-frame dt - never
 *    mEffectTime (which wraps at 500s) nor wall-clock.
 *  - The steady wave/gradient pipeline (ps3bg) is left untouched except for one
 *    safe hook: ps3bg::setBootWaveBrightness() scales uFade so the wave emerges
 *    from black (1.0 = steady, restored when the intro ends). The gradient/scene
 *    reveal is done here with a fading black overlay so no shader path changes.
 *  - The warning backdrop blur reuses the proven captureGlass/drawFrostedGlass
 *    chain, captured once and held (frozen) for the whole warning.
 */

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for the logo wipe scissor
#include "NanoI18n.h"      // trDyn() resource-file translations
#include "NanoMenuShaders.h"   // FONT_CHAR_H (DSi font-scale idiom)
#include "NanoBootChime.h"     // RG DS direct-ALSA boot chime (bypasses AudioFlinger)
#include "NanoHalChime.h"      // SPRD boot chime: drive the vendor audio HAL directly (bypasses audioserver)

#include <math.h>
#include <string.h>
#include <unistd.h>       // access() for the DSi boot audio path fallback
#include <string>
#include <vector>
#include <mutex>          // serialize the AAudio boot-SFX player so the enter theme is never dropped

#include <GLES2/gl2.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>   // android::uptimeMillis() for the ambiance hand-off

namespace android {

// ---- timeline constants (ms), from the web boot timeline ----
static const double BOOT_WAVE_IN_A   = 800.0;    // wave brightness ramp start
static const double BOOT_WAVE_IN_B   = 2600.0;   // wave brightness full
static const double BOOT_SCENE_A     = 800.0;    // scene reveal (black wash recedes)
static const double BOOT_SCENE_B     = 4500.0;
static const double BOOT_LOGO_IN_A   = 2200.0;   // logo wipe in
static const double BOOT_LOGO_IN_B   = 4000.0;
static const double BOOT_LOGO_HOLD_B = 5800.0;   // logo full hold end
static const double BOOT_LOGO_OUT_B  = 6800.0;   // logo gone
static const double BOOT_WARN_BLUR_A = 7115.0;   // warning backdrop blur in
static const double BOOT_WARN_BLUR_B = 7415.0;
static const double BOOT_WARN_IN     = 7665.0;   // warning text hard cut in
static const double BOOT_WARN_OUT    = 12530.0;  // warning text hard cut out
static const double BOOT_WARN_BLUROUT_A = 12650.0;
static const double BOOT_WARN_BLUROUT_B = 12870.0;
static const double BOOT_UI_IN_MS    = 15900.0;  // un-suppress the XMB UI
static const double BOOT_LABEL_A     = 15900.0;  // category label reveal
static const double BOOT_LABEL_B     = 16350.0;
static const double BOOT_ICON_A      = 16000.0;  // category icon / item / clock reveal
static const double BOOT_ICON_B      = 16900.0;
static const double BOOT_SEQ_END_MS  = 16900.0;  // hand-off complete

static const float EDGE_W = 1.6f;                // logo wipe soft-edge lead

// ---- DSi 1:1 boot timeline (frames @60fps, straight from web config.js/boot.js) ----
// The DSi theme drives its OWN frame clock (mDsiBootFrame) through these landmarks; the
// PS3 BOOT_* millisecond constants above are untouched and still used for !mNdsTheme.
enum { DSI_BOOT = 0, DSI_WAIT = 1, DSI_ENTERING = 2, DSI_DONE = 3 };
static const double DSI_BLACK_END   = 22.0;   // 0..22   pure black
static const double DSI_WHITE_END   = 92.0;   // 22..92  pure white hold
static const double DSI_LOGO_START  = 92.0;   // 92+     logo + notice animate in
static const double DSI_CHIME_SEC   = 1.45;   // boot chime start (seconds) - just BEFORE the logo
                                              // appears (DSI_LOGO_START 92f = 1.53s), so the chime
                                              // leads into the GammaOS logo build (user request).
static const double DSI_TOUCH_PROMPT= 180.0;  // frame the touch-to-continue prompt starts pulsing (input accepted)
static const double DSI_PULSE_PERIOD= 60.0;   // 30f-in / 30f-out linear triangle
static const double DSI_HS_FADE     = 23.0;   // static-content fade-in frames from whiteEnd
static const double DSI_ENTER_END   = 61.0;   // entering-phase length (frames)

static const char* kWarnTitle = "IMPORTANT NOTICE";
static const char* kWarnBody =
    "GAMMAOS IS PROVIDED \"AS IS\", WITHOUT WARRANTY OR SUPPORT OF ANY KIND. "
    "THE DEVELOPERS ASSUME NO LIABILITY FOR HARDWARE DAMAGE, INOPERABLE DEVICES "
    "OR DATA LOSS DURING INSTALLATION OR USE. BY USING THIS SOFTWARE, YOU ACCEPT "
    "THESE RISKS.";

static inline float bootRamp(double e, double a, double b) {
    if (b <= a) return e >= b ? 1.0f : 0.0f;
    float t = (float)((e - a) / (b - a));
    if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
    return t;
}
static inline float smooth01(float u) { return u * u * (3.0f - 2.0f * u); }

// ---------------------------------------------------------------------------
// lifecycle
// ---------------------------------------------------------------------------
static std::string dsiAudioPath(const char* file);   // defined below (used by the pre-warm)
static float dsiEarlyAudioGain(float master);        // defined below (used by the pre-warm volume)
static bool dsiBootCompleted();                      // defined below (gates the pre-warm vs HAL chime)
void NanoMenu::ps3BootReset(bool freshSetup) {
    mPs3BootElapsedMs = 0.0;
    mPs3BootActive = true;
    mPs3BootWizardAfter = freshSetup;
    mPs3BootLabelReveal = 0.0f;
    mPs3BootIconReveal = 0.0f;
    ps3bg::setBootWaveBrightness(0.0f);
    // DSi 1:1 boot state machine (mNdsTheme only). Re-arm the carousel cascade so the
    // hand-off replays after a bootreplay, and pre-warm the SFX stream so the chime at
    // 1.94s is not late behind the first AAudio open (~200ms).
    mDsiBootPhase = DSI_BOOT; mDsiBootFrame = 0.0; mDsiEnterStart = 0.0;
    mDsiChimePlayed = false; mDsiWantProceed = false; mDsiEnterAudioStartMs = 0;
    mDsiBootSeed = (float)((android::uptimeMillis() % 100000) * 0.001);   // vary the mini-logo scatter per boot
    mChimeReady = false; mChimePlayReq = false; mChimeStarted = false;
    mPs3ColdSoundPlayed = false;   // PS3 XMB cold-boot sound fires once per (re)boot / bootreplay
    if (mNdsTheme) {
        mNdsIntroStart = 0; mSfxPlayer.init();
        // On a SoC whose loudspeaker only sounds through the vendor HAL/DSP (SPRD/Unisoc), the
        // cold-boot chime is played by driving the vendor HAL directly at its mark (ps3BootUpdate).
        // Pre-warming the AAudio stream here would hold mSfxOpening and BLOCK that HAL thread from
        // spawning, so skip the pre-warm in exactly that case (matches the HAL-branch condition).
        if (nanoDirectAudioUsable() || dsiBootCompleted()) dsiPrewarmChime();
    }
    else if (!nanoDirectAudioUsable()) {
        // PS3 theme on a SoC where the direct-ALSA path is dead (e.g. SPRD/Unisoc, whose
        // loudspeaker only sounds through the vendor HAL/DSP). The boot chime must come through
        // AAudio, and in force-SF mode nano's render loop is CPU/SF-starved during the busy boot,
        // so the render-clock 800ms chime mark lands ~12s late. DECOUPLE the chime from the render
        // clock: play it from a dedicated thread the instant audioserver is up, so it lands ASAP
        // (~the audioserver floor) regardless of how slowly the intro renders. Suppress the
        // render-clock fire below (mPs3ColdSoundPlayed=true).
        mPs3ColdSoundPlayed = true;
        if (!mSfxOpening.exchange(true)) {
            std::string path = dsiAudioPath("coldboot_stereo.wav");
            std::thread([this, path]() {
                float gain = dsiEarlyAudioGain(0.8f);
                // FASTEST: drive the vendor audio HAL directly (bypasses audioserver's ~11.7s
                // AudioPolicyManager, which blocks on ActivityManager) -> chime at the HAL floor
                // ~8s instead of ~20s. Falls back to the AAudio-late path if the HAL is unreachable
                // (that path waits for audioserver = the old ~20s behaviour).
                if (!nanoHalChimePlay(path, gain)) {
                    mSfxPlayer.init();
                    for (int i = 0; i < 400; i++) {        // wait for audioserver (comes up ~7.8s+)
                        char s[PROPERTY_VALUE_MAX] = {};
                        property_get("init.svc.audioserver", s, "");
                        if (strcmp(s, "running") == 0) break;
                        usleep(25 * 1000);
                    }
                    if (mSfxPlayer.open(path)) { mSfxPlayer.setVolume(gain); mSfxPlayer.play(); }
                }
                mSfxOpening.store(false);
            }).detach();
        }
    }
}

// Open the boot chime stream ASAP on a detached thread (no play yet). Kicking this at boot
// start lets the cold audio-service/HAL bring-up overlap the first ~1.9s of the boot
// animation so the chime is not late behind a fresh AAudio open when its mark is reached.
void NanoMenu::dsiPrewarmChime() {
    if (mSfxOpening.exchange(true)) return;
    std::string path = dsiAudioPath("boot_chime.wav");
    std::thread([this, path]() {
        mSfxPlayer.init();
        // A prior boot's chime may have left the shared SFX stream STARTED; ensureStream reuses
        // a same-format stream without clearing that, so open() would feed the ring into an
        // already-running stream and the chime would sound NOW (at pre-warm / boot start) instead
        // of at its mark. Pause around the open so the stream is opened + ring-filled but SILENT
        // until ps3BootUpdate calls play() at the chime mark (fixes the too-early boot chime).
        mSfxPlayer.pause();
        bool ok = mSfxPlayer.open(path);
        if (ok) { mSfxPlayer.setVolume(dsiEarlyAudioGain(0.8f)); mSfxPlayer.pause(); }   // volume-model scaled
        mChimeReady.store(ok);
        mSfxOpening.store(false);
    }).detach();
}

void NanoMenu::ps3BootSkip() {
    // DSi 1:1 boot: a touch/button only acts during the WAIT phase, where it triggers
    // proceed() (touch sound -> enter transition). Ignored during the black/white/logo
    // build-up, exactly like boot.js proceed()'s `phase !== 'wait'` guard.
    if (mNdsTheme || mMinimaTheme) { if (mDsiBootPhase == DSI_WAIT) mDsiWantProceed = true; return; }
    // PS3 boot: jump to the end of the sequence; the next update() snaps everything steady.
    mPs3BootElapsedMs = BOOT_SEQ_END_MS;
}

// Resolve a boot-audio wav (dev override first, then the bundled install path).
static std::string dsiAudioPath(const char* file) {
    char p[256];
    snprintf(p, sizeof(p), "/data/system/nano_xmb/audio/%s", file);
    if (access(p, R_OK) == 0) return std::string(p);
    snprintf(p, sizeof(p), "/system/etc/nano_xmb/audio/%s", file);
    return std::string(p);
}

// True once the framework has finished booting; after this the audio server is up and the normal
// AAudio path is used, so the direct-PCM path releases the card.
static bool dsiBootCompleted() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.boot_completed", v, "");
    return v[0] == '1';
}

// Final early-audio gain = per-voice web master * the user's saved system music volume, mapped through
// the SAME curve audioserver applies to STREAM_MUSIC, so the direct-PCM loudness equals what the user
// hears from media at that setting (the old linear map made the boot audio disproportionately loud).
// Curve = AOSP default speaker MUSIC table (1,-58)(20,-40)(60,-17)(100,0) dB; DbToAmpl = 10^(dB/20).
// persist.gammaos.nano.volume is the route-resolved index (PhoneWindowManager writes it, persist props
// load in first-stage init). index 0 -> mute (skip); no saved volume (first boot) -> full master.
static float dsiEarlyAudioGain(float master) {
    char vmax[PROPERTY_VALUE_MAX] = {}, vcur[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.volmax", vmax, "");
    property_get("persist.gammaos.nano.volume", vcur, "");
    int mx = vmax[0] ? atoi(vmax) : 0;
    int cv = vcur[0] ? atoi(vcur) : -1;
    if (cv == 0) return 0.0f;                          // muted -> silent
    if (mx <= 0 || cv < 0) return master;              // first boot, no saved volume -> full
    if (cv > mx) cv = mx;
    int volIdx = (100 * cv) / mx;                      // rescale to the curve's 0..100 domain
    float db;
    if      (volIdx <= 1)  db = -58.0f;
    else if (volIdx <= 20) db = -58.0f + (volIdx - 1)  * (18.0f / 19.0f);
    else if (volIdx <= 60) db = -40.0f + (volIdx - 20) * (23.0f / 40.0f);
    else                   db = -17.0f + (volIdx - 60) * (17.0f / 40.0f);
    return master * expf(db * 0.115129f);              // 10^(dB/20)
}

// Play a DSi boot one-shot (chime at 1.94s / touch-continue / menu-enter fanfare) on the
// dedicated SFX player so it never contends with the carousel music. Web master gain 0.8.
// Serialize the shared AAudio boot-SFX player (mSfxPlayer). A request that arrives while an open is
// in flight is stashed here and played immediately after, so the touch tick and the menu-entry theme
// (fired together on proceed) both sound instead of the second being dropped.
static std::mutex  gBootSfxMx;
static std::string gBootSfxPending;
static bool        gBootSfxPendingSet = false;

// ---- DSi interactive SFX (nav_blip / app_launch / settings_nav / settings_back / settings_enter) ----
// The web app (audio.js) plays a distinct sound for each menu event; nano was silent in DS mode (the
// PS3 cursor sound ps3NavSound is gated to the PS3 theme). Five FILE-STATIC low-latency retriggerable
// players (one per clip) load their wav once on a bg thread and then trigger lock-free from the render
// thread. File-static (not NanoMenu members) so the class layout is unchanged (no full recompile).
// NdsSfxId now lives at namespace scope in NanoMenu.h so other TUs can name the ids too.
static NanoSfxPlayer  gNdsSfx[NDS_SFX_COUNT];
static std::atomic<bool> gNdsSfxOpening[NDS_SFX_COUNT] = {};   // per-clip open-in-flight guard

void NanoMenu::dsiBootSound(DsiSfx which) {
    const char* file;
    if (mMinimaTheme) {
        // Minima plays ONLY its own boot jingle at the chime; it does NOT play the DSi
        // touch_continue / menu_enter theme on dismiss (that DSi menu-entry theme is the
        // "NDS ambient music" the user heard briefly when leaving the warning screen).
        if (which == DsiSfx::Chime) file = "minima_boot.wav";
        else return;   // silent dismiss straight into the Minima home
    } else {
        file = which == DsiSfx::Chime ? "boot_chime.wav"
             : which == DsiSfx::Touch ? "touch_continue.wav"
             : "menu_enter.wav";
    }
    // Pre-boot-complete: the audio server is not up, so go straight to the ALSA PCM (queued,
    // sequential). Any device with a card 0 playback node; falls back to AAudio if the open fails.
    if (nanoDirectAudioUsable() && !dsiBootCompleted()) {
        nanoDirectPlayOneShot(dsiAudioPath(file), dsiEarlyAudioGain(0.8f));
        return;
    }
    // Open + play on a DETACHED thread. AAudioStreamBuilder_openStream() blocks until the
    // audio service is up, so opening on the render thread stalls rendering; on a cold boot
    // that stall exceeds the 8s render watchdog and SIGABRTs nano into a crash loop. The
    // player object is shared, so only one open runs at a time. Touch + Enter are triggered
    // in the SAME frame on proceed; the old code dropped the second (the menu-entry theme)
    // whenever an open was in flight, so the intermediate transition music went missing on
    // this path. Instead, stash a request that arrives mid-open and play it right after, so
    // the enter theme always sounds. The cold-boot direct path already queues both voices.
    {
        std::lock_guard<std::mutex> lk(gBootSfxMx);
        if (mSfxOpening.load()) { gBootSfxPending = dsiAudioPath(file); gBootSfxPendingSet = true; return; }
        mSfxOpening.store(true);   // claim the player under the lock (paired with the thread's reset)
    }
    std::string path = dsiAudioPath(file);
    std::thread([this, path]() {
        std::string p = path;
        for (;;) {
            mSfxPlayer.init();
            if (mSfxPlayer.open(p)) { mSfxPlayer.setVolume(dsiEarlyAudioGain(0.8f)); mSfxPlayer.play(); }   // volume-model scaled
            std::lock_guard<std::mutex> lk(gBootSfxMx);
            if (gBootSfxPendingSet) { p = gBootSfxPending; gBootSfxPendingSet = false; continue; }  // enter after touch
            mSfxOpening.store(false);   // reset under the lock so a concurrent request re-claims cleanly
            return;
        }
    }).detach();
}

// Trigger one DSi interactive SFX (NDS_SFX_* id). Lazy-loads the clip once on a bg thread (AAudio /
// decode never on the render thread), then triggers are lock-free. Only in the DSi theme (the PS3
// theme uses ps3NavSound). Post-boot only in practice (nav happens after the audio server is up).
// Shared early-audio hook for the interactive menu effects (DSi ndsSfxPlay + PS3 ps3NavSound). Before
// boot-complete the RK3568 audio server (AudioPolicyManager) is still ~23s into its init, so AAudio is
// blocked; play the effect on the direct-ALSA one-shot path instead, where the worker mixes it over any
// early BGM bed. Lives here because dsiBootCompleted / dsiEarlyAudioGain / dsiAudioPath are file-local.
// Returns true if it was queued to the direct mixer (caller done); false -> the caller uses AAudio.
bool NanoMenu::earlySfxOneShot(const char* wavName, float master) {
    if (!(nanoDirectAudioUsable() && !dsiBootCompleted())) return false;
    nanoDirectPlayOneShot(dsiAudioPath(wavName), dsiEarlyAudioGain(master));
    return true;
}

// User setting (persist.gammaos.nano.nav_sounds, default on): when off, silence the interactive
// navigation / UI sound effects (cursor, ok, back, drill, launch, error) across ALL themes. Read
// live so the toggle applies immediately. The boot jingle (nanoDirectChimePlay) is a separate path
// and stays audible. Gating the three theme SFX dispatchers covers every nav/UI call site.
static bool navSoundsOn() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.nav_sounds", v, "1");
    return !(v[0] == '0' || v[0] == 'f' || v[0] == 'F');
}

void NanoMenu::ndsSfxPlay(int which) {
    if (!mNdsTheme) return;
    if (!navSoundsOn()) return;
    if (which < 0 || which >= NDS_SFX_COUNT) return;
    static const char* kFiles[NDS_SFX_COUNT] = {
        "nav_blip.wav", "app_launch.wav", "settings_nav.wav", "settings_back.wav", "settings_enter.wav" };
    // Pre-boot-complete (RK3568 audio server still initialising): play on the direct-ALSA mixer so the
    // nav / enter / back / launch effects are audible in the early menu instead of silent. Post-boot
    // uses the pre-loaded low-latency AAudio player.
    if (earlySfxOneShot(kFiles[which], 0.4f)) return;
    if (gNdsSfx[which].loaded()) { gNdsSfx[which].trigger(); return; }
    if (gNdsSfxOpening[which].exchange(true)) return;             // one decode in flight per clip
    std::string path = dsiAudioPath(kFiles[which]);
    std::thread([which, path]() {
        // master 0.4 = half the web level (user: the DSi SFX were too loud, drop to 50%). The BGM
        // ambiance is unchanged.
        if (gNdsSfx[which].load(path, 0.4f)) gNdsSfx[which].trigger();
        gNdsSfxOpening[which].store(false);
    }).detach();
}

// ---- Authentic PS3 XMB nav effects (firmware system_plugin, /work/ps3/firmware_audio/system) --------
// SE02 cursor move / SE03 normal OK / SE04 back / SE05 category OK / SE08 option / SE09 error. Mirrors
// the DSi gNdsSfx model: FILE-STATIC low-latency players, one per clip, decoded once on a bg thread then
// triggered lock-free. Pre-boot-complete they route to the direct-ALSA mixer (earlySfxOneShot) so PS3
// XMB nav is audible in the early menu too; post-boot they use the pre-loaded AAudio players.
// Ps3SfxId (PS3_SFX_*) lives at namespace scope in NanoMenu.h so both this file-static array and the
// nav call sites in NanoMenuPS3Menu.cpp can name it.
static NanoSfxPlayer     gPs3Sfx[PS3_SFX_COUNT];
static std::atomic<bool> gPs3SfxOpening[PS3_SFX_COUNT] = {};

void NanoMenu::ps3Sfx(int which) {
    if (mNdsTheme || mMinimaTheme) return;              // PS3 XMB theme only (DSi=ndsSfxPlay, Minima=minimaSfx)
    if (!navSoundsOn()) return;
    if (which < 0 || which >= PS3_SFX_COUNT) return;
    static const char* kFiles[PS3_SFX_COUNT] = {
        "SE02_Cursor.wav", "SE03_Normal_OK.wav", "SE04_Back.wav",
        "SE05_Category_OK.wav", "SE08_Option.wav", "SE09_Error.wav" };
    if (earlySfxOneShot(kFiles[which], 0.8f)) return;   // pre-boot-complete: direct mixer
    if (gPs3Sfx[which].loaded()) { gPs3Sfx[which].trigger(); return; }
    if (gPs3SfxOpening[which].exchange(true)) return;    // one decode in flight per clip
    std::string path = dsiAudioPath(kFiles[which]);
    std::thread([which, path]() {
        if (gPs3Sfx[which].load(path, 0.8f)) gPs3Sfx[which].trigger();
        gPs3SfxOpening[which].store(false);
    }).detach();
}

// ---- Minima interactive SFX (soft/premium nav/select/back/drill/error blips) --------------------
// Same file-static low-latency model as gNdsSfx / gPs3Sfx. Only in the Minima theme. The clips are
// composed for Minima (NextUI itself is silent) and installed alongside the other sound sets in the
// nano_xmb audio dir; they are triggered per-frame by state-diff from minimaSfxTick (NanoMenuMinima.cpp).
static NanoSfxPlayer     gMinSfx[MIN_SFX_COUNT];
static std::atomic<bool> gMinSfxOpening[MIN_SFX_COUNT] = {};

void NanoMenu::minimaSfx(int which) {
    if (!mMinimaTheme) return;                          // Minima theme only
    if (!navSoundsOn()) return;
    if (which < 0 || which >= MIN_SFX_COUNT) return;
    static const char* kFiles[MIN_SFX_COUNT] = {
        "minima_cursor.wav", "minima_ok.wav", "minima_back.wav",
        "minima_drill.wav", "minima_error.wav" };
    if (earlySfxOneShot(kFiles[which], 0.6f)) return;   // pre-boot-complete: direct mixer
    if (gMinSfx[which].loaded()) { gMinSfx[which].trigger(); return; }
    if (gMinSfxOpening[which].exchange(true)) return;   // one decode in flight per clip
    std::string path = dsiAudioPath(kFiles[which]);
    std::thread([which, path]() {
        if (gMinSfx[which].load(path, 0.6f)) gMinSfx[which].trigger();
        gMinSfxOpening[which].store(false);
    }).detach();
}

// Per-frame DSi SFX driver: detect nav / drill / back / launch by DIFFING this frame's menu state
// against the previous frame, so exactly one sound fires per event regardless of whether the change
// came from the D-pad or from touch (no per-call-site wiring, no double-fire). Called once per frame
// from renderNdsCarousel. Suppressed inside modals (they own their own feel) and during boot.
void NanoMenu::ndsSfxTick() {
    if (!mNdsTheme) return;
    static bool    sInit = false, sRoot = true, sModal = false;
    static int     sDepth = 0, sSel = 0, sModalSel = 0;
    static int64_t sFade = 0;
    // launch: fire once when the launch fade-out begins (a card/app is being launched).
    if (mLaunchFadeStart != 0 && sFade == 0) ndsSfxPlay(NDS_SFX_LAUNCH);
    sFade = mLaunchFadeStart;

    const bool root  = mNdsAtRoot;
    const int  depth = (int)mPs3Stack.size();
    const int  sel   = mNdsAtRoot ? mPs3CatIdx
                     : (mPs3Stack.empty() ? mPs3ItemIdx : mPs3Stack.back().sel);
    // A modal (chooser / picker / option menu / dialog) owns input; track its own cursor so moving
    // through its options still chimes (settings_nav) and opening/closing it fires enter/back, 1:1
    // with the DSi. Pick the active modal's selection index.
    const bool modal = ndsInModal();
    const int  modalSel = mPs3TzActive   ? mTzSelected
                        : mPs3LangActive  ? mLangSelected
                        : (mPs3DlgActive || mPs3DlgClosing) ? mPs3DlgSel
                        : mPs3OptActive   ? (mPs3OptSubOpen ? mPs3OptSubSel : mPs3OptSel)
                        : 0;

    if (!sInit) { sInit = true; sRoot = root; sDepth = depth; sSel = sel; sModal = modal; sModalSel = modalSel; return; }
    if (mPs3BootActive || mLaunchFadeStart != 0) {   // no chime during boot or a launch fade
        sRoot = root; sDepth = depth; sSel = sel; sModal = modal; sModalSel = modalSel; return;
    }

    if (modal || sModal) {
        if      (modal && !sModal)                ndsSfxPlay(NDS_SFX_SET_ENTER);   // opened a chooser/picker/dialog
        else if (!modal && sModal)                ndsSfxPlay(NDS_SFX_SET_BACK);    // closed it
        else if (modal && modalSel != sModalSel)  ndsSfxPlay(NDS_SFX_SET_NAV);     // moved within it
    } else {
        const int curLevel  = root  ? 0 : depth + 1;    // 0 = categories root, 1 = a category, 2+ = a submenu
        const int prevLevel = sRoot ? 0 : sDepth + 1;
        if      (curLevel > prevLevel) ndsSfxPlay(NDS_SFX_SET_ENTER);                    // drilled in
        else if (curLevel < prevLevel) ndsSfxPlay(NDS_SFX_SET_BACK);                     // popped up
        else if (sel != sSel)          ndsSfxPlay(ndsCurLevelIsList() ? NDS_SFX_SET_NAV  // settings-list move
                                                                      : NDS_SFX_NAV);    // carousel move
    }
    sRoot = root; sDepth = depth; sSel = sel; sModal = modal; sModalSel = modalSel;
}

// Per-frame carousel background ambiance: loop menu_ambiance.wav while the DSi home is up
// (started only after the boot enter fanfare finishes so it is not clipped), stopped
// otherwise. Looped by seeking back to 0 when the track ends. Independent player instance.
void NanoMenu::ndsAmbianceTick(bool wantOnHome) {
    const bool direct = nanoDirectAudioUsable() && !dsiBootCompleted();

    // ---- Phase A: pre-boot-complete -> DIRECT PCM loop (audio server not up yet) ----
    if (direct) {
        if (!wantOnHome) { if (mDirectAmbiancePlaying) { nanoDirectStopLoop(); mDirectAmbiancePlaying = false; } return; }
        if (mDsiEnterAudioStartMs > 0 &&
            (int64_t)android::uptimeMillis() - mDsiEnterAudioStartMs < 2580) return;   // hold for the enter fanfare
        if (!mDirectAmbiancePlaying) {
            nanoDirectStartLoop(dsiAudioPath("menu_ambiance.wav"), dsiEarlyAudioGain(0.56f));   // web 0.8*0.7
            mDirectAmbiancePlaying = true;
        } else {
            nanoDirectSetGain(dsiEarlyAudioGain(0.56f));   // track a live volume change during boot
        }
        return;
    }

    // ---- Handoff: booted (or direct unavailable) while the direct bed was up -> release for AAudio ----
    if (mDirectAmbiancePlaying) {
        nanoDirectStopLoop();
        nanoDirectShutdown();                 // bounded wait: the PCM is closed before AAudio opens (no EBUSY)
        mDirectAmbiancePlaying = false;
        mDirectHandedOff = true;              // card0 handed to the HAL; the safety-net tick need not repeat it
        // fall through: mAmbiancePlaying is still false, so the AAudio block opens fresh.
    }

    // ---- Phase B: AAudio path (all devices post-boot, or where direct is unavailable) ----
    if (mAmbianceOpening) return;   // a bg open is in flight: never touch the player concurrently
    if (!wantOnHome) {
        if (mAmbiancePlaying) { mAmbiancePlayer.stop(); mAmbiancePlaying = false; }
        return;
    }
    if (!mAmbiancePlaying) {
        if (mDsiEnterAudioStartMs > 0 &&
            (int64_t)android::uptimeMillis() - mDsiEnterAudioStartMs < 2580) return;
        mAmbianceOpening.store(true);
        std::string path = dsiAudioPath("menu_ambiance.wav");
        std::thread([this, path]() {
            mAmbiancePlayer.init();
            if (mAmbiancePlayer.open(path)) {
                mAmbiancePlayer.setVolume(0.56f);   // web master 0.8 * voice 0.7 (was 0.7, a 1:1 miss)
                mAmbiancePlayer.play();
                mAmbiancePlaying = true;
            }
            mAmbianceOpening.store(false);
        }).detach();
        return;
    }
    if (mAmbiancePlayer.ended()) { mAmbiancePlayer.seek(0.0); mAmbiancePlayer.play(); }  // loop
}

// PS3 XMB early-boot audio hold. The DSi keeps card0 (the direct-ALSA PCM) held open through the
// pre-boot-complete window via its menu_ambiance loop, so its nav SFX mix into an already-open
// substream. The PS3 theme has no early BGM, so it never held card0: the worker parked after the
// boot chime / coldboot, and the first post-intro nav SFX re-opened card0 exactly as the RK3568
// audio HAL was grabbing it (EBUSY), permanently latching gEng.failed and dropping all PS3 early
// SFX onto the still-blocked AAudio path -> silence until boot_completed. Fix: hold card0 open with
// SILENCE through the pre-boot-complete window (nav one-shots mix in), then hand it to the HAL at
// boot_completed so post-boot audio is unaffected. DSi is handled by ndsAmbianceTick; this is the
// PS3 (!mNdsTheme) equivalent. Called once per frame from render().
void NanoMenu::ps3EarlyAudioTick() {
    if (mNdsTheme) {
        // A LIVE PS3->DSi theme switch (the Home Theme row flips mNdsTheme in-memory without a
        // restart) during the hold window would leak our hold; release it so ndsAmbianceTick
        // cleanly owns the direct engine from here.
        if (mPs3DirectHolding) { nanoDirectHoldOpen(false); mPs3DirectHolding = false; }
        return;                                              // DSi uses ndsAmbianceTick's own hold
    }
    const bool direct = nanoDirectAudioUsable() && !dsiBootCompleted();
    if (direct) {
        if (!mPs3DirectHolding) { nanoDirectHoldOpen(true); mPs3DirectHolding = true; }
        return;
    }
    // Booted (or direct unavailable) while we were holding -> release the hold + the card for the
    // audio HAL, exactly once. nanoDirectShutdown does a bounded wait so card0 is closed before
    // AAudio opens (no EBUSY), mirroring the DSi handoff in ndsAmbianceTick.
    if (mPs3DirectHolding) {
        nanoDirectHoldOpen(false);
        nanoDirectShutdown();
        mPs3DirectHolding = false;
        mDirectHandedOff = true;              // card0 handed to the HAL; the safety-net tick need not repeat it
    }
}

// Guaranteed direct-PCM -> audio-HAL handoff, called every frame from the main run loop
// (NOT from render(), which skips the audio ticks while the first-boot setup wizard is up).
// The home ambiance tick (ndsAmbianceTick) and the PS3 hold tick (ps3EarlyAudioTick) each
// release card0 with a bounded wait (nanoDirectShutdown) at boot-complete, but only when
// their own bed was up, and neither runs during the setup wizard. So on a first boot the
// direct engine could keep owning card0 past boot_completed, and every later open by the
// audio HAL / AudioFlinger would EBUSY -> all system audio dies after setup. Close the
// engine here exactly once, after the home ticks have had their chance to hand off cleanly.
void NanoMenu::nanoDirectHandoffTick() {
    if (mDirectHandedOff || !dsiBootCompleted()) return;
    // If a direct bed is still up, let its own tick do the smooth handoff (and set the flag);
    // only step in for the case where nothing on the home ever ran (the setup wizard).
    if (mDirectAmbiancePlaying || mPs3DirectHolding) return;
    nanoDirectShutdown();                     // bounded wait: card0 is free before the HAL opens it
    mDirectHandedOff = true;
}

// Test hook (nav-hook token "bootreplay"): re-run the whole cold-boot intro from
// t=0 so the sequence can be verified 1:1 against the web without a real reboot.
// The logo/footer plates are freed once the first boot completes, so force a
// reload; the warning backdrop blur is captured live each frame and needs no reset.
void NanoMenu::ps3BootReplay() {
    mPs3BootPlatesLoaded = false;
    mPs3BootLogoTex = 0;
    mPs3BootFooterTex = 0;
    ps3BootReset(false);
}

// Advance the boot clock and recompute the cross-module reveal values + the
// wave-brightness hook. Returns true while the XMB UI must stay suppressed.
bool NanoMenu::ps3BootUpdate(float dtSeconds) {
    if (!mPs3BootActive) return false;
    if (dtSeconds < 0.0f) dtSeconds = 0.0f;
    if (dtSeconds > 0.1f) dtSeconds = 0.1f;

    // ---- DSi 1:1 boot state machine (mNdsTheme). Own 60fps frame clock, wait-for-touch;
    // the PS3 millisecond timeline below is skipped entirely for the DSi theme. ----
    if (mNdsTheme || mMinimaTheme) {
        mDsiBootFrame += (double)dtSeconds * 60.0;   // frames @60fps == web boot.js this.frame
        double f = mDsiBootFrame;
        // Minima reuses this exact frame-clock + phase machine (so the hand-off to its menu is
        // identical), but its own black-field intro (renderMinimaBootOverlay) + jingle.
        const char* bootChimeWav = mMinimaTheme ? "minima_boot.wav" : "boot_chime.wav";
        ps3bg::setBootWaveBrightness(0.0f);          // the opaque field covers the wave
        mDisplayDirty = true;                        // keep the loop live through the WAIT hold (pulse + input)
        if (mDsiBootPhase == DSI_BOOT) {
            // Chime: the stream was pre-opened at boot start (dsiPrewarmChime). At the mark,
            // request play; then play() as soon as the stream is ready (usually immediately -
            // it plays exactly on the mark; if the audio service was still coming up it plays
            // the instant it is ready, rather than blocking the render thread on open).
            if (!mDsiChimePlayed && f / 60.0 >= DSI_CHIME_SEC) { mChimePlayReq = true; mDsiChimePlayed = true; }
            if (mChimePlayReq && !mChimeStarted) {
                if (nanoDirectAudioUsable() && !dsiBootCompleted()) {
                    // The audio server does not come up until ~13-37s (its AudioPolicyManager ctor
                    // blocks on ActivityManager), so AAudio cannot sound at this 1.45s mark. Play the
                    // chime straight to the ALSA PCM - it lands on the mark, honours the saved volume
                    // (dsiEarlyAudioGain, silent when muted), and never blocks the render thread. Any
                    // device with a card 0 playback node; falls back to AAudio below if the open fails.
                    if (!mDirectChimeInFlight.exchange(true))   // guard a bootreplay re-fire mid-play
                        nanoDirectChimePlay(dsiAudioPath(bootChimeWav), 0, 0, dsiEarlyAudioGain(0.8f),
                                            &mDirectChimeInFlight);
                    mChimeStarted = true;
                } else if (!nanoDirectAudioUsable() && !dsiBootCompleted()) {
                    // SoC where the direct-ALSA path is dead (e.g. SPRD/Unisoc, whose loudspeaker only
                    // sounds through the vendor HAL/DSP). Same as the PS3 cold-boot sound: drive the
                    // vendor audio HAL directly from a dedicated thread (chime at the HAL floor ~8s,
                    // not the ~20s audioserver floor), falling back to the AAudio-late path inside the
                    // thread if the HAL is unreachable. Never blocks the render loop.
                    if (!mSfxOpening.exchange(true)) {
                        std::string path = dsiAudioPath(bootChimeWav);
                        float gain = dsiEarlyAudioGain(0.8f);
                        std::thread([this, path, gain]() {
                            if (!nanoHalChimePlay(path, gain)) {
                                mSfxPlayer.init();
                                for (int i = 0; i < 400; i++) {        // wait for audioserver (~7.8s+)
                                    char s[PROPERTY_VALUE_MAX] = {};
                                    property_get("init.svc.audioserver", s, "");
                                    if (strcmp(s, "running") == 0) break;
                                    usleep(25 * 1000);
                                }
                                if (mSfxPlayer.open(path)) { mSfxPlayer.setVolume(gain); mSfxPlayer.play(); }
                            }
                            mSfxOpening.store(false);
                        }).detach();
                    }
                    mChimeStarted = true;
                } else if (mChimeReady.load()) { mSfxPlayer.play(); mChimeStarted = true; }
                else if (!mSfxOpening.load()) { dsiBootSound(DsiSfx::Chime); mChimeStarted = true; }  // pre-warm failed: async fallback
            }
            if (f >= DSI_TOUCH_PROMPT) mDsiBootPhase = DSI_WAIT;   // prompt + input accepted
        } else if (mDsiBootPhase == DSI_WAIT) {
            if (mDsiWantProceed) {                                  // proceed() on touch/press
                mDsiWantProceed = false;
                dsiBootSound(DsiSfx::Touch);
                mDsiBootPhase = DSI_ENTERING;
                mDsiEnterStart = f;
                dsiBootSound(DsiSfx::Enter);
                mDsiEnterAudioStartMs = (int64_t)android::uptimeMillis();
            }
        } else if (mDsiBootPhase == DSI_ENTERING) {
            if (f - mDsiEnterStart > DSI_ENTER_END) {               // enter cover done -> menu
                mDsiBootPhase = DSI_DONE;
                mPs3BootLabelReveal = 1.0f; mPs3BootIconReveal = 1.0f;
                ps3bg::setBootWaveBrightness(1.0f);
                mPs3BootActive = false;   // next frame arms the carousel intro cascade
                if (mPs3BootLogoTex)   { glDeleteTextures(1, &mPs3BootLogoTex);   mPs3BootLogoTex = 0; }
                if (mPs3BootFooterTex) { glDeleteTextures(1, &mPs3BootFooterTex); mPs3BootFooterTex = 0; }
                return false;
            }
        }
        return true;   // renderNdsBootOverlay owns the frame until the hand-off above
    }

    mPs3BootElapsedMs += (double)dtSeconds * 1000.0;
    double e = mPs3BootElapsedMs;

    // PS3 XMB cold-boot sound: play coldboot_stereo.wav (10s) once as the fade-in begins
    // (BOOT_SCENE_A = 800ms, the black wash receding / wave brightening). Direct-PCM pre-boot
    // (the audio server is not up), AAudio fallback otherwise. Fire-once (reset in ps3BootReset).
    if (!mPs3ColdSoundPlayed && e >= BOOT_SCENE_A) {
        mPs3ColdSoundPlayed = true;
        if (nanoDirectAudioUsable() && !dsiBootCompleted()) {
            nanoDirectPlayOneShot(dsiAudioPath("coldboot_stereo.wav"), dsiEarlyAudioGain(0.8f));
        } else if (!mSfxOpening.exchange(true)) {          // AAudio fallback (plays reliably once the audio server is up)
            std::string path = dsiAudioPath("coldboot_stereo.wav");
            std::thread([this, path]() {
                mSfxPlayer.init();
                // On SoCs where the direct-ALSA path is disabled (e.g. SPRD/Unisoc, whose
                // loudspeaker only sounds through the vendor audio HAL/DSP) this AAudio
                // fallback is the ONLY boot chime path. AAudioStreamBuilder_openStream fails
                // fast if the audio server is still mid-init, and coldboot_stereo.wav is raw
                // PCM (no format-change reopen), so opening before the server is up silently
                // DROPS the chime. Wait for audioserver to be running, then open+play once so
                // the chime reliably sounds at the earliest audible instant.
                for (int i = 0; i < 400; i++) {            // up to ~10s for audioserver to come up
                    char s[PROPERTY_VALUE_MAX] = {};
                    property_get("init.svc.audioserver", s, "");
                    if (strcmp(s, "running") == 0) break;
                    usleep(25 * 1000);
                }
                if (mSfxPlayer.open(path)) { mSfxPlayer.setVolume(dsiEarlyAudioGain(0.8f)); mSfxPlayer.play(); }   // volume-model scaled, matching the direct path (not a flat 0.8)
                mSfxOpening.store(false);
            }).detach();
        }
    }

    // Fresh-setup boots end the intro right after the epilepsy warning fades out
    // and hand straight to the setup wizard - the XMB category/item icon reveal is
    // skipped so the wizard is never preceded by a flash of the live menu.
    double endMs = mPs3BootWizardAfter ? BOOT_WARN_BLUROUT_B : BOOT_SEQ_END_MS;
    if (e >= endMs) {
        // Hand-off complete: snap everything to the steady state.
        mPs3BootLabelReveal = 1.0f;
        mPs3BootIconReveal = 1.0f;
        ps3bg::setBootWaveBrightness(1.0f);
        mPs3BootActive = false;
        // The cold-boot logo/footer plates are only drawn by renderPs3BootOverlay
        // during this intro and never again this process lifetime. Free them now
        // (700x350 RGBA x2 ~= 1.9 MB of otherwise-mlocked GPU memory). We are on
        // the render thread (renderPs3Xmb -> ps3BootUpdate) so the GL context is
        // current. Leave mPs3BootPlatesLoaded true so they are never reloaded.
        if (mPs3BootLogoTex)   { glDeleteTextures(1, &mPs3BootLogoTex);   mPs3BootLogoTex = 0; }
        if (mPs3BootFooterTex) { glDeleteTextures(1, &mPs3BootFooterTex); mPs3BootFooterTex = 0; }
        return false;
    }

    ps3bg::setBootWaveBrightness(smooth01(bootRamp(e, BOOT_WAVE_IN_A, BOOT_WAVE_IN_B)));
    mPs3BootLabelReveal = smooth01(bootRamp(e, BOOT_LABEL_A, BOOT_LABEL_B));
    mPs3BootIconReveal  = smooth01(bootRamp(e, BOOT_ICON_A, BOOT_ICON_B));

    return e < BOOT_UI_IN_MS;   // suppress the XMB UI until the staged reveal
}

// (loadPs3BootPlate is implemented in NanoMenuPS3Icons.cpp where decodeRGBA lives)

// ---------------------------------------------------------------------------
// overlay render (drawn on top of the composited wave/gradient, primary pass)
// ---------------------------------------------------------------------------
// primary=true draws the full boot (fade + logo/wordmark + warning blur + warning text). primary=false
// is the SECONDARY (bottom) panel on a dual-screen device: it gets the SAME black fade-in and the same
// frosted-wave blur as the primary (user: the secondary must not show the unfiltered wave), but skips
// the logo and the warning text, which are the primary panel's focal content.
void NanoMenu::renderPs3BootOverlay(bool primary) {
    // Make sure the responsive layout globals are current (the menu, which
    // normally computes them, is suppressed during boot).
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    if (!mPs3BootPlatesLoaded) {
        mPs3BootLogoTex   = loadPs3BootPlate("logo_white.png");
        mPs3BootFooterTex = loadPs3BootPlate("footer_white.png");
        mPs3BootPlatesLoaded = true;
    }

    double e = mPs3BootElapsedMs;
    const float fx = ps3::gFrameX, fy = ps3::gFrameY, fw = ps3::gFrameW, fh = ps3::gFrameH;
    // Visual-down drop shadow: rotate the device-down vector by sDrmRotMat so the
    // shadow falls toward the bottom of the panel on any orientation (matches the
    // menu's ps3ShadowOffset; on the 180-degree Brick this is {0,-s}).
    float ss = ps3::devS(1.5f);
    float so[2] = { sDrmRotMat[2] * ss, sDrmRotMat[3] * ss };

    // ---- 1. scene reveal: black wash receding (gradient emerges from black) ----
    float sceneReveal = smooth01(bootRamp(e, BOOT_SCENE_A, BOOT_SCENE_B));
    float blackA = 1.0f - sceneReveal;
    if (blackA > 0.001f)
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, blackA);

    // ---- 2. logo + footer white plates (2200..6800) - PRIMARY panel only ----
    if (primary && e >= BOOT_LOGO_IN_A && e < BOOT_LOGO_OUT_B) {
        float mainA;
        if (e < BOOT_LOGO_IN_B)        { float u = bootRamp(e, BOOT_LOGO_IN_A, BOOT_LOGO_IN_B); mainA = u * u; }
        else if (e < BOOT_LOGO_HOLD_B) mainA = 1.0f;
        else                           mainA = 1.0f - smooth01(bootRamp(e, BOOT_LOGO_HOLD_B, BOOT_LOGO_OUT_B));

        // logo: native 700x350, centred at (0.734fw, 0.532fh) - the same position the PS3 logo
        // occupied - but 50% BIGGER than before (user request: dW 0.365*fw -> 0.5475*fw). The G ink
        // (plate x235..464) stays on screen at this width; only the plate's transparent margin
        // nominally overshoots the right edge.
        float dW = 0.5475f * fw, dH = dW * 0.5f;
        float lcx = fx + 0.734f * fw, lcy = fy + 0.532f * fh;
        float lx = lcx - dW * 0.5f, ly = lcy - dH * 0.5f;
        // sensible gap between the bigger G and the "GammaOS" wordmark (user request): the logo plate
        // lifts and the wordmark plate drops half the gap each, so the group stays centred at lcy.
        float wordGap = 0.030f * fh;
        float lyLogo = ly - wordGap * 0.5f, lyFoot = ly + wordGap * 0.5f;

        // L->R wipe during the in-phase only (settled full after). A scissor on a
        // full-height band clips the revealed left fraction (rotation-aware).
        bool wipe = (e < BOOT_LOGO_IN_B);
        bool scissorOn = false;
        if (wipe) {
            float revealU = bootRamp(e, BOOT_LOGO_IN_A, BOOT_LOGO_IN_B);
            float front = revealU * (1.0f + EDGE_W);
            if (front > 1.0f) front = 1.0f;
            // Composed rotation+flip mapping (see scissorLogicalRect). The old
            // rotation-only switch put the band on the wrong side of a flipped
            // panel, scissoring away the ENTIRE logo for the whole wipe so it
            // hard-popped at full alpha instead of fading in.
            scissorLogicalRect(lx, 0.0f, fmaxf(1.0f, dW * front),
                               (float)mHeight);
            scissorOn = true;
        }
        if (mPs3BootLogoTex) {
            drawIconTex(mPs3BootLogoTex, lx + so[0], lyLogo + so[1], dW, dH, 0.0f, 0.0f, 0.0f, 0.45f * mainA);
            drawIconTex(mPs3BootLogoTex, lx, lyLogo, dW, dH, 1.0f, 1.0f, 1.0f, mainA);
        }
        // The footer plate (the "GammaOS" wordmark) is a SAME-SIZE 700x350 overlay - its text is
        // positioned within its own canvas to sit under the mark - drawn a wordGap lower than the
        // logo plate (sensible margin, user request) and riding the same wipe.
        if (mPs3BootFooterTex) {
            drawIconTex(mPs3BootFooterTex, lx + so[0], lyFoot + so[1], dW, dH, 0.0f, 0.0f, 0.0f, 0.45f * mainA);
            drawIconTex(mPs3BootFooterTex, lx, lyFoot, dW, dH, 1.0f, 1.0f, 1.0f, mainA);
        }
        if (scissorOn) glDisable(GL_SCISSOR_TEST);
    }

    // ---- 3. epilepsy warning backdrop blur + text ----
    if (e >= BOOT_WARN_BLUR_A && e < BOOT_WARN_BLUROUT_B) {
        // Backdrop behind the warning text. When the WAVE is the wallpaper, blur its
        // work-texture (captureGlassFromWave reads ps3bg::workTex; the full-framebuffer
        // captureGlass path renders BLACK here because the freshly drawn scene is not
        // yet resolved to the FBO when glCopyTexSubImage2D runs during the DRM-direct
        // boot, while workTex is its own resolved FBO). For ANY OTHER wallpaper the
        // selected effect is already drawn behind us but has no resolved offscreen
        // texture to blur mid-boot, so dim it with a scrim instead - that shows the
        // user's chosen wallpaper (not the wave) and keeps the white text readable.
        float warnBlur = smooth01(bootRamp(e, BOOT_WARN_BLUR_A, BOOT_WARN_BLUR_B))
                       * (1.0f - smooth01(bootRamp(e, BOOT_WARN_BLUROUT_A, BOOT_WARN_BLUROUT_B)));
        if (warnBlur > 0.001f) {
            if (mCurrentEffect == 22 && captureGlassFromWave())
                drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                                 0.90f, 0.90f, 0.92f, 1.0f, warnBlur, /*waveSpace=*/true);
            else
                drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f * warnBlur);
        }
    }

    if (primary && e >= BOOT_WARN_IN && e < BOOT_WARN_OUT) {
        float sy = fh / 1080.0f;
        float titleS = ps3::fontScale(30.0f);
        float bodyS  = ps3::fontScale(24.0f);
        float pitch  = 34.0f * sy;
        float wrapW  = fw * (1280.0f / 1920.0f);

        // Localised title/body (static English literals are translation keys).
        const char* warnTitle = trDyn(kWarnTitle);
        const char* warnBody  = trDyn(kWarnBody);

        // word-wrap the body to wrapW
        std::vector<std::string> lines;
        std::string cur, word;
        auto commit = [&](bool last) {
            if (word.empty()) { if (last && !cur.empty()) lines.push_back(cur); return; }
            std::string trial = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && measureText(trial.c_str(), bodyS) > wrapW) { lines.push_back(cur); cur = word; }
            else cur = trial;
            word.clear();
            if (last && !cur.empty()) lines.push_back(cur);
        };
        for (const char* p = warnBody; ; ++p) {
            if (*p == ' ' || *p == '\0') { commit(*p == '\0'); if (*p == '\0') break; }
            else word.push_back(*p);
        }

        float titleW = measureText(warnTitle, titleS);
        float maxW = titleW;
        for (auto& l : lines) { float w = measureText(l.c_str(), bodyS); if (w > maxW) maxW = w; }
        float blockX = fx + (fw - maxW) * 0.5f;
        int totalLines = 1 + 1 + (int)lines.size();   // title + gap + body
        float totalH = totalLines * pitch;
        float baseY = fy + (fh - totalH) * 0.5f;

        // Fade the warning TEXT in and out (matching the backdrop). The stock PS3/
        // web boot hard-cuts the text, but that reads as an abrupt flash - especially
        // on the DRM-direct path where the backdrop is a plain scrim. The alpha is a
        // pipeline-agnostic ramp on the drawText colour, so it fades identically under
        // both the DRM and SurfaceFlinger back-ends.
        float warnA = smooth01(bootRamp(e, BOOT_WARN_IN, BOOT_WARN_IN + 400.0))
                    * (1.0f - smooth01(bootRamp(e, BOOT_WARN_OUT - 400.0, BOOT_WARN_OUT)));
        auto line = [&](const char* t, float scale, float yTop) {
            drawText(t, blockX + so[0], yTop + so[1], scale, 0.0f, 0.0f, 0.0f, 0.55f * warnA);
            drawText(t, blockX, yTop, scale, 1.0f, 1.0f, 1.0f, warnA);
        };
        float y = baseY;
        line(warnTitle, titleS, y); y += pitch * 2.0f;   // title + blank line
        for (auto& l : lines) { line(l.c_str(), bodyS, y); y += pitch; }
    }
}

// ---------------------------------------------------------------------------
// DSi-styled cold-boot overlay (persist.gammaos.nano.ndstheme). Replaces the PS3
// wave/logo intro: the DSi boots on a WHITE screen - black wash receding to the
// firmware #f6 white, the GammaOS logo wiping in dark-on-white, then the same
// IMPORTANT NOTICE liability text rendered DSi-flat (no drop shadow, the DSVec
// face). Reuses the shared boot clock (mPs3BootElapsedMs) + timeline so the
// hand-off to the carousel intro cascade is identical to the PS3 path. primary =
// the top panel (logo + notice); the bottom panel gets the white field only.
// Minima cold-boot intro: a pure-black field throughout (never the DSi white or the PS3 wave), a
// GammaOS wordmark in the theme accent that rises in, the photosensitivity IMPORTANT NOTICE, a
// pulsing press-to-continue prompt during the WAIT hold, then a black cover that fades up as it hands
// over. Driven by the SAME frame clock + phase machine as the DSi boot (ps3BootUpdate's shared
// branch), so the hand-off into the Minima menu is identical and no XMB is ever shown.
void NanoMenu::renderMinimaBootOverlay(bool primary) {
    // Minima reuses the DSi cold-boot animation verbatim, only with INVERTED colours (dark field,
    // light content - see the inv / ink* handling in renderNdsBootOverlay, gated on mMinimaTheme).
    // Boot audio stays Minima's own: dsiBootSound() plays only minima_boot.wav at the chime and is
    // silent on Touch/Enter, so none of the DSi transition audio is used.
    renderNdsBootOverlay(primary);
}

void NanoMenu::renderNdsBootOverlay(bool primary) {
    setUiBlend();
    ensureNdsAssets();
    if (!mPs3BootPlatesLoaded) {
        mPs3BootLogoTex   = loadPs3BootPlate("logo_white.png");
        mPs3BootFooterTex = loadPs3BootPlate("footer_white.png");
        mPs3BootPlatesLoaded = true;
    }
    if (!mDsiTriTex) mDsiTriTex = ndsLoadTex("hs_triangle");   // caution triangle (colour-preserving)
    const bool prevFont = mNdsFontPref; mNdsFontPref = true;      // DSVec faces
    const int  prevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // flat DSi text

    const double f = mDsiBootFrame;                              // 60fps frame clock (== web boot.js this.frame)
    float rw = (float)mWidth, rh = (float)mHeight;
    float scale = rh / 192.0f; if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    float offY = (rh - 192.0f * scale) * 0.5f;
    float cxp = rw * 0.5f;
    auto Y = [&](float dy){ return offY + dy * scale; };
    auto S = [&](float v){ return v * scale; };

    // Minima reuses this exact DSi boot animation with INVERTED colours (dark field, light
    // content) - the DSi path (inv=false) keeps the original values unchanged.
    const bool  inv    = mMinimaTheme;
    const float bgHold = inv ? 0.0f   : 1.0f;    // 22..92 hold field  (white -> black)
    const float bgHS   = inv ? 0.035f : 0.984f;  // health-notice field (near-white -> near-black)
    const float ink    = inv ? 0.92f  : 0.16f;   // logo / title line-art tint
    const float ink2   = inv ? 0.80f  : 0.255f;  // notice body text
    const float ink3   = inv ? 0.72f  : 0.353f;  // pulsing prompt
    const float coverC = inv ? 0.0f   : 1.0f;    // enter cross-fade cover (fades to menu colour)

    // (a) Background schedule (both panels), HARD cuts like the web: black (0..22),
    // hold (22..92), then the health-notice field. Opaque - hides the wave.
    if      (f < DSI_BLACK_END)  drawQuad(0.0f, 0.0f, rw, rh, 0.0f,   0.0f,   0.0f,   1.0f);
    else if (f < DSI_WHITE_END)  drawQuad(0.0f, 0.0f, rw, rh, bgHold, bgHold, bgHold, 1.0f);
    else                         drawQuad(0.0f, 0.0f, rw, rh, bgHS,   bgHS,   bgHS,   1.0f);

    // Static content fades in over 23 frames from whiteEnd (web `clamp((f-92)/23)`).
    float hsA = (float)((f - DSI_WHITE_END) / DSI_HS_FADE);
    if (hsA < 0.0f) hsA = 0.0f; if (hsA > 1.0f) hsA = 1.0f;

    // (b) TOP panel: the GammaOS logo BUILD-UP, an ORIGINAL GammaOS animation in the NDS boot
    // choreography STYLE (user: "be creative with the GammaOS logo and name, same layout and
    // style"): multiple rounded squares slide in from spread positions and fade out (the DSi
    // "sliding screens" motif), the GammaOS mark scales/locks in first with an ease-out-back
    // settle, and the "GammaOS" name fades in below it a beat later. Holds through WAIT; the
    // enter cover hides it on the way out. Dark line-art tint on the white field.
    if (primary && f >= DSI_LOGO_START) {
        double lf = f - DSI_LOGO_START;
        const float baseW = S(128.0f);
        const float logoCY = Y(86.0f);                          // settle pose (web LOGO_SCY ~86.7)
        auto drawLogoAt = [&](GLuint tex, float sc, float cyOff, float al){
            if (!tex || al <= 0.004f) return;
            float w2 = baseW * sc, h2 = w2 * (350.0f / 700.0f);
            drawIconTex(tex, cxp - w2 * 0.5f, logoCY + cyOff - h2 * 0.5f, w2, h2, ink, ink, ink, al);
        };
        // A miniature GammaOS logo (the same mPs3BootLogoTex plate as the final mark),
        // centred at (sx,sy) with target WIDTH sz. Replaces the old plain "screen" squares:
        // the converging elements are now small GammaOS logos that fly in and fade out as the
        // full mark locks in. Logo aspect is 2.0 (700x350 -> 0.5 height), matching drawLogoAt.
        auto drawMiniLogo = [&](float sx, float sy, float sz, float al, float cr, float cg, float cb){
            if (!mPs3BootLogoTex || al <= 0.004f) return;
            float w2 = sz, h2 = w2 * (350.0f / 700.0f);
            drawIconTex(mPs3BootLogoTex, sx - w2 * 0.5f, sy - h2 * 0.5f, w2, h2, cr, cg, cb, al);
        };
        // (1) miniature GammaOS logos converging on the mark and fading out. They fly in from
        // RANDOM angles/distances at STAGGERED random start times (not all at once), each a random
        // size and a random vivid colour (user request). Seeded per boot (mDsiBootSeed) so the
        // scatter differs every boot but is stable across frames. The window is 2x the original.
        auto rnd = [&](int i, float salt){ float s = sinf(((float)i + mDsiBootSeed) * 12.9898f + salt) * 43758.5453f; return s - floorf(s); };
        auto hue = [&](float h, float& r, float& g, float& b){   // hue 0..1 -> vivid rgb
            h *= 6.0f; float x = 1.0f - fabsf(fmodf(h, 2.0f) - 1.0f);
            if      (h < 1.0f) { r = 1.0f; g = x;    b = 0.0f; }
            else if (h < 2.0f) { r = x;    g = 1.0f; b = 0.0f; }
            else if (h < 3.0f) { r = 0.0f; g = 1.0f; b = x;    }
            else if (h < 4.0f) { r = 0.0f; g = x;    b = 1.0f; }
            else if (h < 5.0f) { r = x;    g = 0.0f; b = 1.0f; }
            else               { r = 1.0f; g = 0.0f; b = x;    }
        };
        const int NMINI = 16;
        const double MDUR = 54.0;                                // 2x the original 27f convergence window
        if (lf < MDUR) {
            for (int i = 0; i < NMINI; i++) {
                float startT = rnd(i, 1.7f) * (float)(MDUR * 0.6);           // staggered starts over ~60% of the window
                float span   = (float)(MDUR * 0.42) * (0.7f + 0.6f * rnd(i, 9.3f));   // each mini's own fly-in span
                float lt = (float)lf - startT;
                if (lt < 0.0f || lt > span) continue;
                float p = lt / span; if (p > 1.0f) p = 1.0f;
                float e = p * p * (3.0f - 2.0f * p);                          // smoothstep converge
                float ang  = rnd(i, 2.4f) * 6.2832f;                          // random angle
                float dist = 60.0f + rnd(i, 5.1f) * 95.0f;                    // random distance 60..155 DS
                float sx = cxp    + (1.0f - e) * S(cosf(ang) * dist);
                float sy = logoCY + (1.0f - e) * S(sinf(ang) * dist);
                float w  = 46.0f + rnd(i, 7.7f) * 72.0f;                      // random width 46..118 DS
                float sz = S(w) * (1.0f - 0.25f * e);                         // shrink only slightly so they stay big
                float a  = (1.0f - p) * 0.85f;                               // fade out as they arrive
                float cr, cg, cb; hue(rnd(i, 3.9f), cr, cg, cb);             // random vivid colour
                drawMiniLogo(sx, sy, sz, a, cr, cg, cb);
            }
        }
        // (2) the GammaOS mark: ease-out-back scale + fade, locking in first (lf 3..26)
        float gp = (float)(lf < 3.0 ? 0.0 : (lf > 26.0 ? 1.0 : (lf - 3.0) / 23.0));
        float sBk = 1.9f, ub = gp - 1.0f;
        float eob = ub * ub * ((sBk + 1.0f) * ub + sBk) + 1.0f;  // ease-out-back overshoot
        float gScale = (0.55f + 0.45f * eob) * 1.25f;            // central mark 25% bigger (user request)
        float gAlpha = (float)(lf < 3.0 ? 0.0 : (lf > 15.0 ? 1.0 : (lf - 3.0) / 12.0));
        if (mPs3BootLogoTex) drawLogoAt(mPs3BootLogoTex, gScale, 0.0f, gAlpha);
        // (3) the "GammaOS" name: fades in below the mark a beat later (lf 22..46). Extra margin
        // below the (now 25% bigger) mark so the wordmark is not crowded (user request).
        float wp = (float)(lf < 22.0 ? 0.0 : (lf > 46.0 ? 1.0 : (lf - 22.0) / 24.0));
        float we = wp * wp * (3.0f - 2.0f * wp);
        if (mPs3BootFooterTex) drawLogoAt(mPs3BootFooterTex, 1.0f, S(22.0f) + (1.0f - we) * S(4.0f), we);
    }

    // Single-screen only: the pulsing touch/continue prompt normally lives on the bottom
    // (health-notice) panel, which is never shown when nano renders to a single physical screen.
    // Draw it at the bottom of the TOP (logo) screen instead, so single-screen devices still see how
    // to proceed. On a dual-screen device the bottom panel below owns the prompt (so this is gated
    // out to avoid a duplicate). Applies to both DSi and Minima (Minima routes through here).
    if (primary && !hasSecondaryDisplay() && mDsiBootPhase >= DSI_WAIT && f >= DSI_TOUCH_PROMPT) {
        double pp = fmod(f - DSI_TOUCH_PROMPT, DSI_PULSE_PERIOD);
        float pulse = (float)(pp < 30.0 ? pp / 30.0 : (60.0 - pp) / 30.0);
        if (pulse > 0.004f) {
            const char* pr = trDyn("Press A to continue");
            float fs = S(9.0f) / (float)FONT_CHAR_H, tw = measureText(pr, fs);
            drawText(pr, cxp - tw * 0.5f, Y(182.0f), fs, ink3, ink3, ink3, pulse);
        }
    }

    // (c) BOTTOM panel: the DSi health-notice layout (the web puts the H&S on the bottom
    // touch screen) - caution triangle + title (block-centred), the GammaOS liability body
    // word-wrapped, and the pulsing touch-to-continue prompt. Web H&S baselines: title y28,
    // body from y54, prompt y176.8.
    if (!primary && f >= DSI_LOGO_START) {
        const char* title = trDyn(kWarnTitle);
        const char* body  = trDyn(kWarnBody);
        float titleFs = S(10.0f) / (float)FONT_CHAR_H;    // DSi cap-10 title
        float bodyFs  = S(8.0f)  / (float)FONT_CHAR_H;    // DSi cap-8 body
        // triangle + title on one block-centred row near the top of the panel.
        float triH = S(16.0f), triW = triH * (122.0f / 118.0f);
        float titleW = measureText(title, titleFs), gap = S(3.0f);
        float blockLeft = cxp - (triW + gap + titleW) * 0.5f;
        float rowTop = Y(20.0f);
        if (mDsiTriTex) drawIconTex(mDsiTriTex, blockLeft, rowTop, triW, triH, 1.0f, 1.0f, 1.0f, hsA);
        drawText(title, blockLeft + triW + gap, rowTop + (triH - titleFs * FONT_CHAR_H) * 0.5f,
                 titleFs, ink, ink, ink, hsA);

        // wrapped body, centred lines, filling the mid panel between the title and the prompt.
        float wrapW = S(216.0f);
        std::vector<std::string> lines; std::string cur, word;
        auto commit = [&](bool last){
            if (word.empty()) { if (last && !cur.empty()) lines.push_back(cur); return; }
            std::string t = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && measureText(t.c_str(), bodyFs) > wrapW) { lines.push_back(cur); cur = word; }
            else cur = t;
            word.clear();
            if (last && !cur.empty()) lines.push_back(cur);
        };
        for (const char* p = body; ; ++p) {
            if (*p == ' ' || *p == '\0') { commit(*p == '\0'); if (*p == '\0') break; }
            else word.push_back(*p);
        }
        // Fit the body block between DS y50 and y156 (above the y168 prompt): pick a pitch that
        // packs the wrapped lines into that band, capped at the web ~19px row step.
        int nl = (int)lines.size(); if (nl < 1) nl = 1;
        float bandTop = 50.0f, bandBot = 156.0f;
        float pitchDS = (bandBot - bandTop) / (float)nl; if (pitchDS > 18.0f) pitchDS = 18.0f;
        float y = Y(bandTop + (bandBot - bandTop - pitchDS * (nl - 1)) * 0.0f);   // top-anchored in the band
        for (auto& l : lines) { float tw = measureText(l.c_str(), bodyFs);
            drawText(l.c_str(), cxp - tw * 0.5f, y, bodyFs, ink2, ink2, ink2, hsA); y += S(pitchDS); }

        // pulsing prompt - only from frame 180 (WAIT+). 60-frame linear triangle, grey rgb(90,90,90).
        if (mDsiBootPhase >= DSI_WAIT && f >= DSI_TOUCH_PROMPT) {
            double p = fmod(f - DSI_TOUCH_PROMPT, DSI_PULSE_PERIOD);
            float pulse = (float)(p < 30.0 ? p / 30.0 : (60.0 - p) / 30.0) * hsA;
            if (pulse > 0.004f) {
                const char* pr = trDyn("Touch the screen to continue.");
                float fs = S(8.0f) / (float)FONT_CHAR_H, tw = measureText(pr, fs);
                drawText(pr, cxp - tw * 0.5f, Y(168.0f), fs, ink3, ink3, ink3, pulse);
            }
        }
    }

    // (d) Enter white cross-fade cover (both panels), over the boot content: rises 0->1 over
    // 30 frames, holds to the DSI_ENTER_END hand-off. Drawn on the DONE frame too so there is
    // no 1-frame flash of the bare notice before the carousel cascade takes over.
    if (mDsiBootPhase == DSI_ENTERING || mDsiBootPhase == DSI_DONE) {
        float t = (float)((mDsiBootFrame - mDsiEnterStart) / 30.0);
        float a = t < 1.0f ? t : 1.0f;
        drawQuad(0.0f, 0.0f, rw, rh, coverC, coverC, coverC, a);
    }
    mNdsFontPref = prevFont; mTextOutlineMode = prevOutline;
}

} // namespace android
