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

#include "SingleManifestTest.h"

#include <aidl/metadata.h>
#include <android-base/hex.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/apex/ApexInfo.h>
#include <android/apex/IApexService.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <dirent.h>
#include <dlfcn.h>
#include <gmock/gmock.h>
#include <hidl-util/FqInstance.h>
#include <hidl/HidlTransportUtils.h>
#include <stdio.h>
#include <vintf/constants.h>
#include <vintf/parse_string.h>

#include <algorithm>

#include "utils.h"

using ::testing::AnyOf;
using ::testing::Contains;

namespace android {
namespace vintf {
namespace testing {

using android::FqInstance;
using android::vintf::toFQNameString;

// For devices that launched <= Android O-MR1, systems/hals/implementations
// were delivered to companies which either don't start up on device boot.
bool LegacyAndExempt(const FQName &fq_name) {
  return GetBoardApiLevel() <= 27 && !IsAndroidPlatformInterface(fq_name);
}

void FailureHalMissing(const FQName &fq_name, const std::string &instance) {
  if (LegacyAndExempt(fq_name)) {
    cout << "[  WARNING ] " << fq_name.string() << "/" << instance
         << " not available but is exempted because it is legacy. It is still "
            "recommended to fix this."
         << endl;
  } else {
    ADD_FAILURE() << fq_name.string() << "/" << instance << " not available.";
  }
}

void FailureHashMissing(const FQName &fq_name) {
  if (LegacyAndExempt(fq_name)) {
    cout << "[  WARNING ] " << fq_name.string()
         << " has an empty hash but is exempted because it is legacy. It is "
            "still recommended to fix this. This is because it was compiled "
            "without being frozen in a corresponding current.txt file."
         << endl;
  } else if (base::GetProperty("ro.build.version.codename", "") != "REL") {
    cout << "[  WARNING ] " << fq_name.string()
         << " has an empty hash but is exempted because it is not a release "
            "build"
         << endl;
  } else {
    ADD_FAILURE()
        << fq_name.string()
        << " has an empty hash. This is because it was compiled "
           "without being frozen in a corresponding current.txt file.";
  }
}

static FqInstance ToFqInstance(const string &interface,
                               const string &instance) {
  FqInstance fq_interface;
  FqInstance ret;

  if (!fq_interface.setTo(interface)) {
    ADD_FAILURE() << interface << " is not a valid FQName";
    return ret;
  }
  if (!ret.setTo(fq_interface.getPackage(), fq_interface.getMajorVersion(),
                 fq_interface.getMinorVersion(), fq_interface.getInterface(),
                 instance)) {
    ADD_FAILURE() << "Cannot convert to FqInstance: " << interface << "/"
                  << instance;
  }
  return ret;
}

// Given android.foo.bar@x.y::IFoo/default, attempt to get
// android.foo.bar@x.y::IFoo/default, android.foo.bar@x.(y-1)::IFoo/default,
// ... android.foo.bar@x.0::IFoo/default until the passthrough HAL is retrieved.
static sp<IBase> GetPassthroughServiceExact(const FqInstance &fq_instance,
                                            bool expect_interface_chain_valid) {
  for (size_t minor_version = fq_instance.getMinorVersion();; --minor_version) {
    // String out instance name from fq_instance.
    FqInstance interface;
    if (!interface.setTo(fq_instance.getPackage(),
                         fq_instance.getMajorVersion(), minor_version,
                         fq_instance.getInterface())) {
      ADD_FAILURE() << fq_instance.string()
                    << " doesn't contain a valid FQName";
      return nullptr;
    }

    auto hal_service = VtsTrebleVintfTestBase::GetHidlService(
        interface.string(), fq_instance.getInstance(), Transport::PASSTHROUGH);

    if (hal_service != nullptr) {
      bool interface_chain_valid = false;
      hal_service->interfaceChain([&](const auto &chain) {
        for (const auto &intf : chain) {
          if (intf == interface.string()) {
            interface_chain_valid = true;
            return;
          }
        }
      });
      if (!interface_chain_valid && expect_interface_chain_valid) {
        ADD_FAILURE() << "Retrieved " << interface.string() << "/"
                      << fq_instance.getInstance() << " as "
                      << fq_instance.string()
                      << " but interfaceChain() doesn't contain "
                      << fq_instance.string();
        return nullptr;
      }
      cout << "Retrieved " << interface.string() << "/"
           << fq_instance.getInstance() << " as " << fq_instance.string()
           << endl;
      return hal_service;
    }

    if (minor_version == 0) {
      return nullptr;
    }
  }
  ADD_FAILURE() << "Should not reach here";
  return nullptr;
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SingleHwbinderHalTest);
GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SingleHidlTest);

// Given vendor.foo.bar@x.y::IFoo/default, also look up all declared passthrough
// HAL implementations on the device that implements this interface.
sp<IBase> SingleHidlTest::GetPassthroughService(const FqInstance &fq_instance) {
  sp<IBase> hal_service = GetPassthroughServiceExact(
      fq_instance, true /* expect_interface_chain_valid */);
  if (hal_service != nullptr) {
    return hal_service;
  }

  // For vendor extensions, hal_service may be null because we don't know
  // its interfaceChain()[1] to call getService(). However, the base interface
  // should be declared in the manifest. Attempt to find it.
  cout
      << "Can't find passthrough service " << fq_instance.string()
      << ". It might be a vendor extension. Searching all passthrough services "
         "on the device for a match."
      << endl;

  const auto &[_, manifest] = GetParam();
  auto all_declared_passthrough_instances = GetHidlInstances(manifest);
  for (const HidlInstance &other_hidl_instance :
       all_declared_passthrough_instances) {
    if (other_hidl_instance.transport() != Transport::PASSTHROUGH) {
      continue;
    }
    if (other_hidl_instance.instance_name() != fq_instance.getInstance()) {
      cout << "Skipping " << other_hidl_instance.fq_name().string() << "/"
           << other_hidl_instance.instance_name()
           << " because instance name is not " << fq_instance.getInstance();
      continue;
    }
    auto other_fq_instance = FqInstance::from(
        other_hidl_instance.fq_name(), other_hidl_instance.instance_name());
    if (!other_fq_instance) {
      cout << other_hidl_instance.fq_name().string() << "/"
           << other_hidl_instance.instance_name()
           << " is not a valid FqInstance, skipping." << endl;
      continue;
    }
    auto other_service = GetPassthroughServiceExact(
        *other_fq_instance, false /* expect_interface_chain_valid */);
    if (other_service == nullptr) {
      cout << "Cannot retrieve " << other_fq_instance->string() << ", skipping."
           << endl;
      continue;
    }
    bool match = false;
    auto other_interface_chain_ret =
        other_service->interfaceChain([&](const auto &chain) {
          for (const auto &intf : chain) {
            auto other_fq_instance_in_chain = FqInstance::from(
                std::string(intf) + "/" + other_fq_instance->getInstance());
            if (other_fq_instance_in_chain == fq_instance) {
              match = true;
              break;
            }
          }
        });
    if (!other_interface_chain_ret.isOk()) {
      cout << "Cannot call interfaceChain on " << other_fq_instance->string()
           << ", skipping." << endl;
      continue;
    }
    if (match) {
      cout << "The implementation of " << other_fq_instance->string()
           << " also implements " << fq_instance.string()
           << ", using it to check if passthrough is allowed for "
           << fq_instance.string() << endl;
      return other_service;
    }
  }
  cout << "Can't find any other passthrough service implementing "
       << fq_instance.string() << endl;
  return nullptr;
}

// returns true only if the specified apex is updated
static bool IsApexUpdated(const std::string &apex_name) {
  using namespace ::android::apex;
  auto binder =
      defaultServiceManager()->waitForService(String16("apexservice"));
  if (binder != nullptr) {
    auto apex_service = interface_cast<IApexService>(binder);
    std::vector<ApexInfo> list;
    auto status = apex_service->getActivePackages(&list);
    EXPECT_TRUE(status.isOk())
        << "Failed to getActivePackages():" << status.exceptionMessage();
    for (const ApexInfo &apex_info : list) {
      if (apex_info.moduleName == apex_name) {
        return !apex_info.isFactory;
      }
    }
  }
  return false;
}

// Tests that no HAL outside of the allowed set is specified as passthrough in
// VINTF.
// @VsrTest = VSR-3.2-014
TEST_P(SingleHidlTest, HalIsBinderized) {
  const auto &[hidl_instance, manifest] = GetParam();
  const FQName &fq_name = hidl_instance.fq_name();
  auto opt_fq_instance =
      FqInstance::from(fq_name, hidl_instance.instance_name());
  ASSERT_TRUE(opt_fq_instance);
  const FqInstance &fq_instance = *opt_fq_instance;

  EXPECT_THAT(hidl_instance.transport(),
              AnyOf(Transport::HWBINDER, Transport::PASSTHROUGH))
      << "HIDL HAL has unknown transport specified in VINTF ("
      << hidl_instance.transport() << ": " << fq_instance.string();

  if (hidl_instance.transport() == Transport::HWBINDER) {
    return;
  }

  set<FqInstance> passthrough_allowed;
  auto hal_service = GetPassthroughService(fq_instance);
  if (hal_service == nullptr) {
    cout << "Skip calling interfaceChain on " << fq_instance.string()
         << " because it can't be retrieved directly." << endl;
  } else {
    // For example, given the following interfaceChain when
    // hal_service is "android.hardware.mapper@2.0::IMapper/default":
    // ["vendor.foo.mapper@1.0::IMapper",
    //  "android.hardware.mapper@2.1::IMapper",
    //  "android.hardware.mapper@2.0::IMapper",
    //  "android.hidl.base@1.0::IBase"],
    // Allow the following:
    // ["vendor.foo.mapper@1.0::IMapper/default",
    //  "android.hardware.mapper@2.1::IMapper/default",
    //  "android.hardware.mapper@2.0::IMapper/default"]
    hal_service->interfaceChain([&](const auto &chain) {
      vector<FqInstance> fq_instances;
      std::transform(
          chain.begin(), chain.end(), std::back_inserter(fq_instances),
          [&](const auto &interface) {
            return ToFqInstance(interface, fq_instance.getInstance());
          });

      bool allowing = false;
      for (auto it = fq_instances.rbegin(); it != fq_instances.rend(); ++it) {
        if (kPassthroughHals.find(it->getPackage()) != kPassthroughHals.end()) {
          allowing = true;
        }
        if (allowing) {
          cout << it->string() << " is allowed to be passthrough" << endl;
          passthrough_allowed.insert(*it);
        }
      }
    });
  }

  EXPECT_THAT(passthrough_allowed, Contains(fq_instance))
      << "HIDL HAL can't be passthrough under Treble rules (or they "
         "can't be retrieved): "
      << fq_instance.string();
}

// Tests that all HALs specified in the VINTF are available through service
// manager.
// This tests (HAL in manifest) => (HAL is served)
// @VsrTest = VSR-3.2-014
TEST_P(SingleHidlTest, HalIsServed) {
  // Verifies that HAL is available through service manager and is served from a
  // specific set of partitions.

  const auto &[hidl_instance, manifest] = GetParam();

  Partition expected_partition = PartitionOfType(manifest->type());
  const FQName &fq_name = hidl_instance.fq_name();
  const string &instance_name = hidl_instance.instance_name();
  Transport transport = hidl_instance.transport();

  sp<IBase> hal_service;

  if (transport == Transport::PASSTHROUGH) {
    using android::hardware::details::canCastInterface;

    // Passthrough services all start with minor version 0.
    // there are only three of them listed above. They are looked
    // up based on their binary location. For instance,
    // V1_0::IFoo::getService() might correspond to looking up
    // android.hardware.foo@1.0-impl for the symbol
    // HIDL_FETCH_IFoo. For @1.1::IFoo to continue to work with
    // 1.0 clients, it must also be present in a library that is
    // called the 1.0 name. Clients can say:
    //     mFoo1_0 = V1_0::IFoo::getService();
    //     mFoo1_1 = V1_1::IFoo::castFrom(mFoo1_0);
    // This is the standard pattern for making a service work
    // for both versions (mFoo1_1 != nullptr => you have 1.1)
    // and a 1.0 client still works with the 1.1 interface.

    if (!IsAndroidPlatformInterface(fq_name)) {
      // This isn't the case for extensions of core Google interfaces.
      return;
    }

    const FQName lowest_name =
        fq_name.withVersion(fq_name.getPackageMajorVersion(), 0);
    hal_service = GetHidlService(lowest_name, instance_name, transport);
    EXPECT_TRUE(canCastInterface(hal_service.get(), fq_name.string().c_str()))
        << fq_name.string() << " is not on the device.";
  } else {
    hal_service = GetHidlService(fq_name, instance_name, transport);
  }

  if (hal_service == nullptr) {
    FailureHalMissing(fq_name, instance_name);
    return;
  }

  EXPECT_EQ(transport == Transport::HWBINDER, hal_service->isRemote())
      << "transport is " << transport << "but HAL service is "
      << (hal_service->isRemote() ? "" : "not") << " remote.";
  EXPECT_EQ(transport == Transport::PASSTHROUGH, !hal_service->isRemote())
      << "transport is " << transport << "but HAL service is "
      << (hal_service->isRemote() ? "" : "not") << " remote.";

  if (!hal_service->isRemote()) return;

  Partition partition = GetPartition(hal_service);
  if (partition == Partition::UNKNOWN) return;
  EXPECT_EQ(expected_partition, partition)
      << fq_name.string() << "/" << instance_name << " is in partition "
      << partition << " but is expected to be in " << expected_partition;
}

// Tests that all HALs which are served are specified in the VINTF
// This tests (HAL is served) => (HAL in manifest)
// @VsrTest = VSR-3.2-014
TEST_P(SingleHwbinderHalTest, ServedHwbinderHalIsInManifest) {
  const auto &[fq_instance_name, manifest] = GetParam();

  if (fq_instance_name.find(IBase::descriptor) == 0) {
    GTEST_SKIP() << "Ignore IBase: " << fq_instance_name;
  }

  auto expected_partition = PartitionOfType(manifest->type());
  std::set<std::string> manifest_hwbinder_hals =
      GetDeclaredHidlHalsOfTransport(manifest, Transport::HWBINDER);

  auto opt_fq_instance = FqInstance::from(fq_instance_name);
  ASSERT_TRUE(opt_fq_instance);
  const FqInstance &fq_instance = *opt_fq_instance;

  auto service = GetHidlService(
      toFQNameString(fq_instance.getPackage(), fq_instance.getVersion(),
                     fq_instance.getInterface()),
      fq_instance.getInstance(), Transport::HWBINDER);
  ASSERT_NE(service, nullptr);

  Partition partition = GetPartition(service);
  if (partition == Partition::UNKNOWN) {
    // Caught by SystemVendorTest.ServedHwbinderHalIsInManifest
    // if that test is run.
    GTEST_SKIP() << "Unable to determine partition. "
                    "Refer to SystemVendorTest.ServedHwbinderHalIsInManifest "
                    "or SingleHwbinderHalTest.ServedHwbinderHalIsInManifest "
                    "for the other manifest for correct result: "
                 << fq_instance_name;
  }
  if (partition != expected_partition) {
    GTEST_SKIP() << "Skipping because this test only test "
                 << expected_partition << " partition on the "
                 << manifest->type()
                 << " side of Treble boundary. "
                    "Refer to SystemVendorTest.ServedHwbinderHalIsInManifest "
                    "or SingleHwbinderHalTest.ServedHwbinderHalIsInManifest "
                    "for the other manifest for correct result: "
                 << fq_instance_name;
  }
  EXPECT_NE(manifest_hwbinder_hals.find(fq_instance_name),
            manifest_hwbinder_hals.end())
      << fq_instance_name << " is being served, but it is not in a manifest.";
}

std::string SingleHwbinderHalTest::GetTestCaseSuffix(
    const ::testing::TestParamInfo<ParamType> &info) {
  const auto &[fq_instance_name, manifest] = info.param;
  return SanitizeTestCaseName(fq_instance_name) + "_" +
         std::to_string(info.index);
}

// Tests that all HALs which are served are specified in the VINTF
// This tests (HAL is served) => (HAL in manifest) for passthrough HALs
// @VsrTest = VSR-3.2-014
TEST_P(SingleHidlTest, ServedPassthroughHalIsInManifest) {
  const auto &[hidl_instance, manifest] = GetParam();
  const FQName &fq_name = hidl_instance.fq_name();
  const string &instance_name = hidl_instance.instance_name();
  Transport transport = hidl_instance.transport();
  std::set<std::string> manifest_passthrough_hals =
      GetDeclaredHidlHalsOfTransport(manifest, Transport::PASSTHROUGH);

  if (transport != Transport::PASSTHROUGH) {
    GTEST_SKIP() << "Not passthrough: " << fq_name.string() << "/"
                 << instance_name;
  }

  // See HalIsServed. These are always retrieved through the base interface
  // and if it is not a google defined interface, it must be an extension of
  // one.
  if (!IsAndroidPlatformInterface(fq_name)) {
    GTEST_SKIP() << "Not Android Platform Interface: " << fq_name.string()
                 << "/" << instance_name;
  }

  const FQName lowest_name =
      fq_name.withVersion(fq_name.getPackageMajorVersion(), 0);
  sp<IBase> hal_service = GetHidlService(lowest_name, instance_name, transport);
  ASSERT_NE(nullptr, hal_service)
      << "Could not get service " << fq_name.string() << "/" << instance_name;

  Return<void> ret = hal_service->interfaceChain(
      [&manifest_passthrough_hals, &instance_name](const auto &interfaces) {
        for (const auto &interface : interfaces) {
          if (std::string(interface) == IBase::descriptor) continue;

          const std::string instance =
              std::string(interface) + "/" + instance_name;
          EXPECT_NE(manifest_passthrough_hals.find(instance),
                    manifest_passthrough_hals.end())
              << "Instance missing from manifest: " << instance;
        }
      });
  EXPECT_TRUE(ret.isOk());
}

// Tests that HAL interfaces are officially released.
// @VsrTest = VSR-3.2-014
TEST_P(SingleHidlTest, InterfaceIsReleased) {
  const auto &[hidl_instance, manifest] = GetParam();

  const FQName &fq_name = hidl_instance.fq_name();
  const string &instance_name = hidl_instance.instance_name();
  Transport transport = hidl_instance.transport();

  // See HalIsServed. These are always retrieved through the base interface
  // and if it is not a google defined interface, it must be an extension of
  // one.
  if (transport == Transport::PASSTHROUGH &&
      (!IsAndroidPlatformInterface(fq_name) ||
       fq_name.getPackageMinorVersion() != 0)) {
    return;
  }

  sp<IBase> hal_service = GetHidlService(fq_name, instance_name, transport);

  if (hal_service == nullptr) {
    FailureHalMissing(fq_name, instance_name);
    return;
  }

  vector<string> iface_chain = GetInterfaceChain(hal_service);

  vector<string> hash_chain{};
  hal_service->getHashChain([&hash_chain](
                                const hidl_vec<HashCharArray> &chain) {
    for (const HashCharArray &hash : chain) {
      hash_chain.push_back(android::base::HexString(hash.data(), hash.size()));
    }
  });

  ASSERT_EQ(iface_chain.size(), hash_chain.size());
  for (size_t i = 0; i < iface_chain.size(); ++i) {
    FQName fq_iface_name;
    if (!FQName::parse(iface_chain[i], &fq_iface_name)) {
      ADD_FAILURE() << "Could not parse iface name " << iface_chain[i]
                    << " from interface chain of " << fq_name.string();
      return;
    }
    string hash = hash_chain[i];
    if (hash == android::base::HexString(Hash::kEmptyHash.data(),
                                         Hash::kEmptyHash.size())) {
      FailureHashMissing(fq_iface_name);
    } else if (IsAndroidPlatformInterface(fq_iface_name)) {
      set<string> released_hashes = ReleasedHashes(fq_iface_name);
      EXPECT_NE(released_hashes.find(hash), released_hashes.end())
          << "Hash not found. This interface was not released." << endl
          << "Interface name: " << fq_iface_name.string() << endl
          << "Hash: " << hash << endl;
    }
  }
}

static std::optional<AidlInterfaceMetadata> metadataForInterface(
    const std::string &name) {
  for (const auto &module : AidlInterfaceMetadata::all()) {
    if (std::find(module.types.begin(), module.types.end(), name) !=
        module.types.end()) {
      return module;
    }
  }
  return std::nullopt;
}

// TODO(b/150155678): using standard code to do this
static std::string getInterfaceHash(const sp<IBinder> &binder) {
  Parcel data;
  Parcel reply;
  data.writeInterfaceToken(binder->getInterfaceDescriptor());
  status_t err =
      binder->transact(IBinder::LAST_CALL_TRANSACTION - 1, data, &reply, 0);
  if (err == UNKNOWN_TRANSACTION) {
    return "";
  }
  EXPECT_EQ(OK, err);
  binder::Status status;
  EXPECT_EQ(OK, status.readFromParcel(reply));
  EXPECT_TRUE(status.isOk()) << status.toString8().c_str();
  std::string str;
  EXPECT_EQ(OK, reply.readUtf8FromUtf16(&str));
  return str;
}

// TODO(b/150155678): using standard code to do this
static int32_t getInterfaceVersion(const sp<IBinder> &binder) {
  Parcel data;
  Parcel reply;
  const auto &descriptor = binder->getInterfaceDescriptor();
  data.writeInterfaceToken(descriptor);
  status_t err = binder->transact(IBinder::LAST_CALL_TRANSACTION, data, &reply);
  // On upgrading devices, the HAL may not implement this transaction. libvintf
  // treats missing <version> as version 1, so we do the same here.
  if (err == UNKNOWN_TRANSACTION) {
    std::cout << "INFO: " << descriptor
              << " does not have an interface version, using default value "
              << android::vintf::kDefaultAidlMinorVersion << std::endl;
    return android::vintf::kDefaultAidlMinorVersion;
  }
  EXPECT_EQ(OK, err);
  binder::Status status;
  EXPECT_EQ(OK, status.readFromParcel(reply));
  EXPECT_TRUE(status.isOk()) << status.toString8().c_str();
  auto version = reply.readInt32();
  return version;
}

static bool CheckAidlVersionMatchesDeclared(sp<IBinder> binder,
                                            const std::string &name,
                                            uint64_t declared_version,
                                            bool allow_upgrade) {
  const int32_t actual_version = getInterfaceVersion(binder);
  if (actual_version < 1) {
    ADD_FAILURE() << "For " << name << ", version should be >= 1 but it is "
                  << actual_version << ".";
    return false;
  }

  if (declared_version == actual_version) {
    std::cout << "For " << name << ", version " << actual_version
              << " matches declared value." << std::endl;
    return true;
  }
  if (allow_upgrade && actual_version > declared_version) {
    std::cout << "For " << name << ", upgraded version " << actual_version
              << " is okay. (declared value = " << declared_version << ".)"
              << std::endl;
    return true;
  }

  // Android R VINTF did not support AIDL version in the manifest.
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  if (shipping_fcm_version != Level::UNSPECIFIED &&
      shipping_fcm_version <= Level::R) {
    std::cout << "For " << name << ", manifest declares version "
              << declared_version << ", but the actual version is "
              << actual_version << ". Exempted for shipping FCM version "
              << shipping_fcm_version << ". (b/178458001, b/199190514)"
              << std::endl;
    return true;
  }

  ADD_FAILURE()
      << "For " << name << ", manifest (targeting FCM:" << shipping_fcm_version
      << ") declares version " << declared_version
      << ", but the actual version is " << actual_version << std::endl
      << "Either the VINTF manifest <hal> entry needs to be updated with a "
         "version tag for the actual version, or the implementation should be "
         "changed to use the declared version";
  return false;
}

// An AIDL HAL with VINTF stability can only be registered if it is in the
// manifest. However, we still must manually check that every declared HAL is
// actually present on the device.
// @VsrTest = VSR-3.2-014
TEST_P(SingleAidlTest, HalIsServed) {
  const auto &[aidl_instance, manifest] = GetParam();
  const string &package = aidl_instance.package();
  uint64_t version = aidl_instance.version();
  const string &interface = aidl_instance.interface();
  const string &instance = aidl_instance.instance();
  const optional<string> &updatable_via_apex =
      aidl_instance.updatable_via_apex();

  const std::string type = package + "." + interface;
  const std::string name = type + "/" + instance;

  sp<IBinder> binder = GetAidlService(name);

  ASSERT_NE(binder, nullptr) << "Failed to get " << name;

  // allow upgrade if updatable HAL's declared APEX is actually updated.
  const bool allow_upgrade = updatable_via_apex.has_value() &&
                             IsApexUpdated(updatable_via_apex.value());
  const bool reliable_version =
      CheckAidlVersionMatchesDeclared(binder, name, version, allow_upgrade);

  const std::string hash = getInterfaceHash(binder);
  const std::optional<AidlInterfaceMetadata> metadata =
      metadataForInterface(type);

  const bool is_aosp = base::StartsWith(package, "android.");
  ASSERT_TRUE(!is_aosp || metadata)
      << "AOSP interface must have metadata: " << package;

  const bool is_release =
      base::GetProperty("ro.build.version.codename", "") == "REL";

  const bool is_existing =
      metadata ? std::find(metadata->versions.begin(), metadata->versions.end(),
                           version) != metadata->versions.end()
               : false;

  const std::vector<std::string> hashes =
      metadata ? metadata->hashes : std::vector<std::string>();
  const bool found_hash =
      std::find(hashes.begin(), hashes.end(), hash) != hashes.end();

  if (is_aosp) {
    if (!found_hash) {
      if (is_release || (reliable_version && is_existing)) {
        ADD_FAILURE() << "Interface " << name << " has an unrecognized hash: '"
                      << hash << "'. The following hashes are known:\n"
                      << base::Join(hashes, '\n')
                      << "\nHAL interfaces must be released and unchanged.";
      } else {
        std::cout << "INFO: using unfrozen hash '" << hash << "' for " << type
                  << ". This will become an error upon release." << std::endl;
      }
    }
  } else {
    // is extension
    //
    // we only require that these are frozen, but we cannot check them for
    // accuracy
    if (hash.empty() || hash == "notfrozen") {
      if (is_release) {
        ADD_FAILURE() << "Interface " << name
                      << " is used but not frozen (cannot find hash for it).";
      } else {
        std::cout << "INFO: missing hash for " << type
                  << ". This will become an error upon release." << std::endl;
      }
    }
  }
}

// We don't want to add more same process HALs in Android. We have some 3rd
// party ones such as openGL and Vulkan. In the future, we should verify those
// here as well. However we want to strictly limit other HALs because a
// same-process HAL confuses the client and server SELinux permissions. In
// Android, we prefer upstream Linux support, then secondary to that, we prefer
// having hardware use in a process isolated from the Android framework.
struct NativePackage {
  std::string name;
  int32_t majorVersion;
};

ostream &operator<<(ostream &os, const NativePackage &pkg) {
  os << pkg.name << "-v" << pkg.majorVersion;
  return os;
}

static const std::array<NativePackage, 1> kKnownNativePackages = {
    NativePackage{"mapper", 5},
};
static const std::vector<std::string> kNativeHalPaths = {
    "/vendor/lib/hw/",
    "/vendor/lib64/hw/",
};

static std::optional<NativePackage> findKnownNativePackage(
    std::string_view package) {
  for (const auto &it : kKnownNativePackages) {
    if (it.name == package) {
      return it;
    }
  }
  return std::nullopt;
}

// using device manifest test for access to GetNativeInstances
TEST(NativeDeclaredTest, NativeDeclaredIfExists) {
  std::set<std::string> names;  // e.g. 'mapper.instance_name'

  // read all the native HALs installed on disk
  bool found_a_dir = false;
  for (const std::string &dir : kNativeHalPaths) {
    DIR *dp = opendir(dir.c_str());
    if (dp == nullptr) continue;
    found_a_dir = true;

    dirent *entry;
    while ((entry = readdir(dp))) {
      std::string name = entry->d_name;
      size_t dot_one = name.find('.');
      if (dot_one == std::string::npos) continue;
      size_t dot_end = name.rfind('.');
      if (dot_end == std::string::npos || dot_one == dot_end) continue;
      ASSERT_LT(dot_one, dot_end);
      if (name.substr(dot_end) != ".so") continue;

      std::string package = name.substr(0, dot_one);
      if (!findKnownNativePackage(package).has_value()) continue;

      names.insert(name.substr(0, dot_end));
    }
    closedir(dp);
  }
  ASSERT_TRUE(found_a_dir);

  // ignore HALs which are declared, because they'll be checked in
  // SingleNativeTest ExistsIfDeclared
  for (const auto &hal : VtsTrebleVintfTestBase::GetNativeInstances(
           VintfObject::GetDeviceHalManifest())) {
    std::string this_name = hal.package() + "." + hal.instance();
    names.erase(this_name);
  }

  for (const std::string &name : names) {
    ADD_FAILURE() << name
                  << " is installed on the device, but it's not declared in "
                     "the VINTF manifest";
  }
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SingleNativeTest);
TEST_P(SingleNativeTest, ExistsIfDeclared) {
  const auto &[native_instance, manifest] = GetParam();

  // Currently only support rev'ing the major version
  EXPECT_EQ(native_instance.minor_version(), 0);

  auto knownPackageInfo = findKnownNativePackage(native_instance.package());
  ASSERT_TRUE(knownPackageInfo.has_value())
      << "Unsupported package: " << native_instance.package()
      << " must be one of: " << base::Join(kKnownNativePackages, ", ");
  EXPECT_EQ(native_instance.major_version(), knownPackageInfo->majorVersion);
  EXPECT_TRUE(native_instance.interface() == "I" ||
              native_instance.interface() == "")
      << "Interface must be 'I' or '' for native HAL: "
      << native_instance.interface();

  void *so = openDeclaredPassthroughHal(
      String16(native_instance.package().c_str()),
      String16(native_instance.instance().c_str()), RTLD_LAZY | RTLD_LOCAL);
  ASSERT_NE(so, nullptr) << "Failed to load " << native_instance << dlerror();

  std::string upperPackage = native_instance.package();
  std::transform(upperPackage.begin(), upperPackage.end(), upperPackage.begin(),
                 ::toupper);
  std::string versionSymbol = "ANDROID_HAL_" + upperPackage + "_VERSION";
  int32_t *halVersion = (int32_t *)dlsym(so, versionSymbol.c_str());
  ASSERT_NE(halVersion, nullptr) << "Failed to find symbol " << versionSymbol;
  EXPECT_EQ(native_instance.major_version(), *halVersion);

  dlclose(so);
}

}  // namespace testing
}  // namespace vintf
}  // namespace android
