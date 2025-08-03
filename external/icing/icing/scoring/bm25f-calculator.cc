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

#include "icing/scoring/bm25f-calculator.h"

#include <cstdint>
#include <cstdlib>
#include <string>
#include <unordered_set>
#include <vector>

#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/corpus-associated-scoring-data.h"
#include "icing/store/corpus-id.h"
#include "icing/store/document-associated-score-data.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// Smoothing parameter, determines the relevance of higher term frequency
// documents. The higher k1, the higher their relevance. 1.2 is the default
// value in the BM25F literature and works well in most corpora.
constexpr float k1_ = 1.2f;
// Smoothing parameter, determines the weight of the document length on the
// final score. The higher b, the higher the influence of the document length.
// 0.7 is the default value in the BM25F literature and works well in most
// corpora.
constexpr float b_ = 0.7f;

// TODO(b/158603900): add tests for Bm25fCalculator
Bm25fCalculator::Bm25fCalculator(const DocumentStore* document_store,
                                 SectionWeights* section_weights,
                                 int64_t current_time_ms)
    : document_store_(document_store),
      section_weights_(*section_weights),
      current_time_ms_(current_time_ms) {}

// During initialization, Bm25fCalculator iterates through
// hit-iterators for each query term to pre-compute n(q_i) for each corpus under
// consideration.
void Bm25fCalculator::PrepareToScore(
    std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
        query_term_iterators) {
  Clear();
  TermId term_id = 0;
  for (auto& iter : *query_term_iterators) {
    const std::string& term = iter.first;
    if (term_id_map_.find(term) != term_id_map_.end()) {
      continue;
    }
    term_id_map_[term] = ++term_id;
    DocHitInfoIterator* term_it = iter.second.get();

    while (term_it->Advance().ok()) {
      auto status_or = document_store_->GetDocumentAssociatedScoreData(
          term_it->doc_hit_info().document_id());
      if (!status_or.ok()) {
        ICING_LOG(ERROR) << "No document score data";
        continue;
      }
      DocumentAssociatedScoreData data = status_or.ValueOrDie();
      CorpusId corpus_id = data.corpus_id();
      CorpusTermInfo corpus_term_info(corpus_id, term_id);
      corpus_nqi_map_[corpus_term_info.value]++;
    }
  }
}

void Bm25fCalculator::Clear() {
  term_id_map_.clear();
  corpus_avgdl_map_.clear();
  corpus_nqi_map_.clear();
  corpus_idf_map_.clear();
}

// Computes BM25F relevance score for query terms matched in document D.
//
// BM25F = \sum_i IDF(q_i) * tf(q_i, D)
//
// where IDF(q_i) is the Inverse Document Frequency (IDF) weight of the query
// term q_i in the corpus with document D, and tf(q_i, D) is the weighted and
// normalized term frequency of query term q_i in the document D.
float Bm25fCalculator::ComputeScore(const DocHitInfoIterator* query_it,
                                    const DocHitInfo& hit_info,
                                    double default_score) {
  auto status_or =
      document_store_->GetDocumentAssociatedScoreData(hit_info.document_id());
  if (!status_or.ok()) {
    ICING_LOG(ERROR) << "No document score data";
    return default_score;
  }
  DocumentAssociatedScoreData data = status_or.ValueOrDie();
  std::vector<TermMatchInfo> matched_terms_stats;
  query_it->PopulateMatchedTermsStats(&matched_terms_stats);

  float score = 0;
  for (const TermMatchInfo& term_match_info : matched_terms_stats) {
    float idf_weight =
        GetCorpusIdfWeightForTerm(term_match_info.term, data.corpus_id());
    float normalized_tf =
        ComputedNormalizedTermFrequency(term_match_info, hit_info, data);
    score += idf_weight * normalized_tf;
  }

  ICING_VLOG(1) << "BM25F: corpus_id:" << data.corpus_id()
                << " docid:" << hit_info.document_id() << " score:" << score;
  return score;
}

// Compute inverse document frequency (IDF) weight for query term in the given
// corpus, and cache it in the map.
//
//                    N - n(q_i) + 0.5
// IDF(q_i) = ln(1 + ------------------)
//                      n(q_i) + 0.5
//
// where N is the number of documents in the corpus, and n(q_i) is the number
// of documents in the corpus containing the query term q_i.
float Bm25fCalculator::GetCorpusIdfWeightForTerm(std::string_view term,
                                                 CorpusId corpus_id) {
  TermId term_id = term_id_map_[term];

  CorpusTermInfo corpus_term_info(corpus_id, term_id);
  auto iter = corpus_idf_map_.find(corpus_term_info.value);
  if (iter != corpus_idf_map_.end()) {
    return iter->second;
  }

  // First, figure out corpus scoring data.
  auto status_or = document_store_->GetCorpusAssociatedScoreData(corpus_id);
  if (!status_or.ok()) {
    ICING_LOG(ERROR) << "No scoring data for corpus [" << corpus_id << "]";
    return 0;
  }
  CorpusAssociatedScoreData csdata = status_or.ValueOrDie();

  uint32_t num_docs = csdata.num_docs();
  uint32_t nqi = corpus_nqi_map_[corpus_term_info.value];
  float idf =
      nqi != 0 ? log(1.0f + (num_docs - nqi + 0.5f) / (nqi + 0.5f)) : 0.0f;
  corpus_idf_map_.insert({corpus_term_info.value, idf});
  ICING_VLOG(1) << "corpus_id:" << corpus_id << " term:" << term
                << " N:" << num_docs << "nqi:" << nqi << " idf:" << idf;
  return idf;
}

// Get per corpus average document length and cache the result in the map.
// The average doc length is calculated as:
//
//                    total_tokens_in_corpus
// Avg Doc Length =  -------------------------
//                    num_docs_in_corpus + 1
float Bm25fCalculator::GetCorpusAvgDocLength(CorpusId corpus_id) {
  auto iter = corpus_avgdl_map_.find(corpus_id);
  if (iter != corpus_avgdl_map_.end()) {
    return iter->second;
  }

  // First, figure out corpus scoring data.
  auto status_or = document_store_->GetCorpusAssociatedScoreData(corpus_id);
  if (!status_or.ok()) {
    ICING_LOG(ERROR) << "No scoring data for corpus [" << corpus_id << "]";
    return 0;
  }
  CorpusAssociatedScoreData csdata = status_or.ValueOrDie();

  corpus_avgdl_map_[corpus_id] = csdata.average_doc_length_in_tokens();
  return csdata.average_doc_length_in_tokens();
}

// Computes normalized term frequency for query term q_i in document D.
//
//                            f(q_i, D) * (k1 + 1)
// Normalized TF = --------------------------------------------
//                 f(q_i, D) + k1 * (1 - b + b * |D| / avgdl)
//
// where f(q_i, D) is the frequency of query term q_i in document D,
// |D| is the #tokens in D, avgdl is the average document length in the corpus,
// k1 and b are smoothing parameters.
float Bm25fCalculator::ComputedNormalizedTermFrequency(
    const TermMatchInfo& term_match_info, const DocHitInfo& hit_info,
    const DocumentAssociatedScoreData& data) {
  uint32_t dl = data.length_in_tokens();
  float avgdl = GetCorpusAvgDocLength(data.corpus_id());
  float f_q = ComputeTermFrequencyForMatchedSections(
      data.corpus_id(), term_match_info, hit_info.document_id());
  float normalized_tf =
      f_q * (k1_ + 1) / (f_q + k1_ * (1 - b_ + b_ * dl / avgdl));

  ICING_VLOG(1) << "corpus_id:" << data.corpus_id()
                << " docid:" << hit_info.document_id() << " dl:" << dl
                << " avgdl:" << avgdl << " f_q:" << f_q
                << " norm_tf:" << normalized_tf;
  return normalized_tf;
}

float Bm25fCalculator::ComputeTermFrequencyForMatchedSections(
    CorpusId corpus_id, const TermMatchInfo& term_match_info,
    DocumentId document_id) const {
  float sum = 0.0f;
  SectionIdMask sections = term_match_info.section_ids_mask;
  SchemaTypeId schema_type_id = GetSchemaTypeId(document_id);

  while (sections != 0) {
    SectionId section_id = __builtin_ctzll(sections);
    sections &= ~(UINT64_C(1) << section_id);

    Hit::TermFrequency tf = term_match_info.term_frequencies[section_id];
    double weighted_tf = tf * section_weights_.GetNormalizedSectionWeight(
                                  schema_type_id, section_id);
    if (tf != Hit::kNoTermFrequency) {
      sum += weighted_tf;
    }
  }
  return sum;
}

SchemaTypeId Bm25fCalculator::GetSchemaTypeId(DocumentId document_id) const {
  auto filter_data_optional = document_store_->GetAliveDocumentFilterData(
      document_id, current_time_ms_);
  if (!filter_data_optional) {
    // This should never happen. The only failure case for
    // GetAliveDocumentFilterData is if the document_id is outside of the range
    // of allocated document_ids, which shouldn't be possible since we're
    // getting this document_id from the posting lists.
    ICING_LOG(WARNING) << "No document filter data for document ["
                       << document_id << "]";
    return kInvalidSchemaTypeId;
  }
  return filter_data_optional.value().schema_type_id();
}

}  // namespace lib
}  // namespace icing
