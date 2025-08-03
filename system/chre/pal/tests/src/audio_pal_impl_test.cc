/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cstdint>

#include "chre/pal/audio.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/lock_guard.h"
#include "chre/util/macros.h"
#include "chre/util/optional.h"
#include "chre/util/time.h"
#include "chre/util/unique_ptr.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace {

using ::chre::ConditionVariable;
using ::chre::gChrePalSystemApi;
using ::chre::kOneMillisecondInNanoseconds;
using ::chre::LockGuard;
using ::chre::MakeUnique;
using ::chre::Mutex;
using ::chre::Nanoseconds;
using ::chre::Optional;
using ::chre::UniquePtr;

class Callbacks {
 public:
  void audioDataEventCallback(struct chreAudioDataEvent *event) {
    LockGuard<Mutex> lock(mMutex);
    if (!mDataEvent.has_value()) {
      mDataEvent = event;
      mCondVarDataEvents.notify_one();
    }
  }

  void audioAvailabilityCallback(uint32_t handle, bool available) {
    UNUSED_VAR(handle);
    UNUSED_VAR(available);
  }

  Optional<struct chreAudioDataEvent *> mDataEvent;

  //! Synchronize access to class members.
  Mutex mMutex;
  ConditionVariable mCondVarDataEvents;
};

UniquePtr<Callbacks> gCallbacks = nullptr;

void audioDataEventCallback(struct chreAudioDataEvent *event) {
  if (gCallbacks != nullptr) {
    gCallbacks->audioDataEventCallback(event);
  }
}

void audioAvailabilityCallback(uint32_t handle, bool available) {
  if (gCallbacks != nullptr) {
    gCallbacks->audioAvailabilityCallback(handle, available);
  }
}

class PalAudioTest : public testing::Test {
 protected:
  void SetUp() override {
    gCallbacks = MakeUnique<Callbacks>();
    chre::TaskManagerSingleton::deinit();
    chre::TaskManagerSingleton::init();
    mApi = chrePalAudioGetApi(CHRE_PAL_AUDIO_API_CURRENT_VERSION);
    ASSERT_NE(mApi, nullptr);
    EXPECT_EQ(mApi->moduleVersion, CHRE_PAL_AUDIO_API_CURRENT_VERSION);
    ASSERT_TRUE(mApi->open(&gChrePalSystemApi, &mPalCallbacks));
  }

  void TearDown() override {
    if (mApi != nullptr) {
      mApi->close();
    }
    chre::TaskManagerSingleton::deinit();
    gCallbacks = nullptr;
  }

  //! CHRE PAL implementation API.
  const struct chrePalAudioApi *mApi;

  const struct chrePalAudioCallbacks mPalCallbacks = {
      .audioDataEventCallback = audioDataEventCallback,
      .audioAvailabilityCallback = audioAvailabilityCallback,
  };
};

TEST_F(PalAudioTest, GetAudioSourceInfoForExistingSource) {
  struct chreAudioSource audioSource;

  EXPECT_EQ(mApi->getSourceCount(), 1);
  EXPECT_TRUE(mApi->getAudioSource(0, &audioSource));
  EXPECT_STREQ(audioSource.name, "Test Source");
}

TEST_F(PalAudioTest, GetAudioSourceInfoForNonExistingSource) {
  struct chreAudioSource audioSource;

  EXPECT_EQ(mApi->getSourceCount(), 1);
  EXPECT_FALSE(mApi->getAudioSource(10, &audioSource));
}

TEST_F(PalAudioTest, GetDataEvent) {
  LockGuard<Mutex> lock(gCallbacks->mMutex);
  EXPECT_TRUE(mApi->requestAudioDataEvent(0 /*handle*/, 1000 /*numSamples*/,
                                          100 /*eventDelaysNs*/));
  gCallbacks->mCondVarDataEvents.wait(gCallbacks->mMutex);
  ASSERT_TRUE(gCallbacks->mDataEvent.has_value());
  struct chreAudioDataEvent *event = gCallbacks->mDataEvent.value();
  EXPECT_EQ(event->handle, 0);
  EXPECT_EQ(event->sampleCount, 1000);

  mApi->releaseAudioDataEvent(event);
}

}  // namespace