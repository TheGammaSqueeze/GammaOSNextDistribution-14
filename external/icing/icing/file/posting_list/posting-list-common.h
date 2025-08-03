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

#ifndef ICING_FILE_POSTING_LIST_POSTING_LIST_COMMON_H_
#define ICING_FILE_POSTING_LIST_POSTING_LIST_COMMON_H_

#include <cstdint>

namespace icing {
namespace lib {

// A FlashIndexBlock can contain multiple posting lists. This specifies which
// PostingList in the FlashIndexBlock we want to refer to.
using PostingListIndex = int32_t;
inline constexpr PostingListIndex kInvalidPostingListIndex = ~0U;

inline constexpr uint32_t kInvalidBlockIndex = 0;

}  //  namespace lib
}  //  namespace icing

#endif  // ICING_FILE_POSTING_LIST_POSTING_LIST_COMMON_H_
