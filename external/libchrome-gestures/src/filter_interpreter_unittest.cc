// Copyright 2020 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include <gtest/gtest.h>

#include "include/filter_interpreter.h"
#include "include/gestures.h"
#include "include/unittest_util.h"
#include "include/util.h"

using std::string;

namespace gestures {

class FilterInterpreterTest : public ::testing::Test {
 public:
  FilterInterpreterTest() : interpreter(nullptr, nullptr, nullptr, false) {}

 protected:
  FilterInterpreter interpreter;
};

TEST_F(FilterInterpreterTest, DeadlineSettingNoDeadlines) {
  stime_t timeout_val =
    interpreter.SetNextDeadlineAndReturnTimeoutVal(10000.0, NO_DEADLINE,
                                                   NO_DEADLINE);
  EXPECT_FLOAT_EQ(NO_DEADLINE, timeout_val);
  EXPECT_FLOAT_EQ(NO_DEADLINE, interpreter.next_timer_deadline_);
}

TEST_F(FilterInterpreterTest, DeadlineSettingLocalOnly) {
  stime_t timeout_val =
    interpreter.SetNextDeadlineAndReturnTimeoutVal(10000.0, 10001.0,
                                                   NO_DEADLINE);
  EXPECT_FLOAT_EQ(1.0, timeout_val);
  EXPECT_FLOAT_EQ(NO_DEADLINE, interpreter.next_timer_deadline_);

  EXPECT_FALSE(interpreter.ShouldCallNextTimer(10001.00));
}

TEST_F(FilterInterpreterTest, DeadlineSettingNextOnly) {
  stime_t timeout_val =
    interpreter.SetNextDeadlineAndReturnTimeoutVal(10000.0, NO_DEADLINE, 1.0);
  EXPECT_FLOAT_EQ(1.0, timeout_val);
  EXPECT_FLOAT_EQ(10001.0, interpreter.next_timer_deadline_);

  EXPECT_TRUE(interpreter.ShouldCallNextTimer(NO_DEADLINE));
}

TEST_F(FilterInterpreterTest, DeadlineSettingLocalBeforeNext) {
  stime_t timeout_val =
    interpreter.SetNextDeadlineAndReturnTimeoutVal(10000.0, 10001.0, 2.0);
  EXPECT_FLOAT_EQ(1.0, timeout_val);
  EXPECT_FLOAT_EQ(10002.0, interpreter.next_timer_deadline_);

  EXPECT_FALSE(interpreter.ShouldCallNextTimer(10001.0));
}

TEST_F(FilterInterpreterTest, DeadlineSettingNextBeforeLocal) {
  stime_t timeout_val =
    interpreter.SetNextDeadlineAndReturnTimeoutVal(10000.0, 10002.0, 1.0);
  EXPECT_FLOAT_EQ(1.0, timeout_val);
  EXPECT_FLOAT_EQ(10001.0, interpreter.next_timer_deadline_);

  EXPECT_TRUE(interpreter.ShouldCallNextTimer(10002.0));
}

}  // namespace gestures
