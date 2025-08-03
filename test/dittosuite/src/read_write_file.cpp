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

#include <ditto/read_write_file.h>

#include <ditto/logger.h>
#include <ditto/shared_variables.h>

#include <ditto/utils.h>

namespace dittosuite {

ReadWriteFile::ReadWriteFile(const std::string& name, const Params& params, int64_t size,
                             int64_t block_size, int64_t starting_offset, Order access_order,
                             uint32_t seed, Reseeding reseeding, int input_fd_key)
    : Instruction(name, params),
      size_(size),
      block_size_(block_size),
      starting_offset_(starting_offset),
      access_order_(access_order),
      gen_(seed),
      seed_(seed),
      reseeding_(reseeding),
      input_fd_key_(input_fd_key),
      update_size_(size == -1),
      update_block_size_(block_size == -1) {
  if (access_order == Order::kRandom && starting_offset != 0) {
    LOGE(
        "Starting offset is not 0, although the chosen access_order is RANDOM. Starting offset "
        "will be "
        "ignored");
  }
}

std::unique_ptr<Result> ReadWriteFile::CollectResults(const std::string& prefix) {
  auto result = Instruction::CollectResults(prefix);
  result->AddMeasurement("bandwidth", bandwidth_sampler_.GetSamples());
  return result;
}

void ReadWriteFile::SetUp() {
  if (reseeding_ == Reseeding::kEachRoundOfCycles) {
    gen_.seed(seed_);
  }
}

void ReadWriteFile::SetUpSingle() {
  int fd = std::get<int>(SharedVariables::Get(input_fd_key_));
  int64_t file_size = GetFileSize(syscall_, fd);

  if (update_size_) {
    size_ = file_size;
  }
  if (update_block_size_) {
    block_size_ = file_size;
  }

  if (block_size_ > file_size) {
    LOGF("Supplied block_size (" + std::to_string(block_size_) +
         ") is greater than total file size (" + std::to_string(file_size) +
         "). File path:" + GetFilePath(syscall_, fd));
  }

  buffer_ = std::unique_ptr<char[]>(new (std::nothrow) char[block_size_]);
  if (buffer_ == nullptr) {
    PLOGF("Error while allocating buffer for Read/Write");
  }
  std::fill(buffer_.get(), buffer_.get() + block_size_, 170);  // 170 = 10101010

  if (reseeding_ == Reseeding::kEachCycle) {
    gen_.seed(seed_);
  }

  units_.clear();

  switch (access_order_) {
    case Order::kSequential: {
      int64_t offset = starting_offset_;
      for (int64_t i = 0; i < (size_ / block_size_); i++) {
        if (offset > file_size - block_size_) {
          offset = 0;
        }
        units_.push_back({block_size_, offset});
        offset += block_size_;
      }
      break;
    }
    case Order::kRandom: {
      std::uniform_int_distribution<> uniform_distribution(0, file_size - block_size_);

      for (int64_t i = 0; i < (size_ / block_size_); i++) {
        units_.push_back({block_size_, uniform_distribution(gen_)});
      }
      break;
    }
  }

  Instruction::SetUpSingle();
}

void ReadWriteFile::RunSingle() {}

WriteFile::WriteFile(const Params& params, int64_t size, int64_t block_size,
                     int64_t starting_offset, Order access_order, uint32_t seed,
                     Reseeding reseeding, bool fsync, int input_fd_key)
    : ReadWriteFile(kName, params, size, block_size, starting_offset, access_order, seed, reseeding,
                    input_fd_key),
      fsync_(fsync) {}

void WriteFile::RunSingle() {
  int fd = std::get<int>(SharedVariables::Get(input_fd_key_));

  for (const auto& unit : units_) {
    if (syscall_.Write(fd, buffer_.get(), unit.count, unit.offset) == -1) {
      LOGF("Error while calling write()");
    }
  }

  if (fsync_ && syscall_.FSync(fd) != 0) {
    LOGF("Error while calling fsync()");
  }
}

ReadFile::ReadFile(const Params& params, int64_t size, int64_t block_size, int64_t starting_offset,
                   Order access_order, uint32_t seed, Reseeding reseeding, int fadvise,
                   int input_fd_key)
    : ReadWriteFile(kName, params, size, block_size, starting_offset, access_order, seed, reseeding,
                    input_fd_key),
      fadvise_(fadvise) {}

void ReadFile::SetUpSingle() {
  int fd = std::get<int>(SharedVariables::Get(input_fd_key_));
  int64_t file_size = GetFileSize(syscall_, fd);

  if (syscall_.FAdvise(fd, 0, file_size, fadvise_) != 0) {
    LOGF("Error while calling fadvise()");
  }
  ReadWriteFile::SetUpSingle();
}

void ReadFile::RunSingle() {
  int fd = std::get<int>(SharedVariables::Get(input_fd_key_));

  for (const auto& unit : units_) {
    if (syscall_.Read(fd, buffer_.get(), unit.count, unit.offset) == -1) {
      LOGF("Error while calling read()");
    }
  }
}

void ReadWriteFile::TearDownSingle(bool is_last) {
  Instruction::TearDownSingle(is_last);
  bandwidth_sampler_.Measure(size_, time_sampler_.GetSamples().back());
}

}  // namespace dittosuite
