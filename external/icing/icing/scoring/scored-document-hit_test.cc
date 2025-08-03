// Copyright (C) 2022 Google LLC
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

#include "icing/scoring/scored-document-hit.h"

#include <cstdint>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::DoubleEq;
using ::testing::IsEmpty;

TEST(ScoredDocumentHitTest, ScoredDocumentHitConvertToJoinedScoredDocumentHit) {
  ScoredDocumentHit::Converter converter;

  double score = 2.0;
  ScoredDocumentHit scored_document_hit(/*document_id=*/5,
                                        /*section_id_mask=*/49, score);

  JoinedScoredDocumentHit joined_scored_document_hit =
      converter(ScoredDocumentHit(scored_document_hit));
  EXPECT_THAT(joined_scored_document_hit.final_score(), DoubleEq(score));
  EXPECT_THAT(joined_scored_document_hit.parent_scored_document_hit(),
              EqualsScoredDocumentHit(scored_document_hit));
  EXPECT_THAT(joined_scored_document_hit.child_scored_document_hits(),
              IsEmpty());
}

TEST(ScoredDocumentHitTest,
     JoinedScoredDocumentHitConvertToJoinedScoredDocumentHit) {
  JoinedScoredDocumentHit::Converter converter;

  ScoredDocumentHit parent_scored_document_hit(/*document_id=*/5,
                                               /*section_id_mask=*/49,
                                               /*score=*/1.0);
  std::vector<ScoredDocumentHit> child_scored_document_hits{
      ScoredDocumentHit(/*document_id=*/1,
                        /*section_id_mask=*/1,
                        /*score=*/2.0),
      ScoredDocumentHit(/*document_id=*/2,
                        /*section_id_mask=*/2,
                        /*score=*/3.0),
      ScoredDocumentHit(/*document_id=*/3,
                        /*section_id_mask=*/3,
                        /*score=*/4.0)};

  JoinedScoredDocumentHit joined_scored_document_hit(
      /*final_score=*/12345.6789, std::move(parent_scored_document_hit),
      std::move(child_scored_document_hits));
  EXPECT_THAT(converter(JoinedScoredDocumentHit(joined_scored_document_hit)),
              EqualsJoinedScoredDocumentHit(joined_scored_document_hit));
}

}  // namespace

}  // namespace lib
}  // namespace icing
