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

#include "chre/pal/sensor.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/lock_guard.h"
#include "chre/util/macros.h"
#include "chre/util/optional.h"
#include "chre/util/time.h"
#include "chre/util/unique_ptr.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace {

using ::chre::ConditionVariable;
using ::chre::FixedSizeVector;
using ::chre::gChrePalSystemApi;
using ::chre::kOneMillisecondInNanoseconds;
using ::chre::LockGuard;
using ::chre::MakeUnique;
using ::chre::Mutex;
using ::chre::Nanoseconds;
using ::chre::Optional;
using ::chre::UniquePtr;
using ::testing::ElementsAre;

const struct chrePalSensorApi *gApi = nullptr;

constexpr uint32_t kTimeoutMultiplier = 10;

class Callbacks {
 public:
  void samplingStatusUpdateCallback(uint32_t sensorInfoIndex,
                                    struct chreSensorSamplingStatus *status) {
    LockGuard<Mutex> lock(mMutex);
    if (!mStatusSensorIndex.has_value()) {
      mStatusSensorIndex = sensorInfoIndex;
      mStatus = status;
      mCondVarStatus.notify_one();
    }
  }

  void dataEventCallback(uint32_t sensorInfoIndex, void *data) {
    LockGuard<Mutex> lock(mMutex);
    if (gApi == nullptr) {
      return;
    }

    if (!mEventSensorIndices.full()) {
      mEventSensorIndices.push_back(sensorInfoIndex);
      mEventData.push_back(data);
      if (mEventSensorIndices.full()) {
        mCondVarEvents.notify_one();
      }
    } else {
      gApi->releaseSensorDataEvent(data);
    }
  }

  void biasEventCallback(uint32_t sensorInfoIndex, void *biasData) {
    UNUSED_VAR(sensorInfoIndex);
    UNUSED_VAR(biasData);
  }

  void flushCompleteCallback(uint32_t sensorInfoIndex, uint32_t flushRequestId,
                             uint8_t errorCode) {
    UNUSED_VAR(sensorInfoIndex);
    UNUSED_VAR(flushRequestId);
    UNUSED_VAR(errorCode);
  }

  static constexpr uint32_t kNumEvents = 3;

  Optional<uint32_t> mStatusSensorIndex;
  Optional<struct chreSensorSamplingStatus *> mStatus;

  FixedSizeVector<uint32_t, kNumEvents> mEventSensorIndices;
  FixedSizeVector<void *, kNumEvents> mEventData;

  //! Synchronize access to class members.
  Mutex mMutex;
  ConditionVariable mCondVarEvents;
  ConditionVariable mCondVarStatus;
};

UniquePtr<Callbacks> gCallbacks = nullptr;

void samplingStatusUpdateCallback(uint32_t sensorInfoIndex,
                                  struct chreSensorSamplingStatus *status) {
  if (gCallbacks != nullptr) {
    gCallbacks->samplingStatusUpdateCallback(sensorInfoIndex, status);
  }
}

void dataEventCallback(uint32_t sensorInfoIndex, void *data) {
  if (gCallbacks != nullptr) {
    gCallbacks->dataEventCallback(sensorInfoIndex, data);
  }
}

void biasEventCallback(uint32_t sensorInfoIndex, void *biasData) {
  if (gCallbacks != nullptr) {
    gCallbacks->biasEventCallback(sensorInfoIndex, biasData);
  }
}

void flushCompleteCallback(uint32_t sensorInfoIndex, uint32_t flushRequestId,
                           uint8_t errorCode) {
  if (gCallbacks != nullptr) {
    gCallbacks->flushCompleteCallback(sensorInfoIndex, flushRequestId,
                                      errorCode);
  }
}

class PalSensorTest : public testing::Test {
 protected:
  void SetUp() override {
    gCallbacks = MakeUnique<Callbacks>();
    chre::TaskManagerSingleton::deinit();
    chre::TaskManagerSingleton::init();
    gApi = chrePalSensorGetApi(CHRE_PAL_SENSOR_API_CURRENT_VERSION);
    ASSERT_NE(gApi, nullptr);
    EXPECT_EQ(gApi->moduleVersion, CHRE_PAL_SENSOR_API_CURRENT_VERSION);
    ASSERT_TRUE(gApi->open(&gChrePalSystemApi, &mPalCallbacks));
  }

  void TearDown() override {
    if (gApi != nullptr) {
      gApi->close();
    }
    chre::TaskManagerSingleton::deinit();
    gCallbacks = nullptr;
  }

  const struct chrePalSensorCallbacks mPalCallbacks = {
      .samplingStatusUpdateCallback = samplingStatusUpdateCallback,
      .dataEventCallback = dataEventCallback,
      .biasEventCallback = biasEventCallback,
      .flushCompleteCallback = flushCompleteCallback,
  };
};

TEST_F(PalSensorTest, GetTheListOfSensors) {
  const struct chreSensorInfo *sensors;
  uint32_t arraySize;

  EXPECT_TRUE(gApi->getSensors(&sensors, &arraySize));
  EXPECT_EQ(arraySize, 1);
  EXPECT_STREQ(sensors[0].sensorName, "Test Accelerometer");
}

TEST_F(PalSensorTest, EnableAContinuousSensor) {
  EXPECT_TRUE(gApi->configureSensor(
      0 /* sensorInfoIndex */, CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS,
      kOneMillisecondInNanoseconds /* intervalNs */, 0 /* latencyNs */));

  LockGuard<Mutex> lock(gCallbacks->mMutex);
  gCallbacks->mCondVarStatus.wait_for(
      gCallbacks->mMutex,
      Nanoseconds(kTimeoutMultiplier * kOneMillisecondInNanoseconds));
  ASSERT_TRUE(gCallbacks->mStatusSensorIndex.has_value());
  EXPECT_EQ(gCallbacks->mStatusSensorIndex.value(), 0);
  ASSERT_TRUE(gCallbacks->mStatus.has_value());
  EXPECT_TRUE(gCallbacks->mStatus.value()->enabled);
  gApi->releaseSamplingStatusEvent(gCallbacks->mStatus.value());
  gCallbacks->mStatus.reset();
  gCallbacks->mStatusSensorIndex.reset();

  gCallbacks->mCondVarEvents.wait_for(
      gCallbacks->mMutex,
      Nanoseconds((2 + gCallbacks->kNumEvents) * kTimeoutMultiplier *
                  kOneMillisecondInNanoseconds));
  EXPECT_TRUE(gCallbacks->mEventSensorIndices.full());
  EXPECT_THAT(gCallbacks->mEventSensorIndices, ElementsAre(0, 0, 0));
  EXPECT_TRUE(gCallbacks->mEventData.full());
  for (void *data : gCallbacks->mEventData) {
    auto threeAxisData =
        static_cast<const struct chreSensorThreeAxisData *>(data);
    EXPECT_EQ(threeAxisData->header.readingCount, 1);
    gApi->releaseSensorDataEvent(data);
  }
  // Need to unlock this mutex because the following disable sensor request
  // needs it.
  gCallbacks->mMutex.unlock();

  EXPECT_TRUE(gApi->configureSensor(
      0 /* sensorInfoIndex */, CHRE_SENSOR_CONFIGURE_MODE_DONE,
      kOneMillisecondInNanoseconds /* intervalNs */, 0 /* latencyNs */));
  gCallbacks->mMutex.lock();
  gCallbacks->mCondVarStatus.wait_for(
      gCallbacks->mMutex,
      Nanoseconds(kTimeoutMultiplier * kOneMillisecondInNanoseconds));
  ASSERT_TRUE(gCallbacks->mStatusSensorIndex.has_value());
  ASSERT_TRUE(gCallbacks->mStatus.has_value());
  gApi->releaseSamplingStatusEvent(gCallbacks->mStatus.value());
  gCallbacks->mStatus.reset();
  gCallbacks->mStatusSensorIndex.reset();
}

TEST_F(PalSensorTest, DisableAContinuousSensor) {
  EXPECT_TRUE(gApi->configureSensor(
      0 /* sensorInfoIndex */, CHRE_SENSOR_CONFIGURE_MODE_DONE,
      kOneMillisecondInNanoseconds /* intervalNs */, 0 /* latencyNs */));

  LockGuard<Mutex> lock(gCallbacks->mMutex);
  gCallbacks->mCondVarStatus.wait_for(
      gCallbacks->mMutex,
      Nanoseconds(kTimeoutMultiplier * kOneMillisecondInNanoseconds));
  ASSERT_TRUE(gCallbacks->mStatusSensorIndex.has_value());
  EXPECT_EQ(gCallbacks->mStatusSensorIndex.value(), 0);
  ASSERT_TRUE(gCallbacks->mStatus.has_value());
  EXPECT_FALSE(gCallbacks->mStatus.value()->enabled);
  gApi->releaseSamplingStatusEvent(gCallbacks->mStatus.value());
  gCallbacks->mStatus.reset();
  gCallbacks->mStatusSensorIndex.reset();
}

}  // namespace
