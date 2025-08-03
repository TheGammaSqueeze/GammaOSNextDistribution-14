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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_ALL_DOCUMENT_ID_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_ALL_DOCUMENT_ID_H_

#include <cstdint>
#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// Iterator for all DocumentIds in range [0, document_id_limit_]: 0 inclusive,
// document_id_limit_ inclusive. Returns DocumentIds in descending order.
class DocHitInfoIteratorAllDocumentId : public DocHitInfoIterator {
 public:
  explicit DocHitInfoIteratorAllDocumentId(DocumentId document_id_limit);

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  void MapChildren(const ChildrenMapper& mapper) override {}

  CallStats GetCallStats() const override {
    return CallStats(
        /*num_leaf_advance_calls_lite_index_in=*/0,
        /*num_leaf_advance_calls_main_index_in=*/0,
        /*num_leaf_advance_calls_integer_index_in=*/0,
        /*num_leaf_advance_calls_no_index_in=*/document_id_limit_ -
            current_document_id_,
        /*num_blocks_inspected_in=*/0);
  }

  std::string ToString() const override {
    return IcingStringUtil::StringPrintf("(ALL document_id_limit:%d)",
                                         document_id_limit_);
  }

 private:
  const DocumentId document_id_limit_;

  // An internal value for the iterator to track the current doc id.
  DocumentId current_document_id_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_ALL_DOCUMENT_ID_H_
