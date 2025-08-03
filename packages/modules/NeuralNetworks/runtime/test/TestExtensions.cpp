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

#include <HalInterfaces.h>
#include <SampleDriver.h>
#include <gtest/gtest.h>

#include <string>
#include <vector>

#include "AppInfoFetcher.h"
#include "HalUtils.h"
#include "Manager.h"
#include "NeuralNetworks.h"
#include "NeuralNetworksExtensions.h"
#include "TypeManager.h"

namespace {

using DeviceManager = ::android::nn::DeviceManager;
using SampleDriver = ::android::nn::sample_driver::SampleDriver;
using TypeManager = ::android::nn::TypeManager;

namespace hardware = ::android::hardware;
namespace V1_0 = ::android::hardware::neuralnetworks::V1_0;
namespace V1_3 = ::android::hardware::neuralnetworks::V1_3;

const char* kTestDriverName = "extensions-test-driver";
const char* kTestExtension1 = "vendor.test.one";
const char* kTestExtension2 = "vendor.test.two";
const char* kTestExtension3 = "vendor.test.three";

class TestDriver : public SampleDriver {
   public:
    TestDriver() : SampleDriver(kTestDriverName) {}
    ~TestDriver() override {}

    hardware::Return<void> getSupportedExtensions(getSupportedExtensions_cb cb) override {
        cb(V1_0::ErrorStatus::NONE, {
                                            {.name = kTestExtension1},
                                            {.name = kTestExtension2},
                                            {.name = kTestExtension3},
                                    });
        return hardware::Void();
    }

    hardware::Return<void> getCapabilities_1_3(getCapabilities_1_3_cb cb) override {
        cb(V1_3::ErrorStatus::NONE, ::android::nn::makeCapabilities(1.0));
        return hardware::Void();
    }

    hardware::Return<void> getSupportedOperations_1_3(const V1_3::Model&,
                                                      getSupportedOperations_1_3_cb) override {
        CHECK(false) << "not implemented";
        return hardware::Void();
    }
};

class ExtensionsTest : public ::testing::Test {
   protected:
    virtual void SetUp() {
        // This is needed before we have the CPU fallback path being treated as a Device.
        if (DeviceManager::get()->getUseCpuOnly()) {
            GTEST_SKIP();
        }

        DeviceManager::get()->forTest_registerDevice(
                android::nn::makeSharedDevice(kTestDriverName, new TestDriver()));
        // Discover extensions provided by registered devices.
        TypeManager::get()->forTest_reset();
        mDevice = getDeviceByName(kTestDriverName);
        ASSERT_NE(mDevice, nullptr);
    }

    virtual void TearDown() {
        DeviceManager::get()->forTest_reInitializeDeviceList();
        TypeManager::get()->forTest_reset();
    }

    ANeuralNetworksDevice* getDeviceByName(const std::string& name) {
        ANeuralNetworksDevice* result = nullptr;
        uint32_t numDevices = 0;
        EXPECT_EQ(ANeuralNetworks_getDeviceCount(&numDevices), ANEURALNETWORKS_NO_ERROR);
        EXPECT_GE(numDevices, 1u);
        for (uint32_t i = 0; i < numDevices; i++) {
            ANeuralNetworksDevice* device = nullptr;
            EXPECT_EQ(ANeuralNetworks_getDevice(i, &device), ANEURALNETWORKS_NO_ERROR);
            const char* buffer = nullptr;
            EXPECT_EQ(ANeuralNetworksDevice_getName(device, &buffer), ANEURALNETWORKS_NO_ERROR);
            if (name.compare(buffer) == 0) {
                EXPECT_EQ(result, nullptr) << "multiple devices named " << name;
                result = device;
            }
        }
        return result;
    }

    bool testDriverSupportsExtension(const char* extensionName) {
        bool result;
        EXPECT_EQ(ANeuralNetworksDevice_getExtensionSupport(mDevice, extensionName, &result),
                  ANEURALNETWORKS_NO_ERROR);
        return result;
    }

   private:
    ANeuralNetworksDevice* mDevice;
};

TEST_F(ExtensionsTest, DeviceReportsSupportedExtensions) {
    EXPECT_TRUE(testDriverSupportsExtension(kTestExtension1));
    EXPECT_FALSE(testDriverSupportsExtension("vendor.test.unknown"));
    EXPECT_FALSE(testDriverSupportsExtension("asdfasdfas"));
    EXPECT_TRUE(testDriverSupportsExtension(kTestExtension2));
    EXPECT_TRUE(testDriverSupportsExtension(kTestExtension3));
}

TEST_F(ExtensionsTest, TestAllowedNativeBinaries) {
    std::vector<std::string> allowlist = {"/data/foo",    "/vendor/foo",         "/odm/foo",
                                          "/product/foo", "/system/allowlisted", "/foobar/foo"};

    auto native_info = [&](const std::string& binaryPath) -> android::nn::AppInfoFetcher::AppInfo {
        return {.binaryPath = binaryPath,
                .appPackageName = "",
                .appIsSystemApp = false,
                .appIsOnVendorImage = false,
                .appIsOnProductImage = false};
    };

    // No binary info
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed(native_info(""),
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    // Non-approved top-level dir
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed(native_info("/foobar/foo"),
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    // Allowlisted /data binary
    EXPECT_TRUE(TypeManager::isExtensionsUseAllowed(native_info("/data/foo"),
                                                    /* useOnProductImageEnabled = */ false,
                                                    allowlist));
    // Allowlisted /vendor binary
    EXPECT_TRUE(TypeManager::isExtensionsUseAllowed(native_info("/vendor/foo"),
                                                    /* useOnProductImageEnabled = */ false,
                                                    allowlist));
    // Allowlisted /odm binary
    EXPECT_TRUE(TypeManager::isExtensionsUseAllowed(native_info("/odm/foo"),
                                                    /* useOnProductImageEnabled = */ false,
                                                    allowlist));
    // Non-allowlisted /system binary
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed(native_info("/system/foo"),
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    // allowlisted /system binary (can't be allowlisted)
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed(native_info("/system/allowlisted"),
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    // Allowlisted /product binary, product disabled
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed(native_info("/product/foo"),
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    // Allowlisted /product binary, product enabled
    EXPECT_TRUE(TypeManager::isExtensionsUseAllowed(native_info("/product/foo"),
                                                    /* useOnProductImageEnabled = */ true,
                                                    allowlist));

    // Allowlist for vendor/data partiion is not present on Android S.
    // Before S, checks below will fail. On S and later they will succeed.
#ifdef __ANDROID__
    bool disableProductAllowlist = android_get_device_api_level() >= __ANDROID_API_S__;
#else   // __ANDROID__
    bool disableProductAllowlist = true;
#endif  // __ANDROID__

    // Non-allowlisted /product binary, product enabled
    EXPECT_EQ(TypeManager::isExtensionsUseAllowed(native_info("/product/foo_not_allowlisted"),
                                                  /* useOnProductImageEnabled = */ true, allowlist),
              disableProductAllowlist);
    // Non-allowlisted /odm binary
    EXPECT_EQ(
            TypeManager::isExtensionsUseAllowed(native_info("/odm/foo_not_allowlisted"),
                                                /* useOnProductImageEnabled = */ false, allowlist),
            disableProductAllowlist);
    // Non-allowlisted /vendor binary
    EXPECT_EQ(
            TypeManager::isExtensionsUseAllowed(native_info("/vendor/foo_not_allowlisted"),
                                                /* useOnProductImageEnabled = */ false, allowlist),
            disableProductAllowlist);
    // Non-allowlisted /data binary
    EXPECT_EQ(
            TypeManager::isExtensionsUseAllowed(native_info("/data/foo_not_allowlisted"),
                                                /* useOnProductImageEnabled = */ false, allowlist),
            disableProductAllowlist);
}

TEST_F(ExtensionsTest, TestAllowedApps) {
    std::string app_process32 = "/system/bin/app_process32";
    std::string app_process64 = "/system/bin/app_process64";
    std::string other_binary = "/system/bin/foo";

    std::string package = "com.foo";
    std::string package_non_allowlisted = "com.foo2";

    std::vector<std::string> allowlist = {"com.foo"};

    // Allowlist for vendor/data partiion is not present on Android S.
    // Before S, checks below will fail. On S and later they will succeed.
#ifdef __ANDROID__
    bool disableProductAllowlist = android_get_device_api_level() >= __ANDROID_API_S__;
#else   // __ANDROID__
    bool disableProductAllowlist = true;
#endif  // __ANDROID__

    auto test_app_process = [&](const std::string& binary) {
        // /data app
        EXPECT_TRUE(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                         .appPackageName = package,
                                                         .appIsSystemApp = false,
                                                         .appIsOnVendorImage = false,
                                                         .appIsOnProductImage = false},
                                                        /* useOnProductImageEnabled = */ false,
                                                        allowlist));

        // /system app
        EXPECT_FALSE(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                          .appPackageName = package,
                                                          .appIsSystemApp = true,
                                                          .appIsOnVendorImage = false,
                                                          .appIsOnProductImage = false},
                                                         /* useOnProductImageEnabled = */ false,
                                                         allowlist));

        // /vendor || /odm app
        EXPECT_TRUE(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                         .appPackageName = package,
                                                         .appIsSystemApp = true,
                                                         .appIsOnVendorImage = true,
                                                         .appIsOnProductImage = false},
                                                        /* useOnProductImageEnabled = */ false,
                                                        allowlist));

        // /product app, disabled
        EXPECT_FALSE(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                          .appPackageName = package,
                                                          .appIsSystemApp = true,
                                                          .appIsOnVendorImage = false,
                                                          .appIsOnProductImage = true},
                                                         /* useOnProductImageEnabled = */ false,
                                                         allowlist));

        // /product app, enabled
        EXPECT_TRUE(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                         .appPackageName = package,
                                                         .appIsSystemApp = true,
                                                         .appIsOnVendorImage = false,
                                                         .appIsOnProductImage = true},
                                                        /* useOnProductImageEnabled = */ true,
                                                        allowlist));

        // /product app, enabled, package name not on allowlist
        EXPECT_EQ(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                       .appPackageName = package_non_allowlisted,
                                                       .appIsSystemApp = true,
                                                       .appIsOnVendorImage = false,
                                                       .appIsOnProductImage = true},
                                                      /* useOnProductImageEnabled = */ true,
                                                      allowlist),
                  disableProductAllowlist);

        // /data app, package name not on allowlist
        EXPECT_EQ(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                       .appPackageName = package_non_allowlisted,
                                                       .appIsSystemApp = false,
                                                       .appIsOnVendorImage = false,
                                                       .appIsOnProductImage = false},
                                                      /* useOnProductImageEnabled = */ false,
                                                      allowlist),
                  disableProductAllowlist);

        // /vendor || /odm app, package name not on allowlist
        EXPECT_EQ(TypeManager::isExtensionsUseAllowed({.binaryPath = binary,
                                                       .appPackageName = package_non_allowlisted,
                                                       .appIsSystemApp = true,
                                                       .appIsOnVendorImage = true,
                                                       .appIsOnProductImage = false},
                                                      /* useOnProductImageEnabled = */ false,
                                                      allowlist),
                  disableProductAllowlist);
    };
    test_app_process(app_process64);
    test_app_process(app_process32);

    // Test all positive cases fail if binary is not app_process32|64
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed({.binaryPath = other_binary,
                                                      .appPackageName = package,
                                                      .appIsSystemApp = false,
                                                      .appIsOnVendorImage = false,
                                                      .appIsOnProductImage = false},
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));
    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed({.binaryPath = other_binary,
                                                      .appPackageName = package,
                                                      .appIsSystemApp = true,
                                                      .appIsOnVendorImage = true,
                                                      .appIsOnProductImage = false},
                                                     /* useOnProductImageEnabled = */ false,
                                                     allowlist));

    EXPECT_FALSE(TypeManager::isExtensionsUseAllowed({.binaryPath = other_binary,
                                                      .appPackageName = package,
                                                      .appIsSystemApp = true,
                                                      .appIsOnVendorImage = false,
                                                      .appIsOnProductImage = true},
                                                     /* useOnProductImageEnabled = */ true,
                                                     allowlist));
}

}  // namespace
