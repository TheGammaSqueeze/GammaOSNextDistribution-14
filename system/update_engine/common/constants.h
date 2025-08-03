//
// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#ifndef UPDATE_ENGINE_COMMON_CONSTANTS_H_
#define UPDATE_ENGINE_COMMON_CONSTANTS_H_

#include <cstdint>

namespace chromeos_update_engine {
// The root path of all exclusion prefs.
static constexpr const auto& kExclusionPrefsSubDir = "exclusion";

// The root path of all DLC metadata.
static constexpr const auto& kDlcPrefsSubDir = "dlc";

// Directory for AU prefs that are preserved across powerwash.
static constexpr const auto& kPowerwashSafePrefsSubDirectory =
    "update_engine/prefs";

// The location where we store the AU preferences (state etc).
static constexpr const auto& kPrefsSubDirectory = "prefs";

// Path to the stateful partition on the root filesystem.
static constexpr const auto& kStatefulPartition = "/mnt/stateful_partition";

// Path to the post install command, relative to the partition.
static constexpr const auto& kPostinstallDefaultScript = "postinst";

// Constants related to preferences.
// Constants defining keys for the persisted state of update engine.
static constexpr const auto& kPrefsAttemptInProgress = "attempt-in-progress";
static constexpr const auto& kPrefsBackoffExpiryTime = "backoff-expiry-time";
static constexpr const auto& kPrefsBootId = "boot-id";
static constexpr const auto& kPrefsCurrentBytesDownloaded =
    "current-bytes-downloaded";
static constexpr const auto& kPrefsCurrentResponseSignature =
    "current-response-signature";
static constexpr const auto& kPrefsCurrentUrlFailureCount =
    "current-url-failure-count";
static constexpr const auto& kPrefsCurrentUrlIndex = "current-url-index";
static constexpr const auto& kPrefsDailyMetricsLastReportedAt =
    "daily-metrics-last-reported-at";
static constexpr const auto& kPrefsDeltaUpdateFailures =
    "delta-update-failures";
static constexpr const auto& kPrefsDynamicPartitionMetadataUpdated =
    "dynamic-partition-metadata-updated";
static constexpr const auto& kPrefsFullPayloadAttemptNumber =
    "full-payload-attempt-number";
static constexpr const auto& kPrefsInstallDateDays = "install-date-days";
static constexpr const auto& kPrefsLastActivePingDay = "last-active-ping-day";
static constexpr const auto& kPrefsLastRollCallPingDay =
    "last-roll-call-ping-day";
static constexpr const auto& kPrefsManifestMetadataSize =
    "manifest-metadata-size";
static constexpr const auto& kPrefsManifestSignatureSize =
    "manifest-signature-size";
static constexpr const auto& kPrefsMetricsAttemptLastReportingTime =
    "metrics-attempt-last-reporting-time";
static constexpr const auto& kPrefsMetricsCheckLastReportingTime =
    "metrics-check-last-reporting-time";
static constexpr const auto& kPrefsNoIgnoreBackoff = "no-ignore-backoff";
static constexpr const auto& kPrefsNumReboots = "num-reboots";
static constexpr const auto& kPrefsNumResponsesSeen = "num-responses-seen";
static constexpr const auto& kPrefsOmahaCohort = "omaha-cohort";
static constexpr const auto& kPrefsOmahaCohortHint = "omaha-cohort-hint";
static constexpr const auto& kPrefsOmahaCohortName = "omaha-cohort-name";
static constexpr const auto& kPrefsOmahaEolDate = "omaha-eol-date";
static constexpr const auto& kPrefsP2PEnabled = "p2p-enabled";
static constexpr const auto& kPrefsP2PFirstAttemptTimestamp =
    "p2p-first-attempt-timestamp";
static constexpr const auto& kPrefsP2PNumAttempts = "p2p-num-attempts";
static constexpr const auto& kPrefsPayloadAttemptNumber =
    "payload-attempt-number";
static constexpr const auto& kPrefsTestUpdateCheckIntervalTimeout =
    "test-update-check-interval-timeout";
// Keep |kPrefsPingActive| in sync with |kDlcMetadataFilePingActive| in
// dlcservice.
static constexpr const auto& kPrefsPingActive = "active";
static constexpr const auto& kPrefsPingLastActive = "date_last_active";
static constexpr const auto& kPrefsPingLastRollcall = "date_last_rollcall";
static constexpr const auto& kPrefsLastFp = "last-fp";
static constexpr const auto& kPrefsPostInstallSucceeded =
    "post-install-succeeded";
static constexpr const auto& kPrefsPreviousVersion = "previous-version";
static constexpr const auto& kPrefsResumedUpdateFailures =
    "resumed-update-failures";
static constexpr const auto& kPrefsRollbackHappened = "rollback-happened";
static constexpr const auto& kPrefsRollbackVersion = "rollback-version";
static constexpr const auto& kPrefsChannelOnSlotPrefix = "channel-on-slot-";
static constexpr const auto& kPrefsSystemUpdatedMarker =
    "system-updated-marker";
static constexpr const auto& kPrefsTargetVersionAttempt =
    "target-version-attempt";
static constexpr const auto& kPrefsTargetVersionInstalledFrom =
    "target-version-installed-from";
static constexpr const auto& kPrefsTargetVersionUniqueId =
    "target-version-unique-id";
static constexpr const auto& kPrefsTotalBytesDownloaded =
    "total-bytes-downloaded";
static constexpr const auto& kPrefsUpdateCheckCount = "update-check-count";
static constexpr const auto& kPrefsUpdateCheckResponseHash =
    "update-check-response-hash";
static constexpr const auto& kPrefsUpdateCompletedBootTime =
    "update-completed-boot-time";
static constexpr const auto& kPrefsUpdateCompletedOnBootId =
    "update-completed-on-boot-id";
static constexpr const auto& kPrefsUpdateDurationUptime =
    "update-duration-uptime";
static constexpr const auto& kPrefsUpdateFirstSeenAt = "update-first-seen-at";
static constexpr const auto& kPrefsUpdateOverCellularPermission =
    "update-over-cellular-permission";
static constexpr const auto& kPrefsUpdateOverCellularTargetVersion =
    "update-over-cellular-target-version";
static constexpr const auto& kPrefsUpdateOverCellularTargetSize =
    "update-over-cellular-target-size";
static constexpr const auto& kPrefsUpdateServerCertificate =
    "update-server-cert";
static constexpr const auto& kPrefsUpdateStateNextDataLength =
    "update-state-next-data-length";
static constexpr const auto& kPrefsUpdateStateNextDataOffset =
    "update-state-next-data-offset";
static constexpr const auto& kPrefsUpdateStateNextOperation =
    "update-state-next-operation";
static constexpr const auto& kPrefsUpdateStatePayloadIndex =
    "update-state-payload-index";
static constexpr const auto& kPrefsUpdateStateSHA256Context =
    "update-state-sha-256-context";
static constexpr const auto& kPrefsUpdateStateSignatureBlob =
    "update-state-signature-blob";
static constexpr const auto& kPrefsUpdateStateSignedSHA256Context =
    "update-state-signed-sha-256-context";
static constexpr const auto& kPrefsUpdateBootTimestampStart =
    "update-boot-timestamp-start";
static constexpr const auto& kPrefsUpdateTimestampStart =
    "update-timestamp-start";
static constexpr const auto& kPrefsUrlSwitchCount = "url-switch-count";
static constexpr const auto& kPrefsVerityWritten = "verity-written";
static constexpr const auto& kPrefsWallClockScatteringWaitPeriod =
    "wall-clock-wait-period";
static constexpr const auto& kPrefsWallClockStagingWaitPeriod =
    "wall-clock-staging-wait-period";
static constexpr const auto& kPrefsManifestBytes = "manifest-bytes";
static constexpr const auto& kPrefsPreviousSlot = "previous-slot";

// Keys used when storing and loading payload properties.
// These four fields are generated by scripts/brillo_update_payload.
static constexpr const auto& kPayloadPropertyFileSize = "FILE_SIZE";
static constexpr const auto& kPayloadPropertyFileHash = "FILE_HASH";
static constexpr const auto& kPayloadPropertyMetadataSize = "METADATA_SIZE";
static constexpr const auto& kPayloadPropertyMetadataHash = "METADATA_HASH";
// The Authorization: HTTP header to be sent when downloading the payload.
static constexpr const auto& kPayloadPropertyAuthorization = "AUTHORIZATION";
// The User-Agent HTTP header to be sent when downloading the payload.
static constexpr const auto& kPayloadPropertyUserAgent = "USER_AGENT";
// Set "POWERWASH=1" to powerwash (factory data reset) the device after
// applying the update.
static constexpr const auto& kPayloadPropertyPowerwash = "POWERWASH";
static constexpr const auto& kPayloadPropertySplDowngrade = "SPL_DOWNGRADE";
// The network id to pass to android_setprocnetwork before downloading.
// This can be used to zero-rate OTA traffic by sending it over the correct
// network.
static constexpr const auto& kPayloadPropertyNetworkId = "NETWORK_ID";

// Proxy URL to use for downloading OTA. This will be forwarded to libcurl
static constexpr const auto& kPayloadPropertyNetworkProxy = "NETWORK_PROXY";

// Set Virtual AB Compression's compression algorithm to "none", but still use
// userspace snapshots and snapuserd for update installation.
static constexpr const auto& kPayloadVABCNone = "VABC_NONE";
// Enable/Disable VABC, falls back on plain VAB
static constexpr const auto& kPayloadDisableVABC = "DISABLE_VABC";
// Enable multi-threaded compression for VABC
static constexpr const auto& kPayloadEnableThreading = "ENABLE_THREADING";
// Enable batched writes for VABC
static constexpr const auto& kPayloadBatchedWrites = "BATCHED_WRITES";

// Max retry count for download
static constexpr const auto& kPayloadDownloadRetry = "DOWNLOAD_RETRY";

// Set "SWITCH_SLOT_ON_REBOOT=0" to skip marking the updated partitions active.
// The default is 1 (always switch slot if update succeeded).
static constexpr const auto& kPayloadPropertySwitchSlotOnReboot =
    "SWITCH_SLOT_ON_REBOOT";
// Set "RUN_POST_INSTALL=0" to skip running optional post install.
// The default is 1 (always run post install).
static constexpr const auto& kPayloadPropertyRunPostInstall =
    "RUN_POST_INSTALL";

static constexpr const auto& kOmahaUpdaterVersion = "0.1.0.0";

// X-Goog-Update headers.
// X-Goog-Update headers.
static constexpr const auto& kXGoogleUpdateInteractivity =
    "X-Goog-Update-Interactivity";
static constexpr const auto& kXGoogleUpdateAppId = "X-Goog-Update-AppId";
static constexpr const auto& kXGoogleUpdateUpdater = "X-Goog-Update-Updater";
static constexpr const auto& kXGoogleUpdateSessionId = "X-Goog-SessionId";

// Proxy URL for direction connection
static constexpr const auto& kNoProxy = "direct://";

// A download source is any combination of protocol and server (that's of
// interest to us when looking at UMA metrics) using which we may download
// the payload.
typedef enum {
  kDownloadSourceHttpsServer,  // UMA Binary representation: 0001
  kDownloadSourceHttpServer,   // UMA Binary representation: 0010
  kDownloadSourceHttpPeer,     // UMA Binary representation: 0100

  // Note: Add new sources only above this line.
  kNumDownloadSources
} DownloadSource;

// A payload can be a Full or Delta payload. In some cases, a Full payload is
// used even when a Delta payload was available for the update, called here
// ForcedFull. The PayloadType enum is only used to send UMA metrics about the
// successfully applied payload.
typedef enum {
  kPayloadTypeFull,
  kPayloadTypeDelta,
  kPayloadTypeForcedFull,

  // Note: Add new payload types only above this line.
  kNumPayloadTypes
} PayloadType;

// Maximum number of times we'll allow using p2p for the same update payload.
constexpr int kMaxP2PAttempts = 10;

// Maximum wallclock time we allow attempting to update using p2p for
// the same update payload - five days.
constexpr int kMaxP2PAttemptTimeSeconds = 5 * 24 * 60 * 60;

// The maximum amount of time to spend waiting for p2p-client(1) to
// return while waiting in line to use the LAN - six hours.
constexpr int kMaxP2PNetworkWaitTimeSeconds = 6 * 60 * 60;

// The maximum number of payload files to keep in /var/cache/p2p.
constexpr int kMaxP2PFilesToKeep = 3;

// The maximum number of days to keep a p2p file;
constexpr int kMaxP2PFileAgeDays = 5;

// The default number of UMA buckets for metrics.
constexpr int kNumDefaultUmaBuckets = 50;

// General constexprants
constexpr int kNumBytesInOneMiB = 1024 * 1024;

// Number of redirects allowed when downloading.
constexpr int kDownloadMaxRedirects = 10;

// The minimum average speed that downloads must sustain...
//
// This is set low because some devices may have very poor
// connectivity and we want to make as much forward progress as
// possible. For p2p this is high (25 kB/second) since we can assume
// high bandwidth (same LAN) and we want to fail fast.
constexpr int kDownloadLowSpeedLimitBps = 1;
constexpr int kDownloadP2PLowSpeedLimitBps = 25 * 1000;

// ... measured over this period.
//
// For non-official builds (e.g. typically built on a developer's
// workstation and served via devserver) bump this since it takes time
// for the workstation to generate the payload. For normal operation
// and p2p, make this relatively low since we want to fail fast in
// those cases.
constexpr int kDownloadLowSpeedTimeSeconds = 30;
constexpr int kDownloadDevModeLowSpeedTimeSeconds = 180;
constexpr int kDownloadP2PLowSpeedTimeSeconds = 60;

// The maximum amount of HTTP server reconnect attempts.
//
// This is set high in order to maximize the attempt's chance of
// succeeding. When using p2p, this is low in order to fail fast.
constexpr int kDownloadMaxRetryCount = 20;
constexpr int kDownloadMaxRetryCountOobeNotComplete = 3;
constexpr int kDownloadMaxRetryCountInteractive = 3;
constexpr int kDownloadP2PMaxRetryCount = 5;

// The connect timeout, in seconds.
//
// This is set high because some devices may have very poor
// connectivity and we may be using HTTPS which involves complicated
// multi-roundtrip setup. For p2p, this is set low because we can
// the server is on the same LAN and we want to fail fast.
constexpr int kDownloadConnectTimeoutSeconds = 30;
constexpr int kDownloadP2PConnectTimeoutSeconds = 5;

// Size in bytes of SHA256 hash.
constexpr int kSHA256Size = 32;

// A hardcoded label to mark end of all InstallOps
// This number must be greater than number of install ops.
// Number of install ops is bounded by number of blocks on any partition.
// Currently, the block size is 4096. Using |kEndOfInstallLabel| of 2^48 will
// allow partitions with 2^48 * 4096 = 2^60 bytes. That's 1024PB? Partitions on
// android aren't getting that big any time soon.
constexpr uint64_t kEndOfInstallLabel = (1ULL << 48);

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_COMMON_CONSTANTS_H_
