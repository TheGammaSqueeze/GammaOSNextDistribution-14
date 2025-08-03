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

#ifndef ICING_INDEX_HIT_DOC_HIT_INFO_H_
#define ICING_INDEX_HIT_DOC_HIT_INFO_H_

#include <limits>

#include "icing/index/hit/hit.h"
#include "icing/legacy/core/icing-packed-pod.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// DocHitInfo provides a collapsed view of all hits for a specific doc.
// Hits contain a document_id and section_id. The information in multiple hits
// is collapse into a DocHitInfo by providing a SectionIdMask of all sections
// that contained a hit for this term.
class DocHitInfo {
 public:
  explicit DocHitInfo(DocumentId document_id_in = kInvalidDocumentId,
                      SectionIdMask hit_section_ids_mask = kSectionIdMaskNone)
      : document_id_(document_id_in),
        hit_section_ids_mask_(hit_section_ids_mask) {}

  DocumentId document_id() const { return document_id_; }

  void set_document_id(DocumentId document_id) { document_id_ = document_id; }

  SectionIdMask hit_section_ids_mask() const { return hit_section_ids_mask_; }

  void set_hit_section_ids_mask(SectionIdMask section_id_mask) {
    hit_section_ids_mask_ = section_id_mask;
  }

  bool operator<(const DocHitInfo& other) const {
    if (document_id() != other.document_id()) {
      // Sort by document_id descending. This mirrors how the individual hits
      // that are collapsed into this DocHitInfo would sort with other hits -
      // document_ids are inverted when encoded in hits. Hits are encoded this
      // way because they are appended to posting lists and the most recent
      // value appended to a posting list must have the smallest encoded value
      // of any hit on the posting list.
      return document_id() > other.document_id();
    }
    return hit_section_ids_mask() < other.hit_section_ids_mask();
  }
  bool operator==(const DocHitInfo& other) const {
    return document_id_ == other.document_id_ &&
           hit_section_ids_mask_ == other.hit_section_ids_mask_;
  }

  // Updates the hit_section_ids_mask for the section, if necessary.
  void UpdateSection(SectionId section_id) {
    hit_section_ids_mask_ |= (UINT64_C(1) << section_id);
  }

  // Merges the sections of other into this. The hit_section_ids_masks are or'd.
  //
  // This does not affect the DocumentId of this or other. If callers care about
  // only merging sections for DocHitInfos with the same DocumentId, callers
  // should check this themselves.
  void MergeSectionsFrom(const SectionIdMask& other_hit_section_ids_mask) {
    hit_section_ids_mask_ |= other_hit_section_ids_mask;
  }

 private:
  DocumentId document_id_;
  SectionIdMask hit_section_ids_mask_;
} __attribute__((packed));
static_assert(sizeof(DocHitInfo) == 12, "");
// TODO(b/138991332) decide how to remove/replace all is_packed_pod assertions.
static_assert(icing_is_packed_pod<DocHitInfo>::value, "go/icing-ubsan");

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_HIT_DOC_HIT_INFO_H_
