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

#define LOG_TAG "pixelstats: BatteryHealthReporter"

#include <android-base/file.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <log/log.h>
#include <pixelstats/BatteryHealthReporter.h>
#include <pixelstats/StatsHelper.h>
#include <time.h>
#include <utils/Timers.h>

#include <cinttypes>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::base::WriteStringToFile;
using android::hardware::google::pixel::PixelAtoms::BatteryHealthStatus;
using android::hardware::google::pixel::PixelAtoms::BatteryHealthUsage;

const int SECONDS_PER_MONTH = 60 * 60 * 24 * 30;

BatteryHealthReporter::BatteryHealthReporter() {}

int64_t BatteryHealthReporter::getTimeSecs(void) {
    return nanoseconds_to_seconds(systemTime(SYSTEM_TIME_BOOTTIME));
}

bool BatteryHealthReporter::reportBatteryHealthStatus(const std::shared_ptr<IStats> &stats_client) {
    std::string path = kBatteryHealthStatusPath;
    std::string file_contents, line;
    std::istringstream ss;

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        ALOGD("Unsupported path %s - %s", path.c_str(), strerror(errno));
        return false;
    }

    ss.str(file_contents);

    while (std::getline(ss, line)) {
        reportBatteryHealthStatusEvent(stats_client, line.c_str());
    }

    return true;
}

void BatteryHealthReporter::reportBatteryHealthStatusEvent(
        const std::shared_ptr<IStats> &stats_client, const char *line) {
    int health_status_stats_fields[] = {
            BatteryHealthStatus::kHealthAlgorithmFieldNumber,
            BatteryHealthStatus::kHealthStatusFieldNumber,
            BatteryHealthStatus::kHealthIndexFieldNumber,
            BatteryHealthStatus::kHealthCapacityIndexFieldNumber,
            BatteryHealthStatus::kHealthImpedanceIndexFieldNumber,
            BatteryHealthStatus::kSwellingCumulativeFieldNumber,
            BatteryHealthStatus::kHealthFullCapacityFieldNumber,
            BatteryHealthStatus::kCurrentImpedanceFieldNumber,
            BatteryHealthStatus::kBatteryAgeFieldNumber,
            BatteryHealthStatus::kCycleCountFieldNumber,
            BatteryHealthStatus::kBatteryDisconnectStatusFieldNumber,
    };

    const int32_t vtier_fields_size = std::size(health_status_stats_fields);
    static_assert(vtier_fields_size == 11, "Unexpected battery health status fields size");
    std::vector<VendorAtomValue> values(vtier_fields_size);
    VendorAtomValue val;
    int32_t i = 0, fields_size = 0, tmp[vtier_fields_size] = {0};

    // health_algo: health_status, health_index,healh_capacity_index,health_imp_index,
    // swelling_cumulative,health_full_capacity,current_impedance, battery_age,cycle_count,
    // bpst_status
    fields_size = sscanf(line, "%d: %d, %d,%d,%d %d,%d,%d %d,%d, %d", &tmp[0], &tmp[1], &tmp[2],
                         &tmp[3], &tmp[4], &tmp[5], &tmp[6], &tmp[7], &tmp[8], &tmp[9], &tmp[10]);
    if (fields_size < (vtier_fields_size - 1) || fields_size > vtier_fields_size) {
        // Whether bpst_status exists or not, it needs to be compatible
        // If format isn't as expected, then ignore line on purpose
        return;
    }

    ALOGD("BatteryHealthStatus: processed %s", line);
    for (i = 0; i < fields_size; i++) {
        val.set<VendorAtomValue::intValue>(tmp[i]);
        values[health_status_stats_fields[i] - kVendorAtomOffset] = val;
    }

    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kBatteryHealthStatus,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report BatteryHealthStatus to Stats service");
}

bool BatteryHealthReporter::reportBatteryHealthUsage(const std::shared_ptr<IStats> &stats_client) {
    std::string path = kBatteryHealthUsagePath;
    std::string file_contents, line;
    std::istringstream ss;

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        ALOGD("Unsupported path %s - %s", path.c_str(), strerror(errno));
        return false;
    }

    ss.str(file_contents);

    // skip first title line
    if (!std::getline(ss, line)) {
        ALOGE("Unable to read first line of: %s", path.c_str());
        return false;
    }

    while (std::getline(ss, line)) {
        reportBatteryHealthUsageEvent(stats_client, line.c_str());
    }

    return true;
}

void BatteryHealthReporter::reportBatteryHealthUsageEvent(
        const std::shared_ptr<IStats> &stats_client, const char *line) {
    int health_status_stats_fields[] = {
            BatteryHealthUsage::kTemperatureLimitDeciCFieldNumber,
            BatteryHealthUsage::kSocLimitFieldNumber,
            BatteryHealthUsage::kChargeTimeSecsFieldNumber,
            BatteryHealthUsage::kDischargeTimeSecsFieldNumber,
    };

    const int32_t vtier_fields_size = std::size(health_status_stats_fields);
    static_assert(vtier_fields_size == 4, "Unexpected battery health status fields size");
    std::vector<VendorAtomValue> values(vtier_fields_size);
    VendorAtomValue val;
    int32_t i = 0, tmp[vtier_fields_size] = {0};

    // temp/soc charge(s) discharge(s)
    if (sscanf(line, "%d/%d\t%d\t%d", &tmp[0], &tmp[1], &tmp[2], &tmp[3]) != vtier_fields_size) {
        /* If format isn't as expected, then ignore line on purpose */
        return;
    }

    ALOGD("BatteryHealthUsage: processed %s", line);
    for (i = 0; i < vtier_fields_size; i++) {
        val.set<VendorAtomValue::intValue>(tmp[i]);
        values[health_status_stats_fields[i] - kVendorAtomOffset] = val;
    }

    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kBatteryHealthUsage,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report BatteryHealthStatus to Stats service");
}

void BatteryHealthReporter::checkAndReportStatus(const std::shared_ptr<IStats> &stats_client) {
    int64_t now = getTimeSecs();
    if ((report_time_ != 0) && (now - report_time_ < SECONDS_PER_MONTH)) {
        ALOGD("Do not upload yet. now: %" PRId64 ", pre: %" PRId64, now, report_time_);
        return;
    }

    bool successStatus = reportBatteryHealthStatus(stats_client);
    bool successUsage = reportBatteryHealthUsage(stats_client);

    if (successStatus && successUsage) {
        report_time_ = now;
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
