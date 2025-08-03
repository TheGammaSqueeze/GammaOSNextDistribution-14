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

#ifndef ICING_TESTING_NUMERIC_UNIFORM_DISTRIBUTION_INTEGER_GENERATOR_H_
#define ICING_TESTING_NUMERIC_UNIFORM_DISTRIBUTION_INTEGER_GENERATOR_H_

#include <random>

#include "icing/testing/numeric/number-generator.h"

namespace icing {
namespace lib {

template <typename T>
class UniformDistributionIntegerGenerator : public NumberGenerator<T> {
 public:
  explicit UniformDistributionIntegerGenerator(int seed, T range_lower,
                                               T range_upper)
      : NumberGenerator<T>(seed), distribution_(range_lower, range_upper) {}

  T Generate() override { return distribution_(this->engine_); }

 private:
  std::uniform_int_distribution<T> distribution_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_NUMERIC_UNIFORM_DISTRIBUTION_INTEGER_GENERATOR_H_
