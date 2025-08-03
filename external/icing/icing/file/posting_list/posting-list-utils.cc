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

#include "icing/file/posting_list/posting-list-utils.h"

#include "icing/legacy/index/icing-bit-util.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace posting_list_utils {

bool IsValidPostingListSize(uint32_t size_in_bytes, uint32_t data_type_bytes,
                            uint32_t min_posting_list_size) {
  // size must be data_type_bytes aligned. Otherwise, we can have serious
  // wasted space in the worst case.
  if (size_in_bytes % data_type_bytes != 0) {
    ICING_LOG(ERROR) << "Size " << size_in_bytes << " data " << data_type_bytes;
    return false;
  }

  // Must be able to store the min information.
  if (size_in_bytes < min_posting_list_size) {
    ICING_LOG(ERROR) << "Size " << size_in_bytes << " is less than min size "
                     << min_posting_list_size;
    return false;
  }

  // We re-use the first two data as pointers into the posting list
  // so the posting list size must fit in data_type_bytes.
  if (BitsToStore(size_in_bytes) > data_type_bytes * 8) {
    ICING_LOG(ERROR)
        << "Posting list size must be small enough to store the offset in "
        << data_type_bytes << " bytes.";
    return false;
  }

  return true;
}

}  // namespace posting_list_utils

}  // namespace lib
}  // namespace icing
