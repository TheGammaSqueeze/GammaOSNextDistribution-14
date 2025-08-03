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

#include <filesystem>
#include <optional>

#include <android-base/properties.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <kver/kernel_release.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

#include "ramdisk_utils.h"

using android::base::GetBoolProperty;
using android::base::GetProperty;
using android::kver::KernelRelease;
using android::vintf::Level;
using android::vintf::RuntimeInfo;
using android::vintf::Version;
using android::vintf::VintfObject;
using testing::IsSupersetOf;

// Returns true iff the device has the specified feature.
static bool deviceSupportsFeature(const char *feature) {
  bool device_supports_feature = false;
  FILE *p = popen("pm list features", "re");
  if (p) {
    char *line = NULL;
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

std::optional<std::string> get_config(
    const std::map<std::string, std::string>& configs, const std::string& key) {
  auto it = configs.find(key);
  if (it == configs.end()) {
    return std::nullopt;
  }
  return it->second;
}

class GenericBootImageTest : public testing::Test {
 public:
  void SetUp() override {
    auto vintf = VintfObject::GetInstance();
    ASSERT_NE(nullptr, vintf);
    runtime_info = vintf->getRuntimeInfo(RuntimeInfo::FetchFlag::CPU_VERSION |
                                         RuntimeInfo::FetchFlag::CONFIG_GZ);
    ASSERT_NE(nullptr, runtime_info);

    const auto& configs = runtime_info->kernelConfigs();
    if (get_config(configs, "CONFIG_ARM") == "y") {
      GTEST_SKIP() << "Skipping on 32-bit ARM devices";
    } else if (get_config(configs, "CONFIG_X86") == "y" ||
               get_config(configs, "CONFIG_X86_64") == "y") {
      GTEST_SKIP() << "Skipping on X86 & X86_64 devices";
    }
    // Technically, the test should also be skipped on CONFIG_X86 and
    // CONFIG_X86_64, and only run on CONFIG_ARM64,
    // but we want to keep this test passing on virtual
    // device targets, and we don't have any requests to skip this test
    // on x86 / x86_64 as of 2022-06-07.

    int firstApiLevel = std::stoi(android::base::GetProperty("ro.product.first_api_level", "0"));
    if (isTV() && firstApiLevel <= __ANDROID_API_T__) {
      GTEST_SKIP() << "Skipping on TV devices";
    }
  }
  std::shared_ptr<const RuntimeInfo> runtime_info;
};

TEST_F(GenericBootImageTest, KernelReleaseFormat) {
  // On "GKI 2.0" with 5.10+ kernels, VTS runs once with the device kernel,
  // so this test is meaningful.
  if (runtime_info->kernelVersion().dropMinor() < Version{5, 10}) {
    GTEST_SKIP() << "Exempt generic kernel image (GKI) test on kernel "
                 << runtime_info->kernelVersion()
                 << ". Only required on 5.10+.";
  }

  const std::string& release = runtime_info->osRelease();
  ASSERT_TRUE(
      KernelRelease::Parse(release, true /* allow_suffix */).has_value())
      << "Kernel release '" << release
      << "' does not have generic kernel image (GKI) release format. It must "
         "match this regex:\n"
      << R"(^(?P<w>\d+)[.](?P<x>\d+)[.](?P<y>\d+)-(?P<z>android\d+)-(?P<k>\d+).*$)"
      << "\nExample: 5.4.42-android12-0-something";
}

std::set<std::string> GetRequirementBySdkLevel(uint32_t target_sdk_level) {
  // Files which must be present in generic ramdisk. This list acts as a lower
  // bound for device's ramdisk.
  static const std::map<uint32_t, std::set<std::string>> required_by_level = {
      {0, {"init", "system/etc/ramdisk/build.prop"}},  // or some other number?
      {
          __ANDROID_API_T__,
          {"system/bin/snapuserd", "system/etc/init/snapuserd.rc"},
      }};
  std::set<std::string> res;
  for (const auto& [level, requirements] : required_by_level) {
    if (level > target_sdk_level) {
      break;
    }
    res.insert(requirements.begin(), requirements.end());
  }
  return res;
}

std::set<std::string> GetAllowListBySdkLevel(uint32_t target_sdk_level) {
  // Files that are allowed in generic ramdisk(but not necessarily required)
  // This list acts as an upper bound for what the device's ramdisk can possibly
  // contain.
  static const std::map<uint32_t, std::set<std::string>> allow_by_level = {
      {__ANDROID_API_T__, {"system/bin/snapuserd_ramdisk"}},
      {__ANDROID_API_U__, {"dev/console", "dev/null", "dev/urandom"}},
  };
  auto res = GetRequirementBySdkLevel(target_sdk_level);
  for (const auto& [level, requirements] : allow_by_level) {
    if (level > target_sdk_level) {
      break;
    }
    res.insert(requirements.begin(), requirements.end());
  }
  return res;
}

TEST_F(GenericBootImageTest, GenericRamdisk) {
  // On "GKI 2.0" with 5.10+ kernels, VTS runs once with the device kernel,
  // so this test is meaningful.
  if (runtime_info->kernelVersion().dropMinor() < Version{5, 10}) {
    GTEST_SKIP() << "Exempt generic ramdisk test on kernel "
                 << runtime_info->kernelVersion()
                 << ". Only required on 5.10+.";
    return;
  }

  using std::filesystem::recursive_directory_iterator;

  std::string slot_suffix = GetProperty("ro.boot.slot_suffix", "");
  // Launching devices with T+ using android13+ kernels have the ramdisk in
  // init_boot instead of boot
  std::string error_msg;
  const auto kernel_level =
      VintfObject::GetInstance()->getKernelLevel(&error_msg);
  ASSERT_NE(Level::UNSPECIFIED, kernel_level) << error_msg;
  std::string boot_path;
  if (kernel_level >= Level::T) {
    if (std::stoi(android::base::GetProperty("ro.vendor.api_level", "0")) >=
        __ANDROID_API_T__) {
      boot_path = "/dev/block/by-name/init_boot" + slot_suffix;
    } else {
      // This is the case of a device launched before Android 13 that is
      // upgrading its kernel to android13+. These devices can't add an
      // init_boot partition and need to include the equivalent ramdisk
      // functionality somewhere outside of boot.img (most likely in the
      // vendor_boot image). Since we don't know where to look, or which files
      // will be present, we can skip the rest of this test case.
      GTEST_SKIP() << "Exempt generic ramdisk test on upgrading device that "
                   << "launched before Android 13 and is now using an Android "
                   << "13+ kernel.";
      return;
    }
  } else {
    boot_path = "/dev/block/by-name/boot" + slot_suffix;
  }
  if (0 != access(boot_path.c_str(), R_OK)) {
    int saved_errno = errno;
    FAIL() << "Can't access " << boot_path << ": " << strerror(saved_errno);
    return;
  }

  const auto extracted_ramdisk = android::ExtractRamdiskToDirectory(boot_path);
  ASSERT_TRUE(extracted_ramdisk.ok())
      << "Failed to extract ramdisk: " << extracted_ramdisk.error();

  std::set<std::string> actual_files;
  const std::filesystem::path extracted_ramdisk_path((*extracted_ramdisk)->path);
  for (auto& p : recursive_directory_iterator(extracted_ramdisk_path)) {
    if (p.is_directory()) continue;
    auto rel_path = p.path().lexically_relative(extracted_ramdisk_path);
    actual_files.insert(rel_path.string());
  }

  const auto sdk_level =
      android::base::GetIntProperty("ro.bootimage.build.version.sdk", 0);
  const std::set<std::string> generic_ramdisk_required_list =
      GetRequirementBySdkLevel(sdk_level);
  std::set<std::string> generic_ramdisk_allow_list =
      GetAllowListBySdkLevel(sdk_level);

  const bool is_debuggable = GetBoolProperty("ro.debuggable", false);
  if (is_debuggable) {
    const std::set<std::string> debuggable_allowlist{
        "adb_debug.prop",
        "force_debuggable",
        "userdebug_plat_sepolicy.cil",
    };
    generic_ramdisk_allow_list.insert(debuggable_allowlist.begin(),
                                      debuggable_allowlist.end());
  }
  EXPECT_THAT(actual_files, IsSupersetOf(generic_ramdisk_required_list))
      << "Missing files required by " << (is_debuggable ? "debuggable " : "")
      << "generic ramdisk";
  EXPECT_THAT(generic_ramdisk_allow_list, IsSupersetOf(actual_files))
      << "Contains files disallowed by " << (is_debuggable ? "debuggable " : "")
      << "generic ramdisk";
}
