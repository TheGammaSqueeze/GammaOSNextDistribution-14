/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <sys/epoll.h>
#include <unistd.h>

TEST(EPoll, Pipe) {
  int pipefd[2];
  ASSERT_EQ(pipe(pipefd), 0);

  int epfd = epoll_create(1);
  ASSERT_NE(epfd, -1);

  epoll_event event;
  event.events = EPOLLIN | EPOLLOUT;

  const uint64_t kData0 = 0x0123456701234567ULL;
  event.data.u64 = kData0;
  ASSERT_EQ(epoll_ctl(epfd, EPOLL_CTL_ADD, pipefd[0], &event), 0);

  const uint64_t kData1 = 0x7654321076543210ULL;
  event.data.u64 = kData1;
  ASSERT_EQ(epoll_ctl(epfd, EPOLL_CTL_ADD, pipefd[1], &event), 0);

  epoll_event events[2];
  ASSERT_EQ(epoll_wait(epfd, events, 2, -1), 1);
  ASSERT_EQ(events[0].data.u64, kData1);

  char buf = ' ';
  ASSERT_EQ(write(pipefd[1], &buf, 1), 1);

  ASSERT_EQ(epoll_ctl(epfd, EPOLL_CTL_DEL, pipefd[1], nullptr), 0);

  ASSERT_EQ(epoll_wait(epfd, events, 2, -1), 1);
  ASSERT_EQ(events[0].data.u64, kData0);

  close(epfd);
  close(pipefd[0]);
  close(pipefd[1]);
}
