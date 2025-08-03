/*
 * Copyright 2021 The Android Open Source Project
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

#include "OveruseConfigurationTestUtils.h"
#include "OveruseConfigurationXmlHelper.h"

#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <android-base/file.h>
#include <android-base/result.h>
#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration;

namespace {

constexpr const char* kTestDataDir = "/tests/data/";

constexpr const char* kValidSystemConfiguration = "valid_overuse_system_configuration.xml";
constexpr const char* kValidVendorConfiguration = "valid_overuse_vendor_configuration.xml";
constexpr const char* kValidThirdPartyConfiguration = "valid_overuse_third_party_configuration.xml";

const std::vector<const char*> kInvalidOveruseConfigurations =
        {"duplicate_component_io_thresholds_overuse_configuration.xml",
         "duplicate_component_type_overuse_configuration.xml",
         "duplicate_io_config_overuse_configuration.xml",
         "incomplete_app_category_io_thresholds_overuse_configuration.xml",
         "incomplete_component_io_thresholds_overuse_configuration.xml",
         "incomplete_pkg_io_thresholds_overuse_configuration.xml",
         "incomplete_systemwide_io_thresholds_overuse_configuration.xml",
         "invalid_component_type_overuse_configuration.xml",
         "invalid_param_systemwide_io_thresholds_overuse_configuration.xml",
         "invalid_state_app_category_io_thresholds_overuse_configuration.xml",
         "invalid_state_component_io_thresholds_overuse_configuration.xml",
         "invalid_state_pkg_io_thresholds_overuse_configuration.xml",
         "invalid_type_app_category_mapping_overuse_configuration.xml",
         "missing_component_io_thresholds_overuse_configuration.xml",
         "missing_io_config_overuse_configuration.xml",
         "missing_pkg_name_app_category_mapping_overuse_configuration.xml",
         "missing_pkg_name_pkg_io_thresholds_overuse_configuration.xml",
         "missing_pkg_name_safe_to_kill_entry_overuse_configuration.xml",
         "missing_threshold_app_category_io_thresholds_overuse_configuration.xml",
         "missing_threshold_component_io_thresholds_overuse_configuration.xml",
         "missing_threshold_pkg_io_thresholds_overuse_configuration.xml",
         "missing_threshold_systemwide_io_thresholds_overuse_configuration.xml"};

std::string getTestFilePath(const char* filename) {
    static std::string baseDir = android::base::GetExecutableDirectory();
    return baseDir + kTestDataDir + filename;
}

}  // namespace

TEST(OveruseConfigurationXmlHelperTest, TestValidSystemConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::SYSTEM,
                                                            300 * kOneMegaByte, 150 * kOneMegaByte,
                                                            500 * kOneMegaByte),
            /*packageSpecific=*/
            {toPerStateIoOveruseThreshold("system.package.C", 400 * kOneMegaByte,
                                          100 * kOneMegaByte, 200 * kOneMegaByte),
             toPerStateIoOveruseThreshold("system.package.D", 1024 * kOneMegaByte,
                                          500 * kOneMegaByte, 2048 * kOneMegaByte)},
            /*categorySpecific=*/{},
            /*systemWide=*/{toIoOveruseAlertThreshold(10, 200), toIoOveruseAlertThreshold(5, 50)});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::SYSTEM,
                                           /*safeToKill=*/{"system.package.A", "system.package.B"},
                                           /*vendorPrefixes=*/{},
                                           /*packageMetadata=*/
                                           {toPackageMetadata("system.package.A",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("system.package.B",
                                                              ApplicationCategoryType::MAPS)},
                                           ioConfig);
    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(
            getTestFilePath(kValidSystemConfiguration).c_str());
    ASSERT_RESULT_OK(actual);
    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();
}

TEST(OveruseConfigurationXmlHelperTest, TestValidVendorConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::VENDOR,
                                                            1024 * kOneMegaByte, 512 * kOneMegaByte,
                                                            3072 * kOneMegaByte),
            /*packageSpecific=*/
            {toPerStateIoOveruseThreshold("com.vendor.package.C", 400 * kOneMegaByte,
                                          100 * kOneMegaByte, 200 * kOneMegaByte),
             toPerStateIoOveruseThreshold("com.vendor.package.D", 1024 * kOneMegaByte,
                                          500 * kOneMegaByte, 2048 * kOneMegaByte)},
            /*categorySpecific=*/
            {toPerStateIoOveruseThreshold("MAPS", 800 * kOneMegaByte, 900 * kOneMegaByte,
                                          2048 * kOneMegaByte),
             toPerStateIoOveruseThreshold("MEDIA", 600 * kOneMegaByte, 700 * kOneMegaByte,
                                          1024 * kOneMegaByte)},
            /*systemWide=*/{});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::VENDOR,
                                           /*safeToKill=*/
                                           {"com.vendor.package.A", "com.vendor.package.B"},
                                           /*vendorPrefixes=*/{"com.vendor.package"},
                                           /*packageMetadata=*/
                                           {toPackageMetadata("com.vendor.package.A",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("com.vendor.package.B",
                                                              ApplicationCategoryType::MAPS),
                                            toPackageMetadata("com.third.party.package.C",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("system.package.D",
                                                              ApplicationCategoryType::MAPS)},
                                           ioConfig);
    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(
            getTestFilePath(kValidVendorConfiguration).c_str());
    ASSERT_RESULT_OK(actual);
    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();
}

TEST(OveruseConfigurationXmlHelperTest, TestValidThirdPartyConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::THIRD_PARTY,
                                                            300 * kOneMegaByte, 150 * kOneMegaByte,
                                                            500 * kOneMegaByte),
            /*packageSpecific=*/{},
            /*categorySpecific=*/{},
            /*systemWide=*/{});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::THIRD_PARTY,
                                           /*safeToKill=*/{},
                                           /*vendorPrefixes=*/{},
                                           /*packageMetadata=*/{}, ioConfig);
    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(
            getTestFilePath(kValidThirdPartyConfiguration).c_str());
    ASSERT_RESULT_OK(actual);
    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();
}

TEST(OveruseConfigurationXmlHelperTest, TestInvalidOveruseConfigurations) {
    for (const auto& filename : kInvalidOveruseConfigurations) {
        ASSERT_FALSE(
                OveruseConfigurationXmlHelper::parseXmlFile(getTestFilePath(filename).c_str()).ok())
                << "Must return error on parsing '" << filename << "'";
    }
}

TEST(OveruseConfigurationXmlHelperTest, TestWriteXmlFileWithSystemConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::SYSTEM,
                                                            300 * kOneMegaByte, 150 * kOneMegaByte,
                                                            500 * kOneMegaByte),
            /*packageSpecific=*/
            {toPerStateIoOveruseThreshold("system.package.C", 400 * kOneMegaByte,
                                          100 * kOneMegaByte, 200 * kOneMegaByte),
             toPerStateIoOveruseThreshold("system.package.D", 1024 * kOneMegaByte,
                                          500 * kOneMegaByte, 2048 * kOneMegaByte)},
            /*categorySpecific=*/{},
            /*systemWide=*/{toIoOveruseAlertThreshold(10, 200), toIoOveruseAlertThreshold(5, 50)});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::SYSTEM,
                                           /*safeToKill=*/{"system.package.A", "system.package.B"},
                                           /*vendorPrefixes=*/{},
                                           /*packageMetadata=*/
                                           {toPackageMetadata("system.package.A",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("system.package.B",
                                                              ApplicationCategoryType::MAPS)},
                                           ioConfig);
    TemporaryFile temporaryFile;
    ASSERT_NE(temporaryFile.fd, -1);

    ASSERT_RESULT_OK(OveruseConfigurationXmlHelper::writeXmlFile(expected, temporaryFile.path));

    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(temporaryFile.path);

    ASSERT_RESULT_OK(actual);

    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();

    temporaryFile.release();
}

TEST(OveruseConfigurationXmlHelperTest, TestWriteXmlFileWithVendorConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::VENDOR,
                                                            1024 * kOneMegaByte, 512 * kOneMegaByte,
                                                            3072 * kOneMegaByte),
            /*packageSpecific=*/
            {toPerStateIoOveruseThreshold("com.vendor.package.C", 400 * kOneMegaByte,
                                          100 * kOneMegaByte, 200 * kOneMegaByte),
             toPerStateIoOveruseThreshold("com.vendor.package.D", 1024 * kOneMegaByte,
                                          500 * kOneMegaByte, 2048 * kOneMegaByte)},
            /*categorySpecific=*/
            {toPerStateIoOveruseThreshold("MAPS", 800 * kOneMegaByte, 900 * kOneMegaByte,
                                          2048 * kOneMegaByte),
             toPerStateIoOveruseThreshold("MEDIA", 600 * kOneMegaByte, 700 * kOneMegaByte,
                                          1024 * kOneMegaByte)},
            /*systemWide=*/{});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::VENDOR,
                                           /*safeToKill=*/
                                           {"com.vendor.package.A", "com.vendor.package.B"},
                                           /*vendorPrefixes=*/{"com.vendor.package"},
                                           /*packageMetadata=*/
                                           {toPackageMetadata("com.vendor.package.A",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("com.vendor.package.B",
                                                              ApplicationCategoryType::MAPS),
                                            toPackageMetadata("com.third.party.package.C",
                                                              ApplicationCategoryType::MEDIA),
                                            toPackageMetadata("system.package.D",
                                                              ApplicationCategoryType::MAPS)},
                                           ioConfig);
    TemporaryFile temporaryFile;
    ASSERT_NE(temporaryFile.fd, -1);

    ASSERT_RESULT_OK(OveruseConfigurationXmlHelper::writeXmlFile(expected, temporaryFile.path));

    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(temporaryFile.path);

    ASSERT_RESULT_OK(actual);

    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();

    temporaryFile.release();
}

TEST(OveruseConfigurationXmlHelperTest, TestWriteXmlFileWithThirdPartyConfiguration) {
    auto ioConfig = constructIoOveruseConfig(
            /*componentLevel=*/toPerStateIoOveruseThreshold(ComponentType::THIRD_PARTY,
                                                            300 * kOneMegaByte, 150 * kOneMegaByte,
                                                            500 * kOneMegaByte),
            /*packageSpecific=*/{},
            /*categorySpecific=*/{},
            /*systemWide=*/{});
    ResourceOveruseConfiguration expected =
            constructResourceOveruseConfig(ComponentType::THIRD_PARTY,
                                           /*safeToKill=*/{},
                                           /*vendorPrefixes=*/{},
                                           /*packageMetadata=*/{}, ioConfig);
    TemporaryFile temporaryFile;
    ASSERT_NE(temporaryFile.fd, -1);

    ASSERT_RESULT_OK(OveruseConfigurationXmlHelper::writeXmlFile(expected, temporaryFile.path));

    auto actual = OveruseConfigurationXmlHelper::parseXmlFile(temporaryFile.path);

    ASSERT_RESULT_OK(actual);

    EXPECT_THAT(*actual, ResourceOveruseConfigurationMatcher(expected))
            << "Expected: " << expected.toString() << "\nActual: " << actual->toString();

    temporaryFile.release();
}

TEST(OveruseConfigurationXmlHelperTest, TestFailsWriteXmlFileWithInvalidConfig) {
    ResourceOveruseConfiguration resourceOveruseConfig;
    resourceOveruseConfig.componentType = ComponentType::THIRD_PARTY;

    TemporaryFile temporaryFile;
    ASSERT_NE(temporaryFile.fd, -1);

    ASSERT_FALSE(
            OveruseConfigurationXmlHelper::writeXmlFile(resourceOveruseConfig, temporaryFile.path)
                    .ok())
            << "Should fail to write invalid config";

    temporaryFile.release();
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
