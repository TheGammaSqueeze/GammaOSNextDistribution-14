// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string.h>

#include <gtest/gtest.h>

#include "include/trace_marker.h"

namespace gestures {

class TraceMarkerTest : public ::testing::Test {};

TEST(TraceMarkerTest, DeleteTraceMarkerTest) {
    EXPECT_EQ(nullptr, TraceMarker::GetTraceMarker());
    TraceMarker::CreateTraceMarker();
    EXPECT_TRUE(nullptr != TraceMarker::GetTraceMarker());
    TraceMarker::StaticTraceWrite("Test");
    EXPECT_EQ(-1, TraceMarker::GetTraceMarker()->fd_);
    EXPECT_EQ(1, TraceMarker::trace_marker_count_);
    TraceMarker::DeleteTraceMarker();
    EXPECT_EQ(nullptr, TraceMarker::GetTraceMarker());
    TraceMarker::StaticTraceWrite("Test");
    EXPECT_EQ(0, TraceMarker::trace_marker_count_);
    TraceMarker::DeleteTraceMarker();
    EXPECT_EQ(0, TraceMarker::trace_marker_count_);
};
}  // namespace gestures
