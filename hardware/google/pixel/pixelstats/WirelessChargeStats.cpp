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
#include <pixelstats/WirelessChargeStats.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using android::base::ReadFileToString;
using android::base::WriteStringToFile;

/*  Reference to <kernel>/private/google-modules/bms/p9221_charger.h
 *  translate sys_mode value to enum define in pixelatoms.proto
 */
int WirelessChargeStats::TranslateSysModeToAtomValue(const int sys_mode) {
    switch (sys_mode) {
        case 1:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_WPC_BPP;
        case 2:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_WPC_EPP;
        case 3:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_WPC_L7;
        case 0xe0:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_DL;
        case 0xa0:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_L7;
        default:
            return PixelAtoms::ChargeStats::ADAPTER_TYPE_WLC;
    }
}

bool WirelessChargeStats::CheckWirelessContentsAndAck(std::string *file_contents) {
    std::string line;
    std::istringstream ss;

    if (!ReadFileToString(kWirelessChargeMetricsPath.c_str(), file_contents))
        return false;

    ss.str(*file_contents);

    if (!std::getline(ss, line)) {
        ALOGE("Unable to read first line %s - %s", kWirelessChargeMetricsPath.c_str(),
              strerror(errno));
        return false;
    }
    if (!WriteStringToFile(std::to_string(0), kWirelessChargeMetricsPath.c_str())) {
        ALOGE("Couldn't clear %s - %s", kWirelessChargeMetricsPath.c_str(), strerror(errno));
        return false;
    }
    return true;
}

void WirelessChargeStats::ResetChargeMetrics() {
    pout_min_ = 0;
    pout_avg_ = 0;
    pout_max_ = 0;
    of_freq_ = 0;
    alignment_ = 0;
    count_ = 0;
}

void WirelessChargeStats::CalculateWirelessChargeMetrics(const int pout_min, const int pout_avg,
                                                         const int pout_max, const int of_freq,
                                                         const int alignment) {
    if ((pout_min_ == 0) || (pout_min_ > pout_min))
        pout_min_ = pout_min;

    pout_avg_ += pout_avg;
    count_++;

    if ((pout_max_ == 0) || (pout_max_ < pout_max))
        pout_max_ = pout_max;

    if (alignment_ == 0 || ((alignment >= 0) && (alignment_ > alignment))) {
        of_freq_ = of_freq;
        alignment_ = alignment;
    }
}

void WirelessChargeStats::CalculateWirelessChargeStats(const int ssoc_tmp,
                                                       const std::string file_contents) {
    std::string line;
    std::istringstream ss;

    ResetChargeMetrics();
    ss.str(file_contents);

    while (std::getline(ss, line)) {
        int32_t buf[11] = {0};
        if (sscanf(line.c_str(), "%d:%d, %d,%d,%d, %d,%d, %d,%d,%d,%d", &buf[0], &buf[1], &buf[2],
                   &buf[3], &buf[4], &buf[5], &buf[6], &buf[7], &buf[8], &buf[9], &buf[10]) == 11) {
            const int32_t soc = buf[0];

            /* calculate wireless charge stats of next voltage tier */
            if (soc > tier_soc_) {
                const int32_t alignment = buf[6];

                if (alignment >= 0 && alignment < 100)
                    ALOGD("WirelessChargeStats: misalignment %s", line.c_str());

                CalculateWirelessChargeMetrics(buf[2], buf[3], buf[4], buf[5], buf[6]);
                if (soc >= ssoc_tmp) {
                    /* reach next voltage tier, restore final results before sending out*/
                    pout_avg_ = (pout_avg_ / count_);
                    tier_soc_ = soc;
                    ALOGD("WirelessChargeStats: atoms %d %d %d %d", pout_min_, pout_avg_, pout_max_,
                          of_freq_);
                    return;
                }
            }
        }
    }
}

WirelessChargeStats::WirelessChargeStats(const std::string wireless_charge_metrics_path)
    : kWirelessChargeMetricsPath(wireless_charge_metrics_path) {}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
