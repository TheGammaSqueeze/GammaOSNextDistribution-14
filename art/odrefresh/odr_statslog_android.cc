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


#include "odr_statslog/odr_statslog.h"

#include <cstdint>
#include <fstream>
#include <istream>
#include <string>

#include "android-base/logging.h"
#include "android-base/stringprintf.h"
#include "odr_metrics.h"
#include "odr_metrics_record.h"
#include "statslog_odrefresh.h"

namespace art {
namespace odrefresh {

using android::base::StringPrintf;

namespace {

bool ReadValues(const char* metrics_file,
                /*out*/ OdrMetricsRecord* record,
                /*out*/ std::string* error_msg) {
  const android::base::Result<void>& result = record->ReadFromFile(metrics_file);
  if (!result.ok()) {
    *error_msg = android::base::StringPrintf("Unable to open or parse metrics file %s (error: %s)",
                                             metrics_file,
                                             result.error().message().data());
    return false;
  }

  return true;
}

}  // namespace

bool UploadStatsIfAvailable(/*out*/std::string* error_msg) {
  OdrMetricsRecord record;
  if (!ReadValues(kOdrefreshMetricsFile, &record, error_msg)) {
    return false;
  }

  // Write values to statsd. The order of values passed is the same as the order of the
  // fields in OdrMetricsRecord.
  int bytes_written =
      art::metrics::statsd::stats_write(metrics::statsd::ODREFRESH_REPORTED,
                                        record.art_apex_version,
                                        record.trigger,
                                        record.stage_reached,
                                        record.status,
                                        record.primary_bcp_compilation_millis / 1000,
                                        record.secondary_bcp_compilation_millis / 1000,
                                        record.system_server_compilation_millis / 1000,
                                        record.cache_space_free_start_mib,
                                        record.cache_space_free_end_mib,
                                        record.primary_bcp_compilation_millis,
                                        record.secondary_bcp_compilation_millis,
                                        record.system_server_compilation_millis,
                                        record.primary_bcp_dex2oat_result.status,
                                        record.primary_bcp_dex2oat_result.exit_code,
                                        record.primary_bcp_dex2oat_result.signal,
                                        record.secondary_bcp_dex2oat_result.status,
                                        record.secondary_bcp_dex2oat_result.exit_code,
                                        record.secondary_bcp_dex2oat_result.signal,
                                        record.system_server_dex2oat_result.status,
                                        record.system_server_dex2oat_result.exit_code,
                                        record.system_server_dex2oat_result.signal,
                                        record.primary_bcp_compilation_type,
                                        record.secondary_bcp_compilation_type);
  if (bytes_written <= 0) {
    *error_msg = android::base::StringPrintf("stats_write returned %d", bytes_written);
    return false;
  }

  if (unlink(kOdrefreshMetricsFile) != 0) {
    *error_msg = StringPrintf("failed to unlink '%s': %s", kOdrefreshMetricsFile, strerror(errno));
    return false;
  }

  return true;
}

}  // namespace odrefresh
}  // namespace art
