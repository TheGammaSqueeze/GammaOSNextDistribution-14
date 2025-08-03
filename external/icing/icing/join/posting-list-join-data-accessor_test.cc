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

#include "icing/join/posting-list-join-data-accessor.h"

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
#include "icing/file/posting_list/posting-list-accessor.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/join/document-id-to-join-info.h"
#include "icing/join/posting-list-join-data-serializer.h"
#include "icing/store/document-id.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
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

using JoinDataType = DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>;

static constexpr NamespaceId kDefaultNamespaceId = 1;

class PostingListJoinDataAccessorTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/test_dir";
    file_name_ = test_dir_ + "/test_file.idx.index";

    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(test_dir_.c_str()));

    serializer_ =
        std::make_unique<PostingListJoinDataSerializer<JoinDataType>>();

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
  std::unique_ptr<PostingListJoinDataSerializer<JoinDataType>> serializer_;
  std::unique_ptr<FlashIndexStorage> flash_index_storage_;
};

std::vector<JoinDataType> CreateData(int num_data, DocumentId start_document_id,
                                     NamespaceId ref_namespace_id,
                                     uint64_t start_ref_hash_uri) {
  std::vector<JoinDataType> data;
  data.reserve(num_data);
  for (int i = 0; i < num_data; ++i) {
    data.push_back(JoinDataType(
        start_document_id,
        NamespaceFingerprintIdentifier(ref_namespace_id,
                                       /*fingerprint=*/start_ref_hash_uri)));

    ++start_document_id;
    ++start_ref_hash_uri;
  }
  return data;
}

TEST_F(PostingListJoinDataAccessorTest, DataAddAndRetrieveProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  // Add some join data
  std::vector<JoinDataType> data_vec =
      CreateData(/*num_data=*/5, /*start_document_id=*/0,
                 /*ref_namespace_id=*/kDefaultNamespaceId,
                 /*start_ref_hash_uri=*/819);
  for (const JoinDataType& data : data_vec) {
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

TEST_F(PostingListJoinDataAccessorTest, PreexistingPLKeepOnSameBlock) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  // Add a single data. This will fit in a min-sized posting list.
  JoinDataType data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(kDefaultNamespaceId, /*fingerprint=*/123));
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
      PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  JoinDataType data2(
      /*document_id=*/2,
      NamespaceFingerprintIdentifier(kDefaultNamespaceId, /*fingerprint=*/456));
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

TEST_F(PostingListJoinDataAccessorTest, PreexistingPLReallocateToLargerPL) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  // Adding 3 data should cause Finalize allocating a 56-byte posting list,
  // which can store at most 4 data.
  std::vector<JoinDataType> data_vec1 =
      CreateData(/*num_data=*/3, /*start_document_id=*/0,
                 /*ref_namespace_id=*/kDefaultNamespaceId,
                 /*start_ref_hash_uri=*/819);
  for (const JoinDataType& data : data_vec1) {
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
      PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  // The current posting list can fit 1 more data. Adding 12 more data should
  // result in these data being moved to a larger posting list. Also the total
  // size of these data won't exceed max size posting list, so there will be
  // only one single posting list and no chain.
  std::vector<JoinDataType> data_vec2 = CreateData(
      /*num_data=*/12, /*start_document_id=*/data_vec1.back().document_id() + 1,
      /*ref_namespace_id=*/kDefaultNamespaceId, /*start_ref_hash_uri=*/819);

  for (const JoinDataType& data : data_vec2) {
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
  std::vector<JoinDataType> all_data_vec;
  all_data_vec.reserve(data_vec1.size() + data_vec2.size());
  all_data_vec.insert(all_data_vec.end(), data_vec1.begin(), data_vec1.end());
  all_data_vec.insert(all_data_vec.end(), data_vec2.begin(), data_vec2.end());
  ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder pl_holder,
                             flash_index_storage_->GetPostingList(result2.id));
  EXPECT_THAT(serializer_->GetData(&pl_holder.posting_list),
              IsOkAndHolds(ElementsAreArray(all_data_vec.rbegin(),
                                            all_data_vec.rend())));
}

TEST_F(PostingListJoinDataAccessorTest, MultiBlockChainsBlocksProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  // Block size is 4096, sizeof(BlockHeader) is 12 and sizeof(JoinDataType)
  // is 14, so the max size posting list can store (4096 - 12) / 14 = 291 data.
  // Adding 292 data should cause:
  // - 2 max size posting lists being allocated to block 1 and block 2.
  // - Chaining: block 2 -> block 1
  std::vector<JoinDataType> data_vec = CreateData(
      /*num_data=*/292, /*start_document_id=*/0,
      /*ref_namespace_id=*/kDefaultNamespaceId, /*start_ref_hash_uri=*/819);
  for (const JoinDataType& data : data_vec) {
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
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<JoinDataType> second_block_data,
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

TEST_F(PostingListJoinDataAccessorTest,
       PreexistingMultiBlockReusesBlocksProperly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  // Block size is 4096, sizeof(BlockHeader) is 12 and sizeof(JoinDataType)
  // is 14, so the max size posting list can store (4096 - 12) / 14 = 291 data.
  // Adding 292 data will cause:
  // - 2 max size posting lists being allocated to block 1 and block 2.
  // - Chaining: block 2 -> block 1
  std::vector<JoinDataType> data_vec1 = CreateData(
      /*num_data=*/292, /*start_document_id=*/0,
      /*ref_namespace_id=*/kDefaultNamespaceId, /*start_ref_hash_uri=*/819);
  for (const JoinDataType& data : data_vec1) {
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
      PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), first_add_id));
  std::vector<JoinDataType> data_vec2 = CreateData(
      /*num_data=*/10, /*start_document_id=*/data_vec1.back().document_id() + 1,
      /*ref_namespace_id=*/kDefaultNamespaceId, /*start_ref_hash_uri=*/819);
  for (const JoinDataType& data : data_vec2) {
    ICING_ASSERT_OK(pl_accessor->PrependData(data));
  }
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor).Finalize();
  ICING_ASSERT_OK(result2.status);
  PostingListIdentifier second_add_id = result2.id;
  EXPECT_THAT(second_add_id, Eq(first_add_id));

  // We should be able to retrieve all data.
  std::vector<JoinDataType> all_data_vec;
  all_data_vec.reserve(data_vec1.size() + data_vec2.size());
  all_data_vec.insert(all_data_vec.end(), data_vec1.begin(), data_vec1.end());
  all_data_vec.insert(all_data_vec.end(), data_vec2.begin(), data_vec2.end());
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListHolder pl_holder,
      flash_index_storage_->GetPostingList(second_add_id));
  // This pl_holder will only hold a posting list with the data that didn't fit
  // on the first block.
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<JoinDataType> second_block_data,
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

TEST_F(PostingListJoinDataAccessorTest,
       InvalidDataShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  JoinDataType invalid_data = JoinDataType::GetInvalid();
  EXPECT_THAT(pl_accessor->PrependData(invalid_data),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListJoinDataAccessorTest,
       JoinDataNonIncreasingShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  JoinDataType data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(kDefaultNamespaceId, /*fingerprint=*/819));
  ICING_ASSERT_OK(pl_accessor->PrependData(data1));

  JoinDataType data2(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(kDefaultNamespaceId, /*fingerprint=*/818));
  EXPECT_THAT(pl_accessor->PrependData(data2),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  JoinDataType data3(/*document_id=*/1,
                     NamespaceFingerprintIdentifier(kDefaultNamespaceId - 1,
                                                    /*fingerprint=*/820));
  EXPECT_THAT(pl_accessor->PrependData(data3),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  JoinDataType data4(/*document_id=*/0,
                     NamespaceFingerprintIdentifier(kDefaultNamespaceId + 1,
                                                    /*fingerprint=*/820));
  EXPECT_THAT(pl_accessor->PrependData(data4),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListJoinDataAccessorTest,
       NewPostingListNoDataAddedShouldReturnInvalidArgument) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  PostingListAccessor::FinalizeResult result =
      std::move(*pl_accessor).Finalize();
  EXPECT_THAT(result.status,
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(PostingListJoinDataAccessorTest,
       PreexistingPostingListNoDataAddedShouldSucceed) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor1,
      PostingListJoinDataAccessor<JoinDataType>::Create(
          flash_index_storage_.get(), serializer_.get()));
  JoinDataType data1(
      /*document_id=*/1,
      NamespaceFingerprintIdentifier(kDefaultNamespaceId, /*fingerprint=*/819));
  ICING_ASSERT_OK(pl_accessor1->PrependData(data1));
  PostingListAccessor::FinalizeResult result1 =
      std::move(*pl_accessor1).Finalize();
  ICING_ASSERT_OK(result1.status);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor2,
      PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
          flash_index_storage_.get(), serializer_.get(), result1.id));
  PostingListAccessor::FinalizeResult result2 =
      std::move(*pl_accessor2).Finalize();
  EXPECT_THAT(result2.status, IsOk());
}

}  // namespace

}  // namespace lib
}  // namespace icing
