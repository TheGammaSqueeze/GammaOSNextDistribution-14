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
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/api-level.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <gmock/gmock.h>
#include <hidl/ServiceManagement.h>

#define __ANDROID_VENDOR_API_24Q2__ 202404

namespace android {
namespace vintf {
namespace testing {

static constexpr int kMaxNumberOfHidlHalsU = 100;
// TODO(b/232439834) This number will be 0 for Android V. There is still some
// cleanup left to do.
static constexpr int kMaxNumberOfHidlHalsV = 4;

// Tests that the device is not registering any HIDL interfaces.
// HIDL is being deprecated. Only applicable to devices launching with Android
// 14 and later.
class VintfNoHidlTest : public ::testing::Test {};

// @VsrTest = VSR-3.2-001.001|VSR-3.2-001.002
TEST_F(VintfNoHidlTest, NoHidl) {
  int apiLevel = android::base::GetIntProperty("ro.vendor.api_level", 0);
  if (apiLevel < __ANDROID_API_U__) {
    GTEST_SKIP() << "Not applicable to this device";
    return;
  }
  int maxNumberOfHidlHals = 0;
  if (apiLevel == __ANDROID_API_U__) {
    maxNumberOfHidlHals = kMaxNumberOfHidlHalsU;
  } else if (apiLevel == __ANDROID_VENDOR_API_24Q2__) {
    maxNumberOfHidlHals = kMaxNumberOfHidlHalsV;
  } else {
    // TODO(232439834) We can remove this once kMaxNumberOfHidlHalsV is 0.
    GTEST_FAIL() << "Unexpected Android vendor API level (" << apiLevel
                 << "). Must be either " << __ANDROID_API_U__ << " or "
                 << __ANDROID_VENDOR_API_24Q2__;
  }
  sp<hidl::manager::V1_0::IServiceManager> sm =
      ::android::hardware::defaultServiceManager();
  ASSERT_NE(sm, nullptr);
  hardware::Return<void> ret = sm->list([&maxNumberOfHidlHals](
                                            const auto& interfaces) {
    std::set<std::string> packages;
    for (const auto& interface : interfaces) {
      std::vector<std::string> splitInterface =
          android::base::Split(interface, "@");
      ASSERT_GE(splitInterface.size(), 1);
      // We only care about packages, since HIDL HALs typically need to
      // include all of the older minor versions as well as the version they
      // are implementing
      packages.insert(splitInterface[0]);
    }
    if (packages.size() > maxNumberOfHidlHals) {
      ADD_FAILURE() << "There are " << packages.size()
                    << " HIDL interfaces served on the device. "
                    << "These must be converted to AIDL as part of HIDL's "
                       "deprecation processes.";
      for (const auto& package : packages) {
        ADD_FAILURE() << package << " registered as a HIDL interface "
                      << "but must be in AIDL";
      }
    }
  });
  ASSERT_TRUE(ret.isOk());
}

}  // namespace testing
}  // namespace vintf
}  // namespace android
