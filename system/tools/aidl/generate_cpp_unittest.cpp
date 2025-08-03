/*
 * Copyright (C) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string>

#include <android-base/stringprintf.h>
#include <gtest/gtest.h>

#include "aidl.h"
#include "aidl_language.h"
#include "code_writer.h"
#include "generate_cpp.h"
#include "os.h"
#include "tests/fake_io_delegate.h"
#include "tests/test_util.h"

using ::android::aidl::test::FakeIoDelegate;
using ::android::base::StringPrintf;
using std::string;
using std::unique_ptr;

namespace android {
namespace aidl {
namespace cpp {

class ASTTest : public ::testing::Test {
 protected:
  ASTTest(const string& cmdline, const string& file_contents)
      : options_(Options::From(cmdline)), file_contents_(file_contents) {
  }

  AidlInterface* ParseSingleInterface() {
    io_delegate_.SetFileContents(options_.InputFiles().at(0), file_contents_);

    vector<string> imported_files;
    ImportResolver import_resolver{io_delegate_, options_.InputFiles().at(0), {"."}};
    AidlError err = ::android::aidl::internals::load_and_validate_aidl(
        options_.InputFiles().front(), options_, io_delegate_, &typenames_, &imported_files);

    if (err != AidlError::OK) {
      return nullptr;
    }

    const auto& defined_types = typenames_.MainDocument().DefinedTypes();
    EXPECT_EQ(1ul, defined_types.size());
    EXPECT_NE(nullptr, defined_types.front().get()->AsInterface());

    return defined_types.front().get()->AsInterface();
  }

  const Options options_;
  const string file_contents_;
  FakeIoDelegate io_delegate_;
  AidlTypenames typenames_;
};

namespace test_io_handling {

const char kInputPath[] = "a/IFoo.aidl";
const char kOutputPath[] = "output.cpp";
const char kHeaderDir[] = "headers";
const char kInterfaceHeaderRelPath[] = "a/IFoo.h";

const string kCmdline =
    string("aidl-cpp -I . ") + kInputPath + " " + kHeaderDir + " " + kOutputPath;

}  // namespace test_io_handling

class IoErrorHandlingTest : public ASTTest {
 public:
  IoErrorHandlingTest() : ASTTest(test_io_handling::kCmdline, "package a; interface IFoo {}") {}
};

TEST_F(IoErrorHandlingTest, GenerateCorrectlyAbsentErrors) {
  // Confirm that this is working correctly without I/O problems.
  AidlInterface* interface = ParseSingleInterface();
  ASSERT_NE(interface, nullptr);
  ASSERT_TRUE(GenerateCpp(options_.OutputFile(), options_, typenames_, *interface, io_delegate_));
}

TEST_F(IoErrorHandlingTest, HandlesBadHeaderWrite) {
  using namespace test_io_handling;
  AidlInterface* interface = ParseSingleInterface();
  ASSERT_NE(interface, nullptr);

  // Simulate issues closing the interface header.
  const string header_path =
      StringPrintf("%s%c%s", kHeaderDir, OS_PATH_SEPARATOR,
                   kInterfaceHeaderRelPath);
  io_delegate_.AddBrokenFilePath(header_path);
  ASSERT_DEATH(GenerateCpp(options_.OutputFile(), options_, typenames_, *interface, io_delegate_),
               "I/O Error!");
  // We should never attempt to write the C++ file if we fail writing headers.
  ASSERT_FALSE(io_delegate_.GetWrittenContents(kOutputPath, nullptr));
}

TEST_F(IoErrorHandlingTest, HandlesBadCppWrite) {
  using test_io_handling::kOutputPath;
  AidlInterface* interface = ParseSingleInterface();
  ASSERT_NE(interface, nullptr);

  // Simulate issues closing the cpp file.
  io_delegate_.AddBrokenFilePath(kOutputPath);
  ASSERT_DEATH(GenerateCpp(options_.OutputFile(), options_, typenames_, *interface, io_delegate_),
               "I/O Error!");
}

}  // namespace cpp
}  // namespace aidl
}  // namespace android
