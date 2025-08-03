// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <ditto/sampler.h>

#include <cmath>
#include <ctime>

#include <ditto/logger.h>

namespace dittosuite {

void TimeSampler::MeasureStart() {
  clock_gettime(CLOCK_MONOTONIC, &start_);
}

void TimeSampler::MeasureEnd() {
  clock_gettime(CLOCK_MONOTONIC, &end_);
  samples_.push_back(end_ - start_);
}

void BandwidthSampler::Measure(const size_t file_size, const timespec& duration) {
  double bandwidth = file_size * 1e6 / TimespecToNanos(duration);  // currently bytes/milliseconds
  bandwidth = bandwidth * 1e3 / (1 << 10);                      // Kb / s
  samples_.push_back(bandwidth);
}

}  // namespace dittosuite
