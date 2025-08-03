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

#include "icing/index/hit/doc-hit-info.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

using ::testing::ElementsAre;
using ::testing::Ne;

TEST(DocHitInfoTest, Comparison) {
  constexpr DocumentId kDocumentId = 1;
  DocHitInfo info(kDocumentId);
  info.UpdateSection(1);

  constexpr DocumentId kHighDocumentId = 15;
  DocHitInfo high_document_id_info(kHighDocumentId);
  high_document_id_info.UpdateSection(1);

  DocHitInfo high_section_id_info(kDocumentId);
  high_section_id_info.UpdateSection(1);
  high_section_id_info.UpdateSection(6);

  std::vector<DocHitInfo> infos{info, high_document_id_info,
                                high_section_id_info};
  std::sort(infos.begin(), infos.end());
  EXPECT_THAT(infos,
              ElementsAre(high_document_id_info, info, high_section_id_info));

  // There are no requirements for how DocHitInfos with the same DocumentIds and
  // hit masks will compare, but they must not be equal.
  DocHitInfo different_term_frequency_info(kDocumentId);
  different_term_frequency_info.UpdateSection(2);
  EXPECT_THAT(info < different_term_frequency_info,
              Ne(different_term_frequency_info < info));
}

}  // namespace lib
}  // namespace icing
