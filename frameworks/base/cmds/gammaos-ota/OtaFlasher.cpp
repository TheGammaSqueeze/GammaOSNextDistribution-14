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

#define LOG_TAG "GammaOSOta"

#include "OtaFlasher.h"
#include "XzDecompressor.h"

#include <liblp/liblp.h>
#include <unordered_map>
#include <inttypes.h>
#include <limits.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/sysmacros.h>
#include <sys/reboot.h>
#include <sys/mount.h>
#include <sys/statvfs.h>
#include <sys/mman.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <algorithm>
#include <set>
#include <linux/fs.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <stdarg.h>
#include <time.h>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <cutils/properties.h>
#include <utils/Log.h>

extern char** environ;

namespace android {

// Libraries that must be staged to tmpfs before flashing
const std::vector<std::string> OtaFlasher::REQUIRED_SYSTEM_LIBS = {
    "libbase.so", "liblog.so", "liblp.so", "libsparse.so", "libfs_mgr.so",
    "libutils.so", "libcutils.so", "libhidlbase.so", "libc++.so",
    "libcrypto.so", "libcrypto_utils.so", "libext4_utils.so", "libz.so",
    "libfec.so", "libselinux.so", "libvndksupport.so", "libext2_uuid.so",
    "libsquashfs_utils.so", "libpcre2.so", "libpackagelistparser.so",
    "android.hardware.boot@1.0.so", "android.hardware.boot@1.1.so",
    "libdm.so", "libbinder.so", "libui.so", "libgui.so",
    "libEGL.so", "libGLESv2.so", "libft2.so", "liblzma.so",
    "libhardware.so", "libnativewindow.so", "libsync.so",
};

const std::vector<std::string> OtaFlasher::REQUIRED_APEX_LIBS = {
    "/apex/com.android.runtime/lib64/bionic/libc.so",
    "/apex/com.android.runtime/lib64/bionic/libm.so",
    "/apex/com.android.runtime/lib64/bionic/libdl.so",
    "/apex/com.android.runtime/lib64/bionic/libdl_android.so",
};

const std::vector<std::string> OtaFlasher::REQUIRED_BINARIES = {
    "/system/bin/gammaos-ota",
    "/system/bin/lptools",
    "/system/bin/dmctl",
    "/system/bin/lpdump",
    "/system/bin/xz",
    "/system/bin/unzip",
    "/system/bin/sh",
    "/system/bin/toybox",
    "/apex/com.android.runtime/bin/linker64",
};

OtaFlasher::OtaFlasher() : mPackageDir(OTA_DIR) {
    initLogFile();
}

FILE* OtaFlasher::sLogFile = nullptr;

void OtaFlasher::initLogFile() {
    if (sLogFile) return;
    mkdir(OTA_DIR, 0771);
    std::string logPath = std::string(OTA_DIR) + "/ota.log";
    sLogFile = fopen(logPath.c_str(), "a");
    if (sLogFile) {
        setlinebuf(sLogFile);
        logToFile("INFO", "=== GammaOS OTA session started ===");
        // Log device info
        std::string device = android::base::GetProperty("ro.gammaos.device", "unknown");
        std::string build = android::base::GetProperty("ro.build.display.id", "unknown");
        std::string slot = android::base::GetProperty("ro.boot.slot_suffix", "unknown");
        logToFile("INFO", "Device: %s, Build: %s, Slot: %s", device.c_str(), build.c_str(), slot.c_str());
        logToFile("INFO", "Running from tmpfs: %s", isRunningFromTmpfs() ? "yes" : "no");
    }
}

void OtaFlasher::logToFile(const char* level, const char* fmt, ...) {
    if (!sLogFile) return;
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tm;
    localtime_r(&ts.tv_sec, &tm);
    fprintf(sLogFile, "%04d-%02d-%02d %02d:%02d:%02d.%03ld [%s] ",
            tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
            tm.tm_hour, tm.tm_min, tm.tm_sec, ts.tv_nsec / 1000000, level);
    va_list args;
    va_start(args, fmt);
    vfprintf(sLogFile, fmt, args);
    va_end(args);
    fprintf(sLogFile, "\n");
}

bool OtaFlasher::isRunningFromTmpfs() {
    return getenv("GAMMAOS_OTA_STAGED") != nullptr;
}

bool OtaFlasher::stageToTmpfs(int argc, char** argv) {
    if (isRunningFromTmpfs()) {
        ALOGI("Already running from tmpfs, skipping staging");
        return true;
    }

    ALOGI("Staging OTA binaries to tmpfs...");
    logToFile("INFO", "=== STAGING TO TMPFS ===");

    // Create staging directories
    mkdir(STAGE_DIR, 0755);
    std::string binDir = std::string(STAGE_DIR) + "/bin";
    std::string libDir = std::string(STAGE_DIR) + "/lib64";
    std::string fontDir = std::string(STAGE_DIR) + "/fonts";
    mkdir(binDir.c_str(), 0755);
    mkdir(libDir.c_str(), 0755);
    mkdir(fontDir.c_str(), 0755);

    // Helper: copy file using cp command (handles symlinks, large files, SELinux)
    auto copyFile = [](const std::string& src, const std::string& dst) -> bool {
        std::string cmd = "cp -f " + src + " " + dst + " 2>/dev/null";
        int ret = system(cmd.c_str());
        if (ret != 0) {
            // Try resolving symlinks
            char resolved[PATH_MAX];
            if (realpath(src.c_str(), resolved)) {
                cmd = "cp -f " + std::string(resolved) + " " + dst + " 2>/dev/null";
                ret = system(cmd.c_str());
            }
        }
        if (ret == 0) chmod(dst.c_str(), 0755);
        return ret == 0;
    };

    // Count the files up front so we can report REAL staging progress to nano's "Preparing
    // update... X%" screen while we do the slow (~60s) copy - the panel is never black.
    int totalStageFiles = 0;
    { DIR* d = opendir("/system/bin"); if (d) { struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            if (e->d_type != DT_REG && e->d_type != DT_LNK) continue;
            totalStageFiles++;
        } closedir(d); } }
    { DIR* d = opendir("/system/lib64"); if (d) { struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            totalStageFiles++;
        } closedir(d); } }
    if (totalStageFiles < 1) totalStageFiles = 1;
    int stageDone = 0;
    android::base::SetProperty("sys.gammaos.ota.stageprog", "0");
    auto reportStage = [&]() {
        stageDone++;
        if (stageDone % 12 == 0)
            android::base::SetProperty("sys.gammaos.ota.stageprog",
                                       std::to_string(stageDone * 95 / totalStageFiles));
    };

    // Copy ALL binaries from /system/bin/ to tmpfs
    // This ensures nothing is left demand-paging from the system partition
    logToFile("INFO", "Copying ALL binaries from /system/bin/ ...");
    {
        DIR* dir = opendir("/system/bin");
        int binCount = 0;
        if (dir) {
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                if (entry->d_type != DT_REG && entry->d_type != DT_LNK) continue;
                std::string src = "/system/bin/" + std::string(entry->d_name);
                std::string dst = binDir + "/" + entry->d_name;
                if (copyFile(src, dst)) binCount++;
                reportStage();
            }
            closedir(dir);
        }
        logToFile("INFO", "Staged %d binaries from /system/bin/", binCount);
    }

    // Copy ALL libs from /system/lib64/ to tmpfs
    logToFile("INFO", "Copying ALL libs from /system/lib64/ ...");
    {
        DIR* dir = opendir("/system/lib64");
        int libCount = 0;
        if (dir) {
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                std::string src = "/system/lib64/" + std::string(entry->d_name);
                std::string dst = libDir + "/" + entry->d_name;
                if (copyFile(src, dst)) libCount++;
                reportStage();
            }
            closedir(dir);
        }
        logToFile("INFO", "Staged %d libs from /system/lib64/", libCount);
    }

    // Copy apex bionic libs (these are outside /system/lib64/)
    for (const auto& src : REQUIRED_APEX_LIBS) {
        std::string name = src.substr(src.rfind('/') + 1);
        std::string dst = libDir + "/" + name;
        if (!copyFile(src, dst)) {
            ALOGE("Failed to stage critical lib %s", src.c_str());
            logToFile("ERROR", "CRITICAL: Failed to stage apex lib: %s", src.c_str());
            return false;
        }
        logToFile("INFO", "Staged apex lib: %s", src.c_str());
    }

    // Copy font for UI rendering
    copyFile("/system/fonts/Roboto-Regular.ttf", fontDir + "/Roboto-Regular.ttf");
    logToFile("INFO", "Staged font: Roboto-Regular.ttf");

    ALOGI("Staging complete. Re-exec from tmpfs...");
    logToFile("INFO", "Staging complete — re-exec from tmpfs");

    // Build new argv
    std::string newExe = binDir + "/gammaos-ota";
    std::vector<char*> newArgv;
    newArgv.push_back(const_cast<char*>(newExe.c_str()));
    for (int i = 1; i < argc; i++) {
        newArgv.push_back(argv[i]);
    }
    newArgv.push_back(nullptr);

    // Set environment. LD_LIBRARY_PATH must include the staged libDir first
    // (so the re-exec'd binary finds its bionic + system libs even after the
    // real /system gets bind-mounted over during flash), but ALSO the standard
    // /system and /vendor paths so that libui.so's gralloc mapper fallback can
    // still dlopen device-specific HIDL/AIDL impls from /vendor/lib64/hw/ at
    // preflight time. Without the vendor path, libui aborts with
    // "gralloc-mapper is missing" on devices that ship HIDL mapper@4.0 impls.
    std::string ldPath = libDir + ":/system/lib64:/system/lib64/hw:"
                         "/vendor/lib64:/vendor/lib64/hw";
    setenv("LD_LIBRARY_PATH", ldPath.c_str(), 1);
    setenv("GAMMAOS_OTA_STAGED", "1", 1);
    setenv("GAMMAOS_OTA_FONT_DIR", fontDir.c_str(), 1);

    // Re-exec from tmpfs
    execv(newExe.c_str(), newArgv.data());

    // If we get here, execv failed
    ALOGE("execv from tmpfs failed: %s", strerror(errno));
    return false;
}

std::string OtaFlasher::getSlotSuffix() {
    // Non-A/B devices may have ro.boot.slot_suffix unset entirely — defaulting
    // to "_a" turns partition names like "system" into "system_a" which don't
    // exist, so dm lookups return empty and size accounting breaks. Gate on
    // ro.build.ab_update so non-A/B always returns "".
    bool isAb = android::base::GetBoolProperty("ro.build.ab_update", false);
    if (!isAb) {
        return "";
    }
    return android::base::GetProperty("ro.boot.slot_suffix", "_a");
}

void OtaFlasher::notifyStatus(FlashPhase phase, const std::string& partition,
                               int idx, int count, int progress,
                               const std::string& error) {
    if (mCallback) {
        FlashStatus s;
        s.phase = phase;
        s.currentPartition = partition;
        s.partitionIndex = idx;
        s.partitionCount = count;
        s.progressPercent = progress;
        s.errorMsg = error;
        if (mPlanTotal > 0) {
            mStepFrac = std::min(100, std::max(0, progress)) / 100.0;
            const double pos = mWorkDone + mStepWeight * mStepFrac;
            const double span = mPlanTotal - mAnchorWork;
            const double v = span > 0 ? mAnchorShown + (1.0 - mAnchorShown) * (pos - mAnchorWork) / span
                                      : mAnchorShown;
            if (v > mOverall) mOverall = v;
            // 100% is shown only once everything has been proven, not when the last step's
            // counter happens to reach its end.
            if (!mProgressDone && mOverall > 0.99) mOverall = 0.99;
            s.overallPermille = (int)(mOverall * 1000.0 + 0.5);
            s.redoing = mStepRedo;
        }
        mCallback(s);
    }
}

std::vector<std::string> OtaFlasher::physicalDevices(const std::string& name) {
    // Both slots on A/B, the single partition otherwise (the devices flashPhysical writes).
    std::vector<std::string> devs;
    for (const char* sfx : { "_a", "_b" }) {
        std::string d = "/dev/block/by-name/" + name + sfx;
        if (access(d.c_str(), F_OK) == 0) devs.push_back(d);
    }
    if (devs.empty()) devs.push_back("/dev/block/by-name/" + name);
    return devs;
}

void OtaFlasher::planProgress(const OtaManifest& manifest, bool withBackup) {
    // Seconds per MiB of each kind of step, measured on an RG DS Plus (system on the SD card,
    // 2 GB EROFS system image): what matters is their ratio, so the bar moves at an even pace
    // whatever the step, and a 2 GB system counts for more than a 100 MB recovery.
    constexpr double kHashPackage = 0.022;   // preflight: SHA-256 of each compressed file
    constexpr double kDecompress  = 0.050;   // xz to the staging file on /data
    constexpr double kCheck       = 0.022;   // reading the staging copy back
    constexpr double kWrite       = 0.055;   // writing a partition
    constexpr double kReadBack    = 0.026;   // reading it back past the page cache
    constexpr double kSaveOrig    = 0.100;   // saving a physical partition (copy and two hashes)
    constexpr double kBackup      = 0.080;   // the optional full backup (dd)
    constexpr double kStop        = 4.0;     // stopping the framework and pinning, in seconds
    constexpr double kMiB = 1.0 / (1 << 20);

    mPlan.clear();
    mPlanSeen.clear();
    mOverall = mWorkDone = mStepWeight = mStepFrac = mAnchorShown = mAnchorWork = 0;
    mProgressDone = false;
    double t = 0;
    auto add = [&](const std::string& key, double w) {
        if (mPlan.count(key)) return;
        mPlan[key] = w;
        t += w;
    };
    double compressed = 0;
    for (const auto& part : manifest.partitions) {
        struct stat st;
        if (stat((mPackageDir + "/" + part.file).c_str(), &st) == 0) compressed += (double)st.st_size;
    }
    add("preflight", 1.0 + compressed * kMiB * kHashPackage);
    if (withBackup)
        for (const auto& part : manifest.partitions) add("backup:" + part.name, part.size * kMiB * kBackup);
    add("stop", kStop);
    for (const auto* part : manifest.physicalPartitions()) {
        const double mb = part->size * kMiB;
        add("dec:" + part->name, mb * kDecompress);
        add("check:" + part->name, mb * kCheck);
        for (const auto& dev : physicalDevices(part->name)) {
            add("save:" + dev, mb * kSaveOrig);
            add("write:" + dev, mb * kWrite);
            add("read:" + dev, mb * kReadBack);
        }
    }
    for (const auto* part : manifest.logicalPartitions()) {
        const double mb = part->size * kMiB;
        add("dec:" + part->name, mb * kDecompress);
        add("check:" + part->name, mb * kCheck);
        add("write:" + part->name, mb * kWrite);
        add("read:" + part->name, mb * kReadBack);
    }
    for (const auto& part : manifest.partitions) {
        const size_t copies = part.type == "logical" ? 1 : physicalDevices(part.name).size();
        add("verify:" + part.name, part.size * kMiB * kReadBack * copies);
    }
    mPlanTotal = t;
    logToFile("INFO", "Progress plan: %zu steps, about %.0f s of work", mPlan.size(), t);
}

// From here on the unfilled part of the bar covers the work left, whatever the plan now says:
// shown = anchor + (1 - anchor) * (work since the anchor) / (work left at the anchor).
void OtaFlasher::progressReanchor() {
    mAnchorShown = mOverall;
    mAnchorWork = mWorkDone + mStepWeight * mStepFrac;
}

void OtaFlasher::progressStage(const std::string& key) {
    if (mPlanTotal <= 0) return;
    mWorkDone += mStepWeight;   // the step before this one is over (done, or given up on)
    mStepWeight = mStepFrac = 0;
    mStepRedo = false;
    auto it = mPlan.find(key);
    if (it == mPlan.end()) return;
    if (!mPlanSeen.insert(key).second) {
        // Done before: this is a retry or a repair, work on top of the plan. Late in the update
        // there is little bar left to stretch over it, so the screen also shows the step's own
        // percentage while it runs.
        progressReanchor();
        mPlanTotal += it->second;
        mStepRedo = true;
    }
    mStepWeight = it->second;
}

void OtaFlasher::progressSkip(const std::string& key) {
    auto it = mPlan.find(key);
    if (mPlanTotal <= 0 || it == mPlan.end() || !mPlanSeen.insert(key).second) return;
    progressReanchor();
    mPlanTotal -= it->second;
}

void OtaFlasher::finishProgress() {
    mProgressDone = true;
    mOverall = 1.0;
}

std::string OtaFlasher::preflight(const OtaManifest& manifest) {
    progressStage("preflight");
    notifyStatus(FlashPhase::PREFLIGHT);
    logToFile("INFO", "=== PREFLIGHT CHECKS ===");
    logToFile("INFO", "Manifest: version=%s, version_code=%d, partitions=%zu",
              manifest.version.c_str(), manifest.versionCode, manifest.partitions.size());

    // Check device compatibility
    std::string device = android::base::GetProperty("ro.gammaos.device", "");
    logToFile("INFO", "Device check: ro.gammaos.device='%s'", device.c_str());
    if (!manifest.isDeviceCompatible(device)) {
        std::string err = "Device '" + device + "' not compatible with this update";
        logToFile("ERROR", "PREFLIGHT FAIL: %s", err.c_str());
        return err;
    }
    logToFile("INFO", "Device compatible: OK");

    // Battery/charger preflight check removed: sysfs node names
    // (/sys/class/power_supply/battery, .../ac, .../usb) vary per SoC, and a
    // false-negative here silently aborts the flash. Rely on the manifest's
    // min_battery field at package-build time instead of enforcing on-device.
    logToFile("INFO", "Battery check: skipped (not enforced on-device)");

    // Verify compressed file checksums
    logToFile("INFO", "Verifying compressed file checksums...");
    uint64_t hashTotal = 0, hashDone = 0;
    for (const auto& part : manifest.partitions) {
        struct stat st;
        if (!part.sha256.empty() && stat((mPackageDir + "/" + part.file).c_str(), &st) == 0)
            hashTotal += (uint64_t)st.st_size;
    }
    for (const auto& part : manifest.partitions) {
        std::string path = mPackageDir + "/" + part.file;
        logToFile("INFO", "  Partition: name=%s type=%s size=%llu file=%s",
                  part.name.c_str(), part.type.c_str(),
                  (unsigned long long)part.size, part.file.c_str());
        if (access(path.c_str(), R_OK) != 0) {
            std::string err = "Missing partition image: " + part.file;
            logToFile("ERROR", "PREFLIGHT FAIL: %s (path: %s)", err.c_str(), path.c_str());
            return err;
        }
        struct stat st;
        stat(path.c_str(), &st);
        logToFile("INFO", "  File exists: %s (%lld bytes)", path.c_str(), (long long)st.st_size);
        if (!part.sha256.empty()) {
            logToFile("INFO", "  Computing SHA-256 for %s...", part.file.c_str());
            std::string hash = XzDecompressor::sha256File(path, 0, [&](uint64_t r, uint64_t) {
                notifyStatus(FlashPhase::PREFLIGHT, part.name, 0, 0,
                             hashTotal ? (int)((hashDone + r) * 100 / hashTotal) : 0);
            });
            hashDone += (uint64_t)st.st_size;
            logToFile("INFO", "  SHA-256: expected=%s got=%s", part.sha256.c_str(), hash.c_str());
            if (hash != part.sha256) {
                std::string err = "Checksum mismatch for " + part.file +
                       " (expected " + part.sha256.substr(0, 12) + "..." +
                       " got " + hash.substr(0, 12) + "...)";
                logToFile("ERROR", "PREFLIGHT FAIL: %s", err.c_str());
                return err;
            }
        }
    }

    // Check super free space for logical partition size increases.
    // On non-A/B (single slot), partitions are replaced in place: the old
    // allocation will be freed before the new one is allocated, so the usable
    // budget is (free + sum(current sizes of partitions being replaced)), not
    // just (free). Comparing growth delta against raw free space falsely
    // rejects updates where the new image fits after reclaiming the old
    // partition's extents.
    auto logicals = manifest.logicalPartitions();
    if (!logicals.empty()) {
        std::string freeOutput;
        execCommand("lptools free", &freeOutput);
        logToFile("INFO", "lptools free output: %s", freeOutput.c_str());
        // Parse "Free space: NNNN"
        auto pos = freeOutput.find("Free space:");
        uint64_t freeSpace = 0;
        if (pos != std::string::npos) {
            freeSpace = strtoull(freeOutput.c_str() + pos + 11, nullptr, 10);
        }

        std::string slot = getSlotSuffix();
        uint64_t totalCurrent = 0;
        uint64_t totalTarget = 0;
        for (const auto* part : logicals) {
            std::string dmName = part->name + slot;
            std::string dmPath = getDmDevPath(dmName);
            uint64_t currentSize = getBlockDevSize(dmPath);
            logToFile("INFO", "  Logical %s: current=%llu target=%llu delta=%+lld",
                      dmName.c_str(), (unsigned long long)currentSize,
                      (unsigned long long)part->size,
                      (long long)(part->size - currentSize));
            totalCurrent += currentSize;
            totalTarget += part->size;
        }
        uint64_t budget = freeSpace + totalCurrent;
        logToFile("INFO",
                  "Super budget: free=%llu + current=%llu = %llu bytes, "
                  "target total=%llu bytes",
                  (unsigned long long)freeSpace, (unsigned long long)totalCurrent,
                  (unsigned long long)budget, (unsigned long long)totalTarget);
        if (totalTarget > budget) {
            std::string err = "Not enough space in super partition (need " +
                   std::to_string(totalTarget / 1024 / 1024) + "MB, have " +
                   std::to_string(budget / 1024 / 1024) +
                   "MB usable after reclaiming current partitions)";
            logToFile("ERROR", "PREFLIGHT FAIL: %s", err.c_str());
            return err;
        }
    }

    // Check /data free space for staging decompressed images
    struct statvfs dataFs;
    if (statvfs("/data", &dataFs) == 0) {
        uint64_t dataFree = (uint64_t)dataFs.f_bavail * dataFs.f_bsize;
        // Find the largest partition image size (that's how much staging space we need)
        uint64_t maxPartSize = 0;
        for (const auto& part : manifest.partitions) {
            if (part.size > maxPartSize) maxPartSize = part.size;
        }
        logToFile("INFO", "/data free space: %llu bytes, largest partition: %llu bytes",
                  (unsigned long long)dataFree, (unsigned long long)maxPartSize);
        // The largest image is staged at a time, and the original contents of every physical
        // partition are kept until the update is proven (two copies on A/B), plus a margin.
        uint64_t originals = 0;
        for (const auto& part : manifest.partitions) {
            if (part.type == "logical") continue;
            const bool ab = access(("/dev/block/by-name/" + part.name + "_a").c_str(), F_OK) == 0;
            originals += part.size * (ab ? 2 : 1);
        }
        logToFile("INFO", "Space for saved physical originals: %llu bytes", (unsigned long long)originals);
        uint64_t needed = maxPartSize + originals + (100 * 1024 * 1024);
        if (dataFree < needed) {
            std::string err = "Not enough space on /data for staging (need " +
                   std::to_string(needed / 1024 / 1024) + "MB, have " +
                   std::to_string(dataFree / 1024 / 1024) + "MB free)";
            logToFile("ERROR", "PREFLIGHT FAIL: %s", err.c_str());
            return err;
        }
    }

    logToFile("INFO", "=== PREFLIGHT PASSED ===");
    return ""; // all checks passed
}

bool OtaFlasher::backup(const OtaManifest& manifest) {
    notifyStatus(FlashPhase::BACKUP);
    ALOGI("Starting backup...");
    logToFile("INFO", "=== BACKUP STARTED ===");

    mkdir(BACKUP_DIR, 0755);

    std::string slot = getSlotSuffix();
    int idx = 0;
    int count = (int)manifest.partitions.size();
    logToFile("INFO", "Backing up %d partitions, slot=%s", count, slot.c_str());

    for (const auto& part : manifest.partitions) {
        progressStage("backup:" + part.name);
        notifyStatus(FlashPhase::BACKUP, part.name, idx, count, 0);

        std::string srcPath;
        if (part.type == "logical") {
            std::string dmName = part.name + slot;
            srcPath = getDmDevPath(dmName);
            if (srcPath.empty() && !slot.empty()) {
                dmName = part.name;
                srcPath = getDmDevPath(dmName);
            }
        } else {
            // Check A/B first, then non-A/B
            std::string slotPath = "/dev/block/by-name/" + part.name + slot;
            std::string noSlotPath = "/dev/block/by-name/" + part.name;
            srcPath = (access(slotPath.c_str(), F_OK) == 0) ? slotPath : noSlotPath;
        }

        // Just dd the raw partition to backup dir (no compression for speed)
        uint64_t size = getBlockDevSize(srcPath);
        std::string dstPath = std::string(BACKUP_DIR) + "/" + part.name + ".img";

        logToFile("INFO", "Backup %s: %s -> %s (%llu bytes)",
                  part.name.c_str(), srcPath.c_str(), dstPath.c_str(), (unsigned long long)size);

        std::string cmd = "dd if=" + srcPath + " of=" + dstPath +
                          " bs=1048576 count=" + std::to_string(size / 1048576 + 1) +
                          " 2>/dev/null";
        if (!execCommand(cmd, nullptr, 3600)) {
            ALOGE("Backup failed for %s", part.name.c_str());
            logToFile("ERROR", "Backup FAILED for %s", part.name.c_str());
            return false;
        }
        logToFile("INFO", "Backup %s: complete", part.name.c_str());

        notifyStatus(FlashPhase::BACKUP, part.name, idx, count, 100);
        idx++;
    }

    // Save state
    std::string stateJson = "{\n  \"slot_suffix\": \"" + slot + "\",\n  \"partitions\": [\n";
    for (size_t i = 0; i < manifest.partitions.size(); i++) {
        const auto& part = manifest.partitions[i];
        std::string dmName = part.name + slot;
        if (part.type == "logical") {
            std::string dmPath = getDmDevPath(dmName);
            if (dmPath.empty() && !slot.empty()) dmName = part.name;
            std::string tableOut;
            execCommand("dmctl table " + dmName, &tableOut);
            uint64_t size = getBlockDevSize(getDmDevPath(dmName));
            stateJson += "    {\"name\": \"" + dmName + "\", \"size\": " +
                         std::to_string(size) + ", \"type\": \"logical\"}";
            logToFile("INFO", "Backup state: %s size=%llu type=logical", dmName.c_str(), (unsigned long long)size);
        } else {
            stateJson += "    {\"name\": \"" + part.name + "\", \"type\": \"physical\"}";
            logToFile("INFO", "Backup state: %s type=physical", part.name.c_str());
        }
        if (i + 1 < manifest.partitions.size()) stateJson += ",";
        stateJson += "\n";
    }
    stateJson += "  ]\n}\n";
    android::base::WriteStringToFile(stateJson, std::string(BACKUP_DIR) + "/pre_ota_state.json");
    logToFile("INFO", "Backup state saved to pre_ota_state.json");

    ALOGI("Backup complete");
    logToFile("INFO", "=== BACKUP COMPLETE ===");
    return true;
}

void OtaFlasher::stopFramework() {
    ALOGI("Stopping framework...");
    logToFile("INFO", "=== STOPPING FRAMEWORK ===");

    // Stop zygote but KEEP SurfaceFlinger running for progress display.
    // SurfaceFlinger is already loaded in memory from /system — the bind-mount
    // over /system/lib64 won't affect it since its libs are already mapped.
    // Keeping SF alive means our EGL surface (OtaMenu) continues to render
    // the progress bar through the HWC pipeline, which works universally.
    logToFile("INFO", "Stopping zygote (keeping SurfaceFlinger for progress display)...");
    property_set("ctl.stop", "zygote");
    usleep(500000); // 500ms for zygote to stop

    // Keep every running process's code resident before system and vendor are rewritten (and
    // before /vendor is covered by a tmpfs below, which would hide the files to pin).
    pinRunningCode();

    // Bind-mount tmpfs directories over /system paths so nothing reads from
    // the system block device during the flash. This prevents kernel page cache
    // conflicts that cause crashes when the block device content changes.
    logToFile("INFO", "Bind-mounting tmpfs over /system and /vendor paths...");
    std::string binDir = std::string(STAGE_DIR) + "/bin";
    std::string libDir = std::string(STAGE_DIR) + "/lib64";
    // mount --bind our staged tmpfs dirs over the system dirs
    execCommand("mount --bind " + binDir + " /system/bin");
    logToFile("INFO", "  Bind-mounted %s -> /system/bin", binDir.c_str());
    execCommand("mount --bind " + libDir + " /system/lib64");
    logToFile("INFO", "  Bind-mounted %s -> /system/lib64", libDir.c_str());

    // Also bind-mount tmpfs over /vendor to prevent page cache conflicts
    // when writing to the vendor partition. Without this, writing a large
    // image to the vendor block device while /vendor is mounted can cause
    // kernel page cache conflicts → OOM/crash.
    execCommand("mount -t tmpfs tmpfs /vendor");
    logToFile("INFO", "  Mounted tmpfs over /vendor");

    logToFile("INFO", "Syncing filesystems...");
    sync();
    // Note: we deliberately do NOT drop_caches=3 here. It evicts every
    // running daemon's clean mmap'd text pages backed by /system, and any
    // subsequent page-fault during the flash would re-read the (by then
    // overwritten) dm device — vold crashing on garbage code triggers
    // init's reboot_on_failure and force-reboots mid-write. The write loop
    // uses O_DIRECT, so dirty page accumulation is not a concern.

    // Display progress is handled by OtaMenu's EGL render loop via notifyStatus.
    // SurfaceFlinger is kept alive so EGL rendering works throughout the flash.
    // OtaDisplay (fbdev) is available as a fallback but not used when SF is alive.

    ALOGI("Framework stopped, system paths bind-mounted to tmpfs");
    logToFile("INFO", "Framework stopped, /system/bin and /system/lib64 bind-mounted to tmpfs");
}

void OtaFlasher::dropCaches() {
    android::base::WriteStringToFile("3", "/proc/sys/vm/drop_caches");
}

void OtaFlasher::dumpSuperMetadata(const char* label) {
    auto metadata = android::fs_mgr::ReadMetadata("/dev/block/by-name/super", 0);
    if (!metadata) {
        logToFile("WARN", "  [%s] Failed to read super metadata", label);
        return;
    }
    logToFile("INFO", "  [%s] Super metadata: %zu partitions, %zu extents",
              label, metadata->partitions.size(), metadata->extents.size());
    for (const auto& p : metadata->partitions) {
        uint64_t totalSectors = 0;
        for (uint32_t i = 0; i < p.num_extents; i++) {
            totalSectors += metadata->extents[p.first_extent_index + i].num_sectors;
        }
        logToFile("INFO", "    %s: %llu bytes (%u extents)",
                  p.name, (unsigned long long)(totalSectors * 512), p.num_extents);
    }
}

bool OtaFlasher::flash(const OtaManifest& manifest) {
    logToFile("INFO", "=== FLASH STARTED ===");
    loadFaults();

    // Dump super metadata before flash for diagnostics
    logToFile("INFO", "=== SUPER METADATA BEFORE FLASH ===");
    dumpSuperMetadata("BEFORE");

    auto physicals = manifest.physicalPartitions();
    auto logicals = manifest.logicalPartitions();
    int totalParts = (int)(physicals.size() + logicals.size());
    int idx = 0;
    logToFile("INFO", "Total partitions to flash: %d (physical=%zu, logical=%zu)",
              totalParts, physicals.size(), logicals.size());

    // === PRE-RESIZE PHASE (before stopping framework) ===
    // Resize logical partitions and cache the new extent layout from lpdump
    // while the framework (and lpdump's binder service) is still alive.
    // The actual dmctl replace + write happens after stopping the framework.
    std::string slot = getSlotSuffix();
    for (const auto* part : logicals) {
        std::string dmName = part->name + slot;
        // Try without slot suffix if slotted version doesn't exist
        std::string dmPath = getDmDevPath(dmName);
        if (dmPath.empty()) {
            dmName = part->name;
            dmPath = getDmDevPath(dmName);
        }
        uint64_t currentSize = getBlockDevSize(dmPath);
        if (part->size > currentSize) {
            logToFile("INFO", "PRE-RESIZE: %s needs to grow %llu -> %llu (+%llu bytes)",
                      dmName.c_str(), (unsigned long long)currentSize,
                      (unsigned long long)part->size,
                      (unsigned long long)(part->size - currentSize));

            // Step 1: lptools resize (update super metadata)
            std::string resizeCmd = "lptools resize " + dmName + " " + std::to_string(part->size);
            std::string resizeOut;
            if (!execCommand(resizeCmd, &resizeOut)) {
                logToFile("ERROR", "PRE-RESIZE: lptools resize FAILED for %s: %s",
                          dmName.c_str(), resizeOut.c_str());
                return false;
            }
            logToFile("INFO", "PRE-RESIZE: lptools resize OK for %s", dmName.c_str());

            // Step 2: Read new extent layout directly from super metadata via liblp
            // This is the authoritative source — same as what init uses on reboot.
            std::vector<OtaFlasher::CachedExtent> cachedExtents;
            {
                // The metadata of the slot being updated. This was always slot 0, so on an A/B
                // device running from _b the cached extents (and the writes made through them)
                // belonged to the other slot.
                const uint32_t slotNum = android::fs_mgr::SlotNumberForSlotSuffix(slot);
                auto metadata = android::fs_mgr::ReadMetadata("/dev/block/by-name/super", slotNum);
                if (metadata) {
                    for (const auto& p : metadata->partitions) {
                        if (std::string(p.name) == dmName) {
                            uint64_t dmSector = 0;
                            for (uint32_t i = 0; i < p.num_extents; i++) {
                                const auto& ext = metadata->extents[p.first_extent_index + i];
                                cachedExtents.push_back({
                                    dmSector,
                                    dmSector + ext.num_sectors,
                                    ext.target_data
                                });
                                dmSector += ext.num_sectors;
                            }
                            break;
                        }
                    }
                } else {
                    logToFile("ERROR", "PRE-RESIZE: ReadMetadata failed for super partition");
                }
            }

            logToFile("INFO", "PRE-RESIZE: cached %zu extents for %s from lpdump",
                      cachedExtents.size(), dmName.c_str());
            for (size_t i = 0; i < cachedExtents.size(); i++) {
                logToFile("INFO", "  cached_extent[%zu]: dm=%llu-%llu offset=%llu",
                          i, (unsigned long long)cachedExtents[i].dmStart,
                          (unsigned long long)cachedExtents[i].dmEnd,
                          (unsigned long long)cachedExtents[i].physOffset);
            }

            // Store in a map for use by flashLogical later
            mCachedExtents[dmName] = std::move(cachedExtents);
        }
    }

    // Phase: Stop framework
    progressStage("stop");
    notifyStatus(FlashPhase::STOPPING_FRAMEWORK);
    stopFramework();

    // Phase: Flash physical partitions, as one transaction. Each one is saved first, written,
    // read back and compared. A partition that cannot be written reliably used to be skipped
    // ("non-fatal"), which shipped a half-written boot or uboot with a new system on top: an
    // update that completed and a device that did not boot. Now, if any physical partition fails,
    // every one already written is put back and verified, and the update stops before system or
    // vendor are touched, so the device boots exactly as it did.
    // A previous attempt in this process that could not roll back left its good copies in
    // mUnrestored: carry them over so this attempt uses them. Anything else in the originals
    // directory is from an older run (or a run that ended without cleaning up) and is not
    // trustworthy as "the current contents", so it is dropped to free the space.
    mPhysicalOriginals = mUnrestored;
    mUnrestored.clear();
    if (DIR* d = opendir(ORIGINALS_DIR)) {
        while (struct dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            const std::string f = std::string(ORIGINALS_DIR) + "/" + e->d_name;
            bool keep = false;
            for (const auto& o : mPhysicalOriginals) keep |= (o.file == f);
            if (!keep) unlink(f.c_str());
        }
        closedir(d);
    }
    mLeftUnchanged.clear();
    mExpectedSha.clear();
    for (const auto* part : physicals) {
        logToFile("INFO", "--- Flashing physical partition %d/%d: %s ---",
                  idx + 1, totalParts, part->name.c_str());
        notifyStatus(FlashPhase::FLASHING_PHYSICAL, part->name, idx, totalParts, 0);
        if (!flashPhysical(*part, idx, totalParts)) {
            logToFile("ERROR", "Physical partition %s could not be written reliably: rolling back", part->name.c_str());
            const bool restored = restorePhysicalOriginals();
            if (restored) {
                // Back exactly as before: the copies are no longer needed (100 MB or more on /data).
                for (const auto& o : mPhysicalOriginals) unlink(o.file.c_str());
            } else {
                mUnrestored = mPhysicalOriginals;   // kept for the Retry
            }
            mPhysicalOriginals.clear();
            notifyStatus(FlashPhase::FAILED, part->name, idx, totalParts, 0,
                         restored ? "Could not write " + part->name + " reliably. Nothing was changed; it is safe to restart and try again."
                                  : "Could not write " + part->name + " reliably, and restoring it failed. Do not restart; use Retry.");
            return false;
        }
        logToFile("INFO", "Physical partition %s: DONE", part->name.c_str());
        notifyStatus(FlashPhase::FLASHING_PHYSICAL, part->name, idx, totalParts, 100);
        idx++;
    }

    // Phase: Flash logical partitions (point of no return)
    for (const auto* part : logicals) {
        logToFile("INFO", "--- Flashing logical partition %d/%d: %s (POINT OF NO RETURN) ---",
                  idx + 1, totalParts, part->name.c_str());
        notifyStatus(FlashPhase::FLASHING_LOGICAL, part->name, idx, totalParts, 0);
        if (!flashLogical(*part, idx, totalParts)) {
            logToFile("ERROR", "FLASH FAILED: logical partition %s", part->name.c_str());
            notifyStatus(FlashPhase::FAILED, part->name, idx, totalParts, 0,
                         "Failed to flash " + part->name);
            return false;
        }
        logToFile("INFO", "Logical partition %s: DONE", part->name.c_str());
        notifyStatus(FlashPhase::FLASHING_LOGICAL, part->name, idx, totalParts, 100);
        // No drop_caches here — see writeFileToBlock and stopFramework.
        sync();
        idx++;
    }

    sync();
    logToFile("INFO", "=== FLASH COMPLETE — ALL PARTITIONS WRITTEN ===");

    // Dump super metadata after flash for diagnostics
    logToFile("INFO", "=== SUPER METADATA AFTER FLASH ===");
    dumpSuperMetadata("AFTER");

    // Final verification, every partition read back once more the way the next boot will see
    // it: logical partitions through the super metadata (not the live dm table the writes went
    // through), physical ones by name. Anything that does not match is rewritten and checked
    // again; the device is only rebooted onto an image that has been proven on the medium.
    for (const auto& part : manifest.partitions) {
        if (!takeFault("final:" + part.name)) continue;
        if (part.type == "logical") {
            auto ranges = logicalRanges(part.name);
            if (!ranges.empty()) corruptDevice(ranges[0].device, ranges[0].offset + ranges[0].length / 2);
        } else {
            corruptDevice("/dev/block/by-name/" + part.name, part.size / 2);
        }
    }
    for (int round = 0; ; round++) {
        auto failures = verify(manifest);
        if (failures.empty()) break;
        if (round >= 2) {
            std::string list;
            for (auto& f : failures) list += (list.empty() ? "" : ", ") + f;
            logToFile("ERROR", "Final verification still fails for: %s", list.c_str());
            notifyStatus(FlashPhase::FAILED, failures.front(), 0, totalParts, 0,
                         "The update could not be written reliably (" + list + "). Do not restart; use Retry.");
            return false;
        }
        int ri = 0;
        for (const auto& name : failures) {
            for (const auto& part : manifest.partitions) {
                if (part.name != name) continue;
                logToFile("WARN", "Rewriting %s after a failed verification (round %d)", name.c_str(), round + 1);
                repairPartition(part, ri, (int)failures.size());
            }
            ri++;
        }
    }
    // Every physical partition is new and verified: the saved originals are no longer needed.
    for (const auto& o : mPhysicalOriginals) unlink(o.file.c_str());
    mPhysicalOriginals.clear();
    return true;
}

bool OtaFlasher::flashPhysical(const OtaPartition& part, int partIdx, int partCount) {
    logToFile("INFO", "flashPhysical: %s, file=%s, size=%llu",
              part.name.c_str(), part.file.c_str(), (unsigned long long)part.size);

    // Target devices: both slots on A/B, the single partition otherwise.
    std::vector<std::string> devs;
    for (const char* sfx : { "_a", "_b" }) {
        std::string d = "/dev/block/by-name/" + part.name + sfx;
        if (access(d.c_str(), F_OK) == 0) devs.push_back(d);
    }
    if (devs.empty()) {
        std::string d = "/dev/block/by-name/" + part.name;
        if (access(d.c_str(), F_OK) != 0) {
            logToFile("ERROR", "Partition not found: %s", d.c_str());
            return false;
        }
        devs.push_back(d);
    }

    const std::string stagingFile = std::string(STAGING_DIR) + "/" + part.name + ".img";
    std::string sha;
    uint64_t size = 0;
    if (!prepareStaging(part, stagingFile, partIdx, partCount, &sha, &size)) {
        logToFile("ERROR", "  No good copy of %s could be staged", part.name.c_str());
        return false;
    }
    for (const auto& dev : devs) {
        uint64_t devSize = getBlockDevSize(dev);
        if (devSize < size) {
            logToFile("ERROR", "  %s is %llu bytes, the image is %llu: it does not fit", dev.c_str(),
                      (unsigned long long)devSize, (unsigned long long)size);
            unlink(stagingFile.c_str());
            return false;
        }
    }

    for (const auto& dev : devs) {
        PhysicalOriginal orig;
        // A Retry after a failed rollback: this device holds a half-written image and the good copy
        // is already saved. Use that copy and restore it if this attempt fails too.
        const PhysicalOriginal* kept = nullptr;
        for (const auto& o : mPhysicalOriginals) if (o.device == dev) kept = &o;
        if (kept) {
            logToFile("WARN", "  %s was not restored by the last attempt; using the saved original (%s)",
                      dev.c_str(), kept->sha.c_str());
            orig = *kept;
            mPhysicalOriginals.erase(mPhysicalOriginals.begin() + (kept - mPhysicalOriginals.data()));
        } else {
            progressStage("save:" + dev);
            if (!savePhysicalOriginal(part.name, dev, size, &orig)) {
                unlink(stagingFile.c_str());
                return false;
            }
        }
        if (!kept && orig.sha == sha) {
            // Already this image (an update re-applied, or a retry after a later failure).
            logToFile("INFO", "  %s already holds this image, not rewritten", dev.c_str());
            progressSkip("write:" + dev);
            progressSkip("read:" + dev);
            unlink(orig.file.c_str());
            continue;
        }
        mPhysicalOriginals.push_back(orig);
        WriteResult r = writeVerified(stagingFile, dev, size, sha, part.name, FlashPhase::FLASHING_PHYSICAL,
                                      partIdx, partCount);
        if (r == WriteResult::SourceBad) {
            // The staging copy changed under us: make a new one and try once more.
            if (prepareStaging(part, stagingFile, partIdx, partCount, &sha, &size))
                r = writeVerified(stagingFile, dev, size, sha, part.name, FlashPhase::FLASHING_PHYSICAL,
                                  partIdx, partCount);
        }
        if (r != WriteResult::Ok) {
            const std::string now = XzDecompressor::sha256BlockDev(dev, size);
            if (now == orig.sha) {
                // Nothing reached it: a partition this device write-protects. It still holds its
                // complete original, so it is consistent, just not updated: kept as before
                // (a warning, the update goes on), as the flasher always treated protected
                // partitions.
                logToFile("WARN", "  %s refused every write and is unchanged: left as it was", dev.c_str());
                mPhysicalOriginals.pop_back();
                unlink(orig.file.c_str());
                mLeftUnchanged.insert(part.name);
                continue;
            }
            // Partly written: never leave that behind. The caller restores the original.
            logToFile("ERROR", "  %s could not be written reliably; it now reads %s (original %s)", dev.c_str(),
                      now.c_str(), orig.sha.c_str());
            unlink(stagingFile.c_str());
            return false;
        }
    }
    mExpectedSha[part.name] = sha;
    unlink(stagingFile.c_str());
    return true;
}

bool OtaFlasher::flashLogical(const OtaPartition& part, int partIdx, int partCount) {
    std::string slot = getSlotSuffix();
    // Try with slot suffix first, then without (non-A/B)
    std::string dmName = part.name + slot;
    std::string dmPath = getDmDevPath(dmName);
    if (dmPath.empty() && !slot.empty()) {
        // Slot suffix didn't work — try without (non-A/B device)
        logToFile("INFO", "  dm device '%s' not found, trying without slot suffix", dmName.c_str());
        dmName = part.name;
        dmPath = getDmDevPath(dmName);
    }
    logToFile("INFO", "flashLogical: %s (dm=%s), file=%s, target_size=%llu",
              part.name.c_str(), dmName.c_str(), part.file.c_str(), (unsigned long long)part.size);
    logToFile("INFO", "  dm device path: %s", dmPath.empty() ? "(not found)" : dmPath.c_str());

    if (dmPath.empty()) {
        ALOGE("Could not find dm device for %s", dmName.c_str());
        logToFile("ERROR", "Could not find dm device for %s (tried with and without slot suffix)", dmName.c_str());
        return false;
    }

    uint64_t currentSize = getBlockDevSize(dmPath);
    logToFile("INFO", "  current size: %llu bytes, target size: %llu bytes",
              (unsigned long long)currentSize, (unsigned long long)part.size);

    bool needsGrow = (part.size > currentSize && part.size > 0);
    bool needsShrink = (part.size < currentSize && part.size > 0);

    // GROWING: resize first (expand dm device so write fits), then write
    if (needsGrow) {
        ALOGI("Growing %s: %llu -> %llu bytes",
              dmName.c_str(), (unsigned long long)currentSize,
              (unsigned long long)part.size);
        logToFile("INFO", "  GROW: %llu -> %llu bytes (+%llu)",
                  (unsigned long long)currentSize, (unsigned long long)part.size,
                  (unsigned long long)(part.size - currentSize));
        if (!resizeLogicalPartition(dmName, part.size)) {
            ALOGE("Failed to grow %s", dmName.c_str());
            logToFile("ERROR", "Grow FAILED for %s", dmName.c_str());
            return false;
        }
        // Re-read dm path after resize (might change)
        dmPath = getDmDevPath(dmName);
        logToFile("INFO", "  After grow: dm path=%s, size=%llu",
                  dmPath.c_str(), (unsigned long long)getBlockDevSize(dmPath));
    } else if (needsShrink) {
        logToFile("INFO", "  SHRINK pending: %llu -> %llu bytes (-%llu) — will resize metadata after write",
                  (unsigned long long)currentSize, (unsigned long long)part.size,
                  (unsigned long long)(currentSize - part.size));
    } else {
        logToFile("INFO", "  No resize needed");
    }

    // Android dynamic partitions are created by first-stage init with
    // DM_READONLY_FLAG set (Attributes: readonly in super metadata).
    // Even after BLKROSET clears the block-device ro bit, the underlying
    // dm target still rejects writes with EPERM. Reload the current table
    // via dmctl replace, which creates the dm device without the readonly
    // flag while keeping the exact same extents. This applies to both
    // shrink and same-size cases (grow already remaps via resizeLogicalPartition).
    if (!needsGrow) {
        logToFile("INFO", "  Reloading dm table as rw (clear DM_READONLY_FLAG)...");

        std::string tableOut;
        if (!execCommand("dmctl table " + dmName, &tableOut)) {
            logToFile("ERROR", "dmctl table FAILED for %s", dmName.c_str());
            return false;
        }
        logToFile("INFO", "  Current dm table:\n%s", tableOut.c_str());

        // Parse the table. Each line: "START-END: linear, MAJOR:MINOR OFFSET"
        std::string replaceCmd = "dmctl replace " + dmName;
        std::string devStr;
        int extentCount = 0;
        size_t pos = 0;
        while (pos < tableOut.size()) {
            size_t lineEnd = tableOut.find('\n', pos);
            if (lineEnd == std::string::npos) lineEnd = tableOut.size();
            std::string line = tableOut.substr(pos, lineEnd - pos);
            uint64_t dmStart, dmEnd, physOffset;
            unsigned int major, minor;
            if (sscanf(line.c_str(), "%" SCNu64 "-%" SCNu64 ": linear, %u:%u %" SCNu64,
                       &dmStart, &dmEnd, &major, &minor, &physOffset) == 5) {
                uint64_t sectors = dmEnd - dmStart;
                if (devStr.empty()) {
                    devStr = std::to_string(major) + ":" + std::to_string(minor);
                }
                replaceCmd += " linear " + std::to_string(dmStart) + " " +
                              std::to_string(sectors) + " " + devStr + " " +
                              std::to_string(physOffset);
                extentCount++;
            }
            pos = lineEnd + 1;
        }

        if (extentCount == 0) {
            logToFile("ERROR", "No extents parsed from dm table for %s", dmName.c_str());
            return false;
        }

        logToFile("INFO", "  dmctl replace (rw reload, %d extents): %s",
                  extentCount, replaceCmd.c_str());
        std::string replaceOut;
        if (!execCommand(replaceCmd, &replaceOut)) {
            logToFile("ERROR", "dmctl replace (rw reload) FAILED: %s", replaceOut.c_str());
            return false;
        }
        logToFile("INFO", "  dm table reloaded as rw: %s", replaceOut.c_str());

        // Refresh dm path after reload in case the node changed
        dmPath = getDmDevPath(dmName);
        logToFile("INFO", "  After rw reload: dm path=%s, size=%llu",
                  dmPath.c_str(), (unsigned long long)getBlockDevSize(dmPath));
    }

    // Decompress and prove the staging copy, then write it and prove the write.
    const std::string stagingFile = std::string(STAGING_DIR) + "/" + part.name + ".img";
    std::string sha;
    uint64_t size = 0;
    if (!prepareStaging(part, stagingFile, partIdx, partCount, &sha, &size)) {
        logToFile("ERROR", "  No good copy of %s could be staged", part.name.c_str());
        return false;
    }
    const uint64_t devSize = getBlockDevSize(dmPath);
    if (devSize < size) {
        logToFile("ERROR", "  %s is %llu bytes, the image is %llu: it does not fit", dmPath.c_str(),
                  (unsigned long long)devSize, (unsigned long long)size);
        unlink(stagingFile.c_str());
        return false;
    }

    logToFile("INFO", "  UI: Switching to FLASHING_LOGICAL phase");
    notifyStatus(FlashPhase::FLASHING_LOGICAL, part.name, partIdx, partCount, 0);
    WriteResult r = writeVerified(stagingFile, dmPath, size, sha, part.name, FlashPhase::FLASHING_LOGICAL,
                                  partIdx, partCount);
    if (r == WriteResult::SourceBad && prepareStaging(part, stagingFile, partIdx, partCount, &sha, &size))
        r = writeVerified(stagingFile, dmPath, size, sha, part.name, FlashPhase::FLASHING_LOGICAL, partIdx, partCount);
    unlink(stagingFile.c_str());
    if (r != WriteResult::Ok) {
        logToFile("ERROR", "Write of %s to %s could not be verified after %d attempts", part.name.c_str(),
                  dmPath.c_str(), kWriteAttempts);
        return false;
    }
    mExpectedSha[part.name] = sha;
    logToFile("INFO", "  Flash %s: written and verified, staging cleaned up", dmName.c_str());

    // SHRINKING: resize metadata after write (image already written to larger partition)
    // The smaller image fits in the current partition. After lptools resize, the super metadata
    // reflects the new smaller size. On reboot, init remaps the partition at the correct size.
    // We do NOT shrink the live dm device (dangerous on mounted FS) — just update metadata.
    if (needsShrink) {
        ALOGI("Shrinking %s metadata: %llu -> %llu bytes",
              dmName.c_str(), (unsigned long long)currentSize,
              (unsigned long long)part.size);
        logToFile("INFO", "  SHRINK: updating super metadata to %llu bytes",
                  (unsigned long long)part.size);
        std::string resizeCmd = "lptools resize " + dmName + " " + std::to_string(part.size);
        std::string resizeOut;
        if (!execCommand(resizeCmd, &resizeOut)) {
            ALOGE("lptools resize (shrink) failed for %s: %s", dmName.c_str(), resizeOut.c_str());
            logToFile("ERROR", "Shrink metadata FAILED for %s: %s", dmName.c_str(), resizeOut.c_str());
            // Non-fatal: image is written, partition will just be slightly oversized after reboot
            logToFile("WARN", "  Continuing despite shrink failure — partition will be oversized");
        } else {
            logToFile("INFO", "  Shrink metadata updated: %s", resizeOut.c_str());
        }
    }

    return true;
}

bool OtaFlasher::resizeLogicalPartition(const std::string& dmName, uint64_t newSize) {
    logToFile("INFO", "resizeLogicalPartition: %s -> %llu bytes", dmName.c_str(), (unsigned long long)newSize);

    // Check if lptools resize was already done in the pre-resize phase
    bool alreadyResized = (mCachedExtents.count(dmName) > 0);

    if (!alreadyResized) {
        // Step 1: Update super partition metadata (not pre-resized)
        std::string resizeCmd = "lptools resize " + dmName + " " + std::to_string(newSize);
        logToFile("INFO", "  Step 1: %s", resizeCmd.c_str());
        std::string resizeOut;
        if (!execCommand(resizeCmd, &resizeOut)) {
            ALOGE("lptools resize failed: %s", resizeOut.c_str());
            logToFile("ERROR", "lptools resize FAILED: %s", resizeOut.c_str());
            return false;
        }
        logToFile("INFO", "  lptools resize OK: %s", resizeOut.c_str());
    } else {
        logToFile("INFO", "  Step 1: lptools resize already done in pre-resize phase (cached %zu extents)",
                  mCachedExtents[dmName].size());
    }

    // Step 2: Try to unmap + remap the dm device
    // This may fail if the partition is mounted (e.g., system at /)
    std::string unmapCmd = "lptools unmap " + dmName;
    logToFile("INFO", "  Step 2: attempting unmap: %s", unmapCmd.c_str());
    std::string unmapOut;
    if (execCommand(unmapCmd, &unmapOut)) {
        logToFile("INFO", "  Unmap succeeded — remapping with new metadata");
        // Unmap succeeded — remap with new metadata
        std::string mapCmd = "lptools map " + dmName;
        std::string mapOut;
        if (!execCommand(mapCmd, &mapOut)) {
            ALOGE("lptools map failed after unmap: %s", mapOut.c_str());
            logToFile("ERROR", "lptools map FAILED after unmap: %s", mapOut.c_str());
            return false;
        }

        // Verify new size
        std::string dmPath = getDmDevPath(dmName);
        uint64_t actualSize = getBlockDevSize(dmPath);
        logToFile("INFO", "  After unmap/map: path=%s size=%llu (target=%llu)",
                  dmPath.c_str(), (unsigned long long)actualSize, (unsigned long long)newSize);
        if (actualSize < newSize) {
            ALOGE("Resize verification failed: expected %llu, got %llu",
                  (unsigned long long)newSize, (unsigned long long)actualSize);
            logToFile("ERROR", "Resize verification FAILED: expected %llu got %llu",
                      (unsigned long long)newSize, (unsigned long long)actualSize);
            return false;
        }
        ALOGI("Resized %s to %llu bytes (unmap/map, dm: %llu)",
              dmName.c_str(), (unsigned long long)newSize, (unsigned long long)actualSize);
        logToFile("INFO", "  Resize via unmap/map: SUCCESS");
    } else {
        // Unmap failed — partition is likely mounted (e.g., system at /)
        // Use dmctl replace to live-resize the dm device by parsing the current
        // table and extending it with the additional sectors needed.
        ALOGI("Partition %s is mounted — using dmctl replace for live resize", dmName.c_str());
        logToFile("INFO", "  Unmap FAILED (expected for mounted partition) — using dmctl replace");

        // Read the current dm table
        std::string tableOut;
        execCommand("dmctl table " + dmName, &tableOut);
        logToFile("INFO", "  Current dm table for %s:\n%s", dmName.c_str(), tableOut.c_str());

        // Parse all linear extents from the current table
        // Format: "START-END: linear, MAJOR:MINOR OFFSET"
        struct Extent {
            uint64_t dmStart, dmEnd;
            unsigned int major, minor;
            uint64_t physOffset;
        };
        std::vector<Extent> extents;
        size_t pos = 0;
        while (pos < tableOut.size()) {
            size_t lineEnd = tableOut.find('\n', pos);
            if (lineEnd == std::string::npos) lineEnd = tableOut.size();
            std::string line = tableOut.substr(pos, lineEnd - pos);

            Extent e;
            if (sscanf(line.c_str(), "%" SCNu64 "-%" SCNu64 ": linear, %u:%u %" SCNu64,
                       &e.dmStart, &e.dmEnd, &e.major, &e.minor, &e.physOffset) == 5) {
                extents.push_back(e);
            }
            pos = lineEnd + 1;
        }

        logToFile("INFO", "  Parsed %zu extents from dm table", extents.size());
        for (size_t i = 0; i < extents.size(); i++) {
            logToFile("INFO", "    extent[%zu]: dm=%llu-%llu dev=%u:%u offset=%llu",
                      i, (unsigned long long)extents[i].dmStart,
                      (unsigned long long)extents[i].dmEnd,
                      extents[i].major, extents[i].minor,
                      (unsigned long long)extents[i].physOffset);
        }

        if (extents.empty()) {
            ALOGE("Could not parse current dm table for %s", dmName.c_str());
            logToFile("ERROR", "No extents parsed from dm table for %s", dmName.c_str());
            return false;
        }

        uint64_t currentSectors = extents.back().dmEnd;
        uint64_t newSectors = newSize / 512;
        std::string devStr = std::to_string(extents[0].major) + ":" + std::to_string(extents[0].minor);

        ALOGI("Current sectors: %" PRIu64 ", need: %" PRIu64 ", delta: %" PRIu64,
              currentSectors, newSectors, newSectors - currentSectors);
        logToFile("INFO", "  Current sectors: %llu, target sectors: %llu, delta: %+lld",
                  (unsigned long long)currentSectors, (unsigned long long)newSectors,
                  (long long)(newSectors - currentSectors));

        // Use cached extents from pre-resize phase (read from lpdump while
        // binder service was alive). This is the authoritative extent layout
        // that init will use on reboot.
        std::string replaceCmd = "dmctl replace " + dmName;
        auto cachedIt = mCachedExtents.find(dmName);
        if (cachedIt != mCachedExtents.end() && !cachedIt->second.empty()) {
            logToFile("INFO", "  Using %zu cached extents from pre-resize lpdump",
                      cachedIt->second.size());
            for (const auto& ce : cachedIt->second) {
                uint64_t sectors = ce.dmEnd - ce.dmStart;
                replaceCmd += " linear " + std::to_string(ce.dmStart) + " " +
                              std::to_string(sectors) + " " + devStr + " " +
                              std::to_string(ce.physOffset);
            }
        } else {
            // Fallback: extend last extent (legacy behavior — may cause mismatch
            // between live dm table and super metadata on reboot)
            logToFile("WARN", "  No cached extents — falling back to last-extent extension");
            for (size_t i = 0; i < extents.size(); i++) {
                const auto& e = extents[i];
                uint64_t sectors = e.dmEnd - e.dmStart;
                if (i == extents.size() - 1 && newSectors > currentSectors) {
                    sectors += (newSectors - currentSectors);
                }
                replaceCmd += " linear " + std::to_string(e.dmStart) + " " +
                              std::to_string(sectors) + " " + devStr + " " +
                              std::to_string(e.physOffset);
            }
        }

        ALOGI("dmctl replace cmd: %s", replaceCmd.c_str());
        logToFile("INFO", "  dmctl replace cmd: %s", replaceCmd.c_str());
        std::string replaceOut;
        if (!execCommand(replaceCmd, &replaceOut)) {
            ALOGE("dmctl replace failed: %s", replaceOut.c_str());
            logToFile("ERROR", "dmctl replace FAILED: %s", replaceOut.c_str());
            return false;
        }
        logToFile("INFO", "  dmctl replace output: %s", replaceOut.c_str());

        std::string dmPath = getDmDevPath(dmName);
        uint64_t actualSize = getBlockDevSize(dmPath);
        ALOGI("After dmctl replace: %s is %" PRIu64 " bytes (target: %" PRIu64 ")",
              dmName.c_str(), actualSize, newSize);
        logToFile("INFO", "  After dmctl replace: %s = %llu bytes (target %llu)",
                  dmName.c_str(), (unsigned long long)actualSize, (unsigned long long)newSize);

        if (actualSize < newSize) {
            ALOGE("Live resize failed: got %" PRIu64 " but need %" PRIu64, actualSize, newSize);
            logToFile("ERROR", "Live resize FAILED: got %llu need %llu",
                      (unsigned long long)actualSize, (unsigned long long)newSize);
            return false;
        }
        logToFile("INFO", "  Resize via dmctl replace: SUCCESS");
    }

    return true;
}

std::vector<std::string> OtaFlasher::verify(const OtaManifest& manifest) {
    notifyStatus(FlashPhase::VERIFYING);
    std::vector<std::string> failures;
    logToFile("INFO", "=== VERIFICATION STARTED ===");
    sync();

    int idx = 0;
    const int count = (int)manifest.partitions.size();
    for (const auto& part : manifest.partitions) {
        progressStage("verify:" + part.name);
        notifyStatus(FlashPhase::VERIFYING, part.name, idx, count, 0);
        logToFile("INFO", "Verifying %d/%d: %s", idx + 1, count, part.name.c_str());
        auto prog = [&](uint64_t r, uint64_t t) {
            notifyStatus(FlashPhase::VERIFYING, part.name, idx, count, t ? (int)(r * 100 / t) : 0);
        };
        if (mLeftUnchanged.count(part.name)) {
            logToFile("WARN", "  %s was write-protected and left as it was: not verified", part.name.c_str());
            idx++;
            continue;
        }
        auto e = mExpectedSha.find(part.name);
        const std::string expected = e != mExpectedSha.end() ? e->second : part.sha256_uncompressed;
        const uint64_t size = part.size;
        if (expected.empty() || size == 0) {
            logToFile("WARN", "  No hash or size for %s: cannot verify", part.name.c_str());
            idx++;
            continue;
        }

        bool ok = true;
        if (part.type == "logical") {
            // Through the super metadata, so this is what init will map on the next boot. (These
            // reads go past the page cache in 1 MiB pieces; the old reason for skipping system and
            // vendor, running out of memory on the read-back, no longer applies.)
            auto ranges = logicalRanges(part.name);
            std::string got;
            if (!ranges.empty()) {
                got = XzDecompressor::sha256Ranges(ranges, size, prog);
                logToFile("INFO", "  %s through the metadata (%zu extents): %s", part.name.c_str(), ranges.size(), got.c_str());
            } else {
                std::string dm = getDmDevPath(part.name + getSlotSuffix());
                if (dm.empty()) dm = getDmDevPath(part.name);
                got = dm.empty() ? "" : XzDecompressor::sha256BlockDev(dm, size, prog);
                logToFile("WARN", "  %s read through the live dm device %s: %s", part.name.c_str(), dm.c_str(), got.c_str());
            }
            ok = (got == expected);
        } else {
            for (const auto& d : physicalDevices(part.name)) {
                const std::string got = XzDecompressor::sha256BlockDev(d, size, prog);
                logToFile("INFO", "  %s: %s", d.c_str(), got.c_str());
                if (got != expected) ok = false;
            }
        }
        if (ok) {
            logToFile("INFO", "  Verified %s: OK", part.name.c_str());
        } else {
            logToFile("ERROR", "  VERIFICATION FAILED for %s (expected %s)", part.name.c_str(), expected.c_str());
            failures.push_back(part.name);
        }
        notifyStatus(FlashPhase::VERIFYING, part.name, idx, count, 100);
        idx++;
    }
    logToFile("INFO", "=== VERIFICATION %s (%zu failures) ===",
              failures.empty() ? "PASSED" : "FAILED", failures.size());
    return failures;
}

bool OtaFlasher::restoreFromBackup() {
    logToFile("INFO", "=== RESTORE FROM BACKUP ===");
    std::string stateFile = std::string(BACKUP_DIR) + "/pre_ota_state.json";
    if (access(stateFile.c_str(), R_OK) != 0) {
        ALOGE("No backup state found");
        logToFile("ERROR", "No backup state file found at %s", stateFile.c_str());
        return false;
    }

    ALOGI("Restoring from backup...");
    std::string slot = getSlotSuffix();
    logToFile("INFO", "Restoring partitions, slot=%s", slot.c_str());

    // Parse pre_ota_state.json to get original partition sizes
    std::string stateContent;
    android::base::ReadFileToString(stateFile, &stateContent);
    logToFile("INFO", "Backup state:\n%s", stateContent.c_str());

    // Build map of partition name -> original size from state JSON
    // Format: {"name": "system_a", "size": 3525021696, "type": "logical"}
    std::unordered_map<std::string, uint64_t> originalSizes;
    size_t searchPos = 0;
    while ((searchPos = stateContent.find("\"name\"", searchPos)) != std::string::npos) {
        // Parse name
        size_t nameStart = stateContent.find('"', searchPos + 6);
        if (nameStart == std::string::npos) break;
        nameStart++;
        size_t nameEnd = stateContent.find('"', nameStart);
        if (nameEnd == std::string::npos) break;
        std::string partDmName = stateContent.substr(nameStart, nameEnd - nameStart);

        // Parse size (only logical partitions have size)
        size_t sizePos = stateContent.find("\"size\"", nameEnd);
        size_t nextEntry = stateContent.find("\"name\"", nameEnd);
        if (sizePos != std::string::npos && (nextEntry == std::string::npos || sizePos < nextEntry)) {
            size_t colonPos = stateContent.find(':', sizePos + 6);
            if (colonPos != std::string::npos) {
                uint64_t origSize = strtoull(stateContent.c_str() + colonPos + 1, nullptr, 10);
                if (origSize > 0) {
                    originalSizes[partDmName] = origSize;
                    logToFile("INFO", "  Original size: %s = %llu bytes",
                              partDmName.c_str(), (unsigned long long)origSize);
                }
            }
        }
        searchPos = nameEnd + 1;
    }

    // Step 1: Resize logical partitions back to original sizes BEFORE writing
    for (const auto& [dmName, origSize] : originalSizes) {
        std::string dmPath = getDmDevPath(dmName);
        if (dmPath.empty()) continue;

        uint64_t currentSize = getBlockDevSize(dmPath);
        if (currentSize == origSize) {
            logToFile("INFO", "  %s already at original size %llu — no resize needed",
                      dmName.c_str(), (unsigned long long)origSize);
            continue;
        }

        logToFile("INFO", "  Resizing %s back to original: %llu -> %llu bytes",
                  dmName.c_str(), (unsigned long long)currentSize, (unsigned long long)origSize);

        if (origSize > currentSize) {
            // Need to grow back — use full resize logic (unmap/map or dmctl replace)
            if (!resizeLogicalPartition(dmName, origSize)) {
                logToFile("ERROR", "Failed to grow %s back to original size", dmName.c_str());
                // Continue anyway — write what we can
            }
        } else {
            // Shrinking — just update metadata, image write will fit in current larger device
            // On reboot, init will remap at the correct smaller size
            std::string resizeCmd = "lptools resize " + dmName + " " + std::to_string(origSize);
            std::string resizeOut;
            if (!execCommand(resizeCmd, &resizeOut)) {
                logToFile("ERROR", "Failed to shrink metadata for %s: %s",
                          dmName.c_str(), resizeOut.c_str());
            } else {
                logToFile("INFO", "  Shrink metadata updated for %s", dmName.c_str());
            }
        }
    }

    // Step 2: Write backup images to partitions
    DIR* dir = opendir(BACKUP_DIR);
    if (!dir) {
        logToFile("ERROR", "Cannot open backup dir: %s", BACKUP_DIR);
        return false;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.size() < 5 || name.substr(name.size() - 4) != ".img") continue;

        std::string partName = name.substr(0, name.size() - 4);
        std::string backupPath = std::string(BACKUP_DIR) + "/" + name;

        // Determine target block device (handle both A/B and non-A/B)
        std::string blockDev;
        std::string dmName = partName + slot;
        std::string dmPath = getDmDevPath(dmName);
        if (dmPath.empty() && !slot.empty()) {
            // Try without slot suffix (non-A/B)
            dmName = partName;
            dmPath = getDmDevPath(dmName);
        }
        if (!dmPath.empty()) {
            blockDev = dmPath;
        } else {
            // Physical partition — check A/B then non-A/B
            std::string slotPath = "/dev/block/by-name/" + partName + slot;
            std::string noSlotPath = "/dev/block/by-name/" + partName;
            blockDev = (access(slotPath.c_str(), F_OK) == 0) ? slotPath : noSlotPath;
        }

        if (access(blockDev.c_str(), W_OK) != 0) {
            ALOGW("Cannot write to %s for restore, skipping", blockDev.c_str());
            logToFile("WARN", "Cannot write to %s for restore — skipping", blockDev.c_str());
            continue;
        }

        struct stat st;
        stat(backupPath.c_str(), &st);
        ALOGI("Restoring %s -> %s", backupPath.c_str(), blockDev.c_str());
        logToFile("INFO", "Restoring %s -> %s (%lld bytes)",
                  backupPath.c_str(), blockDev.c_str(), (long long)st.st_size);
        std::string cmd = "dd if=" + backupPath + " of=" + blockDev + " bs=1048576 conv=fsync 2>/dev/null";
        if (!execCommand(cmd, nullptr, 3600)) {
            logToFile("ERROR", "Restore FAILED for %s", partName.c_str());
        } else {
            logToFile("INFO", "Restore %s: complete", partName.c_str());
        }
    }
    closedir(dir);

    sync();
    ALOGI("Restore complete");
    logToFile("INFO", "=== RESTORE COMPLETE (partitions resized to original sizes) ===");
    return true;
}

void OtaFlasher::reboot() {
    // Clean up extracted package to prevent re-launch after reboot
    // Use direct syscalls instead of execCommand — the staged shell may be gone
    // after system partition was overwritten
    logToFile("INFO", "Cleaning up extracted package directory...");
    DIR* dir = opendir("/data/gammaos_ota/package");
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            std::string path = std::string("/data/gammaos_ota/package/") + entry->d_name;
            unlink(path.c_str());
            logToFile("INFO", "  Deleted: %s", path.c_str());
        }
        closedir(dir);
    }
    // Also clean staging dir
    dir = opendir(STAGING_DIR);
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            std::string path = std::string(STAGING_DIR) + "/" + entry->d_name;
            unlink(path.c_str());
        }
        closedir(dir);
    }
    // Unmount bind-mounts over /system paths before rebooting
    // If these persist, the system will boot with tmpfs covering /system/lib64
    // and ALL binaries will fail with "library not found"
    // Show "Rebooting..." on direct display
    // No fbdev rendering needed — EGL/SF handles display

    logToFile("INFO", "Unmounting bind-mounts...");
    umount("/system/bin");
    umount("/system/lib64");
    umount("/vendor");
    logToFile("INFO", "Bind-mounts removed");

    // Close direct display
    // mDisplay not used when SF is alive

    // Clear OTA properties
    property_set("sys.gammaos.ota.package", "");
    property_set("sys.gammaos.ota.autoinstall", "0");

    sync();
    ALOGI("Rebooting...");
    logToFile("INFO", "=== REBOOT INITIATED ===");
    // Use direct syscall for reboot — more reliable than property after system overwrite
    ::reboot(RB_AUTOBOOT);
}

uint64_t OtaFlasher::getBlockDevSize(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return 0;
    uint64_t size = 0;
    ioctl(fd, BLKGETSIZE64, &size);
    close(fd);
    return size;
}

std::string OtaFlasher::getDmDevPath(const std::string& dmName) {
    // Try /dev/block/mapper/<name>
    std::string mapperPath = "/dev/block/mapper/" + dmName;
    if (access(mapperPath.c_str(), F_OK) == 0) {
        // Resolve symlink to actual dm device
        char buf[256];
        ssize_t len = readlink(mapperPath.c_str(), buf, sizeof(buf) - 1);
        if (len > 0) {
            buf[len] = '\0';
            return std::string(buf);
        }
        return mapperPath;
    }

    // Fallback: use dmctl getpath
    std::string output;
    if (execCommand("dmctl getpath " + dmName, &output)) {
        // Trim whitespace
        while (!output.empty() && (output.back() == '\n' || output.back() == ' '))
            output.pop_back();
        if (!output.empty()) return output;
    }

    return "";
}

// ---- reliable writes ------------------------------------------------------------------

void OtaFlasher::loadFaults() {
    mFaults.clear();
    if (!android::base::GetBoolProperty("ro.debuggable", false)) return;
    // A file rather than a property: a property value is limited to 91 characters. Read once and
    // deleted, so a fault can never carry over to another update.
    std::string spec;
    const std::string faultFile = std::string(OTA_DIR) + "/faults";
    if (!android::base::ReadFileToString(faultFile, &spec)) return;
    unlink(faultFile.c_str());
    spec = android::base::Trim(spec);
    android::base::SetProperty("sys.gammaos.ota.fault_ui", spec.find("ui_crash") != std::string::npos ? "1" : "0");
    for (const auto& item : android::base::Split(spec, ",")) {
        if (item.empty()) continue;
        auto parts = android::base::Split(item, ":");
        int n = 1;
        std::string key = parts[0];
        if (parts.size() >= 2) key += ":" + parts[1];
        if (parts.size() >= 3) n = atoi(parts[2].c_str());
        if (parts[0] == "exec_crash" && parts.size() == 2) { key = "exec_crash"; n = atoi(parts[1].c_str()); }
        mFaults[key] = n;
        logToFile("WARN", "FAULT INJECTION armed: %s x%d", key.c_str(), n);
    }
}

bool OtaFlasher::takeFault(const std::string& key) {
    auto it = mFaults.find(key);
    if (it == mFaults.end() || it->second <= 0) return false;
    it->second--;
    logToFile("WARN", "FAULT INJECTED: %s (%d left)", key.c_str(), it->second);
    return true;
}

void OtaFlasher::corruptDevice(const std::string& dev, uint64_t offset) {
    // Flip one byte, past every cache, the way a bad write would leave it.
    offset &= ~(uint64_t)4095;
    void* p = nullptr;
    if (posix_memalign(&p, 4096, 4096) != 0) return;
    int fd = open(dev.c_str(), O_RDWR | O_DIRECT | O_CLOEXEC);
    if (fd >= 0 && pread(fd, p, 4096, (off_t)offset) == 4096) {
        static_cast<uint8_t*>(p)[123] ^= 0x5a;
        if (pwrite(fd, p, 4096, (off_t)offset) == 4096) fsync(fd);
    }
    if (fd >= 0) close(fd);
    free(p);
    logToFile("WARN", "FAULT: corrupted one byte of %s at %llu", dev.c_str(), (unsigned long long)offset);
}


bool OtaFlasher::prepareStaging(const OtaPartition& part, const std::string& stagingFile,
                                int partIdx, int partCount, std::string* stagingSha, uint64_t* size) {
    const std::string xzPath = mPackageDir + "/" + part.file;
    mkdir(STAGING_DIR, 0700);
    for (int attempt = 1; attempt <= kWriteAttempts; attempt++) {
        logToFile("INFO", "  Decompressing %s -> %s (attempt %d)", xzPath.c_str(), stagingFile.c_str(), attempt);
        progressStage("dec:" + part.name);
        notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, 0);
        int lastLogged = -10;
        auto prog = [&](uint64_t written, uint64_t total) {
            int pct = total ? (int)(written * 100 / total) : 0;
            notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, pct);
            if (pct >= lastLogged + 10) { lastLogged = pct; logToFile("INFO", "  Decompression %d%%", pct); }
        };
        if (!XzDecompressor::decompressToFile(xzPath, stagingFile, part.size, prog)) {
            logToFile("ERROR", "  Decompression of %s failed (attempt %d)", part.file.c_str(), attempt);
            continue;
        }
        struct stat st;
        if (stat(stagingFile.c_str(), &st) != 0 || st.st_size <= 0) {
            logToFile("ERROR", "  Staging file %s missing after decompression", stagingFile.c_str());
            continue;
        }
        const uint64_t n = part.size ? part.size : (uint64_t)st.st_size;
        if (takeFault("stage:" + part.name)) {
            int fd = open(stagingFile.c_str(), O_RDWR | O_CLOEXEC);
            uint8_t c = 0;
            if (fd >= 0 && pread(fd, &c, 1, (off_t)(n / 2)) == 1) {
                c ^= 0x5a;
                if (pwrite(fd, &c, 1, (off_t)(n / 2)) == 1) fsync(fd);
            }
            if (fd >= 0) close(fd);
        }
        // Hash the staging copy as it is on the medium. On the SD-card devices /data is the card,
        // and a card that corrupts a multi-GB write would otherwise flash those bytes unchecked:
        // xz's own check only covers the decompression, not the file it was written to.
        logToFile("INFO", "  Checking the staging copy (%llu bytes)...", (unsigned long long)n);
        progressStage("check:" + part.name);
        const std::string sha = XzDecompressor::sha256File(stagingFile, n, [&](uint64_t r, uint64_t t) {
            notifyStatus(FlashPhase::VERIFYING, part.name, partIdx, partCount, t ? (int)(r * 100 / t) : 0);
        });
        if (sha.empty()) {
            logToFile("ERROR", "  Staging copy of %s could not be read back", part.name.c_str());
            continue;
        }
        if (!part.sha256_uncompressed.empty() && sha != part.sha256_uncompressed) {
            logToFile("ERROR", "  Staging copy of %s is corrupt: expected %s got %s", part.name.c_str(),
                      part.sha256_uncompressed.c_str(), sha.c_str());
            continue;
        }
        logToFile("INFO", "  Staging copy OK: %s%s", sha.c_str(),
                  part.sha256_uncompressed.empty() ? " (manifest has no uncompressed hash)" : " (matches manifest)");
        *stagingSha = sha;
        *size = n;
        return true;
    }
    unlink(stagingFile.c_str());
    return false;
}

OtaFlasher::WriteResult OtaFlasher::writeVerified(const std::string& file, const std::string& dev,
                                                  uint64_t size, const std::string& expected,
                                                  const std::string& name, FlashPhase phase,
                                                  int partIdx, int partCount) {
    for (int attempt = 1; attempt <= kWriteAttempts; attempt++) {
        logToFile("INFO", "  Writing %s -> %s (%llu bytes, attempt %d/%d)", file.c_str(), dev.c_str(),
                  (unsigned long long)size, attempt, kWriteAttempts);
        // Physical partitions are planned per device (both slots on A/B), logical ones by name.
        const std::string stepKey = phase == FlashPhase::FLASHING_PHYSICAL ? dev : name;
        progressStage("write:" + stepKey);
        notifyStatus(phase, name, partIdx, partCount, 0);
        auto wprog = [&](uint64_t w, uint64_t t) {
            notifyStatus(phase, name, partIdx, partCount, t ? (int)(w * 100 / t) : 0);
        };
        std::string sent;
        const bool wrote = XzDecompressor::writeFileToBlock(file, dev, size, wprog, &sent);
        if (sent != expected) {
            // The bytes read from the source file are not the image: nothing a rewrite from the
            // same file can fix. The caller re-creates the source.
            logToFile("ERROR", "  Source %s read back differently while writing (%s, expected %s)",
                      file.c_str(), sent.c_str(), expected.c_str());
            return WriteResult::SourceBad;
        }
        if (!wrote) {
            logToFile("ERROR", "  Write of %s to %s reported an error (attempt %d)", name.c_str(), dev.c_str(), attempt);
            usleep(500000);
            continue;
        }
        if (takeFault("write:" + name)) corruptDevice(dev, size / 2);
        logToFile("INFO", "  Reading %s back to verify...", dev.c_str());
        progressStage("read:" + stepKey);
        notifyStatus(FlashPhase::VERIFYING, name, partIdx, partCount, 0);
        auto vprog = [&](uint64_t r, uint64_t t) {
            notifyStatus(FlashPhase::VERIFYING, name, partIdx, partCount, t ? (int)(r * 100 / t) : 0);
        };
        const std::string got = XzDecompressor::sha256BlockDev(dev, size, vprog);
        if (got == expected) {
            logToFile("INFO", "  Verified %s on %s: %s", name.c_str(), dev.c_str(), got.c_str());
            return WriteResult::Ok;
        }
        logToFile("ERROR", "  Read-back of %s on %s does not match (got '%s', expected %s), attempt %d",
                  name.c_str(), dev.c_str(), got.c_str(), expected.c_str(), attempt);
        usleep(500000);
    }
    return WriteResult::WriteFailed;
}

std::vector<ByteRange> OtaFlasher::logicalRanges(const std::string& partName) {
    std::vector<ByteRange> ranges;
    const std::string slot = getSlotSuffix();
    const uint32_t slotNum = android::fs_mgr::SlotNumberForSlotSuffix(slot);
    auto metadata = android::fs_mgr::ReadMetadata("/dev/block/by-name/super", slotNum);
    if (!metadata) {
        logToFile("WARN", "  Cannot read super metadata (slot %u) to locate %s", slotNum, partName.c_str());
        return ranges;
    }
    const std::string names[2] = { partName + slot, partName };
    for (const std::string& want : names) {
        for (const auto& p : metadata->partitions) {
            if (want != android::fs_mgr::GetPartitionName(p)) continue;
            for (uint32_t i = 0; i < p.num_extents; i++) {
                const auto& ext = metadata->extents[p.first_extent_index + i];
                if (ext.target_type != LP_TARGET_TYPE_LINEAR || ext.target_source >= metadata->block_devices.size()) {
                    logToFile("WARN", "  %s has a non-linear extent: metadata verification not possible", want.c_str());
                    return {};
                }
                const auto& bdev = metadata->block_devices[ext.target_source];
                ranges.push_back({ "/dev/block/by-name/" + android::fs_mgr::GetBlockDevicePartitionName(bdev),
                                   ext.target_data * 512ULL, ext.num_sectors * 512ULL });
            }
            return ranges;
        }
    }
    logToFile("WARN", "  %s is not in the super metadata (slot %u)", partName.c_str(), slotNum);
    return ranges;
}

void OtaFlasher::pinRunningCode() {
    // While system and vendor are rewritten in place, any process that page-faults code from them
    // reads the new bytes at the old offsets and dies: this process's GPU driver (the UI), and
    // outside it SurfaceFlinger, the composer and allocator HALs, vold (init reboots the device
    // when vold dies, mid-write) and init itself. The page cache is per file and shared, so
    // mapping the same files here and mlock()ing them keeps every process's code resident: nobody
    // re-reads it from the device being overwritten.
    //
    // These devices can have 1 GB of RAM, and locked pages cannot be reclaimed while xz needs its
    // own memory to decompress, so the budget is a quarter of what is available, at most 192 MB,
    // spent in order of how much a crash would cost:
    //   tier 0: everything init maps (code and read-only data, about 10 MB). init dying is a kernel
    //           panic and an immediate reboot, mid-write if it happens then, which leaves a
    //           half-written system that does not boot. Pinned whatever the budget says.
    //   tier 1: executable code of this process and of the processes the update or the display
    //           depends on; tier 2: their read-only data (a fault there kills them just the same:
    //           the composer HAL died of one during a system rewrite while this tier came last and
    //           found the budget spent); tier 3: everyone else's executable code.
    static const char* const kRoots[] = { "/system/", "/vendor/", "/apex/", "/odm/", "/product/", "/system_ext/" };
    static const char* const kCritical[] = { "surfaceflinger", "composer", "allocator", "vold", "servicemanager",
                                             "ueventd", "lmkd", "logd", "gammaos-ota" };
    using Ranges = std::map<std::string, std::vector<std::pair<uint64_t, uint64_t>>>;
    constexpr int kTiers = 4;
    Ranges want[kTiers];
    const pid_t self = getpid();
    DIR* proc = opendir("/proc");
    if (!proc) return;
    struct dirent* e;
    while ((e = readdir(proc)) != nullptr) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        const std::string dir = std::string("/proc/") + e->d_name;
        std::string comm;
        android::base::ReadFileToString(dir + "/comm", &comm);
        const bool isInit = atoi(e->d_name) == 1;
        bool critical = atoi(e->d_name) == self;
        for (const char* c : kCritical) if (comm.find(c) != std::string::npos) critical = true;
        FILE* f = fopen((dir + "/maps").c_str(), "re");
        if (!f) continue;
        char line[1024];
        while (fgets(line, sizeof line, f)) {
            unsigned long lo, hi, off, inode;
            char perms[8], path[768] = {0};
            if (sscanf(line, "%lx-%lx %7s %lx %*s %lu %767[^\n]", &lo, &hi, perms, &off, &inode, path) < 6) continue;
            if (perms[0] != 'r' || inode == 0 || strstr(path, "(deleted)")) continue;
            bool rooted = false;
            for (const char* r : kRoots) if (!strncmp(path, r, strlen(r))) { rooted = true; break; }
            if (!rooted) continue;
            const bool exec = perms[2] == 'x';
            const int tier = isInit ? 0 : exec ? (critical ? 1 : 3) : (critical ? 2 : -1);
            if (tier >= 0) want[tier][path].push_back({ off, off + (hi - lo) });
        }
        fclose(f);
    }
    closedir(proc);

    uint64_t budget = 64ULL << 20;
    {
        std::string mi;
        android::base::ReadFileToString("/proc/meminfo", &mi);
        auto pos = mi.find("MemAvailable:");
        if (pos != std::string::npos) budget = strtoull(mi.c_str() + pos + 13, nullptr, 10) * 1024 / 4;
        if (budget > (192ULL << 20)) budget = 192ULL << 20;
    }
    // A range already pinned by an earlier tier is not pinned twice.
    std::map<std::string, std::vector<std::pair<uint64_t, uint64_t>>> done;
    auto covered = [&](const std::string& path, uint64_t a, uint64_t b) {
        for (auto& r : done[path]) if (r.first <= a && r.second >= b) return true;
        return false;
    };
    uint64_t pinned = 0, skipped = 0;
    const long ps = sysconf(_SC_PAGESIZE);
    for (int tier = 0; tier < kTiers; tier++) {
        for (auto& kv : want[tier]) {
            auto& iv = kv.second;
            std::sort(iv.begin(), iv.end());
            std::vector<std::pair<uint64_t, uint64_t>> merged;
            for (auto& r : iv) {
                if (!merged.empty() && r.first <= merged.back().second) merged.back().second = std::max(merged.back().second, r.second);
                else merged.push_back(r);
            }
            int fd = -1;
            struct stat st = {};
            for (auto& r : merged) {
                const uint64_t a = r.first & ~(uint64_t)(ps - 1);
                if (fd < 0) {
                    fd = open(kv.first.c_str(), O_RDONLY | O_CLOEXEC);
                    if (fd < 0) break;
                    fstat(fd, &st);
                }
                const uint64_t b = std::min<uint64_t>(r.second, (uint64_t)st.st_size);
                if (b <= a || covered(kv.first, a, b)) continue;
                const size_t len = (size_t)(b - a);
                if (tier > 0 && pinned + len > budget) { skipped += len; continue; }
                void* m = mmap(nullptr, len, PROT_READ, MAP_SHARED, fd, (off_t)a);
                if (m == MAP_FAILED) continue;
                if (mlock(m, len) != 0) { munmap(m, len); skipped += len; continue; }
                mPinned.push_back({ m, len });
                done[kv.first].push_back({ a, b });
                pinned += len;
            }
            if (fd >= 0) close(fd);
        }
        logToFile("INFO", "Pinning: after tier %d, %llu MB pinned of a %llu MB budget", tier,
                  (unsigned long long)(pinned >> 20), (unsigned long long)(budget >> 20));
    }
    logToFile("INFO", "Pinned %llu MB of running code (%llu MB left unpinned, over budget)",
              (unsigned long long)(pinned >> 20), (unsigned long long)(skipped >> 20));
}

bool OtaFlasher::savePhysicalOriginal(const std::string& name, const std::string& dev, uint64_t size,
                                      PhysicalOriginal* out) {
    mkdir(ORIGINALS_DIR, 0700);
    std::string base = dev.substr(dev.rfind('/') + 1);
    std::string file = std::string(ORIGINALS_DIR) + "/" + base + ".img";
    // Copy the partition out (read past the page cache), then hash the copy as stored.
    int in = open(dev.c_str(), O_RDONLY | O_CLOEXEC);
    int outFd = open(file.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    bool ok = in >= 0 && outFd >= 0;
    if (ok) {
        ioctl(in, BLKFLSBUF, 0);
        std::vector<uint8_t> buf(1 << 20);
        uint64_t done = 0;
        while (ok && done < size) {
            size_t len = (size_t)std::min<uint64_t>(buf.size(), size - done);
            ssize_t n = pread(in, buf.data(), len, (off_t)done);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) { ok = false; break; }
            size_t w = 0;
            while (w < (size_t)n) {
                ssize_t r = write(outFd, buf.data() + w, (size_t)n - w);
                if (r < 0 && errno == EINTR) continue;
                if (r <= 0) { ok = false; break; }
                w += (size_t)r;
            }
            // Low-memory devices: neither copy should stay in the page cache.
            posix_fadvise(in, (off_t)done, n, POSIX_FADV_DONTNEED);
            done += (uint64_t)n;
        }
        if (ok && fsync(outFd) != 0) ok = false;
        if (outFd >= 0) posix_fadvise(outFd, 0, 0, POSIX_FADV_DONTNEED);
    }
    if (in >= 0) close(in);
    if (outFd >= 0 && close(outFd) != 0) ok = false;
    std::string sha = ok ? XzDecompressor::sha256File(file, size) : "";
    std::string devSha = ok ? XzDecompressor::sha256BlockDev(dev, size) : "";
    if (!ok || sha.empty() || sha != devSha) {
        logToFile("ERROR", "  Could not save the original %s (%s): copy %s, device %s", name.c_str(), dev.c_str(),
                  sha.c_str(), devSha.c_str());
        unlink(file.c_str());
        return false;
    }
    *out = { name, dev, file, sha, size };
    logToFile("INFO", "  Saved the original %s (%s, %llu bytes): %s", name.c_str(), dev.c_str(),
              (unsigned long long)size, sha.c_str());
    return true;
}

bool OtaFlasher::restorePhysicalOriginals() {
    bool all = true;
    for (auto it = mPhysicalOriginals.rbegin(); it != mPhysicalOriginals.rend(); ++it) {
        logToFile("WARN", "Restoring the original %s on %s", it->name.c_str(), it->device.c_str());
        if (writeVerified(it->file, it->device, it->size, it->sha, it->name, FlashPhase::FLASHING_PHYSICAL, 0, 1)
            != WriteResult::Ok) {
            logToFile("ERROR", "  Restoring the original %s FAILED", it->name.c_str());
            all = false;
        }
    }
    return all;
}

bool OtaFlasher::repairPartition(const OtaPartition& part, int partIdx, int partCount) {
    const std::string stagingFile = std::string(STAGING_DIR) + "/" + part.name + ".img";
    std::string sha;
    uint64_t size = 0;
    if (!prepareStaging(part, stagingFile, partIdx, partCount, &sha, &size)) return false;
    bool ok = false;
    if (part.type == "logical") {
        // Through the metadata ranges: the bytes go exactly where the next boot reads them,
        // whatever the live dm table says.
        auto ranges = logicalRanges(part.name);
        if (!ranges.empty()) {
            std::string sent;
            auto prog = [&](uint64_t w, uint64_t t) {
                notifyStatus(FlashPhase::FLASHING_LOGICAL, part.name, partIdx, partCount, t ? (int)(w * 100 / t) : 0);
            };
            progressStage("write:" + part.name);   // again: added to the plan, the bar keeps moving
            if (XzDecompressor::writeFileToRanges(stagingFile, ranges, size, prog, &sent) && sent == sha) {
                progressStage("read:" + part.name);
                ok = XzDecompressor::sha256Ranges(ranges, size, [&](uint64_t r, uint64_t t) {
                    notifyStatus(FlashPhase::VERIFYING, part.name, partIdx, partCount, t ? (int)(r * 100 / t) : 0);
                }) == sha;
            }
        }
    } else {
        ok = true;
        for (const auto& d : physicalDevices(part.name))
            if (writeVerified(stagingFile, d, size, sha, part.name, FlashPhase::FLASHING_PHYSICAL, partIdx, partCount)
                != WriteResult::Ok) ok = false;
    }
    unlink(stagingFile.c_str());
    logToFile(ok ? "INFO" : "ERROR", "  Repair of %s %s", part.name.c_str(), ok ? "succeeded" : "FAILED");
    return ok;
}

bool OtaFlasher::execCommand(const std::string& cmd, std::string* output, int timeoutSec) {
    // The staged shell, never /system/bin/sh: system is being overwritten.
    std::string shellPath = "/system/bin/sh";
    std::string pathEnv = "PATH=/system/bin:/vendor/bin";
    std::string ldEnv;
    if (isRunningFromTmpfs()) {
        shellPath = std::string(STAGE_DIR) + "/bin/sh";
        pathEnv = "PATH=" + std::string(STAGE_DIR) + "/bin:/system/bin:/vendor/bin";
        ldEnv = "LD_LIBRARY_PATH=" + std::string(STAGE_DIR) + "/lib64:/system/lib64";
    }
    std::vector<std::string> envStore;
    for (char** e = environ; e && *e; e++) {
        if (!strncmp(*e, "PATH=", 5) || (!ldEnv.empty() && !strncmp(*e, "LD_LIBRARY_PATH=", 16))) continue;
        envStore.push_back(*e);
    }
    envStore.push_back(pathEnv);
    if (!ldEnv.empty()) envStore.push_back(ldEnv);
    std::vector<char*> envp;
    for (auto& e : envStore) envp.push_back(const_cast<char*>(e.c_str()));
    envp.push_back(nullptr);

    ALOGI("execCommand: %s (shell: %s)", cmd.c_str(), shellPath.c_str());
    logToFile("INFO", "exec: %s", cmd.c_str());

    // posix_spawn, not fork. fork() runs every pthread_atfork child handler, including the GPU
    // driver's; once vendor has been rewritten that handler's code is gone and the child dies
    // with SIGBUS before it can exec. That is how the 1.4.3 update on the RG DS Plus failed:
    // "dmctl table vendor_dlkm" never ran, it reported exit -1, and the flash stopped half way.
    // bionic's posix_spawn does not run atfork handlers.
    for (int attempt = 1; attempt <= 2; attempt++) {
        int pipefd[2];
        if (pipe2(pipefd, O_CLOEXEC) < 0) {
            logToFile("ERROR", "pipe2() failed: %s", strerror(errno));
            return false;
        }
        posix_spawn_file_actions_t fa;
        posix_spawn_file_actions_init(&fa);
        posix_spawn_file_actions_adddup2(&fa, pipefd[1], STDOUT_FILENO);
        posix_spawn_file_actions_adddup2(&fa, pipefd[1], STDERR_FILENO);
        const std::string runCmd = takeFault("exec_crash") ? "kill -SEGV $$; " + cmd : cmd;
        char* argv[] = { const_cast<char*>("sh"), const_cast<char*>("-c"),
                         const_cast<char*>(runCmd.c_str()), nullptr };
        pid_t pid = -1;
        int rc = posix_spawn(&pid, shellPath.c_str(), &fa, nullptr, argv, envp.data());
        posix_spawn_file_actions_destroy(&fa);
        close(pipefd[1]);
        if (rc != 0) {
            close(pipefd[0]);
            logToFile("ERROR", "posix_spawn(%s) failed: %s", shellPath.c_str(), strerror(rc));
            return false;
        }

        std::string result;
        bool timedOut = false;
        const int64_t deadline = (int64_t)time(nullptr) + timeoutSec;
        char buf[4096];
        for (;;) {
            int64_t left = deadline - (int64_t)time(nullptr);
            if (left <= 0) { timedOut = true; break; }
            struct pollfd pfd = { pipefd[0], POLLIN, 0 };
            int pr = poll(&pfd, 1, (int)(left > 5 ? 5000 : left * 1000));
            if (pr < 0) { if (errno == EINTR) continue; break; }
            if (pr == 0) continue;
            ssize_t n = read(pipefd[0], buf, sizeof(buf));
            if (n < 0) { if (errno == EINTR) continue; break; }
            if (n == 0) break;
            result.append(buf, (size_t)n);
        }
        close(pipefd[0]);
        if (timedOut) {
            kill(pid, SIGKILL);
            logToFile("ERROR", "exec timed out after %d s, killed: %s", timeoutSec, cmd.c_str());
        }
        int status = 0;
        while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}

        if (WIFSIGNALED(status) && !timedOut) {
            // Reported as a crash, never as an ordinary failure. The commands run here only read
            // state or are idempotent, so a crash is retried once.
            logToFile("ERROR", "exec child killed by signal %d (attempt %d): %s", WTERMSIG(status), attempt,
                      result.c_str());
            if (attempt < 2) { usleep(200000); continue; }
        }
        int exitCode = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
        ALOGI("execCommand result (%d): %s", exitCode, result.substr(0, 200).c_str());
        logToFile("INFO", "exec result (exit=%d): %s", exitCode, result.c_str());
        if (output) *output = result;
        return !timedOut && WIFEXITED(status) && WEXITSTATUS(status) == 0;
    }
    return false;
}

} // namespace android
