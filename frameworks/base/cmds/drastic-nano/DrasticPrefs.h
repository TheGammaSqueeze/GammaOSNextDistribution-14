/*
 * Copyright (C) 2026 GammaOS
 *
 * DrasticPrefs: read / write drastic's Android SharedPreferences XML
 * at /data/user/0/com.dsemu.drastic/shared_prefs/_Dra$t1c_Pref$_.xml.
 *
 * drastic stores ALL its user-visible settings in that XML. It re-reads
 * them only at startGame time, not mid-session, so anything the overlay
 * menu toggles that maps into applyConfig bits takes effect on the next
 * drastic-nano launch. Live-settable knobs (volume, shader, autosave
 * interval) go through DrasticRunner setters directly and do not need
 * to round-trip through the XML -- but we still write the change to XML
 * so the real drastic app picks it up later.
 *
 * We preserve unknown keys on write so drastic-app-private settings
 * (screen layout, per-ROM overrides, multi-player keymaps, etc.) are
 * not clobbered.
 */

#pragma once

#include <string>
#include <sys/types.h>

namespace android {
namespace drastic_prefs {

// Number of player slots drastic supports.
static constexpr int kNumPlayers = 3;
// Number of action indices per player. Drastic's real slot layout
// (reverse-engineered from the shared_prefs XML produced by the real
// app binding a known Xbox controller):
//   0..7:   face + shoulders + start/select (X, Y, B, A, R, L, Start, Select)
//   8..11:  reserved / unknown (never observed bound by the real app)
//   12..15: D-Pad (Up, Right, Down, Left)
//   16:     Screen Swap
//   17:     Fast Forward
//   18..19: reserved / unknown
//   20:     Menu
//   21..27: reserved / unknown
//   28:     Touch Cursor (toggles the virtual touch cursor; default R3)
//   29:     Save State (quick-save to slot 0; drastic-nano-local, unmapped by default)
//   30:     Load State (quick-load from slot 0; drastic-nano-local, unmapped by default)
// 29 and 30 are drastic-nano-local action slots (the real drastic app does
// not use them), handled entirely inside drastic-nano via DrasticRunner's
// saveStateSlot/loadStateSlot. They round-trip through the shared_prefs XML
// as _KeyMapConfigs_0_29 / _0_30 so a bound button persists across launches.
static constexpr int kNumActions = 31;

struct Prefs {
    // Video filter: basename of the .dfx file (no path, no extension).
    // Default "Linear". When empty or unrecognized we treat it as
    // "Linear".
    std::string currentFx = "2xPrescaleFast_LCD";   // shipped seed _CurrentFx

    // applyConfig-latched bits. Changing any of these requires
    // drastic to be relaunched.
    bool hires3d       = true;
    bool threaded3d    = true;
    bool disableEdge   = true;
    bool soundEnabled  = true;
    // GPU 3D rasterizer (DrasticGpu3d.cpp): live toggles, nano-side only (no drastic config bit).
    bool gpu3d         = false;      // render the hi-res 3D layer on the GPU instead of libdrastic's CPU rasterizer
    bool gpu3dSs       = false;      // GPU path renders at 4x (1024x768) and resolves to the 2x layer

    // Runtime-adjustable knobs.
    int  volume        = 10;         // _Volume 0..10, pinned at max (system volume is the control)
    int  audioLatency  = 1;          // _AudioLatency 0..4 (shipped seed)
    bool micEnabled    = true;       // _MicEnabled (shipped seed)
    int  micLevel      = 1;          // _MicLevel 0..2

    // Frameskip: type 0 = fixed value, 1 = auto. Value is used when
    // type=0.
    int  frameskipType  = 0;
    int  frameskipValue = 0;
    bool frameskipSafe  = false;

    // Analog stick behavior.
    bool  analogTouch    = false;    // LS drives DS stylus
    bool  analogTriggers = false;    // Analog trigger mapping
    int   analogStickMode = 2;       // shipped seed
    float analogDeadzone = 0.50f;    // drastic-nano default (the app shipped 0.15)

    // Delay the secondary (top) display's page flip by one refresh
    // so its logical content matches what the primary (bottom)
    // panel is showing at the same wall-clock moment. Needed on
    // dual-DSI setups that cannot be phase-locked in hardware
    // (RK3568 VOP2). Adds 1 frame of input lag to the top panel.
    // Off by default because the extra buffering interacts badly
    // with heavier 3D titles; user can enable from the in-game
    // Video overlay if they prefer the phase-locked look.
    bool frameSync = false;

    // Low Latency Mode. When on, the DRM ring presents the previous frame
    // (age 1) instead of the frame-before-that (age 2), shaving ~one refresh
    // (~16.7 ms) of input-to-photon latency at the cost of less pipeline
    // slack (the AHB fence wait can block under heavy GPU load). Inter-screen
    // sync is handled by the kernel (rockchip,sync-vp-mask on the RG DS), so
    // this trades purely latency for throughput headroom and supersedes Frame
    // Sync (which is forced off while Low Latency is on). Off by default;
    // user-togglable live from the in-game Video overlay.
    bool lowLatency = false;

    // DS firmware userdata. drastic packs these into a single int it
    // hands to setFirmwareUserdata so the emulated DS boots with the
    // user's chosen language / theme colour / birthday / nickname
    // instead of the English factory default. The real drastic app
    // stores them as these SharedPreferences keys; defaults match
    // drastic's own f0/h defaults (lang English, colour 1, birthday
    // June 6, nick "Dr Drastic").
    int firmwareLanguage  = 1;   // _FirmwareLanguage (0..7)
    int firmwareColor     = 0;   // _FirmwareColor (shipped seed)
    int firmwareBdayMonth = 6;   // _FirmwareBdayMonth (1..12)
    int firmwareBdayDay   = 6;   // _FirmwareBdayDay (1..31)
    std::string firmwareNick = "Dr Drastic";  // _FirmwareNick

    // Per-player action keymap. Each entry is an Android keycode, or
    // -1 for "unmapped". We read all 3 players but only use player 0
    // in drastic-nano (single-player device).
    int keymap[kNumPlayers][kNumActions];

    Prefs() {
        for (int p = 0; p < kNumPlayers; p++) {
            for (int a = 0; a < kNumActions; a++) {
                keymap[p][a] = -1;
            }
        }
        // Player 0 defaults: the binding set the shipped DraStic seed config
        // carried (_KeyMapConfigs_0_*), so a fresh device plays out of the box.
        static const int kSeedKeymap0[kNumActions] = {
            99, 100, 97, 96, 103, 102, 108, 109,      // X Y B A R L Start Select
            -1, -1, -1, -1,
            19, 22, 20, 21,                           // D-Pad Up Right Down Left
            104, 105,                                 // Screen Swap (L2), Fast Forward (R2)
            -1, -1,
            4,                                        // Menu (Back)
            -1, -1, -1, -1, -1, -1, -1,
            107,                                      // Touch Cursor (R3)
            -1, -1                                    // Save State, Load State
        };
        for (int a = 0; a < kNumActions; a++) keymap[0][a] = kSeedKeymap0[a];
    }
};

// ---- Property-backed configuration -------------------------------------
//
// drastic-nano's configuration lives entirely in persist.gammaos.drastic_nano.*
// properties. The compiled-in Prefs defaults are the values the shipped
// DraStic seed config carried, so an unset property means "the default".
//   applyProps   : overlay every SET property onto *p (unset keys untouched,
//                  so a caller may pre-fill device-specific defaults first).
//   writeProps   : persist every field of p that differs from prev (each
//                  persist write is a synchronous store, so only the delta is
//                  written); prev == nullptr writes everything.
//   propsSeeded  : the one-time import marker from the legacy DraStic XML.
void applyProps(Prefs* p);
int  writeProps(const Prefs& p, const Prefs* prev);
bool propsSeeded();
void markPropsSeeded();

// Read the XML at xmlPath into out. Returns true if the file was
// parsed (at least opened and read); unknown or missing keys keep
// their struct-level defaults. When the file does not exist we
// return false and out is left with defaults.
bool readPrefs(const std::string& xmlPath, Prefs* out);

// Write prefs back to xmlPath atomically. On success, chown/chmod the
// result so the real drastic app can still read it (mode 0660, owned
// by appUid/appGid). Preserves any keys we don't know about by
// round-tripping the original file content -- only keys we care about
// are rewritten. When the source file does not exist we skeleton one.
bool writePrefs(const std::string& xmlPath, const Prefs& p,
                uid_t appUid, gid_t appGid);

// Pack the applyConfig-latched bits from p into drastic's config
// bitmask. Caller still forces bit 31 (_SoundEnabled) explicitly if
// it needs to override p.soundEnabled.
long applyConfigBitsFrom(const Prefs& p);

// Returns true iff the diff between a and b includes any applyConfig-
// latched or frameskip/audio-latency bits (i.e., a change the user
// can only pick up after a relaunch).
bool requiresRelaunch(const Prefs& before, const Prefs& after);

// Action labels for the UI. Indexed by action index 0..kNumActions-1.
// Entries beyond the documented 14 map to friendlier best-guess
// labels; where drastic's internal semantics are unknown we render
// "Extra N".
const char* actionName(int actionIdx);

// Map an evdev EV_KEY code to the Android keycode drastic's keymap
// encodes. Returns 0 for unknown codes.
int evdevToAndroidKeycode(int evdevKey);
// BACK, volume and POWER are system keys and can never be bound to a control.
bool isReservedKeycode(int androidKeycode);
bool isReservedEvdev(int evdevKey);

// Map an Android keycode back to an evdev EV_KEY code (reverse of
// evdevToAndroidKeycode). Returns 0 for unmapped.
int androidKeycodeToEvdev(int androidKeycode);

// Human label for an Android keycode (best effort). Used only in the
// Controls UI. Returns a static-lifetime string like "Button A",
// "D-Pad Up", or "KC 123" for unknown values.
const char* androidKeycodeLabel(int androidKeycode);

} // namespace drastic_prefs
} // namespace android
