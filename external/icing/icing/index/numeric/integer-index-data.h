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

#ifndef ICING_INDEX_NUMERIC_INTEGER_INDEX_DATA_H_
#define ICING_INDEX_NUMERIC_INTEGER_INDEX_DATA_H_

#include <cstdint>

#include "icing/index/hit/hit.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// Data wrapper to store BasicHit and key for integer index.
class IntegerIndexData {
 public:
  explicit IntegerIndexData(SectionId section_id, DocumentId document_id,
                            int64_t key)
      : basic_hit_(section_id, document_id), key_(key) {}

  explicit IntegerIndexData() : basic_hit_(), key_(0) {}

  const BasicHit& basic_hit() const { return basic_hit_; }

  int64_t key() const { return key_; }

  bool is_valid() const { return basic_hit_.is_valid(); }

  bool operator<(const IntegerIndexData& other) const {
    return basic_hit_ < other.basic_hit_;
  }

  bool operator==(const IntegerIndexData& other) const {
    return basic_hit_ == other.basic_hit_ && key_ == other.key_;
  }

 private:
  BasicHit basic_hit_;
  int64_t key_;
} __attribute__((packed));
static_assert(sizeof(IntegerIndexData) == 12, "");

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_INTEGER_INDEX_DATA_H_
