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

#include <set>

#include <ditto/instruction_set.h>
#include <ditto/read_write_file.h>

using ::dittosuite::Instruction;
using ::dittosuite::SharedVariables;
using ::testing::_;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::Return;

class ReadFileTest : public InstructionTest {
 protected:
  int fd_ = MockSyscall::kDefaultFileDescriptor;
  int input_key_;

  // Set input fd
  void SetUp() override {
    InstructionTest::SetUp();
    input_key_ = SharedVariables::GetKey(thread_ids, "test_input");
    SharedVariables::Set(input_key_, fd_);
  }
};

using ReadFileDeathTest = ReadFileTest;

TEST_F(ReadFileTest, SetFAdvise) {
  int fadvise = 2;

  EXPECT_CALL(syscall_, FAdvise(fd_, 0, MockSyscall::kDefaultFileSize, fadvise));

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, 1}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kSequential, 0, dittosuite::Reseeding::kOnce, fadvise, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadSingleBlockSequential) {
  EXPECT_CALL(syscall_, Read(fd_, _, MockSyscall::kDefaultFileSize, 0));

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, 1}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kSequential, 0, dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadSingleBlockSequentialRepeated) {
  int repeat = 2;
  EXPECT_CALL(syscall_, Read(fd_, _, MockSyscall::kDefaultFileSize, 0)).Times(repeat);

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, repeat}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kSequential, 0, dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadSingleBlockRandom) {
  EXPECT_CALL(syscall_, Read(fd_, _, MockSyscall::kDefaultFileSize, 0));

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, 1}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kRandom, 0, dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadSingleBlockRandomRepeated) {
  int repeat = 2;
  EXPECT_CALL(syscall_, Read(fd_, _, MockSyscall::kDefaultFileSize, 0)).Times(repeat);

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, repeat}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kRandom, 0, dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadMultipleBlocksSequential) {
  int64_t block_size = 4096;
  int64_t size = block_size * 4;

  // Check that file size is requested two times in each SetUpSingle() and
  // pass needed file size for this test
  EXPECT_CALL(syscall_, FStat(fd_, _)).Times(2).WillRepeatedly(Invoke([&](int, struct stat64* buf) {
    buf->st_size = size;
    return 0;
  }));

  {
    InSequence sq;
    EXPECT_CALL(syscall_, Read(fd_, _, block_size, 0));
    EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size));
    EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size * 2));
    EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size * 3));
  }

  auto read_file = dittosuite::ReadFile((Instruction::Params){syscall_, 1}, size, block_size, 0,
                                        dittosuite::Order::kSequential, 0,
                                        dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadMultipleBlocksSequentialRepeated) {
  int repeat = 2;
  int64_t block_size = 4096;
  int64_t size = block_size * 4;

  // Check that file size is requested two times in each SetUpSingle() and
  // pass needed file size for this test
  EXPECT_CALL(syscall_, FStat(fd_, _))
      .Times(repeat * 2)
      .WillRepeatedly(Invoke([&](int, struct stat64* buf) {
        buf->st_size = size;
        return 0;
      }));

  {
    InSequence sq;
    for (int i = 0; i < repeat; ++i) {
      EXPECT_CALL(syscall_, Read(fd_, _, block_size, 0));
      EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size));
      EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size * 2));
      EXPECT_CALL(syscall_, Read(fd_, _, block_size, block_size * 3));
    }
  }

  auto read_file = dittosuite::ReadFile((Instruction::Params){syscall_, repeat}, size, block_size,
                                        0, dittosuite::Order::kSequential, 0,
                                        dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, ReadMultipleBlocksRandomRepeatedReseededOnce) {
  int repeat = 2;
  int64_t block_size = 4096;
  int64_t size = block_size * repeat;
  int64_t file_size = 1024 * 1024 * 1024;  // 1GB
  std::set<int64_t> blocks;
  int number_of_blocks = repeat * repeat * repeat;
  int number_of_unique_blocks = number_of_blocks;

  // Check that file size is requested two times in each SetUpSingle() and
  // pass needed file size for this test
  EXPECT_CALL(syscall_, FStat(fd_, _))
      .Times(repeat * repeat * 2)
      .WillRepeatedly(Invoke([&](int, struct stat64* buf) {
        buf->st_size = file_size;
        return 0;
      }));

  // Collect the blocks
  EXPECT_CALL(syscall_, Read(fd_, _, block_size, _))
      .Times(number_of_blocks)
      .WillRepeatedly(Invoke([&](int, char*, int64_t size, int64_t offset) {
        blocks.insert(offset);
        return size;
      }));

  auto read_file = std::make_unique<dittosuite::ReadFile>(
      (Instruction::Params){syscall_, repeat}, size, block_size, 0, dittosuite::Order::kRandom, 0,
      dittosuite::Reseeding::kOnce, 0, input_key_);
  std::vector<std::unique_ptr<dittosuite::Instruction>> instructions;
  instructions.push_back(std::move(read_file));
  auto instruction_set =
      dittosuite::InstructionSet((Instruction::Params){syscall_, repeat}, std::move(instructions));
  instruction_set.Run();

  // Check that the number of unique blocks, that were collected, matches the expected number
  ASSERT_EQ(static_cast<int>(blocks.size()), number_of_unique_blocks);
}

TEST_F(ReadFileTest, ReadMultipleBlocksRandomRepeatedReseededEachRoundOfCycles) {
  int repeat = 2;
  int64_t block_size = 4096;
  int64_t size = block_size * repeat;
  int64_t file_size = 1024 * 1024 * 1024;  // 1GB
  std::set<int64_t> blocks;
  int number_of_blocks = repeat * repeat * repeat;
  int number_of_unique_blocks = repeat * repeat;

  // Check that file size is requested two times in each SetUpSingle() and
  // pass needed file size for this test
  EXPECT_CALL(syscall_, FStat(fd_, _))
      .Times(repeat * repeat * 2)
      .WillRepeatedly(Invoke([&](int, struct stat64* buf) {
        buf->st_size = file_size;
        return 0;
      }));

  // Collect the blocks
  EXPECT_CALL(syscall_, Read(fd_, _, block_size, _))
      .Times(number_of_blocks)
      .WillRepeatedly(Invoke([&](int, char*, int64_t size, int64_t offset) {
        blocks.insert(offset);
        return size;
      }));

  auto read_file = std::make_unique<dittosuite::ReadFile>(
      (Instruction::Params){syscall_, repeat}, size, block_size, 0, dittosuite::Order::kRandom, 0,
      dittosuite::Reseeding::kEachRoundOfCycles, 0, input_key_);
  std::vector<std::unique_ptr<dittosuite::Instruction>> instructions;
  instructions.push_back(std::move(read_file));
  auto instruction_set =
      dittosuite::InstructionSet((Instruction::Params){syscall_, repeat}, std::move(instructions));
  instruction_set.Run();

  // Check that the number of unique blocks, that were collected, matches the expected number
  ASSERT_EQ(static_cast<int>(blocks.size()), number_of_unique_blocks);
}

TEST_F(ReadFileTest, ReadMultipleBlocksRandomRepeatedReseededEachCycle) {
  int repeat = 2;
  int64_t block_size = 4096;
  int64_t size = block_size * repeat;
  int64_t file_size = 1024 * 1024 * 1024;  // 1GB
  std::set<int64_t> blocks;
  int number_of_blocks = repeat * repeat * repeat;
  int number_of_unique_blocks = repeat;

  // Check that file size is requested two times in each SetUpSingle() and
  // pass needed file size for this test
  EXPECT_CALL(syscall_, FStat(fd_, _))
      .Times(repeat * repeat * 2)
      .WillRepeatedly(Invoke([&](int, struct stat64* buf) {
        buf->st_size = file_size;
        return 0;
      }));

  // Collect the blocks
  EXPECT_CALL(syscall_, Read(fd_, _, block_size, _))
      .Times(number_of_blocks)
      .WillRepeatedly(Invoke([&](int, char*, int64_t size, int64_t offset) {
        blocks.insert(offset);
        return size;
      }));

  auto read_file = std::make_unique<dittosuite::ReadFile>(
      (Instruction::Params){syscall_, repeat}, size, block_size, 0, dittosuite::Order::kRandom, 0,
      dittosuite::Reseeding::kEachCycle, 0, input_key_);
  std::vector<std::unique_ptr<dittosuite::Instruction>> instructions;
  instructions.push_back(std::move(read_file));
  auto instruction_set =
      dittosuite::InstructionSet((Instruction::Params){syscall_, repeat}, std::move(instructions));
  instruction_set.Run();

  // Check that the number of unique blocks, that were collected, matches the expected number
  ASSERT_EQ(static_cast<int>(blocks.size()), number_of_unique_blocks);
}

TEST_F(ReadFileTest, UsedFileSize) {
  // Expect a single Read() with the correct block_size (equal to file size)
  EXPECT_CALL(syscall_, Read(fd_, _, MockSyscall::kDefaultFileSize, _));

  auto read_file = dittosuite::ReadFile((Instruction::Params){syscall_, 1}, -1, -1, 0,
                                        dittosuite::Order::kSequential, 0,
                                        dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileTest, UsedFileSizeRepeated) {
  std::vector<int64_t> sizes = {1024, 2048};
  {
    InSequence sq;
    for (const auto& size : sizes) {
      EXPECT_CALL(syscall_, FStat(fd_, _))
          .Times(2)
          .WillRepeatedly(Invoke([&](int, struct stat64* buf) {
            buf->st_size = size;
            return 0;
          }));
      // Expect a single Read() with the correct block_size (equal to file size)
      EXPECT_CALL(syscall_, Read(fd_, _, size, _));
    }
  }

  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, static_cast<int>(sizes.size())}, -1, -1, 0,
      dittosuite::Order::kSequential, 0, dittosuite::Reseeding::kOnce, 0, input_key_);
  read_file.Run();
}

TEST_F(ReadFileDeathTest, DiedDueToInvalidFd) {
  SharedVariables::Set(input_key_, -1);
  auto read_file = dittosuite::ReadFile(
      (Instruction::Params){syscall_, 1}, -1, MockSyscall::kDefaultFileSize, 0,
      dittosuite::Order::kRandom, 0, dittosuite::Reseeding::kOnce, 0, input_key_);

  // Will fail when GetFileSize() is called for an invalid fd during setup
  EXPECT_CALL(syscall_, FStat(-1, _)).WillRepeatedly(Return(-1));
  EXPECT_DEATH(read_file.Run(), _);
}
