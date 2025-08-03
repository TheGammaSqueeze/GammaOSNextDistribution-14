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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONSTATSREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONSTATSREPORTER_H

#include <map>
#include <string>

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

/**
 * A class to upload Pixel Mitigation Stats metrics
 */
class MitigationStatsReporter {
  public:
    MitigationStatsReporter();
    void logMitigationStatsPerHour(const std::shared_ptr<IStats> &stats_client,
                                   const std::string &path);

  private:
    struct MitigationCount {
        int batoilo_count;
        int vdroop1_count;
        int vdroop2_count;
        int smpl_warn_count;
        int ocp_cpu1_count;
        int ocp_cpu2_count;
        int ocp_tpu_count;
        int ocp_gpu_count;
        int soft_ocp_cpu1_count;
        int soft_ocp_cpu2_count;
        int soft_ocp_tpu_count;
        int soft_ocp_gpu_count;
    };

    struct MitigationCap {
        int batoilo_cap;
        int vdroop1_cap;
        int vdroop2_cap;
        int smpl_warn_cap;
        int ocp_cpu1_cap;
        int ocp_cpu2_cap;
        int ocp_tpu_cap;
        int ocp_gpu_cap;
        int soft_ocp_cpu1_cap;
        int soft_ocp_cpu2_cap;
        int soft_ocp_tpu_cap;
        int soft_ocp_gpu_cap;
    };

    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;
    struct MitigationCount prev_count;

    bool logMitigationCount(const std::string kMitigationDir, struct MitigationCount *last_count);
    void logMitigationCap(const std::string kMitigationDir, struct MitigationCap *last_cap);
    bool ReadFileToInt(const std::string &path, int *val);
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MITIGATIONSTATSREPORTER_H
