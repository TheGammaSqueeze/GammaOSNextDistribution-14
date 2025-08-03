/*
 * Copyright (C) 2019-2021 The LineageOS Project
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

#include "livedisplay/sysfs/AdaptiveBacklight.h"

#include <android-base/file.h>
#include <android-base/strings.h>

using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace {
constexpr const char* kFileAcl = "/sys/class/graphics/fb0/acl";
constexpr const char* kFileCabc = "/sys/class/graphics/fb0/cabc";
}  // anonymous namespace

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sysfs {

AdaptiveBacklight::AdaptiveBacklight() {
    if (!access(kFileAcl, R_OK | W_OK)) {
        file_ = kFileAcl;
    } else if (!access(kFileCabc, R_OK | W_OK)) {
        file_ = kFileCabc;
    } else {
        file_ = nullptr;
    }
}

bool AdaptiveBacklight::isSupported() {
    return file_ != nullptr;
}

// Methods from ::vendor::lineage::livedisplay::V2_0::IAdaptiveBacklight follow.
Return<bool> AdaptiveBacklight::isEnabled() {
    std::string tmp;
    int32_t contents = 0;

    if (ReadFileToString(file_, &tmp)) {
        contents = std::stoi(Trim(tmp));
    }

    return contents > 0;
}

Return<bool> AdaptiveBacklight::setEnabled(bool enabled) {
    return WriteStringToFile(std::to_string(enabled), file_, true);
}

}  // namespace sysfs
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
