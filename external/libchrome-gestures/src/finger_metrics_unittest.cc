// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/finger_metrics.h"
#include "include/gestures.h"

namespace gestures {

class FingerMetricsTest : public ::testing::Test {};

// This test adds code coverage for finger_metrics.

TEST(FingerMetricsTest, SimpleTest) {
  Vector2 v1 = {1.0, 1.0};
  Vector2 v2 = {2.0, 2.0};

  Vector2 v3 = Add(v1, v2);
  EXPECT_EQ(v3.x, 3.0);
  EXPECT_EQ(v3.y, 3.0);

  float mag = v3.Mag();
  EXPECT_GT(mag, 4.242);
  EXPECT_LT(mag, 4.243);

  EXPECT_TRUE(v1 == v1);
  EXPECT_TRUE(v1 != v2);

  EXPECT_EQ(Dot(v1, v2), 4.0);

  FingerState fs = {
    44.0, 24.0,
    30.0, 10.0,
    100.0,
    0.0,
    123.0, 321.0,
    42,
    0
  };
  FingerMetrics fm = {fs, 0.0};
  EXPECT_EQ(fm.position(), Vector2(123.0, 321.0));

  fm.Update(fs, 0.1, true);
  EXPECT_EQ(Vector2(0.0, 0.0), fm.delta());
  EXPECT_EQ(Vector2(123.0, 321.0), fm.origin_position());
  EXPECT_EQ(0.0, fm.origin_time());
  EXPECT_EQ(Vector2(0.0, 0.0), fm.origin_delta());
  EXPECT_EQ(Vector2(123.0, 321.0), fm.start_position());
  EXPECT_EQ(0.1, fm.start_time());
  EXPECT_EQ(Vector2(0.0, 0.0), fm.start_delta());
}

}  // namespace gestures
