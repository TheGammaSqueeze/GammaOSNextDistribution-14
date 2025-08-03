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

#ifndef GNSSPAL_IMPL_TEST_H_
#define GNSSPAL_IMPL_TEST_H_

#include "chre/pal/gnss.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/time.h"
#include "gtest/gtest.h"

namespace gnss_pal_impl_test {

class PalGnssTest : public ::testing::TestWithParam<uint64_t> {
 public:
  /**
   * Implements CHRE PAL API callbacks
   */
  void requestStateResync();
  void locationStatusChangeCallback(bool enabled, uint8_t errorCode);
  void locationEventCallback(struct chreGnssLocationEvent *event);
  void measurementStatusChangeCallback(bool enabled, uint8_t errorCode);
  void measurementEventCallback(struct chreGnssDataEvent *event);

 protected:
  void SetUp() override;

  void TearDown() override;

  /**
   * Prepares for a subsequent PAL API call that expects an async response.
   */
  void prepareForAsyncResponse() {
    errorCode_ = CHRE_ERROR_LAST;
  }

  /**
   * Waits for an async response by the PAL implementation (e.g. via location
   * or measurement status change callback), and asserts that a success
   * error code was received.
   */
  void waitForAsyncResponseAssertSuccess(chre::Nanoseconds timeoutNs);

  //! The pointer to the CHRE PAL implementation API
  const struct chrePalGnssApi *api_;

  //! The error code of the most recent callback
  uint8_t errorCode_ = CHRE_ERROR_LAST;

  //! True if location session is currently enabled
  bool locationSessionEnabled_ = false;

  //! True if location session is currently enabled
  bool measurementSessionEnabled_ = false;

  //! A list to store the location events
  static constexpr size_t kEventArraySize = 5;
  chre::FixedSizeVector<chreGnssLocationEvent *, kEventArraySize>
      locationEventVector_;
  chre::FixedSizeVector<chreGnssDataEvent *, kEventArraySize>
      measurementEventVector_;

  //! Mutex to protect class variables
  chre::Mutex mutex_;
  chre::ConditionVariable condVar_;
};

}  // namespace gnss_pal_impl_test

#endif  // GNSSPAL_IMPL_TEST_H_
