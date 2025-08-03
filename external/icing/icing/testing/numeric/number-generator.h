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

#ifndef ICING_TESTING_NUMERIC_NUMBER_GENERATOR_H_
#define ICING_TESTING_NUMERIC_NUMBER_GENERATOR_H_

#include <random>

namespace icing {
namespace lib {

template <typename T>
class NumberGenerator {
 public:
  virtual ~NumberGenerator() = default;

  virtual T Generate() = 0;

 protected:
  explicit NumberGenerator(int seed) : engine_(seed) {}

  std::default_random_engine engine_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_NUMERIC_NUMBER_GENERATOR_H_
