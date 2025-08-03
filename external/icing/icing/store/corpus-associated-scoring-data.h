// Copyright (C) 2020 Google LLC
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

#ifndef ICING_STORE_TYPE_NAMESPACE_ASSOCIATED_SCORING_DATA_H_
#define ICING_STORE_TYPE_NAMESPACE_ASSOCIATED_SCORING_DATA_H_

#include <cstdint>
#include <limits>
#include <type_traits>

#include "icing/legacy/core/icing-packed-pod.h"

namespace icing {
namespace lib {

// This is the cache entity of corpus-associated scores. The ground-truth data
// is stored somewhere else. The cache includes:
// 1. Number of documents contained in the corpus.
//    Positive values are required.
// 2. The sum of the documents' lengths, in number of tokens.
class CorpusAssociatedScoreData {
 public:
  explicit CorpusAssociatedScoreData(int num_docs = 0,
                                     int64_t sum_length_in_tokens = 0)
      : sum_length_in_tokens_(sum_length_in_tokens), num_docs_(num_docs) {}

  bool operator==(const CorpusAssociatedScoreData& other) const {
    return num_docs_ == other.num_docs() &&
           sum_length_in_tokens_ == other.sum_length_in_tokens();
  }

  uint32_t num_docs() const { return num_docs_; }
  void set_num_docs(uint32_t val) { num_docs_ = val; }

  uint64_t sum_length_in_tokens() const { return sum_length_in_tokens_; }
  void set_sum_length_in_tokens(uint64_t val) { sum_length_in_tokens_ = val; }

  float average_doc_length_in_tokens() const {
    return sum_length_in_tokens_ / (1.0f + num_docs_);
  }

  // Adds a new document.
  // Adds the document's length to the total length of the corpus,
  // sum_length_in_tokens_.
  void AddDocument(uint32_t doc_length_in_tokens) {
    ++num_docs_;
    sum_length_in_tokens_ =
        (std::numeric_limits<int>::max() - doc_length_in_tokens <
         sum_length_in_tokens_)
            ? std::numeric_limits<int>::max()
            : sum_length_in_tokens_ + doc_length_in_tokens;
  }

 private:
  // The sum total of the length of all documents in the corpus.
  int sum_length_in_tokens_;
  int num_docs_;
} __attribute__((packed));

static_assert(sizeof(CorpusAssociatedScoreData) == 8,
              "Size of CorpusAssociatedScoreData should be 8");
static_assert(icing_is_packed_pod<CorpusAssociatedScoreData>::value,
              "go/icing-ubsan");

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_TYPE_NAMESPACE_ASSOCIATED_SCORING_DATA_H_
