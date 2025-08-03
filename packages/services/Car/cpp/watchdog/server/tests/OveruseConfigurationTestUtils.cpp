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

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::PerStateBytes;
using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold;
using ::aidl::android::automotive::watchdog::internal::IoOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::PackageMetadata;
using ::aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::ResourceSpecificConfiguration;
using ::testing::AllOf;
using ::testing::ExplainMatchResult;
using ::testing::Field;
using ::testing::Matcher;
using ::testing::UnorderedElementsAreArray;

namespace {

MATCHER_P(IsIoOveruseConfiguration, config, "") {
    return arg.componentLevelThresholds == config.componentLevelThresholds &&
            ExplainMatchResult(UnorderedElementsAreArray(config.packageSpecificThresholds),
                               arg.packageSpecificThresholds, result_listener) &&
            ExplainMatchResult(UnorderedElementsAreArray(config.categorySpecificThresholds),
                               arg.categorySpecificThresholds, result_listener) &&
            ExplainMatchResult(UnorderedElementsAreArray(config.systemWideThresholds),
                               arg.systemWideThresholds, result_listener);
}

MATCHER_P(IsResourceSpecificConfiguration, config, "") {
    if (arg.getTag() != config.getTag()) {
        return false;
    }
    // Reference with the actual datatype so the templated get method can be called.
    const ResourceSpecificConfiguration& expected = config;
    const ResourceSpecificConfiguration& actual = arg;
    switch (arg.getTag()) {
        case ResourceSpecificConfiguration::ioOveruseConfiguration: {
            const auto& expectedIoConfig =
                    expected.get<ResourceSpecificConfiguration::ioOveruseConfiguration>();
            const auto& actualIoConfig =
                    actual.get<ResourceSpecificConfiguration::ioOveruseConfiguration>();
            return ExplainMatchResult(IsIoOveruseConfiguration(expectedIoConfig), actualIoConfig,
                                      result_listener);
        }
        default:
            return true;
    }
}

MATCHER_P(IsPackageMetadata, expected, "") {
    return arg.packageName == expected.packageName &&
            arg.appCategoryType == expected.appCategoryType;
}

}  // namespace

ResourceOveruseConfiguration constructResourceOveruseConfig(
        const ComponentType type, const std::vector<std::string>&& safeToKill,
        const std::vector<std::string>&& vendorPrefixes,
        const std::vector<PackageMetadata> packageMetadata,
        const IoOveruseConfiguration& ioOveruseConfiguration) {
    ResourceOveruseConfiguration resourceOveruseConfig;
    resourceOveruseConfig.componentType = type;
    resourceOveruseConfig.safeToKillPackages = safeToKill;
    resourceOveruseConfig.vendorPackagePrefixes = vendorPrefixes;
    resourceOveruseConfig.packageMetadata = packageMetadata;
    ResourceSpecificConfiguration config;
    config.set<ResourceSpecificConfiguration::ioOveruseConfiguration>(ioOveruseConfiguration);
    resourceOveruseConfig.resourceSpecificConfigurations.push_back(config);
    return resourceOveruseConfig;
}

IoOveruseConfiguration constructIoOveruseConfig(
        PerStateIoOveruseThreshold componentLevel,
        const std::vector<PerStateIoOveruseThreshold>& packageSpecific,
        const std::vector<PerStateIoOveruseThreshold>& categorySpecific,
        const std::vector<IoOveruseAlertThreshold>& systemWide) {
    IoOveruseConfiguration config;
    config.componentLevelThresholds = componentLevel;
    config.packageSpecificThresholds = packageSpecific;
    config.categorySpecificThresholds = categorySpecific;
    config.systemWideThresholds = systemWide;
    return config;
}

PerStateBytes toPerStateBytes(const int64_t fgBytes, const int64_t bgBytes,
                              const int64_t garageModeBytes) {
    PerStateBytes perStateBytes;
    perStateBytes.foregroundBytes = fgBytes;
    perStateBytes.backgroundBytes = bgBytes;
    perStateBytes.garageModeBytes = garageModeBytes;
    return perStateBytes;
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(const std::string& name,
                                                        const PerStateBytes& perStateBytes) {
    PerStateIoOveruseThreshold threshold;
    threshold.name = name;
    threshold.perStateWriteBytes = perStateBytes;
    return threshold;
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(const std::string& name,
                                                        const int64_t fgBytes,
                                                        const int64_t bgBytes,
                                                        const int64_t garageModeBytes) {
    return toPerStateIoOveruseThreshold(name, toPerStateBytes(fgBytes, bgBytes, garageModeBytes));
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(const ComponentType type,
                                                        const PerStateBytes& perStateBytes) {
    return toPerStateIoOveruseThreshold(toString(type), perStateBytes);
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(const ComponentType type,
                                                        const int64_t fgBytes,
                                                        const int64_t bgBytes,
                                                        const int64_t garageModeBytes) {
    return toPerStateIoOveruseThreshold(type, toPerStateBytes(fgBytes, bgBytes, garageModeBytes));
}

PackageMetadata toPackageMetadata(std::string packageName, ApplicationCategoryType type) {
    PackageMetadata meta;
    meta.packageName = packageName;
    meta.appCategoryType = type;
    return meta;
}

IoOveruseAlertThreshold toIoOveruseAlertThreshold(const int64_t durationInSeconds,
                                                  const int64_t writtenBytesPerSecond) {
    IoOveruseAlertThreshold threshold;
    threshold.durationInSeconds = durationInSeconds;
    threshold.writtenBytesPerSecond = writtenBytesPerSecond;
    return threshold;
}

Matcher<const ResourceOveruseConfiguration&> ResourceOveruseConfigurationMatcher(
        const ResourceOveruseConfiguration& config) {
    std::vector<Matcher<const ResourceSpecificConfiguration&>> resourceSpecificConfigMatchers;
    for (const auto& resourceSpecificConfig : config.resourceSpecificConfigurations) {
        resourceSpecificConfigMatchers.push_back(
                IsResourceSpecificConfiguration(resourceSpecificConfig));
    }

    std::vector<Matcher<const PackageMetadata&>> metadataMatchers;
    for (const auto& metadata : config.packageMetadata) {
        metadataMatchers.push_back(IsPackageMetadata(metadata));
    }

    return AllOf(Field(&ResourceOveruseConfiguration::componentType, config.componentType),
                 Field(&ResourceOveruseConfiguration::safeToKillPackages,
                       UnorderedElementsAreArray(config.safeToKillPackages)),
                 Field(&ResourceOveruseConfiguration::vendorPackagePrefixes,
                       UnorderedElementsAreArray(config.vendorPackagePrefixes)),
                 Field(&ResourceOveruseConfiguration::packageMetadata,
                       UnorderedElementsAreArray(metadataMatchers)),
                 Field(&ResourceOveruseConfiguration::resourceSpecificConfigurations,
                       UnorderedElementsAreArray(resourceSpecificConfigMatchers)));
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
