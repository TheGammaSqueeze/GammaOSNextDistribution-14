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

#include "icing/index/hit/hit.h"

#include "icing/store/document-id.h"
#include "icing/util/bit-util.h"

namespace icing {
namespace lib {

namespace {

enum FlagOffset {
  // This hit, whether exact or not, came from a prefixed section and will
  // need to be backfilled into branching posting lists if/when those are
  // created.
  kInPrefixSection = 0,
  // This hit represents a prefix of a longer term. If exact matches are
  // required, then this hit should be ignored.
  kPrefixHit = 1,
  // Whether or not the hit has a term_frequency  other than
  // kDefaultTermFrequency.
  kHasTermFrequency = 2,
  kNumFlags = 3,
};

static_assert(kDocumentIdBits + kSectionIdBits + kNumFlags <
                  sizeof(Hit::Value) * 8,
              "Hit::kInvalidValue contains risky value and we should have at "
              "least one unused bit to avoid potential bugs. Please follow the "
              "process mentioned in hit.h to correct the value of "
              "Hit::kInvalidValue and remove this static_assert afterwards.");

static_assert(kDocumentIdBits + kSectionIdBits + kNumFlags <=
                  sizeof(Hit::Value) * 8,
              "HitOverflow");
static_assert(kDocumentIdBits == 22, "");
static_assert(kSectionIdBits == 6, "");
static_assert(kNumFlags == 3, "");

inline DocumentId InvertDocumentId(DocumentId document_id) {
  static_assert(kMaxDocumentId <= (std::numeric_limits<DocumentId>::max() - 1),
                "(kMaxDocumentId + 1) must not overflow.");
  static_assert(
      (kMaxDocumentId + 1) < (1U << kDocumentIdBits),
      "(kMaxDocumentId + 1) must also fit in kDocumentIdBits wide bitfield");
  // Invert the document_id value. +1 is added so the resulting range is [1,
  // kMaxDocumentId + 1].
  return (kMaxDocumentId + 1) - document_id;
}

}  // namespace

BasicHit::BasicHit(SectionId section_id, DocumentId document_id) {
  // Values are stored so that when sorted, they appear in document_id
  // descending, section_id ascending, order. So inverted document_id appears in
  // the most significant bits, followed by (uninverted) section_id.
  Value temp_value = 0;
  bit_util::BitfieldSet(/*new_value=*/InvertDocumentId(document_id),
                        /*lsb_offset=*/kSectionIdBits, /*len=*/kDocumentIdBits,
                        /*value_out=*/&temp_value);
  bit_util::BitfieldSet(/*new_value=*/section_id, /*lsb_offset=*/0,
                        /*len=*/kSectionIdBits, /*value_out=*/&temp_value);
  value_ = temp_value;
}

DocumentId BasicHit::document_id() const {
  DocumentId inverted_document_id = bit_util::BitfieldGet(
      value_, /*lsb_offset=*/kSectionIdBits, /*len=*/kDocumentIdBits);
  // Undo the document_id inversion.
  return InvertDocumentId(inverted_document_id);
}

SectionId BasicHit::section_id() const {
  return bit_util::BitfieldGet(value_, /*lsb_offset=*/0,
                               /*len=*/kSectionIdBits);
}

Hit::Hit(SectionId section_id, DocumentId document_id,
         Hit::TermFrequency term_frequency, bool is_in_prefix_section,
         bool is_prefix_hit)
    : term_frequency_(term_frequency) {
  // Values are stored so that when sorted, they appear in document_id
  // descending, section_id ascending, order. Also, all else being
  // equal, non-prefix hits sort before prefix hits. So inverted
  // document_id appears in the most significant bits, followed by
  // (uninverted) section_id.
  Value temp_value = 0;
  bit_util::BitfieldSet(InvertDocumentId(document_id),
                        kSectionIdBits + kNumFlags, kDocumentIdBits,
                        &temp_value);
  bit_util::BitfieldSet(section_id, kNumFlags, kSectionIdBits, &temp_value);
  bit_util::BitfieldSet(term_frequency != kDefaultTermFrequency,
                        kHasTermFrequency, /*len=*/1, &temp_value);
  bit_util::BitfieldSet(is_prefix_hit, kPrefixHit, /*len=*/1, &temp_value);
  bit_util::BitfieldSet(is_in_prefix_section, kInPrefixSection,
                        /*len=*/1, &temp_value);
  value_ = temp_value;
}

DocumentId Hit::document_id() const {
  DocumentId inverted_document_id = bit_util::BitfieldGet(
      value(), kSectionIdBits + kNumFlags, kDocumentIdBits);
  // Undo the document_id inversion.
  return InvertDocumentId(inverted_document_id);
}

SectionId Hit::section_id() const {
  return bit_util::BitfieldGet(value(), kNumFlags, kSectionIdBits);
}

bool Hit::has_term_frequency() const {
  return bit_util::BitfieldGet(value(), kHasTermFrequency, 1);
}

bool Hit::is_prefix_hit() const {
  return bit_util::BitfieldGet(value(), kPrefixHit, 1);
}

bool Hit::is_in_prefix_section() const {
  return bit_util::BitfieldGet(value(), kInPrefixSection, 1);
}

Hit Hit::TranslateHit(Hit old_hit, DocumentId new_document_id) {
  return Hit(old_hit.section_id(), new_document_id, old_hit.term_frequency(),
             old_hit.is_in_prefix_section(), old_hit.is_prefix_hit());
}

bool Hit::EqualsDocumentIdAndSectionId::operator()(const Hit& hit1,
                                                   const Hit& hit2) const {
  return (hit1.value() >> kNumFlags) == (hit2.value() >> kNumFlags);
}

}  // namespace lib
}  // namespace icing
