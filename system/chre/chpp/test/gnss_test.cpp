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

#include <gtest/gtest.h>

#include "chre/pal/gnss.h"
#include "chre/platform/shared/pal_system_api.h"

#include <stddef.h>
#include <stdint.h>
#include <thread>

#include "app_test_base.h"
#include "chpp/clients/gnss.h"
#include "chpp/log.h"
#include "chpp/platform/platform_gnss.h"

/*
 * Test suite for the CHPP GNSS client/service
 */
namespace chpp {
namespace {

class ChppGnssTest : public AppTestBase {};

const struct chrePalGnssApi *gApi;

void chrePalRequestStateResync() {}

void chrePalLocationStatusChangeCallback(bool /* enabled */,
                                         uint8_t /* errorCode */) {}

void chrePalLocationEventCallback(struct chreGnssLocationEvent *event) {
  CHPP_LOGI("Got location event");
  gApi->releaseLocationEvent(event);
}

void chrePalMeasurementStatusChangeCallback(bool /* enabled */,
                                            uint8_t /*  errorCode */) {}

void chrePalMeasurementEventCallback(struct chreGnssDataEvent *event) {
  CHPP_LOGI("Got measurement event");
  gApi->releaseMeasurementDataEvent(event);
}

TEST_F(ChppGnssTest, SimpleGnss) {
  gApi = chppPalGnssGetApi(CHRE_PAL_GNSS_API_CURRENT_VERSION);
  ASSERT_NE(gApi, nullptr);

  static const struct chrePalGnssCallbacks kCallbacks = {
      .requestStateResync = chrePalRequestStateResync,
      .locationStatusChangeCallback = chrePalLocationStatusChangeCallback,
      .locationEventCallback = chrePalLocationEventCallback,
      .measurementStatusChangeCallback = chrePalMeasurementStatusChangeCallback,
      .measurementEventCallback = chrePalMeasurementEventCallback,
  };
  bool success = gApi->open(&chre::gChrePalSystemApi, &kCallbacks);
  ASSERT_TRUE(success);

  for (size_t i = 0; i < 10; i++) {
    gnssPalSendLocationEvent();
    gnssPalSendMeasurementEvent();
  }

  gApi->close();
}

TEST_F(ChppGnssTest, GnssCapabilitiesTest) {
  gApi = chppPalGnssGetApi(CHRE_PAL_GNSS_API_CURRENT_VERSION);
  ASSERT_NE(gApi, nullptr);

  static const struct chrePalGnssCallbacks kCallbacks = {
      .requestStateResync = chrePalRequestStateResync,
      .locationStatusChangeCallback = chrePalLocationStatusChangeCallback,
      .locationEventCallback = chrePalLocationEventCallback,
      .measurementStatusChangeCallback = chrePalMeasurementStatusChangeCallback,
      .measurementEventCallback = chrePalMeasurementEventCallback,
  };
  bool success = gApi->open(&chre::gChrePalSystemApi, &kCallbacks);
  ASSERT_TRUE(success);

  // Set the linkActive flag to false so that CHPP link layer does not
  // receive/send message, which causes the capabilities to be set to the
  // default CHPP_GNSS_DEFAULT_CAPABILITIES
  mClientLinkContext.isLinkActive = false;
  uint32_t capabilities = gApi->getCapabilities();
  ASSERT_EQ(capabilities, CHPP_GNSS_DEFAULT_CAPABILITIES);
  mClientLinkContext.isLinkActive = true;

  gApi->close();
}

}  //  anonymous namespace
}  //  namespace chpp
