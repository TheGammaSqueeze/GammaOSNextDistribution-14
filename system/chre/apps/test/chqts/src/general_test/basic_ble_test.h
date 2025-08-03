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

#ifndef _GTS_NANOAPPS_GENERAL_TEST_BASIC_BLE_TEST_H_
#define _GTS_NANOAPPS_GENERAL_TEST_BASIC_BLE_TEST_H_

#include <general_test/test.h>

#include <cstdint>

#include <shared/test_success_marker.h>

#include "chre_api/chre.h"

namespace general_test {

class BasicBleTest : public Test {
 public:
  BasicBleTest();

 protected:
  void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData) override;
  void setUp(uint32_t messageSize, const void *message) override;

 private:
  /**
   * If true, chreBleFlushAsync(...) was called. If false, it was not called.
   */
  bool mFlushWasCalled;

  /**
   * If true, the device supports batching, and we can call
   * chreBleFlushAsync(...).
   */
  bool mSupportsBatching;

  /**
   * If true, the device supports all filtering available.
   */
  bool mSupportsFiltering;

  enum BasicBleTestStage {
    BASIC_BLE_TEST_STAGE_SCAN =
        0,  // stage: BLE scanning start and stop was successful
    BASIC_BLE_TEST_STAGE_FLUSH,  // stage: the flush API was successful
    BASIC_BLE_TEST_STAGE_COUNT,  // total number of stages
  };

  nanoapp_testing::TestSuccessMarker mTestSuccessMarker =
      nanoapp_testing::TestSuccessMarker(BASIC_BLE_TEST_STAGE_COUNT);

  bool isCapabilitySet(uint32_t capability) {
    return (chreBleGetCapabilities() & capability);
  };

  bool isFilterCapabilitySet(uint32_t capability) {
    return (chreBleGetFilterCapabilities() & capability);
  };

  void handleBleAsyncResult(const chreAsyncResult *result);

  void handleAdvertisementEvent(const chreBleAdvertisementEvent *event);

  void handleTimerEvent();
};

}  // namespace general_test

#endif  // _GTS_NANOAPPS_GENERAL_TEST_BASIC_BLE_TEST_H_
