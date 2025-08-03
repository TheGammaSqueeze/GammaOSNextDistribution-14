// Copyright (C) 2023 Google LLC
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

#include "icing/join/doc-join-info.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::IsFalse;
using ::testing::IsTrue;

static constexpr DocumentId kSomeDocumentId = 24;
static constexpr JoinablePropertyId kSomeJoinablePropertyId = 5;

TEST(DocJoinInfoTest, Accessors) {
  DocJoinInfo doc_join_info(kSomeDocumentId, kSomeJoinablePropertyId);
  EXPECT_THAT(doc_join_info.document_id(), Eq(kSomeDocumentId));
  EXPECT_THAT(doc_join_info.joinable_property_id(),
              Eq(kSomeJoinablePropertyId));
}

TEST(DocJoinInfoTest, Invalid) {
  DocJoinInfo default_invalid;
  EXPECT_THAT(default_invalid.is_valid(), IsFalse());

  // Also make sure the invalid DocJoinInfo contains an invalid document id.
  EXPECT_THAT(default_invalid.document_id(), Eq(kInvalidDocumentId));
  EXPECT_THAT(default_invalid.joinable_property_id(),
              Eq(kMaxJoinablePropertyId));
}

TEST(DocJoinInfoTest, Valid) {
  DocJoinInfo maximum_document_id_info(kMaxDocumentId, kSomeJoinablePropertyId);
  EXPECT_THAT(maximum_document_id_info.is_valid(), IsTrue());
  EXPECT_THAT(maximum_document_id_info.document_id(), Eq(kMaxDocumentId));
  EXPECT_THAT(maximum_document_id_info.joinable_property_id(),
              Eq(kSomeJoinablePropertyId));

  DocJoinInfo maximum_joinable_property_id_info(kSomeDocumentId,
                                                kMaxJoinablePropertyId);
  EXPECT_THAT(maximum_joinable_property_id_info.is_valid(), IsTrue());
  EXPECT_THAT(maximum_joinable_property_id_info.document_id(),
              Eq(kSomeDocumentId));
  EXPECT_THAT(maximum_joinable_property_id_info.joinable_property_id(),
              Eq(kMaxJoinablePropertyId));

  DocJoinInfo minimum_document_id_info(kMinDocumentId, kSomeJoinablePropertyId);
  EXPECT_THAT(minimum_document_id_info.is_valid(), IsTrue());
  EXPECT_THAT(minimum_document_id_info.document_id(), Eq(kMinDocumentId));
  EXPECT_THAT(minimum_document_id_info.joinable_property_id(),
              Eq(kSomeJoinablePropertyId));

  DocJoinInfo minimum_joinable_property_id_info(kSomeDocumentId,
                                                kMinJoinablePropertyId);
  EXPECT_THAT(minimum_joinable_property_id_info.is_valid(), IsTrue());
  EXPECT_THAT(minimum_joinable_property_id_info.document_id(),
              Eq(kSomeDocumentId));
  EXPECT_THAT(minimum_joinable_property_id_info.joinable_property_id(),
              Eq(kMinJoinablePropertyId));

  DocJoinInfo all_maximum_info(kMaxDocumentId, kMaxJoinablePropertyId);
  EXPECT_THAT(all_maximum_info.is_valid(), IsTrue());
  EXPECT_THAT(all_maximum_info.document_id(), Eq(kMaxDocumentId));
  EXPECT_THAT(all_maximum_info.joinable_property_id(),
              Eq(kMaxJoinablePropertyId));

  DocJoinInfo all_minimum_info(kMinDocumentId, kMinJoinablePropertyId);
  EXPECT_THAT(all_minimum_info.is_valid(), IsTrue());
  EXPECT_THAT(all_minimum_info.document_id(), Eq(kMinDocumentId));
  EXPECT_THAT(all_minimum_info.joinable_property_id(),
              Eq(kMinJoinablePropertyId));
}

}  // namespace

}  // namespace lib
}  // namespace icing
