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

#include <ditto/logger.h>
#include <ditto/sampler.h>
#include <gtest/gtest.h>

#include <unistd.h>

namespace dittosuite {

std::vector<timespec> tss = {{0, 0},         {0, 1}, {1, 0}, {1, 1},
                             {1, 999999999}, {2, 2}, {2, 3}, {3, 1}};
std::vector<int64_t> tss_ns = {0,          1,          1000000000, 1000000001,
                               1999999999, 2000000002, 2000000003, 3000000001};

TEST(TimespecUtilsTest, TimespecToNanos) {
  for (std::size_t i = 0; i < tss.size(); ++i) {
    ASSERT_EQ(TimespecToNanos(tss[i]), tss_ns[i]);
  }
}

TEST(TimespecUtilsTest, NanosToTimespec) {
  for (std::size_t i = 0; i < tss.size(); ++i) {
    ASSERT_EQ(tss[i], NanosToTimespec(tss_ns[i]));
  }
}

class TimeSpecConversion : public testing::TestWithParam<std::tuple<uint64_t, timespec>> {};

TEST_P(TimeSpecConversion, MicrosToTimespec) {
  auto const& param = GetParam();
  ASSERT_TRUE(MicrosToTimespec(std::get<0>(param)) == std::get<1>(param));
}

INSTANTIATE_TEST_CASE_P(TimeSpecConversionParametric, TimeSpecConversion,
                        ::testing::Values(std::make_tuple(0, (timespec){0, 0}),
                                          std::make_tuple(1e6, (timespec){1, 0}),
                                          std::make_tuple(1, (timespec){0, 1000})));

TEST(TimespecUtilsTest, TimespecToNanosInverse) {
  for (const auto& ts0 : tss) {
    ASSERT_EQ(ts0, NanosToTimespec(TimespecToNanos(ts0)));
  }
}

TEST(TimespecUtilsTest, EqualityOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 == ts1, ts0_ns == ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, DisequalityOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 != ts1, ts0_ns != ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, LessThanOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 < ts1, ts0_ns < ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, LessThanOrEqualOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 <= ts1, ts0_ns <= ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, GreaterThanOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 > ts1, ts0_ns > ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, GreaterThanOrEqualOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      ASSERT_EQ(ts0 >= ts1, ts0_ns >= ts1_ns);
    }
  }
}

TEST(TimespecUtilsTest, SumOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);
      auto sum_ns = ts0_ns + ts1_ns;
      auto sum_ts = ts0 + ts1;

      ASSERT_EQ(TimespecToNanos(sum_ts), sum_ns);
      ASSERT_EQ(sum_ts, NanosToTimespec(sum_ns));
    }
  }
}

TEST(TimespecUtilsTest, SubtractOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);
      auto sub_ns = ts0_ns - ts1_ns;
      timespec sub_ts;

      if (ts0 < ts1) {
        EXPECT_DEATH(ts0 - ts1, "");
      } else {
        sub_ts = ts0 - ts1;

        ASSERT_EQ(TimespecToNanos(sub_ts), sub_ns);
        ASSERT_EQ(sub_ts, NanosToTimespec(sub_ns));
      }
    }
  }
}

TEST(TimespecUtilsTest, DivideOperator) {
  for (const auto& ts0 : tss) {
    for (const auto& ts1 : tss) {
      auto ts0_ns = TimespecToNanos(ts0);
      auto ts1_ns = TimespecToNanos(ts1);

      if (ts1_ns == 0) {
        EXPECT_DEATH(ts0 / ts1, "");
      } else {
        auto div_ns = ts0_ns / ts1_ns;
        timespec div_ts;
        div_ts = ts0 / ts1;

        ASSERT_EQ(TimespecToNanos(div_ts), div_ns);
        ASSERT_EQ(div_ts, NanosToTimespec(div_ns));
      }
    }
  }
}

}  // namespace dittosuite
