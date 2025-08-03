/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BATTERYEEPROMREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BATTERYEEPROMREPORTER_H

#include <cstdint>
#include <string>

#include <aidl/android/frameworks/stats/IStats.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;

// The storage for save whole history is 928 byte
// each history contains 19 items with total size 28 byte
// hence the history number is 928/28~33
#define BATT_HIST_NUM_MAX 33

// New history layout total size is 924 or 900 byte
// each history data size is 12 bytes: 900/12=75
#define BATT_HIST_NUM_MAX_V2 75

/**
 * A class to upload battery EEPROM metrics
 */
class BatteryEEPROMReporter {
  public:
    BatteryEEPROMReporter();
    void checkAndReport(const std::shared_ptr<IStats> &stats_client, const std::string &path);
    void checkAndReportGMSR(const std::shared_ptr<IStats> &stats_client, const std::vector<std::string> &paths);
    void checkAndReportMaxfgHistory(const std::shared_ptr<IStats> &stats_client,
                                    const std::string &path);

  private:
    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;

    struct BatteryHistory {
        /* The cycle count number; record of charge/discharge times */
        uint16_t cycle_cnt;
        /* The current full capacity of the battery under nominal conditions */
        uint16_t full_cap;
        /* The battery equivalent series resistance */
        uint16_t esr;
        /* Battery resistance related to temperature change */
        uint16_t rslow;
        /* Battery health indicator reflecting the battery age state */
        uint8_t soh;
        /* The battery temperature */
        int8_t batt_temp;
        /* Battery state of charge (SOC) shutdown point */
        uint8_t cutoff_soc;
        /* Raw battery state of charge (SOC), based on battery current (CC = Coulomb Counter) */
        uint8_t cc_soc;
        /* Estimated battery state of charge (SOC) from batt_soc with endpoint limiting
         * (0% and 100%)
         */
        uint8_t sys_soc;
        /* Filtered monotonic SOC, handles situations where the cutoff_soc is increased and
         * then decreased from the battery physical properties
         */
        uint8_t msoc;
        /* Estimated SOC derived from cc_soc that provides voltage loop feedback correction using
         * battery voltage, current, and status values
         */
        uint8_t batt_soc;

        /* Field used for data padding in the EEPROM data */
        uint8_t reserve;

        /* The maximum battery temperature ever seen */
        int8_t max_temp;
        /* The minimum battery temperature ever seen */
        int8_t min_temp;
        /* The maximum battery voltage ever seen */
        uint16_t max_vbatt;
        /* The minimum battery voltage ever seen */
        uint16_t min_vbatt;
        /* The maximum battery current ever seen */
        int16_t max_ibatt;
        /* The minimum battery current ever seen */
        int16_t min_ibatt;
        /* Field used to verify the integrity of the EEPROM data */
        uint16_t checksum;
        /* Extend data for P21 */
        /* Temperature compensation information */
        uint16_t tempco;
        /* Learned characterization related to the voltage gauge */
        uint16_t rcomp0;
        /* For time to monitor the life of cell */
        uint8_t timer_h;
         /* The full capacity of the battery learning at the end of every charge cycle */
        uint16_t full_rep;
    };
    /* The number of elements in struct BatteryHistory for P20 series */
    const int kNumBatteryHistoryFields = 19;

    /* P21+ history format */
    struct BatteryHistoryExtend {
        uint16_t tempco;
        uint16_t rcomp0;
        uint8_t timer_h;
        unsigned fullcapnom:10;
        unsigned fullcaprep:10;
        unsigned mixsoc:6;
        unsigned vfsoc:6;
        unsigned maxvolt:4;
        unsigned minvolt:4;
        unsigned maxtemp:4;
        unsigned mintemp:4;
        unsigned maxchgcurr:4;
        unsigned maxdischgcurr:4;
    };

    int64_t report_time_ = 0;
    int64_t getTimeSecs();

    bool checkLogEvent(struct BatteryHistory hist);
    void reportEvent(const std::shared_ptr<IStats> &stats_client,
                     const struct BatteryHistory &hist);

    const int kNum77759GMSRFields = 11;
    const int kNum77779GMSRFields = 5;
    const int kNum17201HISTFields = 16;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_BATTERYEEPROMREPORTER_H
