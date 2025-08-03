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

#include <ditto/resize_file.h>

#include <ditto/logger.h>
#include <ditto/shared_variables.h>

#include <ditto/utils.h>

namespace dittosuite {

ResizeFile::ResizeFile(const Params& params, int64_t size, int input_fd_key)
    : Instruction(kName, params), size_(size), input_fd_key_(input_fd_key) {}

void ResizeFile::RunSingle() {
  int fd = std::get<int>(SharedVariables::Get(input_fd_key_));
  int64_t file_size = GetFileSize(syscall_, fd);

  if (size_ > file_size) {
    if (syscall_.FAllocate(fd, 0, 0, size_) != 0) {
      PLOGF("Error while calling fallocate()");
    }
  } else if (size_ < file_size) {
    if (syscall_.FTruncate(fd, size_) != 0) {
      PLOGF("Error while calling ftruncate()");
    }
  }
}

ResizeFileRandom::ResizeFileRandom(const Params& params, int64_t min, int64_t max, uint64_t seed,
                                   Reseeding reseeding, int input_fd_key)
    : ResizeFile(params, -1, input_fd_key),
      min_(min),
      max_(max),
      gen_(seed),
      seed_(seed),
      reseeding_(reseeding) {}

void ResizeFileRandom::SetUp() {
  if (reseeding_ == Reseeding::kEachRoundOfCycles) {
    gen_.seed(seed_);
  }
  Instruction::SetUp();
}

void ResizeFileRandom::SetUpSingle() {
  if (reseeding_ == Reseeding::kEachCycle) {
    gen_.seed(seed_);
  }

  std::uniform_int_distribution<> uniform_distribution(min_, max_);
  size_ = uniform_distribution(gen_);

  Instruction::SetUpSingle();
}

}  // namespace dittosuite
