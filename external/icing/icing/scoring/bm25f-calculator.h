// Copyright (C) 2021 Google LLC
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

#ifndef ICING_SCORING_BM25F_CALCULATOR_H_
#define ICING_SCORING_BM25F_CALCULATOR_H_

#include <cstdint>
#include <string>
#include <unordered_set>
#include <vector>

#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/legacy/index/icing-bit-util.h"
#include "icing/scoring/section-weights.h"
#include "icing/store/corpus-id.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

// Bm25fCalculator encapsulates the logic to compute BM25F term-weight based
// ranking function.
//
// The formula to compute BM25F is as follows:
//
// BM25F = \sum_i IDF(q_i) * tf(q_i, D)
//
// where IDF(q_i) is the Inverse Document Frequency (IDF) weight of the query
// term q_i in the corpus with document D, and tf(q_i, D) is the weighted and
// normalized term frequency of query term q_i in the document D.
//
// IDF(q_i) is computed as follows:
//
//                     N - n(q_i) + 0.5
// IDF(q_i) = log(1 + ------------------)
//                       n(q_i) + 0.5
//
// where N is the number of documents in the corpus, and n(q_i) is the number
// of documents in the corpus containing the query term q_i.
//
// Lastly, tf(q_i, D) is computed as follows:
//
//                            f(q_i, D) * (k1 + 1)
// Normalized TF = --------------------------------------------
//                 f(q_i, D) + k1 * (1 - b + b * |D| / avgdl)
//
// where f(q_i, D) is the frequency of query term q_i in document D,
// |D| is the #tokens in D, avgdl is the average document length in the corpus,
// k1 and b are smoothing parameters.
//
// see: go/icing-bm25f
// see: glossary/bm25
class Bm25fCalculator {
 public:
  explicit Bm25fCalculator(const DocumentStore *document_store,
                           SectionWeights *section_weights,
                           int64_t current_time_ms);

  // Precompute and cache statistics relevant to BM25F.
  // Populates term_id_map_ and corpus_nqi_map_ for use while scoring other
  // results.
  // The query_term_iterators map is used to build the
  // std::unordered_map<std::string_view, TermId> term_id_map_. It must
  // outlive the bm25f-calculator otherwise the string_view key in term_id_map_,
  // used later to compute a document score, will be meaningless.
  void PrepareToScore(
      std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
          *query_term_iterators);

  // Compute the BM25F relevance score for the given hit, represented by
  // DocHitInfo.
  // The default score will be returned only when the scorer fails to find or
  // calculate a score for the document.
  float ComputeScore(const DocHitInfoIterator *query_it,
                     const DocHitInfo &hit_info, double default_score);

 private:
  // Compact ID for each query term.
  using TermId = uint16_t;

  // Compact representation of <CorpusId, TermId> for use as a key in a
  // hash_map.
  struct CorpusTermInfo {
    // Layout bits: 16 bit CorpusId + 16 bit TermId
    using Value = uint32_t;

    Value value;

    static constexpr int kCorpusIdBits = sizeof(CorpusId);
    static constexpr int kTermIdBits = sizeof(TermId);

    explicit CorpusTermInfo(CorpusId corpus_id, TermId term_id) : value(0) {
      BITFIELD_OR(value, kTermIdBits, kCorpusIdBits,
                  static_cast<uint64_t>(corpus_id));
      BITFIELD_OR(value, 0, kTermIdBits, term_id);
    }

    bool operator==(const CorpusTermInfo &other) const {
      return value == other.value;
    }
  };

  // Returns idf weight for the term and provided corpus.
  float GetCorpusIdfWeightForTerm(std::string_view term, CorpusId corpus_id);

  // Returns the average document length for the corpus. The average is
  // calculated as the sum of tokens in the corpus' documents over the total
  // number of documents plus one.
  float GetCorpusAvgDocLength(CorpusId corpus_id);

  // Returns the normalized term frequency for the term match and document hit.
  // This normalizes the term frequency by applying smoothing parameters and
  // factoring document length.
  float ComputedNormalizedTermFrequency(
      const TermMatchInfo &term_match_info, const DocHitInfo &hit_info,
      const DocumentAssociatedScoreData &data);

  // Returns the weighted term frequency for the term match and document. For
  // each section the term is present, we scale the term frequency by its
  // section weight. We return the sum of the weighted term frequencies over all
  // sections.
  float ComputeTermFrequencyForMatchedSections(
      CorpusId corpus_id, const TermMatchInfo &term_match_info,
      DocumentId document_id) const;

  // Returns the schema type id for the document by retrieving it from the
  // DocumentFilterData.
  SchemaTypeId GetSchemaTypeId(DocumentId document_id) const;

  // Clears cached scoring data and prepares the calculator for a new scoring
  // run.
  void Clear();

  const DocumentStore *document_store_;  // Does not own.

  // Used for accessing normalized section weights when computing the weighted
  // term frequency.
  SectionWeights &section_weights_;

  // Map from query term to compact term ID.
  // Necessary as a key to the other maps.
  // The use of the string_view as key here means that the query_term_iterators
  // map must outlive the bm25f
  std::unordered_map<std::string_view, TermId> term_id_map_;

  // Map from corpus ID to average document length (avgdl).
  // Necessary to calculate the normalized term frequency.
  // This information is cached in the DocumentStore::CorpusScoreCache
  std::unordered_map<CorpusId, float> corpus_avgdl_map_;
  // Map from <corpus ID, term ID> to number of documents containing term q_i,
  // called n(q_i).
  // Necessary to calculate IDF(q_i) (inverse document frequency).
  // This information must be calculated by iterating through the hits for these
  // terms.
  std::unordered_map<CorpusTermInfo::Value, uint32_t> corpus_nqi_map_;

  // Map from <corpus ID, term ID> to IDF(q_i) (inverse document frequency).
  std::unordered_map<CorpusTermInfo::Value, float> corpus_idf_map_;

  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_BM25F_CALCULATOR_H_
