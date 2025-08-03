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

#include "icing/query/suggestion-processor.h"

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/term-metadata.h"
#include "icing/proto/search.pb.h"
#include "icing/query/query-processor.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-id.h"
#include "icing/store/suggestion-result-checker-impl.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<std::unique_ptr<SuggestionProcessor>>
SuggestionProcessor::Create(Index* index,
                            const NumericIndex<int64_t>* numeric_index,
                            const LanguageSegmenter* language_segmenter,
                            const Normalizer* normalizer,
                            const DocumentStore* document_store,
                            const SchemaStore* schema_store,
                            const Clock* clock) {
  ICING_RETURN_ERROR_IF_NULL(index);
  ICING_RETURN_ERROR_IF_NULL(numeric_index);
  ICING_RETURN_ERROR_IF_NULL(language_segmenter);
  ICING_RETURN_ERROR_IF_NULL(normalizer);
  ICING_RETURN_ERROR_IF_NULL(document_store);
  ICING_RETURN_ERROR_IF_NULL(schema_store);
  ICING_RETURN_ERROR_IF_NULL(clock);

  return std::unique_ptr<SuggestionProcessor>(
      new SuggestionProcessor(index, numeric_index, language_segmenter,
                              normalizer, document_store, schema_store, clock));
}

libtextclassifier3::StatusOr<
    std::unordered_map<NamespaceId, std::unordered_set<DocumentId>>>
PopulateDocumentIdFilters(
    const DocumentStore* document_store,
    const icing::lib::SuggestionSpecProto& suggestion_spec,
    const std::unordered_set<NamespaceId>& namespace_ids) {
  std::unordered_map<NamespaceId, std::unordered_set<DocumentId>>
      document_id_filter_map;
  document_id_filter_map.reserve(suggestion_spec.document_uri_filters_size());
  for (const NamespaceDocumentUriGroup& namespace_document_uri_group :
       suggestion_spec.document_uri_filters()) {
    auto namespace_id_or = document_store->GetNamespaceId(
        namespace_document_uri_group.namespace_());
    if (!namespace_id_or.ok()) {
      // The current namespace doesn't exist.
      continue;
    }
    NamespaceId namespace_id = namespace_id_or.ValueOrDie();
    if (!namespace_ids.empty() &&
        namespace_ids.find(namespace_id) == namespace_ids.end()) {
      // The current namespace doesn't appear in the namespace filter.
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "The namespace : ", namespace_document_uri_group.namespace_(),
          " appears in the document uri filter, but doesn't appear in the "
          "namespace filter."));
    }

    if (namespace_document_uri_group.document_uris().empty()) {
      // Client should use namespace filter to filter out all document under
      // a namespace.
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "The namespace : ", namespace_document_uri_group.namespace_(),
          " has empty document uri in the document uri filter. Please use the "
          "namespace filter to exclude a namespace instead of the document uri "
          "filter."));
    }

    // Translate namespace document Uris into document_ids
    std::unordered_set<DocumentId> target_document_ids;
    target_document_ids.reserve(
        namespace_document_uri_group.document_uris_size());
    for (std::string_view document_uri :
         namespace_document_uri_group.document_uris()) {
      auto document_id_or = document_store->GetDocumentId(
          namespace_document_uri_group.namespace_(), document_uri);
      if (!document_id_or.ok()) {
        continue;
      }
      target_document_ids.insert(document_id_or.ValueOrDie());
    }
    document_id_filter_map.insert({namespace_id, target_document_ids});
  }
  return document_id_filter_map;
}

libtextclassifier3::StatusOr<std::unordered_map<SchemaTypeId, SectionIdMask>>
PopulatePropertyFilters(
    const SchemaStore* schema_store,
    const icing::lib::SuggestionSpecProto& suggestion_spec,
    const std::unordered_set<SchemaTypeId>& schema_type_ids) {
  std::unordered_map<SchemaTypeId, SectionIdMask> property_filter_map;
  property_filter_map.reserve(suggestion_spec.type_property_filters_size());
  for (const TypePropertyMask& type_field_mask :
       suggestion_spec.type_property_filters()) {
    auto schema_type_id_or =
        schema_store->GetSchemaTypeId(type_field_mask.schema_type());
    if (!schema_type_id_or.ok()) {
      // The current schema doesn't exist
      continue;
    }
    SchemaTypeId schema_type_id = schema_type_id_or.ValueOrDie();

    if (!schema_type_ids.empty() &&
        schema_type_ids.find(schema_type_id) == schema_type_ids.end()) {
      // The current schema type doesn't appear in the schema type filter.
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "The schema : ", type_field_mask.schema_type(),
          " appears in the property filter, but doesn't appear in the schema"
          " type filter."));
    }

    if (type_field_mask.paths().empty()) {
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "The schema type : ", type_field_mask.schema_type(),
          " has empty path in the property filter. Please use the schema type"
          " filter to exclude a schema type instead of the property filter."));
    }

    // Translate property paths into section id mask
    SectionIdMask section_mask = kSectionIdMaskNone;
    auto section_metadata_list_or =
        schema_store->GetSectionMetadata(type_field_mask.schema_type());
    if (!section_metadata_list_or.ok()) {
      // The current schema doesn't has section metadata.
      continue;
    }
    std::unordered_set<std::string> target_property_paths;
    target_property_paths.reserve(type_field_mask.paths_size());
    for (const std::string& target_property_path : type_field_mask.paths()) {
      target_property_paths.insert(target_property_path);
    }
    const std::vector<SectionMetadata>* section_metadata_list =
        section_metadata_list_or.ValueOrDie();
    for (const SectionMetadata& section_metadata : *section_metadata_list) {
      if (target_property_paths.find(section_metadata.path) !=
          target_property_paths.end()) {
        section_mask |= UINT64_C(1) << section_metadata.id;
      }
    }
    property_filter_map.insert({schema_type_id, section_mask});
  }
  return property_filter_map;
}

libtextclassifier3::StatusOr<std::vector<TermMetadata>>
SuggestionProcessor::QuerySuggestions(
    const icing::lib::SuggestionSpecProto& suggestion_spec,
    int64_t current_time_ms) {
  // We use query tokenizer to tokenize the give prefix, and we only use the
  // last token to be the suggestion prefix.

  // Populate target namespace filter.
  std::unordered_set<NamespaceId> namespace_ids;
  namespace_ids.reserve(suggestion_spec.namespace_filters_size());
  for (std::string_view name_space : suggestion_spec.namespace_filters()) {
    auto namespace_id_or = document_store_.GetNamespaceId(name_space);
    if (!namespace_id_or.ok()) {
      // The current namespace doesn't exist.
      continue;
    }
    namespace_ids.insert(namespace_id_or.ValueOrDie());
  }
  if (namespace_ids.empty() && !suggestion_spec.namespace_filters().empty()) {
    // None of desired namespace exists, we should return directly.
    return std::vector<TermMetadata>();
  }

  // Populate target document id filter.
  auto document_id_filter_map_or = PopulateDocumentIdFilters(
      &document_store_, suggestion_spec, namespace_ids);
  if (!document_id_filter_map_or.ok()) {
    return std::move(document_id_filter_map_or).status();
  }

  std::unordered_map<NamespaceId, std::unordered_set<DocumentId>>
      document_id_filter_map = document_id_filter_map_or.ValueOrDie();
  if (document_id_filter_map.empty() &&
      !suggestion_spec.document_uri_filters().empty()) {
    // None of desired DocumentId exists, we should return directly.
    return std::vector<TermMetadata>();
  }

  // Populate target schema type filter.
  std::unordered_set<SchemaTypeId> schema_type_ids;
  schema_type_ids.reserve(suggestion_spec.schema_type_filters_size());
  for (std::string_view schema_type : suggestion_spec.schema_type_filters()) {
    auto schema_type_id_or = schema_store_.GetSchemaTypeId(schema_type);
    if (!schema_type_id_or.ok()) {
      continue;
    }
    schema_type_ids.insert(schema_type_id_or.ValueOrDie());
  }
  if (schema_type_ids.empty() &&
      !suggestion_spec.schema_type_filters().empty()) {
    // None of desired schema type exists, we should return directly.
    return std::vector<TermMetadata>();
  }

  // Populate target properties filter.
  auto property_filter_map_or =
      PopulatePropertyFilters(&schema_store_, suggestion_spec, schema_type_ids);
  if (!property_filter_map_or.ok()) {
    return std::move(property_filter_map_or).status();
  }
  std::unordered_map<SchemaTypeId, SectionIdMask> property_filter_map =
      property_filter_map_or.ValueOrDie();

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<QueryProcessor> query_processor,
      QueryProcessor::Create(&index_, &numeric_index_, &language_segmenter_,
                             &normalizer_, &document_store_, &schema_store_,
                             &clock_));

  SearchSpecProto search_spec;
  search_spec.set_query(suggestion_spec.prefix());
  search_spec.set_term_match_type(
      suggestion_spec.scoring_spec().scoring_match_type());
  ICING_ASSIGN_OR_RETURN(
      QueryResults query_results,
      query_processor->ParseSearch(search_spec,
                                   ScoringSpecProto::RankingStrategy::NONE,
                                   current_time_ms));

  ICING_ASSIGN_OR_RETURN(
      DocHitInfoIterator::TrimmedNode trimmed_node,
      std::move(*query_results.root_iterator).TrimRightMostNode());

  // If the position of the last token is not the end of the prefix, it means
  // there should be some operator tokens after it and are ignored by the
  // tokenizer.
  bool is_last_token =
      trimmed_node.term_start_index_ + trimmed_node.unnormalized_term_length_ >=
      suggestion_spec.prefix().length();

  if (!is_last_token || trimmed_node.term_.empty()) {
    // We don't have a valid last token, return early.
    return std::vector<TermMetadata>();
  }

  // Populate the search base in document ids.
  // Suggestions are only generated for the very last term,
  // trimmed_node.iterator_ tracks search results for all previous terms. If it
  // is null means there is no pervious term and we are generating suggetion for
  // a single term.
  std::unordered_set<DocumentId> search_base;
  if (trimmed_node.iterator_ != nullptr) {
    while (trimmed_node.iterator_->Advance().ok()) {
      search_base.insert(trimmed_node.iterator_->doc_hit_info().document_id());
    }
    if (search_base.empty()) {
      // Nothing matches the previous terms in the query. There are no valid
      // suggestions to make, we should return directly.
      return std::vector<TermMetadata>();
    }
  }

  // Create result checker based on given filters.
  SuggestionResultCheckerImpl suggestion_result_checker_impl(
      &document_store_, &schema_store_, std::move(namespace_ids),
      std::move(document_id_filter_map), std::move(schema_type_ids),
      std::move(property_filter_map), std::move(trimmed_node.target_section_),
      std::move(search_base), current_time_ms);
  // TODO(b/228240987) support generate suggestion and append suffix for advance
  // query and function call.
  std::string query_prefix =
      suggestion_spec.prefix().substr(0, trimmed_node.term_start_index_);
  // Run suggestion based on given SuggestionSpec.
  // Normalize token text to lowercase since all tokens in the lexicon are
  // lowercase.
  ICING_ASSIGN_OR_RETURN(
      std::vector<TermMetadata> terms,
      index_.FindTermsByPrefix(
          trimmed_node.term_, suggestion_spec.num_to_return(),
          suggestion_spec.scoring_spec().scoring_match_type(),
          suggestion_spec.scoring_spec().rank_by(),
          &suggestion_result_checker_impl));
  for (TermMetadata& term : terms) {
    term.content = query_prefix + term.content;
  }
  return terms;
}

SuggestionProcessor::SuggestionProcessor(
    Index* index, const NumericIndex<int64_t>* numeric_index,
    const LanguageSegmenter* language_segmenter, const Normalizer* normalizer,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    const Clock* clock)
    : index_(*index),
      numeric_index_(*numeric_index),
      language_segmenter_(*language_segmenter),
      normalizer_(*normalizer),
      document_store_(*document_store),
      schema_store_(*schema_store),
      clock_(*clock) {}

}  // namespace lib
}  // namespace icing
