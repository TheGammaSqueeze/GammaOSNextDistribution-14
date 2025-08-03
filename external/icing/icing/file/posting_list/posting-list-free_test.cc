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

#include "icing/file/posting_list/posting-list-free.h"

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gtest/gtest.h"
#include "icing/index/main/posting-list-hit-serializer.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

// TODO(b/249829533): test different serializers

TEST(PostingListTest, PostingListFree) {
  PostingListHitSerializer serializer;
  static const size_t kHitsSize = 2551 * sizeof(Hit);

  std::unique_ptr<char[]> hits_buf = std::make_unique<char[]>(kHitsSize);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListFree pl_free,
      PostingListFree::CreateFromUnitializedRegion(
          static_cast<void *>(hits_buf.get()), kHitsSize,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()));
  EXPECT_EQ(pl_free.get_next_posting_list_index(), kInvalidPostingListIndex);
}

TEST(PostingListTest, PostingListTooSmallInvalidArgument) {
  PostingListHitSerializer serializer;
  const size_t kHitSizeTooSmall =
      serializer.GetMinPostingListSize() - sizeof(Hit);

  std::unique_ptr<char[]> hits_buf = std::make_unique<char[]>(kHitSizeTooSmall);
  EXPECT_THAT(
      PostingListFree::CreateFromUnitializedRegion(
          static_cast<void *>(hits_buf.get()), kHitSizeTooSmall,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(
      PostingListFree::CreateFromPreexistingPostingListFreeRegion(
          static_cast<void *>(hits_buf.get()), kHitSizeTooSmall,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(PostingListTest, PostingListNotAlignedInvalidArgument) {
  PostingListHitSerializer serializer;
  const size_t kHitSizeNotAligned = serializer.GetMinPostingListSize() + 1;

  std::unique_ptr<char[]> hits_buf =
      std::make_unique<char[]>(kHitSizeNotAligned);
  EXPECT_THAT(
      PostingListFree::CreateFromUnitializedRegion(
          static_cast<void *>(hits_buf.get()), kHitSizeNotAligned,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(
      PostingListFree::CreateFromPreexistingPostingListFreeRegion(
          static_cast<void *>(hits_buf.get()), kHitSizeNotAligned,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(PostingListTest, PostingListNullBufferFailedPrecondition) {
  PostingListHitSerializer serializer;
  const size_t kHitSize = serializer.GetMinPostingListSize();

  // nullptr posting_list_buffer
  EXPECT_THAT(
      PostingListFree::CreateFromUnitializedRegion(
          /*posting_list_buffer=*/nullptr, kHitSize,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(
      PostingListFree::CreateFromPreexistingPostingListFreeRegion(
          /*posting_list_buffer=*/nullptr, kHitSize,
          serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST(PostingListTest, PostingListFreePreexistingRegion) {
  PostingListHitSerializer serializer;
  constexpr PostingListIndex kOtherPostingListIndex = 12;
  static const size_t kHitsSize = 2551 * sizeof(Hit);

  std::unique_ptr<char[]> hits_buf = std::make_unique<char[]>(kHitsSize);
  {
    // Set posting list index to kOtherPostingListIndex
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListFree pl_free,
        PostingListFree::CreateFromUnitializedRegion(
            static_cast<void *>(hits_buf.get()), kHitsSize,
            serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()));
    pl_free.set_next_posting_list_index(kOtherPostingListIndex);
    EXPECT_EQ(pl_free.get_next_posting_list_index(), kOtherPostingListIndex);
  }
  {
    // We should still be able to retrieve kOtherPostingListIndex when we create
    // a posting list from the existing region.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListFree pl_free,
        PostingListFree::CreateFromPreexistingPostingListFreeRegion(
            static_cast<void *>(hits_buf.get()), kHitsSize,
            serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()));
    EXPECT_EQ(pl_free.get_next_posting_list_index(), kOtherPostingListIndex);
  }
}

TEST(PostingListTest, PostingListFreeUninitializedRegion) {
  PostingListHitSerializer serializer;
  constexpr PostingListIndex kOtherPostingListIndex = 12;
  static const size_t kHitsSize = 2551 * sizeof(Hit);

  std::unique_ptr<char[]> hits_buf = std::make_unique<char[]>(kHitsSize);
  {
    // Set posting list index to kOtherPostingListIndex
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListFree pl_free,
        PostingListFree::CreateFromUnitializedRegion(
            static_cast<void *>(hits_buf.get()), kHitsSize,
            serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()));
    pl_free.set_next_posting_list_index(kOtherPostingListIndex);
    EXPECT_EQ(pl_free.get_next_posting_list_index(), kOtherPostingListIndex);
  }
  {
    // Creating from an uninitialized region should cause the posting list to
    // override kOtherPostingListIndex with kInvalidPostingListIndex.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListFree pl_free,
        PostingListFree::CreateFromUnitializedRegion(
            static_cast<void *>(hits_buf.get()), kHitsSize,
            serializer.GetDataTypeBytes(), serializer.GetMinPostingListSize()));
    EXPECT_EQ(pl_free.get_next_posting_list_index(), kInvalidPostingListIndex);
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
