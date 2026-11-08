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

#ifndef GAMMAOS_NANO_BRIGHTNESS_H
#define GAMMAOS_NANO_BRIGHTNESS_H

// One brightness policy and one apply path for every nano program: the gammaos-nano home and overlay,
// drastic-nano and azahar-nano. Header-only: all three binaries already have this directory on their include
// path and link libcutils, liblog, libutils, libbinder_ndk, libhidlbase and both lights HAL client libraries.
//
// Policy: one step table for keys and sliders on every device, floor 1. A press moves to the next table entry in
// its direction, so a down press never brightens and an up press never darkens. The HUD percent is the position
// in the table.
//
// Apply path: the caller writes the sysfs nodes at once and returns; it never takes a lock. One thread per process
// ("nano-bright") does the rest, the newest level wins: the lights HAL where the device needs it (not on the
// RG DS Plus, where RockchipLights writes the same nodes and a call cost 0.4 to 0.9 s), and once the level has
// been stable for kDebounceMs, persist.gammaos.nano.brightness and then one
// `cmd settings put system screen_brightness N`, never two at once. Android and the other nano programs read
// those two. On the RG DS Plus the thread also undoes a late DisplayPowerController write of an older level while
// this process drives the panel (watchCheck).

#include <errno.h>
#include <math.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <memory>
#include <thread>
#include <vector>

#include <aidl/android/hardware/light/HwLight.h>
#include <aidl/android/hardware/light/HwLightState.h>
#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/LightType.h>
#include <android/binder_manager.h>
#include <android/hardware/light/2.0/ILight.h>
#include <android/log.h>
#include <cutils/properties.h>

#include "NanoBacklight.h"

#ifndef POSIX_SPAWN_USEVFORK
#define POSIX_SPAWN_USEVFORK 64   // bionic's value; defined only under __USE_GNU
#endif

#define NANOBR_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NanoBrightness", __VA_ARGS__)
#define NANOBR_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "NanoBrightness", __VA_ARGS__)

namespace android {
namespace nanobr {

// ---- Policy ---------------------------------------------------------------------------------------------------

// Levels in the Android 0..255 range. On the RG DS Plus the duty table (rk3568-deep-rg_ds-2mipi.dtsi) gives about
// 1.25x the light per step above 8; below 8 every level is a real, darker duty step.
inline constexpr int kSteps[] = {1, 2, 3, 4, 5, 6, 7, 8, 14, 22, 33, 46, 59, 76, 97, 123, 156, 196, 255};
inline constexpr int kStepCount = (int)(sizeof(kSteps) / sizeof(kSteps[0]));
inline constexpr int64_t kDebounceMs = 500;            // stable this long before persist + Settings
inline constexpr int64_t kChordRepeatFirstMs = 400;    // Select+Vol held: first repeat
inline constexpr int64_t kChordRepeatMs = 110;         // then every
inline constexpr int64_t kLateWriteWatchMs = 2000;     // RG DS Plus: watch the nodes this long (push, relight)
inline constexpr int64_t kSessionPushDelayMs = 3000;   // drastic / azahar: the session-start push waits this long

inline int clampLevel(int level) { return level < 1 ? 1 : (level > 255 ? 255 : level); }

// One press: dir < 0 the largest entry below cur, dir > 0 the smallest entry above it, cur itself at the ends.
inline int brightnessStep(int cur, int dir) {
    cur = clampLevel(cur);
    if (dir < 0) {
        for (int i = kStepCount - 1; i >= 0; i--) if (kSteps[i] < cur) return kSteps[i];
    } else if (dir > 0) {
        for (int i = 0; i < kStepCount; i++) if (kSteps[i] > cur) return kSteps[i];
    }
    return cur;
}

// |steps| presses in the direction of the sign.
inline int brightnessStepN(int cur, int steps) {
    int level = clampLevel(cur);
    const int dir = steps < 0 ? -1 : 1;
    for (int n = steps < 0 ? -steps : steps; n > 0; n--) level = brightnessStep(level, dir);
    return level;
}

// HUD percent: the position in the table (interpolated between entries), 1..100; 0 only for level <= 0.
inline int brightnessPercent(int level) {
    if (level <= 0) return 0;
    level = clampLevel(level);
    for (int i = 0; i < kStepCount - 1; i++) {
        if (level <= kSteps[i + 1]) {
            const float pos = (float)i + (float)(level - kSteps[i]) / (float)(kSteps[i + 1] - kSteps[i]);
            const int pct = (int)lroundf(100.0f * pos / (float)(kStepCount - 1));
            return pct < 1 ? 1 : pct;
        }
    }
    return 100;
}

// A drag slider: fraction 0..1 of the bar to a level, the inverse of brightnessPercent()/100.
inline int brightnessLevelForFraction(float f) {
    if (!(f > 0.0f)) f = 0.0f;
    if (f > 1.0f) f = 1.0f;
    const float pos = f * (float)(kStepCount - 1);
    int i = (int)pos;
    if (i > kStepCount - 2) i = kStepCount - 2;
    const float frac = pos - (float)i;
    return clampLevel((int)lroundf((float)kSteps[i] + frac * (float)(kSteps[i + 1] - kSteps[i])));
}

// The table index closest to level (menu choice rows).
inline int stepIndexNearest(int level) {
    level = clampLevel(level);
    int best = 0;
    for (int i = 1; i < kStepCount; i++) {
        const int d = kSteps[i] - level, b = kSteps[best] - level;
        if ((d < 0 ? -d : d) < (b < 0 ? -b : b)) best = i;
    }
    return best;
}

inline bool isRgDsPlus() {
    static const bool sPlus = [] {
        char dev[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.device", dev, "");
        return strcmp(dev, "anbernicrgdsplus") == 0;
    }();
    return sPlus;
}

// The lights HAL is skipped on the RG DS Plus: RockchipLights writes the same sysfs nodes, DisplayPowerController
// writes the HAL itself, and a call cost 0.4 to 0.9 s there (getLights re-reads the HAL config on every call).
// An empty node list there (should not happen) keeps the HAL. Moved here from drastic-nano main.cpp. It also
// decides the late-write watch: only where the nodes follow the framework 1:1.
inline bool backlightHalSkipped() {
    static const bool sSkip = isRgDsPlus() && !nanobl::nanoBacklightNodes().empty();
    return sSkip;
}

// ---- Apply path -----------------------------------------------------------------------------------------------

struct State {
    std::atomic<int> want{-1};             // wanted level 1..255, -1 = not seeded
    std::atomic<uint32_t> seq{0};          // bumps on every change of want or dark
    std::atomic<bool> dark{false};         // panelOff(): sysfs 0, no panel writes, no debounced push until panelOn()
    std::atomic<bool> released{false};     // brightnessRelease(): another process drives the panel
    std::atomic<bool> watchOwner{false};   // brightnessWatchPanel(true): this process drives the panel now
    std::atomic<int64_t> watchUntilMs{0};  // RG DS Plus: the late-write watch runs until then (CLOCK_MONOTONIC ms)
    std::atomic<int64_t> changedMs{0};     // when want last changed (debounce)
    std::atomic<int> lastPersisted{-1};    // what this process last wrote to (or found in) persist
    std::atomic<int> lastPushed{-1};       // what Settings holds as far as this process knows (-1: unknown)
    std::atomic<int> lastSpawned{-1};      // level of the newest push started; BrightnessSynchronizer echoes it into persist
    std::atomic<bool> forcePush{false};    // push even when lastPushed == want (home start, unpark, session start)
    std::atomic<int64_t> forceAtMs{0};     // ... not before this time
    std::atomic<uint32_t> flushReq{0}, flushDone{0};   // flush / push-now: no debounce, also while dark
    std::atomic<bool> pushInFlight{false};
    uint32_t halDoneSeq = 0;               // seq the lights HAL last got; nano-bright thread only (halStep)
    std::atomic<bool> started{false};
    int wakeFd = -1;
    pthread_mutex_t persistMu;             // PTHREAD_PRIO_INHERIT: the persist write of a caller and the thread
};

inline State& state() {
    static State* sState = [] {
        State* s = new State;   // never destroyed: the thread is detached
        pthread_mutexattr_t a;
        pthread_mutexattr_init(&a);
        pthread_mutexattr_setprotocol(&a, PTHREAD_PRIO_INHERIT);
        pthread_mutex_init(&s->persistMu, &a);
        pthread_mutexattr_destroy(&a);
        s->wakeFd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
        return s;
    }();
    return *sState;
}

inline int64_t nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

inline void wakeThread() {
    const int fd = state().wakeFd;
    if (fd >= 0) eventfd_write(fd, 1);
}

// Writes what the panel should show now (0 while dark) and repeats while another thread changed the state meanwhile,
// so the last write always carries the newest state. Never while released, never before a level is known.
inline void applyPanel() {
    State& s = state();
    for (int i = 0; i < 4; i++) {
        if (s.released.load()) return;
        const uint32_t q = s.seq.load();
        const bool dark = s.dark.load();
        const int w = s.want.load();
        if (!dark && w <= 0) return;
        nanobl::nanoBacklightSet(dark ? 0 : w);
        if (s.seq.load() == q) return;
    }
}

// RG DS Plus: watch the nodes for kLateWriteWatchMs from now (a push started or ended, or the panel was relit after
// a dark period: DisplayPowerController may apply an older Settings level after it). No-op elsewhere and in a
// process that does not drive the panel (no needless 100 ms wakes there).
inline void armWatch() {
    if (!backlightHalSkipped()) return;
    State& s = state();
    if (!s.watchOwner.load()) return;
    const int64_t until = nowMs() + kLateWriteWatchMs;
    int64_t cur = s.watchUntilMs.load();
    while (cur < until && !s.watchUntilMs.compare_exchange_weak(cur, until)) {}
}

// RG DS Plus late-write watch. RockchipLights writes the nodes 1:1, so a node that differs from the wanted level
// inside the watch window is DisplayPowerController applying an older pushed level after the user pressed again:
// the wanted level is written back at once. Only while this process drives the panel (never in the overlay while it
// is hidden: there a change is PhoneWindowManager's, for RetroArch), never while dark or released, never while the
// screen is going off (sys.screen.state must be "on"), never from 0 (a framework screen-off), and only while persist
// still holds this process's level: its last persist write, or the echo BrightnessSynchronizer writes for its newest
// push. Any other persist value means another program set the level, and that one wins. nano-bright thread only
// (the loop and runSettingsPut's wait).
inline void watchCheck() {
    if (!backlightHalSkipped()) return;
    State& s = state();
    if (nowMs() >= s.watchUntilMs.load() || !s.watchOwner.load() || s.released.load() || s.dark.load()) return;
    const int cur = s.want.load();
    if (cur <= 0) return;
    const int p = property_get_int32("persist.gammaos.nano.brightness", -1);
    if (p != s.lastPersisted.load() && p != s.lastSpawned.load()) return;
    char scr[PROPERTY_VALUE_MAX] = {};
    property_get("sys.screen.state", scr, "");
    if (strcmp(scr, "on") != 0) return;
    if (nanobl::nanoBacklightReadMin() <= 0) return;   // no readable node, or a node is dark: never relight from 0
    if (nanobl::nanoBacklightMatches(cur)) return;
    applyPanel();
    static int64_t sLoggedWindow = -1;   // one line per watch window
    const int64_t window = s.watchUntilMs.load();
    if (window != sLoggedWindow) {
        sLoggedWindow = window;
        NANOBR_LOGI("late framework backlight write undone, panel back to %d", cur);
    }
}

// Never destroyed (see nanoBacklightNodes): nano-bright may still be in a HAL call while static destructors run at
// a return from main().
struct HalCache {
    std::shared_ptr<aidl::android::hardware::light::ILights> aidl;
    std::vector<int> ids;
    android::sp<::android::hardware::light::V2_0::ILight> hidl;
};
inline HalCache& halCache() {
    static HalCache* sCache = new HalCache;
    return *sCache;
}

// The lights HAL, AIDL ILights first, else HIDL light@2.0 (the Brick routes the backlight only through it).
// 0..255 in the colour channels, the convention of LightsService. The service and the backlight ids are cached;
// a failed call drops the cache. nano-bright thread only.
inline bool setLightsHal(int level) {
    using aidl::android::hardware::light::HwLight;
    using aidl::android::hardware::light::HwLightState;
    using aidl::android::hardware::light::ILights;
    using aidl::android::hardware::light::LightType;
    HalCache& c = halCache();
    const int color = (int)(0xFF000000u | ((uint32_t)level << 16) | ((uint32_t)level << 8) | (uint32_t)level);
    if (!c.aidl) {
        ndk::SpAIBinder binder(AServiceManager_checkService("android.hardware.light.ILights/default"));
        if (binder.get()) {
            c.aidl = ILights::fromBinder(binder);
            c.ids.clear();
            std::vector<HwLight> lights;
            if (c.aidl && c.aidl->getLights(&lights).isOk()) {
                for (const auto& l : lights) if (l.type == LightType::BACKLIGHT) c.ids.push_back(l.id);
            }
        }
    }
    if (c.aidl && !c.ids.empty()) {
        bool ok = true;
        for (int id : c.ids) {
            HwLightState st{};
            st.color = color;
            if (!c.aidl->setLightState(id, st).isOk()) ok = false;
        }
        if (ok) return true;
        c.aidl.reset();
        c.ids.clear();
    }
    using ::android::hardware::light::V2_0::Brightness;
    using ::android::hardware::light::V2_0::Flash;
    using ::android::hardware::light::V2_0::ILight;
    using ::android::hardware::light::V2_0::LightState;
    using ::android::hardware::light::V2_0::Type;
    if (c.hidl == nullptr) c.hidl = ILight::getService();
    if (c.hidl == nullptr) return false;
    LightState st{};
    st.color = (uint32_t)color;
    st.flashMode = Flash::NONE;
    st.brightnessMode = Brightness::USER;
    if (c.hidl->setLight(Type::BACKLIGHT, st).isOk()) return true;
    c.hidl = nullptr;
    return false;
}

// persist.gammaos.nano.brightness = the newest level, one property_set when it differs. Any thread.
inline void brightnessFlushPersist() {
    State& s = state();
    pthread_mutex_lock(&s.persistMu);
    const int w = s.want.load();
    if (w > 0) {
        if (property_get_int32("persist.gammaos.nano.brightness", -1) != w) {
            char v[8];
            snprintf(v, sizeof(v), "%d", w);
            property_set("persist.gammaos.nano.brightness", v);
            NANOBR_LOGI("persist.gammaos.nano.brightness=%d", w);
        }
        s.lastPersisted.store(w);
    }
    pthread_mutex_unlock(&s.persistMu);
}

// Lights HAL, newest target (0 while dark). A no-op on the RG DS Plus (HAL skipped) and while nothing changed since
// the last call. Called at the top of the nano-bright loop and from runSettingsPut's wait, so the newest level
// reaches the HAL while a Settings push is running too. nano-bright thread only.
inline void halStep() {
    if (backlightHalSkipped()) return;
    State& s = state();
    const uint32_t q = s.seq.load();
    if (q == s.halDoneSeq) return;
    s.halDoneSeq = q;
    const int t = s.dark.load() ? 0 : s.want.load();
    if (s.released.load() || t < 0) return;
    const int64_t h0 = nowMs();
    const bool ok = setLightsHal(t);
    NANOBR_LOGI("lights HAL %d %s in %lld ms", t, ok ? "set" : "unavailable", (long long)(nowMs() - h0));
}

// `cmd settings put system screen_brightness N` (what /system/bin/settings runs), vfork-spawned so a big mlocked
// process is never copied, waited for, killed after 5 s. The late-write watch is armed when it starts (DPC can apply
// the level before cmd exits) and again when it ends, and checked every 100 ms while it runs. Returns the exit
// status, or < 0. nano-bright thread only.
inline int runSettingsPut(int level) {
    State& s = state();
    char val[8];
    snprintf(val, sizeof(val), "%d", level);
    char* const argv[] = {(char*)"cmd", (char*)"settings", (char*)"put", (char*)"system",
                          (char*)"screen_brightness", val, nullptr};
    posix_spawnattr_t attr;
    if (posix_spawnattr_init(&attr) != 0) return -ENOMEM;
    posix_spawnattr_setflags(&attr, POSIX_SPAWN_USEVFORK | POSIX_SPAWN_CLOEXEC_DEFAULT);
    pid_t pid = -1;
    s.lastSpawned.store(level);
    s.pushInFlight.store(true);
    const int err = posix_spawn(&pid, "/system/bin/cmd", nullptr, &attr, argv, environ);
    posix_spawnattr_destroy(&attr);
    if (err != 0) {
        s.pushInFlight.store(false);
        return -err;
    }
    armWatch();
    int status = -1;
    const int64_t t0 = nowMs();
    int64_t nextWatchMs = t0 + 100;
    for (;;) {
        const pid_t r = waitpid(pid, &status, WNOHANG);
        if (r == pid) break;
        if (r < 0 && errno != EINTR) { status = -1; break; }
        const int64_t now = nowMs();
        if (now - t0 > 5000) {
            kill(pid, SIGKILL);
            waitpid(pid, nullptr, 0);
            NANOBR_LOGW("settings push hung for 5 s, killed");
            status = -1;
            break;
        }
        if (now >= nextWatchMs) {
            nextWatchMs = now + 100;
            watchCheck();
        }
        halStep();
        usleep(10 * 1000);
    }
    s.pushInFlight.store(false);
    armWatch();
    return (status >= 0 && WIFEXITED(status)) ? WEXITSTATUS(status) : -1;
}

inline void threadMain() {
    pthread_setname_np(pthread_self(), "nano-bright");
    {
        sched_param sp = {};   // a FIFO creator's policy is inherited
        sched_setscheduler(0, SCHED_OTHER, &sp);
        setpriority(PRIO_PROCESS, 0, 0);
    }
    State& s = state();
    const bool hal = !backlightHalSkipped();
    NANOBR_LOGI("thread up (lights HAL %s)", hal ? "used" : "skipped");
    int64_t retryAtMs = 0;
    int retries = 0;
    for (;;) {
        // ---- sleep until something is due
        const int64_t now = nowMs();
        int64_t timeout = -1;
        const auto dueAt = [&](int64_t at) {
            const int64_t d = at > now ? at - now : 0;
            if (timeout < 0 || d < timeout) timeout = d;
        };
        const int w0 = s.want.load();
        if (w0 > 0) {
            const int64_t debounced = s.changedMs.load() + kDebounceMs;
            if (w0 != s.lastPersisted.load()) dueAt(debounced);
            if (!s.released.load() && !s.dark.load()) {
                if (w0 != s.lastPushed.load()) {
                    dueAt(retryAtMs > debounced ? retryAtMs : debounced);
                } else if (s.forcePush.load()) {
                    int64_t at = s.forceAtMs.load();
                    if (at < debounced) at = debounced;
                    dueAt(retryAtMs > at ? retryAtMs : at);
                }
            }
        }
        if (s.watchUntilMs.load() > now) dueAt(now + 100);
        if (hal && s.seq.load() != s.halDoneSeq) timeout = 0;
        if (s.flushReq.load() != s.flushDone.load()) timeout = 0;
        struct pollfd pfd = {s.wakeFd, POLLIN, 0};
        poll(&pfd, 1, timeout < 0 ? -1 : (int)timeout);
        eventfd_t ev;
        eventfd_read(s.wakeFd, &ev);   // clears the wake (EAGAIN when none)

        // ---- 0. RG DS Plus late-write watch: on every wake, before the debounce gate, so a stale DPC write that
        // lands during the debounce of the next press is undone too
        watchCheck();

        // ---- 1. lights HAL, newest target (0 while dark); a newer change during the call loops at once
        halStep();

        // ---- 2. debounce (a flush or push-now skips it)
        const uint32_t fr = s.flushReq.load();
        const bool flushing = fr != s.flushDone.load();
        const int w = s.want.load();
        if (w <= 0) { if (flushing) s.flushDone.store(fr); continue; }
        if (!flushing && nowMs() < s.changedMs.load() + kDebounceMs) continue;

        // ---- 3. persist
        if (w != s.lastPersisted.load()) brightnessFlushPersist();

        // ---- 4. Settings: a change, or a forced push once due. Never while released; while dark only for a flush or
        // push-now (the sleep paths ask for it after the panels are off, where DPC's write cannot light them).
        const bool mayPush = !s.released.load() && (!s.dark.load() || flushing);
        const bool forcedDue = s.forcePush.load() && (flushing || nowMs() >= s.forceAtMs.load());
        if (mayPush && (w != s.lastPushed.load() || forcedDue) && (flushing || nowMs() >= retryAtMs)) {
            const int64_t p0 = nowMs();
            const int rc = runSettingsPut(w);
            if (rc == 0) {
                s.lastPushed.store(w);
                if (s.want.load() == w) s.forcePush.store(false);
                retries = 0;
                retryAtMs = 0;
                NANOBR_LOGI("screen_brightness=%d pushed in %lld ms", w, (long long)(nowMs() - p0));
            } else if (++retries <= 30) {
                retryAtMs = nowMs() + 2000;
                if (retries == 1) NANOBR_LOGW("screen_brightness=%d push failed (status %d), retrying every 2 s", w, rc);
            } else {
                s.lastPushed.store(w);   // give up on this level; the next change pushes again
                s.forcePush.store(false);
                retries = 0;
                retryAtMs = 0;
                NANOBR_LOGW("screen_brightness push given up after 30 tries");
            }
        }
        if (flushing) s.flushDone.store(fr);
    }
}

inline void ensureThread() {
    State& s = state();
    if (!s.started.exchange(true)) std::thread(threadMain).detach();
}

// ---- Public API ---------------------------------------------------------------------------------------------------

// The wanted level. The first call seeds it from persist.gammaos.nano.brightness (1..255, 128 when unset) and
// writes nothing. Later calls adopt a level another program wrote to persist (the gammaos-nano overlay over a
// SurfaceFlinger session, a slider) when this process has nothing pending: no unpersisted level, no push running,
// and the value is not the BrightnessSynchronizer echo of this process's newest push. The caller relights or steps
// from it; nothing is written here.
inline int currentLevel() {
    State& s = state();
    const int p = property_get_int32("persist.gammaos.nano.brightness", -1);
    const int w = s.want.load();
    if (w <= 0) {
        int expected = -1;
        if (s.want.compare_exchange_strong(expected, clampLevel(p > 0 ? p : 128))) s.lastPersisted.store(p);
        return s.want.load();
    }
    if (p >= 1 && p <= 255 && p != w && w == s.lastPersisted.load() && p != s.lastSpawned.load() &&
        !s.pushInFlight.load()) {
        s.lastPersisted.store(p);
        s.lastPushed.store(p);   // the other program put it into Settings (its own push or BrightnessSynchronizer)
        s.want.store(p);
        s.seq.fetch_add(1);
        NANOBR_LOGI("level %d adopted from persist (set by another program)", p);
        return p;
    }
    return w;
}

// Process start with a known level (the home): the panel at once (sysfs); no HAL, persist or Settings work.
// inSettings: the level was read from Settings (no push needed for it).
inline void brightnessInit(int level, bool inSettings) {
    State& s = state();
    level = clampLevel(level);
    s.want.store(level);
    s.lastPersisted.store(property_get_int32("persist.gammaos.nano.brightness", -1));
    if (inSettings) s.lastPushed.store(level);
    s.seq.fetch_add(1);
    applyPanel();
}

// A user change: the panel at once (unless dark), the HAL / persist / Settings from nano-bright.
inline void brightnessSet(int level) {
    State& s = state();
    level = clampLevel(level);
    s.released.store(false);
    if (s.want.load() == level) return;
    s.want.store(level);
    s.changedMs.store(nowMs());
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

// A level read from persist (another program wrote it): the panel at once, no persist write, Settings pushed when it
// differs from the last push (always with forcePush).
inline void brightnessAdopt(int level, bool forcePush) {
    State& s = state();
    level = clampLevel(level);
    s.released.store(false);
    s.lastPersisted.store(level);
    s.want.store(level);
    s.changedMs.store(nowMs() - kDebounceMs);
    if (forcePush) {
        s.forceAtMs.store(0);
        s.forcePush.store(true);
    }
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

// The overlay over an app: a level that is already in Settings (PhoneWindowManager / BrightnessSynchronizer for
// RetroArch, or an earlier push). The panel at once (and the HAL where needed); nothing is persisted or pushed back.
inline void brightnessAdoptFromSettings(int level) {
    State& s = state();
    level = clampLevel(level);
    s.released.store(false);
    s.lastPersisted.store(level);
    s.lastPushed.store(level);
    s.want.store(level);
    s.changedMs.store(nowMs() - kDebounceMs);
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

// Something else may have driven the panel (an app ran): the panel and the HAL again at the wanted level, and
// Settings pushed once more.
inline void brightnessReassert() {
    State& s = state();
    currentLevel();
    s.released.store(false);
    s.forceAtMs.store(0);
    s.forcePush.store(true);
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

// True while a level set here is not yet in persist.
inline bool brightnessPending() {
    State& s = state();
    const int w = s.want.load();
    return w > 0 && w != s.lastPersisted.load();
}

// Push Settings once more even if it already holds the level, not before delayMs from now (home start and unpark:
// 0; drastic / azahar session start: kSessionPushDelayMs).
inline void forceSettingsPush(int delayMs = 0) {
    State& s = state();
    if (delayMs > 0) {
        // Settings' content is unknown (-1) right after the seed: treat it as the current level so that only the
        // forced push at forceAtMs runs. A level change before then still pushes through w != lastPushed.
        const int w = s.want.load();
        int unknown = -1;
        if (w > 0) s.lastPushed.compare_exchange_strong(unknown, w);
    }
    s.forceAtMs.store(nowMs() + (delayMs > 0 ? delayMs : 0));
    s.forcePush.store(true);
    ensureThread();
    wakeThread();
}

// This process drives the panel now (true: a drastic or azahar session, the home that is not the overlay instance,
// the overlay while it is shown) or no longer (false, which also ends a running watch window). The RG DS Plus
// late-write watch only runs while it does.
inline void brightnessWatchPanel(bool on) {
    State& s = state();
    const bool was = s.watchOwner.exchange(on);
    if (!on) s.watchUntilMs.store(0);
    if (was != on && backlightHalSkipped()) NANOBR_LOGI("late-write watch %s", on ? "on" : "off");
}

// Lid grace, sleep: sysfs 0 at once, HAL 0 from the thread, no panel writes and no debounced Settings push until
// panelOn(). A sleep path calls brightnessPushNow() once the panels are powered off.
inline void panelOff() {
    State& s = state();
    if (!s.dark.exchange(true)) NANOBR_LOGI("panel dark (Settings push held back)");
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

// Relight at level (<= 0: currentLevel()) and end the dark state. A relight from dark arms the late-write watch
// (DisplayPowerController applies Settings at screen-on, possibly after this write).
inline void panelOn(int level) {
    State& s = state();
    if (level <= 0) level = currentLevel();
    level = clampLevel(level);
    s.released.store(false);
    if (s.want.load() != level) {
        s.want.store(level);
        s.changedMs.store(nowMs());
    }
    if (s.dark.exchange(false)) {
        NANOBR_LOGI("panel lit at %d", level);
        armWatch();
    }
    s.seq.fetch_add(1);
    applyPanel();
    ensureThread();
    wakeThread();
}

inline bool panelDark() { return state().dark.load(); }

// A pending level goes to persist and Settings now (no debounce, also while dark), without waiting. Nothing is
// pushed when Settings already holds it. Sleep paths after the panels are off, the overlay hide, the Control Center
// slider release, the home's app launch.
inline void brightnessPushNow() {
    State& s = state();
    if (s.want.load() <= 0) return;
    s.flushReq.fetch_add(1);
    ensureThread();
    wakeThread();
}

// Waits up to timeoutMs for a Settings push that is running. True when none runs.
inline bool brightnessWaitPushIdle(int timeoutMs) {
    State& s = state();
    const int64_t t0 = nowMs();
    while (s.pushInFlight.load() && nowMs() - t0 < timeoutMs) usleep(10 * 1000);
    return !s.pushInFlight.load();
}

// Another process takes the panel: persist now (calling thread), then no panel writes, pushes or watch checks from
// this process until it sets, adopts or relights again; waits up to waitPushMs for a running push. The next owner
// pushes Settings (D4).
inline void brightnessRelease(int waitPushMs) {
    State& s = state();
    if (s.want.load() <= 0) return;   // nothing was ever set here
    currentLevel();   // adopt a level another program set meanwhile, so persist is not overwritten with a stale one
    brightnessFlushPersist();
    s.watchUntilMs.store(0);
    s.released.store(true);
    wakeThread();
    const bool idle = waitPushMs > 0 ? brightnessWaitPushIdle(waitPushMs) : !s.pushInFlight.load();
    NANOBR_LOGI("panel handed on at %d%s", s.want.load(), idle ? "" : " (a Settings push is still running)");
}

// After brightnessRelease() when nobody took the panel after all (drastic's tail without a hand-off): pushes and the
// thread work are allowed again. Writes nothing itself.
inline void brightnessReclaim() { state().released.store(false); }

// Exit: no watch any more (static destructors may run after this returns), persist now (calling thread), then
// Settings now (no debounce, also while dark), waiting up to timeoutMs. True when Settings holds the wanted level.
// Returns at once when Settings already holds it.
inline bool brightnessFlush(int timeoutMs) {
    State& s = state();
    if (s.want.load() <= 0 || s.released.load()) return true;
    brightnessWatchPanel(false);
    currentLevel();   // adopt a level another program set meanwhile (see brightnessRelease)
    const int64_t t0 = nowMs();
    brightnessFlushPersist();
    const uint32_t req = s.flushReq.fetch_add(1) + 1;
    ensureThread();
    wakeThread();
    while ((int32_t)(s.flushDone.load() - req) < 0 && nowMs() - t0 < timeoutMs) usleep(10 * 1000);
    const bool ok = s.lastPushed.load() == s.want.load();
    NANOBR_LOGI("flush: %d %s in %lld ms", s.want.load(), ok ? "in Settings" : "NOT in Settings",
                (long long)(nowMs() - t0));
    return ok;
}

}  // namespace nanobr
}  // namespace android

#endif  // GAMMAOS_NANO_BRIGHTNESS_H
