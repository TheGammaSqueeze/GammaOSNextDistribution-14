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
#include "sampler.h"

#include <cmath>
#include <cstdint>
#include <vector>

#include "random_generator.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

void KllSampler::Reset() {
    sampled_item_ = 0;
    item_weight_ = 0;
    capacity_ = 2;
    num_replaced_levels_ = 1;
}

void KllSampler::Add(const int64_t item) {
    if (compactor_stack_->random()->UnbiasedUniform(++item_weight_) == 0) {
        sampled_item_ = item;
    }

    if (item_weight_ >= capacity_) {
        AddSampleToCompactorStackAndRestart();
    }
}

void KllSampler::AddWithWeight(int64_t item, int weight) {
    if (weight > 0) {
        if (item_weight_ + weight < capacity_) {
            item_weight_ += weight;
            if (compactor_stack_->random()->UnbiasedUniform(item_weight_) <
                static_cast<uint64_t>(weight)) {
                sampled_item_ = item;
            }
        } else {
            int64_t added_weight = capacity_ - item_weight_;
            if (compactor_stack_->random()->UnbiasedUniform(capacity_) <
                static_cast<uint64_t>(added_weight)) {
                sampled_item_ = item;
            }
            item_weight_ = capacity_;
            AddSampleToCompactorStackAndRestart();
            AddWithWeight(item, weight - added_weight);
        }
    }
}

void KllSampler::DoubleCapacity() {
    capacity_ *= 2;
    num_replaced_levels_++;
}

void KllSampler::AddSampleToCompactorStackAndRestart() {
    compactor_stack_->AddWithWeight(sampled_item_, item_weight_);
    item_weight_ = 0;
    sampled_item_ = 0;
}

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
