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

#define LOG_TAG "pixelstats: DisplayStats"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/DisplayStatsReporter.h>
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
using android::hardware::google::pixel::PixelAtoms::DisplayPanelErrorStats;

DisplayStatsReporter::DisplayStatsReporter() {}

bool DisplayStatsReporter::readDisplayErrorCount(const std::string &path, int64_t *val) {
    std::string file_contents;

    if (path.empty()) {
        return false;
    }

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        if (errno != ENOENT) {
            ALOGD("readDisplayErrorCount Unable to read %s - %s", path.c_str(), strerror(errno));
        }
        return false;
    } else {
        file_contents = android::base::Trim(file_contents);
        if (!android::base::ParseInt(file_contents, val)) {
            return false;
        }
    }

    return true;
}

int DisplayStatsReporter::verifyCount(int val, bool *report_stats) {
    if (val < 0) {
        ALOGE("Invalid display stats value(%d)", val);
        return -EINVAL;
    } else {
        *report_stats |= (val != 0);
    }

    return 0;
}

bool DisplayStatsReporter::captureDisplayPanelErrorStats(
        const std::vector<std::string> &display_stats_paths,
        struct DisplayPanelErrorStats *pcur_data) {
    bool report_stats = false;
    std::string path;

    if (display_stats_paths.size() < kNumOfDisplayPanelErrorStats) {
        ALOGE("Number of display stats paths (%zu) is less than expected (%d)",
              display_stats_paths.size(), kNumOfDisplayPanelErrorStats);
        return false;
    }

    int64_t index = PixelAtoms::DisplayPanelErrorStats::kPrimaryErrorCountTeFieldNumber;
    index = index - kVendorAtomOffset;
    path = display_stats_paths[index];

    // Read primary panel error stats.
    if (!readDisplayErrorCount(path, &(pcur_data->primary_error_count_te))) {
        pcur_data->primary_error_count_te = prev_panel_data_.primary_error_count_te;
    } else {
        report_stats |=
                (pcur_data->primary_error_count_te > prev_panel_data_.primary_error_count_te);
    }

    index = PixelAtoms::DisplayPanelErrorStats::kPrimaryErrorCountUnknownFieldNumber;
    index = index - kVendorAtomOffset;
    path = display_stats_paths[index];
    if (!readDisplayErrorCount(path, &(pcur_data->primary_error_count_unknown))) {
        pcur_data->primary_error_count_unknown = prev_panel_data_.primary_error_count_unknown;
    } else {
        report_stats |= (pcur_data->primary_error_count_unknown >
                         prev_panel_data_.primary_error_count_unknown);
    }

    // Read secondary panel error stats.
    index = PixelAtoms::DisplayPanelErrorStats::kSecondaryErrorCountTeFieldNumber;
    index = index - kVendorAtomOffset;
    path = display_stats_paths[index];
    if (!readDisplayErrorCount(path, &(pcur_data->secondary_error_count_te))) {
        pcur_data->secondary_error_count_te = prev_panel_data_.secondary_error_count_te;
    } else {
        report_stats |=
                (pcur_data->secondary_error_count_te > prev_panel_data_.secondary_error_count_te);
    }

    index = PixelAtoms::DisplayPanelErrorStats::kSecondaryErrorCountUnknownFieldNumber;
    index = index - kVendorAtomOffset;
    path = display_stats_paths[index];
    if (!readDisplayErrorCount(path, &(pcur_data->secondary_error_count_unknown))) {
        pcur_data->secondary_error_count_unknown = prev_panel_data_.secondary_error_count_unknown;
    } else {
        report_stats |= (pcur_data->secondary_error_count_unknown >
                         prev_panel_data_.secondary_error_count_unknown);
    }

    return report_stats;
}

void DisplayStatsReporter::logDisplayPanelErrorStats(
        const std::shared_ptr<IStats> &stats_client,
        const std::vector<std::string> &display_stats_paths) {
    struct DisplayPanelErrorStats cur_data = prev_panel_data_;
    bool report_stats = false;

    if (!captureDisplayPanelErrorStats(display_stats_paths, &cur_data)) {
        prev_panel_data_ = cur_data;
        return;
    }

    VendorAtomValue tmp;
    int64_t max_error_count = static_cast<int64_t>(INT32_MAX);
    int error_count;
    std::vector<VendorAtomValue> values(kNumOfDisplayPanelErrorStats);

    error_count = std::min<int64_t>(
            cur_data.primary_error_count_te - prev_panel_data_.primary_error_count_te,
            max_error_count);
    if (verifyCount(error_count, &report_stats) < 0)
        return;

    tmp.set<VendorAtomValue::intValue>(error_count);
    int64_t index = PixelAtoms::DisplayPanelErrorStats::kPrimaryErrorCountTeFieldNumber;
    index = index - kVendorAtomOffset;
    values[index] = tmp;

    error_count = std::min<int64_t>(
            cur_data.primary_error_count_unknown - prev_panel_data_.primary_error_count_unknown,
            max_error_count);
    if (verifyCount(error_count, &report_stats) < 0)
        return;

    tmp.set<VendorAtomValue::intValue>(error_count);
    index = PixelAtoms::DisplayPanelErrorStats::kPrimaryErrorCountUnknownFieldNumber;
    index = index - kVendorAtomOffset;
    values[index] = tmp;

    prev_panel_data_ = cur_data;

    if (!report_stats)
        return;

    ALOGD("Report updated display panel metrics to stats service");
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kDisplayPanelErrorStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report display Display Panel stats to Stats service");
}

bool DisplayStatsReporter::captureDisplayPortErrorStats(
        const std::vector<std::string> &displayport_stats_paths, int64_t *pcur_data) {
    int64_t path_index;
    bool report_stats = false;
    std::string path;

    if (displayport_stats_paths.size() < DISPLAY_PORT_ERROR_STATS_SIZE)
        return false;

    for (int i = 0; i < DISPLAY_PORT_ERROR_STATS_SIZE; i++) {
        path_index = display_port_error_path_index[i];
        path_index = path_index - kVendorAtomOffset;
        path = displayport_stats_paths[path_index];

        if (!readDisplayErrorCount(path, &(pcur_data[i]))) {
            pcur_data[i] = prev_dp_data_[i];
        } else {
            report_stats |= (pcur_data[i] > prev_dp_data_[i]);
        }
    }

    return report_stats;
}

void DisplayStatsReporter::logDisplayPortErrorStats(
        const std::shared_ptr<IStats> &stats_client,
        const std::vector<std::string> &displayport_stats_paths) {
    int64_t cur_data[DISPLAY_PORT_ERROR_STATS_SIZE];
    int64_t path_index;
    bool report_stats = false;

    memcpy(cur_data, prev_dp_data_, sizeof(prev_dp_data_));
    if (!captureDisplayPortErrorStats(displayport_stats_paths, &cur_data[0])) {
        memcpy(prev_dp_data_, cur_data, sizeof(cur_data));
        return;
    }

    VendorAtomValue tmp;
    int64_t max_error_count = static_cast<int64_t>(INT32_MAX);
    int error_count;
    std::vector<VendorAtomValue> values(DISPLAY_PORT_ERROR_STATS_SIZE);

    for (int i = 0; i < DISPLAY_PORT_ERROR_STATS_SIZE; i++) {
        error_count = std::min<int64_t>(cur_data[i] - prev_dp_data_[i], max_error_count);
        if (verifyCount(error_count, &report_stats) < 0)
            return;

        tmp.set<VendorAtomValue::intValue>(error_count);
        path_index = display_port_error_path_index[i];
        path_index = path_index - kVendorAtomOffset;
        values[path_index] = tmp;
    }

    memcpy(prev_dp_data_, cur_data, sizeof(cur_data));

    if (!report_stats)
        return;

    ALOGD("Report updated displayport metrics to stats service");
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kDisplayPortErrorStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report DisplayPort stats to Stats service");
}

bool DisplayStatsReporter::captureHDCPAuthTypeStats(
        const std::vector<std::string> &hdcp_stats_paths, int64_t *pcur_data) {
    int64_t path_index;
    bool report_stats = false;
    std::string path;

    if (hdcp_stats_paths.size() < HDCP_AUTH_TYPE_STATS_SIZE)
        return false;

    for (int i = 0; i < HDCP_AUTH_TYPE_STATS_SIZE; i++) {
        path_index = hdcp_auth_type_path_index[i];
        path_index = path_index - kVendorAtomOffset;
        path = hdcp_stats_paths[path_index];

        if (!readDisplayErrorCount(path, &(pcur_data[i]))) {
            pcur_data[i] = prev_hdcp_data_[i];
        } else {
            report_stats |= (pcur_data[i] > prev_hdcp_data_[i]);
        }
    }

    return report_stats;
}

void DisplayStatsReporter::logHDCPAuthTypeStats(const std::shared_ptr<IStats> &stats_client,
                                                const std::vector<std::string> &hdcp_stats_paths) {
    int64_t cur_data[HDCP_AUTH_TYPE_STATS_SIZE];
    int64_t path_index;
    bool report_stats = false;

    memcpy(cur_data, prev_hdcp_data_, sizeof(prev_hdcp_data_));
    if (!captureHDCPAuthTypeStats(hdcp_stats_paths, &cur_data[0])) {
        memcpy(prev_hdcp_data_, cur_data, sizeof(cur_data));
        return;
    }

    VendorAtomValue tmp;
    int64_t max_error_count = static_cast<int64_t>(INT32_MAX);
    int error_count;
    std::vector<VendorAtomValue> values(HDCP_AUTH_TYPE_STATS_SIZE);

    for (int i = 0; i < HDCP_AUTH_TYPE_STATS_SIZE; i++) {
        error_count = std::min<int64_t>(cur_data[i] - prev_hdcp_data_[i], max_error_count);
        if (verifyCount(error_count, &report_stats) < 0)
            return;

        tmp.set<VendorAtomValue::intValue>(error_count);
        path_index = hdcp_auth_type_path_index[i];
        path_index = path_index - kVendorAtomOffset;
        values[path_index] = tmp;
    }

    memcpy(prev_hdcp_data_, cur_data, sizeof(cur_data));

    if (!report_stats)
        return;

    ALOGD("Report updated hdcp metrics to stats service");
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kHdcpAuthTypeStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report hdcp stats to Stats service");
}
void DisplayStatsReporter::logDisplayStats(const std::shared_ptr<IStats> &stats_client,
                                           const std::vector<std::string> &display_stats_paths,
                                           const display_stats_type stats_type) {
    switch (stats_type) {
        case DISP_PANEL_STATE:
            logDisplayPanelErrorStats(stats_client, display_stats_paths);
            break;
        case DISP_PORT_STATE:
            logDisplayPortErrorStats(stats_client, display_stats_paths);
            break;
        case HDCP_STATE:
            logHDCPAuthTypeStats(stats_client, display_stats_paths);
            break;
        default:
            ALOGE("Unsupport display state type(%d)", stats_type);
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
