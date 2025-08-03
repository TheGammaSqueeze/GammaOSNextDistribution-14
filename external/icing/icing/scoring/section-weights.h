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

#ifndef ICING_SCORING_SECTION_WEIGHTS_H_
#define ICING_SCORING_SECTION_WEIGHTS_H_

#include <unordered_map>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/scoring.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

inline constexpr double kDefaultSectionWeight = 1.0;

// Provides functions for setting and retrieving section weights for schema
// type properties. Section weights are used to promote and demote term matches
// in sections when scoring results. Section weights are provided by property
// path, and can range from (0, DBL_MAX]. The SectionId is matched to the
// property path by going over the schema type's section metadata. Weights that
// correspond to a valid property path are then normalized against the maxmium
// section weight, and put into map for quick access for scorers. By default,
// a section is given a raw, pre-normalized weight of 1.0.
class SectionWeights {
 public:
  // SectionWeights instances should not be copied.
  SectionWeights(const SectionWeights&) = delete;
  SectionWeights& operator=(const SectionWeights&) = delete;

  // Factory function to create a SectionWeights instance. Raw weights are
  // provided through the ScoringSpecProto. Provided property paths for weights
  // are validated against the schema type's section metadata. If the property
  // path doesn't exist, the property weight is ignored. If a weight is 0 or
  // negative, an invalid argument error is returned. Raw weights are then
  // normalized against the maximum weight for that schema type.
  //
  // Returns:
  //   A SectionWeights instance on success
  //   FAILED_PRECONDITION on any null pointer input
  //   INVALID_ARGUMENT if a provided weight for a property path is less than or
  // equal to 0.
  static libtextclassifier3::StatusOr<std::unique_ptr<SectionWeights>> Create(
      const SchemaStore* schema_store, const ScoringSpecProto& scoring_spec);

  // Returns the normalized section weight by SchemaTypeId and SectionId. If
  // the SchemaTypeId, or the SectionId for a SchemaTypeId, is not found in the
  // normalized weights map, the default weight is returned instead.
  double GetNormalizedSectionWeight(SchemaTypeId schema_type_id,
                                    SectionId section_id) const;

 private:
  // Holds the normalized section weights for a schema type, as well as the
  // normalized default weight for sections that have no weight set.
  struct NormalizedSectionWeights {
    std::unordered_map<SectionId, double> section_weights;
    double default_weight;
  };

  explicit SectionWeights(
      const std::unordered_map<SchemaTypeId, NormalizedSectionWeights>
          schema_section_weight_map)
      : schema_section_weight_map_(std::move(schema_section_weight_map)) {}

  // Creates a map of section ids to normalized weights from the raw property
  // path weight map and section metadata and calculates the normalized default
  // section weight.
  static inline SectionWeights::NormalizedSectionWeights
  ExtractNormalizedSectionWeights(
      const std::unordered_map<std::string, double>& raw_weights,
      const std::vector<SectionMetadata>& metadata_list);

  // A map of (SchemaTypeId -> SectionId -> Normalized Weight), allows for fast
  // look up of normalized weights. This is precomputed when creating a
  // SectionWeights instance.
  std::unordered_map<SchemaTypeId, NormalizedSectionWeights>
      schema_section_weight_map_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SECTION_WEIGHTS_H_
