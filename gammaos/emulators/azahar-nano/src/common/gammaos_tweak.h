// Copyright 2026 GammaOS
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#ifdef ANDROID
#include <unistd.h>
#include <sys/system_properties.h>
#endif

namespace Common::GammaOS {

/// True on the Mali devices (no Adreno kgsl node, a Mali kbase node): the RG DS family. The
/// emulation thread tweaks below default on there only, so the Adreno devices keep the
/// behaviour they were validated with until a property turns a tweak on for them too.
inline bool IsMaliDevice() {
#ifdef ANDROID
    static const bool mali =
        access("/sys/class/kgsl/kgsl-3d0", F_OK) != 0 && access("/dev/mali0", F_OK) == 0;
    return mali;
#else
    return false;
#endif
}

/// An emulation thread tweak: the property (persist.gammaos.azahar_nano.<name>) set to 1 turns it
/// on, 0 turns it off, unset leaves it on for the Mali devices only (off everywhere when
/// mali_default is false). Callers read it once and keep the result in a static.
inline bool TweakEnabled(const char* property, bool mali_default = true) {
#ifdef ANDROID
    char v[PROP_VALUE_MAX] = {};
    __system_property_get(property, v);
    if (v[0] == '1') {
        return true;
    }
    if (v[0] == '0') {
        return false;
    }
#endif
    return mali_default && IsMaliDevice();
}

/// The real-time priority of the emulation thread (persist.gammaos.azahar_nano.emu_rt: 0 off,
/// 1 on at priority 2, 2 to 9 that priority; unset: 2 on the Mali devices, off elsewhere).
/// 0 means a normal thread.
inline int EmuRtPriority() {
#ifdef ANDROID
    char v[PROP_VALUE_MAX] = {};
    __system_property_get("persist.gammaos.azahar_nano.emu_rt", v);
    if (v[0] >= '2' && v[0] <= '9') {
        return v[0] - '0';
    }
    if (v[0] == '1') {
        return 2;
    }
    if (v[0] == '0') {
        return 0;
    }
    return IsMaliDevice() ? 2 : 0;
#else
    return 0;
#endif
}

} // namespace Common::GammaOS
