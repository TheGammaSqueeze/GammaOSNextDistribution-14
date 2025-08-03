// Copyright (C) 2023 The Android Open Source Project
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

#include <ditto/tracer.h>
#include <gtest/gtest.h>

#include <vector>

namespace dittosuite {

TEST(DittoTracer, OneVar) {
  ASSERT_EQ(trace_format("ABC"), std::string("ABC"));
}

TEST(DittoTracer, TwoVars) {
  ASSERT_EQ(trace_format("ABC", "DEF"), std::string("ABC|DEF"));
}

TEST(DittoTracer, ThreeVar) {
  ASSERT_EQ(trace_format("ABC", "DEF", "GHI"), std::string("ABC|DEF|GHI"));
}

}  // namespace dittosuite
