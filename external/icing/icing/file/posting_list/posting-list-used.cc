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

#include "icing/file/posting_list/posting-list-used.h"

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/posting-list-utils.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<PostingListUsed>
PostingListUsed::CreateFromPreexistingPostingListUsedRegion(
    PostingListSerializer* serializer,
    std::unique_ptr<uint8_t[]> posting_list_buffer, uint32_t size_in_bytes) {
  ICING_RETURN_ERROR_IF_NULL(serializer);
  ICING_RETURN_ERROR_IF_NULL(posting_list_buffer);

  if (!posting_list_utils::IsValidPostingListSize(
          size_in_bytes, serializer->GetDataTypeBytes(),
          serializer->GetMinPostingListSize())) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Requested posting list size %d is invalid!", size_in_bytes));
  }
  return PostingListUsed(std::move(posting_list_buffer), size_in_bytes);
}

libtextclassifier3::StatusOr<PostingListUsed>
PostingListUsed::CreateFromUnitializedRegion(PostingListSerializer* serializer,
                                             uint32_t size_in_bytes) {
  ICING_ASSIGN_OR_RETURN(
      PostingListUsed posting_list_used,
      CreateFromPreexistingPostingListUsedRegion(
          serializer, std::make_unique<uint8_t[]>(size_in_bytes),
          size_in_bytes));
  serializer->Clear(&posting_list_used);
  return posting_list_used;
}

}  // namespace lib
}  // namespace icing
