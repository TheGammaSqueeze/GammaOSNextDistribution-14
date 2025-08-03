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

#include <libvts_vintf_test_common/common.h>
#include <vintf/parse_string.h>

namespace android::vintf::testing {

// kApiLevel2FcmMap is associated with API level. There can be multiple
// Framework Compatibility Matrix Version (FCM Version) per API level, or
// multiple API levels per FCM version.
// kApiLevel2FcmMap is defined apart from android::vintf::Level. Level is an
// integer designed to be irrelevant with API level; the O / O_MR1 values are
// historic values for convenience, and should be removed (b/70628538). Hence
// these values are not used here.
// For example:
//    ...
//    // Assume devices launch with Android X must implement FCM version >= 9
//    X = 9,
//    // Assume devices launch with Android Y and Android Z must implement
//    // FCM version >= 11
//    Y = 11,
//    Z = 11
static const std::map<uint64_t /* Vendor API Level */, Level /* FCM Version */>
    kApiLevel2FcmMap{{
        // N. The test runs on devices that launch with N and
        // become a Treble device when upgrading to O.
        {25, Level::O},

        {26, Level::O},
        {27, Level::O_MR1},
        {28, Level::P},
        {29, Level::Q},
        {30, Level::R},
        {31, Level::S},
        {32, Level::S},
        {33, Level::T},
        {34, Level::U},
        // Starting from 2024Q2, vendor api level has YYYYMM format.
        {202404, Level::V},
    }};

android::base::Result<Level> GetFcmVersionFromApiLevel(uint64_t api_level) {
  auto it = android::vintf::testing::kApiLevel2FcmMap.find(api_level);
  if (it == android::vintf::testing::kApiLevel2FcmMap.end()) {
    return android::base::Error()
           << "Can't find corresponding VINTF level for API level " << api_level
           << ". Is the test updated?";
  }
  return it->second;
}

android::base::Result<void> TestTargetFcmVersion(Level target_fcm_version,
                                                 uint64_t vendor_api_level) {
  if (vendor_api_level == 0u) {
    return android::base::Error()
           << "Device's vendor API level cannot be determined.";
  }

  if (target_fcm_version == Level::UNSPECIFIED) {
    // O / O-MR1 vendor image doesn't have target FCM version declared and
    // target FCM version is inferred from vendor API level, hence it always
    // meets the requirement.
    if (vendor_api_level <= 27) {  // O-MR1
      return {};
    }
    return android::base::Error() << "Target FCM version (device manifest "
                                     "target-level) must be set for "
                                     "device with vendor api level "
                                  << vendor_api_level;
  }

  if (vendor_api_level < kApiLevel2FcmMap.begin()->first /* 25 */) {
    return android::base::Error() << "Pre-N devices should not run this test.";
  }

  auto it = kApiLevel2FcmMap.find(vendor_api_level);
  if (it == kApiLevel2FcmMap.end()) {
    return android::base::Error()
           << "No launch requirement is set yet for vendor API level "
           << vendor_api_level << ". Please update the test.";
  }

  Level required_fcm_version = it->second;
  if (target_fcm_version < required_fcm_version) {
    return android::base::Error()
           << "Vendor API level == " << vendor_api_level
           << " requires Target FCM Version >= " << required_fcm_version
           << " (but is " << target_fcm_version << ")";
  }

  return {};
}

}  // namespace android::vintf::testing
