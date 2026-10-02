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
#include <set>
#include <functional>
#include <cstdio>

#include "OtaManifest.h"
#include "OtaDisplay.h"
#include "XzDecompressor.h"

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
    int progressPercent;    // 0-100 for the current step of the current partition
    int overallPermille = -1;  // 0-1000 across the whole update, never goes back; -1 = no plan
    bool redoing = false;      // the step is a retry or a repair (shown with its own percentage)
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

    // Overall progress. The bar has to show the whole update, start to finish, not each step: a
    // plan of every step the update will take is made up front, each weighted by how long it
    // takes per byte, and every status update reports where the update stands in that plan.
    // Call planProgress before preflight; the stages enter it themselves.
    void planProgress(const OtaManifest& manifest, bool withBackup);
    // The update is done (the reboot countdown): the bar reads 100%.
    void finishProgress();

    // Reboot the device.
    void reboot();

    // Get the slot suffix (e.g. "_a")
    static std::string getSlotSuffix();

    // File logging — public so OtaMenu can log too
    static FILE* sLogFile;
    static void initLogFile();
    static void logToFile(const char* level, const char* fmt, ...) __attribute__((format(printf, 2, 3)));

private:
    // Enter a step of the plan (e.g. "write:system"); the step before it counts as done. A step
    // entered again (a retry, or a repair after the final check) is work the plan did not
    // expect: it is added to the plan, and the part of the bar not yet filled is stretched over
    // what is left, so the bar keeps moving and never goes back. A key the plan does not know
    // only labels what follows.
    void progressStage(const std::string& key);
    // A planned step that will not run (a partition that already holds the image).
    void progressSkip(const std::string& key);
    void progressReanchor();
    std::map<std::string, double> mPlan;   // step -> weight (estimated seconds)
    std::set<std::string> mPlanSeen;
    double mPlanTotal = 0;                 // all work, planned plus added
    double mWorkDone = 0;                  // finished steps
    double mStepWeight = 0, mStepFrac = 0; // the step in progress
    bool mStepRedo = false;                // ... is a retry or repair
    double mAnchorShown = 0, mAnchorWork = 0;
    double mOverall = 0;                   // 0..1 as shown, only ever increases
    bool mProgressDone = false;
    static std::vector<std::string> physicalDevices(const std::string& name);
    void notifyStatus(FlashPhase phase, const std::string& partition = "",
                      int idx = 0, int count = 0, int progress = 0,
                      const std::string& error = "");

    bool flashPhysical(const OtaPartition& part, int partIdx = 0, int partCount = 1);
    bool flashLogical(const OtaPartition& part, int partIdx = 0, int partCount = 1);
    bool resizeLogicalPartition(const std::string& dmName, uint64_t newSize);
    uint64_t getBlockDevSize(const std::string& path);
    std::string getDmDevPath(const std::string& dmName);
    // Run a shell command through the staged shell (posix_spawn, never fork: see execCommand).
    // A command still running after timeoutSec is killed and fails.
    bool execCommand(const std::string& cmd, std::string* output = nullptr, int timeoutSec = 600);
    void stopFramework();

    // ---- reliable writes ----------------------------------------------------------------
    // Decompress a partition image to the staging file and prove the copy is right: its SHA-256
    // must match the manifest's sha256_uncompressed (re-decompressed up to 3 times otherwise).
    // On success *stagingSha holds the hash every later write is checked against and *size the
    // number of bytes to write.
    bool prepareStaging(const OtaPartition& part, const std::string& stagingFile,
                        int partIdx, int partCount, std::string* stagingSha, uint64_t* size);
    enum class WriteResult { Ok, SourceBad, WriteFailed };
    // Write a file to a block device and prove it landed: the bytes sent must hash to `expected`
    // (else the source file itself is bad), and a read-back past the page cache must hash to
    // `expected`. A failed or mismatched write is retried, up to kWriteAttempts in all.
    WriteResult writeVerified(const std::string& file, const std::string& dev, uint64_t size,
                              const std::string& expected, const std::string& name,
                              FlashPhase phase, int partIdx, int partCount);
    // The places a logical partition's data lives according to the super metadata of the slot
    // being updated: exactly what init will map on the next boot. Empty if the partition is not
    // there or uses extents this cannot read.
    std::vector<ByteRange> logicalRanges(const std::string& partName);
    // Pin the code every running process has mapped from /system, /vendor and the APEXes, so
    // nothing pages its instructions back in from a partition while it is being overwritten
    // (the GPU driver in this process, SurfaceFlinger and the composer, vold, init).
    void pinRunningCode();
    // Rewrite a partition that failed the final verification. Logical partitions are written
    // through their metadata ranges on super, which is where the next boot will read them.
    bool repairPartition(const OtaPartition& part, int partIdx, int partCount);
    // The physical partitions' original contents, kept until the update has been written so a
    // physical partition that cannot be written reliably is put back before anything else changes.
    struct PhysicalOriginal { std::string name, device, file, sha; uint64_t size; };
    bool savePhysicalOriginal(const std::string& name, const std::string& dev, uint64_t size,
                              PhysicalOriginal* out);
    bool restorePhysicalOriginals();
    std::vector<PhysicalOriginal> mPhysicalOriginals;
    // Originals a rollback could not put back. A Retry must restore from these, never re-save the
    // half-written partition over them, so they survive into the next flash() of this process.
    std::vector<PhysicalOriginal> mUnrestored;
    std::map<std::string, std::string> mExpectedSha;   // partition -> SHA-256 of what was written
    std::set<std::string> mLeftUnchanged;             // write-protected physical partitions left as they were
    std::vector<std::pair<void*, size_t>> mPinned;     // pinned mappings, kept until reboot
    static constexpr int kWriteAttempts = 3;

    // ---- fault injection (debuggable builds only) -------------------------------------
    // /data/gammaos_ota/faults, a comma list read (and deleted) when a flash starts, to prove the recovery paths
    // on a real device: stage:<part>:<n> corrupts the staging copy after decompression n times,
    // write:<part>:<n> corrupts the partition after a write n times (the read-back must catch
    // it), final:<part> corrupts it just before the final verification, exec_crash:<n> makes the
    // next n helper commands die by a signal, ui_crash faults the renderer mid-write.
    std::map<std::string, int> mFaults;
    void loadFaults();
    bool takeFault(const std::string& key);
    void corruptDevice(const std::string& dev, uint64_t offset);
    void dropCaches();
    void dumpSuperMetadata(const char* label);

    // Staged binaries live under /data/* so the dynamic linker matches the
    // dir.system rule in /linkerconfig/ld.config.txt and the re-exec'd binary
    // inherits the full [system] namespace (including sphal) — required to
    // load HIDL gralloc mapper HALs via dlopen. Paths under /dev/* match no
    // dir rule, fall back to a minimal namespace, and abort with
    // "gralloc-mapper is missing" as soon as any surface is touched.
    static constexpr const char* STAGE_DIR = "/data/gammaos-ota-stage";
    static constexpr const char* BACKUP_DIR = "/data/gammaos_ota/backup";
    static constexpr const char* OTA_DIR = "/data/gammaos_ota";
    static constexpr const char* STAGING_DIR = "/data/gammaos_ota/staging";
    static constexpr const char* ORIGINALS_DIR = "/data/gammaos_ota/originals";

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
