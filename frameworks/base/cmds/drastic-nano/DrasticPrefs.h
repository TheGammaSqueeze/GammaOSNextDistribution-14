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
// Number of action indices per player. 0..11 are DS buttons + dpad,
// 12 is fast-forward, 13 is touch, 14..28 are extras (menu/quick
// save/quick load/screen swap/mic/rapid fire/etc.).
static constexpr int kNumActions = 29;

struct Prefs {
    // Video filter: basename of the .dfx file (no path, no extension).
    // Default "Linear". When empty or unrecognized we treat it as
    // "Linear".
    std::string currentFx = "Linear";

    // applyConfig-latched bits. Changing any of these requires
    // drastic to be relaunched.
    bool hires3d       = true;
    bool threaded3d    = true;
    bool disableEdge   = true;
    bool soundEnabled  = true;

    // Runtime-adjustable knobs.
    int  volume        = 8;          // _Volume 0..10
    int  audioLatency  = 2;          // _AudioLatency 0..4
    bool micEnabled    = false;      // _MicEnabled
    int  micLevel      = 1;          // _MicLevel 0..2

    // Frameskip: type 0 = fixed value, 1 = auto. Value is used when
    // type=0.
    int  frameskipType  = 0;
    int  frameskipValue = 0;
    bool frameskipSafe  = false;

    // Analog stick behavior.
    bool  analogTouch    = false;    // LS drives DS stylus
    bool  analogTriggers = false;    // Analog trigger mapping
    int   analogStickMode = 0;
    float analogDeadzone = 0.15f;

    // Delay the secondary (top) display's page flip by one refresh
    // so its logical content matches what the primary (bottom)
    // panel is showing at the same wall-clock moment. Needed on
    // dual-DSI setups that cannot be phase-locked in hardware
    // (RK3568 VOP2). Adds 1 frame of input lag to the top panel,
    // on by default since the visible tearing without it is more
    // noticeable than a 16 ms input-lag increase on the top screen
    // (which is gameplay-view on most DS titles and rarely takes
    // touch input).
    bool frameSync = true;

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
    }
};

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

// Map an Android keycode back to an evdev EV_KEY code (reverse of
// evdevToAndroidKeycode). Returns 0 for unmapped.
int androidKeycodeToEvdev(int androidKeycode);

// Human label for an Android keycode (best effort). Used only in the
// Controls UI. Returns a static-lifetime string like "Button A",
// "D-Pad Up", or "KC 123" for unknown values.
const char* androidKeycodeLabel(int androidKeycode);

} // namespace drastic_prefs
} // namespace android
