/*
 * Copyright (C) 2019 The Android Open Source Project
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

#pragma once

#include <string>
#include <unordered_set>
#include <vector>

namespace android {
namespace apex {

static constexpr const char* kApexDataDir = "/data/apex";
static constexpr const char* kActiveApexPackagesDataDir = "/data/apex/active";
static constexpr const char* kApexBackupDir = "/data/apex/backup";
static constexpr const char* kApexHashTreeDir = "/data/apex/hashtree";
static constexpr const char* kApexDecompressedDir = "/data/apex/decompressed";
static constexpr const char* kOtaReservedDir = "/data/apex/ota_reserved";
static constexpr const char* kApexPackageSystemDir = "/system/apex";
static constexpr const char* kApexPackageSystemExtDir = "/system_ext/apex";
static constexpr const char* kApexPackageVendorDir = "/vendor/apex";
static const std::vector<std::string> kApexPackageBuiltinDirs = {
    kApexPackageSystemDir,
    kApexPackageSystemExtDir,
    "/product/apex",
    kApexPackageVendorDir,
};
static constexpr const char* kApexRoot = "/apex";
static constexpr const char* kStagedSessionsDir = "/data/app-staging";

static constexpr const char* kApexDataSubDir = "apexdata";
static constexpr const char* kApexSharedLibsSubDir = "sharedlibs";
static constexpr const char* kApexSnapshotSubDir = "apexrollback";
static constexpr const char* kPreRestoreSuffix = "-prerestore";

static constexpr const char* kDeSysDataDir = "/data/misc";
static constexpr const char* kDeNDataDir = "/data/misc_de";
static constexpr const char* kCeDataDir = "/data/misc_ce";

static constexpr const char* kApexPackageSuffix = ".apex";
static constexpr const char* kCompressedApexPackageSuffix = ".capex";
static constexpr const char* kDecompressedApexPackageSuffix =
    ".decompressed.apex";
static constexpr const char* kOtaApexPackageSuffix = ".ota.apex";

static constexpr const char* kManifestFilenameJson = "apex_manifest.json";
static constexpr const char* kManifestFilenamePb = "apex_manifest.pb";

static constexpr const char* kApexInfoList = "apex-info-list.xml";

// These should be in-sync with system/sepolicy/private/property_contexts
static constexpr const char* kApexStatusSysprop = "apexd.status";
static constexpr const char* kApexStatusStarting = "starting";
static constexpr const char* kApexStatusActivated = "activated";
static constexpr const char* kApexStatusReady = "ready";

static constexpr const char* kMultiApexSelectPersistPrefix =
    "persist.vendor.apex.";
static constexpr const char* kMultiApexSelectBootconfigPrefix =
    "ro.boot.vendor.apex.";
static const std::vector<std::string> kMultiApexSelectPrefix = {
    // Check persist props first, to allow users to override bootconfig.
    kMultiApexSelectPersistPrefix,
    kMultiApexSelectBootconfigPrefix,
};

static constexpr const char* kVmPayloadMetadataPartitionProp =
    "apexd.payload_metadata.path";
static constexpr const std::chrono::seconds kBlockApexWaitTime(10);

static constexpr const char* kApexAllReadyProp = "apex.all.ready";
static constexpr const char* kCtlApexLoadSysprop = "ctl.apex_load";
static constexpr const char* kCtlApexUnloadSysprop = "ctl.apex_unload";

// Banned APEX names
static const std::unordered_set<std::string> kBannedApexName = {
    kApexSharedLibsSubDir,  // To avoid conflicts with predefined
                            // /apex/sharedlibs directory
};
}  // namespace apex
}  // namespace android
