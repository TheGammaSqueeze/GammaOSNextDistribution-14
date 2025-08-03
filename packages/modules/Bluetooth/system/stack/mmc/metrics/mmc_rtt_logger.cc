/*
 * Copyright 2023 The Android Open Source Project
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

#include "mmc/metrics/mmc_rtt_logger.h"

#include <base/logging.h>

#include <algorithm>
#include <cmath>
#include <string>

#include "stack/include/stack_metrics_logging.h"

namespace mmc {

MmcRttLogger::MmcRttLogger(int codec_type)
    : codec_type_(codec_type), num_requests_(0), rtt_sum_(0), maximum_rtt_(0) {}

MmcRttLogger::~MmcRttLogger() {}

void MmcRttLogger::RecordRtt(int64_t elapsed_time) {
  if (elapsed_time <= 0) return;
  num_requests_ += 1;
  rtt_sum_ += elapsed_time;
  maximum_rtt_ = std::max(maximum_rtt_, elapsed_time);
  return;
}

void MmcRttLogger::UploadTranscodeRttStatics() {
  if (num_requests_ == 0) return;
  log_mmc_transcode_rtt_stats(maximum_rtt_, rtt_sum_ / num_requests_,
                              num_requests_, codec_type_);
  num_requests_ = 0;
  rtt_sum_ = 0;
  maximum_rtt_ = 0;
  return;
}

}  // namespace mmc
