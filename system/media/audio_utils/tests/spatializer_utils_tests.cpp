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

#include <gtest/gtest.h>
#include <log/log.h>
#include <system/audio.h>

TEST(spatializer_utils_tests, basic)
{
    // We don't spatialize mono at this time.
    ASSERT_FALSE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_MONO));

    // These common multichannel formats we spatialize.
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_QUAD));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_QUAD_SIDE));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_5POINT1));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_5POINT1_SIDE));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_5POINT1POINT4));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_7POINT1));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_7POINT1POINT2));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_7POINT1POINT4));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_9POINT1POINT4));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_9POINT1POINT6));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_13POINT_360RA));
    ASSERT_TRUE(audio_is_channel_mask_spatialized(AUDIO_CHANNEL_OUT_22POINT2));
}
