/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "VintfObject.h"

#include <dirent.h>

#include <algorithm>
#include <functional>
#include <memory>
#include <mutex>
#include <vector>

#include <aidl/metadata.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android-base/strings.h>
#include <hidl/metadata.h>

#include "Apex.h"
#include "CompatibilityMatrix.h"
#include "VintfObjectUtils.h"
#include "constants-private.h"
#include "include/vintf/FqInstance.h"
#include "parse_string.h"
#include "parse_xml.h"
#include "utils.h"

using std::placeholders::_1;
using std::placeholders::_2;
using std::string_literals::operator""s;

namespace android {
namespace vintf {

using namespace details;

#ifdef LIBVINTF_TARGET
static constexpr bool kIsTarget = true;
#else
static constexpr bool kIsTarget = false;
#endif

static std::unique_ptr<FileSystem> createDefaultFileSystem() {
    std::unique_ptr<FileSystem> fileSystem;
    if (kIsTarget) {
        fileSystem = std::make_unique<details::FileSystemImpl>();
    } else {
        fileSystem = std::make_unique<details::FileSystemNoOp>();
    }
    return fileSystem;
}

static std::unique_ptr<PropertyFetcher> createDefaultPropertyFetcher() {
    std::unique_ptr<PropertyFetcher> propertyFetcher;
    if (kIsTarget) {
        propertyFetcher = std::make_unique<details::PropertyFetcherImpl>();
    } else {
        propertyFetcher = std::make_unique<details::PropertyFetcherNoOp>();
    }
    return propertyFetcher;
}

static std::unique_ptr<ApexInterface> createDefaultApex() {
    return std::make_unique<details::Apex>();
}

// Check whether the current executable is allowed to use libvintf.
// Allowed binaries:
// - host binaries
// - tests
// - {hw,}servicemanager
static bool isAllowedToUseLibvintf() {
    if constexpr (!kIsTarget) {
        return true;
    }

    auto execPath = android::base::GetExecutablePath();
    if (android::base::StartsWith(execPath, "/data/")) {
        return true;
    }

    std::vector<std::string> allowedBinaries{
        "/system/bin/servicemanager",
        "/system/bin/hwservicemanager",
        "/system_ext/bin/hwservicemanager",
        // Java: boot time VINTF check
        "/system/bin/app_process32",
        "/system/bin/app_process64",
        // These aren't daemons so the memory impact is less concerning.
        "/system/bin/lshal",
    };

    return std::find(allowedBinaries.begin(), allowedBinaries.end(), execPath) !=
           allowedBinaries.end();
}

std::shared_ptr<VintfObject> VintfObject::GetInstance() {
    static details::LockedSharedPtr<VintfObject> sInstance{};
    std::unique_lock<std::mutex> lock(sInstance.mutex);
    if (sInstance.object == nullptr) {
        if (!isAllowedToUseLibvintf()) {
            LOG(ERROR) << "libvintf-usage-violation: Executable "
                       << android::base::GetExecutablePath()
                       << " should not use libvintf. It should query VINTF "
                       << "metadata via servicemanager";
        }

        sInstance.object = std::shared_ptr<VintfObject>(VintfObject::Builder().build().release());
    }
    return sInstance.object;
}

std::shared_ptr<const HalManifest> VintfObject::GetDeviceHalManifest() {
    return GetInstance()->getDeviceHalManifest();
}

std::shared_ptr<const HalManifest> VintfObject::getDeviceHalManifest() {
    // Check if any updates to the APEX data, if so rebuild the manifest
    {
        std::lock_guard<std::mutex> lock(mDeviceManifest.mutex);
        if (mDeviceManifest.fetchedOnce) {
            if (getApex()->HasUpdate(getFileSystem().get(), getPropertyFetcher().get())) {
                LOG(INFO) << __func__ << ": Reloading VINTF information.";
                mDeviceManifest.object = nullptr;
                mDeviceManifest.fetchedOnce = false;
                // TODO(b/242070736): only APEX data needs to be updated
            }
        }
    }

    return Get(__func__, &mDeviceManifest,
               std::bind(&VintfObject::fetchDeviceHalManifest, this, _1, _2));
}

std::shared_ptr<const HalManifest> VintfObject::GetFrameworkHalManifest() {
    return GetInstance()->getFrameworkHalManifest();
}

std::shared_ptr<const HalManifest> VintfObject::getFrameworkHalManifest() {
    return Get(__func__, &mFrameworkManifest,
               std::bind(&VintfObject::fetchFrameworkHalManifest, this, _1, _2));
}

std::shared_ptr<const CompatibilityMatrix> VintfObject::GetDeviceCompatibilityMatrix() {
    return GetInstance()->getDeviceCompatibilityMatrix();
}

std::shared_ptr<const CompatibilityMatrix> VintfObject::getDeviceCompatibilityMatrix() {
    return Get(__func__, &mDeviceMatrix, std::bind(&VintfObject::fetchDeviceMatrix, this, _1, _2));
}

std::shared_ptr<const CompatibilityMatrix> VintfObject::GetFrameworkCompatibilityMatrix() {
    return GetInstance()->getFrameworkCompatibilityMatrix();
}

std::shared_ptr<const CompatibilityMatrix> VintfObject::getFrameworkCompatibilityMatrix() {
    // To avoid deadlock, get device manifest before any locks.
    auto deviceManifest = getDeviceHalManifest();

    std::string error;
    auto kernelLevel = getKernelLevel(&error);
    if (kernelLevel == Level::UNSPECIFIED) {
        LOG(WARNING) << "getKernelLevel: " << error;
    }

    std::unique_lock<std::mutex> _lock(mFrameworkCompatibilityMatrixMutex);

    auto combined = Get(__func__, &mCombinedFrameworkMatrix,
                        std::bind(&VintfObject::getCombinedFrameworkMatrix, this, deviceManifest,
                                  kernelLevel, _1, _2));
    if (combined != nullptr) {
        return combined;
    }

    return Get(__func__, &mFrameworkMatrix,
               std::bind(&CompatibilityMatrix::fetchAllInformation, _1, getFileSystem().get(),
                         kSystemLegacyMatrix, _2));
}

status_t VintfObject::getCombinedFrameworkMatrix(
    const std::shared_ptr<const HalManifest>& deviceManifest, Level kernelLevel,
    CompatibilityMatrix* out, std::string* error) {
    std::vector<CompatibilityMatrix> matrixFragments;
    auto matrixFragmentsStatus = getAllFrameworkMatrixLevels(&matrixFragments, error);
    if (matrixFragmentsStatus != OK) {
        return matrixFragmentsStatus;
    }
    if (matrixFragments.empty()) {
        if (error && error->empty()) {
            *error = "Cannot get framework matrix for each FCM version for unknown error.";
        }
        return NAME_NOT_FOUND;
    }

    Level deviceLevel = Level::UNSPECIFIED;

    if (deviceManifest != nullptr) {
        deviceLevel = deviceManifest->level();
    }

    if (deviceLevel == Level::UNSPECIFIED) {
        // Cannot infer FCM version. Combine all matrices by assuming
        // Shipping FCM Version == min(all supported FCM Versions in the framework)
        for (auto&& fragment : matrixFragments) {
            Level fragmentLevel = fragment.level();
            if (fragmentLevel != Level::UNSPECIFIED && deviceLevel > fragmentLevel) {
                deviceLevel = fragmentLevel;
            }
        }
    }

    if (deviceLevel == Level::UNSPECIFIED) {
        // None of the fragments specify any FCM version. Should never happen except
        // for inconsistent builds.
        if (error) {
            *error = "No framework compatibility matrix files under "s + kSystemVintfDir +
                     " declare FCM version.";
        }
        return NAME_NOT_FOUND;
    }

    auto combined = CompatibilityMatrix::combine(deviceLevel, kernelLevel, &matrixFragments, error);
    if (combined == nullptr) {
        return BAD_VALUE;
    }
    *out = std::move(*combined);
    return OK;
}

// Load and combine all of the manifests in a directory
// If forceSchemaType, all fragment manifests are coerced into manifest->type().
status_t VintfObject::addDirectoryManifests(const std::string& directory, HalManifest* manifest,
                                            bool forceSchemaType, std::string* error) {
    std::vector<std::string> fileNames;
    status_t err = getFileSystem()->listFiles(directory, &fileNames, error);
    // if the directory isn't there, that's okay
    if (err == NAME_NOT_FOUND) {
        if (error) {
            error->clear();
        }
        return OK;
    }
    if (err != OK) return err;

    for (const std::string& file : fileNames) {
        // Only adds HALs because all other things are added by libvintf
        // itself for now.
        HalManifest fragmentManifest;
        err = fetchOneHalManifest(directory + file, &fragmentManifest, error);
        if (err != OK) return err;

        if (forceSchemaType) {
            fragmentManifest.setType(manifest->type());
        }

        if (!manifest->addAll(&fragmentManifest, error)) {
            if (error) {
                error->insert(0, "Cannot add manifest fragment " + directory + file + ": ");
            }
            return UNKNOWN_ERROR;
        }
    }

    return OK;
}

// Create device HalManifest
// 1. Create manifest based on /vendor /odm data
// 2. Add any APEX data
status_t VintfObject::fetchDeviceHalManifest(HalManifest* out, std::string* error) {
    auto status = fetchDeviceHalManifestMinusApex(out, error);
    if (status != OK) {
        return status;
    }
    return fetchDeviceHalManifestApex(out, error);
}

// Fetch fragments from apexes originated from /vendor.
// For now, we don't have /odm apexes.
status_t VintfObject::fetchDeviceHalManifestApex(HalManifest* out, std::string* error) {
    std::vector<std::string> dirs;
    status_t status =
        getApex()->DeviceVintfDirs(getFileSystem().get(), getPropertyFetcher().get(), &dirs, error);
    if (status != OK) {
        return status;
    }

    if (dirs.empty()) {
        return OK;
    }
    // Create HalManifest for all APEX HALs so that the apex defined attribute can
    // be set.
    HalManifest apexManifest;
    for (const auto& dir : dirs) {
        status = addDirectoryManifests(dir, &apexManifest, false, error);
        if (status != OK) {
            return status;
        }
    }

    // Add APEX HALs to out
    if (!out->addAllHals(&apexManifest, error)) {
        return UNKNOWN_ERROR;
    }
    return OK;
}

// Priority for loading vendor manifest:
// 1. Vendor manifest + device fragments + ODM manifest (optional) + odm fragments
// 2. Vendor manifest + device fragments
// 3. ODM manifest (optional) + odm fragments
// 4. /vendor/manifest.xml (legacy, no fragments)
// where:
// A + B means unioning <hal> tags from A and B. If B declares an override, then this takes priority
// over A.
status_t VintfObject::fetchDeviceHalManifestMinusApex(HalManifest* out, std::string* error) {
    HalManifest vendorManifest;
    status_t vendorStatus = fetchVendorHalManifest(&vendorManifest, error);
    if (vendorStatus != OK && vendorStatus != NAME_NOT_FOUND) {
        return vendorStatus;
    }

    if (vendorStatus == OK) {
        *out = std::move(vendorManifest);
        status_t fragmentStatus = addDirectoryManifests(kVendorManifestFragmentDir, out,
                                                        false /* forceSchemaType*/, error);
        if (fragmentStatus != OK) {
            return fragmentStatus;
        }
    }

    HalManifest odmManifest;
    status_t odmStatus = fetchOdmHalManifest(&odmManifest, error);
    if (odmStatus != OK && odmStatus != NAME_NOT_FOUND) {
        return odmStatus;
    }

    if (vendorStatus == OK) {
        if (odmStatus == OK) {
            if (!out->addAll(&odmManifest, error)) {
                if (error) {
                    error->insert(0, "Cannot add ODM manifest :");
                }
                return UNKNOWN_ERROR;
            }
        }
        return addDirectoryManifests(kOdmManifestFragmentDir, out, false /* forceSchemaType */,
                                     error);
    }

    // vendorStatus != OK, "out" is not changed.
    if (odmStatus == OK) {
        *out = std::move(odmManifest);
        return addDirectoryManifests(kOdmManifestFragmentDir, out, false /* forceSchemaType */,
                                     error);
    }

    // Use legacy /vendor/manifest.xml
    return out->fetchAllInformation(getFileSystem().get(), kVendorLegacyManifest, error);
}

// Priority:
// 1. if {vendorSku} is defined, /vendor/etc/vintf/manifest_{vendorSku}.xml
// 2. /vendor/etc/vintf/manifest.xml
// where:
// {vendorSku} is the value of ro.boot.product.vendor.sku
status_t VintfObject::fetchVendorHalManifest(HalManifest* out, std::string* error) {
    status_t status;

    std::string vendorSku;
    vendorSku = getPropertyFetcher()->getProperty("ro.boot.product.vendor.sku", "");

    if (!vendorSku.empty()) {
        status =
            fetchOneHalManifest(kVendorVintfDir + "manifest_"s + vendorSku + ".xml", out, error);
        if (status == OK || status != NAME_NOT_FOUND) {
            return status;
        }
    }

    status = fetchOneHalManifest(kVendorManifest, out, error);
    if (status == OK || status != NAME_NOT_FOUND) {
        return status;
    }

    return NAME_NOT_FOUND;
}

std::string getOdmProductManifestFile(const std::string& dir, const ::std::string& sku) {
    return sku.empty() ? "" : dir + "manifest_"s + sku + ".xml";
}

// "out" is written to iff return status is OK.
// Priority:
// 1. if {sku} is defined, /odm/etc/vintf/manifest_{sku}.xml
// 2. /odm/etc/vintf/manifest.xml
// 3. if {sku} is defined, /odm/etc/manifest_{sku}.xml
// 4. /odm/etc/manifest.xml
// where:
// {sku} is the value of ro.boot.product.hardware.sku
status_t VintfObject::fetchOdmHalManifest(HalManifest* out, std::string* error) {
    status_t status;
    std::string productModel;
    productModel = getPropertyFetcher()->getProperty("ro.boot.product.hardware.sku", "");

    const std::string productFile = getOdmProductManifestFile(kOdmVintfDir, productModel);
    if (!productFile.empty()) {
        status = fetchOneHalManifest(productFile, out, error);
        if (status == OK || status != NAME_NOT_FOUND) {
            return status;
        }
    }

    status = fetchOneHalManifest(kOdmManifest, out, error);
    if (status == OK || status != NAME_NOT_FOUND) {
        return status;
    }

    const std::string productLegacyFile =
        getOdmProductManifestFile(kOdmLegacyVintfDir, productModel);
    if (!productLegacyFile.empty()) {
        status = fetchOneHalManifest(productLegacyFile, out, error);
        if (status == OK || status != NAME_NOT_FOUND) {
            return status;
        }
    }

    status = fetchOneHalManifest(kOdmLegacyManifest, out, error);
    if (status == OK || status != NAME_NOT_FOUND) {
        return status;
    }

    return NAME_NOT_FOUND;
}

// Fetch one manifest.xml file. "out" is written to iff return status is OK.
// Returns NAME_NOT_FOUND if file is missing.
status_t VintfObject::fetchOneHalManifest(const std::string& path, HalManifest* out,
                                          std::string* error) {
    HalManifest ret;
    status_t status = ret.fetchAllInformation(getFileSystem().get(), path, error);
    if (status == OK) {
        *out = std::move(ret);
    }
    return status;
}

status_t VintfObject::fetchDeviceMatrix(CompatibilityMatrix* out, std::string* error) {
    CompatibilityMatrix etcMatrix;
    if (etcMatrix.fetchAllInformation(getFileSystem().get(), kVendorMatrix, error) == OK) {
        *out = std::move(etcMatrix);
        return OK;
    }
    return out->fetchAllInformation(getFileSystem().get(), kVendorLegacyMatrix, error);
}

// Priority:
// 1. /system/etc/vintf/manifest.xml
//    + /system/etc/vintf/manifest/*.xml if they exist
//    + /product/etc/vintf/manifest.xml if it exists
//    + /product/etc/vintf/manifest/*.xml if they exist
// 2. (deprecated) /system/manifest.xml
status_t VintfObject::fetchUnfilteredFrameworkHalManifest(HalManifest* out, std::string* error) {
    auto systemEtcStatus = fetchOneHalManifest(kSystemManifest, out, error);
    if (systemEtcStatus == OK) {
        auto dirStatus = addDirectoryManifests(kSystemManifestFragmentDir, out,
                                               false /* forceSchemaType */, error);
        if (dirStatus != OK) {
            return dirStatus;
        }

        std::vector<std::pair<const char*, const char*>> extensions{
            {kProductManifest, kProductManifestFragmentDir},
            {kSystemExtManifest, kSystemExtManifestFragmentDir},
        };
        for (auto&& [manifestPath, frags] : extensions) {
            HalManifest halManifest;
            auto status = fetchOneHalManifest(manifestPath, &halManifest, error);
            if (status != OK && status != NAME_NOT_FOUND) {
                return status;
            }
            if (status == OK) {
                if (!out->addAll(&halManifest, error)) {
                    if (error) {
                        error->insert(0, "Cannot add "s + manifestPath + ":");
                    }
                    return UNKNOWN_ERROR;
                }
            }

            auto fragmentStatus =
                addDirectoryManifests(frags, out, false /* forceSchemaType */, error);
            if (fragmentStatus != OK) {
                return fragmentStatus;
            }
        }

        return OK;
    } else {
        LOG(WARNING) << "Cannot fetch " << kSystemManifest << ": "
                     << (error ? *error : strerror(-systemEtcStatus));
    }

    return out->fetchAllInformation(getFileSystem().get(), kSystemLegacyManifest, error);
}

status_t VintfObject::fetchFrameworkHalManifest(HalManifest* out, std::string* error) {
    status_t status = fetchUnfilteredFrameworkHalManifest(out, error);
    if (status != OK) {
        return status;
    }
    filterHalsByDeviceManifestLevel(out);
    return OK;
}

// If deviceManifestLevel is not in the range [minLevel, maxLevel] of a HAL, remove the HAL,
// where:
//    minLevel = hal.getMinLevel(); if unspecified, -infinity
//    maxLevel = hal.getMaxLevel(); if unspecified, +infinity
//    deviceManifestLevel = deviceManifest->level(); if unspecified, -infinity
// That is, if device manifest has no level, it is treated as an infinitely old device.
void VintfObject::filterHalsByDeviceManifestLevel(HalManifest* out) {
    auto deviceManifest = getDeviceHalManifest();
    Level deviceManifestLevel =
        deviceManifest != nullptr ? deviceManifest->level() : Level::UNSPECIFIED;

    if (deviceManifest == nullptr) {
        LOG(WARNING) << "Cannot fetch device manifest to determine target FCM version to "
                        "filter framework manifest HALs properly. Treating as infinitely old "
                        "device.";
    } else if (deviceManifestLevel == Level::UNSPECIFIED) {
        LOG(WARNING)
            << "Cannot filter framework manifest HALs properly because target FCM version is "
               "unspecified in the device manifest. Treating as infinitely old device.";
    }

    out->removeHalsIf([deviceManifestLevel](const ManifestHal& hal) {
        if (hal.getMaxLevel() != Level::UNSPECIFIED) {
            if (deviceManifestLevel != Level::UNSPECIFIED &&
                hal.getMaxLevel() < deviceManifestLevel) {
                return true;
            }
        }
        if (hal.getMinLevel() != Level::UNSPECIFIED) {
            if (deviceManifestLevel == Level::UNSPECIFIED ||
                hal.getMinLevel() > deviceManifestLevel) {
                return true;
            }
        }
        return false;
    });
}

static void appendLine(std::string* error, const std::string& message) {
    if (error != nullptr) {
        if (!error->empty()) *error += "\n";
        *error += message;
    }
}

status_t VintfObject::getOneMatrix(const std::string& path, CompatibilityMatrix* out,
                                   std::string* error) {
    std::string content;
    status_t status = getFileSystem()->fetch(path, &content, error);
    if (status != OK) {
        return status;
    }
    if (!fromXml(out, content, error)) {
        if (error) {
            error->insert(0, "Cannot parse " + path + ": ");
        }
        return BAD_VALUE;
    }
    out->setFileName(path);
    return OK;
}

status_t VintfObject::getAllFrameworkMatrixLevels(std::vector<CompatibilityMatrix>* results,
                                                  std::string* error) {
    std::vector<std::string> dirs = {
        kSystemVintfDir,
        kSystemExtVintfDir,
        kProductVintfDir,
    };
    for (const auto& dir : dirs) {
        std::vector<std::string> fileNames;
        status_t listStatus = getFileSystem()->listFiles(dir, &fileNames, error);
        if (listStatus == NAME_NOT_FOUND) {
            if (error) {
                error->clear();
            }
            continue;
        }
        if (listStatus != OK) {
            return listStatus;
        }
        for (const std::string& fileName : fileNames) {
            std::string path = dir + fileName;
            CompatibilityMatrix namedMatrix;
            std::string matrixError;
            status_t matrixStatus = getOneMatrix(path, &namedMatrix, &matrixError);
            if (matrixStatus != OK) {
                // Manifests and matrices share the same dir. Client may not have enough
                // permissions to read system manifests, or may not be able to parse it.
                auto logLevel = matrixStatus == BAD_VALUE ? base::DEBUG : base::ERROR;
                LOG(logLevel) << "Framework Matrix: Ignore file " << path << ": " << matrixError;
                continue;
            }
            results->emplace_back(std::move(namedMatrix));
        }

        if (dir == kSystemVintfDir && results->empty()) {
            if (error) {
                *error = "No framework matrices under " + dir + " can be fetched or parsed.\n";
            }
            return NAME_NOT_FOUND;
        }
    }

    if (results->empty()) {
        if (error) {
            *error =
                "No framework matrices can be fetched or parsed. "
                "The following directories are searched:\n  " +
                android::base::Join(dirs, "\n  ");
        }
        return NAME_NOT_FOUND;
    }
    return OK;
}

std::shared_ptr<const RuntimeInfo> VintfObject::GetRuntimeInfo(RuntimeInfo::FetchFlags flags) {
    return GetInstance()->getRuntimeInfo(flags);
}
std::shared_ptr<const RuntimeInfo> VintfObject::getRuntimeInfo(RuntimeInfo::FetchFlags flags) {
    std::unique_lock<std::mutex> _lock(mDeviceRuntimeInfo.mutex);

    // Skip fetching information that has already been fetched previously.
    flags &= (~mDeviceRuntimeInfo.fetchedFlags);

    if (mDeviceRuntimeInfo.object == nullptr) {
        mDeviceRuntimeInfo.object = getRuntimeInfoFactory()->make_shared();
    }

    status_t status = mDeviceRuntimeInfo.object->fetchAllInformation(flags);
    if (status != OK) {
        // If only kernel FCM is needed, ignore errors when fetching RuntimeInfo because RuntimeInfo
        // is not available on host. On host, the kernel level can still be inferred from device
        // manifest.
        // If other information is needed, flag the error by returning nullptr.
        auto allExceptKernelFcm = RuntimeInfo::FetchFlag::ALL & ~RuntimeInfo::FetchFlag::KERNEL_FCM;
        bool needDeviceRuntimeInfo = flags & allExceptKernelFcm;
        if (needDeviceRuntimeInfo) {
            mDeviceRuntimeInfo.fetchedFlags &= (~flags);  // mark the fields as "not fetched"
            return nullptr;
        }
    }

    // To support devices without GKI, RuntimeInfo::fetchAllInformation does not report errors
    // if kernel level cannot be retrieved. If so, fetch kernel FCM version from device HAL
    // manifest and store it in RuntimeInfo too.
    if (flags & RuntimeInfo::FetchFlag::KERNEL_FCM) {
        Level deviceManifestKernelLevel = Level::UNSPECIFIED;
        auto manifest = getDeviceHalManifest();
        if (manifest) {
            deviceManifestKernelLevel = manifest->inferredKernelLevel();
        }
        if (deviceManifestKernelLevel != Level::UNSPECIFIED) {
            Level kernelLevel = mDeviceRuntimeInfo.object->kernelLevel();
            if (kernelLevel == Level::UNSPECIFIED) {
                mDeviceRuntimeInfo.object->setKernelLevel(deviceManifestKernelLevel);
            } else if (kernelLevel != deviceManifestKernelLevel) {
                LOG(WARNING) << "uname() reports kernel level " << kernelLevel
                             << " but device manifest sets kernel level "
                             << deviceManifestKernelLevel << ". Using kernel level " << kernelLevel;
            }
        }
    }

    mDeviceRuntimeInfo.fetchedFlags |= flags;
    return mDeviceRuntimeInfo.object;
}

int32_t VintfObject::checkCompatibility(std::string* error, CheckFlags::Type flags) {
    status_t status = OK;
    // null checks for files and runtime info
    if (getFrameworkHalManifest() == nullptr) {
        appendLine(error, "No framework manifest file from device or from update package");
        status = NO_INIT;
    }
    if (getDeviceHalManifest() == nullptr) {
        appendLine(error, "No device manifest file from device or from update package");
        status = NO_INIT;
    }
    if (getFrameworkCompatibilityMatrix() == nullptr) {
        appendLine(error, "No framework matrix file from device or from update package");
        status = NO_INIT;
    }
    if (getDeviceCompatibilityMatrix() == nullptr) {
        appendLine(error, "No device matrix file from device or from update package");
        status = NO_INIT;
    }

    if (flags.isRuntimeInfoEnabled()) {
        if (getRuntimeInfo() == nullptr) {
            appendLine(error, "No runtime info from device");
            status = NO_INIT;
        }
    }
    if (status != OK) return status;

    // compatiblity check.
    if (!getDeviceHalManifest()->checkCompatibility(*getFrameworkCompatibilityMatrix(), error)) {
        if (error) {
            error->insert(0,
                          "Device manifest and framework compatibility matrix are incompatible: ");
        }
        return INCOMPATIBLE;
    }
    if (!getFrameworkHalManifest()->checkCompatibility(*getDeviceCompatibilityMatrix(), error)) {
        if (error) {
            error->insert(0,
                          "Framework manifest and device compatibility matrix are incompatible: ");
        }
        return INCOMPATIBLE;
    }

    if (flags.isRuntimeInfoEnabled()) {
        if (!getRuntimeInfo()->checkCompatibility(*getFrameworkCompatibilityMatrix(), error,
                                                  flags)) {
            if (error) {
                error->insert(0,
                              "Runtime info and framework compatibility matrix are incompatible: ");
            }
            return INCOMPATIBLE;
        }
    }

    return COMPATIBLE;
}

namespace details {

std::vector<std::string> dumpFileList(const std::string& sku) {
    std::vector<std::string> list = {
        // clang-format off
        kSystemVintfDir,
        kVendorVintfDir,
        kOdmVintfDir,
        kProductVintfDir,
        kSystemExtVintfDir,
        kOdmLegacyManifest,
        kVendorLegacyManifest,
        kVendorLegacyMatrix,
        kSystemLegacyManifest,
        kSystemLegacyMatrix,
        // clang-format on
    };
    if (!sku.empty()) {
        list.push_back(getOdmProductManifestFile(kOdmLegacyVintfDir, sku));
    }
    return list;
}

}  // namespace details

bool VintfObject::IsHalDeprecated(const MatrixHal& oldMatrixHal,
                                  const std::string& oldMatrixHalFileName,
                                  const CompatibilityMatrix& targetMatrix,
                                  const std::shared_ptr<const HalManifest>& deviceManifest,
                                  const ChildrenMap& childrenMap, std::string* appendedError) {
    bool isDeprecated = false;
    oldMatrixHal.forEachInstance([&](const MatrixInstance& oldMatrixInstance) {
        if (IsInstanceDeprecated(oldMatrixInstance, oldMatrixHalFileName, targetMatrix,
                                 deviceManifest, childrenMap, appendedError)) {
            isDeprecated = true;
        }
        return true;  // continue to check next instance
    });
    return isDeprecated;
}

// Let oldMatrixInstance = package@x.y-w::interface/instancePattern.
// If any "@servedVersion::interface/servedInstance" in deviceManifest(package@x.y::interface)
// matches instancePattern, return true iff for all child interfaces (from
// GetListedInstanceInheritance), IsFqInstanceDeprecated returns false.
bool VintfObject::IsInstanceDeprecated(const MatrixInstance& oldMatrixInstance,
                                       const std::string& oldMatrixInstanceFileName,
                                       const CompatibilityMatrix& targetMatrix,
                                       const std::shared_ptr<const HalManifest>& deviceManifest,
                                       const ChildrenMap& childrenMap, std::string* appendedError) {
    const std::string& package = oldMatrixInstance.package();
    const Version& version = oldMatrixInstance.versionRange().minVer();
    const std::string& interface = oldMatrixInstance.interface();

    std::vector<std::string> accumulatedErrors;

    auto addErrorForInstance = [&](const ManifestInstance& manifestInstance) {
        const std::string& servedInstance = manifestInstance.instance();
        Version servedVersion = manifestInstance.version();
        if (!oldMatrixInstance.matchInstance(servedInstance)) {
            // ignore unrelated instance
            return true;  // continue
        }

        auto inheritance =
            GetListedInstanceInheritance(oldMatrixInstance.format(), package, servedVersion,
                                         interface, servedInstance, deviceManifest, childrenMap);
        if (!inheritance.has_value()) {
            accumulatedErrors.push_back(inheritance.error().message());
            return true;  // continue
        }

        std::vector<std::string> errors;
        for (const auto& fqInstance : *inheritance) {
            auto result = IsFqInstanceDeprecated(targetMatrix, oldMatrixInstance.format(),
                                                 fqInstance, deviceManifest);
            if (result.ok()) {
                errors.clear();
                break;
            }
            std::string error = result.error().message() + "\n    ";
            std::string servedFqInstanceString =
                toFQNameString(package, servedVersion, interface, servedInstance);
            if (fqInstance.string() == servedFqInstanceString) {
                error += "because it matches ";
            } else {
                error += "because it inherits from " + fqInstance.string() + " that matches ";
            }
            error += oldMatrixInstance.description(oldMatrixInstance.versionRange().minVer()) +
                     " from " + oldMatrixInstanceFileName;
            errors.push_back(error);
            // Do not immediately think (package, servedVersion, interface, servedInstance)
            // is deprecated; check parents too.
        }

        if (errors.empty()) {
            return true;  // continue
        }
        accumulatedErrors.insert(accumulatedErrors.end(), errors.begin(), errors.end());
        return true;  // continue to next instance
    };
    (void)deviceManifest->forEachInstanceOfInterface(oldMatrixInstance.format(), package, version,
                                                     interface, addErrorForInstance);

    if (accumulatedErrors.empty()) {
        return false;
    }
    appendLine(appendedError, android::base::Join(accumulatedErrors, "\n"));
    return true;
}

// Check if fqInstance is listed in |deviceManifest|.
bool VintfObject::IsInstanceListed(const std::shared_ptr<const HalManifest>& deviceManifest,
                                   HalFormat format, const FqInstance& fqInstance) {
    bool found = false;
    (void)deviceManifest->forEachInstanceOfInterface(
        format, fqInstance.getPackage(), fqInstance.getVersion(), fqInstance.getInterface(),
        [&](const ManifestInstance& manifestInstance) {
            if (manifestInstance.instance() == fqInstance.getInstance()) {
                found = true;
            }
            return !found;  // continue to next instance if not found
        });
    return found;
}

// Return a list of FqInstance, where each element:
// - is listed in |deviceManifest|; AND
// - is, or inherits from, package@version::interface/instance (as specified by |childrenMap|)
android::base::Result<std::vector<FqInstance>> VintfObject::GetListedInstanceInheritance(
    HalFormat format, const std::string& package, const Version& version,
    const std::string& interface, const std::string& instance,
    const std::shared_ptr<const HalManifest>& deviceManifest, const ChildrenMap& childrenMap) {
    FqInstance fqInstance;
    if (!fqInstance.setTo(package, version.majorVer, version.minorVer, interface, instance)) {
        return android::base::Error() << toFQNameString(package, version, interface, instance)
                                      << " is not a valid FqInstance";
    }

    if (!IsInstanceListed(deviceManifest, format, fqInstance)) {
        return {};
    }

    const auto& fqName = fqInstance.getFqNameString();

    std::vector<FqInstance> ret;
    ret.push_back(fqInstance);

    auto childRange = childrenMap.equal_range(fqName);
    for (auto it = childRange.first; it != childRange.second; ++it) {
        const auto& childFqNameString = it->second;
        FQName childFqName;
        if (!childFqName.setTo(childFqNameString)) {
            return android::base::Error() << "Cannot parse " << childFqNameString << " as FQName";
        }
        FqInstance childFqInstance;
        if (!childFqInstance.setTo(childFqName.package(), childFqName.getPackageMajorVersion(),
                                   childFqName.getPackageMinorVersion(),
                                   childFqName.getInterfaceName(), fqInstance.getInstance())) {
            return android::base::Error() << "Cannot merge " << childFqName.string() << "/"
                                          << fqInstance.getInstance() << " as FqInstance";
            continue;
        }
        if (!IsInstanceListed(deviceManifest, format, childFqInstance)) {
            continue;
        }
        ret.push_back(childFqInstance);
    }
    return ret;
}

// Check if |fqInstance| is in |targetMatrix|; essentially equal to
// targetMatrix.matchInstance(fqInstance), but provides richer error message. In details:
// 1. package@x.?::interface/servedInstance is not in targetMatrix; OR
// 2. package@x.z::interface/servedInstance is in targetMatrix but
//    servedInstance is not in deviceManifest(package@x.z::interface)
android::base::Result<void> VintfObject::IsFqInstanceDeprecated(
    const CompatibilityMatrix& targetMatrix, HalFormat format, const FqInstance& fqInstance,
    const std::shared_ptr<const HalManifest>& deviceManifest) {
    // Find minimum package@x.? in target matrix, and check if instance is in target matrix.
    bool foundInstance = false;
    Version targetMatrixMinVer{SIZE_MAX, SIZE_MAX};
    targetMatrix.forEachInstanceOfPackage(
        format, fqInstance.getPackage(), [&](const auto& targetMatrixInstance) {
            if (targetMatrixInstance.versionRange().majorVer == fqInstance.getMajorVersion() &&
                targetMatrixInstance.interface() == fqInstance.getInterface() &&
                targetMatrixInstance.matchInstance(fqInstance.getInstance())) {
                targetMatrixMinVer =
                    std::min(targetMatrixMinVer, targetMatrixInstance.versionRange().minVer());
                foundInstance = true;
            }
            return true;
        });
    if (!foundInstance) {
        return android::base::Error()
               << fqInstance.string() << " is deprecated in compatibility matrix at FCM Version "
               << targetMatrix.level() << "; it should not be served.";
    }

    // Assuming that targetMatrix requires @x.u-v, require that at least @x.u is served.
    bool targetVersionServed = false;

    (void)deviceManifest->forEachInstanceOfInterface(
        format, fqInstance.getPackage(), targetMatrixMinVer, fqInstance.getInterface(),
        [&](const ManifestInstance& manifestInstance) {
            if (manifestInstance.instance() == fqInstance.getInstance()) {
                targetVersionServed = true;
                return false;  // break
            }
            return true;  // continue
        });

    if (!targetVersionServed) {
        return android::base::Error()
               << fqInstance.string() << " is deprecated; requires at least " << targetMatrixMinVer;
    }
    return {};
}

int32_t VintfObject::checkDeprecation(const std::vector<HidlInterfaceMetadata>& hidlMetadata,
                                      std::string* error) {
    std::vector<CompatibilityMatrix> matrixFragments;
    auto matrixFragmentsStatus = getAllFrameworkMatrixLevels(&matrixFragments, error);
    if (matrixFragmentsStatus != OK) {
        return matrixFragmentsStatus;
    }
    if (matrixFragments.empty()) {
        if (error && error->empty()) {
            *error = "Cannot get framework matrix for each FCM version for unknown error.";
        }
        return NAME_NOT_FOUND;
    }
    auto deviceManifest = getDeviceHalManifest();
    if (deviceManifest == nullptr) {
        if (error) *error = "No device manifest.";
        return NAME_NOT_FOUND;
    }
    Level deviceLevel = deviceManifest->level();
    if (deviceLevel == Level::UNSPECIFIED) {
        if (error) *error = "Device manifest does not specify Shipping FCM Version.";
        return BAD_VALUE;
    }
    std::string kernelLevelError;
    Level kernelLevel = getKernelLevel(&kernelLevelError);
    if (kernelLevel == Level::UNSPECIFIED) {
        LOG(WARNING) << kernelLevelError;
    }

    std::vector<CompatibilityMatrix> targetMatrices;
    // Partition matrixFragments into two groups, where the second group
    // contains all matrices whose level == deviceLevel.
    auto targetMatricesPartition = std::partition(
        matrixFragments.begin(), matrixFragments.end(),
        [&](const CompatibilityMatrix& matrix) { return matrix.level() != deviceLevel; });
    // Move these matrices into the targetMatrices vector...
    std::move(targetMatricesPartition, matrixFragments.end(), std::back_inserter(targetMatrices));
    if (targetMatrices.empty()) {
        if (error)
            *error = "Cannot find framework matrix at FCM version " + to_string(deviceLevel) + ".";
        return NAME_NOT_FOUND;
    }
    // so that they can be combined into one matrix for deprecation checking.
    auto targetMatrix =
        CompatibilityMatrix::combine(deviceLevel, kernelLevel, &targetMatrices, error);
    if (targetMatrix == nullptr) {
        return BAD_VALUE;
    }

    std::multimap<std::string, std::string> childrenMap;
    for (const auto& child : hidlMetadata) {
        for (const auto& parent : child.inherited) {
            childrenMap.emplace(parent, child.name);
        }
    }
    // AIDL does not have inheritance.

    // Find a list of possibly deprecated HALs by comparing |deviceManifest| with older matrices.
    // Matrices with unspecified level are considered "current".
    bool isDeprecated = false;
    for (auto it = matrixFragments.begin(); it < targetMatricesPartition; ++it) {
        const auto& namedMatrix = *it;
        if (namedMatrix.level() == Level::UNSPECIFIED) continue;
        if (namedMatrix.level() > deviceLevel) continue;
        for (const MatrixHal& hal : namedMatrix.getHals()) {
            if (IsHalDeprecated(hal, namedMatrix.fileName(), *targetMatrix, deviceManifest,
                                childrenMap, error)) {
                isDeprecated = true;
            }
        }
    }

    return isDeprecated ? DEPRECATED : NO_DEPRECATED_HALS;
}

Level VintfObject::getKernelLevel(std::string* error) {
    auto runtimeInfo = getRuntimeInfo(RuntimeInfo::FetchFlag::KERNEL_FCM);
    if (!runtimeInfo) {
        if (error) *error = "Cannot retrieve runtime info with kernel level.";
        return Level::UNSPECIFIED;
    }
    if (runtimeInfo->kernelLevel() != Level::UNSPECIFIED) {
        return runtimeInfo->kernelLevel();
    }
    if (error) {
        *error = "Both device manifest and kernel release do not specify kernel FCM version.";
    }
    return Level::UNSPECIFIED;
}

const std::unique_ptr<FileSystem>& VintfObject::getFileSystem() {
    return mFileSystem;
}

const std::unique_ptr<PropertyFetcher>& VintfObject::getPropertyFetcher() {
    return mPropertyFetcher;
}

const std::unique_ptr<ObjectFactory<RuntimeInfo>>& VintfObject::getRuntimeInfoFactory() {
    return mRuntimeInfoFactory;
}

const std::unique_ptr<ApexInterface>& VintfObject::getApex() {
    return mApex;
}

android::base::Result<bool> VintfObject::hasFrameworkCompatibilityMatrixExtensions() {
    std::vector<CompatibilityMatrix> matrixFragments;
    std::string error;
    status_t status = getAllFrameworkMatrixLevels(&matrixFragments, &error);
    if (status != OK) {
        return android::base::Error(-status)
               << "Cannot get all framework matrix fragments: " << error;
    }
    for (const auto& namedMatrix : matrixFragments) {
        // Returns true if product matrix exists.
        if (android::base::StartsWith(namedMatrix.fileName(), kProductVintfDir)) {
            return true;
        }
        // Returns true if system_ext matrix exists.
        if (android::base::StartsWith(namedMatrix.fileName(), kSystemExtVintfDir)) {
            return true;
        }
        // Returns true if device system matrix exists.
        if (android::base::StartsWith(namedMatrix.fileName(), kSystemVintfDir) &&
            namedMatrix.level() == Level::UNSPECIFIED && !namedMatrix.getHals().empty()) {
            return true;
        }
    }
    return false;
}

android::base::Result<void> VintfObject::checkUnusedHals(
    const std::vector<HidlInterfaceMetadata>& hidlMetadata) {
    auto matrix = getFrameworkCompatibilityMatrix();
    if (matrix == nullptr) {
        return android::base::Error(-NAME_NOT_FOUND) << "Missing framework matrix.";
    }
    auto manifest = getDeviceHalManifest();
    if (manifest == nullptr) {
        return android::base::Error(-NAME_NOT_FOUND) << "Missing device manifest.";
    }
    auto unused = manifest->checkUnusedHals(*matrix, hidlMetadata);
    if (!unused.empty()) {
        return android::base::Error()
               << "The following instances are in the device manifest but "
               << "not specified in framework compatibility matrix: \n"
               << "    " << android::base::Join(unused, "\n    ") << "\n"
               << "Suggested fix:\n"
               << "1. Update deprecated HALs to the latest version.\n"
               << "2. Check for any typos in device manifest or framework compatibility "
               << "matrices with FCM version >= " << matrix->level() << ".\n"
               << "3. For new platform HALs, add them to any framework compatibility matrix "
               << "with FCM version >= " << matrix->level() << " where applicable.\n"
               << "4. For device-specific HALs, add to DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE "
               << "or DEVICE_PRODUCT_COMPATIBILITY_MATRIX_FILE.";
    }
    return {};
}

namespace {

// Insert |name| into |ret| if shouldCheck(name).
void InsertIf(const std::string& name, const std::function<bool(const std::string&)>& shouldCheck,
              std::set<std::string>* ret) {
    if (shouldCheck(name)) ret->insert(name);
}

std::string StripHidlInterface(const std::string& fqNameString) {
    FQName fqName;
    if (!fqName.setTo(fqNameString)) {
        return "";
    }
    return fqName.getPackageAndVersion().string();
}

// StripAidlType(android.hardware.foo.IFoo)
// -> android.hardware.foo
std::string StripAidlType(const std::string& type) {
    auto items = android::base::Split(type, ".");
    if (items.empty()) {
        return "";
    }
    items.erase(items.end() - 1);
    return android::base::Join(items, ".");
}

// GetAidlPackageAndVersion(android.hardware.foo.IFoo, 1)
// -> android.hardware.foo@1
std::string GetAidlPackageAndVersion(const std::string& package, size_t version) {
    return package + "@" + std::to_string(version);
}

// android.hardware.foo@1.0
std::set<std::string> HidlMetadataToPackagesAndVersions(
    const std::vector<HidlInterfaceMetadata>& hidlMetadata,
    const std::function<bool(const std::string&)>& shouldCheck) {
    std::set<std::string> ret;
    for (const auto& item : hidlMetadata) {
        InsertIf(StripHidlInterface(item.name), shouldCheck, &ret);
    }
    return ret;
}

// android.hardware.foo@1
// All non-vintf stable interfaces are filtered out.
android::base::Result<std::set<std::string>> AidlMetadataToVintfPackagesAndVersions(
    const std::vector<AidlInterfaceMetadata>& aidlMetadata,
    const std::function<bool(const std::string&)>& shouldCheck) {
    std::set<std::string> ret;
    for (const auto& item : aidlMetadata) {
        if (item.stability != "vintf") {
            continue;
        }
        for (const auto& type : item.types) {
            auto package = StripAidlType(type);
            for (const auto& version : item.versions) {
                InsertIf(GetAidlPackageAndVersion(package, version), shouldCheck, &ret);
            }
            if (item.has_development) {
                auto maxVerIt = std::max_element(item.versions.begin(), item.versions.end());
                // If no frozen versions, the in-development version is 1.
                size_t maxVer = maxVerIt == item.versions.end() ? 0 : *maxVerIt;
                auto nextVer = maxVer + 1;
                if (nextVer < maxVer) {
                    return android::base::Error()
                           << "Bad version " << maxVer << " for AIDL type " << type
                           << "; integer overflow when inferring in-development version";
                }
                InsertIf(GetAidlPackageAndVersion(package, nextVer), shouldCheck, &ret);
            }
        }
    }
    return ret;
}

// android.hardware.foo@1.0::IFoo.
// Note that UDTs are not filtered out, so there might be non-interface types.
std::set<std::string> HidlMetadataToNames(const std::vector<HidlInterfaceMetadata>& hidlMetadata) {
    std::set<std::string> ret;
    for (const auto& item : hidlMetadata) {
        ret.insert(item.name);
    }
    return ret;
}

// android.hardware.foo.IFoo
// Note that UDTs are not filtered out, so there might be non-interface types.
// All non-vintf stable interfaces are filtered out.
std::set<std::string> AidlMetadataToVintfNames(
    const std::vector<AidlInterfaceMetadata>& aidlMetadata) {
    std::set<std::string> ret;
    for (const auto& item : aidlMetadata) {
        if (item.stability == "vintf") {
            for (const auto& type : item.types) {
                ret.insert(type);
            }
        }
    }
    return ret;
}

}  // anonymous namespace

android::base::Result<std::vector<CompatibilityMatrix>> VintfObject::getAllFrameworkMatrixLevels() {
    // Get all framework matrix fragments instead of the combined framework compatibility matrix
    // because the latter may omit interfaces from the latest FCM if device target-level is not
    // the latest.
    std::vector<CompatibilityMatrix> matrixFragments;
    std::string error;
    auto matrixFragmentsStatus = getAllFrameworkMatrixLevels(&matrixFragments, &error);
    if (matrixFragmentsStatus != OK) {
        return android::base::Error(-matrixFragmentsStatus)
               << "Unable to get all framework matrix fragments: " << error;
    }
    if (matrixFragments.empty()) {
        if (error.empty()) {
            error = "Cannot get framework matrix for each FCM version for unknown error.";
        }
        return android::base::Error(-NAME_NOT_FOUND) << error;
    }
    return matrixFragments;
}

// Check the compatibility matrix for the latest available AIDL interfaces only
// when AIDL_USE_UNFROZEN is defined
bool VintfObject::getCheckAidlCompatMatrix() {
#ifdef AIDL_USE_UNFROZEN
    constexpr bool kAidlUseUnfrozen = true;
#else
    constexpr bool kAidlUseUnfrozen = false;
#endif
    return mFakeCheckAidlCompatibilityMatrix.value_or(kAidlUseUnfrozen);
}

android::base::Result<void> VintfObject::checkMissingHalsInMatrices(
    const std::vector<HidlInterfaceMetadata>& hidlMetadata,
    const std::vector<AidlInterfaceMetadata>& aidlMetadata,
    std::function<bool(const std::string&)> shouldCheckHidl,
    std::function<bool(const std::string&)> shouldCheckAidl) {
    auto matrixFragments = getAllFrameworkMatrixLevels();
    if (!matrixFragments.ok()) return matrixFragments.error();

    // Filter aidlMetadata and hidlMetadata with shouldCheck.
    auto allAidlPackagesAndVersions =
        AidlMetadataToVintfPackagesAndVersions(aidlMetadata, shouldCheckAidl);
    if (!allAidlPackagesAndVersions.ok()) return allAidlPackagesAndVersions.error();
    auto allHidlPackagesAndVersions =
        HidlMetadataToPackagesAndVersions(hidlMetadata, shouldCheckHidl);

    // Filter out instances in allAidlVintfPackages and allHidlPackagesAndVersions that are
    // in the matrices.
    std::vector<std::string> errors;
    for (const auto& matrix : matrixFragments.value()) {
        matrix.forEachInstance([&](const MatrixInstance& matrixInstance) {
            switch (matrixInstance.format()) {
                case HalFormat::AIDL: {
                    for (Version v = matrixInstance.versionRange().minVer();
                         v <= matrixInstance.versionRange().maxVer(); ++v.minorVer) {
                        allAidlPackagesAndVersions->erase(
                            GetAidlPackageAndVersion(matrixInstance.package(), v.minorVer));
                    }
                    return true;  // continue to next instance
                }
                case HalFormat::HIDL: {
                    for (Version v = matrixInstance.versionRange().minVer();
                         v <= matrixInstance.versionRange().maxVer(); ++v.minorVer) {
                        allHidlPackagesAndVersions.erase(
                            toFQNameString(matrixInstance.package(), v));
                    }
                    return true;  // continue to next instance
                }
                default: {
                    for (Version v = matrixInstance.versionRange().minVer();
                         v <= matrixInstance.versionRange().maxVer(); ++v.minorVer) {
                        if (shouldCheckHidl(toFQNameString(matrixInstance.package(), v))) {
                            errors.push_back("HAL package " + matrixInstance.package() +
                                             " is not allowed to have format " +
                                             to_string(matrixInstance.format()) + ".");
                        }
                    }
                    return true;  // continue to next instance
                }
            }
        });
    }

    if (!allHidlPackagesAndVersions.empty()) {
        errors.push_back(
            "The following HIDL packages are not found in any compatibility matrix fragments:\t\n" +
            android::base::Join(allHidlPackagesAndVersions, "\t\n"));
    }
    if (!allAidlPackagesAndVersions->empty() && getCheckAidlCompatMatrix()) {
        errors.push_back(
            "The following AIDL packages are not found in any compatibility matrix fragments:\t\n" +
            android::base::Join(*allAidlPackagesAndVersions, "\t\n"));
    }

    if (!errors.empty()) {
        return android::base::Error() << android::base::Join(errors, "\n");
    }

    return {};
}

android::base::Result<void> VintfObject::checkMatrixHalsHasDefinition(
    const std::vector<HidlInterfaceMetadata>& hidlMetadata,
    const std::vector<AidlInterfaceMetadata>& aidlMetadata) {
    auto matrixFragments = getAllFrameworkMatrixLevels();
    if (!matrixFragments.ok()) return matrixFragments.error();

    auto allAidlVintfNames = AidlMetadataToVintfNames(aidlMetadata);
    auto allHidlNames = HidlMetadataToNames(hidlMetadata);
    std::set<std::string> badAidlInterfaces;
    std::set<std::string> badHidlInterfaces;

    std::vector<std::string> errors;
    for (const auto& matrix : matrixFragments.value()) {
        if (matrix.level() == Level::UNSPECIFIED) {
            LOG(INFO) << "Skip checkMatrixHalsHasDefinition() on " << matrix.fileName()
                      << " with no level.";
            continue;
        }

        matrix.forEachInstance([&](const MatrixInstance& matrixInstance) {
            switch (matrixInstance.format()) {
                case HalFormat::AIDL: {
                    auto matrixInterface =
                        toAidlFqnameString(matrixInstance.package(), matrixInstance.interface());
                    if (allAidlVintfNames.find(matrixInterface) == allAidlVintfNames.end()) {
                        errors.push_back(
                            "AIDL interface " + matrixInterface + " is referenced in " +
                            matrix.fileName() +
                            ", but there is no corresponding .aidl definition associated with an "
                            "aidl_interface module in this build. Typo?");
                    }
                    return true;  // continue to next instance
                }
                case HalFormat::HIDL: {
                    for (Version v = matrixInstance.versionRange().minVer();
                         v <= matrixInstance.versionRange().maxVer(); ++v.minorVer) {
                        auto matrixInterface = matrixInstance.interfaceDescription(v);
                        if (allHidlNames.find(matrixInterface) == allHidlNames.end()) {
                            errors.push_back(
                                "HIDL interface " + matrixInterface + " is referenced in " +
                                matrix.fileName() +
                                ", but there is no corresponding .hal definition associated with "
                                "a hidl_interface module in this build. Typo?");
                        }
                    }
                    return true;  // continue to next instance
                }
                default: {
                    // We do not have data for native HALs.
                    return true;  // continue to next instance
                }
            }
        });
    }

    if (!errors.empty()) {
        return android::base::Error() << android::base::Join(errors, "\n");
    }

    return {};
}

android::base::Result<KernelVersion> VintfObject::getLatestMinLtsAtFcmVersion(Level fcmVersion) {
    auto allFcms = getAllFrameworkMatrixLevels();
    if (!allFcms.ok()) return allFcms.error();

    // Get the max of latestKernelMinLts for all FCM fragments at |fcmVersion|.
    // Usually there's only one such fragment.
    KernelVersion foundLatestMinLts;
    for (const auto& fcm : *allFcms) {
        if (fcm.level() != fcmVersion) {
            continue;
        }
        // Note: this says "minLts", but "Latest" indicates that it is a max value.
        auto thisLatestMinLts = fcm.getLatestKernelMinLts();
        if (foundLatestMinLts < thisLatestMinLts) foundLatestMinLts = thisLatestMinLts;
    }
    if (foundLatestMinLts != KernelVersion{}) {
        return foundLatestMinLts;
    }
    return android::base::Error(-NAME_NOT_FOUND)
           << "Can't find compatibility matrix fragment for level " << fcmVersion;
}

// make_unique does not work because VintfObject constructor is private.
VintfObject::Builder::Builder()
    : VintfObjectBuilder(std::unique_ptr<VintfObject>(new VintfObject())) {}

namespace details {

VintfObjectBuilder::~VintfObjectBuilder() {}

VintfObjectBuilder& VintfObjectBuilder::setFileSystem(std::unique_ptr<FileSystem>&& e) {
    mObject->mFileSystem = std::move(e);
    return *this;
}

VintfObjectBuilder& VintfObjectBuilder::setRuntimeInfoFactory(
    std::unique_ptr<ObjectFactory<RuntimeInfo>>&& e) {
    mObject->mRuntimeInfoFactory = std::move(e);
    return *this;
}

VintfObjectBuilder& VintfObjectBuilder::setPropertyFetcher(std::unique_ptr<PropertyFetcher>&& e) {
    mObject->mPropertyFetcher = std::move(e);
    return *this;
}

VintfObjectBuilder& VintfObjectBuilder::setApex(std::unique_ptr<ApexInterface>&& a) {
    mObject->mApex = std::move(a);
    return *this;
}

std::unique_ptr<VintfObject> VintfObjectBuilder::buildInternal() {
    if (!mObject->mFileSystem) mObject->mFileSystem = createDefaultFileSystem();
    if (!mObject->mRuntimeInfoFactory)
        mObject->mRuntimeInfoFactory = std::make_unique<ObjectFactory<RuntimeInfo>>();
    if (!mObject->mPropertyFetcher) mObject->mPropertyFetcher = createDefaultPropertyFetcher();
    if (!mObject->mApex) mObject->mApex = createDefaultApex();
    return std::move(mObject);
}

}  // namespace details

}  // namespace vintf
}  // namespace android
