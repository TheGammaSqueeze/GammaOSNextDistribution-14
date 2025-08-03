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

#include "icing/file/file-backed-vector.h"

#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/mock-filesystem.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsTrue;
using ::testing::Lt;
using ::testing::Not;
using ::testing::Pointee;
using ::testing::SizeIs;

namespace icing {
namespace lib {

namespace {

class FileBackedVectorTest : public testing::Test {
 protected:
  void SetUp() override {
    file_path_ = GetTestTempDir() + "/test.array";
    fd_ = filesystem_.OpenForWrite(file_path_.c_str());
    ASSERT_NE(-1, fd_);
    ASSERT_TRUE(filesystem_.Truncate(fd_, 0));
  }

  void TearDown() override {
    close(fd_);
    filesystem_.DeleteFile(file_path_.c_str());
  }

  // Helper method to loop over some data and insert into the vector at some idx
  template <typename T>
  void Insert(FileBackedVector<T>* vector, int32_t idx,
              const std::vector<T>& data) {
    for (int i = 0; i < data.size(); ++i) {
      ICING_ASSERT_OK(vector->Set(idx + i, data.at(i)));
    }
  }

  void Insert(FileBackedVector<char>* vector, int32_t idx, std::string data) {
    Insert(vector, idx, std::vector<char>(data.begin(), data.end()));
  }

  // Helper method to retrieve data from the beginning of the vector
  template <typename T>
  std::vector<T> Get(FileBackedVector<T>* vector, int32_t idx,
                     int32_t expected_len) {
    return std::vector<T>(vector->array() + idx,
                          vector->array() + idx + expected_len);
  }

  std::string_view Get(FileBackedVector<char>* vector, int32_t expected_len) {
    return Get(vector, 0, expected_len);
  }

  std::string_view Get(FileBackedVector<char>* vector, int32_t idx,
                       int32_t expected_len) {
    return std::string_view(vector->array() + idx, expected_len);
  }

  const Filesystem& filesystem() const { return filesystem_; }

  Filesystem filesystem_;
  std::string file_path_;
  int fd_;
};

TEST_F(FileBackedVectorTest, Create) {
  {
    // Create a vector for a new file
    ICING_ASSERT_OK_AND_ASSIGN(
        auto vector, FileBackedVector<char>::Create(
                         filesystem_, file_path_,
                         MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  }

  {
    // We can create it again based on the same file.
    ICING_ASSERT_OK_AND_ASSIGN(
        auto vector, FileBackedVector<char>::Create(
                         filesystem_, file_path_,
                         MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  }
}

TEST_F(FileBackedVectorTest, CreateWithInvalidStrategy) {
  // Create a vector with unimplemented strategy
  EXPECT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_MANUAL_SYNC),
              StatusIs(libtextclassifier3::StatusCode::UNIMPLEMENTED));
}

TEST_F(FileBackedVectorTest, CreateWithCustomMaxFileSize) {
  int32_t header_size = FileBackedVector<char>::Header::kHeaderSize;

  // Create a vector with invalid max_file_size
  EXPECT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/header_size - 1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/header_size + sizeof(char) - 1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  {
    // Create a vector with max_file_size that allows only 1 element.
    ICING_ASSERT_OK_AND_ASSIGN(
        auto vector, FileBackedVector<char>::Create(
                         filesystem_, file_path_,
                         MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                         /*max_file_size=*/header_size + sizeof(char) * 1));
    ICING_ASSERT_OK(vector->Set(0, 'a'));
  }

  {
    // We can create it again with larger max_file_size, as long as it is not
    // greater than kMaxFileSize.
    ICING_ASSERT_OK_AND_ASSIGN(
        auto vector, FileBackedVector<char>::Create(
                         filesystem_, file_path_,
                         MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                         /*max_file_size=*/header_size + sizeof(char) * 2));
    EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(Eq('a'))));
    ICING_ASSERT_OK(vector->Set(1, 'b'));
  }

  // We cannot create it again with max_file_size < current_file_size, even if
  // it is a valid value.
  int64_t current_file_size = filesystem_.GetFileSize(file_path_.c_str());
  ASSERT_THAT(current_file_size, Eq(header_size + sizeof(char) * 2));
  ASSERT_THAT(current_file_size - 1, Not(Lt(header_size + sizeof(char))));
  EXPECT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/current_file_size - 1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  {
    // We can create it again with max_file_size == current_file_size.
    ICING_ASSERT_OK_AND_ASSIGN(
        auto vector, FileBackedVector<char>::Create(
                         filesystem_, file_path_,
                         MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                         /*max_file_size=*/current_file_size));
    EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(Eq('a'))));
    EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(Eq('b'))));
  }
}

TEST_F(FileBackedVectorTest, SimpleShared) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  std::string expected = "abcde";
  Insert(vector.get(), 0, expected);
  EXPECT_EQ(expected.length(), vector->num_elements());
  EXPECT_EQ(expected, Get(vector.get(), expected.length()));

  uint32_t good_crc_value = 1134899064U;
  const Crc32 good_crc(good_crc_value);
  // Explicit call to update the crc does update the value
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(good_crc));

  // PersistToDisk does nothing bad.
  ICING_EXPECT_OK(vector->PersistToDisk());

  // Close out the old vector to ensure everything persists properly before we
  // reassign it
  vector.reset();

  // Write a bad crc, this would be a mismatch compared to the computed crc of
  // the contents on reinitialization.
  uint32_t bad_crc_value = 123;
  filesystem_.PWrite(file_path_.data(),
                     offsetof(FileBackedVector<char>::Header, vector_checksum),
                     &bad_crc_value, sizeof(bad_crc_value));

  ASSERT_THAT(FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  // Get it back into an ok state
  filesystem_.PWrite(file_path_.data(),
                     offsetof(FileBackedVector<char>::Header, vector_checksum),
                     &good_crc_value, sizeof(good_crc_value));
  ICING_ASSERT_OK_AND_ASSIGN(
      vector, FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_EQ(expected, Get(vector.get(), expected.length()));

  // Close out the old vector to ensure everything persists properly before we
  // reassign it
  vector.reset();

  // Can reinitialize it safely
  ICING_ASSERT_OK_AND_ASSIGN(
      vector, FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  // Truncate the content
  ICING_EXPECT_OK(vector->TruncateTo(0));

  // Crc is cleared after truncation and reset to 0.
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));
  EXPECT_EQ(0u, vector->num_elements());
}

TEST_F(FileBackedVectorTest, Get) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  std::string expected = "abc";
  Insert(vector.get(), 0, expected);
  EXPECT_EQ(expected.length(), vector->num_elements());

  EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(Eq('a'))));
  EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(Eq('b'))));
  EXPECT_THAT(vector->Get(2), IsOkAndHolds(Pointee(Eq('c'))));

  // Out of bounds error
  EXPECT_THAT(vector->Get(3),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Get(-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, SetWithoutGrowing) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  std::string original = "abcde";
  Insert(vector.get(), /*idx=*/0, original);
  ASSERT_THAT(vector->num_elements(), Eq(original.length()));
  ASSERT_THAT(Get(vector.get(), /*idx=*/0, /*expected_len=*/5), Eq(original));

  ICING_EXPECT_OK(vector->Set(/*idx=*/1, /*len=*/3, 'z'));
  EXPECT_THAT(vector->num_elements(), Eq(5));
  EXPECT_THAT(Get(vector.get(), /*idx=*/0, /*expected_len=*/5), Eq("azzze"));
}

TEST_F(FileBackedVectorTest, SetWithGrowing) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  std::string original = "abcde";
  Insert(vector.get(), /*idx=*/0, original);
  ASSERT_THAT(vector->num_elements(), Eq(original.length()));
  ASSERT_THAT(Get(vector.get(), /*idx=*/0, /*expected_len=*/5), Eq(original));

  ICING_EXPECT_OK(vector->Set(/*idx=*/3, /*len=*/4, 'z'));
  EXPECT_THAT(vector->num_elements(), Eq(7));
  EXPECT_THAT(Get(vector.get(), /*idx=*/0, /*expected_len=*/7), Eq("abczzzz"));
}

TEST_F(FileBackedVectorTest, SetInvalidArguments) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_THAT(vector->Set(/*idx=*/0, /*len=*/-1, 'z'),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Set(/*idx=*/0, /*len=*/0, 'z'),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Set(/*idx=*/-1, /*len=*/2, 'z'),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Set(/*idx=*/100,
                          /*len=*/std::numeric_limits<int32_t>::max(), 'z'),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, MutableView) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::string(1000, 'a'));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2620640643U)));

  ICING_ASSERT_OK_AND_ASSIGN(FileBackedVector<char>::MutableView mutable_elt,
                             vector->GetMutable(3));

  mutable_elt.Get() = 'b';
  EXPECT_THAT(vector->Get(3), IsOkAndHolds(Pointee(Eq('b'))));

  mutable_elt.Get() = 'c';
  EXPECT_THAT(vector->Get(3), IsOkAndHolds(Pointee(Eq('c'))));
}

TEST_F(FileBackedVectorTest, MutableViewShouldSetDirty) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::string(1000, 'a'));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2620640643U)));

  std::string_view reconstructed_view =
      std::string_view(vector->array(), vector->num_elements());

  ICING_ASSERT_OK_AND_ASSIGN(FileBackedVector<char>::MutableView mutable_elt,
                             vector->GetMutable(3));

  // Mutate the element via MutateView
  // If non-const Get() is called, MutateView should set the element index dirty
  // so that ComputeChecksum() can pick up the change and compute the checksum
  // correctly. Validate by mapping another array on top.
  mutable_elt.Get() = 'b';
  ASSERT_THAT(vector->Get(3), IsOkAndHolds(Pointee(Eq('b'))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc1, vector->ComputeChecksum());
  Crc32 full_crc1;
  full_crc1.Append(reconstructed_view);
  EXPECT_THAT(crc1, Eq(full_crc1));

  // Mutate and test again.
  mutable_elt.Get() = 'c';
  ASSERT_THAT(vector->Get(3), IsOkAndHolds(Pointee(Eq('c'))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc2, vector->ComputeChecksum());
  Crc32 full_crc2;
  full_crc2.Append(reconstructed_view);
  EXPECT_THAT(crc2, Eq(full_crc2));
}

TEST_F(FileBackedVectorTest, MutableArrayView) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::vector<int>(/*count=*/100, /*value=*/1));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2494890115U)));

  constexpr int kArrayViewOffset = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedVector<int>::MutableArrayView mutable_arr,
      vector->GetMutable(kArrayViewOffset, /*len=*/3));
  EXPECT_THAT(mutable_arr, SizeIs(3));

  mutable_arr[0] = 2;
  mutable_arr[1] = 3;
  mutable_arr[2] = 4;

  EXPECT_THAT(vector->Get(kArrayViewOffset + 0), IsOkAndHolds(Pointee(Eq(2))));
  EXPECT_THAT(mutable_arr.data()[0], Eq(2));

  EXPECT_THAT(vector->Get(kArrayViewOffset + 1), IsOkAndHolds(Pointee(Eq(3))));
  EXPECT_THAT(mutable_arr.data()[1], Eq(3));

  EXPECT_THAT(vector->Get(kArrayViewOffset + 2), IsOkAndHolds(Pointee(Eq(4))));
  EXPECT_THAT(mutable_arr.data()[2], Eq(4));
}

TEST_F(FileBackedVectorTest, MutableArrayViewSetArray) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::vector<int>(/*count=*/100, /*value=*/1));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2494890115U)));

  constexpr int kArrayViewOffset = 3;
  constexpr int kArrayViewLen = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedVector<int>::MutableArrayView mutable_arr,
      vector->GetMutable(kArrayViewOffset, kArrayViewLen));

  std::vector<int> change1{2, 3, 4};
  mutable_arr.SetArray(/*idx=*/0, change1.data(), change1.size());
  EXPECT_THAT(Get(vector.get(), kArrayViewOffset, kArrayViewLen),
              ElementsAre(2, 3, 4, 1, 1));

  std::vector<int> change2{5, 6};
  mutable_arr.SetArray(/*idx=*/2, change2.data(), change2.size());
  EXPECT_THAT(Get(vector.get(), kArrayViewOffset, kArrayViewLen),
              ElementsAre(2, 3, 5, 6, 1));
}

TEST_F(FileBackedVectorTest, MutableArrayViewSetArrayWithZeroLength) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::vector<int>(/*count=*/100, /*value=*/1));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2494890115U)));

  constexpr int kArrayViewOffset = 3;
  constexpr int kArrayViewLen = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedVector<int>::MutableArrayView mutable_arr,
      vector->GetMutable(kArrayViewOffset, kArrayViewLen));

  // Zero arr_len should work and change nothing
  std::vector<int> change{2, 3};
  mutable_arr.SetArray(/*idx=*/0, change.data(), /*arr_len=*/0);
  EXPECT_THAT(Get(vector.get(), kArrayViewOffset, kArrayViewLen),
              ElementsAre(1, 1, 1, 1, 1));
}

TEST_F(FileBackedVectorTest, MutableArrayViewIndexOperatorShouldSetDirty) {
  // Create an array with some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::vector<int>(/*count=*/100, /*value=*/1));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2494890115U)));

  std::string_view reconstructed_view(
      reinterpret_cast<const char*>(vector->array()),
      vector->num_elements() * sizeof(int));

  constexpr int kArrayViewOffset = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedVector<int>::MutableArrayView mutable_arr,
      vector->GetMutable(kArrayViewOffset, /*len=*/3));

  // Use operator[] to mutate elements
  // If non-const operator[] is called, MutateView should set the element index
  // dirty so that ComputeChecksum() can pick up the change and compute the
  // checksum correctly. Validate by mapping another array on top.
  mutable_arr[0] = 2;
  ASSERT_THAT(vector->Get(kArrayViewOffset + 0), IsOkAndHolds(Pointee(Eq(2))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc1, vector->ComputeChecksum());
  EXPECT_THAT(crc1, Eq(Crc32(reconstructed_view)));

  mutable_arr[1] = 3;
  ASSERT_THAT(vector->Get(kArrayViewOffset + 1), IsOkAndHolds(Pointee(Eq(3))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc2, vector->ComputeChecksum());
  EXPECT_THAT(crc2, Eq(Crc32(reconstructed_view)));

  mutable_arr[2] = 4;
  ASSERT_THAT(vector->Get(kArrayViewOffset + 2), IsOkAndHolds(Pointee(Eq(4))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc3, vector->ComputeChecksum());
  EXPECT_THAT(crc3, Eq(Crc32(reconstructed_view)));

  // Change the same position. It should set dirty again.
  mutable_arr[0] = 5;
  ASSERT_THAT(vector->Get(kArrayViewOffset + 0), IsOkAndHolds(Pointee(Eq(5))));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc4, vector->ComputeChecksum());
  EXPECT_THAT(crc4, Eq(Crc32(reconstructed_view)));
}

TEST_F(FileBackedVectorTest, MutableArrayViewSetArrayShouldSetDirty) {
  // Create an array with some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::vector<int>(/*count=*/100, /*value=*/1));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2494890115U)));

  std::string_view reconstructed_view(
      reinterpret_cast<const char*>(vector->array()),
      vector->num_elements() * sizeof(int));

  constexpr int kArrayViewOffset = 3;
  constexpr int kArrayViewLen = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedVector<int>::MutableArrayView mutable_arr,
      vector->GetMutable(kArrayViewOffset, kArrayViewLen));

  std::vector<int> change{2, 3, 4};
  mutable_arr.SetArray(/*idx=*/0, change.data(), change.size());
  ASSERT_THAT(Get(vector.get(), kArrayViewOffset, kArrayViewLen),
              ElementsAre(2, 3, 4, 1, 1));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc, vector->ComputeChecksum());
  EXPECT_THAT(crc, Eq(Crc32(reconstructed_view)));
}

TEST_F(FileBackedVectorTest, Append) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ASSERT_THAT(vector->num_elements(), Eq(0));

  ICING_EXPECT_OK(vector->Append('a'));
  EXPECT_THAT(vector->num_elements(), Eq(1));
  EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(Eq('a'))));

  ICING_EXPECT_OK(vector->Append('b'));
  EXPECT_THAT(vector->num_elements(), Eq(2));
  EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(Eq('b'))));
}

TEST_F(FileBackedVectorTest, AppendAfterSet) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ASSERT_THAT(vector->num_elements(), Eq(0));

  ICING_ASSERT_OK(vector->Set(9, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(10));
  ICING_EXPECT_OK(vector->Append('a'));
  EXPECT_THAT(vector->num_elements(), Eq(11));
  EXPECT_THAT(vector->Get(10), IsOkAndHolds(Pointee(Eq('a'))));
}

TEST_F(FileBackedVectorTest, AppendAfterTruncate) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::string(1000, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(1000));

  ICING_ASSERT_OK(vector->TruncateTo(5));
  ICING_EXPECT_OK(vector->Append('a'));
  EXPECT_THAT(vector->num_elements(), Eq(6));
  EXPECT_THAT(vector->Get(5), IsOkAndHolds(Pointee(Eq('a'))));
}

TEST_F(FileBackedVectorTest, AppendShouldFailIfExceedingMaxFileSize) {
  int32_t max_file_size = (1 << 10) - 1;
  int32_t max_num_elements =
      (max_file_size - FileBackedVector<char>::Header::kHeaderSize) /
      sizeof(char);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size));
  ICING_ASSERT_OK(vector->Set(max_num_elements - 1, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(max_num_elements));

  EXPECT_THAT(vector->Append('a'),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, Allocate) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ASSERT_THAT(vector->num_elements(), Eq(0));

  ICING_ASSERT_OK_AND_ASSIGN(
      typename FileBackedVector<char>::MutableArrayView mutable_arr,
      vector->Allocate(3));
  EXPECT_THAT(vector->num_elements(), Eq(3));
  EXPECT_THAT(mutable_arr, SizeIs(3));
  std::string change = "abc";
  mutable_arr.SetArray(/*idx=*/0, /*arr=*/change.data(), /*arr_len=*/3);
  EXPECT_THAT(Get(vector.get(), /*idx=*/0, /*expected_len=*/3), Eq(change));
}

TEST_F(FileBackedVectorTest, AllocateAfterSet) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ASSERT_THAT(vector->num_elements(), Eq(0));

  ICING_ASSERT_OK(vector->Set(9, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(10));
  ICING_ASSERT_OK_AND_ASSIGN(
      typename FileBackedVector<char>::MutableArrayView mutable_arr,
      vector->Allocate(3));
  EXPECT_THAT(vector->num_elements(), Eq(13));
  EXPECT_THAT(mutable_arr, SizeIs(3));
  std::string change = "abc";
  mutable_arr.SetArray(/*idx=*/0, /*arr=*/change.data(), /*arr_len=*/3);
  EXPECT_THAT(Get(vector.get(), /*idx=*/10, /*expected_len=*/3), Eq(change));
}

TEST_F(FileBackedVectorTest, AllocateAfterTruncate) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), /*idx=*/0, std::string(1000, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(1000));

  ICING_ASSERT_OK(vector->TruncateTo(5));
  ICING_ASSERT_OK_AND_ASSIGN(
      typename FileBackedVector<char>::MutableArrayView mutable_arr,
      vector->Allocate(3));
  EXPECT_THAT(vector->num_elements(), Eq(8));
  std::string change = "abc";
  mutable_arr.SetArray(/*idx=*/0, /*arr=*/change.data(), /*arr_len=*/3);
  EXPECT_THAT(Get(vector.get(), /*idx=*/5, /*expected_len=*/3), Eq(change));
}

TEST_F(FileBackedVectorTest, AllocateInvalidLengthShouldFail) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ASSERT_THAT(vector->num_elements(), Eq(0));

  EXPECT_THAT(vector->Allocate(-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->num_elements(), Eq(0));

  EXPECT_THAT(vector->Allocate(0),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->num_elements(), Eq(0));
}

TEST_F(FileBackedVectorTest, AllocateShouldFailIfExceedingMaxFileSize) {
  int32_t max_file_size = (1 << 10) - 1;
  int32_t max_num_elements =
      (max_file_size - FileBackedVector<char>::Header::kHeaderSize) /
      sizeof(char);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size));
  ICING_ASSERT_OK(vector->Set(max_num_elements - 3, 'z'));
  ASSERT_THAT(vector->num_elements(), Eq(max_num_elements - 2));

  EXPECT_THAT(vector->Allocate(3),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Allocate(2), IsOk());
}

TEST_F(FileBackedVectorTest, IncrementalCrc_NonOverlappingChanges) {
  int num_elements = 1000;
  int incremental_size = 3;
  // Create an array with some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  Insert(vector.get(), 0, std::string(num_elements, 'a'));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2620640643U)));

  // Non-overlapping changes to the array, with increasing intervals
  // between updating the checksum. Validate by mapping another array on top.
  uint32_t next_update = 2;
  for (uint32_t i = 0; i < num_elements; i += incremental_size) {
    Insert(vector.get(), i, std::string(incremental_size, 'b'));

    if (i >= next_update) {
      ICING_ASSERT_OK_AND_ASSIGN(Crc32 incremental_crc,
                                 vector->ComputeChecksum());
      ICING_LOG(INFO) << "Now crc @" << incremental_crc.Get();

      Crc32 full_crc;
      std::string_view reconstructed_view =
          std::string_view(vector->array(), vector->num_elements());
      full_crc.Append(reconstructed_view);

      ASSERT_EQ(incremental_crc, full_crc);
      next_update *= 2;
    }
  }

  for (uint32_t i = 0; i < num_elements; ++i) {
    EXPECT_THAT(vector->Get(i), IsOkAndHolds(Pointee(Eq('b'))));
  }
}

TEST_F(FileBackedVectorTest, IncrementalCrc_OverlappingChanges) {
  int num_elements = 1000;
  int incremental_size = 3;
  // Create an array with some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  Insert(vector.get(), 0, std::string(num_elements, 'a'));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(2620640643U)));

  // Overlapping changes to the array, with increasing intervals
  // between updating the checksum. Validate by mapping another array on top.
  uint32_t next_update = 2;
  for (uint32_t i = 0; i < num_elements; i++) {
    Insert(vector.get(), i, std::string(incremental_size, 'b'));

    if (i >= next_update) {
      ICING_ASSERT_OK_AND_ASSIGN(Crc32 incremental_crc,
                                 vector->ComputeChecksum());
      ICING_LOG(INFO) << "Now crc @" << incremental_crc.Get();

      Crc32 full_crc;
      std::string_view reconstructed_view =
          std::string_view(vector->array(), vector->num_elements());
      full_crc.Append(reconstructed_view);

      ASSERT_EQ(incremental_crc, full_crc);
      next_update *= 2;
    }
  }
  for (uint32_t i = 0; i < num_elements; ++i) {
    EXPECT_THAT(vector->Get(i), IsOkAndHolds(Pointee(Eq('b'))));
  }
}

TEST_F(FileBackedVectorTest, SetIntMaxShouldReturnOutOfRangeError) {
  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int32_t>> vector,
      FileBackedVector<int32_t>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  // It is an edge case. Since Set() calls GrowIfNecessary(idx + 1), we have to
  // make sure that when idx is INT32_MAX, Set() should handle it correctly.
  EXPECT_THAT(vector->Set(std::numeric_limits<int32_t>::max(), 1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, Grow) {
  int32_t max_file_size = (1 << 20) - 1;
  int32_t header_size = FileBackedVector<int32_t>::Header::kHeaderSize;
  int32_t element_type_size = static_cast<int32_t>(sizeof(int32_t));

  // Max file size includes size of the header and elements, so max # of
  // elements will be (max_file_size - header_size) / element_type_size.
  //
  // Also ensure that (max_file_size - header_size) is not a multiple of
  // element_type_size, in order to test if the desired # of elements is
  // computed by (math) floor instead of ceil.
  ASSERT_THAT((max_file_size - header_size) % element_type_size, Not(Eq(0)));
  int32_t max_num_elements = (max_file_size - header_size) / element_type_size;

  ASSERT_TRUE(filesystem_.Truncate(fd_, 0));

  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int32_t>> vector,
      FileBackedVector<int32_t>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));
  // max_num_elements is the allowed max # of elements, so the valid index
  // should be 0 to max_num_elements-1.
  EXPECT_THAT(vector->Set(max_num_elements, 1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Set(-1, 1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Set(max_num_elements - 1, 1), IsOk());

  int32_t start = max_num_elements - 5;
  std::vector<int32_t> data{1, 2, 3, 4, 5};
  Insert(vector.get(), start, data);

  // Crc works?
  const Crc32 good_crc(650981917U);
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(good_crc));

  // PersistToDisk does nothing bad, and ensures the content is still there
  // after we recreate the vector
  ICING_EXPECT_OK(vector->PersistToDisk());

  // Close out the old vector to ensure everything persists properly before we
  // reassign it
  vector.reset();

  ICING_ASSERT_OK_AND_ASSIGN(
      vector,
      FileBackedVector<int32_t>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size));

  EXPECT_THAT(Get(vector.get(), start, data.size()), Eq(data));
}

TEST_F(FileBackedVectorTest, GrowsInChunks) {
  // This is the same value as FileBackedVector::kGrowElements
  constexpr int32_t kGrowElements = 1U << 14;  // 16K

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  // Our initial file size should just be the size of the header. Disk usage
  // will indicate that one block has been allocated, which contains the header.
  int header_size = sizeof(FileBackedVector<char>::Header);
  int page_size = getpagesize();
  EXPECT_THAT(filesystem_.GetFileSize(fd_), Eq(header_size));
  EXPECT_THAT(filesystem_.GetDiskUsage(fd_), Eq(page_size));

  // Once we add something though, we'll grow to be kGrowElements big. From this
  // point on, file size and disk usage should be the same because Growing will
  // explicitly allocate the number of blocks needed to accomodate the file.
  Insert(vector.get(), 0, {1});
  int file_size = 1 * kGrowElements * sizeof(int);
  EXPECT_THAT(filesystem_.GetFileSize(fd_), Eq(file_size));
  EXPECT_THAT(filesystem_.GetDiskUsage(fd_), Eq(file_size));

  // Should still be the same size, don't need to grow underlying file
  Insert(vector.get(), 1, {2});
  EXPECT_THAT(filesystem_.GetFileSize(fd_), Eq(file_size));
  EXPECT_THAT(filesystem_.GetDiskUsage(fd_), Eq(file_size));

  // Now we grow by a kGrowElements chunk, so the underlying file is 2
  // kGrowElements big
  file_size = 2 * kGrowElements * sizeof(int);
  Insert(vector.get(), 2, std::vector<int>(kGrowElements, 3));
  EXPECT_THAT(filesystem_.GetFileSize(fd_), Eq(file_size));
  EXPECT_THAT(filesystem_.GetDiskUsage(fd_), Eq(file_size));

  // Destroy/persist the contents.
  vector.reset();

  // Reinitialize
  ICING_ASSERT_OK_AND_ASSIGN(
      vector, FileBackedVector<int>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  // Should be the same file size as before
  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
              Eq(kGrowElements * 2 * sizeof(int)));
}

TEST_F(FileBackedVectorTest, Delete) {
  // Can delete even if there's nothing there
  ICING_EXPECT_OK(FileBackedVector<int64_t>::Delete(filesystem_, file_path_));

  // Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  std::string expected = "abcde";
  Insert(vector.get(), 0, expected);
  ASSERT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(1134899064U)));
  ASSERT_EQ(expected.length(), vector->num_elements());

  // Close out the old vector to ensure everything persists properly before we
  // delete the underlying files
  vector.reset();

  ICING_EXPECT_OK(FileBackedVector<int64_t>::Delete(filesystem_, file_path_));

  EXPECT_FALSE(filesystem_.FileExists(file_path_.data()));

  // Can successfully create again.
  ICING_ASSERT_OK_AND_ASSIGN(
      vector, FileBackedVector<char>::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
}

TEST_F(FileBackedVectorTest, TruncateTo) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  Insert(vector.get(), 0, "A");
  Insert(vector.get(), 1, "Z");

  EXPECT_EQ(2, vector->num_elements());
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(1658635950)));

  // Modify 1 element, out of 2 total elements. 1/2 changes exceeds the partial
  // crc limit, so our next checksum call will recompute the entire vector's
  // checksum.
  Insert(vector.get(), 1, "J");
  // We'll ignore everything after the 1st element, so the full vector's
  // checksum will only include "J".
  ICING_EXPECT_OK(vector->TruncateTo(1));
  EXPECT_EQ(1, vector->num_elements());
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(31158534)));

  // Truncating clears the checksum and resets it to 0
  ICING_EXPECT_OK(vector->TruncateTo(0));
  EXPECT_EQ(0, vector->num_elements());
  EXPECT_THAT(vector->ComputeChecksum(), IsOkAndHolds(Crc32(0)));

  // Can't truncate past end.
  EXPECT_THAT(vector->TruncateTo(100),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Must be greater than or equal to 0
  EXPECT_THAT(vector->TruncateTo(-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, TruncateAndReReadFile) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<float>> vector,
        FileBackedVector<float>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

    ICING_ASSERT_OK(vector->Set(0, 1.0));
    ICING_ASSERT_OK(vector->Set(1, 2.0));
    ICING_ASSERT_OK(vector->Set(2, 2.0));
    ICING_ASSERT_OK(vector->Set(3, 2.0));
    ICING_ASSERT_OK(vector->Set(4, 2.0));
  }  // Destroying the vector should trigger a checksum of the 5 elements

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<float>> vector,
        FileBackedVector<float>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

    EXPECT_EQ(5, vector->num_elements());
    ICING_EXPECT_OK(vector->TruncateTo(4));
    EXPECT_EQ(4, vector->num_elements());
  }  // Destroying the vector should update the checksum to 4 elements

  // Creating again should double check that our checksum of 4 elements matches
  // what was previously saved.
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<float>> vector,
        FileBackedVector<float>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

    EXPECT_EQ(vector->num_elements(), 4);
  }
}

TEST_F(FileBackedVectorTest, Sort) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ICING_ASSERT_OK(vector->Set(0, 5));
  ICING_ASSERT_OK(vector->Set(1, 4));
  ICING_ASSERT_OK(vector->Set(2, 2));
  ICING_ASSERT_OK(vector->Set(3, 3));
  ICING_ASSERT_OK(vector->Set(4, 1));

  // Sort vector range [1, 4) (excluding 4).
  EXPECT_THAT(vector->Sort(/*begin_idx=*/1, /*end_idx=*/4), IsOk());
  // Verify sorted range should be sorted and others should remain unchanged.
  EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(5)));
  EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(2)));
  EXPECT_THAT(vector->Get(2), IsOkAndHolds(Pointee(3)));
  EXPECT_THAT(vector->Get(3), IsOkAndHolds(Pointee(4)));
  EXPECT_THAT(vector->Get(4), IsOkAndHolds(Pointee(1)));

  // Sort again by end_idx = num_elements().
  EXPECT_THAT(vector->Sort(/*begin_idx=*/0, /*end_idx=*/vector->num_elements()),
              IsOk());
  EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(1)));
  EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(2)));
  EXPECT_THAT(vector->Get(2), IsOkAndHolds(Pointee(3)));
  EXPECT_THAT(vector->Get(3), IsOkAndHolds(Pointee(4)));
  EXPECT_THAT(vector->Get(4), IsOkAndHolds(Pointee(5)));
}

TEST_F(FileBackedVectorTest, SortByInvalidIndexShouldReturnOutOfRangeError) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  ICING_ASSERT_OK(vector->Set(0, 5));
  ICING_ASSERT_OK(vector->Set(1, 4));
  ICING_ASSERT_OK(vector->Set(2, 2));
  ICING_ASSERT_OK(vector->Set(3, 3));
  ICING_ASSERT_OK(vector->Set(4, 1));

  EXPECT_THAT(vector->Sort(/*begin_idx=*/-1, /*end_idx=*/4),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Sort(/*begin_idx=*/0, /*end_idx=*/-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Sort(/*begin_idx=*/3, /*end_idx=*/3),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Sort(/*begin_idx=*/3, /*end_idx=*/1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Sort(/*begin_idx=*/5, /*end_idx=*/5),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(vector->Sort(/*begin_idx=*/3, /*end_idx=*/6),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedVectorTest, SortShouldSetDirtyCorrectly) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<int>> vector,
        FileBackedVector<int>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    ICING_ASSERT_OK(vector->Set(0, 5));
    ICING_ASSERT_OK(vector->Set(1, 4));
    ICING_ASSERT_OK(vector->Set(2, 2));
    ICING_ASSERT_OK(vector->Set(3, 3));
    ICING_ASSERT_OK(vector->Set(4, 1));
  }  // Destroying the vector should trigger a checksum of the 5 elements

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<int>> vector,
        FileBackedVector<int>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

    // Sort vector range [1, 4) (excluding 4).
    EXPECT_THAT(vector->Sort(/*begin_idx=*/1, /*end_idx=*/4), IsOk());
  }  // Destroying the vector should update the checksum

  // Creating again should check that the checksum after sorting matches what
  // was previously saved. This tests the correctness of SetDirty() for sorted
  // elements.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> vector,
      FileBackedVector<int>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  // Verify sorted range should be sorted and others should remain unchanged.
  EXPECT_THAT(vector->Get(0), IsOkAndHolds(Pointee(5)));
  EXPECT_THAT(vector->Get(1), IsOkAndHolds(Pointee(2)));
  EXPECT_THAT(vector->Get(2), IsOkAndHolds(Pointee(3)));
  EXPECT_THAT(vector->Get(3), IsOkAndHolds(Pointee(4)));
  EXPECT_THAT(vector->Get(4), IsOkAndHolds(Pointee(1)));
}

TEST_F(FileBackedVectorTest, SetDirty) {
  // 1. Create a vector and add some data.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<char>> vector,
      FileBackedVector<char>::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
  Insert(vector.get(), 0, "abcd");

  std::string_view reconstructed_view =
      std::string_view(vector->array(), vector->num_elements());

  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc1, vector->ComputeChecksum());
  Crc32 full_crc_before_overwrite;
  full_crc_before_overwrite.Append(reconstructed_view);
  EXPECT_THAT(crc1, Eq(full_crc_before_overwrite));

  // 2. Manually overwrite the values of the first two elements.
  std::string corrupted_content = "ef";
  ASSERT_THAT(
      filesystem_.PWrite(fd_, /*offset=*/sizeof(FileBackedVector<char>::Header),
                         corrupted_content.c_str(), corrupted_content.length()),
      IsTrue());
  ASSERT_THAT(Get(vector.get(), 0, 4), Eq("efcd"));
  Crc32 full_crc_after_overwrite;
  full_crc_after_overwrite.Append(reconstructed_view);
  ASSERT_THAT(full_crc_before_overwrite, Not(Eq(full_crc_after_overwrite)));

  // 3. Without calling SetDirty(), the checksum will be recomputed incorrectly.
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc2, vector->ComputeChecksum());
  EXPECT_THAT(crc2, Not(Eq(full_crc_after_overwrite)));

  // 4. Call SetDirty()
  vector->SetDirty(0);
  vector->SetDirty(1);

  // 5. The checksum should be computed correctly after calling SetDirty() with
  // correct index.
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 crc3, vector->ComputeChecksum());
  EXPECT_THAT(crc3, Eq(full_crc_after_overwrite));
}

TEST_F(FileBackedVectorTest, InitFileTooSmallForHeaderFails) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  // 2. Shrink the file to be smaller than the header.
  filesystem_.Truncate(fd_, sizeof(FileBackedVector<char>::Header) - 1);

  {
    // 3. Attempt to create the file and confirm that it fails.
    EXPECT_THAT(FileBackedVector<char>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(FileBackedVectorTest, InitWrongDataSizeFails) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  {
    // 2. Attempt to create the file with a different element size and confirm
    // that it fails.
    EXPECT_THAT(FileBackedVector<int>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(FileBackedVectorTest, InitCorruptHeaderFails) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  // 2. Modify the header, but don't update the checksum. This would be similar
  // to corruption of the header.
  FileBackedVector<char>::Header header;
  ASSERT_THAT(filesystem_.PRead(fd_, &header, sizeof(header), /*offset=*/0),
              IsTrue());
  header.num_elements = 1;
  ASSERT_THAT(filesystem_.PWrite(fd_, /*offset=*/0, &header, sizeof(header)),
              IsTrue());

  {
    // 3. Attempt to create the file with a header that doesn't match its
    // checksum and confirm that it fails.
    EXPECT_THAT(FileBackedVector<char>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  }
}

TEST_F(FileBackedVectorTest, InitHeaderElementSizeTooBigFails) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  // 2. Modify the header so that the number of elements exceeds the actual size
  // of the underlying file.
  FileBackedVector<char>::Header header;
  ASSERT_THAT(filesystem_.PRead(fd_, &header, sizeof(header), /*offset=*/0),
              IsTrue());
  int64_t file_size = filesystem_.GetFileSize(fd_);
  int64_t allocated_elements_size = file_size - sizeof(header);
  header.num_elements = (allocated_elements_size / sizeof(char)) + 1;
  header.header_checksum = header.CalculateHeaderChecksum();
  ASSERT_THAT(filesystem_.PWrite(fd_, /*offset=*/0, &header, sizeof(header)),
              IsTrue());

  {
    // 3. Attempt to create the file with num_elements that is larger than the
    // underlying file and confirm that it fails.
    EXPECT_THAT(FileBackedVector<char>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(FileBackedVectorTest, InitCorruptElementsFails) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  // 2. Overwrite the values of the first two elements.
  std::string corrupted_content = "BY";
  ASSERT_THAT(
      filesystem_.PWrite(fd_, /*offset=*/sizeof(FileBackedVector<char>::Header),
                         corrupted_content.c_str(), corrupted_content.length()),
      IsTrue());

  {
    // 3. Attempt to create the file with elements that don't match their
    // checksum and confirm that it fails.
    EXPECT_THAT(FileBackedVector<char>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  }
}

TEST_F(FileBackedVectorTest, InitNormalSucceeds) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    Insert(vector.get(), 0, "A");
    Insert(vector.get(), 1, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  {
    // 2. Attempt to create the file with a completely valid header and elements
    // region. This should succeed.
    EXPECT_THAT(FileBackedVector<char>::Create(
                    filesystem_, file_path_,
                    MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC),
                IsOk());
  }
}

TEST_F(FileBackedVectorTest, InitFromExistingFileShouldPreMapAtLeastFileSize) {
  {
    // 1. Create a vector with a few elements.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
            FileBackedVector<char>::kMaxFileSize));
    Insert(vector.get(), 10000, "A");
    Insert(vector.get(), 10001, "Z");
    ASSERT_THAT(vector->PersistToDisk(), IsOk());
  }

  {
    // 2. Attempt to create the file with pre_mapping_mmap_size < file_size. It
    //    should still pre-map file_size, so we can pass the checksum
    //    verification when initializing and get the correct contents.
    int64_t file_size = filesystem_.GetFileSize(file_path_.c_str());
    int pre_mapping_mmap_size = 10;
    ASSERT_THAT(pre_mapping_mmap_size, Lt(file_size));
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> vector,
        FileBackedVector<char>::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
            FileBackedVector<char>::kMaxFileSize, pre_mapping_mmap_size));
    EXPECT_THAT(Get(vector.get(), /*idx=*/10000, /*expected_len=*/2), Eq("AZ"));
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
