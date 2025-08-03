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

#include "chre/pal/ble.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/lock_guard.h"
#include "chre/util/macros.h"
#include "chre/util/nanoapp/ble.h"
#include "chre/util/optional.h"
#include "chre/util/unique_ptr.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace {

using ::chre::ConditionVariable;
using ::chre::createBleScanFilterForKnownBeacons;
using ::chre::createBleScanFilterForKnownBeaconsV1_9;
using ::chre::FixedSizeVector;
using ::chre::gChrePalSystemApi;
using ::chre::LockGuard;
using ::chre::MakeUnique;
using ::chre::Milliseconds;
using ::chre::Mutex;
using ::chre::Nanoseconds;
using ::chre::Optional;
using ::chre::Seconds;
using ::chre::UniquePtr;
using ::chre::ble_constants::kNumScanFilters;

const Nanoseconds kBleStatusTimeoutNs = Milliseconds(200);
const Nanoseconds kBleEventTimeoutNs = Seconds(10);
constexpr uint32_t kBleBatchDurationMs = 0;

class Callbacks {
 public:
  Callbacks() = delete;

  explicit Callbacks(const struct chrePalBleApi *api) : mApi(api) {}

  void requestStateResync() {}

  void scanStatusChangeCallback(bool enabled, uint8_t errorCode) {
    LOGI("Received scan status change with enabled %d error %d", enabled,
         errorCode);
    LockGuard<Mutex> lock(mMutex);
    mEnabled = enabled;
    mCondVarStatus.notify_one();
  }

  void advertisingEventCallback(struct chreBleAdvertisementEvent *event) {
    LOGI("Received advertising event");
    LockGuard<Mutex> lock(mMutex);
    if (!mEventData.full()) {
      mEventData.push_back(event);
      if (mEventData.full()) {
        mCondVarEvents.notify_one();
      }
    } else {
      mApi->releaseAdvertisingEvent(event);
    }
  }

  Optional<bool> mEnabled;

  static constexpr uint32_t kNumEvents = 3;
  FixedSizeVector<struct chreBleAdvertisementEvent *, kNumEvents> mEventData;

  //! Synchronize access to class members.
  Mutex mMutex;
  ConditionVariable mCondVarStatus;
  ConditionVariable mCondVarEvents;

  //! CHRE PAL implementation API.
  const struct chrePalBleApi *mApi;
};

UniquePtr<Callbacks> gCallbacks = nullptr;

void requestStateResync() {
  if (gCallbacks != nullptr) {
    gCallbacks->requestStateResync();
  }
}

void scanStatusChangeCallback(bool enabled, uint8_t errorCode) {
  if (gCallbacks != nullptr) {
    gCallbacks->scanStatusChangeCallback(enabled, errorCode);
  }
}

void advertisingEventCallback(struct chreBleAdvertisementEvent *event) {
  if (gCallbacks != nullptr) {
    gCallbacks->advertisingEventCallback(event);
  }
}

class PalBleTest : public testing::Test {
 protected:
  void SetUp() override {
    chre::TaskManagerSingleton::deinit();
    chre::TaskManagerSingleton::init();
    mApi = chrePalBleGetApi(CHRE_PAL_BLE_API_CURRENT_VERSION);
    gCallbacks = MakeUnique<Callbacks>(mApi);
    ASSERT_NE(mApi, nullptr);
    EXPECT_EQ(mApi->moduleVersion, CHRE_PAL_BLE_API_CURRENT_VERSION);
    ASSERT_TRUE(mApi->open(&gChrePalSystemApi, &mPalCallbacks));
  }

  void TearDown() override {
    if (mApi != nullptr) {
      mApi->close();
    }
    chre::TaskManagerSingleton::deinit();
    gCallbacks = nullptr;
  }

  chreBleGenericFilter createBleGenericFilter(uint8_t type, uint8_t len,
                                              uint8_t *data, uint8_t *mask) {
    chreBleGenericFilter filter;
    memset(&filter, 0, sizeof(filter));
    filter.type = type;
    filter.len = len;
    memcpy(filter.data, data, sizeof(uint8_t) * len);
    memcpy(filter.dataMask, mask, sizeof(uint8_t) * len);
    return filter;
  }

  //! CHRE PAL implementation API.
  const struct chrePalBleApi *mApi;

  const struct chrePalBleCallbacks mPalCallbacks = {
      .requestStateResync = requestStateResync,
      .scanStatusChangeCallback = scanStatusChangeCallback,
      .advertisingEventCallback = advertisingEventCallback,
  };
};

TEST_F(PalBleTest, Capabilities) {
  auto caps = mApi->getCapabilities();
  LOGI("capabilities: 0x%x", caps);
  EXPECT_NE(caps, 0);
  EXPECT_EQ(caps & ~(CHRE_BLE_CAPABILITIES_SCAN |
                     CHRE_BLE_CAPABILITIES_SCAN_FILTER_BEST_EFFORT |
                     CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING |
                     CHRE_BLE_CAPABILITIES_SCAN_FILTER_BEST_EFFORT),
            0);

  auto filter_caps = mApi->getFilterCapabilities();
  LOGI("filter capabilities: 0x%x", filter_caps);
  EXPECT_NE(filter_caps, 0);
  EXPECT_EQ(filter_caps & ~(CHRE_BLE_FILTER_CAPABILITIES_RSSI |
                            CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA),
            0);
}

// NB: To pass this test, it is required to have an external BLE device
// advertising BLE beacons with service data for either the Google eddystone
// or fastpair UUIDs.
TEST_F(PalBleTest, FilteredScan) {
  struct chreBleScanFilterV1_9 filterV1_9;
  chreBleGenericFilter uuidFilters[kNumScanFilters];
  createBleScanFilterForKnownBeaconsV1_9(filterV1_9, uuidFilters,
                                         kNumScanFilters);

  LockGuard<Mutex> lock(gCallbacks->mMutex);

  EXPECT_TRUE(mApi->startScan(CHRE_BLE_SCAN_MODE_BACKGROUND,
                              kBleBatchDurationMs, &filterV1_9));

  EXPECT_TRUE(mApi->startScan(CHRE_BLE_SCAN_MODE_AGGRESSIVE,
                              kBleBatchDurationMs, &filterV1_9));
  gCallbacks->mCondVarStatus.wait_for(gCallbacks->mMutex, kBleStatusTimeoutNs);
  ASSERT_TRUE(gCallbacks->mEnabled.has_value());
  EXPECT_TRUE(gCallbacks->mEnabled.value());

  gCallbacks->mCondVarEvents.wait_for(gCallbacks->mMutex, kBleEventTimeoutNs);
  EXPECT_TRUE(gCallbacks->mEventData.full());
  for (auto event : gCallbacks->mEventData) {
    // TODO(b/249577259): validate event data
    mApi->releaseAdvertisingEvent(event);
  }

  EXPECT_TRUE(mApi->stopScan());
}

}  // namespace