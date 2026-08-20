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

#ifndef GAMMAOS_NANO_POWER_MODE_H
#define GAMMAOS_NANO_POWER_MODE_H

#include <cstring>

#include <cutils/properties.h>

namespace android {
namespace nano_power {

// The active mode remains separate from these per-context defaults. Quick Settings and
// the in-game overlay write the active mode directly; entering a new context replaces it
// with that context's default.
constexpr const char* kActiveMode = "persist.gammaos.performance_mode";
constexpr const char* kNanoDefault = "persist.gammaos.nano.performance_mode";
constexpr const char* kDrasticDefault = "persist.gammaos.drastic_nano.performance_mode";
constexpr const char* kOtherAppsDefault = "persist.gammaos.apps.performance_mode";

inline const char* normalizeMode(const char* mode) {
    if (mode && !strcmp(mode, "max")) return "max";
    if (mode && (!strcmp(mode, "powersave") || !strcmp(mode, "power_save"))) {
        return "powersave";
    }
    // Accept the old human-facing value as an alias for the current max profile.
    if (mode && !strcmp(mode, "performance")) return "max";
    return "stock";
}

inline const char* serviceForMode(const char* mode) {
    if (!strcmp(mode, "max")) return "setclock_max";
    if (!strcmp(mode, "powersave")) return "setclock_powersave";
    return "setclock_stock";
}

inline void applyMode(const char* mode) {
    const char* normalized = normalizeMode(mode);
    property_set(kActiveMode, normalized);
    // The init trigger normally handles property changes. Explicitly restarting the
    // profile also handles re-entering the same mode and avoids trigger races at boot.
    property_set("ctl.start", serviceForMode(normalized));
}

inline void applyDefault(const char* defaultKey, const char* fallback) {
    char mode[PROPERTY_VALUE_MAX] = {};
    property_get(defaultKey, mode, fallback);
    applyMode(mode);
}

inline void applyNanoDefault() {
    applyDefault(kNanoDefault, "stock");
}

inline void applyDrasticDefault() {
    applyDefault(kDrasticDefault, "max");
}

inline void applyOtherAppsDefault() {
    applyDefault(kOtherAppsDefault, "stock");
}

inline bool isDrasticPackage(const char* packageName) {
    return packageName && (!strcmp(packageName, "com.dsemu.drastic")
            || !strcmp(packageName, "com.gammaos.drasticsf"));
}

inline void applyDefaultForPackage(const char* packageName) {
    if (isDrasticPackage(packageName)) applyDrasticDefault();
    else applyOtherAppsDefault();
}

} // namespace nano_power
} // namespace android

#endif // GAMMAOS_NANO_POWER_MODE_H
