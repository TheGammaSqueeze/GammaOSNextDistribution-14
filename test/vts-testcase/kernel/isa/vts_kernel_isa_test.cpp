/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android/api-level.h>
#include <android-base/properties.h>
#include <gtest/gtest.h>
#include <vintf/VintfObject.h>

using android::vintf::VintfObject;
using android::vintf::RuntimeInfo;

std::optional<std::string> get_config(
    const std::map<std::string, std::string>& configs, const std::string& key) {
  auto it = configs.find(key);
  if (it == configs.end()) {
    return std::nullopt;
  }
  return it->second;
}

// Returns true if the device has the specified feature.
static bool deviceSupportsFeature(const char* feature) {
  bool device_supports_feature = false;
  FILE* p = popen("pm list features", "re");
  if (p) {
    char* line = NULL;
    size_t len = 0;
    while (getline(&line, &len, p) > 0) {
      if (strstr(line, feature)) {
        device_supports_feature = true;
        break;
      }
    }
    if (line) {
      free(line);
      line = NULL;
    }
    pclose(p);
  }
  return device_supports_feature;
}

static bool isTV() {
  return deviceSupportsFeature("android.software.leanback");
}

/*
 * Tests that the kernel in use is meant to run on CPUs that support a
 * 64-bit Instruction Set Architecture (ISA).
 */
TEST(KernelISATest, KernelUses64BitISA) {
  /*
   * Exclude VSR-3.12 from Android TV
   */

  if (isTV()) GTEST_SKIP() << "Exempt from TV devices";
  /*
   * ro.vendor.api_level is the VSR API level, which is calculated
   * as:
   *
   * vendor.api_level = min(ro.product.first_api_level, ro.board.[first_]api_level)
   *
   * If ro.board.api_level is defined, it is used for the comparison instead
   * of ro.board.first_api_level.
   */
  int vendor_api_level = android::base::GetIntProperty("ro.vendor.api_level",
                                                       -1);

  /*
   * Ensure that we run this test for devices launching with Android 14+, but not
   * devices that are upgrading to Android 14+.
   */
  if (vendor_api_level < __ANDROID_API_U__)
    GTEST_SKIP() << "Exempt from KernelUses64BitISATest: ro.vendor.api_level ("
                 << vendor_api_level << ") < " << __ANDROID_API_U__;

  std::shared_ptr<const RuntimeInfo> runtime_info = VintfObject::GetRuntimeInfo();
  ASSERT_NE(nullptr, runtime_info);

  const auto& configs = runtime_info->kernelConfigs();
  ASSERT_EQ(get_config(configs, "CONFIG_64BIT"), "y") << "VSR-3.12: Devices "
                                                      << "launching with Android 14+ "
                                                      << "must support 64-bit ABIs and "
                                                      << "thus must use a 64-bit kernel.";
}
