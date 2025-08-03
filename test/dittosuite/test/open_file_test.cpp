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

#include <ditto/open_file.h>
#include <ditto/syscall.h>

using dittosuite::Instruction;

class OpenFileTest : public InstructionTestWithParam<dittosuite::OpenFile::AccessMode> {
 protected:
  std::string file_name = "test";
  std::string path = absolute_path + file_name;

  // Make sure that the files, which have been created in the tests, are deleted
  void TearDown() override { unlink(path.c_str()); }
};

TEST_P(OpenFileTest, FileCreatedWithPathName) {
  dittosuite::OpenFile::AccessMode access_mode = GetParam();
  dittosuite::OpenFile instruction((Instruction::Params){dittosuite::Syscall::GetSyscall(), 1},
                                   file_name, true, false, -1, access_mode);
  instruction.Run();

  ASSERT_EQ(access(path.c_str(), F_OK), 0);
}

TEST_P(OpenFileTest, FileCreatedWithVariable) {
  dittosuite::OpenFile::AccessMode access_mode = GetParam();
  dittosuite::SharedVariables::Set(thread_ids, "input", path);
  dittosuite::OpenFile instruction((Instruction::Params){dittosuite::Syscall::GetSyscall(), 1},
                                   dittosuite::SharedVariables::GetKey(thread_ids, "input"), true,
                                   false, -1, access_mode);
  instruction.Run();

  ASSERT_EQ(access(path.c_str(), F_OK), 0);
}

INSTANTIATE_TEST_CASE_P(OpenFileTestParametric, OpenFileTest,
                        ::testing::Values(dittosuite::OpenFile::AccessMode::kReadOnly,
                                          dittosuite::OpenFile::AccessMode::kWriteOnly,
                                          dittosuite::OpenFile::AccessMode::kReadWrite));
