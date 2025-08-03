/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <audio_utils/Balance.h>
#include <gtest/gtest.h>
#include <vector>

TEST(audio_utils_balance, stereo) {
  // disable ramping so we can check single frame processing.
  android::audio_utils::Balance balance(false /* ramp */);
  ASSERT_EQ(false, balance.getRamp());

  balance.setChannelMask(AUDIO_CHANNEL_OUT_STEREO);
  std::vector<float> buffer = {1.f, -1.f};

  // balance of 0 is no change.
  ASSERT_EQ(0.f, balance.getBalance());
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, -1.f}), buffer);

  // balance of 1.f is right.
  balance.setBalance(1.f);
  ASSERT_EQ(1.f, balance.getBalance());
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{0.f, -1.f}), buffer);

  // balance of -1.f is left.
  buffer = {1.f, -1.f};
  balance.setBalance(-1.f); // to left
  ASSERT_EQ(-1.f, balance.getBalance());
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, 0.f}), buffer);
}

TEST(audio_utils_balance, 7point1) {
  // disable ramping so we can check single frame processing.
  android::audio_utils::Balance balance(false /* ramp */);
  ASSERT_EQ(false, balance.getRamp());

  balance.setChannelMask(AUDIO_CHANNEL_OUT_7POINT1);
  // FL, FR, FC, LFE, BL, BR, SL, SR
  std::vector<float> buffer = {1.f, -1.f, 0.5f, 0.25f, 0.75f, 0.75f, 0.125f, 0.125f};

  // balance of 0 is no change.
  ASSERT_EQ(0.f, balance.getBalance());
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, -1.f, 0.5f, 0.25f, 0.75f, 0.75f, 0.125f, 0.125f}), buffer);

  // balance of 1.f is right.
  balance.setBalance(1.f);
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{0.f, -1.f, 0.5f, 0.25f, 0.f, 0.75f, 0.f, 0.125f}), buffer);

  // balance of -1.f is left.
  buffer = {1.f, -1.f, 0.5f, 0.25f, 0.75f, 0.75f, 0.125f, 0.125f};
  balance.setBalance(-1.f); // to left
  ASSERT_EQ(-1.f, balance.getBalance());
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, 0.f, 0.5f, 0.25f, 0.75f, 0.f, 0.125f, 0.f}), buffer);
}

TEST(audio_utils_balance, lfe) {
  // disable ramping so we can check single frame processing.
  android::audio_utils::Balance balance(false /* ramp */);
  ASSERT_EQ(false, balance.getRamp());
  std::vector<float> buffer = {1.f, -1.f};

  // NOTE: single channel falls under mono exception (we ignore balance)
  // so we pair with another "center" channel.
  // LFE by itself is considered "center".
  for (auto channelMask : {
        (AUDIO_CHANNEL_OUT_FRONT_CENTER | AUDIO_CHANNEL_OUT_LOW_FREQUENCY),
        (AUDIO_CHANNEL_OUT_FRONT_CENTER | AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2),
      }) {
    balance.setChannelMask((audio_channel_mask_t)channelMask);

      // balance of 0 is no change.
      balance.setBalance(0.f);
      balance.process(buffer.data(), 1 /* frames */);
      ASSERT_EQ((std::vector<float>{1.f, -1.f}), buffer);

      // balance of 1.f is right. (center unaffected)
      balance.setBalance(1.f);
      balance.process(buffer.data(), 1 /* frames */);
      ASSERT_EQ((std::vector<float>{1.f, -1.f}), buffer);

      // balance of -1.f is left. (center unaffected)
      balance.setBalance(-1.f);
      balance.process(buffer.data(), 1 /* frames */);
      ASSERT_EQ((std::vector<float>{1.f, -1.f}), buffer);
  }

  // If both LFE and LFE2 are present, we assume L/R.
  balance.setChannelMask((audio_channel_mask_t)
      (AUDIO_CHANNEL_OUT_LOW_FREQUENCY | AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2));
  // balance of 0 is no change.
  balance.setBalance(0.f); // to left
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, -1.f}), buffer);

  // balance of 1.f is right.
  balance.setBalance(1.f);
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{0.f, -1.f}), buffer);

  // balance of -1.f is left.
  buffer = {1.f, -1.f};
  balance.setBalance(-1.f); // to left
  balance.process(buffer.data(), 1 /* frames */);
  ASSERT_EQ((std::vector<float>{1.f, 0.f}), buffer);
}
