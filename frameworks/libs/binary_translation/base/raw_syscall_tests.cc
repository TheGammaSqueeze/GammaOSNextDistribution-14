/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "berberis/base/raw_syscall.h"

#include <fcntl.h>
#include <linux/unistd.h>
#include <stdio.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include "gtest/gtest.h"

namespace berberis {

namespace {

TEST(RawSyscall, SyscallWith0Args) {
  pid_t pid = getpid();
  pid_t ret = RawSyscall(__NR_getpid);
  EXPECT_EQ(ret, pid);
}

TEST(RawSyscall, SyscallWith2Args) {
  struct timespec ts;
  long ret;
  ret = clock_gettime(CLOCK_REALTIME, &ts);
  EXPECT_EQ(ret, 0);

  struct timespec ts2;
  ret = RawSyscall(
      __NR_clock_gettime, static_cast<long>(CLOCK_REALTIME), reinterpret_cast<long>(&ts2));
  EXPECT_EQ(ret, 0);
  EXPECT_LE(ts2.tv_sec - ts2.tv_sec, 1)
      << "clib call and raw call should be within 1 second of each other";
}

TEST(RawSyscall, SyscallWith6Args) {
  FILE* file_in = popen("cat /dev/zero", "r");
  int fd_in = fileno(file_in);
  int fd_out = open("/dev/null", O_WRONLY);
  // Equivalent call: splice(fd_in, NULL, fd_out, NULL, 10, 0);
  long bytes_count = RawSyscall(__NR_splice, fd_in, 0, fd_out, 0, 10, 0);
  pclose(file_in);
  close(fd_out);
  EXPECT_EQ(bytes_count, 10);
}

}  // namespace

}  // namespace berberis
