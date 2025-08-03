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

#ifndef VTS_TREBLE_VINTF_TEST_BASE_H_
#define VTS_TREBLE_VINTF_TEST_BASE_H_

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <binder/IBinder.h>
#include <gtest/gtest.h>
#include <vintf/VintfObject.h>

#include <string>
#include <vector>

#include "utils.h"

namespace android {
namespace vintf {
namespace testing {

using android::sp;
using android::hidl::base::V1_0::IBase;
using android::hidl::manager::V1_0::IServiceManager;

// Base class for many test suites. Provides some utility functions.
class VtsTrebleVintfTestBase : public ::testing::Test {
 public:
  virtual ~VtsTrebleVintfTestBase() {}

  // Applies given function to each HAL instance in VINTF.
  static std::vector<AidlInstance> GetAidlInstances(const HalManifestPtr &);
  static std::vector<HidlInstance> GetHidlInstances(const HalManifestPtr &);
  static std::vector<NativeInstance> GetNativeInstances(const HalManifestPtr &);

  // Retrieves an existing HAL service.
  static sp<IBase> GetHidlService(const string &fq_name,
                                  const string &instance_name, Transport,
                                  bool log = true);
  static sp<IBase> GetHidlService(const FQName &fq_name,
                                  const string &instance_name, Transport,
                                  bool log = true);
  static sp<IBinder> GetAidlService(const std::string &name);

  static vector<string> GetInstanceNames(const sp<IServiceManager> &manager,
                                         const FQName &fq_name);

  static vector<string> GetInterfaceChain(const sp<IBase> &service);

  static set<string> GetDeclaredHidlHalsOfTransport(HalManifestPtr manifest,
                                                Transport transport);

  Partition GetPartition(sp<IBase> hal_service);

  static sp<IServiceManager> default_manager();

  // List HALs registered through hwservicemanager. Return the list of FQ
  // instance names.
  static std::vector<std::string> ListRegisteredHwbinderHals();
};

}  // namespace testing
}  // namespace vintf
}  // namespace android

#endif  // VTS_TREBLE_VINTF_TEST_BASE_H_
