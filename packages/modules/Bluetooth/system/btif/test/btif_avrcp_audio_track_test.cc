/*
 * Copyright 2023 The Android Open Source Project
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

#include "btif/include/btif_avrcp_audio_track.h"

#ifndef __INTRODUCED_IN
#define __INTRODUCED_IN(x)
#endif

#include <aaudio/AAudio.h>
#include <aaudio/AAudioTesting.h>
#include <gtest/gtest.h>

#include <memory>

// Define the incomplete audio stream struct type.
struct AAudioStreamStruct {
  // The ID of the stream.
  int32_t streamId;
};

// Expected audio track.
typedef struct {
  AAudioStream* stream;
  int bitsPerSample;
  int channelCount;
  float* buffer;
  size_t bufferLength;
  float gain;
} BtifAvrcpAudioTrack;

class BtifAvrcpAudioTrackTest : public ::testing::Test {};

TEST_F(BtifAvrcpAudioTrackTest, setAudioTrackGain_maxGainSet) {
  void* track_handle = BtifAvrcpAudioTrackCreate(10, 16, 3);
  BtifAvrcpSetAudioTrackGain(track_handle, 1.0f);
  BtifAvrcpAudioTrack* trackHolder =
      static_cast<BtifAvrcpAudioTrack*>(track_handle);
  EXPECT_EQ(trackHolder->gain, 1.0f);
  BtifAvrcpAudioTrackDelete(track_handle);
}

TEST_F(BtifAvrcpAudioTrackTest, setAudioTrackGain_minimumGainSet) {
  void* track_handle = BtifAvrcpAudioTrackCreate(10, 16, 3);
  BtifAvrcpSetAudioTrackGain(track_handle, 0.0f);
  BtifAvrcpAudioTrack* trackHolder =
      static_cast<BtifAvrcpAudioTrack*>(track_handle);
  EXPECT_EQ(trackHolder->gain, 0.0f);
  BtifAvrcpAudioTrackDelete(track_handle);
}

TEST_F(BtifAvrcpAudioTrackTest,
       setAudioTrackGain_maxGainOutOfBounds_setsCappedGain) {
  void* track_handle = BtifAvrcpAudioTrackCreate(10, 16, 3);
  BtifAvrcpAudioTrack* trackHolder =
      static_cast<BtifAvrcpAudioTrack*>(track_handle);
  BtifAvrcpSetAudioTrackGain(track_handle, 2.0f);
  EXPECT_EQ(trackHolder->gain, 1.0f);
  BtifAvrcpAudioTrackDelete(track_handle);
}

TEST_F(BtifAvrcpAudioTrackTest,
       setAudioTrackGain_minGainOutOfBounds_setsCappedGain) {
  void* track_handle = BtifAvrcpAudioTrackCreate(10, 16, 3);
  BtifAvrcpAudioTrack* trackHolder =
      static_cast<BtifAvrcpAudioTrack*>(track_handle);
  BtifAvrcpSetAudioTrackGain(track_handle, -2.0f);
  EXPECT_EQ(trackHolder->gain, 0.0f);
  BtifAvrcpAudioTrackDelete(track_handle);
}

TEST_F(BtifAvrcpAudioTrackTest,
       setMaxAudioTrackGain_minGain_bufferStreamDucked) {
  constexpr float scaleQ15ToFloat = 1.0f / 32768.0f;
  constexpr size_t bufferLength = 100;
  constexpr int bitsPerSample = 16;
  constexpr size_t sampleSize = bitsPerSample / 8;
  constexpr auto gainValue = 0.5f;
  void* track_handle = BtifAvrcpAudioTrackCreate(10, bitsPerSample, 3);
  BtifAvrcpAudioTrack* trackHolder =
      static_cast<BtifAvrcpAudioTrack*>(track_handle);
  std::unique_ptr<AAudioStream> stream(new AAudioStream);
  // Set the values to track holder as mock audio lib APIs are a no-op.
  trackHolder->stream = stream.get();
  trackHolder->bufferLength = bufferLength;
  trackHolder->buffer = new float[trackHolder->bufferLength]();

  BtifAvrcpSetAudioTrackGain(trackHolder, gainValue);
  // Create a fake buffer.
  uint8_t data[bufferLength];
  for (size_t index = 0; index < bufferLength; ++index) {
    data[index] = index;
  }
  BtifAvrcpAudioTrackWriteData(trackHolder, data, bufferLength);
  const int16_t* dataInt = (int16_t*)data;
  for (size_t index = 0; index < bufferLength / sampleSize; ++index) {
    const float expected = dataInt[index] * scaleQ15ToFloat * gainValue;
    EXPECT_NEAR(expected, trackHolder->buffer[index], 0.01f);
  }
  BtifAvrcpAudioTrackDelete(trackHolder);
}
