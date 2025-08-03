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

#include "icing/schema/schema-property-iterator.h"

#include <algorithm>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema/property-util.h"

namespace icing {
namespace lib {

libtextclassifier3::Status SchemaPropertyIterator::Advance() {
  while (!levels_.empty()) {
    if (!levels_.back().Advance()) {
      // When finishing iterating all properties of the current level, pop it
      // from the stack (levels_), return to the previous level and resume the
      // iteration.
      parent_type_config_names_.erase(
          parent_type_config_names_.find(levels_.back().GetSchemaTypeName()));
      levels_.pop_back();
      continue;
    }

    const PropertyConfigProto& curr_property_config =
        levels_.back().GetCurrentPropertyConfig();
    std::string curr_property_path = levels_.back().GetCurrentPropertyPath();

    // Iterate through the sorted_top_level_indexable_nested_properties_ in
    // order until we find the first element that is >= curr_property_path.
    while (current_top_level_indexable_nested_properties_idx_ <
               sorted_top_level_indexable_nested_properties_.size() &&
           sorted_top_level_indexable_nested_properties_.at(
               current_top_level_indexable_nested_properties_idx_) <
               curr_property_path) {
      // If an element in sorted_top_level_indexable_nested_properties_ < the
      // current property path, it means that we've already iterated past the
      // possible position for it without seeing it.
      // It's not a valid property path in our schema definition. Add it to
      // unknown_indexable_nested_properties_ and advance
      // current_top_level_indexable_nested_properties_idx_.
      unknown_indexable_nested_property_paths_.push_back(
          sorted_top_level_indexable_nested_properties_.at(
              current_top_level_indexable_nested_properties_idx_));
      ++current_top_level_indexable_nested_properties_idx_;
    }

    if (curr_property_config.data_type() !=
        PropertyConfigProto::DataType::DOCUMENT) {
      // We've advanced to a leaf property.
      // Set whether this property is indexable according to its level's
      // indexable config. If this property is declared in
      // indexable_nested_properties_list of the top-level schema, it is also
      // nested indexable.
      std::string* current_indexable_nested_prop =
          current_top_level_indexable_nested_properties_idx_ <
                  sorted_top_level_indexable_nested_properties_.size()
              ? &sorted_top_level_indexable_nested_properties_.at(
                    current_top_level_indexable_nested_properties_idx_)
              : nullptr;
      if (current_indexable_nested_prop == nullptr ||
          *current_indexable_nested_prop > curr_property_path) {
        // Current property is not in the indexable list. Set it as indexable if
        // its schema level is indexable AND it is an indexable property.
        bool is_property_indexable =
            levels_.back().GetLevelNestedIndexable() &&
            SchemaUtil::IsIndexedProperty(curr_property_config);
        levels_.back().SetCurrentPropertyIndexable(is_property_indexable);
      } else if (*current_indexable_nested_prop == curr_property_path) {
        // Current property is in the indexable list. Set its indexable config
        // to true. This property will consume a sectionId regardless of whether
        // or not it is actually indexable.
        levels_.back().SetCurrentPropertyIndexable(true);
        ++current_top_level_indexable_nested_properties_idx_;
      }
      return libtextclassifier3::Status::OK;
    }

    // - When advancing to a TYPE_DOCUMENT property, it means it is a nested
    //   schema and we need to traverse the next level. Look up SchemaTypeConfig
    //   (by the schema name) by type_config_map_, and push a new level into
    //   levels_.
    // - Each level has to record the index of property it is currently at, so
    //   we can resume the iteration when returning back to it. Also other
    //   essential info will be maintained in LevelInfo as well.
    auto nested_type_config_iter =
        type_config_map_.find(curr_property_config.schema_type());
    if (nested_type_config_iter == type_config_map_.end()) {
      // This should never happen because our schema should already be
      // validated by this point.
      return absl_ports::NotFoundError(absl_ports::StrCat(
          "Type config not found: ", curr_property_config.schema_type()));
    }
    const SchemaTypeConfigProto& nested_type_config =
        nested_type_config_iter->second;

    if (levels_.back().GetLevelNestedIndexable()) {
      // We should set sorted_top_level_indexable_nested_properties_ to the list
      // defined by the current level.
      // GetLevelNestedIndexable() is true either because:
      // 1. We're looking at a document property of the top-level schema --
      //    The first LevelInfo for the iterator is initialized with
      //    all_nested_properties_indexable_ = true.
      // 2. All previous levels set index_nested_properties = true:
      //    This indicates that upper-level schema types want to follow nested
      //    properties definition of its document subtypes. If this is the first
      //    subtype level that defines a list, we should set it as
      //    top_level_indexable_nested_properties_ for the current top-level
      //    schema.
      sorted_top_level_indexable_nested_properties_.clear();
      sorted_top_level_indexable_nested_properties_.reserve(
          curr_property_config.document_indexing_config()
              .indexable_nested_properties_list()
              .size());
      for (const std::string& property :
           curr_property_config.document_indexing_config()
               .indexable_nested_properties_list()) {
        // Concat the current property name to each property to get the full
        // property path expression for each indexable nested property.
        sorted_top_level_indexable_nested_properties_.push_back(
            property_util::ConcatenatePropertyPathExpr(curr_property_path,
                                                       property));
      }
      current_top_level_indexable_nested_properties_idx_ = 0;
      // Sort elements and dedupe
      std::sort(sorted_top_level_indexable_nested_properties_.begin(),
                sorted_top_level_indexable_nested_properties_.end());
      auto last =
          std::unique(sorted_top_level_indexable_nested_properties_.begin(),
                      sorted_top_level_indexable_nested_properties_.end());
      sorted_top_level_indexable_nested_properties_.erase(
          last, sorted_top_level_indexable_nested_properties_.end());
    }

    bool is_cycle =
        parent_type_config_names_.find(nested_type_config.schema_type()) !=
        parent_type_config_names_.end();
    bool is_parent_property_path =
        current_top_level_indexable_nested_properties_idx_ <
            sorted_top_level_indexable_nested_properties_.size() &&
        property_util::IsParentPropertyPath(
            curr_property_path,
            sorted_top_level_indexable_nested_properties_.at(
                current_top_level_indexable_nested_properties_idx_));
    if (is_cycle && !is_parent_property_path) {
      // Cycle detected. The schema definition is guaranteed to be valid here
      // since it must have already been validated during SchemaUtil::Validate,
      // which would have rejected any schema with bad cycles.
      //
      // There are no properties in the indexable_nested_properties_list that
      // are a part of this circular reference.
      // We do not need to iterate this type further so we simply move on to
      // other properties in the parent type.
      continue;
    }

    bool all_nested_properties_indexable =
        levels_.back().GetLevelNestedIndexable() &&
        curr_property_config.document_indexing_config()
            .index_nested_properties();
    levels_.push_back(LevelInfo(nested_type_config,
                                std::move(curr_property_path),
                                all_nested_properties_indexable));
    parent_type_config_names_.insert(nested_type_config.schema_type());
  }

  // Before returning, move all remaining uniterated properties from
  // sorted_top_level_indexable_nested_properties_ into
  // unknown_indexable_nested_properties_.
  std::move(sorted_top_level_indexable_nested_properties_.begin() +
                current_top_level_indexable_nested_properties_idx_,
            sorted_top_level_indexable_nested_properties_.end(),
            std::back_inserter(unknown_indexable_nested_property_paths_));

  return absl_ports::OutOfRangeError("End of iterator");
}

}  // namespace lib
}  // namespace icing
