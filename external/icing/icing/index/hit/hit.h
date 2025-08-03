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

#ifndef ICING_INDEX_HIT_HIT_H_
#define ICING_INDEX_HIT_HIT_H_

#include <array>
#include <cstdint>
#include <limits>

#include "icing/legacy/core/icing-packed-pod.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// BasicHit is a specific encoding that refers to content within a document. A
// basic hit consists of:
// - a DocumentId
// - a SectionId
// referring to the document and section that the hit corresponds to.
//
// The hit is the most basic unit of the index and, when grouped together by
// term, can be used to encode what terms appear in what documents.
//
// BasicHit is for indices (e.g. numeric index) that don't require term
// frequency.
class BasicHit {
 public:
  // The datatype used to encode BasicHit information: the document_id and
  // section_id.
  using Value = uint32_t;

  // WARNING: Changing this value will invalidate any pre-existing posting lists
  // on user devices.
  //
  // kInvalidValue contains:
  // - 0 for unused bits. Note that unused bits are always 0 for both valid and
  //   invalid BasicHit values.
  // - Inverted kInvalidDocumentId
  // - SectionId 0 (valid), which is ok because inverted kInvalidDocumentId has
  //   already invalidated the value. In fact, we currently use all 2^6 section
  //   ids and there is no "invalid section id", so it doesn't matter what
  //   SectionId we set for kInvalidValue.
  static constexpr Value kInvalidValue = 0;

  explicit BasicHit(SectionId section_id, DocumentId document_id);

  explicit BasicHit() : value_(kInvalidValue) {}

  bool is_valid() const { return value_ != kInvalidValue; }
  Value value() const { return value_; }
  DocumentId document_id() const;
  SectionId section_id() const;

  bool operator<(const BasicHit& h2) const { return value_ < h2.value_; }
  bool operator==(const BasicHit& h2) const { return value_ == h2.value_; }

 private:
  // Value bits layout: 4 unused + 22 document_id + 6 section id.
  Value value_;
} __attribute__((packed));
static_assert(sizeof(BasicHit) == 4, "");

// Hit is a specific encoding that refers to content within a document. A hit
// consists of:
// - a DocumentId
// - a SectionId
// referring to the document and section that the hit corresponds to, as well as
// metadata about the hit:
// - whether the Hit has a TermFrequency other than the default value
// - whether the Hit does not appear exactly in the document, but instead
//   represents a term that is a prefix of a term in the document
// - whether the Hit came from a section that has prefix expansion enabled
//   and a term frequency for the hit.
//
// The hit is the most basic unit of the index and, when grouped together by
// term, can be used to encode what terms appear in what documents.
class Hit {
 public:
  // The datatype used to encode Hit information: the document_id, section_id
  // and the has_term_frequency, prefix hit and in prefix section flags.
  using Value = uint32_t;

  // WARNING: Changing this value will invalidate any pre-existing posting lists
  // on user devices.
  //
  // WARNING:
  // - Hit::kInvalidValue should contain inverted kInvalidDocumentId, which is
  //   b'00...0. However, currently we set it as UINT32_MAX and actually it
  //   contains b'11...1, which is the inverted document_id 0.
  // - It means Hit::kInvalidValue contains valid (document_id, section_id,
  //   flags), so we potentially cannot distinguish if a Hit is invalid or not.
  //   The invalidity is an essential feature for posting list since we use it
  //   to determine the state of the posting list.
  // - The reason why it won't break the current posting list is because the
  //   unused bit(s) are set as 1 for Hit::kInvalidValue and 0 for all valid
  //   Hits. In other words, the unused bit(s) are actually serving as "invalid
  //   flag".
  // - If we want to exhaust all unused bits in the future, then we have to
  //   change Hit::kInvalidValue to set the inverted document_id section
  //   correctly (b'00...0, refer to BasicHit::kInvalidValue as an example).
  // - Also this problem is guarded by static_assert in hit.cc. If exhausting
  //   all unused bits, then the static_assert will detect and fail. We can
  //   safely remove the static_assert check after following the above process
  //   to resolve the incorrect Hit::kInvalidValue issue.
  static constexpr Value kInvalidValue = std::numeric_limits<Value>::max();
  // Docs are sorted in reverse, and 0 is never used as the inverted
  // DocumentId (because it is the inverse of kInvalidValue), so it is always
  // the max in a descending sort.
  static constexpr Value kMaxDocumentIdSortValue = 0;

  // The Term Frequency of a Hit.
  using TermFrequency = uint8_t;
  using TermFrequencyArray = std::array<Hit::TermFrequency, kTotalNumSections>;
  // Max TermFrequency is 255.
  static constexpr TermFrequency kMaxTermFrequency =
      std::numeric_limits<TermFrequency>::max();
  static constexpr TermFrequency kDefaultTermFrequency = 1;
  static constexpr TermFrequency kNoTermFrequency = 0;

  explicit Hit(Value value = kInvalidValue,
               TermFrequency term_frequency = kDefaultTermFrequency)
      : value_(value), term_frequency_(term_frequency) {}
  Hit(SectionId section_id, DocumentId document_id,
      TermFrequency term_frequency, bool is_in_prefix_section = false,
      bool is_prefix_hit = false);

  bool is_valid() const { return value() != kInvalidValue; }
  Value value() const { return value_; }
  DocumentId document_id() const;
  SectionId section_id() const;
  // Whether or not the hit contains a valid term frequency.
  bool has_term_frequency() const;
  TermFrequency term_frequency() const { return term_frequency_; }
  bool is_prefix_hit() const;
  bool is_in_prefix_section() const;

  // Creates a new hit based on old_hit but with new_document_id set.
  static Hit TranslateHit(Hit old_hit, DocumentId new_document_id);

  bool operator<(const Hit& h2) const { return value() < h2.value(); }
  bool operator==(const Hit& h2) const { return value() == h2.value(); }

  struct EqualsDocumentIdAndSectionId {
    bool operator()(const Hit& hit1, const Hit& hit2) const;
  };

 private:
  // Value and TermFrequency must be in this order.
  // Value bits layout: 1 unused + 22 document_id + 6 section id + 3 flags.
  Value value_;
  TermFrequency term_frequency_;
} __attribute__((packed));
static_assert(sizeof(Hit) == 5, "");
// TODO(b/138991332) decide how to remove/replace all is_packed_pod assertions.
static_assert(icing_is_packed_pod<Hit>::value, "go/icing-ubsan");

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_HIT_HIT_H_
