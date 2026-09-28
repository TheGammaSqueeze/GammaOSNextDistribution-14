/*
 * drastic-nano: the per-game performance mode.
 *
 * persist.gammaos.performance_mode (max / stock / powersave) is a device-wide setting the
 * home and the in-game overlay both change, and init.gammaos_power.rc applies it through the
 * setclock_* services. A per-game override (DrasticSettings.h) may carry its own mode under
 * the short key "performance_mode". When it differs from the global mode at launch the global
 * value is parked in persist.gammaos.drastic.perf_restore, the game's mode is applied,
 * and the parked value is put back on exit (clean exit, crash handler, and the home as a
 * safety net after a session that ended without either). Persisted so a reboot mid-game
 * still restores the user's global choice.
 */
#pragma once

#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>

#include "DrasticSettings.h"

namespace android {
namespace drastic_perf {

constexpr const char* kGlobalProp   = "persist.gammaos.performance_mode";
// Not under the drastic_nano prefix on purpose: while a per-game override is active every
// drastic_nano key written through drastic_settings lands in the override file, and this
// bookkeeping must stay a real property the home can read after the game is gone.
constexpr const char* kRestoreProp  = "persist.gammaos.drastic.perf_restore";
constexpr const char* kOverrideProp = "persist.gammaos.drastic_nano.performance_mode";

inline const char* serviceFor(const char* mode) {
    if (strcmp(mode, "stock") == 0) return "setclock_stock";
    if (strcmp(mode, "powersave") == 0) return "setclock_powersave";
    return "setclock_max";
}

inline bool validMode(const char* m) {
    return strcmp(m, "max") == 0 || strcmp(m, "stock") == 0 || strcmp(m, "powersave") == 0;
}

// Switch the device to `mode` for this game, remembering the global mode the first time.
inline void applyForGame(const char* mode) {
    if (!validMode(mode)) return;
    char cur[PROPERTY_VALUE_MAX] = {};
    property_get(kGlobalProp, cur, "stock");
    if (strcmp(cur, mode) == 0) return;
    char saved[PROPERTY_VALUE_MAX] = {};
    property_get(kRestoreProp, saved, "");
    if (!saved[0]) property_set(kRestoreProp, cur);
    property_set(kGlobalProp, mode);
    property_set("ctl.start", serviceFor(mode));
    ALOGI("drastic-nano: per-game performance mode %s (global %s parked)", mode, saved[0] ? saved : cur);
}

// After the per-game override loaded: apply its mode if it has one.
inline void applyOverrideAtLaunch() {
    if (!drastic_settings::overrideActive()) return;
    char want[PROPERTY_VALUE_MAX] = {};
    drastic_settings::get(kOverrideProp, want, "");
    if (want[0]) applyForGame(want);
}

// Put the parked global mode back (no-op when nothing was parked). Safe from a signal
// handler: only property reads and writes.
inline void restoreGlobal() {
    char saved[PROPERTY_VALUE_MAX] = {};
    property_get(kRestoreProp, saved, "");
    if (!saved[0]) return;
    property_set(kGlobalProp, saved);
    property_set("ctl.start", serviceFor(saved));
    property_set(kRestoreProp, "");
    ALOGI("drastic-nano: global performance mode %s restored", saved);
}

} // namespace drastic_perf
} // namespace android
