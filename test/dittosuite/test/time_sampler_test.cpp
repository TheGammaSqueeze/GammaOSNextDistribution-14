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

#include <ditto/logger.h>
#include <ditto/sampler.h>
#include <gtest/gtest.h>

#include <unistd.h>

namespace dittosuite {

TEST(TimeSamplerTest, TimeSamplerVector) {
  dittosuite::TimeSampler time_sampler;
  int n = 5;
  for (int i = 0; i < n; i++) {
    time_sampler.MeasureStart();
    time_sampler.MeasureEnd();
  }
  ASSERT_EQ(time_sampler.GetSamples().size(), static_cast<unsigned int>(n));
}

}  // namespace dittosuite
