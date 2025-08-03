/*
 *  Copyright 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "bta/include/bta_api.h"
#include "bta/include/bta_gatt_api.h"
#include "bta/include/bta_hh_api.h"

using namespace testing;

// Ensure default to_test operatives provide expect output for a sample subset.
TEST(CommonStackTest, any_to_text_unknown_default) {
  ASSERT_STREQ(
      "UNKNOWN[255]",
      gatt_client_event_text(static_cast<tBTA_GATTC_EVT>(255)).c_str());
  ASSERT_STREQ("UNKNOWN[255]",
               preferred_role_text(static_cast<tBTA_PREF_ROLES>(255)).c_str());
  ASSERT_STREQ("UNKNOWN[255]",
               bta_hh_status_text(static_cast<tBTA_HH_STATUS>(255)).c_str());
}
