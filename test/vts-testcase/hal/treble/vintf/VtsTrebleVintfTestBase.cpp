/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "VtsTrebleVintfTestBase.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <binder/IServiceManager.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <hidl-hash/Hash.h>
#include <hidl-util/FQName.h>
#include <hidl-util/FqInstance.h>
#include <hidl/HidlTransportUtils.h>
#include <hidl/ServiceManagement.h>
#include <procpartition/procpartition.h>
#include <vintf/HalManifest.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

#include <chrono>
#include <condition_variable>
#include <functional>
#include <future>
#include <iostream>
#include <map>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "SingleManifestTest.h"
#include "utils.h"

namespace android {
namespace vintf {
namespace testing {

using android::FqInstance;
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
using android::vintf::Transport;
using android::vintf::Version;
using android::vintf::VintfObject;
using android::vintf::operator<<;
using android::vintf::to_string;
using android::vintf::toFQNameString;

using std::cout;
using std::endl;
using std::map;
using std::set;
using std::string;
using std::vector;

using ::testing::AnyOf;
using ::testing::Eq;

sp<IServiceManager> VtsTrebleVintfTestBase::default_manager() {
  static auto default_manager = ::android::hardware::defaultServiceManager();
  if (default_manager == nullptr) {
    ADD_FAILURE() << "Failed to get default service manager.";
  }
  return default_manager;
}

std::vector<HidlInstance> VtsTrebleVintfTestBase::GetHidlInstances(
    const HalManifestPtr &manifest) {
  std::vector<HidlInstance> ret;
  manifest->forEachInstance([manifest, &ret](const auto &manifest_instance) {
    if (manifest_instance.format() == HalFormat::HIDL) {
      ret.emplace_back(manifest_instance);
    }
    return true;  // continue to next instance
  });
  return ret;
}

std::vector<AidlInstance> VtsTrebleVintfTestBase::GetAidlInstances(
    const HalManifestPtr &manifest) {
  std::vector<AidlInstance> ret;
  manifest->forEachInstance([manifest, &ret](const auto &manifest_instance) {
    if (manifest_instance.format() == HalFormat::AIDL) {
      ret.emplace_back(manifest_instance);
    }
    return true;  // continue to next instance
  });
  return ret;
}

std::vector<NativeInstance> VtsTrebleVintfTestBase::GetNativeInstances(
    const HalManifestPtr &manifest) {
  std::vector<NativeInstance> ret;
  manifest->forEachInstance([manifest, &ret](const auto &manifest_instance) {
    if (manifest_instance.format() == HalFormat::NATIVE) {
      ret.emplace_back(manifest_instance);
    }
    return true;  // continue to next instance
  });
  return ret;
}

sp<IBase> VtsTrebleVintfTestBase::GetHidlService(const FQName &fq_name,
                                                 const string &instance_name,
                                                 Transport transport,
                                                 bool log) {
  return GetHidlService(fq_name.string(), instance_name, transport, log);
}

sp<IBase> VtsTrebleVintfTestBase::GetHidlService(const string &fq_name,
                                                 const string &instance_name,
                                                 Transport transport,
                                                 bool log) {
  using android::hardware::details::getRawServiceInternal;

  if (log) {
    cout << "Getting: " << fq_name << "/" << instance_name << endl;
  }

  // getService blocks until a service is available. In 100% of other cases
  // where getService is used, it should be called directly. However, this test
  // enforces that various services are actually available when they are
  // declared, it must make a couple of precautions in case the service isn't
  // actually available so that the proper failure can be reported.

  auto task = std::packaged_task<sp<IBase>()>([fq_name, instance_name]() {
    return getRawServiceInternal(fq_name, instance_name, true /* retry */,
                                 false /* getStub */);
  });
  int timeout_multiplier = base::GetIntProperty("ro.hw_timeout_multiplier", 1);
  auto max_time = timeout_multiplier * std::chrono::seconds(1);

  std::future<sp<IBase>> future = task.get_future();
  std::thread(std::move(task)).detach();
  auto status = future.wait_for(max_time);

  if (status != std::future_status::ready) return nullptr;

  sp<IBase> base = future.get();
  if (base == nullptr) return nullptr;

  bool wantRemote = transport == Transport::HWBINDER;
  if (base->isRemote() != wantRemote) return nullptr;

  return base;
}

sp<IBinder> VtsTrebleVintfTestBase::GetAidlService(const string &name) {
  auto task = std::packaged_task<sp<IBinder>()>([name]() {
    return defaultServiceManager()->waitForService(String16(name.c_str()));
  });

  int timeout_multiplier = base::GetIntProperty("ro.hw_timeout_multiplier", 1);
  auto max_time = timeout_multiplier * std::chrono::seconds(1);
  auto future = task.get_future();
  std::thread(std::move(task)).detach();
  auto status = future.wait_for(max_time);

  return status == std::future_status::ready ? future.get() : nullptr;
}

vector<string> VtsTrebleVintfTestBase::GetInstanceNames(
    const sp<IServiceManager> &manager, const FQName &fq_name) {
  vector<string> ret;
  auto status =
      manager->listByInterface(fq_name.string(), [&](const auto &out) {
        for (const auto &e : out) ret.push_back(e);
      });
  EXPECT_TRUE(status.isOk()) << status.description();
  return ret;
}

vector<string> VtsTrebleVintfTestBase::GetInterfaceChain(
    const sp<IBase> &service) {
  vector<string> iface_chain{};
  service->interfaceChain([&iface_chain](const hidl_vec<hidl_string> &chain) {
    for (const auto &iface_name : chain) {
      iface_chain.push_back(iface_name);
    }
  });
  return iface_chain;
}

Partition VtsTrebleVintfTestBase::GetPartition(sp<IBase> hal_service) {
  Partition partition = Partition::UNKNOWN;
  auto ret = hal_service->getDebugInfo(
      [&](const auto &info) { partition = PartitionOfProcess(info.pid); });
  EXPECT_TRUE(ret.isOk());
  return partition;
}

set<string> VtsTrebleVintfTestBase::GetDeclaredHidlHalsOfTransport(
    HalManifestPtr manifest, Transport transport) {
  EXPECT_THAT(transport,
              AnyOf(Eq(Transport::HWBINDER), Eq(Transport::PASSTHROUGH)))
      << "Unrecognized transport of HIDL: " << transport;
  std::set<std::string> ret;
  for (const auto &hidl_instance : GetHidlInstances(manifest)) {
    if (hidl_instance.transport() != transport) {
      continue;  // ignore
    }

    // 1.n in manifest => 1.0, 1.1, ... 1.n are all served (if they exist)
    FQName fq = hidl_instance.fq_name();
    while (true) {
      ret.insert(fq.string() + "/" + hidl_instance.instance_name());
      if (fq.getPackageMinorVersion() <= 0) break;
      fq = fq.downRev();
    }
  }
  return ret;
}

std::vector<std::string> VtsTrebleVintfTestBase::ListRegisteredHwbinderHals() {
  std::vector<std::string> return_value;
  EXPECT_NE(default_manager(), nullptr);
  if (default_manager() == nullptr) return {};
  Return<void> ret = default_manager()->list([&](const auto &list) {
    return_value.reserve(list.size());
    for (const auto &s : list) return_value.push_back(s);
  });
  EXPECT_TRUE(ret.isOk());
  return return_value;
}

}  // namespace testing
}  // namespace vintf
}  // namespace android
