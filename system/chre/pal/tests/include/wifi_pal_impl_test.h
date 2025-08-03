/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef WIFI_PAL_IMPL_TEST_H_
#define WIFI_PAL_IMPL_TEST_H_

#include "chre/pal/wifi.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/optional.h"
#include "chre/util/time.h"
#include "gtest/gtest.h"

namespace wifi_pal_impl_test {

class PalWifiTest : public ::testing::Test {
 public:
  /**
   * Implements CHRE PAL API callbacks
   */
  void scanMonitorStatusChangeCallback(bool enabled, uint8_t errorCode);
  void scanResponseCallback(bool pending, uint8_t errorCode);
  void scanEventCallback(struct chreWifiScanEvent *event);
  void rangingEventCallback(uint8_t errorCode,
                            struct chreWifiRangingEvent *event);
  void nanServiceIdentifierCallback(uint8_t errorCode, uint32_t subscriptionId);
  void nanServiceDiscoveryCallback(struct chreWifiNanDiscoveryEvent *event);
  void nanServiceLostCallback(uint32_t subscriptionId, uint32_t publisherId);
  void nanServiceTerminatedCallback(uint32_t reason, uint32_t subscriptionId);
  void nanSubscriptionCanceledCallback(uint8_t errorCode,
                                       uint32_t subscriptionId);

 protected:
  void SetUp() override;

  void TearDown() override;

  /**
   * Validates an incoming WiFi scan event.
   *
   * @param event The WiFi scan event.
   */
  void validateWifiScanEvent(const chreWifiScanEvent &event);

  /**
   * Prepares for a subsequent PAL API call that expects an async response.
   */
  void prepareForAsyncResponse() {
    errorCode_ = CHRE_ERROR_LAST;
  }

  /**
   * Waits for an async response by the PAL implementation (e.g. via scan
   * response/monitor status change callback), and asserts that a success
   * error code was received.
   */
  void waitForAsyncResponseAssertSuccess(chre::Nanoseconds timeoutNs);

  //! The pointer to the CHRE PAL implementation API
  const struct chrePalWifiApi *api_;

  //! The error code of the most recent callback
  uint8_t errorCode_ = CHRE_ERROR_LAST;

  //! The number of scan events currently stored
  uint32_t numScanResultCount_ = 0;

  //! True if the last scan event has been received
  bool lastScanEventReceived_ = false;

  //! A list to store the scan results
  chre::DynamicVector<chreWifiScanEvent *> scanEventList_;

  //! Stores active scan params
  chre::Optional<chreWifiScanParams> scanParams_;

  //! The last scan event index received, UINT8_MAX if invalid
  uint8_t lastEventIndex_;

  //! True if scan monitoring is currently enabled
  bool scanMonitorEnabled_ = false;

  //! The last received service subscription and publisher IDs from the NAN
  //! engine.
  chre::Optional<uint32_t> subscriptionId_;
  chre::Optional<uint32_t> publishId_;

  //! Mutex to protect class variables
  chre::Mutex mutex_;
  chre::ConditionVariable condVar_;
};

}  // namespace wifi_pal_impl_test

#endif  // WIFI_PAL_IMPL_TEST_H_
