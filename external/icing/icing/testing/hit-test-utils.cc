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

#include "icing/testing/hit-test-utils.h"

namespace icing {
namespace lib {

// Returns a hit that has a delta of desired_byte_length from last_hit.
Hit CreateHit(Hit last_hit, int desired_byte_length) {
  Hit hit = (last_hit.section_id() == kMinSectionId)
                ? Hit(kMaxSectionId, last_hit.document_id() + 1,
                      last_hit.term_frequency())
                : Hit(last_hit.section_id() - 1, last_hit.document_id(),
                      last_hit.term_frequency());
  uint8_t buf[5];
  while (VarInt::Encode(last_hit.value() - hit.value(), buf) <
         desired_byte_length) {
    hit = (hit.section_id() == kMinSectionId)
              ? Hit(kMaxSectionId, hit.document_id() + 1, hit.term_frequency())
              : Hit(hit.section_id() - 1, hit.document_id(),
                    hit.term_frequency());
  }
  return hit;
}

// Returns a vector of num_hits Hits with the first hit starting at start_docid
// and with 1-byte deltas.
std::vector<Hit> CreateHits(DocumentId start_docid, int num_hits,
                            int desired_byte_length) {
  std::vector<Hit> hits;
  if (num_hits < 1) {
    return hits;
  }
  hits.push_back(Hit(/*section_id=*/1, /*document_id=*/start_docid,
                     Hit::kDefaultTermFrequency));
  while (hits.size() < num_hits) {
    hits.push_back(CreateHit(hits.back(), desired_byte_length));
  }
  return hits;
}

std::vector<Hit> CreateHits(int num_hits, int desired_byte_length) {
  return CreateHits(/*start_docid=*/0, num_hits, desired_byte_length);
}

}  // namespace lib
}  // namespace icing
