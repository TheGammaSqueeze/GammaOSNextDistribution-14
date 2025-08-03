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
#include "compactor_stack.h"

#define LOG_TAG "libkll"

#include <log/log.h>

#include <vector>

#include "random_generator.h"
#include "sampler.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

CompactorStack::CompactorStack(int64_t inv_eps, int64_t inv_delta, RandomGenerator* random)
    : CompactorStack(inv_eps, inv_delta, 0, random) {
}

CompactorStack::CompactorStack(int64_t inv_eps, int64_t inv_delta, int k, RandomGenerator* random)
    : random_(random) {
    if (k != 0) {
        k_ = k;
    } else {
        // k = 1/eps * sqrt(log_2(1/delta)) - taken from proof of Thm 1.
        double raw_k = inv_eps * std::sqrt(std::log2(inv_delta));
        k_ = std::pow(2, std::lround(std::log2(raw_k)));
    }
    Reset();
}

CompactorStack::~CompactorStack() {
    ClearCompactors();
}

// Initialize or reset the compactor stack and all counters and thresholds.
void CompactorStack::Reset() {
    overall_capacity_ = 0;
    ClearCompactors();
    sampler_ = nullptr;
    AddLevel();
}

void CompactorStack::Add(const int64_t value) {
    if (sampler_ == nullptr) {
        compactors_[0].push_back(value);
        num_items_in_compactors_++;
        CompactStack();
    } else {
        sampler_->Add(value);
    }
}

// Adds an item to the compactor stack with weight >= 1.
// Does nothing if weight <= 0.
void CompactorStack::AddWithWeight(int64_t value, int weight) {
    if (weight > 0) {
        int remaining_weight = weight;
        size_t level_to_add = 0;
        if (sampler_ != nullptr) {
            sampler_->AddWithWeight(value, remaining_weight % sampler_->capacity());
            remaining_weight /= sampler_->capacity();
            level_to_add = sampler_->num_replaced_levels();
        }
        while (remaining_weight != 0) {
            if (level_to_add >= compactors_.size()) {
                AddLevel();
            }
            if ((remaining_weight & 1) != 0) {
                compactors_[level_to_add].push_back(value);
                num_items_in_compactors_++;
            }
            remaining_weight >>= 1;
            level_to_add++;
        }
        CompactStack();
    }
}

void CompactorStack::SortCompactorContents() {
    for (std::vector<int64_t>& compactor : compactors_) {
        std::sort(compactor.begin(), compactor.end());
    }
}

void CompactorStack::ClearCompactors() {
    compactors_.clear();
    num_items_in_compactors_ = 0;
}

void CompactorStack::AddLevel() {
    compactors_.resize(compactors_.size() + 1);

    int cap_at_lowest_active_level = TargetCapacityAtLevel(lowest_active_level());
    // All levels i get capacity that previously level i-1 had, except the
    // (previous) lowest active level, which gets a new smaller capacity.
    // Overall capacity changes by that amount.
    overall_capacity_ += cap_at_lowest_active_level;

    if (cap_at_lowest_active_level == 0) {
        DoubleSamplerCapacity();
    }
}

void CompactorStack::CompactStack() {
    int initial_num_items_in_compactors = num_items_in_compactors_;
    while (num_items_in_compactors_ >= overall_capacity_) {
        for (size_t i = 0; i < compactors_.size(); i++) {
            if (!compactors_[i].empty() &&
                static_cast<int>(compactors_[i].size()) >= TargetCapacityAtLevel(i)) {
                CompactLevel(i);
                if (num_items_in_compactors_ < overall_capacity_) {
                    break;
                }
            }
        }
        // TODO(b/237694338): Remove the temporary infinite loop detection code
        if (num_items_in_compactors_ >= initial_num_items_in_compactors) {
            // The loop above didn't do anything in terms of reducing the number of items.
            // To prevent an infinite loop, crash now.
            ALOGI("num_items_in_compactors_=%d, compactors_.size()=%zu, overall_capacity_=%d",
                  num_items_in_compactors_, compactors_.size(), overall_capacity_);
            for (size_t i = 0; i < compactors_.size(); i++) {
                const std::vector<int64_t>& compactor = compactors_[i];
                ALOGI("compactors_[%zu].size()=%zu, TargetCapacityAtLevel(i)=%d", i,
                      compactor.size(), TargetCapacityAtLevel(i));
            }
            LOG_ALWAYS_FATAL("Detected infinite loop in %s ", __func__);
        }
        initial_num_items_in_compactors = num_items_in_compactors_;
    }
}

void CompactorStack::CompactLevel(int level) {
    if (level == static_cast<int>(compactors_.size()) - 1) {
        AddLevel();
    }
    Halve(&compactors_[level], &compactors_[level + 1]);
    std::vector<int64_t>().swap(compactors_[level]);
}

// To compact the items in a compactor to roughly half the size,
// sorts the items and adds every even or odd item (determined randomly)
// to the up_compactor.
void CompactorStack::Halve(std::vector<int64_t>* down_compactor,
                           std::vector<int64_t>* up_compactor) {
    std::sort(down_compactor->begin(), down_compactor->end());
    double half_of_items = down_compactor->size() / static_cast<double>(2);
    bool keep_even_items = (random_->UnbiasedUniform(2) == 0);
    num_items_in_compactors_ -= static_cast<int>(keep_even_items ? std::floor(half_of_items)
                                                                 : std::ceil(half_of_items));

    bool even = true;

    for (size_t i = 0; i < down_compactor->size(); i++) {
        if (even == keep_even_items) {
            up_compactor->push_back((*down_compactor)[i]);
        }
        even = !even;
    }
    down_compactor->clear();
}

int CompactorStack::TargetCapacityAtLevel(int h) const {
    int num_stack_levels = compactors_.size();

    int raw_capacity = static_cast<int>(std::ceil(std::pow(c_, num_stack_levels - h - 1) * k_));

    // If the capacity is two or less, the level will be replaced by the
    // sampler.
    return raw_capacity > 2 ? raw_capacity : 0;
}

void CompactorStack::DoubleSamplerCapacity() {
    int prev_lowest_active_level = lowest_active_level();
    if (sampler_ != nullptr) {
        sampler_->DoubleCapacity();
    } else {
        sampler_ = std::make_unique<KllSampler>(this);
    }

    CompactLevel(prev_lowest_active_level);
}

int CompactorStack::num_stored_items() const {
    if (sampler_ == nullptr) {
        return num_items_in_compactors_;
    } else {
        return num_items_in_compactors_ +
               ((sampler_->sampled_item_and_weight().has_value()) ? 1 : 0);
    }
}

std::optional<std::pair<const int64_t, int64_t>> CompactorStack::sampled_item_and_weight() const {
    if (sampler_ != nullptr) {
        return sampler_->sampled_item_and_weight();
    } else {
        return std::nullopt;
    }
}

int64_t CompactorStack::sampler_capacity() const {
    return sampler_ ? sampler_->capacity() : 1;  // capacity = 1 to denote the empty sampler.
}

int CompactorStack::lowest_active_level() const {
    return sampler_ ? sampler_->num_replaced_levels() : 0;
}

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
