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
#include <unistd.h>

#include <ditto/delete_file.h>
#include <ditto/syscall.h>

using dittosuite::Instruction;

class DeleteFileTest : public InstructionTest {
 protected:
  std::string file_name = "test";
  std::string path = absolute_path + file_name;

  // Create a file for testing
  void SetUp() override {
    InstructionTest::SetUp();
    ASSERT_NE(open(path.c_str(), O_CREAT | O_CLOEXEC | O_RDWR, S_IRUSR | S_IWUSR), -1);
  }
  // Make sure that the file created in SetUp() is actually deleted
  void TearDown() override { unlink(path.c_str()); }
};

TEST_F(DeleteFileTest, FileDeletedWithPathName) {
  dittosuite::DeleteFile instruction((Instruction::Params){dittosuite::Syscall::GetSyscall(), 1},
                                     file_name);
  instruction.Run();

  ASSERT_EQ(access(path.c_str(), F_OK), -1);
}

TEST_F(DeleteFileTest, FileDeletedWithVariable) {
  dittosuite::SharedVariables::Set(thread_ids, "input", path);
  dittosuite::DeleteFile instruction((Instruction::Params){dittosuite::Syscall::GetSyscall(), 1},
                                     dittosuite::SharedVariables::GetKey(thread_ids, "input"));
  instruction.Run();

  ASSERT_EQ(access(path.c_str(), F_OK), -1);
}
