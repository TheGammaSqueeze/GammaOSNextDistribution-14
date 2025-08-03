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

#include <algorithm>
#include <memory>
#include <optional>
#include <ostream>
#include <sstream>
#include <string>

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/result-gmock.h>
#include <android-base/strings.h>
#include <android/api-level.h>
#include <gmock/gmock.h>
#include <google/protobuf/repeated_field.h>
#include <google/protobuf/text_format.h>
#include <gtest/gtest.h>
#include <kver/kernel_release.h>
#include <libvts_vintf_test_common/common.h>
#include <vintf/Version.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

#include "gsi_validation_utils.h"
#include "kernel_version_matrix.pb.h"

namespace {

using android::base::testing::Ok;
using KernelVersionMatrix = std::map<uint64_t, AndroidReleaseRequirement>;

std::ostream& operator<<(std::ostream& os, const Kmi& kmi) {
  os << "android";
  if (kmi.android_release() != 0) {
    os << kmi.android_release();
  }
  return os << "-" << kmi.kernel_version().major_version() << "."
            << kmi.kernel_version().minor_version();
}

// Represents the information about the running kernel.
struct ActualKmi {
 public:
  ActualKmi(const android::vintf::KernelVersion& kernel_version,
            const std::optional<android::kver::KernelRelease>& kernel_release)
      : kernel_version_(kernel_version), kernel_release_(kernel_release) {}

  [[nodiscard]] android::vintf::Version kernel_version() const {
    return kernel_version_.dropMinor();
  }

  [[nodiscard]] std::optional<uint64_t> android_release() const {
    if (kernel_release_.has_value()) {
      return kernel_release_->android_release();
    }
    return std::nullopt;
  }

  [[nodiscard]] std::string string() const {
    std::string ret = android::vintf::to_string(kernel_version_);
    if (kernel_release_.has_value()) {
      ret += " (" + kernel_release_->string() + ")";
    }
    return ret;
  }

  friend std::ostream& operator<<(std::ostream& os, const ActualKmi& kmi) {
    os << kmi.kernel_version_;
    if (kmi.kernel_release_.has_value()) {
      os << " (" + kmi.kernel_release_->string() << ")";
    }
    return os;
  }

 private:
  android::vintf::KernelVersion kernel_version_;
  std::optional<android::kver::KernelRelease> kernel_release_;
};

// Read the raw compatibility matrix from test data.
std::optional<RawKernelVersionMatrix> ReadRawKernelVersionMatrix() {
  auto exec_dir = android::base::GetExecutableDirectory();
  auto matrix_path = exec_dir + "/kernel_version_matrix.textproto";
  std::string matrix_content;
  if (!android::base::ReadFileToString(matrix_path, &matrix_content)) {
    ADD_FAILURE() << "Can't read " << matrix_path;
    return std::nullopt;
  }

  RawKernelVersionMatrix ret;
  if (!google::protobuf::TextFormat::ParseFromString(matrix_content, &ret)) {
    ADD_FAILURE() << matrix_path << " is not valid";
    return std::nullopt;
  }
  return ret;
}

// Parse a raw KMI string (e.g. "android14-5.15") to structured Kmi object.
bool FromRaw(const std::string& s, Kmi* mutable_out) {
  auto tokens = android::base::Split(s, "-");
  if (tokens.size() != 2) {
    ADD_FAILURE() << "Unrecognized requirement: " << s;
    return false;
  }

  std::string_view android_release_sv = tokens[0];
  if (!android::base::ConsumePrefix(&android_release_sv, "android")) {
    ADD_FAILURE() << "Unrecognized requirement: " << s;
    return false;
  }

  uint64_t android_release;
  if (android_release_sv.empty()) {
    mutable_out->clear_android_release();
  } else {
    if (!android::base::ParseUint(std::string(android_release_sv),
                                  &android_release)) {
      ADD_FAILURE() << "Unrecognized requirement: " << s;
      return false;
    }
    mutable_out->set_android_release(android_release);
  }

  android::vintf::Version vintf_kernel_version;
  if (!android::vintf::parse(tokens[1], &vintf_kernel_version)) {
    ADD_FAILURE() << "Unrecognized requirement: " << s;
    return false;
  }
  mutable_out->mutable_kernel_version()->set_major_version(
      vintf_kernel_version.majorVer);
  mutable_out->mutable_kernel_version()->set_minor_version(
      vintf_kernel_version.minorVer);

  return true;
}

// Parse an array of raw KMI strings to an array of structured Kmi object.
bool FromRaw(const google::protobuf::RepeatedPtrField<std::string>& raw_in,
             google::protobuf::RepeatedPtrField<Kmi>* mutable_out) {
  mutable_out->Reserve(raw_in.size());
  for (const auto& raw_s : raw_in) {
    if (!FromRaw(raw_s, mutable_out->Add())) {
      return false;
    }
  }
  return true;
}

// Turn the raw compatibility matrix into structured data.
std::optional<KernelVersionMatrix> FromRaw(
    const RawKernelVersionMatrix& raw_kernel_version_matrix) {
  KernelVersionMatrix ret;
  for (const auto& [api_level, raw_android_release_requirements] :
       raw_kernel_version_matrix.release_requirements()) {
    AndroidReleaseRequirement ret_value;
    if (!FromRaw(raw_android_release_requirements.upgrade(),
                 ret_value.mutable_upgrade())) {
      return std::nullopt;
    }
    if (!FromRaw(raw_android_release_requirements.launch(),
                 ret_value.mutable_launch())) {
      return std::nullopt;
    }
    if (!FromRaw(raw_android_release_requirements.launch_grf(),
                 ret_value.mutable_launch_grf())) {
      return std::nullopt;
    }
    ret.emplace(api_level, std::move(ret_value));
  }
  return ret;
}

// Return requirements on kernel version and KMI for the given platform SDK
// level. Return nullptr on failure.
const google::protobuf::RepeatedPtrField<Kmi>* GetKernelVersionRequirements(
    const KernelVersionMatrix& kernel_version_matrix,
    uint32_t android_platform_release, bool is_launch, bool is_grf) {
  auto release_requirements_it =
      kernel_version_matrix.find(android_platform_release);
  if (release_requirements_it == kernel_version_matrix.end()) {
    ADD_FAILURE() << "Unable to find requirement for SDK level "
                  << android_platform_release << ". Is the test updated?";
    return nullptr;
  }

  if (is_launch) {
    if (is_grf) {
      return &release_requirements_it->second.launch_grf();
    }
    return &release_requirements_it->second.launch();
  }
  return &release_requirements_it->second.upgrade();
}

// Read the information about the running kernel that this test needs.
std::optional<ActualKmi> GetActualKmi() {
  auto vintf_object = android::vintf::VintfObject::GetInstance();
  if (vintf_object == nullptr) {
    ADD_FAILURE() << "Cannot get VintfObject instance";
    return std::nullopt;
  }
  auto runtime_info = vintf_object->getRuntimeInfo(
      android::vintf::RuntimeInfo::FetchFlag::CPU_VERSION);
  if (runtime_info == nullptr) {
    ADD_FAILURE() << "Cannot get kernel release";
    return std::nullopt;
  }

  auto kernel_release = android::kver::KernelRelease::Parse(
      runtime_info->osRelease(), true /* allow suffix */);
  auto kernel_version = runtime_info->kernelVersion();

  return std::make_optional<ActualKmi>(kernel_version, kernel_release);
}

// Check kernel version and KMI against a list of requirements.
bool KernelVersionIsSupported(
    const ActualKmi& actual,
    const google::protobuf::RepeatedPtrField<Kmi>& requirements,
    std::string* error) {
  for (const auto& req : requirements) {
    if (req.kernel_version().major_version() !=
            actual.kernel_version().majorVer ||
        req.kernel_version().minor_version() !=
            actual.kernel_version().minorVer) {
      GTEST_LOG_(INFO) << "Failed to match " << actual << " against required "
                       << req << ": kernel version does not match.";
      continue;  // check next item
    }

    if (req.android_release() != actual.android_release().value_or(0)) {
      GTEST_LOG_(INFO) << "Failed to match " << actual << " against required "
                       << req
                       << ": The Android release part of KMI does not match.";
      continue;  // check next item
    }

    GTEST_LOG_(INFO) << "Matched " << actual << " against requirement " << req;
    return true;
  }

  std::stringstream error_stream;
  error_stream << "Kernel " << actual << " is not valid. It must be one of [\n";
  for (const auto& req : requirements) {
    error_stream << "  " << req << ",\n";
  }
  error_stream << "].";
  *error = error_stream.str();
  return false;
}

// If ro.build.version.codename == REL, expect the given condition is true.
// Otherwise, only log a warning message, but don't fail the test.
struct ExpectTrueOnRelease : public std::stringstream {
  explicit ExpectTrueOnRelease(bool cond) : cond_(cond) {}
  ~ExpectTrueOnRelease() override {
    if (cond_) {
      return;
    }
    if (!IsReleasedAndroidVersion()) {
      GTEST_LOG_(ERROR) << str() << " This will be an error upon release.";
    } else {
      ADD_FAILURE() << str();
    }
  }

 private:
  bool cond_;
};

bool IsGrf() {
  return !android::base::GetProperty("ro.board.first_api_level", "").empty();
}

// Returns true if the device has the specified feature.
bool DeviceSupportsFeature(const char* feature) {
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
    pclose(p);
  }
  return device_supports_feature;
}

TEST(KernelVersionTest, AgainstPlatformRelease) {
  auto raw_kernel_version_matrix = ReadRawKernelVersionMatrix();
  ASSERT_TRUE(raw_kernel_version_matrix.has_value());
  ASSERT_GT(raw_kernel_version_matrix->release_requirements_size(), 0);

  auto kernel_version_matrix = FromRaw(*raw_kernel_version_matrix);
  ASSERT_TRUE(kernel_version_matrix.has_value());
  ASSERT_FALSE(kernel_version_matrix->empty());

  auto android_platform_release = GetSdkLevel();

  auto min_enforcing_android_release = kernel_version_matrix->begin()->first;
  if (android_platform_release < min_enforcing_android_release) {
    GTEST_SKIP() << "Kernel version is not enforced for platform SDK level "
                 << android_platform_release << " ( < "
                 << min_enforcing_android_release << " )";
  }

  auto product_first_api_level = GetProductFirstApiLevel();
  ExpectTrueOnRelease(product_first_api_level <= android_platform_release)
      << "Product first API level " << product_first_api_level
      << " should not exceed the platform release " << android_platform_release
      << ".";

  const static bool is_tv_device =
      DeviceSupportsFeature("android.software.leanback");
  if (product_first_api_level <= 33 && is_tv_device) {
    GTEST_SKIP()
        << "Exempt from GKI test on TV devices launched before Android U";
  }

  bool is_launch = product_first_api_level >= android_platform_release;

  auto requirements = GetKernelVersionRequirements(
      *kernel_version_matrix, android_platform_release, is_launch, IsGrf());
  ASSERT_NE(requirements, nullptr);

  auto actual = GetActualKmi();
  ASSERT_TRUE(actual.has_value());

  std::string error;
  ExpectTrueOnRelease(KernelVersionIsSupported(*actual, *requirements, &error))
      << error;
}

TEST(KernelVersionTest, GrfDevicesMustUseLatestKernel) {
  if (!IsGrf()) {
    GTEST_SKIP() << "Non-GRF device kernel requirements are checked in "
                    "SystemVendorTest.KernelCompatibility";
  }

  auto vendor_api_level = GetVendorApiLevel();
  ASSERT_TRUE(vendor_api_level != 0)
      << "Unable to determine board API level on GRF devices";

  if (vendor_api_level <= __ANDROID_API_R__) {
    GTEST_SKIP() << "[VSR-3.4.1-001] does not enforce latest kernel x.y for "
                 << "vendor_api_level == " << vendor_api_level << " <= R";
  }

  auto corresponding_vintf_level =
      android::vintf::testing::GetFcmVersionFromApiLevel(vendor_api_level);
  ASSERT_THAT(corresponding_vintf_level, Ok());

  auto latest_min_lts =
      android::vintf::VintfObject::GetInstance()->getLatestMinLtsAtFcmVersion(
          *corresponding_vintf_level);
  ASSERT_THAT(latest_min_lts, Ok());
  auto runtime_info =
      android::vintf::VintfObject::GetInstance()->getRuntimeInfo(
          android::vintf::RuntimeInfo::FetchFlag::CPU_VERSION);
  ASSERT_NE(runtime_info, nullptr);
  auto kernel_version = runtime_info->kernelVersion();

  ASSERT_GE(kernel_version, *latest_min_lts)
      << "[VSR-3.4.1-001] CHIPSETs that are on GRF and are frozen on API level "
      << vendor_api_level << " (corresponding to VINTF level "
      << *corresponding_vintf_level << ") must use kernel version ("
      << *latest_min_lts << ")+, but kernel version is " << kernel_version;
}

}  // namespace
