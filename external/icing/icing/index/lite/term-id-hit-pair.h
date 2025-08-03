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

#ifndef ICING_INDEX_TERM_ID_HIT_PAIR_H_
#define ICING_INDEX_TERM_ID_HIT_PAIR_H_

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <vector>

#include "icing/index/hit/hit.h"
#include "icing/util/bit-util.h"

namespace icing {
namespace lib {

class TermIdHitPair {
 public:
  // Layout bits: 24 termid + 32 hit value + 8 hit term frequency.
  using Value = uint64_t;

  static constexpr int kTermIdBits = 24;
  static constexpr int kHitValueBits = sizeof(Hit::Value) * 8;
  static constexpr int kHitTermFrequencyBits = sizeof(Hit::TermFrequency) * 8;

  static const Value kInvalidValue;

  explicit TermIdHitPair(Value v = kInvalidValue) : value_(v) {}

  TermIdHitPair(uint32_t term_id, const Hit& hit) {
    static_assert(kTermIdBits + kHitValueBits + kHitTermFrequencyBits <=
                      sizeof(Value) * 8,
                  "TermIdHitPairTooBig");

    value_ = 0;
    // Term id goes into the most significant bits because it takes
    // precedent in sorts.
    bit_util::BitfieldSet(term_id, kHitValueBits + kHitTermFrequencyBits,
                          kTermIdBits, &value_);
    bit_util::BitfieldSet(hit.value(), kHitTermFrequencyBits, kHitValueBits,
                          &value_);
    bit_util::BitfieldSet(hit.term_frequency(), 0, kHitTermFrequencyBits,
                          &value_);
  }

  uint32_t term_id() const {
    return bit_util::BitfieldGet(value_, kHitValueBits + kHitTermFrequencyBits,
                                 kTermIdBits);
  }

  Hit hit() const {
    return Hit(
        bit_util::BitfieldGet(value_, kHitTermFrequencyBits, kHitValueBits),
        bit_util::BitfieldGet(value_, 0, kHitTermFrequencyBits));
  }

  Value value() const { return value_; }

  bool operator==(const TermIdHitPair& rhs) const {
    return value_ == rhs.value_;
  }

  bool operator<(const TermIdHitPair& rhs) const { return value_ < rhs.value_; }

 private:
  Value value_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_TERM_ID_HIT_PAIR_H_
