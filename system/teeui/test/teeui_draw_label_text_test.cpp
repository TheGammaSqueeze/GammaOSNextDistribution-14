/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <getopt.h>
#include <gtest/gtest.h>
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <teeui/example/example.h>
#include <unistd.h>

#include "teeui_device_config.h"
#include <teeui/test/teeui_render_test.h>

#define TeeuiDrawLabelTextTest_DO_LOG_DEBUG

namespace teeui {

namespace test {

static constexpr const char kText12Character8Group[] =
    "WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW "
    "WWWWWWWWWWWW";

static constexpr const char kText100Character1Group[] =
    "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW"
    "WWWWWWWW";

static constexpr const char kTextMultiLine[] =
    "Line1\n"
    "Line2. The next line is blank\n"
    "\n"
    "Line4.  This line will wrap onto other lines as it is quite long.\n"
    "LineN\n"
    "\n";

class TeeuiDrawLabelTextTest : public ::testing::Test {};

TEST_F(TeeuiDrawLabelTextTest, Test_12_char_8_group_phys_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, &kText12Character8Group[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_12_char_8_group_phys_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, &kText12Character8Group[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_100_char_1_group_phys_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, &kText100Character1Group[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_100_char_1_group_phys_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, &kText100Character1Group[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_multi_line_phys_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, &kTextMultiLine[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_multi_line_phys_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, &kTextMultiLine[0]);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_empty_text_phys_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, "");
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_empty_text_phys_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, "");
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_12_char_8_group_touch_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, &kText12Character8Group[0],
                              example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_12_char_8_group_touch_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, &kText12Character8Group[0],
                              example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_100_char_1_group_touch_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, &kText100Character1Group[0],
                              example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_100_char_1_group_touch_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, &kText100Character1Group[0],
                              example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_empty_text_touch_button_layout) {
    int error = runRenderTest("en", false /* magnified */, false, "", example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

TEST_F(TeeuiDrawLabelTextTest, Test_empty_text_touch_button_layout_magnified) {
    int error = runRenderTest("en", true /* magnified */, false, "", example::kTouchButtonLayout);
    ASSERT_EQ(error, 0);
}

}  // namespace test

}  // namespace teeui
