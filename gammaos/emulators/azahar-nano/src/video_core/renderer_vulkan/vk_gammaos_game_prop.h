// GammaOS per game switches for the renderer.
//
// The frontend publishes a game's override values (or the global ones when the override
// has no line) as sys.gammaos.azahar_nano.<key> at every launch; the persist prop is the
// fallback (tests set it). Read once at startup by the code that latches a switch.
#pragma once

#include <cstdio>
#include <sys/system_properties.h>

namespace GammaGameProp {

inline void Get(const char* key, char* out /* PROP_VALUE_MAX */) {
    char name[128] = {};   // longer than PROP_NAME_MAX: these names are over 32 characters
    std::snprintf(name, sizeof(name), "sys.gammaos.azahar_nano.%s", key);
    out[0] = '\0';
    __system_property_get(name, out);
    if (out[0] == '\0') {
        std::snprintf(name, sizeof(name), "persist.gammaos.azahar_nano.%s", key);
        __system_property_get(name, out);
    }
}

} // namespace GammaGameProp
