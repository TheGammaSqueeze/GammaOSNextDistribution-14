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

#pragma once

#include <ctime>
#include <vector>

#include <ditto/timespec_utils.h>

namespace dittosuite {

template <class T>
class Sampler {
 public:
  std::vector<T> GetSamples() const { return samples_; }

 protected:
  std::vector<T> samples_;
};

class TimeSampler : public Sampler<timespec> {
 public:
  void MeasureStart();
  void MeasureEnd();

 private:
  timespec start_;
  timespec end_;
};

class BandwidthSampler : public Sampler<double> {
 public:
  void Measure(size_t file_size, const timespec& duration);
};

}  // namespace dittosuite
