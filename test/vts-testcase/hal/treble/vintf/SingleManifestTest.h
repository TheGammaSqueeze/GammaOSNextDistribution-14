/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef VTS_TREBLE_VINTF_TEST_SINGLE_MANIFEST_TEST_H_
#define VTS_TREBLE_VINTF_TEST_SINGLE_MANIFEST_TEST_H_
#include <gtest/gtest.h>
#include <hidl-util/FqInstance.h>

#include "VtsTrebleVintfTestBase.h"

namespace android {
namespace vintf {
namespace testing {

// A parameterized test for an HIDL HAL declared in a device or framework
// manifest.
class SingleHidlTest : public VtsTrebleVintfTestBase,
                       public ::testing::WithParamInterface<
                           std::tuple<HidlInstance, HalManifestPtr>> {
 public:
  virtual ~SingleHidlTest() {}

  sp<IBase> GetPassthroughService(const android::FqInstance& fq_instance);
};

// A parameterized test for an HIDL HAL registered through hwservicemanager
// for a given device or framework manifest.
class SingleHwbinderHalTest
    : public VtsTrebleVintfTestBase,
      public ::testing::WithParamInterface<
          std::tuple<std::string /* fq instance name */, HalManifestPtr>> {
 public:
  virtual ~SingleHwbinderHalTest() {}

  static std::string GetTestCaseSuffix(
      const ::testing::TestParamInfo<ParamType>& info);
};

// A parameterized test for an AIDL HAL declared in a device or framework
// manifest.
class SingleAidlTest : public VtsTrebleVintfTestBase,
                       public ::testing::WithParamInterface<
                           std::tuple<AidlInstance, HalManifestPtr>> {
 public:
  virtual ~SingleAidlTest() {}
};

// A parameterized test for a native HAL in one of the manifests.
class SingleNativeTest : public VtsTrebleVintfTestBase,
                         public ::testing::WithParamInterface<
                             std::tuple<NativeInstance, HalManifestPtr>> {
 public:
  virtual ~SingleNativeTest() {}
};

}  // namespace testing
}  // namespace vintf
}  // namespace android

#endif  // VTS_TREBLE_VINTF_TEST_SINGLE_MANIFEST_TEST_H_
