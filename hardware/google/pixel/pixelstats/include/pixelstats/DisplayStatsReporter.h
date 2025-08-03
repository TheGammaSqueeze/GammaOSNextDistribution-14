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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_DISPLAYSTATSREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_DISPLAYSTATSREPORTER_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

/**
 * A class to upload Pixel Display Stats metrics
 */
class DisplayStatsReporter {
  public:
    DisplayStatsReporter();

    enum display_stats_type {
        DISP_PANEL_STATE = 0,
        DISP_PORT_STATE,
        HDCP_STATE,
    };
    void logDisplayStats(const std::shared_ptr<IStats> &stats_client,
                         const std::vector<std::string> &display_stats_paths,
                         const display_stats_type stats_type);

  private:
    bool readDisplayErrorCount(const std::string &path, int64_t *val);
    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    static constexpr int kVendorAtomOffset = 2;
    static constexpr int kNumOfDisplayPanelErrorStats = 4;

    int verifyCount(int val, bool *report_stats);

    /* display state */
    struct DisplayPanelErrorStats {
        int64_t primary_error_count_te;
        int64_t primary_error_count_unknown;
        int64_t secondary_error_count_te;
        int64_t secondary_error_count_unknown;
    };
    struct DisplayPanelErrorStats prev_panel_data_ = {};
    void logDisplayPanelErrorStats(const std::shared_ptr<IStats> &stats_client,
                                   const std::vector<std::string> &display_stats_paths);
    bool captureDisplayPanelErrorStats(const std::vector<std::string> &display_stats_paths,
                                       struct DisplayPanelErrorStats *cur_data);

    /* displayport state */
    enum display_port_error_stats_index {
        LINK_NEGOTIATION_FAILURES = 0,
        EDID_READ_FAILURES,
        DPCD_READ_FAILURES,
        EDID_INVALID_FAILURES,
        SINK_COUNT_INVALID_FAILURES,
        LINK_UNSTABLE_FAILURES,
        DISPLAY_PORT_ERROR_STATS_SIZE,
    };
    static constexpr int64_t display_port_error_path_index[DISPLAY_PORT_ERROR_STATS_SIZE] = {
            PixelAtoms::DisplayPortErrorStats::kLinkNegotiationFailuresFieldNumber,
            PixelAtoms::DisplayPortErrorStats::kEdidReadFailuresFieldNumber,
            PixelAtoms::DisplayPortErrorStats::kDpcdReadFailuresFieldNumber,
            PixelAtoms::DisplayPortErrorStats::kEdidInvalidFailuresFieldNumber,
            PixelAtoms::DisplayPortErrorStats::kSinkCountInvalidFailuresFieldNumber,
            PixelAtoms::DisplayPortErrorStats::kLinkUnstableFailuresFieldNumber};
    int64_t prev_dp_data_[DISPLAY_PORT_ERROR_STATS_SIZE] = {0};

    void logDisplayPortErrorStats(const std::shared_ptr<IStats> &stats_client,
                                  const std::vector<std::string> &displayport_stats_paths);
    bool captureDisplayPortErrorStats(const std::vector<std::string> &displayport_stats_paths,
                                      int64_t *cur_data);

    /* HDCP state */
    enum hdcp_auth_type_stats_index {
        HDCP2_SUCCESS = 0,
        HDCP2_FALLBACK,
        HDCP2_FAIL,
        HDCP1_SUCCESS,
        HDCP1_FAIL,
        HDCP0,
        HDCP_AUTH_TYPE_STATS_SIZE,
    };
    static constexpr int64_t hdcp_auth_type_path_index[HDCP_AUTH_TYPE_STATS_SIZE] = {
            PixelAtoms::HDCPAuthTypeStats::kHdcp2SuccessCountFieldNumber,
            PixelAtoms::HDCPAuthTypeStats::kHdcp2FallbackCountFieldNumber,
            PixelAtoms::HDCPAuthTypeStats::kHdcp2FailCountFieldNumber,
            PixelAtoms::HDCPAuthTypeStats::kHdcp1SuccessCountFieldNumber,
            PixelAtoms::HDCPAuthTypeStats::kHdcp1FailCountFieldNumber,
            PixelAtoms::HDCPAuthTypeStats::kHdcp0CountFieldNumber};
    int64_t prev_hdcp_data_[HDCP_AUTH_TYPE_STATS_SIZE] = {0};

    void logHDCPAuthTypeStats(const std::shared_ptr<IStats> &stats_client,
                              const std::vector<std::string> &hdcp_stats_paths);
    bool captureHDCPAuthTypeStats(const std::vector<std::string> &hdcp_stats_paths,
                                  int64_t *cur_data);
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_DISPLAYSTATSREPORTER_H
