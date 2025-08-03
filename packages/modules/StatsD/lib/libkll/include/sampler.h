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

#include <assert.h>

#include <optional>

#include "compactor_stack.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

class CompactorStack;

// Class that does reservoir sampling to uniformly-at-random select one out of
// capacity() items that are added to it. The selected item is added to the
// compactor stack and sampling continues with the next capacity_ items.
//
// Serves as an replacement of num_replaced_levels levels of size 2 of the
// compactor stack, while only using constant memory.
class KllSampler {
public:
    KllSampler(CompactorStack* compactor_stack) : compactor_stack_(compactor_stack) {
        assert(compactor_stack != nullptr);
        Reset();
    }

    void Reset();

    // Adds an item to the sampler with weight one.
    void Add(int64_t item);

    // Adds an item to the sampler with weight >= 1. Does nothing if weight <= 0.
    void AddWithWeight(int64_t item, int weight);

    void DoubleCapacity();

    int64_t capacity() const {
        return capacity_;
    }

    std::optional<std::pair<int64_t, int>> sampled_item_and_weight() const {
        if (item_weight_ == 0) {
            return std::nullopt;
        }
        return std::make_pair(sampled_item_, item_weight_);
    }

    int num_replaced_levels() const {
        return num_replaced_levels_;
    }

private:
    void AddSampleToCompactorStackAndRestart();
    int64_t sampled_item_;
    int64_t item_weight_;
    int64_t capacity_;
    int num_replaced_levels_;
    CompactorStack* compactor_stack_;
};

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
