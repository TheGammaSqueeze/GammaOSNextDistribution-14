// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>
#include <string>
#include "MonotonicMap.h"

class MagmaTest : public ::testing::Test {
protected:
    static void SetUpTestSuite() {}
    static void TearDownTestSuite() {}
    void SetUp() override {}
    void TearDown() override {}
};

TEST_F(MagmaTest, MonotonicMap) {
    struct MapTester{
        MapTester(int i, std::string s) {
            x = i + s.length();
        }
        uint64_t x;
    };
    gfxstream::magma::MonotonicMap<uint64_t, MapTester> m;

    auto k1 = m.create(42, "hello");
    EXPECT_EQ(k1, 1);
    auto v1 = m.get(k1);
    ASSERT_NE(v1, nullptr);
    EXPECT_EQ(v1->x, 42 + 5);

    auto k2 = m.create(5, "foo");
    EXPECT_EQ(k2, 2);
    auto v2 = m.get(k2);
    ASSERT_NE(v2, nullptr);
    EXPECT_EQ(v2->x, 5 + 3);

    EXPECT_TRUE(m.erase(k1));
    EXPECT_FALSE(m.erase(k1));

    auto k3 = m.create(8, "bar");
    EXPECT_EQ(k3, 3);
    auto v3 = m.get(k3);
    ASSERT_NE(v3, nullptr);
    EXPECT_EQ(v3->x, 11);

    auto v2b = m.get(k2);
    EXPECT_EQ(v2, v2b);
}
