/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONDURATIONREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONDURATIONREPORTER_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include <map>
#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

#define MITIGATION_DURATION_MAIN_COUNT 12
#define MITIGATION_DURATION_SUB_COUNT 12

/**
 * A class to upload Pixel Mitigation Duration metrics
 */
class MitigationDurationReporter {
  public:
    MitigationDurationReporter();
    void logMitigationDuration(const std::shared_ptr<IStats> &stats_client,
                               const std::string &path);

  private:
    struct IrqDurationCounts {
        int uvlo1_none;
        int uvlo1_mmwave;
        int uvlo1_rffe;
        int uvlo2_none;
        int uvlo2_mmwave;
        int uvlo2_rffe;
        int batoilo_none;
        int batoilo_mmwave;
        int batoilo_rffe;
        int main[MITIGATION_DURATION_MAIN_COUNT];
        int sub[MITIGATION_DURATION_SUB_COUNT];
    };

    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;
    const int kExpectedNumberOfLines = 33;
    const std::string kGreaterThanTenMsSysfsNode = "/greater_than_10ms_count";

    void valueAssignmentHelper(std::vector<VendorAtomValue> *values, int *val, int fieldNumber);

    int updateStat(const std::string *line, int *val);

    bool getIrqDurationCountHelper(const std::string kMitigationDurationFile,
                                   struct IrqDurationCounts *counts);
    bool getStatFromLine(const std::string *line, int *val);
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONDURATIONREPORTER_H
