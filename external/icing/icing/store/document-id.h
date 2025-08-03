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

#ifndef ICING_STORE_DOCUMENT_ID_H_
#define ICING_STORE_DOCUMENT_ID_H_

#include <cstdint>

namespace icing {
namespace lib {

// Id of a document
using DocumentId = int32_t;

// We use 22 bits to encode document_ids and use the largest value (2^22 - 1) to
// represent an invalid document_id.
inline constexpr int kDocumentIdBits = 22;
inline constexpr DocumentId kInvalidDocumentId =
    (INT32_C(1) << kDocumentIdBits) - 1;
inline constexpr DocumentId kMinDocumentId = 0;
inline constexpr DocumentId kMaxDocumentId = kInvalidDocumentId - 1;

constexpr bool IsDocumentIdValid(DocumentId document_id) {
  return document_id >= kMinDocumentId && document_id <= kMaxDocumentId;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_DOCUMENT_ID_H_
