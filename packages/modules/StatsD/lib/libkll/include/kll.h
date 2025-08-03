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

#include "aggregator.pb.h"
#include "compactor_stack.h"
#include "random_generator.h"

// KLL Quantile - Implementation of Approximate quantiles algorithm based on
// the KLL16 paper (up to section 3), see https://arxiv.org/abs/1603.05346.
//
// Simplified, single-type library for use on Android devices which is
// compatible with internal libraries. Cannot use Abseil; trimmed down feature
// set, doing leaf aggregation only; no multi-type support/templates, no dynamic
// types.
namespace dist_proc {
namespace aggregation {
class KllQuantileOptions;

class KllQuantile {
public:
    static std::unique_ptr<KllQuantile> Create(std::string* error = nullptr);
    static std::unique_ptr<KllQuantile> Create(const KllQuantileOptions& options,
                                               std::string* error = nullptr);
    int64_t num_values() const {
        return num_values_;
    }
    int64_t inv_eps() const {
        return inv_eps_;
    }
    int k() const {
        return compactor_stack_.k();
    }
    int64_t num_stored_values() const {
        return compactor_stack_.num_stored_items();
    }
    // Reset the aggregator to its state just after construction.
    void Reset();
    void Add(int64_t value);

    // Adds a value to the aggregator with multiplicity 'weight' (same as adding
    // the value with Add(value) 'weight' times). Does nothing if weight <= 0.
    //
    // If your weights exceed the max int32 size, we recommend to scale all
    // weights down to make them fit within that bound, and use (randomized)
    // rounding where needed.
    // Background: Even at high precision values (inv_eps ~100k), the compactor
    // stack will only accurately track weights in a range of ~31 consecutive
    // powers of 2, covering the largest weights encountered, while reservoir
    // sampling is used for weights below that range. So the precision loss by
    // downscaling and randomized rounding is negligible.
    void AddWeighted(int64_t value, int weight);

    // Not safe to be called concurrently.
    zetasketch::android::AggregatorStateProto SerializeToProto();

    bool IsSamplerOn() const {
        return compactor_stack_.IsSamplerOn();
    }

private:
    // Constructor.
    KllQuantile(int64_t inv_eps, int64_t inv_delta, int k, RandomGenerator* random)
        : inv_eps_(inv_eps),
          owned_random_(random != nullptr ? nullptr : std::make_unique<MTRandomGenerator>()),
          compactor_stack_(inv_eps_, inv_delta, k,
                           random != nullptr ? random : owned_random_.get()) {
        Reset();
    }
    void UpdateMin(const int64_t value);
    void UpdateMax(const int64_t value);
    int64_t inv_eps_;
    // The (exact) minimum item encountered among all items.
    int64_t min_{};
    // The (exact) maximum item encountered among all items.
    int64_t max_{};
    // Number of items added into the aggregator.
    int64_t num_values_;
    // Owned MTRandom instance, if not given a RandomGenerator.
    std::unique_ptr<MTRandomGenerator> owned_random_;
    // Stack of compactors to which newly added items are added;
    // it maintains a 'sketch' of hitherto added items.
    internal::CompactorStack compactor_stack_;

    KllQuantile(const KllQuantile&) = delete;
    KllQuantile& operator=(const KllQuantile&) = delete;
};

// Explicitly set KLL's epsilon and delta parameters that control
// approximation error and failure probability.
// *inv_eps is 1/epsilon, where epsilon is the approximation error parameter:
// When a user queries for a quantile phi, the rank of the returned
// approximate answer may be +/- (epsilon * n) off from the correct
// rank ceil(phi * n), where n is the number of aggregated items.
// *inv_delta is 1/delta, where delta is the failure probability parameter:
// with delta probability, at most one out of all possible quantile
// queries can be further off than the approximation guarantee.
class KllQuantileOptions {
public:
    // Set inv_eps. Default value: 1000
    void set_inv_eps(int64_t inv_eps) {
        inv_eps_ = inv_eps;
    }
    // Set inv_delta. Default value: 100000
    void set_inv_delta(int64_t inv_delta) {
        inv_delta_ = inv_delta;
    }
    // Set k, overriding the default calculation of this parameter from inv_eps
    // and inv_delta.
    void set_k(int k) {
        k_ = k;
    }
    // Set RandomGenerator pointer to use (caller retains ownership). Default is
    // to use an owned MTRandomGenerator instance.
    void set_random(RandomGenerator* random) {
        random_ = random;
    }
    int64_t inv_eps() const {
        return inv_eps_;
    }
    int64_t inv_delta() const {
        return inv_delta_;
    }
    int k() const {
        return k_;
    }
    RandomGenerator* random() const {
        return random_;
    }

private:
    int64_t inv_eps_ = 1000;
    int64_t inv_delta_ = 100000;
    int k_ = 0;
    RandomGenerator* random_ = nullptr;
};

}  // namespace aggregation
}  // namespace dist_proc
