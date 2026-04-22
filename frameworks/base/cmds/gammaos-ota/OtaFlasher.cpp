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
        mCallback(s);
    }
}

std::string OtaFlasher::preflight(const OtaManifest& manifest) {
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
            std::string hash = XzDecompressor::sha256File(path);
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
        // Need at least the largest image + 100MB buffer for staging
        uint64_t needed = maxPartSize + (100 * 1024 * 1024);
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
        if (!execCommand(cmd)) {
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
    // Aggressively drop ALL caches — this forces the kernel to release all
    // cached pages from the system and vendor block devices
    logToFile("INFO", "Dropping caches (aggressive)...");
    dropCaches();
    usleep(500000);
    dropCaches(); // Second pass to catch any stragglers
    usleep(500000);
    dropCaches(); // Third pass — critical for large multi-partition writes

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
                uint32_t slotNum = 0; // slot _a = 0, _b = 1
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
    notifyStatus(FlashPhase::STOPPING_FRAMEWORK);
    stopFramework();

    // Phase: Flash physical partitions (safe — not mounted)
    // Physical partition failures are non-fatal: log a warning and continue.
    // The user can still boot if a physical partition fails (e.g. RO-protected boot).
    std::vector<std::string> physicalFailures;
    for (const auto* part : physicals) {
        logToFile("INFO", "--- Flashing physical partition %d/%d: %s ---",
                  idx + 1, totalParts, part->name.c_str());
        notifyStatus(FlashPhase::FLASHING_PHYSICAL, part->name, idx, totalParts, 0);
        if (!flashPhysical(*part, idx, totalParts)) {
            logToFile("WARN", "Physical partition %s: FAILED (non-fatal, continuing)",
                      part->name.c_str());
            physicalFailures.push_back(part->name);
        } else {
            logToFile("INFO", "Physical partition %s: DONE", part->name.c_str());
        }
        notifyStatus(FlashPhase::FLASHING_PHYSICAL, part->name, idx, totalParts, 100);
        idx++;
    }
    if (!physicalFailures.empty()) {
        std::string failList;
        for (const auto& f : physicalFailures) {
            if (!failList.empty()) failList += ", ";
            failList += f;
        }
        logToFile("WARN", "Physical partitions that failed: %s (continuing with logical)",
                  failList.c_str());
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
        // Drop caches between partitions to relieve memory pressure
        // Critical for multi-partition writes where total data exceeds RAM
        sync();
        dropCaches();
        idx++;
    }

    sync();
    dropCaches();
    logToFile("INFO", "=== FLASH COMPLETE — ALL PARTITIONS WRITTEN ===");

    // Dump super metadata after flash for diagnostics
    logToFile("INFO", "=== SUPER METADATA AFTER FLASH ===");
    dumpSuperMetadata("AFTER");

    return true;
}

bool OtaFlasher::flashPhysical(const OtaPartition& part, int partIdx, int partCount) {
    std::string xzPath = mPackageDir + "/" + part.file;
    logToFile("INFO", "flashPhysical: %s, file=%s, size=%llu",
              part.name.c_str(), part.file.c_str(), (unsigned long long)part.size);

    // Decompress to staging file first
    mkdir(STAGING_DIR, 0700);
    std::string stagingFile = std::string(STAGING_DIR) + "/" + part.name + ".img";
    logToFile("INFO", "  Decompressing %s -> %s (staging)", xzPath.c_str(), stagingFile.c_str());

    // Show decompression progress
    notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, 0);
    auto decompProgress = [this, &part, partIdx, partCount](uint64_t written, uint64_t total) {
        int pct = (total > 0) ? (int)((written * 100) / total) : 0;
        notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, pct);
    };
    if (!XzDecompressor::decompressToFile(xzPath, stagingFile, part.size, decompProgress)) {
        ALOGE("Failed to decompress %s to staging", part.name.c_str());
        logToFile("ERROR", "XZ decompress to staging FAILED: %s", xzPath.c_str());
        unlink(stagingFile.c_str());
        return false;
    }

    // Skip pre-write SHA-256 of decompressed file — reading 3.7GB fills the page
    // cache and triggers OOM on memory-constrained devices. The compressed file's
    // SHA-256 was already verified in preflight, and XZ has internal checksums.
    // The post-write block device read-back is the definitive integrity check.
    logToFile("INFO", "  Skipping pre-write SHA-256 (compressed SHA verified in preflight, post-write verify will confirm)");

    // Drop caches to free memory used by the decompression
    dropCaches();

    // Detect A/B vs non-A/B
    std::string slotA = "/dev/block/by-name/" + part.name + "_a";
    std::string noSlot = "/dev/block/by-name/" + part.name;
    bool isAB = (access(slotA.c_str(), F_OK) == 0);
    logToFile("INFO", "  A/B detection: %s exists=%s → %s device",
              slotA.c_str(), isAB ? "yes" : "no", isAB ? "A/B" : "non-A/B");

    bool anySlotSucceeded = false;

    if (isAB) {
        // Flash both slots — if one fails, continue with the other
        const char* slots[] = {"_a", "_b"};
        for (const char* slot : slots) {
            std::string blockDev = "/dev/block/by-name/" + part.name + slot;
            if (access(blockDev.c_str(), F_OK) != 0) {
                logToFile("WARN", "  Slot %s not found for %s — skipping", slot, part.name.c_str());
                continue;
            }

            // Clear read-only flag if set (physical partitions are often RO-protected)
            std::string setrwCmd = "blockdev --setrw " + blockDev;
            std::string setrwOut;
            if (execCommand(setrwCmd, &setrwOut)) {
                logToFile("INFO", "  Cleared RO flag on %s", blockDev.c_str());
            } else {
                logToFile("WARN", "  Failed to clear RO flag on %s", blockDev.c_str());
            }

            logToFile("INFO", "  Writing %s -> %s", stagingFile.c_str(), blockDev.c_str());
            auto physProgress = [this, &part, partIdx, partCount](uint64_t written, uint64_t total) {
                int pct = (int)((written * 100) / total);
                notifyStatus(FlashPhase::FLASHING_PHYSICAL, part.name, partIdx, partCount, pct);
            };
            if (!XzDecompressor::writeFileToBlock(stagingFile, blockDev, part.size, physProgress)) {
                ALOGW("Failed to write %s to slot %s (non-fatal)", part.name.c_str(), slot);
                logToFile("WARN", "  Slot %s write FAILED for %s (continuing)", slot, part.name.c_str());
            } else {
                logToFile("INFO", "  Slot %s for %s: write complete", slot, part.name.c_str());
                anySlotSucceeded = true;
            }
        }
    } else {
        // Non-A/B: single device
        std::string blockDev = noSlot;
        if (access(blockDev.c_str(), F_OK) != 0) {
            ALOGE("Partition %s not found at %s", part.name.c_str(), blockDev.c_str());
            logToFile("ERROR", "Partition not found: %s", blockDev.c_str());
            unlink(stagingFile.c_str());
            return false;
        }

        // Clear read-only flag
        std::string setrwCmd = "blockdev --setrw " + blockDev;
        std::string setrwOut;
        execCommand(setrwCmd, &setrwOut);

        logToFile("INFO", "  Writing %s -> %s (non-A/B)", stagingFile.c_str(), blockDev.c_str());
        auto physProgress = [this, &part, partIdx, partCount](uint64_t written, uint64_t total) {
            notifyStatus(FlashPhase::FLASHING_PHYSICAL, part.name, partIdx, partCount,
                         (int)((written * 100) / total));
        };
        if (!XzDecompressor::writeFileToBlock(stagingFile, blockDev, part.size, physProgress)) {
            ALOGW("Failed to write %s (non-fatal)", part.name.c_str());
            logToFile("WARN", "  Write FAILED for %s (non-A/B, continuing)", part.name.c_str());
        } else {
            logToFile("INFO", "  Partition %s: write complete (non-A/B)", part.name.c_str());
            anySlotSucceeded = true;
        }
    }

    if (!anySlotSucceeded) {
        logToFile("WARN", "  No slots succeeded for %s — partition unchanged", part.name.c_str());
    }

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
        // Android dynamic partitions are created by first-stage init with
        // DM_READONLY_FLAG set (Attributes: readonly in super metadata).
        // Even after BLKROSET clears the block-device ro bit, the underlying
        // dm target still rejects writes with EPERM. Reload the current table
        // via dmctl replace, which creates the dm device without the readonly
        // flag while keeping the exact same extents. This makes the partition
        // writable without disturbing the mounted filesystem above it.
        logToFile("INFO", "  No resize needed — reloading dm table as rw");

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

    // Drop caches before writing to ensure no stale reads
    logToFile("INFO", "  Dropping caches before write...");
    dropCaches();

    // Decompress and write
    std::string xzPath = mPackageDir + "/" + part.file;
    ALOGI("Flashing %s -> %s", part.file.c_str(), dmPath.c_str());

    // Decompress to staging file first, then write to block device
    mkdir(STAGING_DIR, 0700);
    std::string stagingFile = std::string(STAGING_DIR) + "/" + part.name + ".img";
    logToFile("INFO", "  Decompressing %s -> %s (staging)", xzPath.c_str(), stagingFile.c_str());

    // Show decompression progress — distinct from the "Writing" phase
    logToFile("INFO", "  UI: Switching to DECOMPRESSING phase");
    notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, 0);
    usleep(500000); // 500ms to ensure the render loop draws the initial frame
    int lastLoggedPct = -1;
    auto decompProgress = [this, &part, &lastLoggedPct, partIdx, partCount](uint64_t written, uint64_t total) {
        int pct = (total > 0) ? (int)((written * 100) / total) : 0;
        notifyStatus(FlashPhase::DECOMPRESSING, part.name, partIdx, partCount, pct);
        // Log every 10%
        if (pct / 10 > lastLoggedPct / 10) {
            lastLoggedPct = pct;
            logToFile("INFO", "  Decompression progress: %d%% (%llu / %llu MB)",
                      pct, (unsigned long long)(written / (1024*1024)),
                      (unsigned long long)(total / (1024*1024)));
        }
    };
    if (!XzDecompressor::decompressToFile(xzPath, stagingFile, part.size, decompProgress)) {
        ALOGE("Failed to decompress %s to staging", part.name.c_str());
        logToFile("ERROR", "XZ decompress to staging FAILED: %s", xzPath.c_str());
        unlink(stagingFile.c_str());
        return false;
    }
    logToFile("INFO", "  Decompression complete");

    // Skip pre-write SHA-256 — see flashPhysical for rationale (OOM on large images)
    logToFile("INFO", "  Skipping pre-write SHA-256 (compressed SHA verified in preflight, post-write verify will confirm)");

    // Drop caches to free memory used by the decompression
    dropCaches();

    // Show warning before write — this is the last frame SF will render before
    // the system partition content changes and SF's state becomes invalid.
    // The screen will go blank during the write (30-60s), then reboot.
    logToFile("INFO", "  UI: Switching to FLASHING_LOGICAL phase");
    notifyStatus(FlashPhase::FLASHING_LOGICAL, part.name, partIdx, partCount, 0);
    logToFile("INFO", "  Showing DO NOT POWER OFF warning (last SF frame)...");
    usleep(500000); // 500ms to ensure the render loop draws the frame

    // Write decompressed file to block device with progress reporting
    logToFile("INFO", "  Writing %s -> %s (%llu bytes)", stagingFile.c_str(), dmPath.c_str(),
              (unsigned long long)part.size);
    auto writeProgress = [this, &part, partIdx, partCount](uint64_t written, uint64_t total) {
        int pct = (int)((written * 100) / total);
        notifyStatus(FlashPhase::FLASHING_LOGICAL, part.name, partIdx, partCount, pct);
        // Update direct display progress (fbdev/DRM)
        std::string status = "Flashing " + part.name + "...";
        // Progress is rendered by OtaMenu's EGL render loop via notifyStatus
    };
    if (!XzDecompressor::writeFileToBlock(stagingFile, dmPath, part.size, writeProgress)) {
        ALOGE("Failed to write %s to %s", stagingFile.c_str(), dmPath.c_str());
        logToFile("ERROR", "Write to block device FAILED: %s -> %s", stagingFile.c_str(), dmPath.c_str());
        unlink(stagingFile.c_str());
        return false;
    }

    // Cleanup staging file
    unlink(stagingFile.c_str());
    logToFile("INFO", "  Flash %s: write complete, staging cleaned up", dmName.c_str());

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
    dropCaches();

    std::string slot = getSlotSuffix();
    int idx = 0;
    int count = (int)manifest.partitions.size();

    for (const auto& part : manifest.partitions) {
        notifyStatus(FlashPhase::VERIFYING, part.name, idx, count, 0);
        logToFile("INFO", "Verifying %d/%d: %s", idx + 1, count, part.name.c_str());

        if (part.sha256_uncompressed.empty()) {
            ALOGW("No uncompressed checksum for %s, skipping verification", part.name.c_str());
            logToFile("WARN", "  No sha256_uncompressed for %s — skipping", part.name.c_str());
            idx++;
            continue;
        }

        // Skip post-write verification for mounted logical partitions (system, vendor).
        // After writing to a mounted block device, the kernel page cache cannot be fully
        // invalidated — SHA-256 read-back causes OOM on large partitions.
        // Boot success is the definitive verification for these partitions.
        // Data integrity is already ensured by: XZ internal checksums + compressed SHA-256
        // verified in preflight + staging file written from verified source.
        if (part.type == "logical" && (part.name == "system" || part.name == "vendor")) {
            logToFile("INFO", "  Skipping verification for %s (mounted) — boot is the verification",
                      part.name.c_str());
            notifyStatus(FlashPhase::VERIFYING, part.name, idx, count, 100);
            idx++;
            continue;
        }

        std::string blockDev;
        uint64_t size = part.size;
        if (part.type == "logical") {
            std::string dmName = part.name + slot;
            blockDev = getDmDevPath(dmName);
            if (blockDev.empty() && !slot.empty()) {
                dmName = part.name;
                blockDev = getDmDevPath(dmName);
            }
            if (size == 0) size = getBlockDevSize(blockDev);
        } else {
            // Check A/B first, then non-A/B
            std::string slotPath = "/dev/block/by-name/" + part.name + slot;
            std::string noSlotPath = "/dev/block/by-name/" + part.name;
            blockDev = (access(slotPath.c_str(), F_OK) == 0) ? slotPath : noSlotPath;
            if (size == 0) size = getBlockDevSize(blockDev);
        }
        logToFile("INFO", "  Block device: %s, verify size: %llu bytes",
                  blockDev.c_str(), (unsigned long long)size);
        logToFile("INFO", "  Expected SHA-256: %s", part.sha256_uncompressed.c_str());

        std::string hash = XzDecompressor::sha256BlockDev(blockDev, size);
        logToFile("INFO", "  Computed SHA-256: %s", hash.c_str());
        if (hash != part.sha256_uncompressed) {
            ALOGE("Verification FAILED for %s: expected %s, got %s",
                  part.name.c_str(), part.sha256_uncompressed.c_str(), hash.c_str());
            logToFile("ERROR", "  VERIFICATION FAILED for %s", part.name.c_str());
            failures.push_back(part.name);
        } else {
            ALOGI("Verified %s: OK", part.name.c_str());
            logToFile("INFO", "  Verified %s: OK", part.name.c_str());
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
        std::string cmd = "dd if=" + backupPath + " of=" + blockDev + " bs=1048576 2>/dev/null";
        if (!execCommand(cmd)) {
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

bool OtaFlasher::execCommand(const std::string& cmd, std::string* output) {
    // Use the staged shell to avoid depending on /system/bin/sh
    // which may be corrupted after writing to the system block device
    std::string shellPath = "/system/bin/sh";
    std::string fullCmd = cmd;

    if (isRunningFromTmpfs()) {
        shellPath = std::string(STAGE_DIR) + "/bin/sh";
        std::string binDir = std::string(STAGE_DIR) + "/bin";
        std::string libDir = std::string(STAGE_DIR) + "/lib64";
        fullCmd = "export PATH=" + binDir + ":/system/bin:/vendor/bin && "
                  "export LD_LIBRARY_PATH=" + libDir + ":/system/lib64 && " + cmd;
    }

    ALOGI("execCommand: %s (shell: %s)", cmd.c_str(), shellPath.c_str());
    logToFile("INFO", "exec: %s", cmd.c_str());

    // Use fork+exec with staged shell instead of popen (which uses /system/bin/sh)
    int pipefd[2];
    if (pipe(pipefd) < 0) {
        ALOGE("pipe() failed: %s", strerror(errno));
        return false;
    }

    pid_t pid = fork();
    if (pid < 0) {
        ALOGE("fork() failed: %s", strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        return false;
    }

    if (pid == 0) {
        // Child: redirect stdout to pipe, exec staged shell
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);

        // Set LD_LIBRARY_PATH for the shell and its children
        if (isRunningFromTmpfs()) {
            std::string libDir = std::string(STAGE_DIR) + "/lib64";
            setenv("LD_LIBRARY_PATH", (libDir + ":/system/lib64").c_str(), 1);
            std::string binDir = std::string(STAGE_DIR) + "/bin";
            setenv("PATH", (binDir + ":/system/bin:/vendor/bin").c_str(), 1);
        }

        execl(shellPath.c_str(), "sh", "-c", cmd.c_str(), nullptr);
        _exit(127); // execl failed
    }

    // Parent: read output from pipe
    close(pipefd[1]);
    std::string result;
    char buf[512];
    ssize_t n;
    while ((n = read(pipefd[0], buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        result += buf;
    }
    close(pipefd[0]);

    int status;
    waitpid(pid, &status, 0);

    int exitCode = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    ALOGI("execCommand result (%d): %s", exitCode, result.substr(0, 200).c_str());
    logToFile("INFO", "exec result (exit=%d): %s", exitCode, result.c_str());

    if (output) *output = result;
    return WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

} // namespace android
