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

#ifndef ART_ODREFRESH_ODR_METRICS_RECORD_H_
#define ART_ODREFRESH_ODR_METRICS_RECORD_H_

#include <cstdint>
#include <iosfwd>  // For forward-declaration of std::string.

#include "android-base/result.h"
#include "exec_utils.h"
#include "tinyxml2.h"

namespace art {
namespace odrefresh {

// Default location for storing metrics from odrefresh.
constexpr const char* kOdrefreshMetricsFile = "/data/misc/odrefresh/odrefresh-metrics.xml";

// Initial OdrefreshMetrics version
static constexpr int32_t kOdrefreshMetricsVersion = 4;

// Constant value used in ExecResult when the process was not run at all.
// Mirrors EXEC_RESULT_STATUS_NOT_RUN contained in frameworks/proto_logging/atoms.proto.
static constexpr int32_t kExecResultNotRun = 5;
static_assert(kExecResultNotRun > ExecResult::Status::kLast,
              "`art::odrefresh::kExecResultNotRun` value should not overlap with"
              " values of enum `art::ExecResult::Status`");


// MetricsRecord is a simpler container for Odrefresh metric values reported to statsd. The order
// and types of fields here mirror definition of `OdrefreshReported` in
// frameworks/proto_logging/stats/atoms.proto.
struct OdrMetricsRecord {
  struct Dex2OatExecResult {
    int32_t status;
    int32_t exit_code;
    int32_t signal;

    explicit Dex2OatExecResult(int32_t status, int32_t exit_code, int32_t signal)
      : status(status), exit_code(exit_code), signal(signal) {}

    explicit Dex2OatExecResult(const ExecResult& result)
      : status(result.status), exit_code(result.exit_code), signal(result.signal) {}

    Dex2OatExecResult() : status(kExecResultNotRun), exit_code(-1), signal(0) {}
  };

  int32_t odrefresh_metrics_version;
  int64_t art_apex_version;
  int32_t trigger;
  int32_t stage_reached;
  int32_t status;
  int32_t cache_space_free_start_mib;
  int32_t cache_space_free_end_mib;
  int32_t primary_bcp_compilation_millis;
  int32_t secondary_bcp_compilation_millis;
  int32_t system_server_compilation_millis;
  Dex2OatExecResult primary_bcp_dex2oat_result;
  Dex2OatExecResult secondary_bcp_dex2oat_result;
  Dex2OatExecResult system_server_dex2oat_result;
  int32_t primary_bcp_compilation_type;
  int32_t secondary_bcp_compilation_type;

  // Reads a `MetricsRecord` from an XML file.
  // Returns an error if the XML document was not found or parsed correctly.
  android::base::Result<void> ReadFromFile(const std::string& filename);

  // Writes a `MetricsRecord` to an XML file.
  // Returns an error if the XML document was not saved correctly.
  android::base::Result<void> WriteToFile(const std::string& filename) const;
};

}  // namespace odrefresh
}  // namespace art

#endif  // ART_ODREFRESH_ODR_METRICS_RECORD_H_
