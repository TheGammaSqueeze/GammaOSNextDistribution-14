// Copyright (C) 2019 Google LLC
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

#include "icing/schema/schema-util.h"

#include <algorithm>
#include <cstdint>
#include <queue>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/annotate.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

bool ArePropertiesEqual(const PropertyConfigProto& old_property,
                        const PropertyConfigProto& new_property) {
  return old_property.property_name() == new_property.property_name() &&
         old_property.data_type() == new_property.data_type() &&
         old_property.schema_type() == new_property.schema_type() &&
         old_property.cardinality() == new_property.cardinality() &&
         old_property.string_indexing_config().term_match_type() ==
             new_property.string_indexing_config().term_match_type() &&
         old_property.string_indexing_config().tokenizer_type() ==
             new_property.string_indexing_config().tokenizer_type() &&
         old_property.document_indexing_config().index_nested_properties() ==
             new_property.document_indexing_config().index_nested_properties();
}

bool IsCardinalityCompatible(const PropertyConfigProto& old_property,
                             const PropertyConfigProto& new_property) {
  if (old_property.cardinality() < new_property.cardinality()) {
    // We allow a new, less restrictive cardinality (i.e. a REQUIRED field
    // can become REPEATED or OPTIONAL, but not the other way around).
    ICING_VLOG(1) << absl_ports::StrCat(
        "Cardinality is more restrictive than before ",
        PropertyConfigProto::Cardinality::Code_Name(old_property.cardinality()),
        "->",
        PropertyConfigProto::Cardinality::Code_Name(
            new_property.cardinality()));
    return false;
  }
  return true;
}

bool IsDataTypeCompatible(const PropertyConfigProto& old_property,
                          const PropertyConfigProto& new_property) {
  if (old_property.data_type() != new_property.data_type()) {
    // TODO(cassiewang): Maybe we can be a bit looser with this, e.g. we just
    // string cast an int64_t to a string. But for now, we'll stick with
    // simplistics.
    ICING_VLOG(1) << absl_ports::StrCat(
        "Data type ",
        PropertyConfigProto::DataType::Code_Name(old_property.data_type()),
        "->",
        PropertyConfigProto::DataType::Code_Name(new_property.data_type()));
    return false;
  }
  return true;
}

bool IsSchemaTypeCompatible(const PropertyConfigProto& old_property,
                            const PropertyConfigProto& new_property) {
  if (old_property.schema_type() != new_property.schema_type()) {
    ICING_VLOG(1) << absl_ports::StrCat("Schema type ",
                                        old_property.schema_type(), "->",
                                        new_property.schema_type());
    return false;
  }
  return true;
}

bool IsPropertyCompatible(const PropertyConfigProto& old_property,
                          const PropertyConfigProto& new_property) {
  return IsDataTypeCompatible(old_property, new_property) &&
         IsSchemaTypeCompatible(old_property, new_property) &&
         IsCardinalityCompatible(old_property, new_property);
}

bool IsTermMatchTypeCompatible(const StringIndexingConfig& old_indexed,
                               const StringIndexingConfig& new_indexed) {
  return old_indexed.term_match_type() == new_indexed.term_match_type() &&
         old_indexed.tokenizer_type() == new_indexed.tokenizer_type();
}

bool IsIntegerNumericMatchTypeCompatible(
    const IntegerIndexingConfig& old_indexed,
    const IntegerIndexingConfig& new_indexed) {
  return old_indexed.numeric_match_type() == new_indexed.numeric_match_type();
}

bool IsDocumentIndexingCompatible(const DocumentIndexingConfig& old_indexed,
                                  const DocumentIndexingConfig& new_indexed) {
  // TODO(b/265304217): This could mark the new schema as incompatible and
  // generate some unnecessary index rebuilds if the two schemas have an
  // equivalent set of indexed properties, but changed the way that it is
  // declared.
  if (old_indexed.index_nested_properties() !=
      new_indexed.index_nested_properties()) {
    return false;
  }

  if (old_indexed.indexable_nested_properties_list().size() !=
      new_indexed.indexable_nested_properties_list().size()) {
    return false;
  }

  std::unordered_set<std::string_view> old_indexable_nested_properies_set(
      old_indexed.indexable_nested_properties_list().begin(),
      old_indexed.indexable_nested_properties_list().end());
  for (const auto& property : new_indexed.indexable_nested_properties_list()) {
    if (old_indexable_nested_properies_set.find(property) ==
        old_indexable_nested_properies_set.end()) {
      return false;
    }
  }
  return true;
}

void AddIncompatibleChangeToDelta(
    std::unordered_set<std::string>& incompatible_delta,
    const SchemaTypeConfigProto& old_type_config,
    const SchemaUtil::DependentMap& new_schema_dependent_map,
    const SchemaUtil::TypeConfigMap& old_type_config_map,
    const SchemaUtil::TypeConfigMap& new_type_config_map) {
  // If this type is incompatible, then every type that depends on it might
  // also be incompatible. Use the dependent map to mark those ones as
  // incompatible too.
  incompatible_delta.insert(old_type_config.schema_type());
  auto dependent_types_itr =
      new_schema_dependent_map.find(old_type_config.schema_type());
  if (dependent_types_itr != new_schema_dependent_map.end()) {
    for (const auto& [dependent_type, _] : dependent_types_itr->second) {
      // The types from new_schema that depend on the current
      // old_type_config may not present in old_schema.
      // Those types will be listed at schema_delta.schema_types_new
      // instead.
      std::string dependent_type_str(dependent_type);
      if (old_type_config_map.find(dependent_type_str) !=
          old_type_config_map.end()) {
        incompatible_delta.insert(std::move(dependent_type_str));
      }
    }
  }
}

// Returns if C1 <= C2 based on the following rule, where C1 and C2 are
// cardinalities that can be one of REPEATED, OPTIONAL, or REQUIRED.
//
// Rule: REQUIRED < OPTIONAL < REPEATED
bool CardinalityLessThanEq(PropertyConfigProto::Cardinality::Code C1,
                           PropertyConfigProto::Cardinality::Code C2) {
  if (C1 == C2) {
    return true;
  }
  if (C1 == PropertyConfigProto::Cardinality::REQUIRED) {
    return C2 == PropertyConfigProto::Cardinality::OPTIONAL ||
           C2 == PropertyConfigProto::Cardinality::REPEATED;
  }
  if (C1 == PropertyConfigProto::Cardinality::OPTIONAL) {
    return C2 == PropertyConfigProto::Cardinality::REPEATED;
  }
  return false;
}

// Check if set1 is a subset of set2.
template <typename T>
bool IsSubset(const std::unordered_set<T>& set1,
              const std::unordered_set<T>& set2) {
  for (const auto& item : set1) {
    if (set2.find(item) == set2.end()) {
      return false;
    }
  }
  return true;
}

}  // namespace

libtextclassifier3::Status CalculateTransitiveNestedTypeRelations(
    const SchemaUtil::DependentMap& direct_nested_types_map,
    const std::unordered_set<std::string_view>& joinable_types,
    std::string_view type, bool path_contains_joinable_property,
    SchemaUtil::DependentMap* expanded_nested_types_map,
    std::unordered_map<std::string_view, bool>&&
        pending_expansion_paths_indexable,
    std::unordered_set<std::string_view>* sink_types) {
  // TODO(b/280698121): Implement optimizations to this code to avoid reentering
  // a node after it's already been expanded.

  auto itr = direct_nested_types_map.find(type);
  if (itr == direct_nested_types_map.end()) {
    // It's a sink node. Just return.
    sink_types->insert(type);
    return libtextclassifier3::Status::OK;
  }
  std::unordered_map<std::string_view, std::vector<const PropertyConfigProto*>>
      expanded_relations;

  // Add all of the adjacent outgoing relations.
  expanded_relations.reserve(itr->second.size());
  expanded_relations.insert(itr->second.begin(), itr->second.end());

  // Iterate through each adjacent outgoing relation and add their indirect
  // outgoing relations.
  for (const auto& [adjacent_type, adjacent_property_protos] : itr->second) {
    // Make a copy of pending_expansion_paths_indexable for every iteration.
    std::unordered_map<std::string_view, bool> pending_expansion_paths_copy(
        pending_expansion_paths_indexable);

    // 1. Check the nested indexable config of the edge (type -> adjacent_type),
    //    and the joinable config of the current path up to adjacent_type.
    //
    // The nested indexable config is true if any of the PropertyConfigProtos
    // representing the connecting edge has index_nested_properties=true.
    bool is_edge_nested_indexable = std::any_of(
        adjacent_property_protos.begin(), adjacent_property_protos.end(),
        [](const PropertyConfigProto* property_config) {
          return property_config->document_indexing_config()
              .index_nested_properties();
        });
    // TODO(b/265304217): change this once we add joinable_properties_list.
    // Check if addition of the new edge (type->adjacent_type) makes the path
    // joinable.
    bool new_path_contains_joinable_property =
        joinable_types.count(type) > 0 || path_contains_joinable_property;
    // Set is_nested_indexable field for the current edge
    pending_expansion_paths_copy[type] = is_edge_nested_indexable;

    // If is_edge_nested_indexable=false, then all paths to adjacent_type
    // currently in the pending_expansions map are also not nested indexable.
    if (!is_edge_nested_indexable) {
      for (auto& pending_expansion : pending_expansion_paths_copy) {
        pending_expansion.second = false;
      }
    }

    // 2. Check if we're in the middle of expanding this type - IOW
    // there's a cycle!
    //
    // This cycle is not allowed if either:
    //  1. The cycle starting at adjacent_type is nested indexable, OR
    //  2. The current path contains a joinable property.
    auto adjacent_itr = pending_expansion_paths_copy.find(adjacent_type);
    if (adjacent_itr != pending_expansion_paths_copy.end()) {
      if (adjacent_itr->second || new_path_contains_joinable_property) {
        return absl_ports::InvalidArgumentError(absl_ports::StrCat(
            "Invalid cycle detected in type configs. '", type,
            "' references itself and is nested-indexable or nested-joinable."));
      }
      // The cycle is allowed and there's no need to keep iterating the loop.
      // Move on to the next adjacent value.
      continue;
    }

    // 3. Expand this type as needed.
    ICING_RETURN_IF_ERROR(CalculateTransitiveNestedTypeRelations(
        direct_nested_types_map, joinable_types, adjacent_type,
        new_path_contains_joinable_property, expanded_nested_types_map,
        std::move(pending_expansion_paths_copy), sink_types));
    if (sink_types->count(adjacent_type) > 0) {
      // "adjacent" is a sink node. Just skip to the next.
      continue;
    }

    // 4. "adjacent" has been fully expanded. Add all of its transitive
    // outgoing relations to this type's transitive outgoing relations.
    auto adjacent_expanded_itr = expanded_nested_types_map->find(adjacent_type);
    for (const auto& [transitive_reachable, _] :
         adjacent_expanded_itr->second) {
      // Insert a transitive reachable node `transitive_reachable` for `type` if
      // it wasn't previously reachable.
      // Since there is no direct edge between `type` and `transitive_reachable`
      // we insert an empty vector into the dependent map.
      expanded_relations.insert({transitive_reachable, {}});
    }
  }
  for (const auto& kvp : expanded_relations) {
    expanded_nested_types_map->operator[](type).insert(kvp);
  }
  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::Status CalculateAcyclicTransitiveRelations(
    const SchemaUtil::TypeRelationMap<T>& direct_relation_map,
    std::string_view type,
    SchemaUtil::TypeRelationMap<T>* expanded_relation_map,
    std::unordered_set<std::string_view>* pending_expansions,
    std::unordered_set<std::string_view>* sink_types) {
  auto expanded_itr = expanded_relation_map->find(type);
  if (expanded_itr != expanded_relation_map->end()) {
    // We've already expanded this type. Just return.
    return libtextclassifier3::Status::OK;
  }
  auto itr = direct_relation_map.find(type);
  if (itr == direct_relation_map.end()) {
    // It's a sink node. Just return.
    sink_types->insert(type);
    return libtextclassifier3::Status::OK;
  }
  pending_expansions->insert(type);
  std::unordered_map<std::string_view, T> expanded_relations;

  // Add all of the adjacent outgoing relations.
  expanded_relations.reserve(itr->second.size());
  expanded_relations.insert(itr->second.begin(), itr->second.end());

  // Iterate through each adjacent outgoing relation and add their indirect
  // outgoing relations.
  for (const auto& [adjacent, _] : itr->second) {
    // 1. Check if we're in the middle of expanding this type - IOW there's a
    // cycle!
    if (pending_expansions->count(adjacent) > 0) {
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Invalid cycle detected in type configs. '", type,
                             "' references or inherits from itself."));
    }

    // 2. Expand this type as needed.
    ICING_RETURN_IF_ERROR(CalculateAcyclicTransitiveRelations(
        direct_relation_map, adjacent, expanded_relation_map,
        pending_expansions, sink_types));
    if (sink_types->count(adjacent) > 0) {
      // "adjacent" is a sink node. Just skip to the next.
      continue;
    }

    // 3. "adjacent" has been fully expanded. Add all of its transitive outgoing
    // relations to this type's transitive outgoing relations.
    auto adjacent_expanded_itr = expanded_relation_map->find(adjacent);
    for (const auto& [transitive_reachable, _] :
         adjacent_expanded_itr->second) {
      // Insert a transitive reachable node `transitive_reachable` for `type`.
      // Also since there is no direct edge between `type` and
      // `transitive_reachable`, the direct edge is initialized by default.
      expanded_relations.insert({transitive_reachable, T()});
    }
  }
  expanded_relation_map->insert({type, std::move(expanded_relations)});
  pending_expansions->erase(type);
  return libtextclassifier3::Status::OK;
}

// Calculate and return the expanded nested-type map from
// direct_nested_type_map. This expands the direct_nested_type_map to also
// include indirect nested-type relations.
//
// Ex. Suppose we have the following relations in direct_nested_type_map.
//
// C -> B (Schema type B has a document property of type C)
// B -> A (Schema type A has a document property of type B)
//
// Then, this function would expand the map by adding C -> A to the map.
libtextclassifier3::StatusOr<SchemaUtil::DependentMap>
CalculateTransitiveNestedTypeRelations(
    const SchemaUtil::DependentMap& direct_nested_type_map,
    const std::unordered_set<std::string_view>& joinable_types,
    bool allow_circular_schema_definitions) {
  SchemaUtil::DependentMap expanded_nested_type_map;
  // Types that have no outgoing relations.
  std::unordered_set<std::string_view> sink_types;

  if (allow_circular_schema_definitions) {
    // Map of nodes that are pending expansion -> whether the path from each key
    // node to the 'current' node is nested_indexable.
    // A copy of this map is made for each new node that we expand.
    std::unordered_map<std::string_view, bool>
        pending_expansion_paths_indexable;
    for (const auto& kvp : direct_nested_type_map) {
      ICING_RETURN_IF_ERROR(CalculateTransitiveNestedTypeRelations(
          direct_nested_type_map, joinable_types, kvp.first,
          /*path_contains_joinable_property=*/false, &expanded_nested_type_map,
          std::unordered_map<std::string_view, bool>(
              pending_expansion_paths_indexable),
          &sink_types));
    }
  } else {
    // If allow_circular_schema_definitions is false, then fallback to the old
    // way of detecting cycles.
    // Types that we are expanding.
    std::unordered_set<std::string_view> pending_expansions;
    for (const auto& kvp : direct_nested_type_map) {
      ICING_RETURN_IF_ERROR(CalculateAcyclicTransitiveRelations(
          direct_nested_type_map, kvp.first, &expanded_nested_type_map,
          &pending_expansions, &sink_types));
    }
  }
  return expanded_nested_type_map;
}

// Calculate and return the expanded inheritance map from
// direct_nested_type_map. This expands the direct_inheritance_map to also
// include indirect inheritance relations.
//
// Ex. Suppose we have the following relations in direct_inheritance_map.
//
// C -> B (Schema type C is B's parent_type )
// B -> A (Schema type B is A's parent_type)
//
// Then, this function would expand the map by adding C -> A to the map.
libtextclassifier3::StatusOr<SchemaUtil::InheritanceMap>
CalculateTransitiveInheritanceRelations(
    const SchemaUtil::InheritanceMap& direct_inheritance_map) {
  SchemaUtil::InheritanceMap expanded_inheritance_map;

  // Types that we are expanding.
  std::unordered_set<std::string_view> pending_expansions;

  // Types that have no outgoing relation.
  std::unordered_set<std::string_view> sink_types;
  for (const auto& kvp : direct_inheritance_map) {
    ICING_RETURN_IF_ERROR(CalculateAcyclicTransitiveRelations(
        direct_inheritance_map, kvp.first, &expanded_inheritance_map,
        &pending_expansions, &sink_types));
  }
  return expanded_inheritance_map;
}

// Builds a transitive dependent map. Types with no dependents will not be
// present in the map as keys.
//
// Ex. Suppose we have a schema with four types A, B, C, D. A has a property of
// type B and B has a property of type C. C and D only have non-document
// properties.
//
// The transitive dependent map for this schema would be:
// C -> A, B (both A and B depend on C)
// B -> A (A depends on B)
//
// A and D will not be present in the map as keys because no type depends on
// them.
//
// RETURNS:
//   On success, a transitive dependent map of all types in the schema.
//   INVALID_ARGUMENT if the schema contains a cycle or an undefined type.
//   ALREADY_EXISTS if a schema type is specified more than once in the schema
libtextclassifier3::StatusOr<SchemaUtil::DependentMap>
BuildTransitiveDependentGraph(const SchemaProto& schema,
                              bool allow_circular_schema_definitions) {
  // We expand the nested-type dependent map and inheritance map differently
  // when calculating transitive relations. These two types of relations also
  // should not be transitive so we keep these as separate maps.
  //
  // e.g. For schema type A, B and C, B depends on A through inheritance, and
  // C depends on B by having a property with type B, we will have the two
  // relations {A, B} and {B, C} in the dependent map, but will not have {A, C}
  // in the map.
  SchemaUtil::DependentMap direct_nested_type_map;
  SchemaUtil::InheritanceMap direct_inheritance_map;

  // Set of schema types that have at least one joinable property.
  std::unordered_set<std::string_view> joinable_types;

  // Add all first-order dependents.
  std::unordered_set<std::string_view> known_types;
  std::unordered_set<std::string_view> unknown_types;
  for (const auto& type_config : schema.types()) {
    std::string_view schema_type(type_config.schema_type());
    if (known_types.count(schema_type) > 0) {
      return absl_ports::AlreadyExistsError(absl_ports::StrCat(
          "Field 'schema_type' '", schema_type, "' is already defined"));
    }
    known_types.insert(schema_type);
    unknown_types.erase(schema_type);
    // Insert inheritance relations into the inheritance map.
    for (std::string_view parent_schema_type : type_config.parent_types()) {
      if (known_types.count(parent_schema_type) == 0) {
        unknown_types.insert(parent_schema_type);
      }
      direct_inheritance_map[parent_schema_type][schema_type] = true;
    }
    for (const auto& property_config : type_config.properties()) {
      if (property_config.joinable_config().value_type() !=
          JoinableConfig::ValueType::NONE) {
        joinable_types.insert(schema_type);
      }
      // Insert nested-type relations into the nested-type map.
      if (property_config.data_type() ==
          PropertyConfigProto::DataType::DOCUMENT) {
        // Need to know what schema_type these Document properties should be
        // validated against
        std::string_view property_schema_type(property_config.schema_type());
        if (known_types.count(property_schema_type) == 0) {
          unknown_types.insert(property_schema_type);
        }
        direct_nested_type_map[property_schema_type][schema_type].push_back(
            &property_config);
      }
    }
  }
  if (!unknown_types.empty()) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Undefined 'schema_type's: ", absl_ports::StrJoin(unknown_types, ",")));
  }

  // Merge two expanded maps into a single dependent_map, without making
  // inheritance and nested-type relations transitive.
  ICING_ASSIGN_OR_RETURN(SchemaUtil::DependentMap merged_dependent_map,
                         CalculateTransitiveNestedTypeRelations(
                             direct_nested_type_map, joinable_types,
                             allow_circular_schema_definitions));
  ICING_ASSIGN_OR_RETURN(
      SchemaUtil::InheritanceMap expanded_inheritance_map,
      CalculateTransitiveInheritanceRelations(direct_inheritance_map));
  for (const auto& [parent_type, inheritance_relation] :
       expanded_inheritance_map) {
    // Insert the parent_type into the dependent map if it is not present
    // already.
    merged_dependent_map.insert({parent_type, {}});
    for (const auto& [child_type, _] : inheritance_relation) {
      // Insert the child_type into parent_type's dependent map if it's not
      // present already, in which case the value will be an empty vector.
      merged_dependent_map[parent_type].insert({child_type, {}});
    }
  }
  return merged_dependent_map;
}

libtextclassifier3::StatusOr<SchemaUtil::InheritanceMap>
SchemaUtil::BuildTransitiveInheritanceGraph(const SchemaProto& schema) {
  SchemaUtil::InheritanceMap direct_inheritance_map;
  for (const auto& type_config : schema.types()) {
    for (std::string_view parent_schema_type : type_config.parent_types()) {
      direct_inheritance_map[parent_schema_type][type_config.schema_type()] =
          true;
    }
  }
  return CalculateTransitiveInheritanceRelations(direct_inheritance_map);
}

libtextclassifier3::StatusOr<SchemaUtil::DependentMap> SchemaUtil::Validate(
    const SchemaProto& schema, bool allow_circular_schema_definitions) {
  // 1. Build the dependent map. This will detect any cycles, non-existent or
  // duplicate types in the schema.
  ICING_ASSIGN_OR_RETURN(
      SchemaUtil::DependentMap dependent_map,
      BuildTransitiveDependentGraph(schema, allow_circular_schema_definitions));

  // Tracks PropertyConfigs within a SchemaTypeConfig that we've validated
  // already.
  std::unordered_set<std::string_view> known_property_names;

  // Tracks PropertyConfigs containing joinable properties.
  std::unordered_set<std::string_view> schema_types_with_joinable_property;

  // 2. Validate the properties of each type.
  for (const auto& type_config : schema.types()) {
    std::string_view schema_type(type_config.schema_type());
    ICING_RETURN_IF_ERROR(ValidateSchemaType(schema_type));

    // We only care about properties being unique within one type_config
    known_property_names.clear();

    for (const auto& property_config : type_config.properties()) {
      std::string_view property_name(property_config.property_name());
      ICING_RETURN_IF_ERROR(ValidatePropertyName(property_name, schema_type));

      // Property names must be unique
      if (!known_property_names.insert(property_name).second) {
        return absl_ports::AlreadyExistsError(absl_ports::StrCat(
            "Field 'property_name' '", property_name,
            "' is already defined for schema '", schema_type, "'"));
      }

      auto data_type = property_config.data_type();
      ICING_RETURN_IF_ERROR(
          ValidateDataType(data_type, schema_type, property_name));

      if (data_type == PropertyConfigProto::DataType::DOCUMENT) {
        // Need to know what schema_type these Document properties should be
        // validated against
        std::string_view property_schema_type(property_config.schema_type());
        libtextclassifier3::Status validated_status =
            ValidateSchemaType(property_schema_type);
        if (!validated_status.ok()) {
          return absl_ports::Annotate(
              validated_status,
              absl_ports::StrCat("Field 'schema_type' is required for DOCUMENT "
                                 "data_types in schema property '",
                                 schema_type, ".", property_name, "'"));
        }

        ICING_RETURN_IF_ERROR(ValidateDocumentIndexingConfig(
            property_config.document_indexing_config(), schema_type,
            property_name));
      }

      ICING_RETURN_IF_ERROR(ValidateCardinality(property_config.cardinality(),
                                                schema_type, property_name));

      if (data_type == PropertyConfigProto::DataType::STRING) {
        ICING_RETURN_IF_ERROR(ValidateStringIndexingConfig(
            property_config.string_indexing_config(), data_type, schema_type,
            property_name));
      }

      ICING_RETURN_IF_ERROR(ValidateJoinableConfig(
          property_config.joinable_config(), data_type,
          property_config.cardinality(), schema_type, property_name));
      if (property_config.joinable_config().value_type() !=
          JoinableConfig::ValueType::NONE) {
        schema_types_with_joinable_property.insert(schema_type);
      }
    }
  }

  // BFS traverse the dependent graph to make sure that no nested levels
  // (properties with DOCUMENT data type) have REPEATED cardinality while
  // depending on schema types with joinable property.
  std::queue<std::string_view> frontier;
  for (const auto& schema_type : schema_types_with_joinable_property) {
    frontier.push(schema_type);
  }
  std::unordered_set<std::string_view> traversed =
      std::move(schema_types_with_joinable_property);
  while (!frontier.empty()) {
    std::string_view schema_type = frontier.front();
    frontier.pop();

    const auto it = dependent_map.find(schema_type);
    if (it == dependent_map.end()) {
      continue;
    }

    // Check every type that has a property of type schema_type.
    for (const auto& [next_schema_type, property_configs] : it->second) {
      // Check all properties in "next_schema_type" that are of type
      // "schema_type".
      for (const PropertyConfigProto* property_config : property_configs) {
        if (property_config != nullptr &&
            property_config->cardinality() ==
                PropertyConfigProto::Cardinality::REPEATED) {
          return absl_ports::InvalidArgumentError(absl_ports::StrCat(
              "Schema type '", next_schema_type,
              "' cannot have REPEATED nested document property '",
              property_config->property_name(),
              "' while connecting to some joinable properties"));
        }
      }

      if (traversed.count(next_schema_type) == 0) {
        traversed.insert(next_schema_type);
        frontier.push(next_schema_type);
      }
    }
  }

  // Verify that every child type's property set has included all compatible
  // properties from parent types.
  ICING_RETURN_IF_ERROR(ValidateInheritedProperties(schema));
  return dependent_map;
}

libtextclassifier3::Status SchemaUtil::ValidateSchemaType(
    std::string_view schema_type) {
  // Require a schema_type
  if (schema_type.empty()) {
    return absl_ports::InvalidArgumentError(
        "Field 'schema_type' cannot be empty.");
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidatePropertyName(
    std::string_view property_name, std::string_view schema_type) {
  // Require a property_name
  if (property_name.empty()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Field 'property_name' for schema '", schema_type,
                           "' cannot be empty."));
  }

  // Only support alphanumeric values.
  for (char c : property_name) {
    if (!std::isalnum(c)) {
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Field 'property_name' '", property_name,
                             "' can only contain alphanumeric characters."));
    }
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidateDataType(
    PropertyConfigProto::DataType::Code data_type, std::string_view schema_type,
    std::string_view property_name) {
  // UNKNOWN is the default enum value and should only be used for backwards
  // compatibility
  if (data_type == PropertyConfigProto::DataType::UNKNOWN) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Field 'data_type' cannot be UNKNOWN for schema property '",
        schema_type, ".", property_name, "'"));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidateCardinality(
    PropertyConfigProto::Cardinality::Code cardinality,
    std::string_view schema_type, std::string_view property_name) {
  // UNKNOWN is the default enum value and should only be used for backwards
  // compatibility
  if (cardinality == PropertyConfigProto::Cardinality::UNKNOWN) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Field 'cardinality' cannot be UNKNOWN for schema property '",
        schema_type, ".", property_name, "'"));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidateStringIndexingConfig(
    const StringIndexingConfig& config,
    PropertyConfigProto::DataType::Code data_type, std::string_view schema_type,
    std::string_view property_name) {
  if (config.term_match_type() == TermMatchType::UNKNOWN &&
      config.tokenizer_type() != StringIndexingConfig::TokenizerType::NONE) {
    // They set a tokenizer type, but no term match type.
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Indexed string property '", schema_type, ".", property_name,
        "' cannot have a term match type UNKNOWN"));
  }

  if (config.term_match_type() != TermMatchType::UNKNOWN &&
      config.tokenizer_type() == StringIndexingConfig::TokenizerType::NONE) {
    // They set a term match type, but no tokenizer type
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Indexed string property '", property_name,
                           "' cannot have a tokenizer type of NONE"));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidateJoinableConfig(
    const JoinableConfig& config, PropertyConfigProto::DataType::Code data_type,
    PropertyConfigProto::Cardinality::Code cardinality,
    std::string_view schema_type, std::string_view property_name) {
  if (config.value_type() == JoinableConfig::ValueType::QUALIFIED_ID) {
    if (data_type != PropertyConfigProto::DataType::STRING) {
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Qualified id joinable property '", property_name,
                             "' is required to have STRING data type"));
    }

    if (cardinality == PropertyConfigProto::Cardinality::REPEATED) {
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Qualified id joinable property '", property_name,
                             "' cannot have REPEATED cardinality"));
    }
  }

  if (config.propagate_delete() &&
      config.value_type() != JoinableConfig::ValueType::QUALIFIED_ID) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Field 'property_name' '", property_name,
                           "' is required to have QUALIFIED_ID joinable "
                           "value type with delete propagation enabled"));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status SchemaUtil::ValidateDocumentIndexingConfig(
    const DocumentIndexingConfig& config, std::string_view schema_type,
    std::string_view property_name) {
  if (!config.indexable_nested_properties_list().empty() &&
      config.index_nested_properties()) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "DocumentIndexingConfig.index_nested_properties is required to be "
        "false when providing a non-empty indexable_nested_properties_list "
        "for property '",
        schema_type, ".", property_name, "'"));
  }
  return libtextclassifier3::Status::OK;
}

/* static */ bool SchemaUtil::IsIndexedProperty(
    const PropertyConfigProto& property_config) {
  switch (property_config.data_type()) {
    case PropertyConfigProto::DataType::STRING:
      return property_config.string_indexing_config().term_match_type() !=
                 TermMatchType::UNKNOWN &&
             property_config.string_indexing_config().tokenizer_type() !=
                 StringIndexingConfig::TokenizerType::NONE;
    case PropertyConfigProto::DataType::INT64:
      return property_config.integer_indexing_config().numeric_match_type() !=
             IntegerIndexingConfig::NumericMatchType::UNKNOWN;
    case PropertyConfigProto::DataType::DOCUMENT:
      // A document property is considered indexed if it has
      // index_nested_properties=true, or a non-empty
      // indexable_nested_properties_list.
      return property_config.document_indexing_config()
                 .index_nested_properties() ||
             !property_config.document_indexing_config()
                  .indexable_nested_properties_list()
                  .empty();
    case PropertyConfigProto::DataType::UNKNOWN:
    case PropertyConfigProto::DataType::DOUBLE:
    case PropertyConfigProto::DataType::BOOLEAN:
    case PropertyConfigProto::DataType::BYTES:
      return false;
  }
}

bool SchemaUtil::IsParent(const SchemaUtil::InheritanceMap& inheritance_map,
                          std::string_view parent_type,
                          std::string_view child_type) {
  auto iter = inheritance_map.find(parent_type);
  if (iter == inheritance_map.end()) {
    return false;
  }
  return iter->second.count(child_type) > 0;
}

bool SchemaUtil::IsInheritedPropertyCompatible(
    const SchemaUtil::InheritanceMap& inheritance_map,
    const PropertyConfigProto& child_property_config,
    const PropertyConfigProto& parent_property_config) {
  // Check if child_property_config->cardinality() <=
  // parent_property_config->cardinality().
  // Subtype may require a stricter cardinality, but cannot loosen cardinality
  // requirements.
  if (!CardinalityLessThanEq(child_property_config.cardinality(),
                             parent_property_config.cardinality())) {
    return false;
  }

  // Now we can assume T1 and T2 are not nullptr, and cardinality check passes.
  if (child_property_config.data_type() !=
          PropertyConfigProto::DataType::DOCUMENT ||
      parent_property_config.data_type() !=
          PropertyConfigProto::DataType::DOCUMENT) {
    return child_property_config.data_type() ==
           parent_property_config.data_type();
  }

  // Now we can assume T1 and T2 are both document type.
  return child_property_config.schema_type() ==
             parent_property_config.schema_type() ||
         IsParent(inheritance_map, parent_property_config.schema_type(),
                  child_property_config.schema_type());
}

libtextclassifier3::Status SchemaUtil::ValidateInheritedProperties(
    const SchemaProto& schema) {
  // Create a inheritance map
  ICING_ASSIGN_OR_RETURN(SchemaUtil::InheritanceMap inheritance_map,
                         BuildTransitiveInheritanceGraph(schema));

  // Create a map that maps from type name to property names, and then from
  // property names to PropertyConfigProto.
  std::unordered_map<
      std::string, std::unordered_map<std::string, const PropertyConfigProto*>>
      property_map;
  for (const SchemaTypeConfigProto& type_config : schema.types()) {
    // Skipping building entries for types without any child or parent, since
    // such entry will never be used.
    if (type_config.parent_types().empty() &&
        inheritance_map.count(type_config.schema_type()) == 0) {
      continue;
    }
    auto& curr_property_map = property_map[type_config.schema_type()];
    for (const PropertyConfigProto& property_config :
         type_config.properties()) {
      curr_property_map[property_config.property_name()] = &property_config;
    }
  }

  // Validate child properties.
  for (const SchemaTypeConfigProto& type_config : schema.types()) {
    const std::string& child_type_name = type_config.schema_type();
    auto& child_property_map = property_map[child_type_name];

    for (const std::string& parent_type_name : type_config.parent_types()) {
      auto& parent_property_map = property_map[parent_type_name];

      for (const auto& [property_name, parent_property_config] :
           parent_property_map) {
        auto child_property_iter = child_property_map.find(property_name);
        if (child_property_iter == child_property_map.end()) {
          return absl_ports::InvalidArgumentError(absl_ports::StrCat(
              "Property ", property_name, " is not present in child type ",
              child_type_name, ", but it is defined in the parent type ",
              parent_type_name, "."));
        }
        if (!IsInheritedPropertyCompatible(inheritance_map,
                                           *child_property_iter->second,
                                           *parent_property_config)) {
          return absl_ports::InvalidArgumentError(absl_ports::StrCat(
              "Property ", property_name, " from child type ", child_type_name,
              " is not compatible to the parent type ", parent_type_name, "."));
        }
      }
    }
  }
  return libtextclassifier3::Status::OK;
}

void SchemaUtil::BuildTypeConfigMap(
    const SchemaProto& schema, SchemaUtil::TypeConfigMap* type_config_map) {
  type_config_map->clear();
  for (const SchemaTypeConfigProto& type_config : schema.types()) {
    type_config_map->emplace(type_config.schema_type(), type_config);
  }
}

SchemaUtil::ParsedPropertyConfigs SchemaUtil::ParsePropertyConfigs(
    const SchemaTypeConfigProto& type_config) {
  ParsedPropertyConfigs parsed_property_configs;

  // TODO(cassiewang): consider caching property_config_map for some properties,
  // e.g. using LRU cache. Or changing schema.proto to use go/protomap.
  for (const PropertyConfigProto& property_config : type_config.properties()) {
    std::string_view property_name = property_config.property_name();
    parsed_property_configs.property_config_map.emplace(property_name,
                                                        &property_config);
    if (property_config.cardinality() ==
        PropertyConfigProto::Cardinality::REQUIRED) {
      parsed_property_configs.required_properties.insert(property_name);
    }

    // A non-default term_match_type indicates that this property is meant to be
    // indexed.
    if (IsIndexedProperty(property_config)) {
      parsed_property_configs.indexed_properties.insert(property_name);
    }

    // A non-default value_type indicates that this property is meant to be
    // joinable.
    if (property_config.joinable_config().value_type() !=
        JoinableConfig::ValueType::NONE) {
      parsed_property_configs.joinable_properties.insert(property_name);
    }

    // Also keep track of how many nested document properties there are. Adding
    // new nested document properties will result in join-index rebuild.
    if (property_config.data_type() ==
        PropertyConfigProto::DataType::DOCUMENT) {
      parsed_property_configs.nested_document_properties.insert(property_name);
    }
  }

  return parsed_property_configs;
}

const SchemaUtil::SchemaDelta SchemaUtil::ComputeCompatibilityDelta(
    const SchemaProto& old_schema, const SchemaProto& new_schema,
    const DependentMap& new_schema_dependent_map) {
  SchemaDelta schema_delta;

  TypeConfigMap old_type_config_map, new_type_config_map;
  BuildTypeConfigMap(old_schema, &old_type_config_map);
  BuildTypeConfigMap(new_schema, &new_type_config_map);

  // Iterate through and check each field of the old schema
  for (const auto& old_type_config : old_schema.types()) {
    auto new_schema_type_and_config =
        new_type_config_map.find(old_type_config.schema_type());

    if (new_schema_type_and_config == new_type_config_map.end()) {
      // Didn't find the old schema type in the new schema, all the old
      // documents of this schema type are invalid without the schema
      ICING_VLOG(1) << absl_ports::StrCat("Previously defined schema type '",
                                          old_type_config.schema_type(),
                                          "' was not defined in new schema");
      schema_delta.schema_types_deleted.insert(old_type_config.schema_type());
      continue;
    }

    ParsedPropertyConfigs new_parsed_property_configs =
        ParsePropertyConfigs(new_schema_type_and_config->second);

    // We only need to check the old, existing properties to see if they're
    // compatible since we'll have old data that may be invalidated or need to
    // be reindexed.
    std::unordered_set<std::string_view> old_required_properties;
    std::unordered_set<std::string_view> old_indexed_properties;
    std::unordered_set<std::string_view> old_joinable_properties;
    std::unordered_set<std::string_view> old_nested_document_properties;

    // If there is a different number of properties, then there must have been a
    // change.
    bool has_property_changed =
        old_type_config.properties_size() !=
        new_schema_type_and_config->second.properties_size();
    bool is_incompatible = false;
    bool is_index_incompatible = false;
    bool is_join_incompatible = false;
    for (const auto& old_property_config : old_type_config.properties()) {
      std::string_view property_name = old_property_config.property_name();
      if (old_property_config.cardinality() ==
          PropertyConfigProto::Cardinality::REQUIRED) {
        old_required_properties.insert(property_name);
      }

      // A non-default term_match_type indicates that this property is meant to
      // be indexed.
      bool is_indexed_property = IsIndexedProperty(old_property_config);
      if (is_indexed_property) {
        old_indexed_properties.insert(property_name);
      }

      bool is_joinable_property =
          old_property_config.joinable_config().value_type() !=
          JoinableConfig::ValueType::NONE;
      if (is_joinable_property) {
        old_joinable_properties.insert(property_name);
      }

      // A nested-document property is a property of DataType::DOCUMENT.
      bool is_nested_document_property =
          old_property_config.data_type() ==
          PropertyConfigProto::DataType::DOCUMENT;
      if (is_nested_document_property) {
        old_nested_document_properties.insert(property_name);
      }

      auto new_property_name_and_config =
          new_parsed_property_configs.property_config_map.find(
              old_property_config.property_name());

      if (new_property_name_and_config ==
          new_parsed_property_configs.property_config_map.end()) {
        // Didn't find the old property
        ICING_VLOG(1) << absl_ports::StrCat(
            "Previously defined property type '", old_type_config.schema_type(),
            ".", old_property_config.property_name(),
            "' was not defined in new schema");
        is_incompatible = true;
        is_index_incompatible |= is_indexed_property;
        is_join_incompatible |=
            is_joinable_property || is_nested_document_property;
        continue;
      }

      const PropertyConfigProto* new_property_config =
          new_property_name_and_config->second;
      if (!has_property_changed &&
          !ArePropertiesEqual(old_property_config, *new_property_config)) {
        // Finally found a property that changed.
        has_property_changed = true;
      }

      if (!IsPropertyCompatible(old_property_config, *new_property_config)) {
        ICING_VLOG(1) << absl_ports::StrCat(
            "Property '", old_type_config.schema_type(), ".",
            old_property_config.property_name(), "' is incompatible.");
        is_incompatible = true;
      }

      // Any change in the indexed property requires a reindexing
      if (!IsTermMatchTypeCompatible(
              old_property_config.string_indexing_config(),
              new_property_config->string_indexing_config()) ||
          !IsIntegerNumericMatchTypeCompatible(
              old_property_config.integer_indexing_config(),
              new_property_config->integer_indexing_config()) ||
          !IsDocumentIndexingCompatible(
              old_property_config.document_indexing_config(),
              new_property_config->document_indexing_config())) {
        is_index_incompatible = true;
      }

      if (old_property_config.joinable_config().value_type() !=
          new_property_config->joinable_config().value_type()) {
        is_join_incompatible = true;
      }
    }

    // We can't have new properties that are REQUIRED since we won't know how
    // to backfill the data, and the existing data will be invalid. We're
    // guaranteed from our previous checks that all the old properties are also
    // present in the new property config, so we can do a simple int comparison
    // here to detect new required properties.
    if (!IsSubset(new_parsed_property_configs.required_properties,
                  old_required_properties)) {
      ICING_VLOG(1) << absl_ports::StrCat(
          "New schema '", old_type_config.schema_type(),
          "' has REQUIRED properties that are not "
          "present in the previously defined schema");
      is_incompatible = true;
    }

    // If we've gained any new indexed properties (this includes gaining new
    // indexed nested document properties), then the section ids may change.
    // Since the section ids are stored in the index, we'll need to
    // reindex everything.
    if (!IsSubset(new_parsed_property_configs.indexed_properties,
                  old_indexed_properties)) {
      ICING_VLOG(1) << "Set of indexed properties in schema type '"
                    << old_type_config.schema_type()
                    << "' has changed, required reindexing.";
      is_index_incompatible = true;
    }

    // If we've gained any new joinable properties, then the joinable property
    // ids may change. Since the joinable property ids are stored in the cache,
    // we'll need to reconstruct join index.
    // If we've gained any new nested document properties, we also rebuild the
    // join index. This is because we index all nested joinable properties, so
    // adding a nested document property will most probably result in having
    // more joinable properties.
    if (!IsSubset(new_parsed_property_configs.joinable_properties,
                  old_joinable_properties) ||
        !IsSubset(new_parsed_property_configs.nested_document_properties,
                  old_nested_document_properties)) {
      ICING_VLOG(1) << "Set of joinable properties in schema type '"
                    << old_type_config.schema_type()
                    << "' has changed, required reconstructing joinable cache.";
      is_join_incompatible = true;
    }

    if (is_incompatible) {
      AddIncompatibleChangeToDelta(schema_delta.schema_types_incompatible,
                                   old_type_config, new_schema_dependent_map,
                                   old_type_config_map, new_type_config_map);
    }

    if (is_index_incompatible) {
      AddIncompatibleChangeToDelta(schema_delta.schema_types_index_incompatible,
                                   old_type_config, new_schema_dependent_map,
                                   old_type_config_map, new_type_config_map);
    }

    if (is_join_incompatible) {
      AddIncompatibleChangeToDelta(schema_delta.schema_types_join_incompatible,
                                   old_type_config, new_schema_dependent_map,
                                   old_type_config_map, new_type_config_map);
    }

    if (!is_incompatible && !is_index_incompatible && !is_join_incompatible &&
        has_property_changed) {
      schema_delta.schema_types_changed_fully_compatible.insert(
          old_type_config.schema_type());
    }

    // Lastly, remove this type from the map. We know that this type can't
    // come up in future iterations through the old schema types because the old
    // type config has unique types.
    new_type_config_map.erase(old_type_config.schema_type());
  }

  // Any types that are still present in the new_type_config_map are newly added
  // types.
  schema_delta.schema_types_new.reserve(new_type_config_map.size());
  for (auto& kvp : new_type_config_map) {
    schema_delta.schema_types_new.insert(std::move(kvp.first));
  }

  return schema_delta;
}

}  // namespace lib
}  // namespace icing
