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
#include <string>

#define LOG_TAG "AudioAidlUtilTest"
#include <log/log.h>
#include <system/audio_aidl_utils.h>
#include <system/audio_effects/effect_uuid.h>

TEST(AudioAidlUtilTest, UuidToString) {
  const std::string testStr = "7b491460-8d4d-1143-0000-0002a5d5c51b";
  const auto uuid = ::aidl::android::hardware::audio::effect::stringToUuid(testStr.c_str());
  const auto targetStr = ::android::audio::utils::toString(uuid);
  EXPECT_EQ(testStr, targetStr);
}
