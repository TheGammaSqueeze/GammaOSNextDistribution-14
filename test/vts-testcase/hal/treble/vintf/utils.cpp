
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

#include "utils.h"

#include <android-base/logging.h>
#include <android-base/properties.h>

#include <map>
#include <set>
#include <string>
#include <vector>

using android::base::GetUintProperty;

namespace android {
namespace vintf {
namespace testing {

// Path to directory on target containing test data.
const string kDataDir = "/data/local/tmp/";

// Name of file containing HAL hashes.
const string kHashFileName = "current.txt";

// Map from package name to package root.
const map<string, string> kPackageRoot = {
    {"android.frameworks", "frameworks/hardware/interfaces/"},
    {"android.hardware", "hardware/interfaces/"},
    {"android.hidl", "system/libhidl/transport/"},
    {"android.system", "system/hardware/interfaces/"},
};

// HALs that are allowed to be passthrough under Treble rules.
const set<string> kPassthroughHals = {
    "android.hardware.graphics.mapper", "android.hardware.renderscript",
    "android.hidl.memory",
};

std::string SanitizeTestCaseName(std::string original) {
  for (char &c : original) {
    if (!isalnum(c)) {
      c = '_';
    }
  }
  return original;
}

HidlInstance::HidlInstance(const ManifestInstance &other)
    : ManifestInstance(other) {
  CHECK_EQ(format(), HalFormat::HIDL);
}

ostream &operator<<(ostream &os, const HidlInstance& val) {
  return os << val.transport() << " HAL " << val.fq_name().string() << "/"
            << val.instance_name();
}

string HidlInstance::test_case_name() const {
  return SanitizeTestCaseName(fq_name().string() + "/" + instance_name());
}

AidlInstance::AidlInstance(const ManifestInstance &other)
    : ManifestInstance(other) {
  CHECK_EQ(format(), HalFormat::AIDL);
}

string AidlInstance::test_case_name() const {
  return SanitizeTestCaseName(
      toAidlFqnameString(package(), interface(), instance()) + "_V" +
      to_string(version()));
}

ostream &operator<<(ostream &os, const AidlInstance& val) {
  os << toAidlFqnameString(val.package(), val.interface(), val.instance())
     << ", Version " << val.version();
  if (val.updatable_via_apex()) {
    os << ", updatable_via_apex = " << *val.updatable_via_apex();
  }
  return os;
}

NativeInstance::NativeInstance(const ManifestInstance &other)
    : ManifestInstance(other) {
  CHECK_EQ(format(), HalFormat::NATIVE);
}

string NativeInstance::test_case_name() const {
  return SanitizeTestCaseName(
      toAidlFqnameString(package(), interface(), instance()) + "_V" +
      to_string(version()));
}

ostream &operator<<(ostream &os, const NativeInstance &val) {
  os << "Native HAL { package: " << val.package()
     << " version: " << val.major_version() << "." << val.minor_version()
     << " interface: " << val.interface() << " instance: " << val.instance()
     << " }";
  return os;
}

uint64_t GetBoardApiLevel() {
  return GetUintProperty<uint64_t>("ro.vendor.api_level", 0);
}

// For a given interface returns package root if known. Returns empty string
// otherwise.
const string PackageRoot(const FQName &fq_iface_name) {
  for (const auto &package_root : kPackageRoot) {
    if (fq_iface_name.inPackage(package_root.first)) {
      return package_root.second;
    }
  }
  return "";
}

// Returns true iff HAL interface is Android platform.
bool IsAndroidPlatformInterface(const FQName &fq_iface_name) {
  // Package roots are only known for Android platform packages.
  return !PackageRoot(fq_iface_name).empty();
}

// Returns the set of released hashes for a given HAL interface.
set<string> ReleasedHashes(const FQName &fq_iface_name) {
  set<string> released_hashes{};
  string err = "";

  string file_path = kDataDir + PackageRoot(fq_iface_name) + kHashFileName;
  auto hashes = Hash::lookupHash(file_path, fq_iface_name.string(), &err);
  released_hashes.insert(hashes.begin(), hashes.end());
  return released_hashes;
}

// Returns the partition that a HAL is associated with.
Partition PartitionOfProcess(int32_t pid) {
  auto partition = android::procpartition::getPartition(pid);

  // TODO(b/70033981): remove once ODM and Vendor manifests are distinguished
  if (partition == Partition::ODM) {
    partition = Partition::VENDOR;
  }

  return partition;
}

Partition PartitionOfType(SchemaType type) {
  switch (type) {
    case SchemaType::DEVICE:
      return Partition::VENDOR;
    case SchemaType::FRAMEWORK:
      return Partition::SYSTEM;
  }
  return Partition::UNKNOWN;
}

}  // namespace testing
}  // namespace vintf
}  // namespace android

namespace std {
void PrintTo(const android::vintf::testing::HalManifestPtr &v, ostream *os) {
  if (v == nullptr) {
    *os << "nullptr";
    return;
  }
  *os << to_string(v->type()) << " manifest";
}
}  // namespace std
