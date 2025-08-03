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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_H_

#include <array>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// Data structure that maps a single matched query term to its section mask
// and the list of term frequencies.
// TODO(b/158603837): add stat on whether the matched terms are prefix matched
// or not. This information will be used to boost exact match.
struct TermMatchInfo {
  std::string_view term;
  // SectionIdMask associated to the term.
  SectionIdMask section_ids_mask;
  // Array with fixed size kMaxSectionId. For every section id, i.e.
  // vector index, it stores the term frequency of the term.
  std::array<Hit::TermFrequency, kTotalNumSections> term_frequencies;

  explicit TermMatchInfo(
      std::string_view term, SectionIdMask section_ids_mask,
      std::array<Hit::TermFrequency, kTotalNumSections> term_frequencies)
      : term(term),
        section_ids_mask(section_ids_mask),
        term_frequencies(std::move(term_frequencies)) {}
};

// Iterator over DocHitInfos (collapsed Hits) in REVERSE document_id order.
//
// NOTE: You must call Advance() before calling hit_info().
//
// Example:
// DocHitInfoIterator itr = GetIterator(...);
// while (itr.Advance()) {
//   HandleDocHitInfo(itr.hit_info());
// }
class DocHitInfoIterator {
 public:
  using ChildrenMapper = std::function<std::unique_ptr<DocHitInfoIterator>(
      std::unique_ptr<DocHitInfoIterator>)>;

  // CallStats is a wrapper class of all stats to collect among all levels of
  // the DocHitInfoIterator tree. Mostly the internal nodes will aggregate the
  // number of all leaf nodes, while the leaf nodes will return the actual
  // numbers.
  struct CallStats {
    // The number of times Advance() was called on the leaf node for term lite
    // index.
    // - Leaf nodes:
    //   - DocHitInfoIteratorTermLite should maintain and set it correctly.
    //   - Others should set it 0.
    // - Internal nodes: should aggregate values from all children.
    int32_t num_leaf_advance_calls_lite_index;

    // The number of times Advance() was called on the leaf node for term main
    // index.
    // - Leaf nodes:
    //   - DocHitInfoIteratorTermMain should maintain and set it correctly.
    //   - Others should set it 0.
    // - Internal nodes: should aggregate values from all children.
    int32_t num_leaf_advance_calls_main_index;

    // The number of times Advance() was called on the leaf node for integer
    // index.
    // - Leaf nodes:
    //   - DocHitInfoIteratorNumeric should maintain and set it correctly.
    //   - Others should set it 0.
    // - Internal nodes: should aggregate values from all children.
    int32_t num_leaf_advance_calls_integer_index;

    // The number of times Advance() was called on the leaf node without reading
    // any hits from index. Usually it is a special field for
    // DocHitInfoIteratorAllDocumentId.
    // - Leaf nodes:
    //   - DocHitInfoIteratorAllDocumentId should maintain and set it correctly.
    //   - Others should set it 0.
    // - Internal nodes: should aggregate values from all children.
    int32_t num_leaf_advance_calls_no_index;

    // The number of flash index blocks that have been read as a result of
    // operations on this object.
    // - Leaf nodes: should maintain and set it correctly for all child classes
    //   involving flash index block access.
    // - Internal nodes: should aggregate values from all children.
    int32_t num_blocks_inspected;

    explicit CallStats()
        : CallStats(/*num_leaf_advance_calls_lite_index_in=*/0,
                    /*num_leaf_advance_calls_main_index_in=*/0,
                    /*num_leaf_advance_calls_integer_index_in=*/0,
                    /*num_leaf_advance_calls_no_index_in=*/0,
                    /*num_blocks_inspected_in=*/0) {}

    explicit CallStats(int32_t num_leaf_advance_calls_lite_index_in,
                       int32_t num_leaf_advance_calls_main_index_in,
                       int32_t num_leaf_advance_calls_integer_index_in,
                       int32_t num_leaf_advance_calls_no_index_in,
                       int32_t num_blocks_inspected_in)
        : num_leaf_advance_calls_lite_index(
              num_leaf_advance_calls_lite_index_in),
          num_leaf_advance_calls_main_index(
              num_leaf_advance_calls_main_index_in),
          num_leaf_advance_calls_integer_index(
              num_leaf_advance_calls_integer_index_in),
          num_leaf_advance_calls_no_index(num_leaf_advance_calls_no_index_in),
          num_blocks_inspected(num_blocks_inspected_in) {}

    int32_t num_leaf_advance_calls() const {
      return num_leaf_advance_calls_lite_index +
             num_leaf_advance_calls_main_index +
             num_leaf_advance_calls_integer_index +
             num_leaf_advance_calls_no_index;
    }

    bool operator==(const CallStats& other) const {
      return num_leaf_advance_calls_lite_index ==
                 other.num_leaf_advance_calls_lite_index &&
             num_leaf_advance_calls_main_index ==
                 other.num_leaf_advance_calls_main_index &&
             num_leaf_advance_calls_integer_index ==
                 other.num_leaf_advance_calls_integer_index &&
             num_leaf_advance_calls_no_index ==
                 other.num_leaf_advance_calls_no_index &&
             num_blocks_inspected == other.num_blocks_inspected;
    }

    CallStats operator+(const CallStats& other) const {
      return CallStats(num_leaf_advance_calls_lite_index +
                           other.num_leaf_advance_calls_lite_index,
                       num_leaf_advance_calls_main_index +
                           other.num_leaf_advance_calls_main_index,
                       num_leaf_advance_calls_integer_index +
                           other.num_leaf_advance_calls_integer_index,
                       num_leaf_advance_calls_no_index +
                           other.num_leaf_advance_calls_no_index,
                       num_blocks_inspected + other.num_blocks_inspected);
    }

    CallStats& operator+=(const CallStats& other) {
      *this = *this + other;
      return *this;
    }
  };

  struct TrimmedNode {
    // the query results which we should only search for suggestion in these
    // documents.
    std::unique_ptr<DocHitInfoIterator> iterator_;
    // term of the trimmed node which we need to generate suggested strings.
    std::string term_;
    // the string in the query which indicates the target section we should
    // search for suggestions.
    std::string target_section_;
    // the start index of the current term in the given search query.
    int term_start_index_;
    // The length of the given unnormalized term in the search query
    int unnormalized_term_length_;

    TrimmedNode(std::unique_ptr<DocHitInfoIterator> iterator, std::string term,
                int term_start_index, int unnormalized_term_length)
        : iterator_(std::move(iterator)),
          term_(term),
          target_section_(""),
          term_start_index_(term_start_index),
          unnormalized_term_length_(unnormalized_term_length) {}
  };

  // Trim the rightmost iterator of the iterator tree.
  // This is to support search suggestions for the last term which is the
  // right-most node of the root iterator tree. Only support trim the right-most
  // node on the AND, AND_NARY, OR, OR_NARY, OR_LEAF, Filter, and the
  // property-in-schema-check iterator.
  //
  // After calling this method, this iterator is no longer usable. Please use
  // the returned iterator.
  // Returns:
  //   the new iterator without the right-most child, if was able to trim the
  //   right-most node.
  //   nullptr if the current iterator should be trimmed.
  //   INVALID_ARGUMENT if the right-most node is not suppose to be trimmed.
  virtual libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && = 0;

  // Map all direct children of this iterator according to the passed mapper.
  virtual void MapChildren(const ChildrenMapper& mapper) = 0;

  virtual bool is_leaf() { return false; }

  virtual ~DocHitInfoIterator() = default;

  // Returns:
  //   OK if was able to advance to a new document_id.
  //   INVALID_ARGUMENT if there are less than 2 iterators for an AND/OR
  //       iterator
  //   RESOUCE_EXHAUSTED if we've run out of document_ids to iterate over
  virtual libtextclassifier3::Status Advance() = 0;

  // Returns the DocHitInfo that the iterator is currently at. The DocHitInfo
  // will have a kInvalidDocumentId if Advance() was not called after
  // construction or if Advance returned an error.
  const DocHitInfo& doc_hit_info() const { return doc_hit_info_; }

  // Returns CallStats of the DocHitInfoIterator tree.
  virtual CallStats GetCallStats() const = 0;

  // A string representing the iterator.
  virtual std::string ToString() const = 0;

  // For the last hit docid, retrieves all the matched query terms and other
  // stats, see TermMatchInfo.
  // filtering_section_mask filters the matching sections and should be set only
  // by DocHitInfoIteratorSectionRestrict.
  // If Advance() wasn't called after construction, Advance() returned false or
  // the concrete HitIterator didn't override this method, the vectors aren't
  // populated.
  virtual void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const {}

 protected:
  DocHitInfo doc_hit_info_;

  // Helper function to advance the given iterator to at most the given
  // document_id.
  libtextclassifier3::StatusOr<DocumentId> AdvanceTo(DocHitInfoIterator* it,
                                                     DocumentId document_id) {
    while (it->Advance().ok()) {
      if (it->doc_hit_info().document_id() <= document_id) {
        return it->doc_hit_info().document_id();
      }
    }

    // Didn't find anything for the other iterator, reset to invalid values and
    // return.
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
};

// A leaf node is a term node or a chain of section restriction node applied on
// a term node.
class DocHitInfoLeafIterator : public DocHitInfoIterator {
 public:
  bool is_leaf() override { return true; }

  // Calling MapChildren on leaf node does not make sense, and will do nothing.
  void MapChildren(const ChildrenMapper& mapper) override {}
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_H_
