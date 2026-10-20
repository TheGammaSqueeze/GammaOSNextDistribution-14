/*
 * Session-wide hardware state shared by drastic-nano and azahar-nano.
 *
 * core_ctl hold: Qualcomm core_ctl pauses cores of a cluster it judges idle (down to min_cpus).
 * On the RP Duo Lite it kept 2 of the 4 big cores paused most of the time during play, and the
 * emulator then sat runnable 5-11 ms behind its worker threads. For a game session every cluster
 * keeps all its cores (min_cpus = max_cpus); the prior values come back on exit and around sleep.
 * Devices without core_ctl (the RG DS line) are untouched.
 *
 * DP pixel clock trim: the RP Duo Lite's DP panel runs on its own pixel clock, so its vblank sits
 * at an arbitrary, slowly drifting offset from the DSI's. The DP pixel clock source takes a new
 * rate live through debugfs (kHz, no modeset, no blank), which moves the DP's vblank against the
 * DSI's: 85 kHz (0.1%) moves it about 1 ms a second. The untrimmed rate is recorded in a property
 * so a session that died with a trim applied still finds the real base next time.
 */
#pragma once

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <unistd.h>

#include <string>

#include <cutils/properties.h>
#include <log/log.h>

namespace android {
namespace nano_session_hw {

inline long readLong(const char* path, long dflt) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return dflt;
    char b[32] = {0};
    ssize_t n = read(fd, b, sizeof(b) - 1);
    close(fd);
    return n > 0 ? strtol(b, nullptr, 10) : dflt;
}

inline bool writeLong(const char* path, long v) {
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char b[32];
    int n = snprintf(b, sizeof(b), "%ld", v);
    bool ok = (write(fd, b, n) == n);
    close(fd);
    return ok;
}

constexpr int kMaxCpus = 16;
inline int gSavedCoreCtlMin[kMaxCpus];
inline bool gCoreCtlHeld = false;

// Raise each core_ctl cluster's min_cpus to its max_cpus (no-op when already held or when
// `disableProp` is set to 0).
inline void holdClusterCores(const char* disableProp) {
    if (gCoreCtlHeld || property_get_int32(disableProp, 1) <= 0) return;
    int held = 0;
    for (int cpu = 0; cpu < kMaxCpus; cpu++) {
        gSavedCoreCtlMin[cpu] = -1;
        char minPath[80], maxPath[80];
        snprintf(minPath, sizeof(minPath), "/sys/devices/system/cpu/cpu%d/core_ctl/min_cpus", cpu);
        snprintf(maxPath, sizeof(maxPath), "/sys/devices/system/cpu/cpu%d/core_ctl/max_cpus", cpu);
        const long mn = readLong(minPath, -1), mx = readLong(maxPath, -1);
        if (mn < 0 || mx <= mn) continue;   // no core_ctl on this cpu, or nothing to hold
        if (!writeLong(minPath, mx)) {
            ALOGW("core_ctl cpu%d min_cpus write failed: %s", cpu, strerror(errno));
            continue;
        }
        gSavedCoreCtlMin[cpu] = (int)mn;
        held++;
        ALOGI("core_ctl cluster of cpu%d keeps all %ld cores (min_cpus %ld -> %ld)", cpu, mx, mn, mx);
    }
    gCoreCtlHeld = held > 0;
}

inline void releaseClusterCores() {
    if (!gCoreCtlHeld) return;
    for (int cpu = 0; cpu < kMaxCpus; cpu++) {
        if (gSavedCoreCtlMin[cpu] < 0) continue;
        char minPath[80];
        snprintf(minPath, sizeof(minPath), "/sys/devices/system/cpu/cpu%d/core_ctl/min_cpus", cpu);
        writeLong(minPath, gSavedCoreCtlMin[cpu]);
        gSavedCoreCtlMin[cpu] = -1;
    }
    gCoreCtlHeld = false;
}

constexpr const char* kDpClkPath = "/sys/kernel/debug/clk/disp_cc_mdss_dp_pixel_clk_src/clk_rate";
constexpr const char* kDpClkBaseProp = "sys.gammaos.nano.dp_clk_base";
inline long gDpClkBase = -1, gDpClkSet = -1;

// True once the DP pixel clock can be trimmed (mounts debugfs when needed).
inline bool dpClkReady() {
    if (gDpClkBase > 0) return true;
    if (access(kDpClkPath, W_OK) != 0) {
        mount("debugfs", "/sys/kernel/debug", "debugfs", 0, nullptr);
        if (access(kDpClkPath, W_OK) != 0) return false;
    }
    const long cur = readLong(kDpClkPath, -1);
    if (cur <= 0) return false;
    // A rate within the trim range of the recorded base is a trim left behind; anything else is a
    // new base (another DP mode was set since).
    const long rec = property_get_int32(kDpClkBaseProp, -1);
    const long base = rec > 0 && labs(cur - rec) <= 100 ? rec : cur;
    property_set(kDpClkBaseProp, std::to_string(base).c_str());
    gDpClkBase = base;
    gDpClkSet = cur;
    return true;
}

// Run the DP pixel clock `khz` above its base (no write unless the rate changes).
inline void dpClkTrim(int khz) {
    if (gDpClkBase <= 0) return;
    const long want = gDpClkBase + khz;
    if (want == gDpClkSet) return;
    if (writeLong(kDpClkPath, want)) gDpClkSet = want;
}

inline void dpClkRestore() {
    if (gDpClkBase > 0 && gDpClkSet != gDpClkBase && writeLong(kDpClkPath, gDpClkBase)) gDpClkSet = gDpClkBase;
}

// Trim choice for a phase error in microseconds (> 0: the DP vblank must come earlier), fed once a
// second. Every write retunes the DP pixel clock, and a retune occasionally costs a vblank: writing
// the clock every second with the lock off gave 3.2 dropped bottom-panel frames a minute against 0.
// So the trim takes only three levels and changes as rarely as it can: 85 kHz (0.1%, ~1 ms a
// second) to close a large error, 25 kHz to settle, and the untrimmed clock once the smoothed error
// is under 0.3 ms, kept until it passes 0.7 ms (the untrimmed DP drifts ~3 us a second against the
// DSI, so a held lock writes about once in a few minutes). The error is smoothed over a few seconds
// so second-to-second noise in the measurement does not flip the level.
struct DpClkLock {
    double ema = 0.0;
    bool primed = false;
    int trim = 0;
    int step(int errUs) {
        if (!primed) { ema = errUs; primed = true; }
        else ema += (errUs - ema) * 0.25;
        const double a = ema < 0 ? -ema : ema;
        const int sign = ema < 0 ? -1 : 1;
        if (a > 2500.0) trim = 85 * sign;
        else if (a > 700.0) trim = 25 * sign;
        else if (a < 300.0) trim = 0;
        else if (trim != 0) trim = 25 * sign;   // between the bands: keep settling, never back to 85
        return trim;
    }
};

}  // namespace nano_session_hw
}  // namespace android
