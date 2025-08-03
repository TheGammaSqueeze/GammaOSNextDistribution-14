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

#define LOG_TAG "pixelstats-uevent: ChargeStatsReporter"

#include <android-base/file.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <log/log.h>
#include <pixelstats/ChargeStatsReporter.h>
#include <pixelstats/StatsHelper.h>
#include <time.h>
#include <utils/Timers.h>

#include <cmath>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::base::WriteStringToFile;
using android::hardware::google::pixel::PixelAtoms::ChargeStats;
using android::hardware::google::pixel::PixelAtoms::VoltageTierStats;

#define DURATION_FILTER_SECS 15

ChargeStatsReporter::ChargeStatsReporter() {}

int64_t ChargeStatsReporter::getTimeSecs(void) {
    return nanoseconds_to_seconds(systemTime(SYSTEM_TIME_BOOTTIME));
}

void ChargeStatsReporter::ReportChargeStats(const std::shared_ptr<IStats> &stats_client,
                                            const std::string line, const std::string wline_at,
                                            const std::string wline_ac,
                                            const std::string pca_line) {
    int charge_stats_fields[] = {
            ChargeStats::kAdapterTypeFieldNumber,
            ChargeStats::kAdapterVoltageFieldNumber,
            ChargeStats::kAdapterAmperageFieldNumber,
            ChargeStats::kSsocInFieldNumber,
            ChargeStats::kVoltageInFieldNumber,
            ChargeStats::kSsocOutFieldNumber,
            ChargeStats::kVoltageOutFieldNumber,
            ChargeStats::kChargeCapacityFieldNumber,
            ChargeStats::kAdapterCapabilities0FieldNumber,
            ChargeStats::kAdapterCapabilities1FieldNumber,
            ChargeStats::kAdapterCapabilities2FieldNumber,
            ChargeStats::kAdapterCapabilities3FieldNumber,
            ChargeStats::kAdapterCapabilities4FieldNumber,
            ChargeStats::kReceiverState0FieldNumber,
            ChargeStats::kReceiverState1FieldNumber,
    };
    const int32_t chg_fields_size = std::size(charge_stats_fields);
    static_assert(chg_fields_size == 15, "Unexpected charge stats fields size");
    const int32_t wlc_fields_size = 7;
    std::vector<VendorAtomValue> values(chg_fields_size);
    VendorAtomValue val;
    int32_t i = 0, tmp[chg_fields_size] = {0}, fields_size = (chg_fields_size - wlc_fields_size);
    int32_t pca_ac[2] = {0}, pca_rs[5] = {0};

    ALOGD("processing %s", line.c_str());
    if (sscanf(line.c_str(), "%d,%d,%d, %d,%d,%d,%d %d", &tmp[0], &tmp[1], &tmp[2], &tmp[3],
               &tmp[4], &tmp[5], &tmp[6], &tmp[7]) == 8) {
        /* Age Adjusted Charge Rate (AACR) logs an additional battery capacity in order to determine
         * the charge curve needed to minimize battery cycle life degradation, while also minimizing
         * impact to the user.
         */
    } else if (sscanf(line.c_str(), "%d,%d,%d, %d,%d,%d,%d", &tmp[0], &tmp[1], &tmp[2], &tmp[3],
                      &tmp[4], &tmp[5], &tmp[6]) != 7) {
        ALOGE("Couldn't process %s", line.c_str());
        return;
    }

    if (!wline_at.empty()) {
        int32_t ssoc_tmp = 0;
        ALOGD("wlc: processing %s", wline_at.c_str());
        if (sscanf(wline_at.c_str(), "A:%d", &ssoc_tmp) != 1) {
            ALOGE("Couldn't process %s", wline_at.c_str());
        } else {
            tmp[0] = wireless_charge_stats_.TranslateSysModeToAtomValue(ssoc_tmp);
            ALOGD("wlc: processing %s", wline_ac.c_str());
            if (sscanf(wline_ac.c_str(), "D:%x,%x,%x,%x,%x, %x,%x", &tmp[8], &tmp[9], &tmp[10],
                       &tmp[11], &tmp[12], &tmp[13], &tmp[14]) != 7)
                ALOGE("Couldn't process %s", wline_ac.c_str());
            else
                fields_size = chg_fields_size; /* include wlc stats */
        }
    }

    if (!pca_line.empty()) {
        ALOGD("pca: processing %s", pca_line.c_str());
        if (sscanf(pca_line.c_str(), "D:%x,%x %x,%x,%x,%x,%x", &pca_ac[0], &pca_ac[1], &pca_rs[0],
                   &pca_rs[1], &pca_rs[2], &pca_rs[3], &pca_rs[4]) != 7) {
            ALOGE("Couldn't process %s", pca_line.c_str());
        } else {
            fields_size = chg_fields_size; /* include pca stats */
            tmp[10] = pca_rs[2];
            tmp[11] = pca_rs[3];
            tmp[12] = pca_rs[4];
            tmp[14] = pca_rs[1];
            if (wline_at.empty()) {
                /* force adapter type to PPS when pca log is available, but not wlc */
                tmp[0] = PixelAtoms::ChargeStats::ADAPTER_TYPE_USB_PD_PPS;
                tmp[8] = pca_ac[0];
                tmp[9] = pca_ac[1];
                tmp[13] = pca_rs[0];
            }
        }
    }

    for (i = 0; i < fields_size; i++) {
        val.set<VendorAtomValue::intValue>(tmp[i]);
        values[charge_stats_fields[i] - kVendorAtomOffset] = val;
    }

    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kChargeStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report ChargeStats to Stats service");
}

void ChargeStatsReporter::ReportVoltageTierStats(const std::shared_ptr<IStats> &stats_client,
                                                 const char *line, const bool has_wireless = false,
                                                 const std::string &wfile_contents = "") {
    int voltage_tier_stats_fields[] = {
            VoltageTierStats::kVoltageTierFieldNumber,
            VoltageTierStats::kSocInFieldNumber, /* retrieved via ssoc_tmp */
            VoltageTierStats::kCcInFieldNumber,
            VoltageTierStats::kTempInFieldNumber,
            VoltageTierStats::kTimeFastSecsFieldNumber,
            VoltageTierStats::kTimeTaperSecsFieldNumber,
            VoltageTierStats::kTimeOtherSecsFieldNumber,
            VoltageTierStats::kTempMinFieldNumber,
            VoltageTierStats::kTempAvgFieldNumber,
            VoltageTierStats::kTempMaxFieldNumber,
            VoltageTierStats::kIbattMinFieldNumber,
            VoltageTierStats::kIbattAvgFieldNumber,
            VoltageTierStats::kIbattMaxFieldNumber,
            VoltageTierStats::kIclMinFieldNumber,
            VoltageTierStats::kIclAvgFieldNumber,
            VoltageTierStats::kIclMaxFieldNumber,
            VoltageTierStats::kMinAdapterPowerOutFieldNumber,
            VoltageTierStats::kTimeAvgAdapterPowerOutFieldNumber,
            VoltageTierStats::kMaxAdapterPowerOutFieldNumber,
            VoltageTierStats::kChargingOperatingPointFieldNumber};

    const int32_t vtier_fields_size = std::size(voltage_tier_stats_fields);
    static_assert(vtier_fields_size == 20, "Unexpected voltage tier stats fields size");
    const int32_t wlc_fields_size = 4;
    std::vector<VendorAtomValue> values(vtier_fields_size);
    VendorAtomValue val;
    float ssoc_tmp;
    int32_t i = 0, tmp[vtier_fields_size - 1] = {0}, /* ssoc_tmp is not saved in this array */
            fields_size = (vtier_fields_size - wlc_fields_size);

    if (sscanf(line, "%d, %f,%d,%d, %d,%d,%d, %d,%d,%d, %d,%d,%d, %d,%d,%d", &tmp[0], &ssoc_tmp,
               &tmp[1], &tmp[2], &tmp[3], &tmp[4], &tmp[5], &tmp[6], &tmp[7], &tmp[8], &tmp[9],
               &tmp[10], &tmp[11], &tmp[12], &tmp[13], &tmp[14]) != 16) {
        /* If format isn't as expected, then ignore line on purpose */
        return;
    }

    if (has_wireless) {
        wireless_charge_stats_.CalculateWirelessChargeStats(static_cast<int>(ssoc_tmp),
                                                            wfile_contents);
        tmp[15] = wireless_charge_stats_.pout_min_;
        tmp[16] = wireless_charge_stats_.pout_avg_;
        tmp[17] = wireless_charge_stats_.pout_max_;
        tmp[18] = wireless_charge_stats_.of_freq_;
        fields_size = vtier_fields_size; /* include wlc stats */
    }

    ALOGD("VoltageTierStats: processed %s", line);
    val.set<VendorAtomValue::intValue>(tmp[0]);
    values[voltage_tier_stats_fields[0] - kVendorAtomOffset] = val;
    val.set<VendorAtomValue::floatValue>(ssoc_tmp);
    values[voltage_tier_stats_fields[1] - kVendorAtomOffset] = val;
    for (i = 2; i < fields_size; i++) {
        val.set<VendorAtomValue::intValue>(tmp[i - 1]);
        values[voltage_tier_stats_fields[i] - kVendorAtomOffset] = val;
    }

    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVoltageTierStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VoltageTierStats to Stats service");
}

/**
 * b/223664185
 * Adds a rolling window filter to charge stats. If the time has expired, there will be a new log
 * event.
 *
 * This helps ensure that we throttle stats even if there is an intermittent disconnect, while still
 * retaining some stats on the disconnect.
 */
bool ChargeStatsReporter::shouldReportEvent(void) {
    const int64_t current_time = getTimeSecs();
    if (current_time == 0) {
        ALOGE("Current boot time is zero!");
        return false;
    }

    if (log_event_time_secs_ == 0 || log_event_time_secs_ + DURATION_FILTER_SECS < current_time) {
        log_event_time_secs_ = current_time;
        return true;
    }

    return false;
}

void ChargeStatsReporter::checkAndReport(const std::shared_ptr<IStats> &stats_client,
                                         const std::string &path) {
    std::string file_contents, line, wfile_contents, wline_at, wline_ac, pca_file_contents,
            pca_line, thermal_file_contents, gcharger_file_contents, gdbatt_file_contents;
    std::istringstream ss;
    bool has_wireless, has_pca, has_thermal, has_gcharger, has_dual_batt;

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        ALOGE("Unable to read %s - %s", path.c_str(), strerror(errno));
        return;
    }

    ss.str(file_contents);

    if (!std::getline(ss, line)) {
        ALOGE("Unable to read first line");
        return;
    }

    if (!WriteStringToFile("0", path.c_str())) {
        ALOGE("Couldn't clear %s - %s", path.c_str(), strerror(errno));
    }

    if (!shouldReportEvent()) {
        ALOGW("Too many log events; event ignored.");
        return;
    }

    has_pca = pca_charge_stats_.CheckPcaContentsAndAck(&pca_file_contents);
    if (has_pca) {
        std::istringstream pca_ss;

        pca_ss.str(pca_file_contents);
        std::getline(pca_ss, pca_line);
    }

    has_wireless = wireless_charge_stats_.CheckWirelessContentsAndAck(&wfile_contents);
    if (has_wireless) {
        std::istringstream wss;

        /* there are two lines in the head, A: ...(Adapter Type) and D: ...(Adapter Capabilities) */
        wss.str(wfile_contents);
        std::getline(wss, wline_at);
        std::getline(wss, wline_ac);

        /* reset initial tier soc */
        wireless_charge_stats_.tier_soc_ = 0;
    }

    ReportChargeStats(stats_client, line, wline_at, wline_ac, pca_line);

    while (std::getline(ss, line)) {
        ReportVoltageTierStats(stats_client, line.c_str(), has_wireless, wfile_contents);
    }

    has_thermal = checkContentsAndAck(&thermal_file_contents, kThermalChargeMetricsPath);
    if (has_thermal) {
        std::istringstream wss;
        wss.str(thermal_file_contents);
        while (std::getline(wss, line)) {
            ReportVoltageTierStats(stats_client, line.c_str());
        }
    }

    has_gcharger = checkContentsAndAck(&gcharger_file_contents, kGChargerMetricsPath);
    if (has_gcharger) {
        std::istringstream wss;
        wss.str(gcharger_file_contents);
        while (std::getline(wss, line)) {
            ReportVoltageTierStats(stats_client, line.c_str());
        }
    }

    has_dual_batt = checkContentsAndAck(&gdbatt_file_contents, kGDualBattMetricsPath);
    if (has_dual_batt) {
        std::istringstream wss;
        wss.str(gdbatt_file_contents);
        while (std::getline(wss, line)) {
            ReportVoltageTierStats(stats_client, line.c_str());
        }
    }
}

bool ChargeStatsReporter::checkContentsAndAck(std::string *file_contents, const std::string &path) {
    if (!ReadFileToString(path.c_str(), file_contents)) {
        return false;
    }

    if (!WriteStringToFile("0", path.c_str())) {
        ALOGE("Couldn't clear %s - %s", path.c_str(), strerror(errno));
        return false;
    }
    return true;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
