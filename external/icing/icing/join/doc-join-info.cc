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

#include "icing/join/doc-join-info.h"

#include <cstdint>

#include "icing/schema/joinable-property.h"
#include "icing/store/document-id.h"
#include "icing/util/bit-util.h"

namespace icing {
namespace lib {

DocJoinInfo::DocJoinInfo(DocumentId document_id,
                         JoinablePropertyId joinable_property_id) {
  Value temp_value = 0;
  bit_util::BitfieldSet(/*new_value=*/document_id,
                        /*lsb_offset=*/kJoinablePropertyIdBits,
                        /*len=*/kDocumentIdBits, &temp_value);
  bit_util::BitfieldSet(/*new_value=*/joinable_property_id,
                        /*lsb_offset=*/0,
                        /*len=*/kJoinablePropertyIdBits, &temp_value);
  value_ = temp_value;
}

DocumentId DocJoinInfo::document_id() const {
  return bit_util::BitfieldGet(value_, /*lsb_offset=*/kJoinablePropertyIdBits,
                               /*len=*/kDocumentIdBits);
}

JoinablePropertyId DocJoinInfo::joinable_property_id() const {
  return bit_util::BitfieldGet(value_, /*lsb_offset=*/0,
                               /*len=*/kJoinablePropertyIdBits);
}

}  // namespace lib
}  // namespace icing
