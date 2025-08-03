/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <cstdint>
#include <optional>
#include <random>

namespace dist_proc {
namespace aggregation {

class RandomGenerator {
public:
    virtual ~RandomGenerator(){};

    // Returns a uniformly distributed pseudorandom integer in [0, n)
    // where n >= 0.
    virtual uint64_t UnbiasedUniform(uint64_t n) = 0;
};

class MTRandomGenerator : public RandomGenerator {
public:
    MTRandomGenerator(std::optional<uint64_t> seed = std::nullopt) {
        if (seed.has_value()) {
            bit_gen_ = std::mt19937(seed.value());
        } else {
            std::random_device rd;
            bit_gen_ = std::mt19937(rd());
        }
    }
    uint64_t UnbiasedUniform(uint64_t n) override {
        std::uniform_int_distribution<uint64_t> distrib(0, n - 1);
        return distrib(bit_gen_);
    }

private:
    std::mt19937 bit_gen_;
};

}  // namespace aggregation
}  // namespace dist_proc
