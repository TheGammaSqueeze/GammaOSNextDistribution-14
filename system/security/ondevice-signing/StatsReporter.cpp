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

#include "StatsReporter.h"
#include <android-base/logging.h>
#include <stdlib.h>
#include <string>
#include <sys/stat.h>

// Keep these constants in sync with those in OdsignStatsLogger.java.
constexpr const char* kOdsignMetricsFile = "/data/misc/odsign/metrics/odsign-metrics.txt";
constexpr const char* kComposMetricName = "comp_os_artifacts_check_record";
constexpr const char* kOdsignMetricName = "odsign_record";

StatsReporter::~StatsReporter() {
    if (comp_os_artifacts_check_record_ == nullptr && !odsign_record_enabled_) {
        LOG(INFO) << "Metrics report is empty";

        // Remove the metrics file if any old version of the file already exists
        if (std::filesystem::remove(kOdsignMetricsFile) != 0 &&
            !((errno = ENOENT) || errno == ENOTDIR)) {
            PLOG(ERROR) << "Could not remove already present file";
        }
        return;
    }

    std::ofstream odsign_metrics_file_;
    odsign_metrics_file_.open(kOdsignMetricsFile, std::ios::trunc);
    if (!odsign_metrics_file_) {
        PLOG(ERROR) << "Could not open file: " << kOdsignMetricsFile;
        return;
    }
    if (chmod(kOdsignMetricsFile, 0644) != 0) {
        PLOG(ERROR) << "Could not set correct file permissions for " << kOdsignMetricsFile;
        return;
    }

    if (comp_os_artifacts_check_record_ != nullptr) {
        odsign_metrics_file_ << kComposMetricName << ' '
                             << comp_os_artifacts_check_record_->current_artifacts_ok << ' '
                             << comp_os_artifacts_check_record_->comp_os_pending_artifacts_exists
                             << ' '
                             << comp_os_artifacts_check_record_->use_comp_os_generated_artifacts
                             << '\n';
    }

    if (odsign_record_enabled_) {
        odsign_metrics_file_ << kOdsignMetricName << ' ' << odsign_record_.status << '\n';
    }

    odsign_metrics_file_.close();
    if (!odsign_metrics_file_) {
        PLOG(ERROR) << "Failed to close the file";
    }
}

StatsReporter::CompOsArtifactsCheckRecord* StatsReporter::GetOrCreateComposArtifactsCheckRecord() {
    if (comp_os_artifacts_check_record_ == nullptr) {
        comp_os_artifacts_check_record_ = std::make_unique<CompOsArtifactsCheckRecord>();
    }
    return comp_os_artifacts_check_record_.get();
}
