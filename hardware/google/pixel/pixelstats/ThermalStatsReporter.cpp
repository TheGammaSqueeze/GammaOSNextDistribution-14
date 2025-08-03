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

#define LOG_TAG "pixelstats: ThermalStats"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/ThermalStatsReporter.h>
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
using android::hardware::google::pixel::PixelAtoms::ThermalDfsStats;

ThermalStatsReporter::ThermalStatsReporter() {}

bool ThermalStatsReporter::readDfsCount(const std::string &path, int64_t *val) {
    std::string file_contents;

    if (path.empty()) {
        ALOGE("Empty path");
        return false;
    }

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        ALOGE("Unable to read %s - %s", path.c_str(), strerror(errno));
        return false;
    } else {
        int64_t trips[8];

        if (sscanf(file_contents.c_str(),
                   "%" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64
                   " %" SCNd64 " %" SCNd64,
                   &trips[0], &trips[1], &trips[2], &trips[3], &trips[4], &trips[5], &trips[6],
                   &trips[7]) < 8) {
            ALOGE("Unable to parse trip_counters %s from file %s", file_contents.c_str(),
                  path.c_str());
            return false;
        }

        /* Trip#6 corresponds to DFS count */
        *val = trips[6];
    }

    return true;
}

bool ThermalStatsReporter::captureThermalDfsStats(
        const std::vector<std::string> &thermal_stats_paths, struct ThermalDfsCounts *pcur_data) {
    bool report_stats = false;
    std::string path;

    if (thermal_stats_paths.size() < kNumOfThermalDfsStats) {
        ALOGE("Number of thermal stats paths (%zu) is less than expected (%d)",
              thermal_stats_paths.size(), kNumOfThermalDfsStats);
        return false;
    }

    path = thermal_stats_paths[ThermalDfsStats::kBigDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->big_count))) {
        pcur_data->big_count = prev_data.big_count;
    } else {
        report_stats |= (pcur_data->big_count > prev_data.big_count);
    }

    path = thermal_stats_paths[ThermalDfsStats::kMidDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->mid_count))) {
        pcur_data->mid_count = prev_data.mid_count;
    } else {
        report_stats |= (pcur_data->mid_count > prev_data.mid_count);
    }

    path = thermal_stats_paths[ThermalDfsStats::kLittleDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->little_count))) {
        pcur_data->little_count = prev_data.little_count;
    } else {
        report_stats |= (pcur_data->little_count > prev_data.little_count);
    }

    path = thermal_stats_paths[ThermalDfsStats::kGpuDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->gpu_count))) {
        pcur_data->gpu_count = prev_data.gpu_count;
    } else {
        report_stats |= (pcur_data->gpu_count > prev_data.gpu_count);
    }

    path = thermal_stats_paths[ThermalDfsStats::kTpuDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->tpu_count))) {
        pcur_data->tpu_count = prev_data.tpu_count;
    } else {
        report_stats |= (pcur_data->tpu_count > prev_data.tpu_count);
    }

    path = thermal_stats_paths[ThermalDfsStats::kAurDfsCountFieldNumber - kVendorAtomOffset];
    if (!readDfsCount(path, &(pcur_data->aur_count))) {
        pcur_data->aur_count = prev_data.aur_count;
    } else {
        report_stats |= (pcur_data->aur_count > prev_data.aur_count);
    }

    return report_stats;
}

void ThermalStatsReporter::logThermalDfsStats(const std::shared_ptr<IStats> &stats_client,
                                              const std::vector<std::string> &thermal_stats_paths) {
    struct ThermalDfsCounts cur_data = prev_data;

    if (!captureThermalDfsStats(thermal_stats_paths, &cur_data)) {
        prev_data = cur_data;
        ALOGI("No update found for thermal stats");
        return;
    }

    VendorAtomValue tmp;
    int64_t max_dfs_count = static_cast<int64_t>(INT32_MAX);
    int dfs_count;
    std::vector<VendorAtomValue> values(kNumOfThermalDfsStats);

    dfs_count = std::min<int64_t>(cur_data.big_count - prev_data.big_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kBigDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    dfs_count = std::min<int64_t>(cur_data.mid_count - prev_data.mid_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kMidDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    dfs_count = std::min<int64_t>(cur_data.little_count - prev_data.little_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kLittleDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    dfs_count = std::min<int64_t>(cur_data.gpu_count - prev_data.gpu_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kGpuDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    dfs_count = std::min<int64_t>(cur_data.tpu_count - prev_data.tpu_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kTpuDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    dfs_count = std::min<int64_t>(cur_data.aur_count - prev_data.aur_count, max_dfs_count);
    tmp.set<VendorAtomValue::intValue>(dfs_count);
    values[ThermalDfsStats::kAurDfsCountFieldNumber - kVendorAtomOffset] = tmp;

    prev_data = cur_data;

    ALOGD("Report updated thermal metrics to stats service");
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kThermalDfsStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report thermal DFS stats to Stats service");
}

void ThermalStatsReporter::logThermalStats(const std::shared_ptr<IStats> &stats_client,
                                           const std::vector<std::string> &thermal_stats_paths) {
    logThermalDfsStats(stats_client, thermal_stats_paths);
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
