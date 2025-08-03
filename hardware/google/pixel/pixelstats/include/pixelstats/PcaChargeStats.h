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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_PCACHARGESTATS_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_PCACHARGESTATS_H

namespace android {
namespace hardware {
namespace google {
namespace pixel {

class PcaChargeStats {
  public:
    PcaChargeStats(const std::string pca_charge_metrics_path =
                           "/sys/class/power_supply/pca9468-mains/device/chg_stats",
                   const std::string pca94xx_charge_metrics_path =
                           "/sys/class/power_supply/pca94xx-mains/device/chg_stats",
                   const std::string dc_charge_metrics_path =
                           "/sys/class/power_supply/dc-mains/device/chg_stats");

    bool CheckPcaContentsAndAck(std::string *file_contents);

  private:
    const std::string kPcaChargeMetricsPath;
    const std::string kPca94xxChargeMetricsPath;
    const std::string kDcChargeMetricsPath;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_PCACHARGESTATS_H
