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

#define LOG_TAG "pixelstats: TempResidencyStats"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/TempResidencyReporter.h>
#include <utils/Log.h>

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
using android::hardware::google::pixel::PixelAtoms::ThermalDfsStats;

bool updateOffsetAndCheckBound(int *offset, const int &bytes_read, const int &data_len) {
    *offset += bytes_read;
    return *offset <= data_len;
}

/**
 * Parse file_contents and read residency stats into stats.
 */
bool parse_file_contents(std::string file_contents,
                         std::map<std::string, TempResidencyStats> *stats) {
    const char *data = file_contents.c_str();
    int data_len = file_contents.length();
    char sensor_name[32];
    int offset = 0;
    int bytes_read;

    while (sscanf(data + offset, "THERMAL ZONE: %31s\n%n", sensor_name, &bytes_read) == 1) {
        TempResidencyStats temp_residency_stats;
        int64_t temp_res_value;
        int num_stats_buckets;
        int index = 0;
        if (!updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        std::string sensor_name_str = sensor_name;

        if (!sscanf(data + offset, "MAX_TEMP: %f\n%n", &temp_residency_stats.max_temp,
                    &bytes_read) ||
            !updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        if (!sscanf(data + offset, "MAX_TEMP_TIMESTAMP: %" PRId64 "s\n%n",
                    &temp_residency_stats.max_temp_timestamp, &bytes_read) ||
            !updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        if (!sscanf(data + offset, "MIN_TEMP: %f\n%n", &temp_residency_stats.min_temp,
                    &bytes_read) ||
            !updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        if (!sscanf(data + offset, "MIN_TEMP_TIMESTAMP: %" PRId64 "s\n%n",
                    &temp_residency_stats.min_temp_timestamp, &bytes_read) ||
            !updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        if (!sscanf(data + offset, "NUM_TEMP_RESIDENCY_BUCKETS: %d\n%n", &num_stats_buckets,
                    &bytes_read) ||
            !updateOffsetAndCheckBound(&offset, bytes_read, data_len))
            return false;

        while (index < num_stats_buckets) {
            if (sscanf(data + offset, "-inf - %*d ====> %" PRId64 "ms\n%n", &temp_res_value,
                       &bytes_read) != 1 &&
                sscanf(data + offset, "%*d - %*d ====> %" PRId64 "ms\n%n", &temp_res_value,
                       &bytes_read) != 1 &&
                sscanf(data + offset, "%*d - inf ====> %" PRId64 "ms\n\n%n", &temp_res_value,
                       &bytes_read) != 1)
                return false;

            temp_residency_stats.temp_residency_buckets.push_back(temp_res_value);
            index++;

            offset += bytes_read;
            if ((offset >= data_len) && (index < num_stats_buckets))
                return false;
        }
        (*stats)[sensor_name_str] = temp_residency_stats;
    }
    return true;
}

/**
 * Logs the Temperature residency stats for every thermal zone.
 */
void TempResidencyReporter::logTempResidencyStats(
        const std::shared_ptr<IStats> &stats_client, std::string_view temperature_residency_path,
        std::string_view temperature_residency_reset_path) {
    if (temperature_residency_path.empty() || temperature_residency_reset_path.empty()) {
        ALOGV("TempResidency Stats/Reset path not specified");
        return;
    }
    std::string file_contents;
    if (!ReadFileToString(temperature_residency_path.data(), &file_contents)) {
        ALOGE("Unable to read TempResidencyStatsPath");
        return;
    }
    std::map<std::string, TempResidencyStats> stats_map;
    if (!parse_file_contents(file_contents, &stats_map)) {
        ALOGE("Fail to parse TempResidencyStatsPath");
        return;
    }
    if (!stats_map.size())
        return;
    ::android::base::boot_clock::time_point curTime = ::android::base::boot_clock::now();
    int64_t since_last_update_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(curTime - prevTime).count();

    // Reset the stats for next day collection and if failed return without reporting to report the
    // combined residency next day.
    if (!WriteStringToFile(std::to_string(1), temperature_residency_reset_path.data())) {
        ALOGE("Failed to reset TempResidencyStats");
        return;
    }

    auto stats_map_iterator = stats_map.begin();
    VendorAtomValue tmp_atom_value;

    // Iterate through stats_map by sensor_name
    while (stats_map_iterator != stats_map.end()) {
        std::vector<VendorAtomValue> values;
        const auto &sensor_name_str = stats_map_iterator->first;
        const auto &temp_residency_stats = stats_map_iterator->second;
        const auto &temp_residency_buckets_count =
                temp_residency_stats.temp_residency_buckets.size();
        if (temp_residency_buckets_count > kMaxBucketLen) {
            stats_map_iterator++;
            continue;
        }
        tmp_atom_value.set<VendorAtomValue::stringValue>(sensor_name_str);
        values.push_back(tmp_atom_value);
        tmp_atom_value.set<VendorAtomValue::longValue>(since_last_update_ms);
        values.push_back(tmp_atom_value);
        // Iterate over every temperature residency buckets
        for (const auto &temp_residency_bucket : temp_residency_stats.temp_residency_buckets) {
            tmp_atom_value.set<VendorAtomValue::longValue>(temp_residency_bucket);
            values.push_back(tmp_atom_value);
        }
        // Fill the remaining residency buckets with 0.
        int remaining_residency_buckets_count = kMaxBucketLen - temp_residency_buckets_count;
        if (remaining_residency_buckets_count > 0) {
            tmp_atom_value.set<VendorAtomValue::longValue>(0);
            values.insert(values.end(), remaining_residency_buckets_count, tmp_atom_value);
        }
        tmp_atom_value.set<VendorAtomValue::floatValue>(temp_residency_stats.max_temp);
        values.push_back(tmp_atom_value);
        tmp_atom_value.set<VendorAtomValue::longValue>(temp_residency_stats.max_temp_timestamp);
        values.push_back(tmp_atom_value);
        tmp_atom_value.set<VendorAtomValue::floatValue>(temp_residency_stats.min_temp);
        values.push_back(tmp_atom_value);
        tmp_atom_value.set<VendorAtomValue::longValue>(temp_residency_stats.min_temp_timestamp);
        values.push_back(tmp_atom_value);
        //  Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorTempResidencyStats,
                            .values = std::move(values)};
        ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorTempResidencyStats to Stats service");

        stats_map_iterator++;
    }
    prevTime = curTime;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
