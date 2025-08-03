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

#include <ditto/open_file.h>

#include <ditto/logger.h>
#include <ditto/shared_variables.h>
#include <ditto/utils.h>

namespace dittosuite {

OpenFile::OpenFile(const Params& params, const std::string& path_name, bool create, bool direct_io,
                   int output_fd_key, AccessMode access_mode)
    : Instruction(kName, params),
      random_name_(false),
      path_name_(GetAbsolutePath() + path_name),
      create_(create),
      direct_io_(direct_io),
      input_key_(-1),
      output_fd_key_(output_fd_key),
      access_mode_(access_mode) {}

OpenFile::OpenFile(const Params& params, int input_key, bool create, bool direct_io,
                   int output_fd_key, AccessMode access_mode)
    : Instruction(kName, params),
      random_name_(false),
      create_(create),
      direct_io_(direct_io),
      input_key_(input_key),
      output_fd_key_(output_fd_key),
      access_mode_(access_mode) {}

OpenFile::OpenFile(const Params& params, bool create, bool direct_io, int output_fd_key,
                   AccessMode access_mode)
    : Instruction(kName, params),
      random_name_(true),
      create_(create),
      direct_io_(direct_io),
      input_key_(-1),
      output_fd_key_(output_fd_key),
      gen_(time(nullptr)),
      access_mode_(access_mode) {}

void OpenFile::SetUpSingle() {
  if (input_key_ != -1) {
    path_name_ = std::get<std::string>(SharedVariables::Get(input_key_));
  } else if (random_name_) {
    std::uniform_int_distribution<> uniform_distribution(1e8, 9e8);  // 9 digit number
    do {
      path_name_ = GetAbsolutePath() + std::to_string(uniform_distribution(gen_));
    } while (FileExists(syscall_, path_name_));
  }
  Instruction::SetUpSingle();
}

void OpenFile::RunSingle() {
  int open_mode = S_IRUSR | S_IWUSR;

  int open_flags = 0;
  switch (access_mode_) {
    case AccessMode::kReadOnly:
      open_flags |= O_RDONLY;
      break;
    case AccessMode::kWriteOnly:
      open_flags |= O_WRONLY;
      break;
    case AccessMode::kReadWrite:
      open_flags |= O_RDWR;
      break;
  }
  open_flags |= O_CLOEXEC;
  if (create_) open_flags |= O_CREAT;
  if (direct_io_) open_flags |= O_DIRECT;

  int fd = syscall_.Open(path_name_, open_flags, open_mode);

  if (fd == -1) {
    PLOGF("Cannot open \"" + path_name_ + "\"");
  }

  if (output_fd_key_ != -1) {
    SharedVariables::Set(output_fd_key_, fd);
  }
}

}  // namespace dittosuite
