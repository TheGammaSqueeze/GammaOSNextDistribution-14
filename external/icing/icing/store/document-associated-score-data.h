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

#ifndef ICING_STORE_DOCUMENT_ASSOCIATED_SCORE_DATA_H_
#define ICING_STORE_DOCUMENT_ASSOCIATED_SCORE_DATA_H_

#include <cstdint>
#include <type_traits>

#include "icing/legacy/core/icing-packed-pod.h"
#include "icing/store/corpus-id.h"

namespace icing {
namespace lib {

// This is the cache entity of document-associated scores. It contains scores
// that are related to the document itself. The ground-truth data is stored
// somewhere else. The cache includes:
// 1. Corpus Id.
// 2. Document score. It's defined in and passed from DocumentProto.score.
//    Positive values are required.
// 3. Document creation timestamp. Unix timestamp of when the document is
//    created and inserted into Icing.
// 4. Document length in number of tokens.
class DocumentAssociatedScoreData {
 public:
  explicit DocumentAssociatedScoreData(CorpusId corpus_id, int document_score,
                                       int64_t creation_timestamp_ms,
                                       int length_in_tokens = 0)
      : creation_timestamp_ms_(creation_timestamp_ms),
        corpus_id_(corpus_id),
        document_score_(document_score),
        length_in_tokens_(length_in_tokens) {}

  bool operator==(const DocumentAssociatedScoreData& other) const {
    return document_score_ == other.document_score() &&
           creation_timestamp_ms_ == other.creation_timestamp_ms() &&
           length_in_tokens_ == other.length_in_tokens() &&
           corpus_id_ == other.corpus_id();
  }

  CorpusId corpus_id() const { return corpus_id_; }

  int document_score() const { return document_score_; }

  int64_t creation_timestamp_ms() const { return creation_timestamp_ms_; }

  int length_in_tokens() const { return length_in_tokens_; }

 private:
  int64_t creation_timestamp_ms_;
  CorpusId corpus_id_;
  int document_score_;
  int length_in_tokens_;
} __attribute__((packed));

static_assert(sizeof(DocumentAssociatedScoreData) == 20,
              "Size of DocumentAssociatedScoreData should be 20");
static_assert(icing_is_packed_pod<DocumentAssociatedScoreData>::value,
              "go/icing-ubsan");

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_DOCUMENT_ASSOCIATED_SCORE_DATA_H_
