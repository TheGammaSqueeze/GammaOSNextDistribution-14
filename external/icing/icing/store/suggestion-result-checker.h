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

#ifndef ICING_STORE_SUGGESTION_RESULT_CHECKER_H_
#define ICING_STORE_SUGGESTION_RESULT_CHECKER_H_

#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

class SuggestionResultChecker {
 public:
  virtual ~SuggestionResultChecker() = default;

  // Check whether the given document id is belongs to the target namespaces.
  // Returns:
  //   On success,
  //     - true:  the given document id belongs to the target namespaces
  //     - false: the given document id doesn't belong to the target namespaces
  //   OUT_OF_RANGE if document_id is negative or exceeds previously seen
  //                DocumentIds
  //   NOT_FOUND if the document or the filter data is not found
  //   INTERNAL_ERROR on all other errors
  virtual bool BelongsToTargetResults(DocumentId document_id,
                                      SectionId section_id) const = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_SUGGESTION_RESULT_CHECKER_H_
