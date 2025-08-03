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

#define LOG_TAG "audio_math_tests"

#include <audio_utils/safe_math.h>

#include <gtest/gtest.h>
#include <utils/Log.h>

TEST(audio_math_tests, safe_isnan) {
    const auto nf = std::nanf("");
    const bool std_isnan = std::isnan(nf);
    const bool eq_isnan = nf != nf;
    const bool au_isnan = android::audio_utils::safe_isnan(nf);

#ifndef FAST_MATH_ENABLED
    EXPECT_TRUE(std_isnan);  // not always true for -ffast-math, assumed false
    EXPECT_TRUE(eq_isnan);   // not always true for -ffast-math
#endif

    EXPECT_TRUE(au_isnan);
    constexpr bool not_a_nan = android::audio_utils::safe_isnan(1.f);
    EXPECT_FALSE(not_a_nan);
    EXPECT_FALSE(android::audio_utils::safe_isnan(std::numeric_limits<float>::infinity()));
    EXPECT_FALSE(android::audio_utils::safe_isnan(std::numeric_limits<float>::max()));
    EXPECT_FALSE(android::audio_utils::safe_isnan(std::numeric_limits<float>::min()));

    EXPECT_TRUE(android::audio_utils::safe_isnan(nan("")));

    ALOGD("%s: std::isnan:%d  eq_isnan:%d  audio_utils::isnan:%d",
            __func__, std_isnan, eq_isnan, au_isnan);
}

TEST(audio_math_tests, safe_isinf) {
    const auto inf = std::numeric_limits<float>::infinity();
    const bool std_isinf = std::isinf(inf);
    const bool eq_isinf = inf == std::numeric_limits<float>::infinity();
    const bool au_isinf = android::audio_utils::safe_isinf(inf);

#ifndef FAST_MATH_ENABLED
    EXPECT_TRUE(std_isinf);  // not always true for -ffast-math, assumed false
#endif

    EXPECT_TRUE(eq_isinf);
    EXPECT_TRUE(au_isinf);
    constexpr bool not_a_inf = android::audio_utils::safe_isinf(1.f);
    EXPECT_FALSE(not_a_inf);
    EXPECT_FALSE(android::audio_utils::safe_isinf(std::nanf("")));
    EXPECT_FALSE(android::audio_utils::safe_isinf(std::numeric_limits<float>::max()));
    EXPECT_FALSE(android::audio_utils::safe_isinf(std::numeric_limits<float>::min()));

    EXPECT_TRUE(android::audio_utils::safe_isinf(std::numeric_limits<double>::infinity()));

    ALOGD("%s: std::isinf:%d  eq_isinf:%d  audio_utils::isinf:%d",
            __func__, std_isinf, eq_isinf, au_isinf);
}
