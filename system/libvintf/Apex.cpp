/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "Apex.h"

#include <android-base/format.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

#include "com_android_apex.h"
#include "constants-private.h"

using android::base::StartsWith;

namespace android {
namespace vintf {
namespace details {

static bool isApexReady(PropertyFetcher* propertyFetcher) {
#ifdef LIBVINTF_TARGET
    return propertyFetcher->getBoolProperty("apex.all.ready", false);
#else
    // When running on host, it assumes that /apex is ready.
    // Reason for still relying on PropertyFetcher API is for host-side tests.
    return propertyFetcher->getBoolProperty("apex.all.ready", true);
#endif
}

static bool operator==(const TimeSpec& a, const TimeSpec& b) {
    return a.tv_sec == b.tv_sec && a.tv_nsec == b.tv_nsec;
}

status_t Apex::DeviceVintfDirs(FileSystem* fileSystem, PropertyFetcher* propertyFetcher,
                               std::vector<std::string>* dirs, std::string* error) {
    std::string apexInfoFile = kApexInfoFile;
    std::string apexDir = "/apex";
    if (!isApexReady(propertyFetcher)) {
        apexInfoFile = kBootstrapApexInfoFile;
        apexDir = "/bootstrap-apex";
    }
    // Update cached mtime_
    TimeSpec mtime{};
    auto status = fileSystem->modifiedTime(apexInfoFile, &mtime, error);

    if (status != OK) {
        switch (status) {
            case NAME_NOT_FOUND:
                status = OK;
                break;
            case -EACCES:
                // Don't error out on access errors, but log it
                LOG(WARNING) << "APEX Device VINTF Dirs: EACCES: "
                             << (error ? *error : "(unknown error message)");
                status = OK;
                break;
            default:
                break;
        }

        if ((status == OK) && (error)) {
            error->clear();
        }

        return status;
    }

    mtime_ = mtime;

    // Load apex-info-list
    std::string xml;
    status = fileSystem->fetch(apexInfoFile, &xml, error);
    if (status == NAME_NOT_FOUND) {
        if (error) {
            error->clear();
        }
        return OK;
    }
    if (status != OK) return status;

    auto apexInfoList = com::android::apex::parseApexInfoList(xml.c_str());
    if (!apexInfoList.has_value()) {
        if (error) {
            *error = std::string("Not a valid XML: ") + apexInfoFile;
        }
        return UNKNOWN_ERROR;
    }

    // Get vendor apex vintf dirs
    for (const auto& apexInfo : apexInfoList->getApexInfo()) {
        // Skip non-active apexes
        if (!apexInfo.getIsActive()) continue;
        // Skip if no preinstalled paths. This shouldn't happen but XML schema says it's optional.
        if (!apexInfo.hasPreinstalledModulePath()) continue;

        const std::string& path = apexInfo.getPreinstalledModulePath();
        if (StartsWith(path, "/vendor/apex/") || StartsWith(path, "/system/vendor/apex/")) {
            dirs->push_back(fmt::format("{}/{}/" VINTF_SUB_DIR, apexDir, apexInfo.getModuleName()));
        }
    }
    LOG(INFO) << "Loaded APEX Infos from " << apexInfoFile;
    return OK;
}

// Returns true when /apex/apex-info-list.xml is updated
bool Apex::HasUpdate(FileSystem* fileSystem, PropertyFetcher* propertyFetcher) const {
    if (!isApexReady(propertyFetcher)) {
        return false;
    }

    TimeSpec mtime{};
    std::string error;
    status_t status = fileSystem->modifiedTime(kApexInfoFile, &mtime, &error);
    if (status == NAME_NOT_FOUND) {
        return false;
    }
    if (status != OK) {
        LOG(ERROR) << error;
        return false;
    }
    if (mtime_.has_value() && mtime == mtime_.value()) {
        return false;
    }
    return true;
}

}  // namespace details
}  // namespace vintf
}  // namespace android
