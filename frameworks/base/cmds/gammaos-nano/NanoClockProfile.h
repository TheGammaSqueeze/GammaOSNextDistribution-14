/*
 * Copyright (C) 2026 GammaOS
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

// RG DS Plus: the vendor setclock_max / setclock_stock / setclock_powersave services (init.gammaos_power.rc)
// are separate oneshot scripts that can run side by side, and the last one to write a node wins. A
// powersave run that the screen-off trigger or a nano sleep path started before a suspend can end after
// the wake's max run, which left the GPU at 400 MHz and the CPU at 1416 MHz until a reboot although the
// persisted mode was max. device/gammaos/gammaos.rc re-applies persist.gammaos.performance_mode whenever
// a foreign setclock run ends while the device is awake. This helper keeps the nano processes in step
// with it: they run the profile through the services (so init sees every run) and mark a sleep they
// start themselves, so init does not undo the pre-sleep powersave run before the screen goes off.
// Header-only, shared by gammaos-nano and drastic-nano (their include_dirs already reach this folder).
// Other devices keep their old behaviour: direct script runs, no marker.
#pragma once

#include <stdio.h>
#include <string.h>

#include <cutils/properties.h>

namespace android {
namespace nanoclock {

inline bool managed() {
    static const bool sManaged = [] {
        char d[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.device", d, "");
        return strcmp(d, "anbernicrgdsplus") == 0;
    }();
    return sManaged;
}

// Start setclock_<mode> through init. Returns false (nothing started) on devices that keep the direct
// script run, or for a mode other than max / stock / powersave, so the caller falls back to its old path.
inline bool startService(const char* mode) {
    if (!mode || !managed()) return false;
    if (strcmp(mode, "max") != 0 && strcmp(mode, "stock") != 0 && strcmp(mode, "powersave") != 0) return false;
    char svc[32];
    snprintf(svc, sizeof svc, "setclock_%s", mode);
    property_set("ctl.start", svc);
    return true;
}

// Call right before the pre-sleep powersave start: init holds off its re-apply, a max or stock run still
// in flight is stopped, and so is a powersave run already in progress (e.g. the vendor screen-off
// trigger's late run after the last wake). init ignores a start on a running oneshot that is not
// disabled, so without that stop the caller's start would do nothing and the max run killed above could
// have left its GPU / DMC writes behind. After the stop the start sets SVC_RESTART (or starts fresh once
// reaped), so powersave runs from the top and is the last writer before the suspend. With the hold at 1
// the resulting state changes cannot fire the re-apply rules.
// The three ctl.stop writes block until init's main loop answers: do not call this on a render thread.
inline void beginSleep() {
    if (!managed()) return;
    property_set("sys.gammaos.nano.clock_hold", "1");
    property_set("ctl.stop", "setclock_max");
    property_set("ctl.stop", "setclock_stock");
    property_set("ctl.stop", "setclock_powersave");
}

// Call on wake, before the profile is re-applied. init clears the hold at every screen on as well, so a
// process that dies mid-sleep cannot leave it set. A property write round-trips to init, so keep it off
// render threads, with the other calls of this header.
inline void endSleep() {
    if (!managed()) return;
    property_set("sys.gammaos.nano.clock_hold", "0");
}

// Stop a running powersave run (the vendor screen-off trigger can start one late). Wake path, before the
// persisted mode is applied again. Blocks until init's main loop answers: never call it on a render thread.
inline void stopPowersave() {
    if (!managed()) return;
    property_set("ctl.stop", "setclock_powersave");
}

} // namespace nanoclock
} // namespace android
