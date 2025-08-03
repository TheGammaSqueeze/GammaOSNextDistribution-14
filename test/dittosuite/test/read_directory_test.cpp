// Copyright (C) 2021 The Android Open Source Project
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

#include "include/instruction_test.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <ditto/read_directory.h>
#include <ditto/syscall.h>

using dittosuite::Instruction;

class ReadDirectoryTest : public InstructionTest {
 protected:
  std::string directory_name = "test_directory";
  std::string path = absolute_path + directory_name;
  std::vector<std::string> files{path + "/test1", path + "/test2", path + "/test3"};

  // Create folder with several files for testing
  void SetUp() override {
    InstructionTest::SetUp();
    ASSERT_NE(mkdir(path.c_str(), S_IRWXU), -1);
    for (const auto& file : files) {
      ASSERT_NE(open(file.c_str(), O_CREAT | O_CLOEXEC, S_IRUSR | S_IWUSR), -1);
    }
  }
  // Remove the folder and files that were created in SetUp()
  void TearDown() override {
    for (const auto& file : files) {
      ASSERT_NE(unlink(file.c_str()), -1);
    }
    ASSERT_NE(rmdir(path.c_str()), -1);
  }
};

TEST_F(ReadDirectoryTest, ReadDirectoryTestRun) {
  auto output_key = dittosuite::SharedVariables::GetKey(thread_ids, "file_list");

  dittosuite::ReadDirectory instruction((Instruction::Params){dittosuite::Syscall::GetSyscall(), 1},
                                        directory_name, output_key);
  instruction.Run();

  auto output = std::get<std::vector<std::string>>(dittosuite::SharedVariables::Get(output_key));
  sort(output.begin(), output.end());

  ASSERT_EQ(output, files);
}
