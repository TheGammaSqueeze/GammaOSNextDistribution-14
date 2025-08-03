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

#include <sys/stat.h>
#include <unistd.h>

#include <cstdint>

#include <ditto/open_file.h>
#include <ditto/resize_file.h>
#include <ditto/syscall.h>

using dittosuite::Instruction;

class ResizeFileTest : public InstructionTestWithParam<dittosuite::OpenFile::AccessMode> {
 protected:
  std::string file_name = "test";
  std::string path = absolute_path + file_name;

  // Make sure that the files, which have been created in the tests, are deleted
  void TearDown() override { unlink(path.c_str()); }
};

TEST_P(ResizeFileTest, ResizeFileTestRun) {
  int repeat = 1;
  int64_t size = 2048;
  dittosuite::OpenFile::AccessMode access_mode = GetParam();
  int fd_key = dittosuite::SharedVariables::GetKey(thread_ids, "test_file");

  dittosuite::OpenFile open_file_instruction(
      (Instruction::Params){dittosuite::Syscall::GetSyscall(), repeat}, file_name, true, false,
      fd_key, access_mode);
  open_file_instruction.Run();

  ASSERT_EQ(access(path.c_str(), F_OK), 0);

  dittosuite::ResizeFile resize_file_instruction(
      (Instruction::Params){dittosuite::Syscall::GetSyscall(), repeat}, size, fd_key);
  if (access_mode == dittosuite::OpenFile::AccessMode::kReadOnly) {
    ASSERT_DEATH(resize_file_instruction.Run(), ".*");
  } else {
    resize_file_instruction.Run();

    struct stat sb;
    stat(path.c_str(), &sb);
    ASSERT_EQ(sb.st_size, size);
  }
}

INSTANTIATE_TEST_CASE_P(ResizeFileTestParametric, ResizeFileTest,
                        ::testing::Values(dittosuite::OpenFile::AccessMode::kReadOnly,
                                          dittosuite::OpenFile::AccessMode::kWriteOnly,
                                          dittosuite::OpenFile::AccessMode::kReadWrite));
