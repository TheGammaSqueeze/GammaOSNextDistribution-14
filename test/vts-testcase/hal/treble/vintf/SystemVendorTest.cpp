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

// SystemVendorTest test cases that runs on P+ vendor.

#include "SystemVendorTest.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <vintf/VintfObject.h>

#include <iostream>

#include "SingleManifestTest.h"

namespace android {
namespace vintf {
namespace testing {

using std::endl;
using ::testing::Combine;
using ::testing::Contains;
using ::testing::Values;
using ::testing::ValuesIn;

// Tests that device manifest and framework compatibility matrix are compatible.
TEST_F(SystemVendorTest, DeviceManifestFrameworkMatrixCompatibility) {
  auto device_manifest = VintfObject::GetDeviceHalManifest();
  ASSERT_NE(device_manifest, nullptr) << "Failed to get device HAL manifest.";
  auto fwk_matrix = VintfObject::GetFrameworkCompatibilityMatrix();
  ASSERT_NE(fwk_matrix, nullptr)
      << "Failed to get framework compatibility matrix.";

  string error;
  EXPECT_TRUE(device_manifest->checkCompatibility(*fwk_matrix, &error))
      << error;
}

// Tests that framework manifest and device compatibility matrix are compatible.
TEST_F(SystemVendorTest, FrameworkManifestDeviceMatrixCompatibility) {
  auto fwk_manifest = VintfObject::GetFrameworkHalManifest();
  ASSERT_NE(fwk_manifest, nullptr) << "Failed to get framework HAL manifest.";
  auto device_matrix = VintfObject::GetDeviceCompatibilityMatrix();
  ASSERT_NE(device_matrix, nullptr)
      << "Failed to get device compatibility matrix.";

  string error;
  EXPECT_TRUE(fwk_manifest->checkCompatibility(*device_matrix, &error))
      << error;
}

// Tests that framework compatibility matrix and runtime info are compatible.
// AVB version is not a compliance requirement.
TEST_F(SystemVendorTest, FrameworkMatrixDeviceRuntimeCompatibility) {
  auto fwk_matrix = VintfObject::GetFrameworkCompatibilityMatrix();
  ASSERT_NE(fwk_matrix, nullptr)
      << "Failed to get framework compatibility matrix.";
  auto runtime_info = VintfObject::GetRuntimeInfo();
  ASSERT_NE(nullptr, runtime_info) << "Failed to get runtime info.";

  string error;
  EXPECT_TRUE(runtime_info->checkCompatibility(
      *fwk_matrix, &error,
      ::android::vintf::CheckFlags::ENABLE_ALL_CHECKS.disableAvb()
          .disableKernel()))
      << error;
}

// Tests that runtime kernel matches requirements in compatibility matrix.
// This includes testing kernel version and kernel configurations.
TEST_F(SystemVendorTest, KernelCompatibility) {
  auto fwk_matrix = VintfObject::GetFrameworkCompatibilityMatrix();
  ASSERT_NE(fwk_matrix, nullptr)
      << "Failed to get framework compatibility matrix.";
  auto runtime_info = VintfObject::GetRuntimeInfo();
  ASSERT_NE(nullptr, runtime_info) << "Failed to get runtime info.";

  string error;
  EXPECT_TRUE(runtime_info->checkCompatibility(
      *fwk_matrix, &error,
      ::android::vintf::CheckFlags::DISABLE_ALL_CHECKS.enableKernel()))
      << error;
}

TEST_F(SystemVendorTest, NoMainlineKernel) {
  auto runtime_info = VintfObject::GetRuntimeInfo();
  ASSERT_NE(nullptr, runtime_info) << "Failed to get runtime info.";

  const bool is_release =
      base::GetProperty("ro.build.version.codename", "") == "REL";

  if (runtime_info->isMainlineKernel()) {
    if (is_release) {
      ADD_FAILURE() << "uname returns \"" << runtime_info->osRelease()
                    << "\". Mainline kernel is not allowed.";
    } else {
      GTEST_LOG_(ERROR)
          << "uname returns \"" << runtime_info->osRelease()
          << "\". Mainline kernel will not be allowed upon release.";
    }
  }
}

// Tests that vendor and framework are compatible.
// If any of the other tests in SystemVendorTest fails, this test will fail as
// well. This is a double check in case the sub-tests do not cover some
// checks.
// AVB version is not a compliance requirement.
TEST_F(SystemVendorTest, VendorFrameworkCompatibility) {
  string error;
  EXPECT_EQ(
      android::vintf::COMPATIBLE,
      VintfObject::GetInstance()->checkCompatibility(
          &error, ::android::vintf::CheckFlags::ENABLE_ALL_CHECKS.disableAvb()))
      << error;
}

template <typename D, typename S>
static void insert(D *d, const S &s) {
  d->insert(s.begin(), s.end());
}

std::set<std::string>
    SystemVendorSingleHwbinderHalTest::manifest_hwbinder_hals_;
void SystemVendorSingleHwbinderHalTest::SetUpTestSuite() {
  auto device_manifest = VintfObject::GetDeviceHalManifest();
  ASSERT_NE(device_manifest, nullptr) << "Failed to get device HAL manifest.";
  auto fwk_manifest = VintfObject::GetFrameworkHalManifest();
  ASSERT_NE(fwk_manifest, nullptr) << "Failed to get framework HAL manifest.";

  insert(&manifest_hwbinder_hals_,
         GetDeclaredHidlHalsOfTransport(fwk_manifest, Transport::HWBINDER));
  insert(&manifest_hwbinder_hals_,
         GetDeclaredHidlHalsOfTransport(device_manifest, Transport::HWBINDER));
}

std::string SystemVendorSingleHwbinderHalTest::GetTestCaseSuffix(
    const ::testing::TestParamInfo<ParamType> &info) {
  return SanitizeTestCaseName(info.param) + "_" +
         std::to_string(info.index);
}

// This needs to be tested besides
// SingleManifestTest.ServedHwbinderHalIsInManifest because some HALs may
// refuse to provide its PID, and the partition cannot be inferred.
TEST_P(SystemVendorSingleHwbinderHalTest, ServedHwbinderHalIsInManifests) {
  const auto &fq_instance_name = GetParam();
  if (fq_instance_name.find(IBase::descriptor) == 0) {
    GTEST_SKIP() << "Skipping for IBase: " << fq_instance_name;
  }

  EXPECT_THAT(manifest_hwbinder_hals_, Contains(fq_instance_name))
      << fq_instance_name << " is being served, but it is not in a manifest.";
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(
    SystemVendorSingleHwbinderHalTest);
INSTANTIATE_TEST_CASE_P(
    SystemVendorTest, SystemVendorSingleHwbinderHalTest,
    ValuesIn(SingleHwbinderHalTest::ListRegisteredHwbinderHals()),
    &SystemVendorSingleHwbinderHalTest::GetTestCaseSuffix);

// Test framework manifest only

INSTANTIATE_TEST_CASE_P(
    FrameworkManifest, SingleHidlTest,
    Combine(ValuesIn(VtsTrebleVintfTestBase::GetHidlInstances(
                VintfObject::GetFrameworkHalManifest())),
            Values(VintfObject::GetFrameworkHalManifest())),
    &GetTestCaseSuffix<SingleHidlTest>);

INSTANTIATE_TEST_CASE_P(
    FrameworkManifest, SingleHwbinderHalTest,
    Combine(ValuesIn(SingleHwbinderHalTest::ListRegisteredHwbinderHals()),
            Values(VintfObject::GetFrameworkHalManifest())),
    &SingleHwbinderHalTest::GetTestCaseSuffix);

INSTANTIATE_TEST_CASE_P(
    FrameworkManifest, SingleAidlTest,
    Combine(ValuesIn(VtsTrebleVintfTestBase::GetAidlInstances(
                VintfObject::GetFrameworkHalManifest())),
            Values(VintfObject::GetFrameworkHalManifest())),
    &GetTestCaseSuffix<SingleAidlTest>);

}  // namespace testing
}  // namespace vintf
}  // namespace android
