/*
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"

#include "IoOveruseConfigs.h"

#include "OveruseConfigurationXmlHelper.h"
#include "PackageInfoResolver.h"

#include <android-base/strings.h>
#include <log/log.h>

#include <inttypes.h>

#include <filesystem>
#include <limits>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::PerStateBytes;
using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold;
using ::aidl::android::automotive::watchdog::internal::IoOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::PackageMetadata;
using ::aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::ResourceSpecificConfiguration;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;

namespace {

// Enum to filter the updatable overuse configs by each component.
enum OveruseConfigEnum {
    COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES = 1 << 0,
    VENDOR_PACKAGE_PREFIXES = 1 << 1,
    PACKAGE_APP_CATEGORY_MAPPINGS = 1 << 2,
    COMPONENT_SPECIFIC_GENERIC_THRESHOLDS = 1 << 3,
    COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS = 1 << 4,
    PER_CATEGORY_THRESHOLDS = 1 << 5,
    SYSTEM_WIDE_ALERT_THRESHOLDS = 1 << 6,
};

const int32_t kSystemComponentUpdatableConfigs = COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES |
        PACKAGE_APP_CATEGORY_MAPPINGS | COMPONENT_SPECIFIC_GENERIC_THRESHOLDS |
        COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS | SYSTEM_WIDE_ALERT_THRESHOLDS;
const int32_t kVendorComponentUpdatableConfigs = COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES |
        VENDOR_PACKAGE_PREFIXES | PACKAGE_APP_CATEGORY_MAPPINGS |
        COMPONENT_SPECIFIC_GENERIC_THRESHOLDS | COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS |
        PER_CATEGORY_THRESHOLDS;
const int32_t kThirdPartyComponentUpdatableConfigs = COMPONENT_SPECIFIC_GENERIC_THRESHOLDS;

const std::vector<std::string> toStringVector(const std::unordered_set<std::string>& values) {
    std::vector<std::string> output;
    for (const auto& v : values) {
        if (!v.empty()) {
            output.emplace_back(v);
        }
    }
    return output;
}

std::string toString(const PerStateIoOveruseThreshold& thresholds) {
    return StringPrintf("name=%s, foregroundBytes=%" PRId64 ", backgroundBytes=%" PRId64
                        ", garageModeBytes=%" PRId64,
                        thresholds.name.c_str(), thresholds.perStateWriteBytes.foregroundBytes,
                        thresholds.perStateWriteBytes.backgroundBytes,
                        thresholds.perStateWriteBytes.garageModeBytes);
}

Result<void> containsValidThresholds(const PerStateIoOveruseThreshold& thresholds) {
    if (thresholds.name.empty()) {
        return Error() << "Doesn't contain threshold name";
    }

    if (thresholds.perStateWriteBytes.foregroundBytes <= 0 ||
        thresholds.perStateWriteBytes.backgroundBytes <= 0 ||
        thresholds.perStateWriteBytes.garageModeBytes <= 0) {
        return Error() << "Some thresholds are less than or equal to zero: "
                       << toString(thresholds);
    }
    return {};
}

Result<void> containsValidThreshold(const IoOveruseAlertThreshold& threshold) {
    if (threshold.durationInSeconds <= 0) {
        return Error() << "Duration must be greater than zero";
    }
    if (threshold.writtenBytesPerSecond <= 0) {
        return Error() << "Written bytes/second must be greater than zero";
    }
    return {};
}

ApplicationCategoryType toApplicationCategoryType(const std::string& value) {
    if (value == "MAPS") {
        return ApplicationCategoryType::MAPS;
    }
    if (value == "MEDIA") {
        return ApplicationCategoryType::MEDIA;
    }
    return ApplicationCategoryType::OTHERS;
}

Result<void> isValidIoOveruseConfiguration(const ComponentType componentType,
                                           const int32_t updatableConfigsFilter,
                                           const IoOveruseConfiguration& ioOveruseConfig) {
    auto componentTypeStr = toString(componentType);
    if (updatableConfigsFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_GENERIC_THRESHOLDS) {
        if (auto result = containsValidThresholds(ioOveruseConfig.componentLevelThresholds);
            !result.ok()) {
            return Error() << "Invalid " << toString(componentType)
                           << " component level generic thresholds: " << result.error();
        }
        if (ioOveruseConfig.componentLevelThresholds.name != componentTypeStr) {
            return Error() << "Invalid component name "
                           << ioOveruseConfig.componentLevelThresholds.name
                           << " in component level generic thresholds for component "
                           << componentTypeStr;
        }
    }
    const auto containsValidSystemWideThresholds = [&]() -> bool {
        if (ioOveruseConfig.systemWideThresholds.empty()) {
            return false;
        }
        for (const auto& threshold : ioOveruseConfig.systemWideThresholds) {
            if (auto result = containsValidThreshold(threshold); !result.ok()) {
                return false;
            }
        }
        return true;
    };
    if ((updatableConfigsFilter & OveruseConfigEnum::SYSTEM_WIDE_ALERT_THRESHOLDS) &&
        !containsValidSystemWideThresholds()) {
        return Error() << "Invalid system-wide alert threshold provided in " << componentTypeStr
                       << " config";
    }
    return {};
}

Result<int32_t> getComponentFilter(const ComponentType componentType) {
    switch (componentType) {
        case ComponentType::SYSTEM:
            return kSystemComponentUpdatableConfigs;
        case ComponentType::VENDOR:
            return kVendorComponentUpdatableConfigs;
        case ComponentType::THIRD_PARTY:
            return kThirdPartyComponentUpdatableConfigs;
        default:
            return Error() << "Invalid component type: " << static_cast<int32_t>(componentType);
    }
}

Result<void> isValidResourceOveruseConfig(
        const ResourceOveruseConfiguration& resourceOveruseConfig) {
    const auto filter = getComponentFilter(resourceOveruseConfig.componentType);
    if (!filter.ok()) {
        return Error() << filter.error();
    }
    std::unordered_map<std::string, ApplicationCategoryType> seenCategoryMappings;
    for (const auto& meta : resourceOveruseConfig.packageMetadata) {
        if (const auto it = seenCategoryMappings.find(meta.packageName);
            it != seenCategoryMappings.end() && it->second != meta.appCategoryType) {
            return Error()
                    << "Must provide exactly one application category mapping for the package "
                    << meta.packageName << ": Provided mappings " << toString(meta.appCategoryType)
                    << " and " << toString(it->second);
        }
        seenCategoryMappings[meta.packageName] = meta.appCategoryType;
    }
    if (resourceOveruseConfig.resourceSpecificConfigurations.size() != 1) {
        return Error() << "Must provide exactly one I/O overuse configuration. Received "
                       << resourceOveruseConfig.resourceSpecificConfigurations.size()
                       << " configurations";
    }
    for (const auto& config : resourceOveruseConfig.resourceSpecificConfigurations) {
        if (config.getTag() != ResourceSpecificConfiguration::ioOveruseConfiguration) {
            return Error() << "Invalid resource type: " << static_cast<int32_t>(config.getTag());
        }
        const auto& ioOveruseConfig =
                config.get<ResourceSpecificConfiguration::ioOveruseConfiguration>();
        if (auto result = isValidIoOveruseConfiguration(resourceOveruseConfig.componentType,
                                                        *filter, ioOveruseConfig);
            !result.ok()) {
            return Error() << "Invalid I/O overuse configuration for component "
                           << toString(resourceOveruseConfig.componentType).c_str() << ": "
                           << result.error();
        }
    }
    return {};
}

Result<void> isValidResourceOveruseConfigs(
        const std::vector<ResourceOveruseConfiguration>& resourceOveruseConfigs) {
    std::unordered_set<ComponentType> seenComponentTypes;
    for (const auto& resourceOveruseConfig : resourceOveruseConfigs) {
        if (seenComponentTypes.count(resourceOveruseConfig.componentType) > 0) {
            return Error() << "Cannot provide duplicate configs for the same component type "
                           << toString(resourceOveruseConfig.componentType);
        }
        if (const auto result = isValidResourceOveruseConfig(resourceOveruseConfig); !result.ok()) {
            return result;
        }
        seenComponentTypes.insert(resourceOveruseConfig.componentType);
    }
    return {};
}

bool isSafeToKillAnyPackage(const std::vector<std::string>& packages,
                            const std::unordered_set<std::string>& safeToKillPackages) {
    for (const auto& packageName : packages) {
        if (safeToKillPackages.find(packageName) != safeToKillPackages.end()) {
            return true;
        }
    }
    return false;
}

}  // namespace

IoOveruseConfigs::ParseXmlFileFunction IoOveruseConfigs::sParseXmlFile =
        &OveruseConfigurationXmlHelper::parseXmlFile;
IoOveruseConfigs::WriteXmlFileFunction IoOveruseConfigs::sWriteXmlFile =
        &OveruseConfigurationXmlHelper::writeXmlFile;

Result<void> ComponentSpecificConfig::updatePerPackageThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds,
        const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes) {
    mPerPackageThresholds.clear();
    if (thresholds.empty()) {
        return Error() << "\tNo per-package thresholds provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& packageThreshold : thresholds) {
        if (packageThreshold.name.empty()) {
            StringAppendF(&errorMsgs, "\tSkipping per-package threshold without package name\n");
            continue;
        }
        maybeAppendVendorPackagePrefixes(packageThreshold.name);
        if (auto result = containsValidThresholds(packageThreshold); !result.ok()) {
            StringAppendF(&errorMsgs,
                          "\tSkipping invalid package specific thresholds for package '%s': %s\n",
                          packageThreshold.name.c_str(), result.error().message().c_str());
            continue;
        }
        if (const auto& it = mPerPackageThresholds.find(packageThreshold.name);
            it != mPerPackageThresholds.end()) {
            StringAppendF(&errorMsgs, "\tDuplicate threshold received for package '%s'\n",
                          packageThreshold.name.c_str());
        }
        mPerPackageThresholds[packageThreshold.name] = packageThreshold;
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> ComponentSpecificConfig::updateSafeToKillPackages(
        const std::vector<std::string>& packages,
        const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes) {
    mSafeToKillPackages.clear();
    if (packages.empty()) {
        return Error() << "\tNo safe-to-kill packages provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& packageName : packages) {
        if (packageName.empty()) {
            StringAppendF(&errorMsgs, "\tSkipping empty safe-to-kill package name");
            continue;
        }
        maybeAppendVendorPackagePrefixes(packageName);
        mSafeToKillPackages.insert(packageName);
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

IoOveruseConfigs::IoOveruseConfigs() :
      mSystemConfig({}),
      mVendorConfig({}),
      mThirdPartyConfig({}),
      mPackagesToAppCategories({}),
      mPackagesToAppCategoryMappingUpdateMode(OVERWRITE),
      mPerCategoryThresholds({}),
      mVendorPackagePrefixes({}) {
    const auto updateFromXmlPerType = [&](const char* filename, const char* configType) -> bool {
        if (const auto result = this->updateFromXml(filename); !result.ok()) {
            ALOGE("Failed to parse %s resource overuse configuration from '%s': %s", configType,
                  filename, result.error().message().c_str());
            return false;
        }
        ALOGI("Updated with %s resource overuse configuration from '%s'", configType, filename);
        return true;
    };
    /*
     * Package to app category mapping is common between system and vendor component configs. When
     * the build system and vendor component configs are used, the mapping shouldn't be
     * overwritten by either of the configs because the build configurations defined by the
     * vendor or system components may not be aware of mappings included in other component's
     * config. Ergo, the mapping from both the component configs should be merged together. When a
     * latest config is used for either of the components, the latest mapping should be given higher
     * priority.
     */
    bool isBuildSystemConfig = false;
    if (!updateFromXmlPerType(kLatestSystemConfigXmlPath, "latest system")) {
        isBuildSystemConfig = updateFromXmlPerType(kBuildSystemConfigXmlPath, "build system");
    }
    if (!updateFromXmlPerType(kLatestVendorConfigXmlPath, "latest vendor")) {
        mPackagesToAppCategoryMappingUpdateMode = isBuildSystemConfig ? MERGE : NO_UPDATE;
        if (!updateFromXmlPerType(kBuildVendorConfigXmlPath, "build vendor") &&
            mSystemConfig.mGeneric.name != kDefaultThresholdName) {
            mVendorConfig.mGeneric = mSystemConfig.mGeneric;
            mVendorConfig.mGeneric.name = toString(ComponentType::VENDOR);
        }
        mPackagesToAppCategoryMappingUpdateMode = OVERWRITE;
    }
    if (!updateFromXmlPerType(kLatestThirdPartyConfigXmlPath, "latest third-party")) {
        if (!updateFromXmlPerType(kBuildThirdPartyConfigXmlPath, "build third-party") &&
            mSystemConfig.mGeneric.name != kDefaultThresholdName) {
            mThirdPartyConfig.mGeneric = mSystemConfig.mGeneric;
            mThirdPartyConfig.mGeneric.name = toString(ComponentType::THIRD_PARTY);
        }
    }
}

size_t IoOveruseConfigs::AlertThresholdHashByDuration::operator()(
        const IoOveruseAlertThreshold& threshold) const {
    return std::hash<std::string>{}(std::to_string(threshold.durationInSeconds));
}

bool IoOveruseConfigs::AlertThresholdEqualByDuration::operator()(
        const IoOveruseAlertThreshold& l, const IoOveruseAlertThreshold& r) const {
    return l.durationInSeconds == r.durationInSeconds;
}

Result<void> IoOveruseConfigs::updatePerCategoryThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds) {
    mPerCategoryThresholds.clear();
    if (thresholds.empty()) {
        return Error() << "\tNo per-category thresholds provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& categoryThreshold : thresholds) {
        if (auto result = containsValidThresholds(categoryThreshold); !result.ok()) {
            StringAppendF(&errorMsgs, "\tInvalid category specific thresholds: '%s'\n",
                          result.error().message().c_str());
            continue;
        }
        if (auto category = toApplicationCategoryType(categoryThreshold.name);
            category == ApplicationCategoryType::OTHERS) {
            StringAppendF(&errorMsgs, "\tInvalid application category '%s'\n",
                          categoryThreshold.name.c_str());
        } else {
            if (const auto& it = mPerCategoryThresholds.find(category);
                it != mPerCategoryThresholds.end()) {
                StringAppendF(&errorMsgs, "\tDuplicate threshold received for category: '%s'\n",
                              categoryThreshold.name.c_str());
            }
            mPerCategoryThresholds[category] = categoryThreshold;
        }
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> IoOveruseConfigs::updateAlertThresholds(
        const std::vector<IoOveruseAlertThreshold>& thresholds) {
    mAlertThresholds.clear();
    std::string errorMsgs;
    for (const auto& alertThreshold : thresholds) {
        if (auto result = containsValidThreshold(alertThreshold); !result.ok()) {
            StringAppendF(&errorMsgs, "\tInvalid system-wide alert threshold: %s\n",
                          result.error().message().c_str());
            continue;
        }
        if (const auto& it = mAlertThresholds.find(alertThreshold); it != mAlertThresholds.end()) {
            StringAppendF(&errorMsgs,
                          "\tDuplicate threshold received for duration %" PRId64
                          ". Overwriting previous threshold with %" PRId64
                          " written bytes per second \n",
                          alertThreshold.durationInSeconds, it->writtenBytesPerSecond);
        }
        mAlertThresholds.emplace(alertThreshold);
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> IoOveruseConfigs::update(
        const std::vector<ResourceOveruseConfiguration>& resourceOveruseConfigs) {
    if (const auto result = isValidResourceOveruseConfigs(resourceOveruseConfigs); !result.ok()) {
        return Error(EX_ILLEGAL_ARGUMENT) << result.error();
    }
    for (const auto& resourceOveruseConfig : resourceOveruseConfigs) {
        updateFromAidlConfig(resourceOveruseConfig);
    }
    return {};
}

Result<void> IoOveruseConfigs::updateFromXml(const char* filename) {
    const auto resourceOveruseConfig = sParseXmlFile(filename);
    if (!resourceOveruseConfig.ok()) {
        return Error() << "Failed to parse configuration: " << resourceOveruseConfig.error();
    }
    if (const auto result = isValidResourceOveruseConfig(*resourceOveruseConfig); !result.ok()) {
        return result;
    }
    updateFromAidlConfig(*resourceOveruseConfig);
    return {};
}

void IoOveruseConfigs::updateFromAidlConfig(
        const ResourceOveruseConfiguration& resourceOveruseConfig) {
    ComponentSpecificConfig* targetComponentConfig;
    int32_t updatableConfigsFilter = 0;
    switch (resourceOveruseConfig.componentType) {
        case ComponentType::SYSTEM:
            targetComponentConfig = &mSystemConfig;
            updatableConfigsFilter = kSystemComponentUpdatableConfigs;
            break;
        case ComponentType::VENDOR:
            targetComponentConfig = &mVendorConfig;
            updatableConfigsFilter = kVendorComponentUpdatableConfigs;
            break;
        case ComponentType::THIRD_PARTY:
            targetComponentConfig = &mThirdPartyConfig;
            updatableConfigsFilter = kThirdPartyComponentUpdatableConfigs;
            break;
        default:
            // This case shouldn't execute as it is caught during validation.
            return;
    }

    const std::string componentTypeStr = toString(resourceOveruseConfig.componentType);
    for (const auto& resourceSpecificConfig :
         resourceOveruseConfig.resourceSpecificConfigurations) {
        /*
         * |resourceSpecificConfig| should contain only ioOveruseConfiguration as it is verified
         * during validation.
         */
        const auto& ioOveruseConfig =
                resourceSpecificConfig.get<ResourceSpecificConfiguration::ioOveruseConfiguration>();
        if (auto res = update(resourceOveruseConfig, ioOveruseConfig, updatableConfigsFilter,
                              targetComponentConfig);
            !res.ok()) {
            ALOGE("Ignorable I/O overuse configuration errors for '%s' component:\n%s",
                  componentTypeStr.c_str(), res.error().message().c_str());
        }
    }
    return;
}

Result<void> IoOveruseConfigs::update(
        const ResourceOveruseConfiguration& resourceOveruseConfiguration,
        const IoOveruseConfiguration& ioOveruseConfiguration, int32_t updatableConfigsFilter,
        ComponentSpecificConfig* targetComponentConfig) {
    if ((updatableConfigsFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_GENERIC_THRESHOLDS)) {
        targetComponentConfig->mGeneric = ioOveruseConfiguration.componentLevelThresholds;
    }

    std::string nonUpdatableConfigMsgs;
    if (updatableConfigsFilter & OveruseConfigEnum::VENDOR_PACKAGE_PREFIXES) {
        mVendorPackagePrefixes.clear();
        for (const auto& prefix : resourceOveruseConfiguration.vendorPackagePrefixes) {
            if (!prefix.empty()) {
                mVendorPackagePrefixes.insert(prefix);
            }
        }
    } else if (!resourceOveruseConfiguration.vendorPackagePrefixes.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%svendor packages prefixes",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & OveruseConfigEnum::PACKAGE_APP_CATEGORY_MAPPINGS) {
        if (mPackagesToAppCategoryMappingUpdateMode == OVERWRITE) {
            mPackagesToAppCategories.clear();
        }
        if (mPackagesToAppCategoryMappingUpdateMode != NO_UPDATE) {
            for (const auto& meta : resourceOveruseConfiguration.packageMetadata) {
                if (!meta.packageName.empty()) {
                    mPackagesToAppCategories[meta.packageName] = meta.appCategoryType;
                }
            }
        }
    } else if (!resourceOveruseConfiguration.packageMetadata.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%spackage to application category mappings",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    std::string errorMsgs;
    const auto maybeAppendVendorPackagePrefixes =
            [&componentType = std::as_const(resourceOveruseConfiguration.componentType),
             &vendorPackagePrefixes = mVendorPackagePrefixes](const std::string& packageName) {
                if (componentType != ComponentType::VENDOR) {
                    return;
                }
                for (const auto& prefix : vendorPackagePrefixes) {
                    if (StartsWith(packageName, prefix)) {
                        return;
                    }
                }
                vendorPackagePrefixes.insert(packageName);
            };

    if (updatableConfigsFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS) {
        if (auto result = targetComponentConfig
                                  ->updatePerPackageThresholds(ioOveruseConfiguration
                                                                       .packageSpecificThresholds,
                                                               maybeAppendVendorPackagePrefixes);
            !result.ok()) {
            StringAppendF(&errorMsgs, "\t\t%s", result.error().message().c_str());
        }
    } else if (!ioOveruseConfiguration.packageSpecificThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%sper-package thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES) {
        if (auto result = targetComponentConfig
                                  ->updateSafeToKillPackages(resourceOveruseConfiguration
                                                                     .safeToKillPackages,
                                                             maybeAppendVendorPackagePrefixes);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s\t\t%s", !errorMsgs.empty() ? "\n" : "",
                          result.error().message().c_str());
        }
    } else if (!resourceOveruseConfiguration.safeToKillPackages.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%ssafe-to-kill list",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & OveruseConfigEnum::PER_CATEGORY_THRESHOLDS) {
        if (auto result =
                    updatePerCategoryThresholds(ioOveruseConfiguration.categorySpecificThresholds);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s\t\t%s", !errorMsgs.empty() ? "\n" : "",
                          result.error().message().c_str());
        }
    } else if (!ioOveruseConfiguration.categorySpecificThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%scategory specific thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & OveruseConfigEnum::SYSTEM_WIDE_ALERT_THRESHOLDS) {
        if (auto result = updateAlertThresholds(ioOveruseConfiguration.systemWideThresholds);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s\t\t%s", !errorMsgs.empty() ? "\n" : "",
                          result.error().message().c_str());
        }
    } else if (!ioOveruseConfiguration.systemWideThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%ssystem-wide alert thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (!nonUpdatableConfigMsgs.empty()) {
        StringAppendF(&errorMsgs, "%s\t\tReceived values for non-updatable configs: [%s]",
                      !errorMsgs.empty() ? "\n" : "", nonUpdatableConfigMsgs.c_str());
    }
    if (!errorMsgs.empty()) {
        return Error() << errorMsgs.c_str();
    }
    return {};
}

void IoOveruseConfigs::get(
        std::vector<ResourceOveruseConfiguration>* resourceOveruseConfigs) const {
    auto systemConfig = get(mSystemConfig, kSystemComponentUpdatableConfigs);
    if (systemConfig.has_value()) {
        systemConfig->componentType = ComponentType::SYSTEM;
        resourceOveruseConfigs->emplace_back(std::move(*systemConfig));
    }

    auto vendorConfig = get(mVendorConfig, kVendorComponentUpdatableConfigs);
    if (vendorConfig.has_value()) {
        vendorConfig->componentType = ComponentType::VENDOR;
        resourceOveruseConfigs->emplace_back(std::move(*vendorConfig));
    }

    auto thirdPartyConfig = get(mThirdPartyConfig, kThirdPartyComponentUpdatableConfigs);
    if (thirdPartyConfig.has_value()) {
        thirdPartyConfig->componentType = ComponentType::THIRD_PARTY;
        resourceOveruseConfigs->emplace_back(std::move(*thirdPartyConfig));
    }
}

std::optional<ResourceOveruseConfiguration> IoOveruseConfigs::get(
        const ComponentSpecificConfig& componentSpecificConfig,
        const int32_t componentFilter) const {
    if (componentSpecificConfig.mGeneric.name == kDefaultThresholdName) {
        return {};
    }
    ResourceOveruseConfiguration resourceOveruseConfiguration;
    IoOveruseConfiguration ioOveruseConfiguration;
    if ((componentFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_GENERIC_THRESHOLDS)) {
        ioOveruseConfiguration.componentLevelThresholds = componentSpecificConfig.mGeneric;
    }
    if (componentFilter & OveruseConfigEnum::VENDOR_PACKAGE_PREFIXES) {
        resourceOveruseConfiguration.vendorPackagePrefixes = toStringVector(mVendorPackagePrefixes);
    }
    if (componentFilter & OveruseConfigEnum::PACKAGE_APP_CATEGORY_MAPPINGS) {
        for (const auto& [packageName, appCategoryType] : mPackagesToAppCategories) {
            PackageMetadata meta;
            meta.packageName = packageName;
            meta.appCategoryType = appCategoryType;
            resourceOveruseConfiguration.packageMetadata.push_back(meta);
        }
    }
    if (componentFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS) {
        for (const auto& [packageName, threshold] : componentSpecificConfig.mPerPackageThresholds) {
            ioOveruseConfiguration.packageSpecificThresholds.push_back(threshold);
        }
    }
    if (componentFilter & OveruseConfigEnum::COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES) {
        resourceOveruseConfiguration.safeToKillPackages =
                toStringVector(componentSpecificConfig.mSafeToKillPackages);
    }
    if (componentFilter & OveruseConfigEnum::PER_CATEGORY_THRESHOLDS) {
        for (const auto& [category, threshold] : mPerCategoryThresholds) {
            ioOveruseConfiguration.categorySpecificThresholds.push_back(threshold);
        }
    }
    if (componentFilter & OveruseConfigEnum::SYSTEM_WIDE_ALERT_THRESHOLDS) {
        for (const auto& threshold : mAlertThresholds) {
            ioOveruseConfiguration.systemWideThresholds.push_back(threshold);
        }
    }
    ResourceSpecificConfiguration resourceSpecificConfig;
    resourceSpecificConfig.set<ResourceSpecificConfiguration::ioOveruseConfiguration>(
            ioOveruseConfiguration);
    resourceOveruseConfiguration.resourceSpecificConfigurations.emplace_back(
            std::move(resourceSpecificConfig));
    return resourceOveruseConfiguration;
}

Result<void> IoOveruseConfigs::writeToDisk() {
    std::vector<ResourceOveruseConfiguration> resourceOveruseConfigs;
    get(&resourceOveruseConfigs);
    for (const auto resourceOveruseConfig : resourceOveruseConfigs) {
        switch (resourceOveruseConfig.componentType) {
            case ComponentType::SYSTEM:
                if (const auto result =
                            sWriteXmlFile(resourceOveruseConfig, kLatestSystemConfigXmlPath);
                    !result.ok()) {
                    return Error() << "Failed to write system resource overuse config to disk";
                }
                continue;
            case ComponentType::VENDOR:
                if (const auto result =
                            sWriteXmlFile(resourceOveruseConfig, kLatestVendorConfigXmlPath);
                    !result.ok()) {
                    return Error() << "Failed to write vendor resource overuse config to disk";
                }
                continue;
            case ComponentType::THIRD_PARTY:
                if (const auto result =
                            sWriteXmlFile(resourceOveruseConfig, kLatestThirdPartyConfigXmlPath);
                    !result.ok()) {
                    return Error() << "Failed to write third-party resource overuse config to disk";
                }
                continue;
            case ComponentType::UNKNOWN:
                continue;
        }
    }
    return {};
}

PerStateBytes IoOveruseConfigs::fetchThreshold(const PackageInfo& packageInfo) const {
    switch (packageInfo.componentType) {
        case ComponentType::SYSTEM:
            if (const auto it = mSystemConfig.mPerPackageThresholds.find(
                        packageInfo.packageIdentifier.name);
                it != mSystemConfig.mPerPackageThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mSystemConfig.mGeneric.perStateWriteBytes;
        case ComponentType::VENDOR:
            if (const auto it = mVendorConfig.mPerPackageThresholds.find(
                        packageInfo.packageIdentifier.name);
                it != mVendorConfig.mPerPackageThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mVendorConfig.mGeneric.perStateWriteBytes;
        case ComponentType::THIRD_PARTY:
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mThirdPartyConfig.mGeneric.perStateWriteBytes;
        default:
            ALOGW("Returning default threshold for %s",
                  packageInfo.packageIdentifier.toString().c_str());
            return defaultThreshold().perStateWriteBytes;
    }
}

bool IoOveruseConfigs::isSafeToKill(const PackageInfo& packageInfo) const {
    if (packageInfo.uidType == UidType::NATIVE) {
        // Native packages can't be disabled so don't kill them on I/O overuse.
        return false;
    }
    switch (packageInfo.componentType) {
        case ComponentType::SYSTEM:
            if (mSystemConfig.mSafeToKillPackages.find(packageInfo.packageIdentifier.name) !=
                mSystemConfig.mSafeToKillPackages.end()) {
                return true;
            }
            return isSafeToKillAnyPackage(packageInfo.sharedUidPackages,
                                          mSystemConfig.mSafeToKillPackages);
        case ComponentType::VENDOR:
            if (mVendorConfig.mSafeToKillPackages.find(packageInfo.packageIdentifier.name) !=
                mVendorConfig.mSafeToKillPackages.end()) {
                return true;
            }
            /*
             * Packages under the vendor shared UID may contain system packages because when
             * CarWatchdogService derives the shared component type it attributes system packages
             * as vendor packages when there is at least one vendor package.
             */
            return isSafeToKillAnyPackage(packageInfo.sharedUidPackages,
                                          mSystemConfig.mSafeToKillPackages) ||
                    isSafeToKillAnyPackage(packageInfo.sharedUidPackages,
                                           mVendorConfig.mSafeToKillPackages);
        default:
            return true;
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
