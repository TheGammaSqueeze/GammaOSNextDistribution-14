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

#ifdef LIVES_IN_SYSTEM
#define LOG_TAG "lineage.livedisplay@2.0-impl-sysfs"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-impl-sysfs"
#endif

#include "livedisplay/sysfs/DisplayColorCalibration.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

namespace {
constexpr const char* kFileRgb = "/sys/class/graphics/fb0/rgb";
};  // anonymous namespace

using ::android::base::ReadFileToString;
using ::android::base::Split;
using ::android::base::StringPrintf;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;
using ::android::hardware::Void;

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sysfs {

bool DisplayColorCalibration::isSupported() {
    return !access(kFileRgb, R_OK | W_OK);
}

// Methods from ::vendor::lineage::livedisplay::V2_0::IDisplayColorCalibration follow.
Return<int32_t> DisplayColorCalibration::getMaxValue() {
    return 32768;
}

Return<int32_t> DisplayColorCalibration::getMinValue() {
    return 255;
}

Return<void> DisplayColorCalibration::getCalibration(getCalibration_cb _hidl_cb) {
    std::vector<int32_t> rgb;
    std::string tmp;

    if (ReadFileToString(kFileRgb, &tmp)) {
        for (auto&& color : Split(Trim(tmp), " ")) {
            rgb.push_back(std::stoi(color));
        }
    }

    _hidl_cb(rgb);
    return Void();
}

Return<bool> DisplayColorCalibration::setCalibration(const hidl_vec<int32_t>& rgb) {
    if (rgb.size() != 3) {
        LOG(ERROR) << "Unrecognized RGB data!";
        return false;
    }

    return WriteStringToFile(StringPrintf("%d %d %d", rgb[0], rgb[1], rgb[2]), kFileRgb, true);
}

}  // namespace sysfs
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
