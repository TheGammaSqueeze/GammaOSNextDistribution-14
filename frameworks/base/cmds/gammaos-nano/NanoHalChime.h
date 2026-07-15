// Play a boot chime by driving the VENDOR AUDIO HAL directly (over HIDL, via libaudiohal),
// bypassing audioserver / AudioPolicyManager. On SPRD (Unisoc) the AudioPolicyManager ctor
// blocks ~11.7s at cold boot waiting for ActivityManager, so the normal AAudio path cannot
// sound the chime until ~20s; the vendor HAL itself is up at ~7.3s and can open+write the
// speaker output immediately. This plays the chime near that HAL floor (~8s).
//
// Blocking: plays the whole WAV synchronously then releases the HAL stream. Call it on a
// dedicated detached thread. Returns true if the HAL played it, false if the HAL was
// unreachable / the open failed (the caller should fall back to the AAudio path).
#pragma once
#include <string>

namespace android {
bool nanoHalChimePlay(const std::string& wavPath, float gain);
}
