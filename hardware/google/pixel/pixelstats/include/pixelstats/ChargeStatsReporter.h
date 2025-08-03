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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_CHARGESTATSREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_CHARGESTATSREPORTER_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <pixelstats/PcaChargeStats.h>
#include <pixelstats/WirelessChargeStats.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;

/**
 * A class to upload battery capacity metrics
 */
class ChargeStatsReporter {
  public:
    ChargeStatsReporter();
    void checkAndReport(const std::shared_ptr<IStats> &stats_client, const std::string &path);

  private:
    bool checkContentsAndAck(std::string *file_contents, const std::string &path);
    void ReportVoltageTierStats(const std::shared_ptr<IStats> &stats_client, const char *line,
                                const bool has_wireless, const std::string &wfile_contents);
    void ReportChargeStats(const std::shared_ptr<IStats> &stats_client, const std::string line,
                           const std::string wline_at, const std::string wline_ac,
                           const std::string pca_line);
    bool shouldReportEvent(void);
    int64_t getTimeSecs(void);

    WirelessChargeStats wireless_charge_stats_;
    PcaChargeStats pca_charge_stats_;

    int log_event_time_secs_ = 0;

    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;

    const std::string kThermalChargeMetricsPath =
            "/sys/devices/platform/google,charger/thermal_stats";

    const std::string kGChargerMetricsPath = "/sys/devices/platform/google,charger/charge_stats";

    const std::string kGDualBattMetricsPath = "/sys/class/power_supply/dualbatt/dbatt_stats";
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_CHARGESTATSREPORTER_H
