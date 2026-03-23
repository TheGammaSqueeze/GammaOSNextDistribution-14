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

#ifndef GAMMAOS_OTA_FLASHER_H
#define GAMMAOS_OTA_FLASHER_H

#include <string>
#include <vector>
#include <map>
#include <functional>
#include <cstdio>

#include "OtaManifest.h"
#include "OtaDisplay.h"

namespace android {

// Status callback for UI updates
enum class FlashPhase {
    STAGING,
    PREFLIGHT,
    BACKUP,
    STOPPING_FRAMEWORK,
    DECOMPRESSING,
    FLASHING_PHYSICAL,
    FLASHING_LOGICAL,
    VERIFYING,
    COMPLETE,
    FAILED,
};

struct FlashStatus {
    FlashPhase phase;
    std::string currentPartition;
    int partitionIndex;     // which partition (0-based)
    int partitionCount;     // total partitions
    int progressPercent;    // 0-100 for current partition
    std::string errorMsg;
};

using FlashStatusCallback = std::function<void(const FlashStatus&)>;

class OtaFlasher {
public:
    OtaFlasher();

    // Set the callback for UI updates
    void setStatusCallback(FlashStatusCallback cb) { mCallback = cb; }

    // Set the OTA package directory (where extracted .img.xz files live)
    void setPackageDir(const std::string& dir) { mPackageDir = dir; }

    // Stage self + dependencies to tmpfs. Returns true if already staged or staging succeeded.
    // After staging, caller should re-exec from the staged path.
    bool stageToTmpfs(int argc, char** argv);

    // Check if we're already running from tmpfs
    static bool isRunningFromTmpfs();

    // Run pre-flight checks. Returns empty string on success, error message on failure.
    std::string preflight(const OtaManifest& manifest);

    // Backup current partitions. Returns true on success.
    bool backup(const OtaManifest& manifest);

    // Flash all partitions per the manifest. This is the point of no return for logical partitions.
    bool flash(const OtaManifest& manifest);

    // Verify all flashed partitions by reading back and checking SHA-256.
    // Returns list of partition names that failed verification.
    std::vector<std::string> verify(const OtaManifest& manifest);

    // Restore from backup (if backup was taken).
    bool restoreFromBackup();

    // Reboot the device.
    void reboot();

    // Get the slot suffix (e.g. "_a")
    static std::string getSlotSuffix();

    // File logging — public so OtaMenu can log too
    static FILE* sLogFile;
    static void initLogFile();
    static void logToFile(const char* level, const char* fmt, ...) __attribute__((format(printf, 2, 3)));

private:
    void notifyStatus(FlashPhase phase, const std::string& partition = "",
                      int idx = 0, int count = 0, int progress = 0,
                      const std::string& error = "");

    bool flashPhysical(const OtaPartition& part, int partIdx = 0, int partCount = 1);
    bool flashLogical(const OtaPartition& part, int partIdx = 0, int partCount = 1);
    bool resizeLogicalPartition(const std::string& dmName, uint64_t newSize);
    uint64_t getBlockDevSize(const std::string& path);
    std::string getDmDevPath(const std::string& dmName);
    bool execCommand(const std::string& cmd, std::string* output = nullptr);
    void stopFramework();
    void dropCaches();

    static constexpr const char* STAGE_DIR = "/dev/gammaos-ota-stage";
    static constexpr const char* BACKUP_DIR = "/data/gammaos_ota/backup";
    static constexpr const char* OTA_DIR = "/data/gammaos_ota";
    static constexpr const char* STAGING_DIR = "/data/gammaos_ota/staging";

    FlashStatusCallback mCallback;
    std::string mPackageDir;
    OtaDisplay mDisplay;  // Direct framebuffer/DRM display for progress during flash

    // Cached extent layouts from lpdump (read before stopping framework)
    struct CachedExtent {
        uint64_t dmStart, dmEnd, physOffset;
    };
    std::map<std::string, std::vector<CachedExtent>> mCachedExtents;

    // Libs to copy for tmpfs staging
    static const std::vector<std::string> REQUIRED_SYSTEM_LIBS;
    static const std::vector<std::string> REQUIRED_APEX_LIBS;
    static const std::vector<std::string> REQUIRED_BINARIES;
};

} // namespace android

#endif // GAMMAOS_OTA_FLASHER_H
