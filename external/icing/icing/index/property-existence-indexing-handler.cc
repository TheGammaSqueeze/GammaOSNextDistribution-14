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

#include "icing/index/property-existence-indexing-handler.h"

#include <memory>
#include <string>
#include <unordered_set>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/index.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

namespace {

void ConstructPropertyExistenceMetaToken(
    const std::string& current_path, const DocumentProto& document,
    std::unordered_set<std::string>& meta_tokens) {
  for (const PropertyProto& property : document.properties()) {
    std::string new_path = current_path;
    if (!new_path.empty()) {
      new_path.append(".");
    }
    new_path.append(property.name());
    for (const DocumentProto& nested_document : property.document_values()) {
      ConstructPropertyExistenceMetaToken(new_path, nested_document,
                                          meta_tokens);
    }
    // A string property exists if and only if there is at least one non-empty
    // string in the property.
    bool has_string_value = false;
    for (const std::string& string_value : property.string_values()) {
      if (!string_value.empty()) {
        has_string_value = true;
        break;
      }
    }
    if (has_string_value || property.int64_values_size() > 0 ||
        property.double_values_size() > 0 ||
        property.boolean_values_size() > 0 ||
        property.bytes_values_size() > 0 ||
        property.document_values_size() > 0) {
      meta_tokens.insert(
          absl_ports::StrCat(kPropertyExistenceTokenPrefix, new_path));
    }
  }
}

}  // namespace

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PropertyExistenceIndexingHandler>>
PropertyExistenceIndexingHandler::Create(const Clock* clock, Index* index) {
  ICING_RETURN_ERROR_IF_NULL(clock);
  ICING_RETURN_ERROR_IF_NULL(index);

  return std::unique_ptr<PropertyExistenceIndexingHandler>(
      new PropertyExistenceIndexingHandler(*clock, index));
}

libtextclassifier3::Status PropertyExistenceIndexingHandler::Handle(
    const TokenizedDocument& tokenized_document, DocumentId document_id,
    PutDocumentStatsProto* put_document_stats) {
  std::unique_ptr<Timer> index_timer = clock_.GetNewTimer();

  libtextclassifier3::Status status;
  // Section id is irrelevant to metadata tokens that is used to support
  // property existence check.
  Index::Editor editor =
      index_.Edit(document_id, /*section_id=*/0, TermMatchType::EXACT_ONLY,
                  /*namespace_id=*/0);
  std::unordered_set<std::string> meta_tokens;
  ConstructPropertyExistenceMetaToken(
      /*current_path=*/"", tokenized_document.document(), meta_tokens);
  for (const std::string& meta_token : meta_tokens) {
    status = editor.BufferTerm(meta_token.c_str());
    if (!status.ok()) {
      // We've encountered a failure. Bail out. We'll mark this doc as deleted
      // and signal a failure to the client.
      ICING_LOG(WARNING) << "Failed to buffer term in lite lexicon due to: "
                         << status.error_message();
      break;
    }
  }

  if (status.ok()) {
    // Add all the metadata tokens to support property existence check.
    status = editor.IndexAllBufferedTerms();
    if (!status.ok()) {
      ICING_LOG(WARNING) << "Failed to add hits in lite index due to: "
                         << status.error_message();
    }
  }

  if (put_document_stats != nullptr) {
    put_document_stats->set_metadata_term_index_latency_ms(
        index_timer->GetElapsedMilliseconds());
    put_document_stats->mutable_tokenization_stats()
        ->set_num_metadata_tokens_indexed(meta_tokens.size());
  }

  return status;
}

}  // namespace lib
}  // namespace icing
