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

#include <sys/time.h>
#include <time.h>

TEST(Time, Time) {
  time_t t = time(nullptr);
  EXPECT_NE(t, -1);
  EXPECT_NE(t, 0);
  time_t t1 = time(&t);
  EXPECT_NE(t1, -1);
  EXPECT_NE(t1, 0);
  EXPECT_LE(t1 - t, 1);
}

TEST(Time, Localtime) {
  time_t time = 123;
  struct tm time_info;
  ASSERT_EQ(localtime_r(&time, &time_info), &time_info);
  ASSERT_EQ(mktime(&time_info), 123);
}

TEST(Time, Gmtime) {
  time_t time = 123;
  struct tm time_info;
  memset(&time_info, 0, sizeof(time_info));
  ASSERT_EQ(gmtime_r(&time, &time_info), &time_info);
  EXPECT_EQ(time_info.tm_year, 70);
  EXPECT_EQ(time_info.tm_gmtoff, 0);
}

TEST(Time, Ctime) {
  time_t time = 123;
  char buf[30];
  buf[0] = 0;
  ASSERT_EQ(ctime_r(&time, buf), buf);
  ASSERT_NE(buf[0], 0);
}

TEST(Time, ClockGetres) {
  struct timespec res;
  ASSERT_EQ(clock_getres(CLOCK_REALTIME, &res), 0);
  ASSERT_TRUE(res.tv_sec != 0 || res.tv_nsec != 0);
}

TEST(Time, ClockGettime) {
  struct timespec res;
  ASSERT_EQ(clock_gettime(CLOCK_REALTIME, &res), 0);
  ASSERT_TRUE(res.tv_sec != 0 || res.tv_nsec != 0);
}

TEST(Time, Gettimeofday) {
  struct timeval tv;
  struct timezone tz;
  tv.tv_sec = -1;
  tv.tv_usec = -1;
  ASSERT_EQ(0, gettimeofday(&tv, &tz));
  EXPECT_NE(tv.tv_sec, -1);
  EXPECT_GE(tv.tv_usec, 0);
  EXPECT_LT(tv.tv_usec, 1000000);
}
