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

#include "icing/index/iterator/doc-hit-info-iterator-property-in-schema.h"

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

DocHitInfoIteratorPropertyInSchema::DocHitInfoIteratorPropertyInSchema(
    std::unique_ptr<DocHitInfoIterator> delegate,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    std::set<std::string> target_sections, int64_t current_time_ms)
    : delegate_(std::move(delegate)),
      document_store_(*document_store),
      schema_store_(*schema_store),
      target_properties_(std::move(target_sections)),
      current_time_ms_(current_time_ms) {}

libtextclassifier3::Status DocHitInfoIteratorPropertyInSchema::Advance() {
  doc_hit_info_ = DocHitInfo(kInvalidDocumentId);

  // Maps from SchemaTypeId to a bool indicating whether or not the type has
  // the requested property.
  std::unordered_map<SchemaTypeId, bool> property_defined_types;
  while (delegate_->Advance().ok()) {
    DocumentId document_id = delegate_->doc_hit_info().document_id();
    auto data_optional = document_store_.GetAliveDocumentFilterData(
        document_id, current_time_ms_);
    if (!data_optional) {
      // Ran into some error retrieving information on this hit, skip
      continue;
    }

    // Guaranteed that the DocumentFilterData exists at this point
    SchemaTypeId schema_type_id = data_optional.value().schema_type_id();
    bool valid_match = false;
    auto itr = property_defined_types.find(schema_type_id);
    if (itr != property_defined_types.end()) {
      valid_match = itr->second;
    } else {
      for (const auto& property : target_properties_) {
        if (schema_store_.IsPropertyDefinedInSchema(schema_type_id, property)) {
          valid_match = true;
          break;
        }
      }
      property_defined_types[schema_type_id] = valid_match;
    }

    if (valid_match) {
      doc_hit_info_ = delegate_->doc_hit_info();
      return libtextclassifier3::Status::OK;
    }

    // The document's schema does not define any properties listed in
    // target_properties_. Continue.
  }

  // Didn't find anything on the delegate iterator.
  return absl_ports::ResourceExhaustedError("No more DocHitInfos in iterator");
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorPropertyInSchema::TrimRightMostNode() && {
  // Don't generate suggestion if the last operator is this custom function.
  return absl_ports::InvalidArgumentError(
      "Cannot generate suggestion if the last term is hasPropertyDefined().");
}

std::string DocHitInfoIteratorPropertyInSchema::ToString() const {
  return absl_ports::StrCat("(", absl_ports::StrJoin(target_properties_, ","),
                            "): ", delegate_->ToString());
}

}  // namespace lib
}  // namespace icing
