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

#include "icing/file/posting_list/flash-index-storage.h"

#include <unistd.h>

#include <algorithm>
#include <cstdlib>
#include <limits>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage-header.h"
#include "icing/index/hit/hit.h"
#include "icing/index/main/posting-list-hit-serializer.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Ne;
using ::testing::Not;

class FlashIndexStorageTest : public testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/test_dir";
    file_name_ = test_dir_ + "/test_file.idx.index";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(test_dir_.c_str()));

    // TODO(b/249829533): test different serializers
    serializer_ = std::make_unique<PostingListHitSerializer>();
  }

  void TearDown() override {
    serializer_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
  }

 protected:
  std::string test_dir_;
  std::string file_name_;
  Filesystem filesystem_;
  std::unique_ptr<PostingListHitSerializer> serializer_;
};

TEST_F(FlashIndexStorageTest, ReadHeaderMagic) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  }
  EXPECT_THAT(FlashIndexStorage::ReadHeaderMagic(&filesystem_, file_name_),
              IsOkAndHolds(HeaderBlock::Header::kMagic));
}

TEST_F(FlashIndexStorageTest, ReadHeaderMagicOldVersion) {
  int block_size;
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
    block_size = flash_index_storage.block_size();
  }

  int old_magic = 0x6dfba6ae;
  ASSERT_THAT(old_magic, Ne(HeaderBlock::Header::kMagic));
  {
    // Manually modify the header magic.
    ScopedFd sfd(filesystem_.OpenForWrite(file_name_.c_str()));
    ASSERT_THAT(sfd.is_valid(), IsTrue());

    // Read and validate header.
    ICING_ASSERT_OK_AND_ASSIGN(
        HeaderBlock header_block,
        HeaderBlock::Read(&filesystem_, sfd.get(), block_size));
    header_block.header()->magic = old_magic;
    ASSERT_THAT(header_block.Write(sfd.get()), IsTrue());
  }

  EXPECT_THAT(FlashIndexStorage::ReadHeaderMagic(&filesystem_, file_name_),
              IsOkAndHolds(old_magic));
}

TEST_F(FlashIndexStorageTest,
       ReadHeaderMagicNonExistingFileShouldGetNotFoundError) {
  EXPECT_THAT(FlashIndexStorage::ReadHeaderMagic(&filesystem_, file_name_),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(FlashIndexStorageTest, CorruptHeader) {
  {
    // Create the header file
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  }
  {
    // Read the valid header - should pass
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  }
  {
    // Corrupt the header file by changing pl_bytes
    ScopedFd sfd(filesystem_.OpenForWrite(file_name_.c_str()));
    off_t offset = 16;
    uint32_t pl_bytes = sizeof(Hit) - 1;  // This is intentionally invalid
    filesystem_.PWrite(sfd.get(), offset, &pl_bytes, sizeof(uint32_t));
  }
  {
    // Read the header file - should fail because pl_bytes is not divisible
    // by sizeof(Hit), which is 5 as of writing
    ASSERT_THAT(
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
  {
    // Correct the pl_bytes header alignment
    ScopedFd sfd(filesystem_.OpenForWrite(file_name_.c_str()));
    off_t offset = 16;
    uint32_t pl_bytes = 2 * sizeof(Hit);  // Should be valid
    filesystem_.PWrite(sfd.get(), offset, &pl_bytes, sizeof(uint32_t));
  }
  {
    // Read the valid header - should pass
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  }

  // Delete the file
  filesystem_.DeleteFile(file_name_.c_str());
}

TEST_F(FlashIndexStorageTest, EmptyStorage) {
  {
    // Create the header file
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
    // An 'empty' FlashIndexStorage should have:
    //   1. One block allocated for the header
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(1));
    EXPECT_THAT(flash_index_storage.empty(), IsTrue());
    //   2. The invalid DocumentId stored in its header
    EXPECT_THAT(flash_index_storage.get_last_indexed_docid(),
                Eq(kInvalidDocumentId));
    //   3. It's disk usage should be the equivalent of one block.
    EXPECT_THAT(flash_index_storage.GetDiskUsage(),
                Eq(flash_index_storage.block_size()));
  }
  {
    // Read the valid header. All functions should return the same values.
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(1));
    EXPECT_THAT(flash_index_storage.empty(), IsTrue());
    EXPECT_THAT(flash_index_storage.get_last_indexed_docid(),
                Eq(kInvalidDocumentId));
    EXPECT_THAT(flash_index_storage.GetDiskUsage(),
                Eq(flash_index_storage.block_size()));
  }
}

TEST_F(FlashIndexStorageTest, FreeListInMemory) {
  // Create the header file
  ICING_ASSERT_OK_AND_ASSIGN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  {
    // 1. Request a PL that is 1/2 block size. Remember that block size also
    // includes the BlockHeader. The BlockHeader isn't publicly visible, so we
    // subtract 100 bytes to be sure. AllocatePostingList will round up from
    // kHalfBlockPostingListSize to whatever the correct size is.
    const int kHalfBlockPostingListSize =
        (flash_index_storage.block_size() - 100) / 2;
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder1,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    PostingListIdentifier id1 = posting_list_holder1.id;
    EXPECT_THAT(id1.is_valid(), IsTrue());
    //   2. The index file should have grown by exactly one flash block.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits1 = {
        Hit(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/6, /*document_id=*/2, /*term_frequency=*/19),
        Hit(/*section_id=*/5, /*document_id=*/2, /*term_frequency=*/100),
        Hit(/*section_id=*/8, /*document_id=*/5, /*term_frequency=*/197)};
    for (const Hit& hit : hits1) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder1.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder1.posting_list),
                IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));

    // 2. Get another PL. This should be on the same flash block. There should
    // be no allocation.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder2,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder2.id.is_valid(), IsTrue());
    //   2. The index file should not have grown.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits2 = {
        Hit(/*section_id=*/4, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/8, /*document_id=*/4, /*term_frequency=*/19),
        Hit(/*section_id=*/9, /*document_id=*/7, /*term_frequency=*/100),
        Hit(/*section_id=*/6, /*document_id=*/7, /*term_frequency=*/197)};
    for (const Hit& hit : hits2) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder2.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder2.posting_list),
                IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));

    // 3. Now, free the first posting list. This should add it to the free list
    ICING_ASSERT_OK(
        flash_index_storage.FreePostingList(std::move(posting_list_holder1)));

    // 4. Request another posting list. This should NOT grow the index because
    // the first posting list is free.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder3,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder3.id.is_valid(), IsTrue());
    //   2. The index file should not have grown.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());
    //   3. The returned posting list holder should have the same id as the
    //   first posting list holder.
    EXPECT_THAT(posting_list_holder3.id.posting_list_index(),
                Eq(id1.posting_list_index()));
    EXPECT_THAT(posting_list_holder3.id.block_index(), Eq(id1.block_index()));
    // Make sure this pl is empty. The hits that used to be there should be
    // gone.
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                IsOkAndHolds(IsEmpty()));
    std::vector<Hit> hits3 = {
        Hit(/*section_id=*/7, /*document_id=*/1, /*term_frequency=*/62),
        Hit(/*section_id=*/12, /*document_id=*/3, /*term_frequency=*/45),
        Hit(/*section_id=*/11, /*document_id=*/18, /*term_frequency=*/12),
        Hit(/*section_id=*/7, /*document_id=*/100, /*term_frequency=*/74)};
    for (const Hit& hit : hits3) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder3.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                IsOkAndHolds(ElementsAreArray(hits3.rbegin(), hits3.rend())));
  }
  EXPECT_THAT(flash_index_storage.GetDiskUsage(),
              Eq(2 * flash_index_storage.block_size()));
}

TEST_F(FlashIndexStorageTest, FreeListNotInMemory) {
  // Create the header file
  ICING_ASSERT_OK_AND_ASSIGN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get(),
                                /*in_memory=*/false));

  {
    // 1. Request a PL that is 1/2 block size. Remember that block size also
    // includes the BlockHeader. The BlockHeader isn't publicly visible, so we
    // subtract 100 bytes to be sure. AllocatePostingList will round up from
    // kHalfBlockPostingListSize to whatever the correct size is.
    const int kHalfBlockPostingListSize =
        (flash_index_storage.block_size() - 100) / 2;
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder1,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    PostingListIdentifier id1 = posting_list_holder1.id;
    EXPECT_THAT(id1.is_valid(), IsTrue());
    //   2. The index file should have grown by exactly one flash block.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits1 = {
        Hit(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/6, /*document_id=*/2, /*term_frequency=*/19),
        Hit(/*section_id=*/5, /*document_id=*/2, /*term_frequency=*/100),
        Hit(/*section_id=*/8, /*document_id=*/5, /*term_frequency=*/197)};
    for (const Hit& hit : hits1) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder1.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder1.posting_list),
                IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));

    // 2. Get another PL. This should be on the same flash block. There should
    // be no allocation.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder2,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder2.id.is_valid(), IsTrue());
    //   2. The index file should not have grown.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits2 = {
        Hit(/*section_id=*/4, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/8, /*document_id=*/4, /*term_frequency=*/19),
        Hit(/*section_id=*/9, /*document_id=*/7, /*term_frequency=*/100),
        Hit(/*section_id=*/6, /*document_id=*/7, /*term_frequency=*/197)};
    for (const Hit& hit : hits2) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder2.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder2.posting_list),
                IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));

    // 3. Now, free the first posting list. This should add it to the free list
    ICING_ASSERT_OK(
        flash_index_storage.FreePostingList(std::move(posting_list_holder1)));

    // 4. Request another posting list. This should NOT grow the index because
    // the first posting list is free.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder3,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder3.id.is_valid(), IsTrue());
    //   2. The index file should not have grown.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());
    //   3. The returned posting list holder should have the same id as the
    //   first posting list holder.
    EXPECT_THAT(posting_list_holder3.id.posting_list_index(),
                Eq(id1.posting_list_index()));
    EXPECT_THAT(posting_list_holder3.id.block_index(), Eq(id1.block_index()));
    // Make sure this pl is empty. The hits that used to be there should be
    // gone.
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                IsOkAndHolds(IsEmpty()));
    std::vector<Hit> hits3 = {
        Hit(/*section_id=*/7, /*document_id=*/1, /*term_frequency=*/62),
        Hit(/*section_id=*/12, /*document_id=*/3, /*term_frequency=*/45),
        Hit(/*section_id=*/11, /*document_id=*/18, /*term_frequency=*/12),
        Hit(/*section_id=*/7, /*document_id=*/100, /*term_frequency=*/74)};
    for (const Hit& hit : hits3) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder3.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                IsOkAndHolds(ElementsAreArray(hits3.rbegin(), hits3.rend())));
  }
  EXPECT_THAT(flash_index_storage.GetDiskUsage(),
              Eq(2 * flash_index_storage.block_size()));
}

TEST_F(FlashIndexStorageTest, FreeListInMemoryPersistence) {
  PostingListIdentifier id1 = PostingListIdentifier::kInvalid;
  int half_block_posting_list_size = 0;
  {
    // Create the header file
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));

    {
      // 1. Request a PL that is 1/2 block size. Remember that block size also
      // includes the BlockHeader. The BlockHeader isn't publicly visible, so we
      // subtract 100 bytes to be sure. AllocatePostingList will round up from
      // kHalfBlockPostingListSize to whatever the correct size is.
      half_block_posting_list_size =
          (flash_index_storage.block_size() - 100) / 2;
      ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder posting_list_holder1,
                                 flash_index_storage.AllocatePostingList(
                                     half_block_posting_list_size));
      // We expect:
      //   1. FlashIndexStorage will return a valid id.
      id1 = posting_list_holder1.id;
      EXPECT_THAT(id1.is_valid(), IsTrue());
      //   2. The index file should have grown by exactly one flash block.
      EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
      EXPECT_THAT(flash_index_storage.empty(), IsFalse());

      std::vector<Hit> hits1 = {
          Hit(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/12),
          Hit(/*section_id=*/6, /*document_id=*/2, /*term_frequency=*/19),
          Hit(/*section_id=*/5, /*document_id=*/2, /*term_frequency=*/100),
          Hit(/*section_id=*/8, /*document_id=*/5, /*term_frequency=*/197)};
      for (const Hit& hit : hits1) {
        ICING_ASSERT_OK(
            serializer_->PrependHit(&posting_list_holder1.posting_list, hit));
      }
      EXPECT_THAT(serializer_->GetHits(&posting_list_holder1.posting_list),
                  IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));

      // 2. Get another PL. This should be on the same flash block. There should
      // be no allocation.
      ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder posting_list_holder2,
                                 flash_index_storage.AllocatePostingList(
                                     half_block_posting_list_size));
      // We expect:
      //   1. FlashIndexStorage will return a valid id.
      EXPECT_THAT(posting_list_holder2.id.is_valid(), IsTrue());
      //   2. The index file should not have grown.
      EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
      EXPECT_THAT(flash_index_storage.empty(), IsFalse());

      std::vector<Hit> hits2 = {
          Hit(/*section_id=*/4, /*document_id=*/0, /*term_frequency=*/12),
          Hit(/*section_id=*/8, /*document_id=*/4, /*term_frequency=*/19),
          Hit(/*section_id=*/9, /*document_id=*/7, /*term_frequency=*/100),
          Hit(/*section_id=*/6, /*document_id=*/7, /*term_frequency=*/197)};
      for (const Hit& hit : hits2) {
        ICING_ASSERT_OK(
            serializer_->PrependHit(&posting_list_holder2.posting_list, hit));
      }
      EXPECT_THAT(serializer_->GetHits(&posting_list_holder2.posting_list),
                  IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));

      // 3. Now, free the first posting list. This should add it to the free
      // list
      ICING_ASSERT_OK(
          flash_index_storage.FreePostingList(std::move(posting_list_holder1)));
    }

    EXPECT_THAT(flash_index_storage.GetDiskUsage(),
                Eq(2 * flash_index_storage.block_size()));
    // 4. The FlashIndexStorage should go out of scope and flush the in-memory
    // posting list to disk
  }

  {
    // Recreate the flash index.
    ICING_ASSERT_OK_AND_ASSIGN(
        FlashIndexStorage flash_index_storage,
        FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));

    {
      // 5. Request another posting list. This should NOT grow the index because
      // the first posting list is free.
      ICING_ASSERT_OK_AND_ASSIGN(PostingListHolder posting_list_holder3,
                                 flash_index_storage.AllocatePostingList(
                                     half_block_posting_list_size));
      // We expect:
      //   1. FlashIndexStorage will return a valid id.
      EXPECT_THAT(posting_list_holder3.id.is_valid(), IsTrue());
      //   2. The index file should not have grown.
      EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
      EXPECT_THAT(flash_index_storage.empty(), IsFalse());
      //   3. The returned posting list holder should have the same id as the
      //   first posting list holder.
      EXPECT_THAT(posting_list_holder3.id.posting_list_index(),
                  Eq(id1.posting_list_index()));
      EXPECT_THAT(posting_list_holder3.id.block_index(), Eq(id1.block_index()));
      // Make sure this pl is empty. The hits that used to be there should be
      // gone.
      EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                  IsOkAndHolds(IsEmpty()));
      std::vector<Hit> hits3 = {
          Hit(/*section_id=*/7, /*document_id=*/1, /*term_frequency=*/62),
          Hit(/*section_id=*/12, /*document_id=*/3, /*term_frequency=*/45),
          Hit(/*section_id=*/11, /*document_id=*/18, /*term_frequency=*/12),
          Hit(/*section_id=*/7, /*document_id=*/100, /*term_frequency=*/74)};
      for (const Hit& hit : hits3) {
        ICING_ASSERT_OK(
            serializer_->PrependHit(&posting_list_holder3.posting_list, hit));
      }
      EXPECT_THAT(serializer_->GetHits(&posting_list_holder3.posting_list),
                  IsOkAndHolds(ElementsAreArray(hits3.rbegin(), hits3.rend())));
    }
    EXPECT_THAT(flash_index_storage.GetDiskUsage(),
                Eq(2 * flash_index_storage.block_size()));
  }
}

TEST_F(FlashIndexStorageTest, DifferentSizedPostingLists) {
  // Create the header file
  ICING_ASSERT_OK_AND_ASSIGN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));
  {
    // 1. Request a PL that is 1/2 block size. Remember that block size also
    // includes the BlockHeader. The BlockHeader isn't publicly visible, so we
    // subtract 100 bytes to be sure. AllocatePostingList will round up from
    // kHalfBlockPostingListSize to whatever the correct size is.
    const int kHalfBlockPostingListSize =
        (flash_index_storage.block_size() - 100) / 2;
    const int kQuarterBlockPostingListSize =
        (flash_index_storage.block_size() - 100) / 4;
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder1,
        flash_index_storage.AllocatePostingList(kHalfBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    PostingListIdentifier id1 = posting_list_holder1.id;
    EXPECT_THAT(id1.is_valid(), IsTrue());
    //   2. The index file should have grown by exactly one flash block.
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(2));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits1 = {
        Hit(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/6, /*document_id=*/2, /*term_frequency=*/19),
        Hit(/*section_id=*/5, /*document_id=*/2, /*term_frequency=*/100),
        Hit(/*section_id=*/8, /*document_id=*/5, /*term_frequency=*/197)};
    for (const Hit& hit : hits1) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder1.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder1.posting_list),
                IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));

    // 2. Get a PL that is 1/4 block size. Even though a 1/4 block PL could
    // theoretically fit in the same block, we'll allocate a new one because PLs
    // on a block are required to be the same size.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder2,
        flash_index_storage.AllocatePostingList(kQuarterBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder2.id.is_valid(), IsTrue());
    //   2. The index file should have grown by one block.
    EXPECT_THAT(posting_list_holder2.id.block_index(),
                Not(Eq(id1.block_index())));
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(3));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());

    std::vector<Hit> hits2 = {
        Hit(/*section_id=*/4, /*document_id=*/0, /*term_frequency=*/12),
        Hit(/*section_id=*/8, /*document_id=*/4, /*term_frequency=*/19),
        Hit(/*section_id=*/9, /*document_id=*/7, /*term_frequency=*/100),
        Hit(/*section_id=*/6, /*document_id=*/7, /*term_frequency=*/197)};
    for (const Hit& hit : hits2) {
      ICING_ASSERT_OK(
          serializer_->PrependHit(&posting_list_holder2.posting_list, hit));
    }
    EXPECT_THAT(serializer_->GetHits(&posting_list_holder2.posting_list),
                IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));

    // 3. Request another 1/4 block-size posting list. This should NOT grow the
    // index because there should be three free posting lists on block2.
    ICING_ASSERT_OK_AND_ASSIGN(
        PostingListHolder posting_list_holder3,
        flash_index_storage.AllocatePostingList(kQuarterBlockPostingListSize));
    // We expect:
    //   1. FlashIndexStorage will return a valid id.
    EXPECT_THAT(posting_list_holder3.id.is_valid(), IsTrue());
    //   2. The index file should have remained the same size as before and the
    //      third posting list holder should use the same block as the second
    //      posting list holder.
    EXPECT_THAT(posting_list_holder3.id.block_index(),
                Eq(posting_list_holder2.id.block_index()));
    EXPECT_THAT(flash_index_storage.num_blocks(), Eq(3));
    EXPECT_THAT(flash_index_storage.empty(), IsFalse());
  }
  EXPECT_THAT(flash_index_storage.GetDiskUsage(),
              Eq(3 * flash_index_storage.block_size()));
}

TEST_F(FlashIndexStorageTest, AllocateTooLargePostingList) {
  // Create the header file
  ICING_ASSERT_OK_AND_ASSIGN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(file_name_, &filesystem_, serializer_.get()));

  // Request a PL that is 2x block size.
  const int kDoubleBlockSize = flash_index_storage.block_size() * 2;
  EXPECT_THAT(flash_index_storage.AllocatePostingList(kDoubleBlockSize),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
