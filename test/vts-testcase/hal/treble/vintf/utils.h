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

#ifndef VTS_TREBLE_VINTF_TEST_UTILS_H_
#define VTS_TREBLE_VINTF_TEST_UTILS_H_
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <gtest/gtest.h>
#include <hidl-hash/Hash.h>
#include <hidl-util/FQName.h>
#include <hidl/HidlSupport.h>
#include <procpartition/procpartition.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

#include <map>
#include <optional>
#include <set>
#include <string>
#include <vector>

namespace android {
namespace vintf {
namespace testing {

using android::FQName;
using android::Hash;
using android::sp;
using android::hardware::hidl_array;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hidl::base::V1_0::IBase;
using android::hidl::manager::V1_0::IServiceManager;
using android::procpartition::Partition;
using android::vintf::HalManifest;
using android::vintf::Level;
using android::vintf::ManifestHal;
using android::vintf::ManifestInstance;
using android::vintf::RuntimeInfo;
using android::vintf::SchemaType;
using android::vintf::to_string;
using android::vintf::Transport;
using android::vintf::Version;
using android::vintf::VintfObject;

using std::cout;
using std::endl;
using std::map;
using std::multimap;
using std::optional;
using std::ostream;
using std::set;
using std::string;
using std::vector;

// Wrapper of ManifestInstance that hides details irrelevant to HIDL.
struct HidlInstance : private ManifestInstance {
 public:
  HidlInstance(const ManifestInstance& other);
  HidlInstance(const HidlInstance&) = default;
  HidlInstance(HidlInstance&&) = default;
  FQName fq_name() const {
    return FQName{package(), to_string(version()), interface()};
  }
  string instance_name() const { return instance(); };
  Transport transport() const { return ManifestInstance::transport(); }

  string test_case_name() const;
};
ostream& operator<<(ostream& os, const HidlInstance& val);

// Wrapper of ManifestInstance that hides details irrelevant to AIDL.
struct AidlInstance : private ManifestInstance {
 public:
  AidlInstance(const ManifestInstance& other);
  AidlInstance(const AidlInstance&) = default;
  AidlInstance(AidlInstance&&) = default;
  string package() const { return ManifestInstance::package(); }
  uint64_t version() const { return ManifestInstance::version().minorVer; }
  string interface() const { return ManifestInstance::interface(); }
  string instance() const { return ManifestInstance::instance(); }
  std::optional<string> updatable_via_apex() const {
    return ManifestInstance::updatableViaApex();
  }

  string test_case_name() const;
};
ostream& operator<<(ostream& os, const AidlInstance& val);

struct NativeInstance : private ManifestInstance {
 public:
  NativeInstance(const ManifestInstance& other);
  NativeInstance(const NativeInstance&) = default;
  NativeInstance(NativeInstance&&) = default;

  string package() const { return ManifestInstance::package(); }
  uint64_t minor_version() const {
    return ManifestInstance::version().minorVer;
  }
  uint64_t major_version() const {
    return ManifestInstance::version().majorVer;
  }
  string interface() const { return ManifestInstance::interface(); }
  string instance() const { return ManifestInstance::instance(); }

  string test_case_name() const;
};
ostream& operator<<(ostream& os, const NativeInstance& val);

// Sanitize a string so it can be used as a test case name.
std::string SanitizeTestCaseName(std::string original);

// Print test case name for SingleHidlTest and SingleAidlTest
template <typename Test>
std::string GetTestCaseSuffix(
    const ::testing::TestParamInfo<typename Test::ParamType>& info) {
  const auto& instance = std::get<0>(info.param);
  return instance.test_case_name() + "_" + std::to_string(info.index);
}

using HashCharArray = hidl_array<unsigned char, 32>;
using HalManifestPtr = std::shared_ptr<const HalManifest>;
using MatrixPtr = std::shared_ptr<const CompatibilityMatrix>;
using RuntimeInfoPtr = std::shared_ptr<const RuntimeInfo>;

// Path to directory on target containing test data.
extern const string kDataDir;
// Name of file containing HAL hashes.
extern const string kHashFileName;
// Map from package name to package root.
extern const map<string, string> kPackageRoot;
// HALs that are allowed to be passthrough under Treble rules.
extern const set<string> kPassthroughHals;

// Read ro.vendor.api_level, that shows the minimum of the following two
// values:
// * First non-empty value for the board api level from the following
// properties:
// -- ro.board.api_level
// -- ro.board.first_api_level
// -- ro.vendor.build.version.sdk
// * First non-empty value for the device api level from the following
// properties:
// -- ro.product.first_api_level
// -- ro.build.version.sdk
uint64_t GetBoardApiLevel();

// For a given interface returns package root if known. Returns empty string
// otherwise.
const string PackageRoot(const FQName& fq_iface_name);

// Returns true iff HAL interface is Android platform.
bool IsAndroidPlatformInterface(const FQName& fq_iface_name);

// Returns the set of released hashes for a given HAL interface.
set<string> ReleasedHashes(const FQName& fq_iface_name);

// Returns the partition that a HAL is associated with.
Partition PartitionOfProcess(int32_t pid);

// Returns SYSTEM for FRAMEWORK, VENDOR for DEVICE.
Partition PartitionOfType(SchemaType type);

}  // namespace testing
}  // namespace vintf

// Allows GTest to print pointers with a human readable string.
template <typename T>
void PrintTo(const sp<T>& v, std::ostream* os) {
  *os << android::hardware::details::toHexString<uintptr_t>(
      reinterpret_cast<uintptr_t>(&*v), true /* prefix */);
}
template <typename T>
void PrintTo(const T* v, std::ostream* os) {
  *os << android::hardware::details::toHexString<uintptr_t>(
      reinterpret_cast<uintptr_t>(v), true /* prefix */);
}

}  // namespace android

// Allows GTest to print pointers with a human readable string.
namespace std {
void PrintTo(const android::vintf::testing::HalManifestPtr& v, ostream* os);
template <typename T>
void PrintTo(const T* v, ostream* os) {
  *os << android::hardware::details::toHexString<uintptr_t>(
      reinterpret_cast<uintptr_t>(v), true /* prefix */);
}
}  // namespace std

#endif  // VTS_TREBLE_VINTF_TEST_UTILS_H_
