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

#include "icing/index/numeric/posting-list-integer-index-accessor.h"

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::Lt;
using ::testing::Ne;
using ::testing::SizeIs;

class PostingListIntegerIndexAccessorTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/test_dir";
    file_name_ = test_dir_ + "/test_file.idx.index";

    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(test_dir_.c_str()));

    serializer_ = std::make_unique<PostingListIntegerIndexSerializer>();

    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
    flash_index_storage_ =
        std::make_unique<FlashIndexStorage>(std::move(flash_index_storage));
  }

  void TearDown() override {
    flash_index_storage_.reset();
    serializer_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
  }

  Filesystem filesystem_;
  std::string test_dir_;
  std::string file_name_;
  std::unique_ptr<PostingListIntegerIndexSerializer> serializer_;
  std::unique_ptr<FlashIndexStorage> flash_index_storage_;
};

std::vector<IntegerIndexData> CreateData(int num_data,
                                         DocumentId start_document_id,
                                         int64_t start_key) {
  SectionId section_id = kMaxSectionId;

  std::vector<IntegerIndexData> data;
  data.reserve(num_data);
  for (int i = 0; i < num_data; ++i) {
    data.push_back(IntegerIndexData(section_id, start_document_id, start_key));

    if (section_id == kMinSectionId) {
      section_id = kMaxSectionId;
    } else {
      --section_id;
    }
    ++start_document_id;
    ++start_key;
  }
  return data;
}

TEST_F(PostingListIntegerIndexAccessorTest, DataAddAndRetrieveProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Add some integer index data
  std::vector<IntegerIndexData> data_vec =
      CreateData(/*num_data=*/5, /*start_document_id=*/0, /*start_key=*/819);
  for (const IntegerIndexData& data : data_vec) {
    EXPECT_THAT(pl_accessor->PrependData(data), IsOk());
  }
  PostingListAccessor::FinalizeResult result =
      std::move(*pl_accessor).Finalize();
  EXPECT_THAT(result.status, IsOk());
  EXPECT_THAT(result.id.block_index(), Eq(1));
  EXPECT_THAT(result.id.posting_list_index(), Eq(0));

  // Retrieve some data.
  ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder pl_holder,
                             flash_index_storage_->GetPostingList(result.id));
  EXPECT_THAT(
      serializer_->GetData(&pl_holder.posting_list),
      IsOkAndHolds(ElementsAreArray(data_vec.rbegin(), data_vec.rend())));
  EXPECT_THAT(pl_holder.next_block_index, Eq(kInvalidBlockIndex));
}

TEST_F(PostingListIntegerIndexAccessorTest, PreexistingPLKeepOnSameBlock) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Add a single data. This will fit in a min-sized posting list.
  IntegerIndexData data1(/*section_id=*/1, /*document_id=*/0, /*key=*/12345);
  ICING_ASSERT_OK(pl_accessor->PrependData(data1));
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result1.status);
  // Should be allocated to the first block.
  ASSERT_THAT(result1.id.block_index(), Eq(1));
  ASSERT_THAT(result1.id.posting_list_index(), Eq(0));

  // Add one more data. The minimum size for a posting list must be able to fit
  // two data, so this should NOT cause the previous pl to be reallocated.
  ICING_ASSERT_OK_AND_ASSIGN(
      pl_accessor,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  IntegerIndexData data2(/*section_id=*/1, /*document_id=*/1, /*key=*/23456);
  ICING_ASSERT_OK(pl_accessor->PrependData(data2));
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result2.status);
  // Should be in the same posting list.
  EXPECT_THAT(result2.id, Eq(result1.id));

  // The posting list at result2.id should hold all of the data that have been
  // added.
  ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder pl_holder,
                             flash_index_storage_->GetPostingList(result2.id));
  EXPECT_THAT(serializer_->GetData(&pl_holder.posting_list),
              IsOkAndHolds(ElementsAre(data2, data1)));
}

TEST_F(PostingListIntegerIndexAccessorTest, PreexistingPLReallocateToLargerPL) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Adding 3 data should cause Finalize allocating a 48-byte posting list,
  // which can store at most 4 data.
  std::vector<IntegerIndexData> data_vec1 =
      CreateData(/*num_data=*/3, /*start_document_id=*/0, /*start_key=*/819);
  for (const IntegerIndexData& data : data_vec1) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result1.status);
  // Should be allocated to the first block.
  ASSERT_THAT(result1.id.block_index(), Eq(1));
  ASSERT_THAT(result1.id.posting_list_index(), Eq(0));

  // Now add more data.
  ICING_ASSERT_OK_AND_ASSIGN(
      pl_accessor,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  // The current posting list can fit 1 more data. Adding 12 more data should
  // result in these data being moved to a larger posting list. Also the total
  // size of these data won't exceed max size posting list, so there will be
  // only one single posting list and no chain.
  std::vector<IntegerIndexData> data_vec2 = CreateData(
      /*num_data=*/12,
      /*start_document_id=*/data_vec1.back().basic_hit().document_id() + 1,
      /*start_key=*/819);

  for (const IntegerIndexData& data : data_vec2) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result2.status);
  // Should be allocated to the second (new) block because the posting list
  // should grow beyond the size that the first block maintains.
  EXPECT_THAT(result2.id.block_index(), Eq(2));
  EXPECT_THAT(result2.id.posting_list_index(), Eq(0));

  // The posting list at result2.id should hold all of the data that have been
  // added.
  std::vector<IntegerIndexData> all_data_vec;
  all_data_vec.reserve(data_vec1.size() + data_vec2.size());
  all_data_vec.insert(all_data_vec.end(), data_vec1.begin(), data_vec1.end());
  all_data_vec.insert(all_data_vec.end(), data_vec2.begin(), data_vec2.end());
  ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder pl_holder,
                             flash_index_storage_->GetPostingList(result2.id));
  EXPECT_THAT(serializer_->GetData(&pl_holder.posting_list),
              IsOkAndHolds(ElementsAreArray(all_data_vec.rbegin(),
                                            all_data_vec.rend())));
}

TEST_F(PostingListIntegerIndexAccessorTest, MultiBlockChainsBlocksProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Block size is 4096, sizeof(BlockHeader) is 12 and sizeof(IntegerIndexData)
  // is 12, so the max size posting list can store (4096 - 12) / 12 = 340 data.
  // Adding 341 data should cause:
  // - 2 max size posting lists being allocated to block 1 and block 2.
  // - Chaining: block 2 -> block 1
  std::vector<IntegerIndexData> data_vec =
      CreateData(/*num_data=*/341, /*start_document_id=*/0, /*start_key=*/819);
  for (const IntegerIndexData& data : data_vec) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result1.status);
  PostingListIdentifier second_block_id = result1.id;
  // Should be allocated to the second block.
  EXPECT_THAT(second_block_id, Eq(PostingListIdentifier(
                                   /*block_index=*/2, /*posting_list_index=*/0,
                                   /*posting_list_index_bits=*/0)));

  // We should be able to retrieve all data.
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListHolder pl_holder,
      flash_index_storage_->GetPostingList(second_block_id));
  // This pl_holder will only hold a posting list with the data that didn't fit
  // on the first block.
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<IntegerIndexData> second_block_data,
                             serializer_->GetData(&pl_holder.posting_list));
  ASSERT_THAT(second_block_data, SizeIs(Lt(data_vec.size())));
  auto first_block_data_start = data_vec.rbegin() + second_block_data.size();
  EXPECT_THAT(second_block_data,
              ElementsAreArray(data_vec.rbegin(), first_block_data_start));

  // Now retrieve all of the data that were on the first block.
  uint32_t first_block_id = pl_holder.next_block_index;
  EXPECT_THAT(first_block_id, Eq(1));

  PostingListIdentifier pl_id(first_block_id, /*posting_list_index=*/0,
                              /*posting_list_index_bits=*/0);
  ICING_ASSERT_OK_AND_ASSIGN(pl_holder,
                             flash_index_storage_->GetPostingList(pl_id));
  EXPECT_THAT(
      serializer_->GetData(&pl_holder.posting_list),
      IsOkAndHolds(ElementsAreArray(first_block_data_start, data_vec.rend())));
}

TEST_F(PostingListIntegerIndexAccessorTest,
       PreexistingMultiBlockReusesBlocksProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Block size is 4096, sizeof(BlockHeader) is 12 and sizeof(IntegerIndexData)
  // is 12, so the max size posting list can store (4096 - 12) / 12 = 340 data.
  // Adding 341 data will cause:
  // - 2 max size posting lists being allocated to block 1 and block 2.
  // - Chaining: block 2 -> block 1
  std::vector<IntegerIndexData> data_vec1 =
      CreateData(/*num_data=*/341, /*start_document_id=*/0, /*start_key=*/819);
  for (const IntegerIndexData& data : data_vec1) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result1.status);
  PostingListIdentifier first_add_id = result1.id;
  EXPECT_THAT(first_add_id, Eq(PostingListIdentifier(
                                /*block_index=*/2, /*posting_list_index=*/0,
                                /*posting_list_index_bits=*/0)));

  // Now add more data. These should fit on the existing second block and not
  // fill it up.
  ICING_ASSERT_OK_AND_ASSIGN(
      pl_accessor,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), first_add_id));
  std::vector<IntegerIndexData> data_vec2 = CreateData(
      /*num_data=*/10,
      /*start_document_id=*/data_vec1.back().basic_hit().document_id() + 1,
      /*start_key=*/819);
  for (const IntegerIndexData& data : data_vec2) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result2.status);
  PostingListIdentifier second_add_id = result2.id;
  EXPECT_THAT(second_add_id, Eq(first_add_id));

  // We should be able to retrieve all data.
  std::vector<IntegerIndexData> all_data_vec;
  all_data_vec.reserve(data_vec1.size() + data_vec2.size());
  all_data_vec.insert(all_data_vec.end(), data_vec1.begin(), data_vec1.end());
  all_data_vec.insert(all_data_vec.end(), data_vec2.begin(), data_vec2.end());
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListHolder pl_holder,
      flash_index_storage_->GetPostingList(second_add_id));
  // This pl_holder will only hold a posting list with the data that didn't fit
  // on the first block.
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<IntegerIndexData> second_block_data,
                             serializer_->GetData(&pl_holder.posting_list));
  ASSERT_THAT(second_block_data, SizeIs(Lt(all_data_vec.size())));
  auto first_block_data_start =
      all_data_vec.rbegin() + second_block_data.size();
  EXPECT_THAT(second_block_data,
              ElementsAreArray(all_data_vec.rbegin(), first_block_data_start));

  // Now retrieve all of the data that were on the first block.
  uint32_t first_block_id = pl_holder.next_block_index;
  EXPECT_THAT(first_block_id, Eq(1));

  PostingListIdentifier pl_id(first_block_id, /*posting_list_index=*/0,
                              /*posting_list_index_bits=*/0);
  ICING_ASSERT_OK_AND_ASSIGN(pl_holder,
                             flash_index_storage_->GetPostingList(pl_id));
  EXPECT_THAT(serializer_->GetData(&pl_holder.posting_list),
              IsOkAndHolds(ElementsAreArray(first_block_data_start,
                                            all_data_vec.rend())));
}

TEST_F(PostingListIntegerIndexAccessorTest,
       InvalidDataShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  IntegerIndexData invalid_data;
  EXPECT_THAT(pl_accessor->PrependData(invalid_data),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListIntegerIndexAccessorTest,
       BasicHitIncreasingShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  IntegerIndexData data1(/*section_id=*/3, /*document_id=*/1, /*key=*/12345);
  ICING_ASSERT_OK(pl_accessor->PrependData(data1));

  IntegerIndexData data2(/*section_id=*/6, /*document_id=*/1, /*key=*/12345);
  EXPECT_THAT(pl_accessor->PrependData(data2),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  IntegerIndexData data3(/*section_id=*/2, /*document_id=*/0, /*key=*/12345);
  EXPECT_THAT(pl_accessor->PrependData(data3),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListIntegerIndexAccessorTest,
       NewPostingListNoDataAddedShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  PostingListAccessor::FinalizeResult result =
      std::move(*pl_accessor).Finalize();
  EXPECT_THAT(result.status,
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListIntegerIndexAccessorTest,
       PreexistingPostingListNoDataAddedShouldSucceed) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor1,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  IntegerIndexData data1(/*section_id=*/3, /*document_id=*/1, /*key=*/12345);
  ICING_ASSERT_OK(pl_accessor1->PrependData(data1));
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor1).Finalize();
  ICING_ASSERT_OK(result1.status);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor2,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor2).Finalize();
  EXPECT_THAT(result2.status, IsOk());
}

TEST_F(PostingListIntegerIndexAccessorTest, GetAllDataAndFree) {
  IntegerIndexData data1(/*section_id=*/3, /*document_id=*/1, /*key=*/123);
  IntegerIndexData data2(/*section_id=*/3, /*document_id=*/2, /*key=*/456);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor1,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Add 2 data.
  ICING_ASSERT_OK(pl_accessor1->PrependData(data1));
  ICING_ASSERT_OK(pl_accessor1->PrependData(data2));
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor1).Finalize();
  ICING_ASSERT_OK(result1.status);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor2,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  EXPECT_THAT(pl_accessor2->GetAllDataAndFree(),
              IsOkAndHolds(ElementsAre(data2, data1)));

  // Allocate a new posting list with same size again.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor3,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Add 2 data.
  ICING_ASSERT_OK(pl_accessor3->PrependData(data1));
  ICING_ASSERT_OK(pl_accessor3->PrependData(data2));
  PostingListAccessor::FinalizeResult result3 =
      std::move(*pl_accessor3).Finalize();
  ICING_ASSERT_OK(result3.status);
  // We should get the same id if the previous one has been freed correctly by
  // GetAllDataAndFree.
  EXPECT_THAT(result3.id, Eq(result1.id));
}

TEST_F(PostingListIntegerIndexAccessorTest, GetAllDataAndFreePostingListChain) {
  uint32_t block_size = FlashIndexStorage::SelectBlockSize();
  uint32_t max_posting_list_bytes = IndexBlock::CalculateMaxPostingListBytes(
      block_size, serializer_->GetDataTypeBytes());
  uint32_t max_num_data_single_posting_list =
      max_posting_list_bytes / serializer_->GetDataTypeBytes();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor1,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));

  // Prepend max_num_data_single_posting_list + 1 data.
  std::vector<IntegerIndexData> data_vec;
  for (uint32_t i = 0; i < max_num_data_single_posting_list + 1; ++i) {
    IntegerIndexData data(/*section_id=*/3, static_cast<DocumentId>(i),
                          /*key=*/i);
    ICING_ASSERT_OK(pl_accessor1->PrependData(data));
    data_vec.push_back(data);
  }

  // This will cause:
  // - Allocate the first max-sized posting list at block index = 1, storing
  //   max_num_data_single_posting_list data.
  // - Allocate the second max-sized posting list at block index = 2, storing 1
  //   data. Also its next_block_index is 1.
  // - IOW, we will get 2 -> 1 and result1.id points to 2.
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor1).Finalize();
  ICING_ASSERT_OK(result1.status);

  uint32_t first_pl_block_index = kInvalidBlockIndex;
  {
    // result1.id points at the second (max-sized) PL, and next_block_index of
    // the second PL points to the first PL's block. Fetch the first PL's block
    // index manually.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder pl_holder,
        flash_index_storage_->GetPostingList(result1.id));
    first_pl_block_index = pl_holder.next_block_index;
  }
  ASSERT_THAT(first_pl_block_index, Ne(kInvalidBlockIndex));

  // Call GetAllDataAndFree. This will free block 2 and block 1.
  // Free block list: 1 -> 2 (since free block list is LIFO).
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor2,
      PostingListIntegerIndexAccessor::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  EXPECT_THAT(
      pl_accessor2->GetAllDataAndFree(),
      IsOkAndHolds(ElementsAreArray(data_vec.rbegin(), data_vec.rend())));
  pl_accessor2.reset();

  // Allocate a new posting list with same size again.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor3,
      PostingListIntegerIndexAccessor::Create(flash_index_storage_.get(),
                                              serializer_.get()));
  // Add same set of data.
  for (uint32_t i = 0; i < max_num_data_single_posting_list + 1; ++i) {
    ICING_ASSERT_OK(pl_accessor3->PrependData(data_vec[i]));
  }

  // This will cause:
  // - Allocate the first max-sized posting list from the free block list, which
  //   is block index = 1, storing max_num_data_single_posting_list data.
  // - Allocate the second max-sized posting list from the next block in free
  //   block list, which is block index = 2, storing 1 data. Also its
  //   next_block_index should be 1.
  PostingListAccessor::FinalizeResult result3 =
      std::move(*pl_accessor3).Finalize();
  ICING_ASSERT_OK(result3.status);
  // We should get the same id if the previous one has been freed correctly by
  // GetAllDataAndFree.
  EXPECT_THAT(result3.id, Eq(result1.id));
  // Also the first PL should be the same if it has been freed correctly by
  // GetAllDataAndFree. Since it is a max-sized posting list, we just need to
  // verify the block index.
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder pl_holder,
        flash_index_storage_->GetPostingList(result3.id));
    EXPECT_THAT(pl_holder.next_block_index, Eq(first_pl_block_index));
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
