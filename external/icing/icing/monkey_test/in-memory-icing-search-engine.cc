// Copyright (C) 2022 Google LLC
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

#include "icing/monkey_test/in-memory-icing-search-engine.h"

#include <algorithm>
#include <cstdint>
#include <memory>
#include <random>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/monkey_test/monkey-tokenized-document.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/store/document-id.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Check if s1 is a prefix of s2.
bool IsPrefix(std::string_view s1, std::string_view s2) {
  if (s1.length() > s2.length()) {
    return false;
  }
  return s1 == s2.substr(0, s1.length());
}

}  // namespace

libtextclassifier3::StatusOr<const PropertyConfigProto *>
InMemoryIcingSearchEngine::GetPropertyConfig(
    const std::string &schema_type, const std::string &property_name) const {
  auto schema_iter = property_config_map_.find(schema_type);
  if (schema_iter == property_config_map_.end()) {
    return absl_ports::NotFoundError(
        absl_ports::StrCat("Schema type: ", schema_type, " is not found."));
  }
  auto property_iter = schema_iter->second.find(property_name);
  if (property_iter == schema_iter->second.end()) {
    return absl_ports::NotFoundError(
        absl_ports::StrCat("Property: ", property_name, " is not found."));
  }
  return &property_iter->second;
}

libtextclassifier3::StatusOr<TermMatchType::Code>
InMemoryIcingSearchEngine::GetTermMatchType(
    const std::string &schema_type,
    const MonkeyTokenizedSection &section) const {
  bool in_indexable_properties_list = false;
  bool all_indexable_from_top = true;

  std::vector<std::string_view> properties_in_path =
      absl_ports::StrSplit(section.path, ".");
  if (properties_in_path.empty()) {
    return absl_ports::InvalidArgumentError("Got empty path.");
  }
  std::string curr_schema_type = schema_type;
  for (int i = 0; i < properties_in_path.size(); ++i) {
    ICING_ASSIGN_OR_RETURN(
        const PropertyConfigProto *prop,
        GetPropertyConfig(curr_schema_type,
                          std::string(properties_in_path[i])));
    if (prop->data_type() == PropertyConfigProto::DataType::STRING) {
      return prop->string_indexing_config().term_match_type();
    }

    if (prop->data_type() != PropertyConfigProto::DataType::DOCUMENT) {
      return TermMatchType::Code::TermMatchType_Code_UNKNOWN;
    }

    bool old_all_indexable_from_top = all_indexable_from_top;
    all_indexable_from_top &=
        prop->document_indexing_config().index_nested_properties();
    if (!all_indexable_from_top && !in_indexable_properties_list) {
      // Only try to update in_indexable_properties_list if this is the first
      // level with index_nested_properties=false.
      if (old_all_indexable_from_top) {
        auto &indexable_properties =
            prop->document_indexing_config().indexable_nested_properties_list();
        std::string relative_path =
            absl_ports::StrCatPieces(std::vector<std::string_view>(
                properties_in_path.begin() + i + 1, properties_in_path.end()));
        in_indexable_properties_list =
            std::find(indexable_properties.begin(), indexable_properties.end(),
                      relative_path) != indexable_properties.end();
      }
      // Check in_indexable_properties_list again.
      if (!in_indexable_properties_list) {
        return TermMatchType::Code::TermMatchType_Code_UNKNOWN;
      }
    }
    curr_schema_type = prop->document_indexing_config().GetTypeName();
  }
  return TermMatchType::Code::TermMatchType_Code_UNKNOWN;
}

libtextclassifier3::StatusOr<bool>
InMemoryIcingSearchEngine::DoesDocumentMatchQuery(
    const MonkeyTokenizedDocument &document, const std::string &query,
    TermMatchType::Code term_match_type) const {
  std::vector<std::string_view> strs = absl_ports::StrSplit(query, ":");
  std::string_view query_term;
  std::string_view section_restrict;
  if (strs.size() > 1) {
    section_restrict = strs[0];
    query_term = strs[1];
  } else {
    query_term = query;
  }
  for (const MonkeyTokenizedSection &section : document.tokenized_sections) {
    if (!section_restrict.empty() && section.path != section_restrict) {
      continue;
    }
    ICING_ASSIGN_OR_RETURN(
        TermMatchType::Code section_term_match_type,
        GetTermMatchType(document.document.schema(), section));
    if (section_term_match_type == TermMatchType::UNKNOWN) {
      // Skip non-indexable property.
      continue;
    }
    for (const std::string &token : section.token_sequence) {
      if (section_term_match_type == TermMatchType::EXACT_ONLY ||
          term_match_type == TermMatchType::EXACT_ONLY) {
        if (token == query_term) {
          return true;
        }
      } else if (IsPrefix(query_term, token)) {
        return true;
      }
    }
  }
  return false;
}

void InMemoryIcingSearchEngine::SetSchema(SchemaProto &&schema) {
  schema_ = std::make_unique<SchemaProto>(std::move(schema));
  property_config_map_.clear();
  for (const SchemaTypeConfigProto &type_config : schema_->types()) {
    auto &curr_property_map = property_config_map_[type_config.schema_type()];
    for (const PropertyConfigProto &property_config :
         type_config.properties()) {
      curr_property_map.insert(
          {property_config.property_name(), property_config});
    }
  }
}

InMemoryIcingSearchEngine::PickDocumentResult
InMemoryIcingSearchEngine::RandomPickDocument(float p_alive, float p_all,
                                              float p_other) const {
  // Normalizing p_alive, p_all and p_other, so that they sum to 1.
  if (p_alive == 0 && p_all == 0 && p_other == 0) {
    p_alive = p_all = p_other = 1 / 3.;
  } else {
    float p_sum = p_alive + p_all + p_other;
    p_alive = p_alive / p_sum;
    p_all = p_all / p_sum;
    p_other = p_other / p_sum;
  }

  std::uniform_real_distribution<> real_dist(0, 1);
  float p = real_dist(*random_);
  if (p <= p_other || documents_.empty()) {
    // 20 is a fair number of non-existing namespaces and uris, enough for
    // monkey testing.
    std::uniform_int_distribution<> dist(0, 19);
    std::string name_space = absl_ports::StrCat("non_existing_namespace",
                                                std::to_string(dist(*random_)));
    std::string uri =
        absl_ports::StrCat("non_existing_uri", std::to_string(dist(*random_)));
    return {name_space, uri};
  }
  p -= p_other;
  DocumentId doc_id;
  if (p <= p_all || existing_doc_ids_.empty()) {
    std::uniform_int_distribution<DocumentId> dist(0, documents_.size() - 1);
    doc_id = dist(*random_);
  } else {
    std::uniform_int_distribution<DocumentId> dist(
        0, existing_doc_ids_.size() - 1);
    doc_id = existing_doc_ids_[dist(*random_)];
  }
  InMemoryIcingSearchEngine::PickDocumentResult result = {
      documents_[doc_id].document.namespace_(),
      documents_[doc_id].document.uri()};

  // Even the (name_space, uri) of the picked doc_id has not been deleted
  // specifically, doc_id may be outdated because of possible overwriting. So we
  // need to find the latest document id, and return the latest DocumentProto.
  auto latest_doc_id = InternalGet(result.name_space, result.uri);
  if (latest_doc_id.ok()) {
    result.document = documents_[latest_doc_id.ValueOrDie()].document;
  }
  return result;
}

void InMemoryIcingSearchEngine::Put(const MonkeyTokenizedDocument &document) {
  // Delete the old one if existing.
  Delete(document.document.namespace_(), document.document.uri()).IgnoreError();
  existing_doc_ids_.push_back(documents_.size());
  namespace_uri_docid_map[document.document.namespace_()]
                         [document.document.uri()] = documents_.size();
  documents_.push_back(document);
}

std::unordered_set<std::string> InMemoryIcingSearchEngine::GetAllNamespaces()
    const {
  std::unordered_set<std::string> namespaces;
  for (DocumentId doc_id : existing_doc_ids_) {
    namespaces.insert(documents_[doc_id].document.namespace_());
  }
  return namespaces;
}

libtextclassifier3::Status InMemoryIcingSearchEngine::Delete(
    const std::string &name_space, const std::string &uri) {
  libtextclassifier3::StatusOr<DocumentId> doc_id_or =
      InternalGet(name_space, uri);
  if (doc_id_or.ok()) {
    DocumentId doc_id = doc_id_or.ValueOrDie();
    const DocumentProto &document = documents_[doc_id].document;
    namespace_uri_docid_map[document.namespace_()].erase(document.uri());
    auto end_itr =
        std::remove(existing_doc_ids_.begin(), existing_doc_ids_.end(), doc_id);
    existing_doc_ids_.erase(end_itr, existing_doc_ids_.end());
  }
  return doc_id_or.status();
}

libtextclassifier3::StatusOr<uint32_t>
InMemoryIcingSearchEngine::DeleteByNamespace(const std::string &name_space) {
  std::vector<DocumentId> doc_ids_to_delete;
  for (DocumentId doc_id : existing_doc_ids_) {
    if (documents_[doc_id].document.namespace_() == name_space) {
      doc_ids_to_delete.push_back(doc_id);
    }
  }
  for (DocumentId doc_id : doc_ids_to_delete) {
    const DocumentProto &document = documents_[doc_id].document;
    if (!Delete(document.namespace_(), document.uri()).ok()) {
      return absl_ports::InternalError(
          "Should never happen. There are inconsistencies in the in-memory "
          "Icing.");
    }
  }
  return doc_ids_to_delete.size();
}

libtextclassifier3::StatusOr<uint32_t>
InMemoryIcingSearchEngine::DeleteBySchemaType(const std::string &schema_type) {
  std::vector<DocumentId> doc_ids_to_delete;
  for (DocumentId doc_id : existing_doc_ids_) {
    if (documents_[doc_id].document.schema() == schema_type) {
      doc_ids_to_delete.push_back(doc_id);
    }
  }
  for (DocumentId doc_id : doc_ids_to_delete) {
    const DocumentProto &document = documents_[doc_id].document;
    if (!Delete(document.namespace_(), document.uri()).ok()) {
      return absl_ports::InternalError(
          "Should never happen. There are inconsistencies in the in-memory "
          "Icing.");
    }
  }
  return doc_ids_to_delete.size();
}

libtextclassifier3::StatusOr<uint32_t> InMemoryIcingSearchEngine::DeleteByQuery(
    const SearchSpecProto &search_spec) {
  ICING_ASSIGN_OR_RETURN(std::vector<DocumentId> doc_ids_to_delete,
                         InternalSearch(search_spec));
  for (DocumentId doc_id : doc_ids_to_delete) {
    const DocumentProto &document = documents_[doc_id].document;
    if (!Delete(document.namespace_(), document.uri()).ok()) {
      return absl_ports::InternalError(
          "Should never happen. There are inconsistencies in the in-memory "
          "Icing.");
    }
  }
  return doc_ids_to_delete.size();
}

libtextclassifier3::StatusOr<std::vector<DocumentProto>>
InMemoryIcingSearchEngine::Search(const SearchSpecProto &search_spec) const {
  ICING_ASSIGN_OR_RETURN(std::vector<DocumentId> matched_doc_ids,
                         InternalSearch(search_spec));
  std::vector<DocumentProto> result;
  result.reserve(matched_doc_ids.size());
  for (DocumentId doc_id : matched_doc_ids) {
    result.push_back(documents_[doc_id].document);
  }
  return result;
}

libtextclassifier3::StatusOr<DocumentId> InMemoryIcingSearchEngine::InternalGet(
    const std::string &name_space, const std::string &uri) const {
  auto uris = namespace_uri_docid_map.find(name_space);
  if (uris != namespace_uri_docid_map.end()) {
    auto doc = uris->second.find(uri);
    if (doc != uris->second.end()) {
      return doc->second;
    }
  }
  return absl_ports::NotFoundError(absl_ports::StrCat(
      name_space, ", ", uri,
      " is not found by InMemoryIcingSearchEngine::InternalGet."));
}

libtextclassifier3::StatusOr<std::vector<DocumentId>>
InMemoryIcingSearchEngine::InternalSearch(
    const SearchSpecProto &search_spec) const {
  std::vector<DocumentId> matched_doc_ids;
  for (DocumentId doc_id : existing_doc_ids_) {
    ICING_ASSIGN_OR_RETURN(
        bool match,
        DoesDocumentMatchQuery(documents_[doc_id], search_spec.query(),
                               search_spec.term_match_type()));
    if (match) {
      matched_doc_ids.push_back(doc_id);
    }
  }
  return matched_doc_ids;
}

}  // namespace lib
}  // namespace icing
