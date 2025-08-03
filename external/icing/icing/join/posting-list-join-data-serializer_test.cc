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

#include "icing/join/posting-list-join-data-serializer.h"

#include <algorithm>
#include <iterator>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/join/document-id-to-join-info.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/testing/common-matchers.h"

using testing::ElementsAre;
using testing::ElementsAreArray;
using testing::Eq;
using testing::IsEmpty;
using testing::SizeIs;

namespace icing {
namespace lib {

namespace {

TEST(PostingListJoinDataSerializerTest, GetMinPostingListSizeToFitNotNull) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size =
      2551 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/2))),
      IsOk());
  EXPECT_THAT(
      serializer.GetMinPostingListSizeToFit(&pl_used),
      Eq(2 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));

  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/1, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/5))),
      IsOk());
  EXPECT_THAT(
      serializer.GetMinPostingListSizeToFit(&pl_used),
      Eq(3 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
}

TEST(PostingListJoinDataSerializerTest, GetMinPostingListSizeToFitAlmostFull) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 3 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/2))),
      IsOk());
  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/1, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/5))),
      IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used), Eq(size));
}

TEST(PostingListJoinDataSerializerTest, GetMinPostingListSizeToFitFull) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 3 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/2))),
      IsOk());
  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/1, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/5))),
      IsOk());
  ASSERT_THAT(
      serializer.PrependData(
          &pl_used,
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/2, NamespaceFingerprintIdentifier(
                                     /*namespace_id=*/1, /*fingerprint=*/10))),
      IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used), Eq(size));
}

TEST(PostingListJoinDataSerializerTest, PrependDataNotFull) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size =
      2551 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // Make used.
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data0(
      /*document_id=*/0,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2));
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  // Size = sizeof(uncompressed data0)
  int expected_size =
      sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(ElementsAre(data0)));

  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/5));
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  // Size = sizeof(uncompressed data1)
  //        + sizeof(uncompressed data0)
  expected_size += sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data2(
      /*document_id=*/2, NamespaceFingerprintIdentifier(
                             /*namespace_id=*/1, /*fingerprint=*/10));
  EXPECT_THAT(serializer.PrependData(&pl_used, data2), IsOk());
  // Size = sizeof(uncompressed data2)
  //        + sizeof(uncompressed data1)
  //        + sizeof(uncompressed data0)
  expected_size += sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data2, data1, data0)));
}

TEST(PostingListJoinDataSerializerTest, PrependDataAlmostFull) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 4 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // Fill up the compressed region.
  // Transitions:
  // Adding data0: EMPTY -> NOT_FULL
  // Adding data1: NOT_FULL -> NOT_FULL
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data0(
      /*document_id=*/0,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2));
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/5));
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  int expected_size =
      2 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  // Add one more data to transition NOT_FULL -> ALMOST_FULL
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data2(
      /*document_id=*/2, NamespaceFingerprintIdentifier(
                             /*namespace_id=*/1, /*fingerprint=*/10));
  EXPECT_THAT(serializer.PrependData(&pl_used, data2), IsOk());
  expected_size =
      3 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data2, data1, data0)));

  // Add one more data to transition ALMOST_FULL -> FULL
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data3(
      /*document_id=*/3, NamespaceFingerprintIdentifier(
                             /*namespace_id=*/1, /*fingerprint=*/0));
  EXPECT_THAT(serializer.PrependData(&pl_used, data3), IsOk());
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data3, data2, data1, data0)));

  // The posting list is FULL. Adding another data should fail.
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data4(
      /*document_id=*/4, NamespaceFingerprintIdentifier(
                             /*namespace_id=*/0, /*fingerprint=*/1234));
  EXPECT_THAT(serializer.PrependData(&pl_used, data4),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListJoinDataSerializerTest, PrependSmallerDataShouldFail) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 4 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data(
      /*document_id=*/100,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2));
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> smaller_data(
      /*document_id=*/99,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2));

  // NOT_FULL -> NOT_FULL
  ASSERT_THAT(serializer.PrependData(&pl_used, data), IsOk());
  EXPECT_THAT(serializer.PrependData(&pl_used, smaller_data),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // NOT_FULL -> ALMOST_FULL
  ASSERT_THAT(serializer.PrependData(&pl_used, data), IsOk());
  EXPECT_THAT(serializer.PrependData(&pl_used, smaller_data),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // ALMOST_FULL -> FULL
  ASSERT_THAT(serializer.PrependData(&pl_used, data), IsOk());
  EXPECT_THAT(serializer.PrependData(&pl_used, smaller_data),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(PostingListJoinDataSerializerTest, PrependDataPostingListUsedMinSize) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // PL State: EMPTY
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(0));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(IsEmpty()));

  // Add a data. PL should shift to ALMOST_FULL state
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data0(
      /*document_id=*/0,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2));
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  // Size = sizeof(uncompressed data0)
  int expected_size =
      sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(ElementsAre(data0)));

  // Add another data. PL should shift to FULL state.
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/5));
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  // Size = sizeof(uncompressed data1) + sizeof(uncompressed data0)
  expected_size += sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  // The posting list is FULL. Adding another data should fail.
  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier> data2(
      /*document_id=*/2, NamespaceFingerprintIdentifier(
                             /*namespace_id=*/1, /*fingerprint=*/10));
  EXPECT_THAT(serializer.PrependData(&pl_used, data2),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListJoinDataSerializerTest, PrependDataArrayDoNotKeepPrepended) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 6 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_in;
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_pushed;

  // Add 3 data. The PL is in the empty state and should be able to fit all 3
  // data without issue, transitioning the PL from EMPTY -> NOT_FULL.
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/0,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/5)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/2,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/10)));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 2 data. The PL should transition from NOT_FULL to ALMOST_FULL.
  data_in.clear();
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/3,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/0)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/4, NamespaceFingerprintIdentifier(/*namespace_id=*/0,
                                                        /*fingerprint=*/1234)));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 2 data. The PL should remain ALMOST_FULL since the remaining space can
  // only fit 1 data.
  data_in.clear();
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/5, NamespaceFingerprintIdentifier(/*namespace_id=*/2,
                                                        /*fingerprint=*/99)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/6, NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                                        /*fingerprint=*/63)));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(0));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 1 data. The PL should transition from ALMOST_FULL to FULL.
  data_in.pop_back();
  ASSERT_THAT(data_in, SizeIs(1));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));
}

TEST(PostingListJoinDataSerializerTest, PrependDataArrayKeepPrepended) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 6 * sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_in;
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_pushed;

  // Add 3 data. The PL is in the empty state and should be able to fit all 3
  // data without issue, transitioning the PL from EMPTY -> NOT_FULL.
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/0,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/2)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/5)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/2,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/10)));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/true),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 4 data. The PL should prepend 3 data and transition from NOT_FULL to
  // FULL.
  data_in.clear();
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/3,
      NamespaceFingerprintIdentifier(/*namespace_id=*/1, /*fingerprint=*/0)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/4, NamespaceFingerprintIdentifier(/*namespace_id=*/0,
                                                        /*fingerprint=*/1234)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/5, NamespaceFingerprintIdentifier(/*namespace_id=*/2,
                                                        /*fingerprint=*/99)));
  data_in.push_back(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
      /*document_id=*/6, NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                                        /*fingerprint=*/63)));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/true),
      IsOkAndHolds(3));
  data_in.pop_back();
  ASSERT_THAT(data_in, SizeIs(3));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() *
                 sizeof(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));
}

TEST(PostingListJoinDataSerializerTest, MoveFrom) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr1 =
      {DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/0, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/2)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/1, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/5))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used1, data_arr1.data(), data_arr1.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr1.size()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr2 =
      {DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/2, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/10)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/3, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/0)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/4,
           NamespaceFingerprintIdentifier(/*namespace_id=*/0,
                                          /*fingerprint=*/1234)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/5,
           NamespaceFingerprintIdentifier(/*namespace_id=*/2,
                                          /*fingerprint=*/99))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used2, data_arr2.data(), data_arr2.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr2.size()));

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1),
              IsOk());
  EXPECT_THAT(
      serializer.GetData(&pl_used2),
      IsOkAndHolds(ElementsAreArray(data_arr1.rbegin(), data_arr1.rend())));
  EXPECT_THAT(serializer.GetData(&pl_used1), IsOkAndHolds(IsEmpty()));
}

TEST(PostingListJoinDataSerializerTest, MoveToNullReturnsFailedPrecondition) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr = {
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/0, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/1, /*fingerprint=*/2)),
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/1, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/1, /*fingerprint=*/5))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used, data_arr.data(), data_arr.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr.size()));

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used, /*src=*/nullptr),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_arr.rbegin(), data_arr.rend())));

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/nullptr, /*src=*/&pl_used),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_arr.rbegin(), data_arr.rend())));
}

TEST(PostingListJoinDataSerializerTest, MoveToPostingListTooSmall) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size1 = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size1));
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr1 =
      {DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/0, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/2)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/1, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/5)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/2, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/10)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/3, NamespaceFingerprintIdentifier(
                                  /*namespace_id=*/1, /*fingerprint=*/0)),
       DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
           /*document_id=*/4,
           NamespaceFingerprintIdentifier(/*namespace_id=*/0,
                                          /*fingerprint=*/1234))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used1, data_arr1.data(), data_arr1.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr1.size()));

  int size2 = serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size2));
  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr2 =
      {DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/5, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/2, /*fingerprint=*/99))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used2, data_arr2.data(), data_arr2.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr2.size()));

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(
      serializer.GetData(&pl_used1),
      IsOkAndHolds(ElementsAreArray(data_arr1.rbegin(), data_arr1.rend())));
  EXPECT_THAT(
      serializer.GetData(&pl_used2),
      IsOkAndHolds(ElementsAreArray(data_arr2.rbegin(), data_arr2.rend())));
}

TEST(PostingListJoinDataSerializerTest, PopFrontData) {
  PostingListJoinDataSerializer<
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>>
      serializer;

  int size = 2 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>> data_arr = {
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/0, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/1, /*fingerprint=*/2)),
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/1, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/1, /*fingerprint=*/5)),
      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
          /*document_id=*/2, NamespaceFingerprintIdentifier(
                                 /*namespace_id=*/1, /*fingerprint=*/10))};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used, data_arr.data(), data_arr.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr.size()));
  ASSERT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_arr.rbegin(), data_arr.rend())));

  // Now, pop the last data. The posting list should contain the first three
  // data.
  EXPECT_THAT(serializer.PopFrontData(&pl_used, /*num_data=*/1), IsOk());
  data_arr.pop_back();
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_arr.rbegin(), data_arr.rend())));
}

}  // namespace

}  // namespace lib
}  // namespace icing
