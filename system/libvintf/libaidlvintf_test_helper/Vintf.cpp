/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <aidl/Vintf.h>

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <gtest/gtest.h>
#include <vintf/VintfObject.h>

namespace android {

static std::vector<std::string> gUnimplementedInterfaces;

// b/290539746. getAidlHalInstanceNames is usually used for parameters
// to create parameterized tests, so if it returns an empty list, then
// oftentimes the test will report no test results. This is confusing
// and it appears like a test error.
//
// Due to translation units defining other tests that will be instantiated
// in another order, we can't instantiate a test suite based on the set
// of unimplemented interfaces, so we can only have one test which shows
// the result.
TEST(AidlTestHelper, CheckNoUnimplementedInterfaces) {
    if (gUnimplementedInterfaces.empty()) {
        // all interfaces are implemented, so no error
        return;
    }

    GTEST_SKIP()
        << "These interfaces are unimplemented on this device, so other tests may be skipped: "
        << android::base::Join(gUnimplementedInterfaces, ", ");
}

std::vector<std::string> getAidlHalInstanceNames(const std::string& descriptor) {
    size_t lastDot = descriptor.rfind('.');
    CHECK(lastDot != std::string::npos) << "Invalid descriptor: " << descriptor;
    const std::string package = descriptor.substr(0, lastDot);
    const std::string iface = descriptor.substr(lastDot + 1);

    std::vector<std::string> ret;

    auto deviceManifest = vintf::VintfObject::GetDeviceHalManifest();
    for (const std::string& instance : deviceManifest->getAidlInstances(package, iface)) {
        ret.push_back(descriptor + "/" + instance);
    }

    auto frameworkManifest = vintf::VintfObject::GetFrameworkHalManifest();
    for (const std::string& instance : frameworkManifest->getAidlInstances(package, iface)) {
        ret.push_back(descriptor + "/" + instance);
    }

    if (ret.size() == 0) {
        std::cerr << "WARNING: There are no instances of AIDL service '" << descriptor
                  << "' declared on this device." << std::endl;

        gUnimplementedInterfaces.push_back(descriptor);
    }

    return ret;
}

std::vector<std::string> getAidlHalInstanceNames(const String16& descriptor) {
    return getAidlHalInstanceNames(String8(descriptor).c_str());
}

}  // namespace android
