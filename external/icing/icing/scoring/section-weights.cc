// Copyright (C) 2021 Google LLC
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

#include "icing/scoring/section-weights.h"

#include <cfloat>
#include <unordered_map>
#include <utility>

#include "icing/proto/scoring.pb.h"
#include "icing/schema/section.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace {

// Normalizes all weights in the map to be in range [0.0, 1.0], where the max
// weight is normalized to 1.0. In the case that all weights are equal to 0.0,
// the normalized weight for each will be 0.0.
inline void NormalizeSectionWeights(
    double max_weight, std::unordered_map<SectionId, double>& section_weights) {
  if (max_weight == 0.0) {
    return;
  }
  for (auto& raw_weight : section_weights) {
    raw_weight.second = raw_weight.second / max_weight;
  }
}
}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<SectionWeights>>
SectionWeights::Create(const SchemaStore* schema_store,
                       const ScoringSpecProto& scoring_spec) {
  ICING_RETURN_ERROR_IF_NULL(schema_store);

  std::unordered_map<SchemaTypeId, NormalizedSectionWeights>
      schema_property_weight_map;
  for (const TypePropertyWeights& type_property_weights :
       scoring_spec.type_property_weights()) {
    std::string_view schema_type = type_property_weights.schema_type();
    auto schema_type_id_or = schema_store->GetSchemaTypeId(schema_type);
    if (!schema_type_id_or.ok()) {
      ICING_LOG(WARNING) << "No schema type id found for schema type: "
                         << schema_type;
      continue;
    }
    SchemaTypeId schema_type_id = schema_type_id_or.ValueOrDie();
    auto section_metadata_list_or =
        schema_store->GetSectionMetadata(schema_type.data());
    if (!section_metadata_list_or.ok()) {
      ICING_LOG(WARNING) << "No metadata found for schema type: "
                         << schema_type;
      continue;
    }

    const std::vector<SectionMetadata>* metadata_list =
        section_metadata_list_or.ValueOrDie();

    std::unordered_map<std::string, double> property_paths_weights;
    for (const PropertyWeight& property_weight :
         type_property_weights.property_weights()) {
      double property_path_weight = property_weight.weight();

      // Return error on negative weights.
      if (property_path_weight < 0.0) {
        return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
            "Property weight for property path \"%s\" is negative. Negative "
            "weights are invalid.",
            property_weight.path().c_str()));
      }
      property_paths_weights.insert(
          {property_weight.path(), property_path_weight});
    }
    NormalizedSectionWeights normalized_section_weights =
        ExtractNormalizedSectionWeights(property_paths_weights, *metadata_list);

    schema_property_weight_map.insert(
        {schema_type_id,
         {/*section_weights*/ std::move(
              normalized_section_weights.section_weights),
          /*default_weight*/ normalized_section_weights.default_weight}});
  }
  // Using `new` to access a non-public constructor.
  return std::unique_ptr<SectionWeights>(
      new SectionWeights(std::move(schema_property_weight_map)));
}

double SectionWeights::GetNormalizedSectionWeight(SchemaTypeId schema_type_id,
                                                  SectionId section_id) const {
  auto schema_type_map = schema_section_weight_map_.find(schema_type_id);
  if (schema_type_map == schema_section_weight_map_.end()) {
    // Return default weight if the schema type has no weights specified.
    return kDefaultSectionWeight;
  }

  auto section_weight =
      schema_type_map->second.section_weights.find(section_id);
  if (section_weight == schema_type_map->second.section_weights.end()) {
    // If there is no entry for SectionId, the weight is implicitly the
    // normalized default weight.
    return schema_type_map->second.default_weight;
  }
  return section_weight->second;
}

inline SectionWeights::NormalizedSectionWeights
SectionWeights::ExtractNormalizedSectionWeights(
    const std::unordered_map<std::string, double>& raw_weights,
    const std::vector<SectionMetadata>& metadata_list) {
  double max_weight = -std::numeric_limits<double>::infinity();
  std::unordered_map<SectionId, double> section_weights;
  for (const SectionMetadata& section_metadata : metadata_list) {
    std::string_view metadata_path = section_metadata.path;
    double section_weight = kDefaultSectionWeight;
    auto iter = raw_weights.find(metadata_path.data());
    if (iter != raw_weights.end()) {
      section_weight = iter->second;
      section_weights.insert({section_metadata.id, section_weight});
    }
    // Replace max if we see new max weight.
    max_weight = std::max(max_weight, section_weight);
  }

  NormalizeSectionWeights(max_weight, section_weights);
  // Set normalized default weight to 1.0 in case there is no section
  // metadata and max_weight is -INF (we should not see this case).
  double normalized_default_weight =
      max_weight == -std::numeric_limits<double>::infinity()
          ? kDefaultSectionWeight
          : kDefaultSectionWeight / max_weight;
  SectionWeights::NormalizedSectionWeights normalized_section_weights =
      SectionWeights::NormalizedSectionWeights();
  normalized_section_weights.section_weights = std::move(section_weights);
  normalized_section_weights.default_weight = normalized_default_weight;
  return normalized_section_weights;
}
}  // namespace lib
}  // namespace icing
