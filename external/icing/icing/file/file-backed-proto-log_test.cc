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

#include "icing/file/file-backed-proto-log.h"

#include <cstdint>
#include <cstdlib>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/proto/document.pb.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::NotNull;

class FileBackedProtoLogTest : public ::testing::Test {
 protected:
  // Adds a user-defined default construct because a const member variable may
  // make the compiler accidentally delete the default constructor.
  // https://stackoverflow.com/a/47368753
  FileBackedProtoLogTest() {}

  void SetUp() override {
    file_path_ = GetTestTempDir() + "/proto_log";
    filesystem_.DeleteFile(file_path_.c_str());
  }

  void TearDown() override { filesystem_.DeleteFile(file_path_.c_str()); }

  const Filesystem filesystem_;
  std::string file_path_;
  bool compress_ = true;
  int64_t max_proto_size_ = 256 * 1024;  // 256 KiB
};

TEST_F(FileBackedProtoLogTest, Initialize) {
  // max_proto_size must be greater than 0
  int invalid_max_proto_size = 0;
  ASSERT_THAT(FileBackedProtoLog<DocumentProto>::Create(
                  &filesystem_, file_path_,
                  FileBackedProtoLog<DocumentProto>::Options(
                      compress_, invalid_max_proto_size)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  ICING_ASSERT_OK_AND_ASSIGN(
      FileBackedProtoLog<DocumentProto>::CreateResult create_result,
      FileBackedProtoLog<DocumentProto>::Create(
          &filesystem_, file_path_,
          FileBackedProtoLog<DocumentProto>::Options(compress_,
                                                     max_proto_size_)));
  EXPECT_THAT(create_result.proto_log, NotNull());
  EXPECT_FALSE(create_result.has_data_loss());

  // Can't recreate the same file with different options.
  ASSERT_THAT(FileBackedProtoLog<DocumentProto>::Create(
                  &filesystem_, file_path_,
                  FileBackedProtoLog<DocumentProto>::Options(!compress_,
                                                             max_proto_size_)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(FileBackedProtoLogTest, CorruptHeader) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        FileBackedProtoLog<DocumentProto>::CreateResult create_result,
        FileBackedProtoLog<DocumentProto>::Create(
            &filesystem_, file_path_,
            FileBackedProtoLog<DocumentProto>::Options(compress_,
                                                       max_proto_size_)));
    auto recreated_proto_log = std::move(create_result.proto_log);
    EXPECT_FALSE(create_result.has_data_loss());

    int corrupt_offset =
        offsetof(FileBackedProtoLog<DocumentProto>::Header, rewind_offset);
    // We should never rewind to a negative offset.
    int invalid_rewind_offset = -1;
    filesystem_.PWrite(file_path_.c_str(), corrupt_offset,
                       &invalid_rewind_offset, sizeof(invalid_rewind_offset));
  }

  {
    // Reinitialize the same proto_log
    ASSERT_THAT(FileBackedProtoLog<DocumentProto>::Create(
                    &filesystem_, file_path_,
                    FileBackedProtoLog<DocumentProto>::Options(
                        compress_, max_proto_size_)),
                StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

}  // namespace
}  // namespace lib
}  // namespace icing
