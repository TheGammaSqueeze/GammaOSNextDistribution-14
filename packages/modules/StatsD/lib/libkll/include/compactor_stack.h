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

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <utility>
#include <vector>

#include "random_generator.h"
#include "sampler.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

class KllSampler;

// Hierarchy of compactors, which store items from the stream and 'compact'
// them when necessary (i.e., keep every second item in a sorted compactor)
// and add them to the compactor one level up.
class CompactorStack {
public:
    CompactorStack(int64_t inv_eps, int64_t inv_delta, RandomGenerator* random);
    CompactorStack(int64_t inv_eps, int64_t inv_delta, int k, RandomGenerator* random);
    ~CompactorStack();

    // Initialize or reset the compactor stack and all counters and thresholds.
    void Reset();

    void Add(const int64_t value);

    // Adds an item to the compactor stack with weight >= 1.
    // Does nothing if weight <= 0.
    void AddWithWeight(int64_t value, int weight);

    // Ensures that the contents of each compactor are sorted.
    void SortCompactorContents();

    // Target capacity of compactor with index h. If this capacity is exceeded,
    // the compactor will be lazily compacted in one of the next CompactStack()
    // runs. I.e., this capacity can be temorarily exceeded.
    int TargetCapacityAtLevel(int h) const;

    void DoubleSamplerCapacity();

    int num_stored_items() const;

    std::optional<std::pair<const int64_t, int64_t>> sampled_item_and_weight() const;

    // Returns the lowest active level in the compactor stack, which is identical
    // with the number of replaced levels, or log2(sampler_capacity()).
    int lowest_active_level() const;

    int64_t sampler_capacity() const;

    // For testing
    bool IsSamplerOn() const {
        return sampler_ != nullptr;
    }

    const std::vector<std::vector<int64_t>>& compactors() const {
        return compactors_;
    }

    RandomGenerator* random() {
        return random_;
    }

    int k() const {
        return k_;
    }

private:
    void ClearCompactors();

    // Adds a new compactor at the highest level. To be called when the currently
    // topmost compactor is full.
    void AddLevel();

    // Called when at least one level in the compactor stack is above capacity.
    // Iterates from bottom to top through the compactors and compacts the
    // first one that is over its capacity by halving its contents and adding
    // them to the compactor one level higher.
    void CompactStack();

    void CompactLevel(int level);

    // To compact the items in a compactor to roughly half the size,
    // sorts the items and adds every even or odd item (determined randomly)
    // to the up_compactor.
    void Halve(std::vector<int64_t>* down_compactor, std::vector<int64_t>* up_compactor);

    std::vector<std::vector<int64_t>> compactors_;
    int k_;
    const double c_ = 2.0 / 3.0;
    int overall_capacity_;
    int num_items_in_compactors_;
    RandomGenerator* random_;
    std::unique_ptr<KllSampler> sampler_;
};

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
