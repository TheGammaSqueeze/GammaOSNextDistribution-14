//
// Copyright (C) 2022 The Android Open Source Project
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
//

#include <gtest/gtest.h>
#include <liburing_cpp/IoUring.h>

#include <linux/fs.h>
#include <stdio.h>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cstring>
#include <vector>

using namespace io_uring_cpp;

class IoUringTest : public ::testing::Test {
 public:
  IoUringTest() { fp = tmpfile(); }
  ~IoUringTest() {
    if (fp) {
      fclose(fp);
    }
  }
  void SetUp() override {
    struct utsname buffer {};

    ASSERT_EQ(uname(&buffer), 0)
        << strerror(errno) << "Failed to get kernel version number";
    int major = 0;
    int minor = 0;
    const auto matched = sscanf(buffer.release, "%d.%d", &major, &minor);
    ASSERT_EQ(matched, 2) << "Unexpected kernel version format: "
                          << buffer.release;

    if (major < 5 || (major == 5 && minor < 6)) {
      GTEST_SKIP() << "Kernel version does not support io_uring "
                   << buffer.release;
      return;
    }

    ring = IoUringInterface::CreateLinuxIoUring(4096, 0);
    ASSERT_NE(ring, nullptr);
  }
  void Write(int fd, const void* data, const size_t len) {
    const auto buf = static_cast<const char*>(data);
    constexpr size_t IO_BATCH_SIZE = 4096;
    size_t i = 0;
    for (i = 0; i < len; i += IO_BATCH_SIZE) {
      const auto sqe = ring->PrepWrite(fd, buf + i, IO_BATCH_SIZE, i);
      ASSERT_TRUE(sqe.IsOk());
    }
    const auto bytes_remaining = len - i;
    if (bytes_remaining) {
      ASSERT_TRUE(ring->PrepWrite(fd, buf + i, bytes_remaining, i).IsOk());
    }
    const auto ret = ring->Submit();
    ASSERT_TRUE(ret.IsOk()) << ret.ErrMsg();
    for (size_t i = (len + IO_BATCH_SIZE - 1) / IO_BATCH_SIZE; i > 0; i--) {
      const auto cqe = ring->PopCQE();
      ASSERT_TRUE(cqe.IsOk());
      ASSERT_GT(cqe.GetResult().res, 0);
    }
  }
  std::unique_ptr<IoUringInterface> ring;
  FILE* fp = nullptr;
};

TEST_F(IoUringTest, SmallRead) {
  int fd = open("/proc/self/maps", O_RDONLY);
  std::array<char, 1024> buf{};
  const auto sqe = ring->PrepRead(fd, buf.data(), buf.size(), 0);
  ASSERT_TRUE(sqe.IsOk()) << "Submission Queue is full!";
  const auto ret = ring->Submit();
  ASSERT_TRUE(ret.IsOk()) << ret.ErrMsg();
  const auto cqe = ring->PopCQE();
  ASSERT_TRUE(cqe.IsOk()) << cqe.GetError();
  ASSERT_GT(cqe.GetResult().res, 0);
}

TEST_F(IoUringTest, SmallWrite) {
  auto fp = tmpfile();
  int fd = fileno(fp);
  std::string buffer(256, 'A');
  const auto sqe = ring->PrepWrite(fd, buffer.data(), buffer.size(), 0);
  ASSERT_TRUE(sqe.IsOk()) << "Submission Queue is full!";
  const auto ret = ring->Submit();
  ASSERT_TRUE(ret.IsOk()) << ret.ErrMsg();
  const auto cqe = ring->PopCQE();
  ASSERT_TRUE(cqe.IsOk()) << cqe.GetError();

  const auto bytes_read = pread(fd, buffer.data(), buffer.size(), 0);

  ASSERT_EQ(bytes_read, buffer.size());

  ASSERT_TRUE(std::all_of(buffer.begin(), buffer.end(), [](const auto& a) {
    return a == 'A';
  })) << buffer;
  fclose(fp);
}

TEST_F(IoUringTest, ChunkedWrite) {
  int fd = fileno(fp);
  std::string buffer(16 * 1024 * 1024, 'A');
  ASSERT_NO_FATAL_FAILURE(Write(fd, buffer.data(), buffer.size()));

  const auto bytes_read = pread(fd, buffer.data(), buffer.size(), 0);

  ASSERT_EQ(bytes_read, buffer.size());

  ASSERT_TRUE(std::all_of(buffer.begin(), buffer.end(), [](const auto& a) {
    return a == 'A';
  })) << buffer;
}

// Page size doesn't really matter. We can replace 4096 with any value.
static constexpr size_t kBlockSize = 4096;
constexpr std::array<unsigned char, 4096> GetArbitraryPageData() {
  std::array<unsigned char, kBlockSize> arr{};
  int i = 0;
  for (auto& a : arr) {
    a = i++;
  }
  return arr;
}

void WriteTestData(int fd, const size_t offset, const size_t size) {
  ASSERT_EQ(size % kBlockSize, 0);
  static const auto data = GetArbitraryPageData();
  size_t bytes_written = 0;
  size_t cur_offset = offset;
  while (bytes_written < size) {
    const auto ret = pwrite(fd, data.data(), kBlockSize, cur_offset);
    ASSERT_GT(ret, 0) << "Failed to pwrite " << strerror(errno);
    bytes_written += ret;
    cur_offset += ret;
  }
}

TEST_F(IoUringTest, ExtentRead) {
  const int fd = fileno(fp);
  ASSERT_NO_FATAL_FAILURE(WriteTestData(fd, kBlockSize * 3, kBlockSize));
  ASSERT_NO_FATAL_FAILURE(WriteTestData(fd, kBlockSize * 5, kBlockSize));
  ASSERT_NO_FATAL_FAILURE(WriteTestData(fd, kBlockSize * 8, kBlockSize));
  ASSERT_NO_FATAL_FAILURE(WriteTestData(fd, kBlockSize * 13, kBlockSize));
  fsync(fd);

  std::vector<unsigned char> data;
  data.resize(kBlockSize * 4);

  ASSERT_TRUE(
      ring->PrepRead(fd, data.data(), kBlockSize, 3 * kBlockSize).IsOk());
  ASSERT_TRUE(
      ring->PrepRead(fd, data.data() + kBlockSize, kBlockSize, 5 * kBlockSize)
          .IsOk());
  ASSERT_TRUE(
      ring->PrepRead(
              fd, data.data() + kBlockSize * 2, kBlockSize, 8 * kBlockSize)
          .IsOk());
  ASSERT_TRUE(
      ring->PrepRead(
              fd, data.data() + kBlockSize * 3, kBlockSize, 13 * kBlockSize)
          .IsOk());
  ring->SubmitAndWait(4);
  const auto cqes = ring->PopCQE(4);
  if (cqes.IsErr()) {
    FAIL() << cqes.GetError().ErrMsg();
    return;
  }
  for (const auto& cqe : cqes.GetResult()) {
    ASSERT_GT(cqe.res, 0);
  }
  for (int i = 0; i < data.size(); ++i) {
    ASSERT_EQ(data[i], i % 256);
  }
}