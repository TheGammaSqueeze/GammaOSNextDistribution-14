// Play a boot chime by driving the VENDOR AUDIO HAL directly (over HIDL, via libaudiohal),
// bypassing audioserver / AudioPolicyManager. On SPRD (Unisoc) the AudioPolicyManager ctor
// blocks ~11.7s at cold boot waiting for ActivityManager, so the normal AAudio path cannot
// sound the chime until ~20s; the vendor HAL itself is up at ~7.3s and can open+write the
// speaker output immediately. This plays the chime near that HAL floor (~8s).
//
// Two-step form: nanoHalChimeOpen() does the slow part (waits for the HAL service, opens the
// device + output stream, routes it to the speaker) and returns with the stream ready but
// silent; nanoHalChimeWrite() then streams the WAV and releases the HAL. The boot intro arms
// the open at its start and calls the write at its sound mark, so the chime lands on the
// animation's cue instead of whenever the HAL happens to come up. nanoHalChimeAbort()
// releases an opened stream without playing (boot sound turned off meanwhile, bootreplay).
// All three block and must run on one dedicated detached thread. nanoHalChimePlay() is
// open + write back to back.
//
// Returns true if the HAL played it, false if the HAL was unreachable / the open failed
// (the caller should fall back to the AAudio path).
#pragma once
#include <string>

namespace android {
bool nanoHalChimePlay(const std::string& wavPath, float gain);
bool nanoHalChimeOpen(const std::string& wavPath, float gain);
void nanoHalChimeWrite();
void nanoHalChimeAbort();
}
