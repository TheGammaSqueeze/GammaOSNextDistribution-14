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

#include "DeviceManifestTest.h"

#include <android-base/properties.h>
#include <android-base/result.h>
#include <libvts_vintf_test_common/common.h>
#include <vintf/VintfObject.h>

#include "SingleManifestTest.h"

using testing::Combine;
using testing::Values;
using testing::ValuesIn;

namespace android {
namespace vintf {
namespace testing {

void DeviceManifestTest::SetUp() {
  VtsTrebleVintfTestBase::SetUp();

  vendor_manifest_ = VintfObject::GetDeviceHalManifest();
  ASSERT_NE(vendor_manifest_, nullptr)
      << "Failed to get vendor HAL manifest." << endl;
}

// Tests that Shipping FCM Version in the device manifest is at least the
// minimum Shipping FCM Version as required by Board API level.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, ShippingFcmVersion) {
  uint64_t board_api_level = GetBoardApiLevel();
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  auto res = TestTargetFcmVersion(shipping_fcm_version, board_api_level);
  ASSERT_RESULT_OK(res);
}

// Tests that deprecated HALs are not in the manifest, unless a higher,
// non-deprecated minor version is in the manifest.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, NoDeprecatedHalsOnManifest) {
  string error;
  EXPECT_EQ(android::vintf::NO_DEPRECATED_HALS,
            VintfObject::GetInstance()->checkDeprecation(
                HidlInterfaceMetadata::all(), &error))
      << error;
}

// Tests that devices launching R support mapper@4.0.  Go devices are exempt
// from this requirement, so we use this test to enforce instead of the
// compatibility matrix.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, GraphicsMapperHalVersionCompatibility) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  bool is_go_device =
      android::base::GetBoolProperty("ro.config.low_ram", false);
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::R ||
      (is_go_device && shipping_fcm_version < Level::V)) {
    GTEST_SKIP() << "Graphics mapper 4 is only required on launching R devices";
  }

  if (shipping_fcm_version >= Level::V) {
    bool exists = false;
    ASSERT_TRUE(vendor_manifest_->forEachInstance(
        [&](const ManifestHal::InstanceType& instance) -> bool {
          if (instance.package() == "mapper" &&
              instance.format() == HalFormat::NATIVE &&
              instance.version().majorVer == 5 &&
              instance.version().minorVer == 0)
            exists = true;
          return true;
        }));
    ASSERT_TRUE(exists)
        << "Graphics mapper 5 is required on launching V+ devices";
  } else {
    bool exists = false;
    bool ret = vendor_manifest_->forEachInstance(
        [&](const ManifestHal::InstanceType& instance) -> bool {
          if (instance.package() == "mapper" &&
              instance.format() == HalFormat::NATIVE &&
              instance.version().majorVer == 5 &&
              instance.version().minorVer == 0)
            exists = true;
          return true;
        });
    // If native implementation doesn't exist, then the HIDL implementation must
    // exist on this device.
    if (!ret || !exists) {
      ASSERT_TRUE(vendor_manifest_->hasHidlInstance(
          "android.hardware.graphics.mapper", {4, 0}, "IMapper", "default"));
      ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
          "android.hardware.graphics.mapper", {2, 0}, "IMapper", "default"));
      ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
          "android.hardware.graphics.mapper", {2, 1}, "IMapper", "default"));
    }
  }
}

// Devices with Shipping FCM version 3~6 must have either the HIDL or the
// AIDL health HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, HealthHal) {
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.health", {2, 0}, "IHealth", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance("android.hardware.health",
                                                    1, "IHealth", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either health HIDL HAL or AIDL HAL";
}

// Devices with Shipping FCM version 5+ must have the
// AIDL power HAL.
//
// The specific versions are handled by the framework compatibility matrix.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, PowerHal) {
  Level fcm_version = VintfObject::GetDeviceHalManifest()->level();
  if (fcm_version == Level::UNSPECIFIED || fcm_version < Level::R) {
    GTEST_SKIP() << "Power HAL is only required on launching R+ devices";
  }
  ASSERT_TRUE(vendor_manifest_->hasAidlInstance("android.hardware.power",
                                                "IPower", "default"))
      << "Device must have the android.hardware.power.IPower/default HAL";
}

// Devices must have either the HIDL or the AIDL gatekeeper HAL.
// Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, GatekeeperHal) {
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.gatekeeper", {1, 0}, "IGatekeeper", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.gatekeeper", "IGatekeeper", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either gatekeeper HIDL HAL or AIDL HAL";
}

// Devices with Shipping FCM version 7 must have either the HIDL or the
// AIDL composer HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, ComposerHal) {
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.composer", {2, 1}, "IComposer", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.composer3", 1, "IComposer", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either composer HIDL HAL or AIDL HAL";
}

// Devices with Shipping FCM version 7 must have either the HIDL or the
// AIDL gralloc HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, GrallocHal) {
  bool has_hidl = false;
  for (size_t hidl_major = 2; hidl_major <= 4; hidl_major++)
    has_hidl = has_hidl || vendor_manifest_->hasHidlInstance(
                               "android.hardware.graphics.allocator",
                               {hidl_major, 0}, "IAllocator", "default");

  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.allocator", "IAllocator", "default");

  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either graphics allocator HIDL HAL or AIDL HAL";
}

// Devices after Android T must have either the HIDL or the
// AIDL thermal HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, ThermalHal) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::T) {
    GTEST_SKIP()
        << "Thermal HAL is only required on devices launching in T or later";
  }
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.thermal", {2, 0}, "IThermal", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance("android.hardware.thermal",
                                                    "IThermal", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either thermal HIDL HAL or AIDL HAL";
}

// Tests that devices launching T support allocator@4.0 or AIDL.
// Go devices are exempt
// from this requirement, so we use this test to enforce instead of the
// compatibility matrix.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, GrallocHalVersionCompatibility) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  bool is_go_device =
      android::base::GetBoolProperty("ro.config.low_ram", false);
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::T ||
      (is_go_device && shipping_fcm_version < Level::V)) {
    GTEST_SKIP() << "Gralloc 4.0/AIDL is only required on launching T devices";
  }

  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.allocator", 1, "IAllocator", "default");
  bool has_hidl_4_0 = vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {4, 0}, "IAllocator", "default");
  ASSERT_TRUE(has_aidl || has_hidl_4_0);

  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {2, 0}, "IAllocator", "default"));
  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {3, 0}, "IAllocator", "default"));
}

// Devices must have either the HIDL or the AIDL audio HAL, both "core" and
// "effect" parts must be of the same type. Checked by a test because
// compatibility matrices cannot express these conditions.
// @VsrTest = VSR-3.2-014
TEST_F(DeviceManifestTest, AudioHal) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::U) {
    GTEST_SKIP() << "AIDL Audio HAL can only appear on launching U devices";
  }
  bool has_hidl_core = false;
  for (const auto v :
       {Version(5, 0), Version(6, 0), Version(7, 0), Version(7, 1)}) {
    has_hidl_core |= vendor_manifest_->hasHidlInstance(
        "android.hardware.audio", v, "IDevicesFactory", "default");
  }
  bool has_hidl_effect = false;
  for (const auto v : {Version(5, 0), Version(6, 0), Version(7, 0)}) {
    has_hidl_effect |= vendor_manifest_->hasHidlInstance(
        "android.hardware.audio.effect", v, "IEffectsFactory", "default");
  }
  bool has_aidl_core = vendor_manifest_->hasAidlInstance(
      "android.hardware.audio.core", "IConfig", "default");
  bool has_aidl_effect = vendor_manifest_->hasAidlInstance(
      "android.hardware.audio.effect", "IFactory", "default");
  EXPECT_EQ(has_hidl_core, has_hidl_effect)
      << "Device must have both Audio Core and Effect HALs of the same type";
  EXPECT_EQ(has_aidl_core, has_aidl_effect)
      << "Device must have both Audio Core and Effect HALs of the same type";
  EXPECT_TRUE(has_hidl_core || has_aidl_core)
      << "Device must have either Audio HIDL HAL or AIDL HAL";
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SingleHidlTest);
INSTANTIATE_TEST_CASE_P(
    DeviceManifest, SingleHidlTest,
    Combine(ValuesIn(VtsTrebleVintfTestBase::GetHidlInstances(
                VintfObject::GetDeviceHalManifest())),
            Values(VintfObject::GetDeviceHalManifest())),
    &GetTestCaseSuffix<SingleHidlTest>);

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SingleHwbinderHalTest);
INSTANTIATE_TEST_CASE_P(
    DeviceManifest, SingleHwbinderHalTest,
    Combine(ValuesIn(SingleHwbinderHalTest::ListRegisteredHwbinderHals()),
            Values(VintfObject::GetDeviceHalManifest())),
    &SingleHwbinderHalTest::GetTestCaseSuffix);

INSTANTIATE_TEST_CASE_P(
    DeviceManifest, SingleAidlTest,
    Combine(ValuesIn(VtsTrebleVintfTestBase::GetAidlInstances(
                VintfObject::GetDeviceHalManifest())),
            Values(VintfObject::GetDeviceHalManifest())),
    &GetTestCaseSuffix<SingleAidlTest>);

INSTANTIATE_TEST_CASE_P(
    DeviceManifest, SingleNativeTest,
    Combine(ValuesIn(VtsTrebleVintfTestBase::GetNativeInstances(
                VintfObject::GetDeviceHalManifest())),
            Values(VintfObject::GetDeviceHalManifest())),
    &GetTestCaseSuffix<SingleNativeTest>);

}  // namespace testing
}  // namespace vintf
}  // namespace android
