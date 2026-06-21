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

// NanoBtStable - Bluetooth A2DP audio stability monitor.
//
// On this SoC A2DP is encoded in software (no offload) and the device usually
// runs pinned at the lowest clock (persist.gammaos.performance_mode=powersave =
// 408 MHz), and nano drops to the powersave governor whenever the screen is off.
// At 408 MHz the software aptX/SBC encoder, competing with 2.4 GHz WiFi/BT
// coexistence, underruns and the audio stutters - for nano's own playback (the
// menu, music, radio, video) and, to a lesser degree, for foreground apps.
//
// The user asked for maximum A2DP stability "even at the expense of battery".
// This monitor, while a BT A2DP sink is the active output route, keeps the BT
// controller out of low-power sniff (lpm=0) so the link never has to wake up,
// and while audio is actually streaming to that sink it pins the CPU to the
// performance governor at the hardware maximum frequency so the encoder always
// has headroom (overriding both the user's powersave mode and nano's screen-off
// powersave). It reverts to the user's persisted performance mode (or powersave
// while the screen is off) as soon as A2DP audio stops.
//
// It lives in nano because nano runs as root (so it can write the cpufreq nodes
// and /proc/bluetooth/sleep/lpm with no extra sepolicy) and is always present,
// and because the gammapad daemon - the only other obvious host - is not always
// running. nano runs two processes (DRM home + overlay); a file lock elects a
// single active monitor and the shared sys-prop state makes takeover after a
// process death correct. Everything no-ops if the control nodes are absent, so
// it is harmless on hardware without these knobs. Gated by the kill-switch
// persist.gammaos.nano.btstable (default on).

#ifndef GAMMAOS_NANO_BT_STABLE_H
#define GAMMAOS_NANO_BT_STABLE_H

namespace android {

// Launch the BT A2DP stability monitor on a dedicated low-priority background
// thread. Idempotent: safe to call once per nano process (a file lock ensures
// only one process actively drives the knobs at a time, with failover).
void nanoStartBtStability();

} // namespace android

#endif // GAMMAOS_NANO_BT_STABLE_H
