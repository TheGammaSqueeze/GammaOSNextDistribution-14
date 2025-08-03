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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BROWNOUTDETECTEDREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BROWNOUTDETECTEDREPORTER_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include <map>
#include <regex>
#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

#define ODPM_MAX_IDX 24
#define DVFS_MAX_IDX 6

enum CsvIdx {
    TIMESTAMP_IDX,
    IRQ_IDX,
    SOC_IDX,
    TEMP_IDX,
    CYCLE_IDX,
    VOLTAGE_IDX,
    CURRENT_IDX,
    DVFS_CHANNEL_0 = 7,
    ODPM_CHANNEL_0 = 12,
};

enum Irq {
    SMPL_WARN,
    OCP_WARN_CPUCL1,
    OCP_WARN_CPUCL2,
    SOFT_OCP_WARN_CPUCL1,
    SOFT_OCP_WARN_CPUCL2,
    OCP_WARN_TPU,
    SOFT_OCP_WARN_TPU,
    OCP_WARN_GPU,
    SOFT_OCP_WARN_GPU,
    PMIC_SOC,
    UVLO1,
    UVLO2,
    BATOILO,
    BATOILO2,
    PMIC_120C,
    PMIC_140C,
    PMIC_OVERHEAT,
};

enum Update { kUpdateMax, kUpdateMin };

/**
 * A class to upload Pixel Brownout metrics
 */
class BrownoutDetectedReporter {
  public:
    void logBrownout(const std::shared_ptr<IStats> &stats_client, const std::string &logFilePath,
                     const std::string &brownoutReasonProp);
    void logBrownoutCsv(const std::shared_ptr<IStats> &stats_client, const std::string &logFilePath,
                        const std::string &brownoutReasonProp);
    int brownoutReasonCheck(const std::string &brownoutReasonProp);

  private:
    struct BrownoutDetectedInfo {
        int triggered_irq_;
        long triggered_timestamp_;
        int battery_temp_;
        int battery_cycle_;
        int battery_soc_;
        int voltage_now_;
        int odpm_value_[ODPM_MAX_IDX];
        int dvfs_value_[DVFS_MAX_IDX];
        int brownout_reason_;
    };

    void setAtomFieldValue(std::vector<VendorAtomValue> *values, int offset, int content);
    long parseTimestamp(std::string timestamp);
    bool updateIfFound(std::string line, std::regex pattern, int *current_value, Update flag);
    void uploadData(const std::shared_ptr<IStats> &stats_client,
                    const struct BrownoutDetectedInfo max_value);
    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BROWNOUTDETECTEDREPORTER_H
