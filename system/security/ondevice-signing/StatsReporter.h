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

#pragma once

#include <fstream>

#include "statslog_odsign.h"

// Class to store CompOsArtifactsCheck related metrics.
// These are flushed to a file kOdsignMetricsFile and consumed by
// System Server (in class OdsignStatsLogger) & sent to statsd.
class StatsReporter {
  public:
    // Keep in sync with the EarlyBootCompOsArtifactsCheckReported definition in
    // proto_logging/stats/atoms.proto.
    struct CompOsArtifactsCheckRecord {
        bool current_artifacts_ok = false;
        bool comp_os_pending_artifacts_exists = false;
        bool use_comp_os_generated_artifacts = false;
    };

    // Keep in sync with the OdsignReported definition in proto_logging/stats/atoms.proto.
    struct OdsignRecord {
        int32_t status = art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_UNSPECIFIED;
    };

    // The report is flushed (from buffer) into a file by the destructor.
    ~StatsReporter();

    // Returns a mutable CompOS record. The pointer remains valid for the lifetime of this
    // StatsReporter. If this function is not called, no CompOS record will be logged.
    CompOsArtifactsCheckRecord* GetOrCreateComposArtifactsCheckRecord();

    // Returns a mutable odsign record. The pointer remains valid for the lifetime of this
    // StatsReporter.
    OdsignRecord* GetOdsignRecord() { return &odsign_record_; }

    // Enables/disables odsign metrics.
    void SetOdsignRecordEnabled(bool value) { odsign_record_enabled_ = value; }

  private:
    // Temporary buffer which stores the metrics.
    std::unique_ptr<CompOsArtifactsCheckRecord> comp_os_artifacts_check_record_;

    OdsignRecord odsign_record_;
    bool odsign_record_enabled_ = true;
};
