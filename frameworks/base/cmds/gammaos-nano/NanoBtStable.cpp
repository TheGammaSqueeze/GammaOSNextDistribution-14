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

#define LOG_TAG "GammaOSNano"

#include "NanoBtStable.h"

#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

namespace android {

namespace {

// ---- control nodes -------------------------------------------------------
constexpr const char* kLpmNode    = "/proc/bluetooth/sleep/lpm";
constexpr const char* kCpufreqDir = "/sys/devices/system/cpu/cpufreq";
constexpr const char* kPolicy0Gov = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
constexpr const char* kLockFile   = "/data/system/nano_btstable.lock";

// ---- props ---------------------------------------------------------------
constexpr const char* kPropEnable    = "persist.gammaos.nano.btstable";        // kill-switch (default on)
constexpr const char* kPropActive    = "sys.gammaos.nano.btstable.active";     // 1 while we hold the CPU boost
constexpr const char* kPropScreenOff = "sys.gammaos.nano.screenoff";           // nano publishes 1 while the screen is off
constexpr const char* kPropPerfMode  = "persist.gammaos.performance_mode";     // user's chosen mode

// ---- cadence (seconds) ---------------------------------------------------
constexpr int kTickSec       = 1;   // base loop period while we hold the lock
constexpr int kRoutePollSec  = 6;   // how often to re-check the A2DP active route (dumpsys)
constexpr int kAudioPollSec  = 2;   // how often to re-check active playback (dumpsys), only while routed

// Read up to n-1 bytes of a sysfs/procfs file, NUL-terminate. False on any error.
bool readFile(const char* path, char* buf, size_t n) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    ssize_t r = read(fd, buf, n - 1);
    close(fd);
    if (r <= 0) return false;
    buf[r] = '\0';
    return true;
}

// Write a string to a node. False on any error (missing node -> harmless no-op).
bool writeFile(const char* path, const char* val) {
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) return false;
    size_t len = strlen(val);
    ssize_t w = write(fd, val, len);
    close(fd);
    return w == (ssize_t)len;
}

void chomp(char* s) {
    size_t L = strlen(s);
    while (L && (s[L - 1] == '\n' || s[L - 1] == '\r' || s[L - 1] == ' ' || s[L - 1] == '\t'))
        s[--L] = '\0';
}

// BT radio state: 1 = on, 0 = off, -1 = unknown (no rfkill / cannot read).
// Scans /sys/class/rfkill for the entry whose type is "bluetooth" (portable),
// falling back to rfkill0 (the Brick's sunxi-bt) if the directory cannot be read.
int btRadioState() {
    DIR* d = opendir("/sys/class/rfkill");
    if (!d) {
        char b[8];
        if (readFile("/sys/class/rfkill/rfkill0/state", b, sizeof b)) return b[0] == '0' ? 0 : 1;
        return -1;
    }
    int st = -1;
    struct dirent* e;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "rfkill", 6) != 0) continue;
        char tp[300], typ[32] = {0};
        snprintf(tp, sizeof tp, "/sys/class/rfkill/%s/type", e->d_name);
        if (!readFile(tp, typ, sizeof typ)) continue;
        chomp(typ);
        if (strcmp(typ, "bluetooth") != 0) continue;
        char sp[300], sv[8] = {0};
        snprintf(sp, sizeof sp, "/sys/class/rfkill/%s/state", e->d_name);
        if (readFile(sp, sv, sizeof sv)) st = (sv[0] == '0') ? 0 : 1;
        break;
    }
    closedir(d);
    return st;
}

int lpmGet() {
    char b[64];
    if (!readFile(kLpmNode, b, sizeof b)) return -1;
    for (int i = (int)strlen(b) - 1; i >= 0; --i)
        if (b[i] == '0' || b[i] == '1') return b[i] - '0';
    return -1;
}
bool lpmSet(int v) { return writeFile(kLpmNode, v ? "1" : "0"); }

// Is a BT A2DP sink the active output route? Parse `dumpsys bluetooth_manager`:
// inside the "Profile: A2dpService" block, mActiveDevice is a non-empty, non-null MAC.
// Runs on the dedicated monitor thread (never the render thread) so the blocking
// dumpsys cannot trip the render watchdog.
bool a2dpRouteActive() {
    FILE* f = popen("/system/bin/dumpsys bluetooth_manager 2>/dev/null", "r");
    if (!f) return false;
    char line[768];
    bool inA2dp = false, active = false;
    while (fgets(line, sizeof line, f)) {
        char* p;
        if ((p = strstr(line, "Profile:"))) {
            inA2dp = (strstr(p, "A2dpService") != nullptr);
            continue;
        }
        if (inA2dp && (p = strstr(line, "mActiveDevice:"))) {
            p += strlen("mActiveDevice:");
            while (*p == ' ' || *p == '\t') ++p;
            chomp(p);
            active = (*p != '\0' && strncmp(p, "null", 4) != 0);
            break;   // first mActiveDevice under A2dpService is the one we want
        }
    }
    pclose(f);
    return active;
}

// Is any audio actively playing? Parse `dumpsys audio` for an active playback
// configuration in the started state. (The event-history lines say "event:started",
// not "AudioPlaybackConfiguration ... state:started", so they do not match.)
bool audioActive() {
    FILE* f = popen("/system/bin/dumpsys audio 2>/dev/null", "r");
    if (!f) return false;
    char line[1280];
    bool active = false;
    while (fgets(line, sizeof line, f)) {
        if (strstr(line, "AudioPlaybackConfiguration") && strstr(line, "state:started")) {
            active = true;
            break;
        }
    }
    pclose(f);
    return active;
}

// Pin every cpufreq policy to `gov` at the hardware maximum frequency.
void cpuPin(const char* gov) {
    DIR* d = opendir(kCpufreqDir);
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "policy", 6) != 0) continue;
        char p[320], hw[32] = {0};
        snprintf(p, sizeof p, "%s/%s/cpuinfo_max_freq", kCpufreqDir, e->d_name);
        if (readFile(p, hw, sizeof hw)) {
            chomp(hw);
            snprintf(p, sizeof p, "%s/%s/scaling_max_freq", kCpufreqDir, e->d_name);
            writeFile(p, hw);
        }
        snprintf(p, sizeof p, "%s/%s/scaling_governor", kCpufreqDir, e->d_name);
        writeFile(p, gov);
    }
    closedir(d);
}

bool govIsPerformance() {
    char g[32] = {0};
    if (!readFile(kPolicy0Gov, g, sizeof g)) return false;
    return strncmp(g, "performance", 11) == 0;
}

// Hand the CPU back to whoever owns it when BT audio is not active: nano's
// screen-off powersave if the screen is currently off, otherwise the user's
// persisted performance mode. Uses the same validated setclock scripts nano does,
// so the governor / min / max are fully restored (cpuPin only touched gov + max).
void cpuRestore() {
    char screenOff[PROPERTY_VALUE_MAX] = {0};
    property_get(kPropScreenOff, screenOff, "0");
    const char* m;
    if (screenOff[0] == '1') {
        m = "powersave";
    } else {
        char mode[PROPERTY_VALUE_MAX] = {0};
        property_get(kPropPerfMode, mode, "stock");
        if (!strcmp(mode, "max"))            m = "max";
        else if (!strcmp(mode, "powersave")) m = "powersave";
        else                                 m = "stock";
    }
    char cmd[96];
    snprintf(cmd, sizeof cmd, "/vendor/bin/setclock_%s.sh", m);   // mode validated above; no injection
    system(cmd);
}

void monitorLoop() {
    setpriority(PRIO_PROCESS, 0, 10);   // be polite; this is a ~1 Hz housekeeping loop
    pthread_setname_np(pthread_self(), "nano-btstable");

    int lockFd = -1;
    bool haveLock = false;

    int routeCtr = 0;  bool routeCached = false;   // throttled dumpsys caches
    int audioCtr = 0;  bool audioCached = false;

    for (;;) {
        // Re-read the kill-switch every loop so a runtime toggle takes effect.
        if (!property_get_bool(kPropEnable, true)) {
            if (haveLock) {
                if (property_get_bool(kPropActive, false)) {
                    cpuRestore();
                    property_set(kPropActive, "0");
                }
                flock(lockFd, LOCK_UN);
                haveLock = false;
            }
            sleep(3);
            continue;
        }

        // Elect a single active monitor across the two nano processes. The holder
        // keeps the lock; if it dies the kernel releases the lock and the other
        // process takes over (and reconciles state from the shared sys-props).
        if (lockFd < 0)
            lockFd = open(kLockFile, O_CREAT | O_RDWR | O_CLOEXEC, 0600);
        if (!haveLock) {
            if (lockFd >= 0 && flock(lockFd, LOCK_EX | LOCK_NB) == 0) {
                haveLock = true;
                routeCtr = audioCtr = 0;   // force a fresh probe on takeover
            } else {
                sleep(2);
                continue;
            }
        }

        const int bt = btRadioState();
        bool routeActive = false, audioOn = false;
        if (bt == 1) {
            if (routeCtr <= 0) { routeCached = a2dpRouteActive(); routeCtr = kRoutePollSec; }
            else               { routeCtr -= kTickSec; }
            routeActive = routeCached;
            if (routeActive) {
                if (audioCtr <= 0) { audioCached = audioActive(); audioCtr = kAudioPollSec; }
                else               { audioCtr -= kTickSec; }
                audioOn = audioCached;
            } else {
                audioCtr = 0; audioCached = false;
            }
        } else {
            routeCtr = audioCtr = 0; routeCached = audioCached = false;
        }

        // ---- Tier 1: keep the BT controller awake while A2DP is the route ----
        // Only touch lpm while the radio is ON; BT-off lpm is nano's suspend domain.
        if (bt == 1) {
            const int wantLpm = routeActive ? 0 : 1;
            const int cur = lpmGet();
            if (cur != -1 && cur != wantLpm) lpmSet(wantLpm);
        }

        // ---- Tier 2: pin the CPU while audio is actually streaming to A2DP ----
        // State lives in a sys-prop so it survives a process death / lock takeover.
        const bool cpuWant = routeActive && audioOn;
        const bool cpuOn   = property_get_bool(kPropActive, false);
        if (cpuWant && !cpuOn) {
            cpuPin("performance");
            property_set(kPropActive, "1");
            ALOGI("btstable: BT A2DP audio active -> CPU performance@max + lpm=0");
        } else if (!cpuWant && cpuOn) {
            cpuRestore();
            property_set(kPropActive, "0");
            ALOGI("btstable: BT A2DP audio idle -> restored CPU governor");
        } else if (cpuWant && cpuOn) {
            // Re-assert over nano's one-shot screen-off powersave (and anything else
            // that may have changed the governor underneath us).
            if (!govIsPerformance()) cpuPin("performance");
        }

        sleep(kTickSec);
    }
}

} // namespace

void nanoStartBtStability() {
    static bool started = false;
    if (started) return;
    started = true;
    std::thread(monitorLoop).detach();
    ALOGI("btstable: A2DP stability monitor started");
}

} // namespace android
