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

#include "icing/file/posting_list/index-block.h"

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/main/posting-list-hit-serializer.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::IsFalse;
using ::testing::IsTrue;

static constexpr int kBlockSize = 4096;

class IndexBlockTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/flash";
    flash_file_ = test_dir_ + "/0";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(test_dir_.c_str()));

    sfd_ = std::make_unique<ScopedFd>(
        filesystem_.OpenForWrite(flash_file_.c_str()));
    ASSERT_TRUE(sfd_->is_valid());

    // Grow the file by one block for the IndexBlock to use.
    ASSERT_TRUE(filesystem_.Grow(sfd_->get(), kBlockSize));

    // TODO: test different serializers
    serializer_ = std::make_unique<PostingListHitSerializer>();
  }

  void TearDown() override {
    serializer_.reset();
    sfd_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
  }

  Filesystem filesystem_;
  std::string test_dir_;
  std::string flash_file_;
  std::unique_ptr<ScopedFd> sfd_;
  std::unique_ptr<PostingListHitSerializer> serializer_;
};

TEST_F(IndexBlockTest, CreateFromUninitializedRegionProducesEmptyBlock) {
  constexpr int kPostingListBytes = 20;

  {
    // Create an IndexBlock from this newly allocated file block.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromUninitializedRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize, kPostingListBytes));
    EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsTrue()));
  }
}

TEST_F(IndexBlockTest, SizeAccessorsWorkCorrectly) {
  constexpr int kPostingListBytes1 = 20;

  // Create an IndexBlock from this newly allocated file block.
  ICING_ASSERT_OK_AND_ASSIGN(IndexBlock block,
                             IndexBlock::CreateFromUninitializedRegion(
                                 &filesystem_, serializer_.get(), sfd_->get(),
                                 /*offset=*/0, kBlockSize, kPostingListBytes1));
  EXPECT_THAT(block.posting_list_bytes(), Eq(kPostingListBytes1));
  // There should be (4096 - 12) / 20 = 204 posting lists
  // (sizeof(BlockHeader)==12). We can store a PostingListIndex of 203 in only 8
  // bits.
  EXPECT_THAT(block.max_num_posting_lists(), Eq(204));
  EXPECT_THAT(block.posting_list_index_bits(), Eq(8));

  constexpr int kPostingListBytes2 = 200;

  // Create an IndexBlock from this newly allocated file block.
  ICING_ASSERT_OK_AND_ASSIGN(
      block, IndexBlock::CreateFromUninitializedRegion(
                 &filesystem_, serializer_.get(), sfd_->get(), /*offset=*/0,
                 kBlockSize, kPostingListBytes2));
  EXPECT_THAT(block.posting_list_bytes(), Eq(kPostingListBytes2));
  // There should be (4096 - 12) / 200 = 20 posting lists
  // (sizeof(BlockHeader)==12). We can store a PostingListIndex of 19 in only 5
  // bits.
  EXPECT_THAT(block.max_num_posting_lists(), Eq(20));
  EXPECT_THAT(block.posting_list_index_bits(), Eq(5));
}

TEST_F(IndexBlockTest, IndexBlockChangesPersistAcrossInstances) {
  constexpr int kPostingListBytes = 2000;

  std::vector<Hit> test_hits{
      Hit(/*section_id=*/2, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/5, /*document_id=*/1, /*term_frequency=*/99),
      Hit(/*section_id=*/3, /*document_id=*/3, /*term_frequency=*/17),
      Hit(/*section_id=*/10, /*document_id=*/10, Hit::kDefaultTermFrequency),
  };
  PostingListIndex allocated_index;
  {
    // Create an IndexBlock from this newly allocated file block.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromUninitializedRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize, kPostingListBytes));
    // Add hits to the first posting list.
    ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info,
                               block.AllocatePostingList());
    for (const Hit& hit : test_hits) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&alloc_info.posting_list_used, hit));
    }
    EXPECT_THAT(
        serializer_->GetHits(&alloc_info.posting_list_used),
        IsOkAndHolds(ElementsAreArray(test_hits.rbegin(), test_hits.rend())));

    ICING_ASSERT_OK(block.WritePostingListToDisk(
        alloc_info.posting_list_used, alloc_info.posting_list_index));
    allocated_index = alloc_info.posting_list_index;
  }
  {
    // Create an IndexBlock from the previously allocated file block.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromPreexistingIndexBlockRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize));
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock::PostingListAndBlockInfo pl_block_info,
        block.GetAllocatedPostingList(allocated_index));
    EXPECT_THAT(
        serializer_->GetHits(&pl_block_info.posting_list_used),
        IsOkAndHolds(ElementsAreArray(test_hits.rbegin(), test_hits.rend())));
    EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsTrue()));
  }
}

TEST_F(IndexBlockTest, IndexBlockMultiplePostingLists) {
  constexpr int kPostingListBytes = 2000;

  std::vector<Hit> hits_in_posting_list1{
      Hit(/*section_id=*/2, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/5, /*document_id=*/1, /*term_frequency=*/99),
      Hit(/*section_id=*/3, /*document_id=*/3, /*term_frequency=*/17),
      Hit(/*section_id=*/10, /*document_id=*/10, Hit::kDefaultTermFrequency),
  };
  std::vector<Hit> hits_in_posting_list2{
      Hit(/*section_id=*/12, /*document_id=*/220, /*term_frequency=*/88),
      Hit(/*section_id=*/17, /*document_id=*/265, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/0, /*document_id=*/287, /*term_frequency=*/2),
      Hit(/*section_id=*/11, /*document_id=*/306, /*term_frequency=*/12),
      Hit(/*section_id=*/10, /*document_id=*/306, Hit::kDefaultTermFrequency),
  };
  PostingListIndex allocated_index_1;
  PostingListIndex allocated_index_2;
  {
    // Create an IndexBlock from this newly allocated file block.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromUninitializedRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize, kPostingListBytes));

    // Add hits to the first posting list.
    ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info_1,
                               block.AllocatePostingList());
    for (const Hit& hit : hits_in_posting_list1) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&alloc_info_1.posting_list_used, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&alloc_info_1.posting_list_used),
                IsOkAndHolds(ElementsAreArray(hits_in_posting_list1.rbegin(),
                                              hits_in_posting_list1.rend())));

    // Add hits to the second posting list.
    ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info_2,
                               block.AllocatePostingList());
    for (const Hit& hit : hits_in_posting_list2) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&alloc_info_2.posting_list_used, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&alloc_info_2.posting_list_used),
                IsOkAndHolds(ElementsAreArray(hits_in_posting_list2.rbegin(),
                                              hits_in_posting_list2.rend())));

    EXPECT_THAT(block.AllocatePostingList(),
                StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
    EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsFalse()));

    // Write both posting lists to disk.
    ICING_ASSERT_OK(block.WritePostingListToDisk(
        alloc_info_1.posting_list_used, alloc_info_1.posting_list_index));
    ICING_ASSERT_OK(block.WritePostingListToDisk(
        alloc_info_2.posting_list_used, alloc_info_2.posting_list_index));
    allocated_index_1 = alloc_info_1.posting_list_index;
    allocated_index_2 = alloc_info_2.posting_list_index;
  }
  {
    // Create an IndexBlock from the previously allocated file block.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromPreexistingIndexBlockRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize));
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock::PostingListAndBlockInfo pl_block_info_1,
        block.GetAllocatedPostingList(allocated_index_1));
    EXPECT_THAT(serializer_->GetHits(&pl_block_info_1.posting_list_used),
                IsOkAndHolds(ElementsAreArray(hits_in_posting_list1.rbegin(),
                                              hits_in_posting_list1.rend())));
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock::PostingListAndBlockInfo pl_block_info_2,
        block.GetAllocatedPostingList(allocated_index_2));
    EXPECT_THAT(serializer_->GetHits(&pl_block_info_2.posting_list_used),
                IsOkAndHolds(ElementsAreArray(hits_in_posting_list2.rbegin(),
                                              hits_in_posting_list2.rend())));
    EXPECT_THAT(block.AllocatePostingList(),
                StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
    EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsFalse()));
  }
}

TEST_F(IndexBlockTest, IndexBlockReallocatingPostingLists) {
  constexpr int kPostingListBytes = 2000;

  // Create an IndexBlock from this newly allocated file block.
  ICING_ASSERT_OK_AND_ASSIGN(IndexBlock block,
                             IndexBlock::CreateFromUninitializedRegion(
                                 &filesystem_, serializer_.get(), sfd_->get(),
                                 /*offset=*/0, kBlockSize, kPostingListBytes));

  // Add hits to the first posting list.
  std::vector<Hit> hits_in_posting_list1{
      Hit(/*section_id=*/2, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/5, /*document_id=*/1, /*term_frequency=*/99),
      Hit(/*section_id=*/3, /*document_id=*/3, /*term_frequency=*/17),
      Hit(/*section_id=*/10, /*document_id=*/10, Hit::kDefaultTermFrequency),
  };
  ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info_1,
                             block.AllocatePostingList());
  for (const Hit& hit : hits_in_posting_list1) {
    ICING_ASSERT_OK(
        serializer_->PrependHit(&alloc_info_1.posting_list_used, hit));
  }
  EXPECT_THAT(serializer_->GetHits(&alloc_info_1.posting_list_used),
              IsOkAndHolds(ElementsAreArray(hits_in_posting_list1.rbegin(),
                                            hits_in_posting_list1.rend())));

  // Add hits to the second posting list.
  std::vector<Hit> hits_in_posting_list2{
      Hit(/*section_id=*/12, /*document_id=*/220, /*term_frequency=*/88),
      Hit(/*section_id=*/17, /*document_id=*/265, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/0, /*document_id=*/287, /*term_frequency=*/2),
      Hit(/*section_id=*/11, /*document_id=*/306, /*term_frequency=*/12),
      Hit(/*section_id=*/10, /*document_id=*/306, Hit::kDefaultTermFrequency),
  };
  ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info_2,
                             block.AllocatePostingList());
  for (const Hit& hit : hits_in_posting_list2) {
    ICING_ASSERT_OK(
        serializer_->PrependHit(&alloc_info_2.posting_list_used, hit));
  }
  EXPECT_THAT(serializer_->GetHits(&alloc_info_2.posting_list_used),
              IsOkAndHolds(ElementsAreArray(hits_in_posting_list2.rbegin(),
                                            hits_in_posting_list2.rend())));

  EXPECT_THAT(block.AllocatePostingList(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsFalse()));

  // Now free the first posting list. Then, reallocate it and fill it with a
  // different set of hits.
  ICING_ASSERT_OK(block.FreePostingList(alloc_info_1.posting_list_index));
  EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsTrue()));

  std::vector<Hit> hits_in_posting_list3{
      Hit(/*section_id=*/12, /*document_id=*/0, /*term_frequency=*/88),
      Hit(/*section_id=*/17, /*document_id=*/1, Hit::kDefaultTermFrequency),
      Hit(/*section_id=*/0, /*document_id=*/2, /*term_frequency=*/2),
  };
  ICING_ASSERT_OK_AND_ASSIGN(IndexBlock::PostingListAndBlockInfo alloc_info_3,
                             block.AllocatePostingList());
  EXPECT_THAT(alloc_info_3.posting_list_index,
              Eq(alloc_info_3.posting_list_index));
  for (const Hit& hit : hits_in_posting_list3) {
    ICING_ASSERT_OK(
        serializer_->PrependHit(&alloc_info_3.posting_list_used, hit));
  }
  EXPECT_THAT(serializer_->GetHits(&alloc_info_3.posting_list_used),
              IsOkAndHolds(ElementsAreArray(hits_in_posting_list3.rbegin(),
                                            hits_in_posting_list3.rend())));
  EXPECT_THAT(block.AllocatePostingList(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(block.HasFreePostingLists(), IsOkAndHolds(IsFalse()));
}

TEST_F(IndexBlockTest, IndexBlockNextBlockIndex) {
  constexpr int kPostingListBytes = 2000;
  constexpr int kSomeBlockIndex = 22;

  {
    // Create an IndexBlock from this newly allocated file block and set the
    // next block index.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromUninitializedRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize, kPostingListBytes));
    EXPECT_THAT(block.GetNextBlockIndex(), IsOkAndHolds(kInvalidBlockIndex));
    EXPECT_THAT(block.SetNextBlockIndex(kSomeBlockIndex), IsOk());
    EXPECT_THAT(block.GetNextBlockIndex(), IsOkAndHolds(kSomeBlockIndex));
  }
  {
    // Create an IndexBlock from this previously allocated file block and make
    // sure that next_block_index is still set properly.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromPreexistingIndexBlockRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize));
    EXPECT_THAT(block.GetNextBlockIndex(), IsOkAndHolds(kSomeBlockIndex));
  }
  {
    // Create an IndexBlock, treating this file block as uninitialized. This
    // reset the next_block_index to kInvalidBlockIndex.
    ICING_ASSERT_OK_AND_ASSIGN(
        IndexBlock block, IndexBlock::CreateFromUninitializedRegion(
                              &filesystem_, serializer_.get(), sfd_->get(),
                              /*offset=*/0, kBlockSize, kPostingListBytes));
    EXPECT_THAT(block.GetNextBlockIndex(), IsOkAndHolds(kInvalidBlockIndex));
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
