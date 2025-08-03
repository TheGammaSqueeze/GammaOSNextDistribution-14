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

#include "kll.h"

#include <cstdint>
#include <memory>

#include "aggregator.pb.h"
#include "compactor_stack.h"
#include "encoding/encoder.h"
#include "kll-quantiles.pb.h"

namespace dist_proc {
namespace aggregation {

using zetasketch::android::AggregatorStateProto;

std::unique_ptr<KllQuantile> KllQuantile::Create(std::string* error) {
    return Create(KllQuantileOptions(), error);
}

std::unique_ptr<KllQuantile> KllQuantile::Create(const KllQuantileOptions& options,
                                                 std::string* error) {
    if (options.k() < 0) {
        if (error != nullptr) {
            *error = "k has to be >= 0";
        }
        return nullptr;
    }
    return std::unique_ptr<KllQuantile>(
            new KllQuantile(options.inv_eps(), options.inv_delta(), options.k(), options.random()));
}

void KllQuantile::Add(const int64_t value) {
    compactor_stack_.Add(value);
    UpdateMin(value);
    UpdateMax(value);
    num_values_++;
}

void KllQuantile::AddWeighted(int64_t value, int weight) {
    if (weight > 0) {
        compactor_stack_.AddWithWeight(value, weight);
        UpdateMin(value);
        UpdateMax(value);
        num_values_ += weight;
    }
}

AggregatorStateProto KllQuantile::SerializeToProto() {
    AggregatorStateProto aggregator_state;

    aggregator_state.set_type(zetasketch::android::KLL_QUANTILES);
    aggregator_state.set_num_values(num_values_);
    aggregator_state.set_value_type(zetasketch::android::DefaultOpsType::INT64);

    zetasketch::android::KllQuantilesStateProto* quantile_state =
            aggregator_state.MutableExtension(zetasketch::android::kll_quantiles_state);

    quantile_state->set_k(compactor_stack_.k());
    quantile_state->set_inv_eps(inv_eps_);

    if (num_values_ == 0) {
        return aggregator_state;
    }

    // Encode min/max.
    encoding::Encoder::AppendToString(min_, quantile_state->mutable_min());
    encoding::Encoder::AppendToString(max_, quantile_state->mutable_max());

    // Sort compactors before encoding them, to only do sorting work once (vs.
    // every time a sketch is read and extracted or merged), and to reduce sketch
    // cardinality, which saves space in e.g. column store dictionaries.
    compactor_stack_.SortCompactorContents();

    // Encode compactors.
    const std::vector<std::vector<int64_t>>& compactors = compactor_stack_.compactors();
    quantile_state->mutable_compactors()->Reserve(compactors.size());

    for (const auto& compactor : compactors) {
        encoding::Encoder::SerializeToPackedStringAll(
                compactor.begin(), compactor.end(),
                quantile_state
                        ->add_compactors()  // Adds one compactor to the compactors field.
                        ->mutable_packed_values());
    }

    // Encode sampler.
    if (compactor_stack_.IsSamplerOn()) {
        const auto& sampled_item_and_weight = compactor_stack_.sampled_item_and_weight();
        if (sampled_item_and_weight.has_value()) {
            encoding::Encoder::AppendToString(
                    sampled_item_and_weight->first,
                    quantile_state->mutable_sampler()->mutable_sampled_item());
            quantile_state->mutable_sampler()->set_sampled_weight(sampled_item_and_weight->second);
        }
        quantile_state->mutable_sampler()->set_log_capacity(compactor_stack_.lowest_active_level());
    }

    return aggregator_state;
}

void KllQuantile::UpdateMin(int64_t value) {
    if (num_values_ == 0 || min_ > value) {
        min_ = value;
    }
}

void KllQuantile::UpdateMax(int64_t value) {
    if (num_values_ == 0 || max_ < value) {
        max_ = value;
    }
}

void KllQuantile::Reset() {
    num_values_ = 0;
    compactor_stack_.Reset();
}

}  // namespace aggregation
}  // namespace dist_proc
