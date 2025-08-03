// Copyright (C) 2023 Google LLC
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

#ifndef ICING_TESTING_NUMERIC_NORMAL_DISTRIBUTION_NUMBER_GENERATOR_H_
#define ICING_TESTING_NUMERIC_NORMAL_DISTRIBUTION_NUMBER_GENERATOR_H_

#include <cmath>
#include <random>

#include "icing/testing/numeric/number-generator.h"

namespace icing {
namespace lib {

template <typename T>
class NormalDistributionNumberGenerator : public NumberGenerator<T> {
 public:
  explicit NormalDistributionNumberGenerator(int seed, double mean,
                                             double stddev)
      : NumberGenerator<T>(seed), distribution_(mean, stddev) {}

  T Generate() override { return std::round(distribution_(this->engine_)); }

 private:
  std::normal_distribution<> distribution_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_NUMERIC_NORMAL_DISTRIBUTION_NUMBER_GENERATOR_H_
