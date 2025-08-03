/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android-base/file.h>
#include <android-base/macros.h>
#include <android-base/result-gmock.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdlib.h>
#include <unistd.h>

#include "BpfSyscallWrappers.h"
#include "bpf/BpfRingbuf.h"
#include "bpf/BpfUtils.h"
#include "bpf/KernelUtils.h"

#define TEST_RINGBUF_MAGIC_NUM 12345

namespace android {
namespace bpf {
using ::android::base::testing::HasError;
using ::android::base::testing::HasValue;
using ::android::base::testing::WithCode;
using ::testing::AllOf;
using ::testing::Gt;
using ::testing::HasSubstr;
using ::testing::Lt;

class BpfRingbufTest : public ::testing::Test {
 protected:
  BpfRingbufTest()
      : mProgPath("/sys/fs/bpf/prog_bpfRingbufProg_skfilter_ringbuf_test"),
        mRingbufPath("/sys/fs/bpf/map_bpfRingbufProg_test_ringbuf") {}

  void SetUp() {
    if (!android::bpf::isAtLeastKernelVersion(5, 8, 0)) {
      GTEST_SKIP() << "BPF ring buffers not supported below 5.8";
    }

    errno = 0;
    mProgram.reset(retrieveProgram(mProgPath.c_str()));
    EXPECT_EQ(errno, 0);
    ASSERT_GE(mProgram.get(), 0)
        << mProgPath << " was either not found or inaccessible.";
  }

  void RunProgram() {
    char fake_skb[128] = {};
    EXPECT_EQ(runProgram(mProgram, fake_skb, sizeof(fake_skb)), 0);
  }

  void RunTestN(int n) {
    int run_count = 0;
    uint64_t output = 0;
    auto callback = [&](const uint64_t& value) {
      output = value;
      run_count++;
    };

    auto result = BpfRingbuf<uint64_t>::Create(mRingbufPath.c_str());
    ASSERT_RESULT_OK(result);
    EXPECT_TRUE(result.value()->isEmpty());

    struct timespec t1, t2;
    EXPECT_EQ(0, clock_gettime(CLOCK_MONOTONIC, &t1));
    EXPECT_FALSE(result.value()->wait(1000 /*ms*/));  // false because wait should timeout
    EXPECT_EQ(0, clock_gettime(CLOCK_MONOTONIC, &t2));
    long long time1 = t1.tv_sec * 1000000000LL + t1.tv_nsec;
    long long time2 = t2.tv_sec * 1000000000LL + t2.tv_nsec;
    EXPECT_GE(time2 - time1, 1000000000 /*ns*/);  // 1000 ms as ns

    for (int i = 0; i < n; i++) {
      RunProgram();
    }

    EXPECT_FALSE(result.value()->isEmpty());

    EXPECT_EQ(0, clock_gettime(CLOCK_MONOTONIC, &t1));
    EXPECT_TRUE(result.value()->wait());
    EXPECT_EQ(0, clock_gettime(CLOCK_MONOTONIC, &t2));
    time1 = t1.tv_sec * 1000000000LL + t1.tv_nsec;
    time2 = t2.tv_sec * 1000000000LL + t2.tv_nsec;
    EXPECT_LE(time2 - time1, 1000000 /*ns*/);  // in x86 CF testing < 5000 ns

    EXPECT_THAT(result.value()->ConsumeAll(callback), HasValue(n));
    EXPECT_TRUE(result.value()->isEmpty());
    EXPECT_EQ(output, TEST_RINGBUF_MAGIC_NUM);
    EXPECT_EQ(run_count, n);
  }

  std::string mProgPath;
  std::string mRingbufPath;
  android::base::unique_fd mProgram;
};

TEST_F(BpfRingbufTest, ConsumeSingle) { RunTestN(1); }
TEST_F(BpfRingbufTest, ConsumeMultiple) { RunTestN(3); }

TEST_F(BpfRingbufTest, FillAndWrap) {
  int run_count = 0;
  auto callback = [&](const uint64_t&) { run_count++; };

  auto result = BpfRingbuf<uint64_t>::Create(mRingbufPath.c_str());
  ASSERT_RESULT_OK(result);

  // 4kb buffer with 16 byte payloads (8 byte data, 8 byte header) should fill
  // after 255 iterations. Exceed that so that some events are dropped.
  constexpr int iterations = 300;
  for (int i = 0; i < iterations; i++) {
    RunProgram();
  }

  // Some events were dropped, but consume all that succeeded.
  EXPECT_THAT(result.value()->ConsumeAll(callback),
              HasValue(AllOf(Gt(250), Lt(260))));
  EXPECT_THAT(run_count, AllOf(Gt(250), Lt(260)));

  // After consuming everything, we should be able to use the ring buffer again.
  run_count = 0;
  RunProgram();
  EXPECT_THAT(result.value()->ConsumeAll(callback), HasValue(1));
  EXPECT_EQ(run_count, 1);
}

TEST_F(BpfRingbufTest, WrongTypeSize) {
  // The program under test writes 8-byte uint64_t values so a ringbuffer for
  // 1-byte uint8_t values will fail to read from it. Note that the map_def does
  // not specify the value size, so we fail on read, not creation.
  auto result = BpfRingbuf<uint8_t>::Create(mRingbufPath.c_str());
  ASSERT_RESULT_OK(result);

  RunProgram();

  EXPECT_THAT(result.value()->ConsumeAll([](const uint8_t&) {}),
              HasError(WithCode(EMSGSIZE)));
}

TEST_F(BpfRingbufTest, InvalidPath) {
  EXPECT_THAT(BpfRingbuf<int>::Create("/sys/fs/bpf/bad_path"),
              HasError(WithCode(ENOENT)));
}

}  // namespace bpf
}  // namespace android
