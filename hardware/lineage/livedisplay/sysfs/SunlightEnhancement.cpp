/*
 * Copyright (C) 2019-2020 The LineageOS Project
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

#include "livedisplay/sysfs/SunlightEnhancement.h"

#include <android-base/file.h>
#include <android-base/strings.h>

namespace {
constexpr const char* kFileHbm = "/sys/class/graphics/fb0/hbm";
constexpr const char* kFileSre = "/sys/class/graphics/fb0/sre";
};  // anonymous namespace

using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sysfs {

SunlightEnhancement::SunlightEnhancement() {
    if (!access(kFileHbm, R_OK | W_OK)) {
        file_ = kFileHbm;
        enabled_mode_ = 1;
    } else if (!access(kFileSre, R_OK | W_OK)) {
        file_ = kFileSre;
        enabled_mode_ = 2;
    } else {
        file_ = nullptr;
    }
}

bool SunlightEnhancement::isSupported() {
    return file_ != nullptr;
}

// Methods from ::vendor::lineage::livedisplay::V2_0::ISunlightEnhancement follow.
Return<bool> SunlightEnhancement::isEnabled() {
    std::string tmp;
    int32_t contents = 0;

    if (ReadFileToString(file_, &tmp)) {
        contents = std::stoi(Trim(tmp));
    }

    return contents > 0;
}

Return<bool> SunlightEnhancement::setEnabled(bool enabled) {
    return WriteStringToFile(enabled ? std::to_string(enabled_mode_) : "0", file_, true);
}

}  // namespace sysfs
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
