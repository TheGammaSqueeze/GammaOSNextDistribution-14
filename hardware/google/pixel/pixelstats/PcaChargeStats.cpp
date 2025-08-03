/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "pixelstats-uevent"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <log/log.h>
#include <pixelstats/PcaChargeStats.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using android::base::ReadFileToString;
using android::base::WriteStringToFile;

bool PcaChargeStats::CheckPcaContentsAndAck(std::string *file_contents) {
    std::string path = kPcaChargeMetricsPath;
    std::string line;
    std::istringstream ss;

    if (!ReadFileToString(path.c_str(), file_contents)) {
        if (!ReadFileToString(kPca94xxChargeMetricsPath.c_str(), file_contents)) {
            path = kDcChargeMetricsPath;
        } else {
            path = kPca94xxChargeMetricsPath;
        }
        if (!ReadFileToString(path.c_str(), file_contents)) {
            return false;
        }
    }

    ss.str(*file_contents);

    if (!std::getline(ss, line)) {
        ALOGE("Unable to read first line %s - %s", path.c_str(), strerror(errno));
        return false;
    }
    if (!WriteStringToFile(std::to_string(0), path.c_str())) {
        ALOGE("Couldn't clear %s - %s", path.c_str(), strerror(errno));
        return false;
    }
    return true;
}

PcaChargeStats::PcaChargeStats(const std::string pca_charge_metrics_path,
                               const std::string pca94xx_charge_metrics_path,
                               const std::string dc_charge_metrics_path)
    : kPcaChargeMetricsPath(pca_charge_metrics_path),
      kPca94xxChargeMetricsPath(pca94xx_charge_metrics_path),
      kDcChargeMetricsPath(dc_charge_metrics_path) {}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
