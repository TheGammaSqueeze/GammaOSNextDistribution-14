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

#include "icing/index/numeric/posting-list-integer-index-serializer.h"

#include <memory>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/testing/common-matchers.h"

using testing::ElementsAre;
using testing::ElementsAreArray;
using testing::Eq;
using testing::IsEmpty;

namespace icing {
namespace lib {

namespace {

// TODO(b/259743562): [Optimization 2] update unit tests after applying
//                    compression. Remember to create varint/delta encoding
//                    overflow (which causes state NOT_FULL -> FULL directly
//                    without ALMOST_FULL) test cases, including for
//                    PopFrontData.

TEST(PostingListIntegerIndexSerializerTest, GetMinPostingListSizeToFitNotNull) {
  PostingListIntegerIndexSerializer serializer;

  int size = 2551 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/0, /*key=*/2)),
              IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used),
              Eq(2 * sizeof(IntegerIndexData)));

  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/1, /*key=*/5)),
              IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used),
              Eq(3 * sizeof(IntegerIndexData)));
}

TEST(PostingListIntegerIndexSerializerTest,
     GetMinPostingListSizeToFitAlmostFull) {
  PostingListIntegerIndexSerializer serializer;

  int size = 3 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/0, /*key=*/2)),
              IsOk());
  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/1, /*key=*/5)),
              IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used), Eq(size));
}

TEST(PostingListIntegerIndexSerializerTest, GetMinPostingListSizeToFitFull) {
  PostingListIntegerIndexSerializer serializer;

  int size = 3 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/0, /*key=*/2)),
              IsOk());
  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/1, /*key=*/5)),
              IsOk());
  ASSERT_THAT(serializer.PrependData(
                  &pl_used, IntegerIndexData(/*section_id=*/0,
                                             /*document_id=*/2, /*key=*/0)),
              IsOk());
  EXPECT_THAT(serializer.GetMinPostingListSizeToFit(&pl_used), Eq(size));
}

TEST(PostingListIntegerIndexSerializerTest, PrependDataNotFull) {
  PostingListIntegerIndexSerializer serializer;

  int size = 2551 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // Make used.
  IntegerIndexData data0(/*section_id=*/0, /*document_id=*/0, /*key=*/2);
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  // Size = sizeof(uncompressed data0)
  int expected_size = sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(ElementsAre(data0)));

  IntegerIndexData data1(/*section_id=*/0, /*document_id=*/1, /*key=*/5);
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  // Size = sizeof(uncompressed data1)
  //        + sizeof(uncompressed data0)
  expected_size += sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  IntegerIndexData data2(/*section_id=*/0, /*document_id=*/2, /*key=*/0);
  EXPECT_THAT(serializer.PrependData(&pl_used, data2), IsOk());
  // Size = sizeof(uncompressed data2)
  //        + sizeof(uncompressed data1)
  //        + sizeof(uncompressed data0)
  expected_size += sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data2, data1, data0)));
}

TEST(PostingListIntegerIndexSerializerTest, PrependDataAlmostFull) {
  PostingListIntegerIndexSerializer serializer;

  int size = 4 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // Fill up the compressed region.
  // Transitions:
  // Adding data0: EMPTY -> NOT_FULL
  // Adding data1: NOT_FULL -> NOT_FULL
  IntegerIndexData data0(/*section_id=*/0, /*document_id=*/0, /*key=*/2);
  IntegerIndexData data1(/*section_id=*/0, /*document_id=*/1, /*key=*/5);
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  int expected_size = 2 * sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  // Add one more data to transition NOT_FULL -> ALMOST_FULL
  IntegerIndexData data2(/*section_id=*/0, /*document_id=*/2, /*key=*/0);
  EXPECT_THAT(serializer.PrependData(&pl_used, data2), IsOk());
  expected_size = 3 * sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data2, data1, data0)));

  // Add one more data to transition ALMOST_FULL -> FULL
  IntegerIndexData data3(/*section_id=*/0, /*document_id=*/3, /*key=*/-3);
  EXPECT_THAT(serializer.PrependData(&pl_used, data3), IsOk());
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data3, data2, data1, data0)));

  // The posting list is FULL. Adding another data should fail.
  IntegerIndexData data4(/*section_id=*/0, /*document_id=*/4, /*key=*/100);
  EXPECT_THAT(serializer.PrependData(&pl_used, data4),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListIntegerIndexSerializerTest, PrependDataPostingListUsedMinSize) {
  PostingListIntegerIndexSerializer serializer;

  int size = serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // PL State: EMPTY
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(0));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(IsEmpty()));

  // Add a data. PL should shift to ALMOST_FULL state
  IntegerIndexData data0(/*section_id=*/0, /*document_id=*/0, /*key=*/2);
  EXPECT_THAT(serializer.PrependData(&pl_used, data0), IsOk());
  // Size = sizeof(uncompressed data0)
  int expected_size = sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used), IsOkAndHolds(ElementsAre(data0)));

  // Add another data. PL should shift to FULL state.
  IntegerIndexData data1(/*section_id=*/0, /*document_id=*/1, /*key=*/5);
  EXPECT_THAT(serializer.PrependData(&pl_used, data1), IsOk());
  // Size = sizeof(uncompressed data1) + sizeof(uncompressed data0)
  expected_size += sizeof(IntegerIndexData);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(expected_size));
  EXPECT_THAT(serializer.GetData(&pl_used),
              IsOkAndHolds(ElementsAre(data1, data0)));

  // The posting list is FULL. Adding another data should fail.
  IntegerIndexData data2(/*section_id=*/0, /*document_id=*/2, /*key=*/0);
  EXPECT_THAT(serializer.PrependData(&pl_used, data2),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListIntegerIndexSerializerTest,
     PrependDataArrayDoNotKeepPrepended) {
  PostingListIntegerIndexSerializer serializer;

  int size = 6 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<IntegerIndexData> data_in;
  std::vector<IntegerIndexData> data_pushed;

  // Add 3 data. The PL is in the empty state and should be able to fit all 3
  // data without issue, transitioning the PL from EMPTY -> NOT_FULL.
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/2, /*key=*/0));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 2 data. The PL should transition from NOT_FULL to ALMOST_FULL.
  data_in.clear();
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/3, /*key=*/-3));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/4, /*key=*/100));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 2 data. The PL should remain ALMOST_FULL since the remaining space can
  // only fit 1 data.
  data_in.clear();
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/5, /*key=*/-200));
  data_in.push_back(IntegerIndexData(/*section_id=*/0, /*document_id=*/6,
                                     /*key=*/2147483647));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(0));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 1 data. The PL should transition from ALMOST_FULL to FULL.
  data_in.resize(1);
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));
}

TEST(PostingListIntegerIndexSerializerTest, PrependDataArrayKeepPrepended) {
  PostingListIntegerIndexSerializer serializer;

  int size = 6 * sizeof(IntegerIndexData);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<IntegerIndexData> data_in;
  std::vector<IntegerIndexData> data_pushed;

  // Add 3 data. The PL is in the empty state and should be able to fit all 3
  // data without issue, transitioning the PL from EMPTY -> NOT_FULL.
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/2, /*key=*/0));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/true),
      IsOkAndHolds(data_in.size()));
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));

  // Add 4 data. The PL should prepend 3 data and transition from NOT_FULL to
  // FULL.
  data_in.clear();
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/3, /*key=*/-3));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/4, /*key=*/100));
  data_in.push_back(
      IntegerIndexData(/*section_id=*/0, /*document_id=*/5, /*key=*/-200));
  data_in.push_back(IntegerIndexData(/*section_id=*/0, /*document_id=*/6,
                                     /*key=*/2147483647));
  EXPECT_THAT(
      serializer.PrependDataArray(&pl_used, data_in.data(), data_in.size(),
                                  /*keep_prepended=*/true),
      IsOkAndHolds(3));
  data_in.resize(3);
  std::move(data_in.begin(), data_in.end(), std::back_inserter(data_pushed));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used),
              Eq(data_pushed.size() * sizeof(IntegerIndexData)));
  EXPECT_THAT(
      serializer.GetData(&pl_used),
      IsOkAndHolds(ElementsAreArray(data_pushed.rbegin(), data_pushed.rend())));
}

TEST(PostingListIntegerIndexSerializerTest, MoveFrom) {
  PostingListIntegerIndexSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<IntegerIndexData> data_arr1 = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5)};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used1, data_arr1.data(), data_arr1.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr1.size()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<IntegerIndexData> data_arr2 = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/2, /*key=*/0),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/3, /*key=*/-3),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/4, /*key=*/100),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/5, /*key=*/-200)};
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

TEST(PostingListIntegerIndexSerializerTest,
     MoveToNullReturnsFailedPrecondition) {
  PostingListIntegerIndexSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<IntegerIndexData> data_arr = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5)};
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

TEST(PostingListIntegerIndexSerializerTest, MoveToPostingListTooSmall) {
  PostingListIntegerIndexSerializer serializer;

  int size1 = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size1));
  std::vector<IntegerIndexData> data_arr1 = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/2, /*key=*/0),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/3, /*key=*/-3),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/4, /*key=*/100)};
  ASSERT_THAT(
      serializer.PrependDataArray(&pl_used1, data_arr1.data(), data_arr1.size(),
                                  /*keep_prepended=*/false),
      IsOkAndHolds(data_arr1.size()));

  int size2 = serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size2));
  std::vector<IntegerIndexData> data_arr2 = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/5, /*key=*/-200)};
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

TEST(PostingListIntegerIndexSerializerTest, PopFrontData) {
  PostingListIntegerIndexSerializer serializer;

  int size = 2 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<IntegerIndexData> data_arr = {
      IntegerIndexData(/*section_id=*/0, /*document_id=*/0, /*key=*/2),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/1, /*key=*/5),
      IntegerIndexData(/*section_id=*/0, /*document_id=*/2, /*key=*/0)};
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
