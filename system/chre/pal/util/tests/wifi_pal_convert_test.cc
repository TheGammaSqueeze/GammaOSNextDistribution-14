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

#include "chre/pal/util/wifi_pal_convert.h"
#include "chre_api/chre/wifi.h"

#include <cinttypes>

#include "chre/util/macros.h"
#include "gtest/gtest.h"

/************************************************
 *  Private functions
 ***********************************************/
namespace {

void validateLciConvert(const uint8_t *lci, size_t len,
                        const struct chreWifiRangingResult &expectedResult) {
  struct chreWifiRangingResult result;
  ASSERT_TRUE(chreWifiLciFromIe(lci, len, &result));
  EXPECT_EQ(result.lci.latitude, expectedResult.lci.latitude);
  EXPECT_EQ(result.lci.longitude, expectedResult.lci.longitude);
  EXPECT_EQ(result.lci.altitude, expectedResult.lci.altitude);
  EXPECT_EQ(result.lci.latitudeUncertainty,
            expectedResult.lci.latitudeUncertainty);
  EXPECT_EQ(result.lci.longitudeUncertainty,
            expectedResult.lci.longitudeUncertainty);
  EXPECT_EQ(result.lci.altitudeType, expectedResult.lci.altitudeType);
  EXPECT_EQ(result.lci.altitudeUncertainty,
            expectedResult.lci.altitudeUncertainty);
  EXPECT_EQ(result.flags, CHRE_WIFI_RTT_RESULT_HAS_LCI);
}

}  // anonymous namespace

/************************************************
 *  Tests
 ***********************************************/
TEST(WifiPalConvert, SimpleConvertTest) {
  // Example taken from IEEE P802.11-REVmc/D8.0, section 9.4.2.22.10
  uint8_t lci[CHRE_LCI_IE_HEADER_LEN_BYTES +
              CHRE_LCI_SUBELEMENT_HEADER_LEN_BYTES +
              CHRE_LCI_SUBELEMENT_DATA_LEN_BYTES] = {
      0x01, 0x0,  0x08, 0x0,  0x10, 0x52, 0x83, 0x4d, 0x12, 0xef, 0xd2,
      0xb0, 0x8b, 0x9b, 0x4b, 0xf1, 0xcc, 0x2c, 0x00, 0x00, 0x41};

  struct chreWifiRangingResult expectedResult;
  expectedResult.lci = {
      .latitude = -1136052723,  // -33.857 deg
      .longitude = 5073940163,  // 151.2152 deg
      .altitude = 2867,         // 11.2 m
      .latitudeUncertainty = 18,
      .longitudeUncertainty = 18,
      .altitudeType = 1,  // CHRE_WIFI_LCI_ALTITUDE_TYPE_METERS
      .altitudeUncertainty = 15,
  };

  validateLciConvert(lci, ARRAY_SIZE(lci), expectedResult);
}

TEST(WifiPalConvert, ExtraDataTest) {
  uint8_t lci[CHRE_LCI_IE_HEADER_LEN_BYTES +
              CHRE_LCI_SUBELEMENT_HEADER_LEN_BYTES +
              CHRE_LCI_SUBELEMENT_DATA_LEN_BYTES + 2] = {
      0x01, 0x0,  0x08, 0x0,  0x10, 0x52, 0x83, 0x4d, 0x12, 0xef, 0xd2, 0xb0,
      0x8b, 0x9b, 0x4b, 0xf1, 0xcc, 0x2c, 0x00, 0x00, 0x41, 0x00, 0x00};

  struct chreWifiRangingResult expectedResult;
  expectedResult.lci = {
      .latitude = -1136052723,  // -33.857 deg
      .longitude = 5073940163,  // 151.2152 deg
      .altitude = 2867,         // 11.2 m
      .latitudeUncertainty = 18,
      .longitudeUncertainty = 18,
      .altitudeType = 1,  // CHRE_WIFI_LCI_ALTITUDE_TYPE_METERS
      .altitudeUncertainty = 15,
  };

  validateLciConvert(lci, ARRAY_SIZE(lci), expectedResult);
}

TEST(WifiPalConvert, NoLciTest) {
  uint8_t lci[CHRE_LCI_IE_HEADER_LEN_BYTES +
              CHRE_LCI_SUBELEMENT_HEADER_LEN_BYTES] = {0x01, 0x0, 0x08, 0x0,
                                                       0x0};

  struct chreWifiRangingResult result;
  ASSERT_TRUE(chreWifiLciFromIe(lci, ARRAY_SIZE(lci), &result));
  EXPECT_EQ(result.flags, 0);
}

TEST(WifiPalConvert, InvalidLciTest) {
  uint8_t lci[CHRE_LCI_IE_HEADER_LEN_BYTES] = {0x01, 0x0, 0x08};

  struct chreWifiRangingResult result;
  EXPECT_FALSE(chreWifiLciFromIe(lci, ARRAY_SIZE(lci), &result));
}
