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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_WIRELESSCHARGESTATS_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_WIRELESSCHARGESTATS_H

namespace android {
namespace hardware {
namespace google {
namespace pixel {

class WirelessChargeStats {
  public:
    WirelessChargeStats(const std::string wireless_charge_metrics_path =
                                "/sys/class/power_supply/wireless/device/charge_stats");

    int TranslateSysModeToAtomValue(const int sys_mode);
    bool CheckWirelessContentsAndAck(std::string *file_contents);
    void CalculateWirelessChargeStats(const int soc_tmp, const std::string file_contents);

    int pout_min_;
    int pout_avg_;
    int pout_max_;
    int of_freq_;
    int tier_soc_;

  private:
    void ResetChargeMetrics();
    void CalculateWirelessChargeMetrics(const int pout_min, const int pout_avg, const int pout_max,
                                        const int of_freq, const int alignment);

    const std::string kWirelessChargeMetricsPath;

    int alignment_;
    int count_;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_WIRELESSCHARGESTATS_H
