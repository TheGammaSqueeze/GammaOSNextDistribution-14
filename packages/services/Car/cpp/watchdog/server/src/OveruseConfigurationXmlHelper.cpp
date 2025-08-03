/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "OveruseConfigurationXmlHelper.h"

#include <aidl/android/automotive/watchdog/PerStateBytes.h>
#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/IoOveruseAlertThreshold.h>
#include <aidl/android/automotive/watchdog/internal/IoOveruseConfiguration.h>
#include <aidl/android/automotive/watchdog/internal/PackageMetadata.h>
#include <aidl/android/automotive/watchdog/internal/PerStateIoOveruseThreshold.h>
#include <aidl/android/automotive/watchdog/internal/ResourceSpecificConfiguration.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include <tinyxml2.h>

#include <unordered_set>
#include <vector>

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
using ::android::base::EqualsIgnoreCase;
using ::android::base::Error;
using ::android::base::Join;
using ::android::base::ParseInt;
using ::android::base::Result;
using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::Trim;
using ::tinyxml2::XML_SUCCESS;
using ::tinyxml2::XMLDeclaration;
using ::tinyxml2::XMLDocument;
using ::tinyxml2::XMLElement;

namespace {
constexpr const char kTagResourceOveruseConfiguration[] = "resourceOveruseConfiguration";
constexpr const char kTagComponentType[] = "componentType";

constexpr const char kTagSafeToKillPackages[] = "safeToKillPackages";
constexpr const char kTagPackage[] = "package";

constexpr const char kTagVendorPackagePrefixes[] = "vendorPackagePrefixes";
constexpr const char kTagPackagePrefix[] = "packagePrefix";

constexpr const char kTagPackageToAppCategoryTypes[] = "packagesToAppCategoryTypes";
constexpr const char kTagPackageAppCategory[] = "packageAppCategory";

constexpr const char kTagIoOveruseConfiguration[] = "ioOveruseConfiguration";
constexpr const char kTagComponentLevelThresholds[] = "componentLevelThresholds";
constexpr const char kTagPackageSpecificThresholds[] = "packageSpecificThresholds";
constexpr const char kTagAppCategorySpecificThresholds[] = "appCategorySpecificThresholds";
constexpr const char kTagPerStateThreshold[] = "perStateThreshold";
constexpr const char kTagState[] = "state";
constexpr const char kStateIdForegroundMode[] = "foreground_mode";
constexpr const char kStateIdBackgroundMode[] = "background_mode";
constexpr const char kStateIdGarageMode[] = "garage_mode";
constexpr int kNumStates = 3;

constexpr const char kTagSystemWideThresholds[] = "systemWideThresholds";
constexpr const char kTagAlertThreshold[] = "alertThreshold";
constexpr const char kTagParam[] = "param";
constexpr const char kParamIdDurationSeconds[] = "duration_seconds";
constexpr const char kParamIdWrittenBytesPerSecond[] = "written_bytes_per_second";
constexpr int kNumParams = 2;

constexpr const char kAttrId[] = "id";
constexpr const char kAttrType[] = "type";
constexpr const char kAttrVersion[] = "version";
constexpr const char kVersionNumber[] = "1.0";

Result<const XMLElement*> readExactlyOneElement(const char* tag, const XMLElement* rootElement) {
    const XMLElement* element = rootElement->FirstChildElement(tag);
    if (element == nullptr) {
        return Error() << "Must specify value for the tag '" << tag << "'";
    }
    if (element->NextSiblingElement(tag) != nullptr) {
        return Error() << "Must specify only one entry for the tag '" << tag << "'";
    }
    return element;
}

Result<ComponentType> readComponentType(const XMLElement* rootElement) {
    const XMLElement* componentTypeElement;
    if (const auto result = readExactlyOneElement(kTagComponentType, rootElement); result.ok()) {
        componentTypeElement = *result;
    } else {
        return Error() << "Failed to read tag '" << kTagComponentType << "': " << result.error();
    }
    std::string componentTypeStr;
    if (const auto text = componentTypeElement->GetText(); text == nullptr) {
        return Error() << "Must specify non-empty component type";
    } else if (componentTypeStr = Trim(text); componentTypeStr.empty()) {
        return Error() << "Must specify non-empty component type";
    }
    static const std::string* const kSystemComponent =
            new std::string(toString(ComponentType::SYSTEM));
    static const std::string* const kVendorComponent =
            new std::string(toString(ComponentType::VENDOR));
    static const std::string* const kThirdPartyComponent =
            new std::string(toString(ComponentType::THIRD_PARTY));
    if (EqualsIgnoreCase(componentTypeStr, *kSystemComponent)) {
        return ComponentType::SYSTEM;
    } else if (EqualsIgnoreCase(componentTypeStr, *kVendorComponent)) {
        return ComponentType::VENDOR;
    } else if (EqualsIgnoreCase(componentTypeStr, *kThirdPartyComponent)) {
        return ComponentType::THIRD_PARTY;
    }
    return Error() << "Must specify valid component type. Received " << componentTypeStr;
}

Result<std::vector<std::string>> readSafeToKillPackages(const XMLElement* rootElement) {
    std::vector<std::string> safeToKillPackages;
    for (const XMLElement* outerElement = rootElement->FirstChildElement(kTagSafeToKillPackages);
         outerElement != nullptr;
         outerElement = outerElement->NextSiblingElement(kTagSafeToKillPackages)) {
        for (const XMLElement* innerElement = outerElement->FirstChildElement(kTagPackage);
             innerElement != nullptr;
             innerElement = innerElement->NextSiblingElement(kTagPackage)) {
            std::string packageName;
            if (const auto text = innerElement->GetText(); text == nullptr) {
                return Error() << "Must specify non-empty safe-to-kill package name";
            } else if (packageName = Trim(text); packageName.empty()) {
                return Error() << "Must specify non-empty safe-to-kill package name";
            }
            safeToKillPackages.push_back(std::string(packageName));
        }
    }
    return safeToKillPackages;
}

Result<std::vector<std::string>> readVendorPackagePrefixes(const XMLElement* rootElement) {
    std::vector<std::string> vendorPackagePrefixes;
    for (const XMLElement* outerElement = rootElement->FirstChildElement(kTagVendorPackagePrefixes);
         outerElement != nullptr;
         outerElement = outerElement->NextSiblingElement(kTagVendorPackagePrefixes)) {
        for (const XMLElement* innerElement = outerElement->FirstChildElement(kTagPackagePrefix);
             innerElement != nullptr;
             innerElement = innerElement->NextSiblingElement(kTagPackagePrefix)) {
            std::string packagePrefix;
            if (const auto text = innerElement->GetText(); text == nullptr) {
                return Error() << "Must specify non-empty vendor package prefix";
            } else if (packagePrefix = Trim(text); packagePrefix.empty()) {
                return Error() << "Must specify non-empty vendor package prefix";
            }
            vendorPackagePrefixes.push_back(std::string(packagePrefix));
        }
    }
    return vendorPackagePrefixes;
}

ApplicationCategoryType toApplicationCategoryType(std::string_view value) {
    static const std::string* const kMapsAppCategory =
            new std::string(toString(ApplicationCategoryType::MAPS));
    static const std::string* const kMediaAppCategory =
            new std::string(toString(ApplicationCategoryType::MEDIA));
    if (EqualsIgnoreCase(value, *kMapsAppCategory)) {
        return ApplicationCategoryType::MAPS;
    } else if (EqualsIgnoreCase(value, *kMediaAppCategory)) {
        return ApplicationCategoryType::MEDIA;
    }
    return ApplicationCategoryType::OTHERS;
}

Result<std::vector<PackageMetadata>> readPackageToAppCategoryTypes(const XMLElement* rootElement) {
    std::vector<PackageMetadata> packageMetadata;
    for (const XMLElement* outerElement =
                 rootElement->FirstChildElement(kTagPackageToAppCategoryTypes);
         outerElement != nullptr;
         outerElement = outerElement->NextSiblingElement(kTagPackageToAppCategoryTypes)) {
        for (const XMLElement* innerElement =
                     outerElement->FirstChildElement(kTagPackageAppCategory);
             innerElement != nullptr;
             innerElement = innerElement->NextSiblingElement(kTagPackageAppCategory)) {
            const char* type = nullptr;
            if (innerElement->QueryStringAttribute(kAttrType, &type) != XML_SUCCESS) {
                return Error() << "Failed to read '" << kAttrType << "' attribute in '"
                               << kTagPackageAppCategory << "' tag";
            }
            PackageMetadata meta;
            if (meta.appCategoryType = toApplicationCategoryType(type);
                meta.appCategoryType == ApplicationCategoryType::OTHERS) {
                return Error() << "Must specify valid app category type. Received " << type;
            }
            if (const auto text = innerElement->GetText(); text == nullptr) {
                return Error() << "Must specify non-empty package name";
            } else if (meta.packageName = Trim(text); meta.packageName.empty()) {
                return Error() << "Must specify non-empty package name";
            }
            packageMetadata.push_back(meta);
        }
    }
    return packageMetadata;
}

Result<PerStateBytes> readPerStateBytes(const XMLElement* rootElement) {
    PerStateBytes perStateBytes;
    std::unordered_set<std::string> seenStates;
    for (const XMLElement* childElement = rootElement->FirstChildElement(kTagState);
         childElement != nullptr; childElement = childElement->NextSiblingElement(kTagState)) {
        const char* state = nullptr;
        if (childElement->QueryStringAttribute(kAttrId, &state) != XML_SUCCESS) {
            return Error() << "Failed to read '" << kAttrId << "' attribute in '" << kTagState
                           << "' tag";
        }
        if (seenStates.find(state) != seenStates.end()) {
            return Error() << "Duplicate threshold specified for state '" << state << "'";
        }
        int64_t megaBytes = 0;
        if (const auto text = childElement->GetText(); text == nullptr) {
            return Error() << "Must specify non-empty threshold for state '" << state << "'";
        } else if (const auto megaBytesStr = Trim(text);
                   !ParseInt(megaBytesStr.c_str(), &megaBytes)) {
            return Error() << "Failed to parse threshold for the state '" << state
                           << "': Received threshold value '" << megaBytesStr << "'";
        }
        if (!strcmp(state, kStateIdForegroundMode)) {
            seenStates.insert(kStateIdForegroundMode);
            perStateBytes.foregroundBytes = megaBytes * kOneMegaByte;
        } else if (!strcmp(state, kStateIdBackgroundMode)) {
            seenStates.insert(kStateIdBackgroundMode);
            perStateBytes.backgroundBytes = megaBytes * kOneMegaByte;
        } else if (!strcmp(state, kStateIdGarageMode)) {
            seenStates.insert(kStateIdGarageMode);
            perStateBytes.garageModeBytes = megaBytes * kOneMegaByte;
        } else {
            return Error() << "Invalid state '" << state << "' in per-state bytes";
        }
    }
    if (seenStates.size() != kNumStates) {
        return Error() << "Thresholds not specified for all states. Specified only for ["
                       << Join(seenStates, ", ") << "] states";
    }
    return perStateBytes;
}

Result<PerStateIoOveruseThreshold> readComponentLevelThresholds(ComponentType componentType,
                                                                const XMLElement* rootElement) {
    const XMLElement* componentLevelThresholdElement = nullptr;
    if (const auto result = readExactlyOneElement(kTagComponentLevelThresholds, rootElement);
        result.ok()) {
        componentLevelThresholdElement = *result;
    } else {
        return Error() << "Failed to read tag '" << kTagComponentLevelThresholds
                       << "': " << result.error();
    }
    PerStateIoOveruseThreshold thresholds;
    thresholds.name = toString(componentType);
    if (const auto result = readPerStateBytes(componentLevelThresholdElement); result.ok()) {
        thresholds.perStateWriteBytes = *result;
    } else {
        return Error() << "Failed to read component level thresholds for component '"
                       << thresholds.name << "': " << result.error();
    }
    return thresholds;
}

Result<std::vector<PerStateIoOveruseThreshold>> readPerStateThresholds(
        const XMLElement* rootElement) {
    std::vector<PerStateIoOveruseThreshold> thresholds;
    for (const XMLElement* childElement = rootElement->FirstChildElement(kTagPerStateThreshold);
         childElement != nullptr;
         childElement = childElement->NextSiblingElement(kTagPerStateThreshold)) {
        PerStateIoOveruseThreshold threshold;
        if (const char* name = nullptr;
            childElement->QueryStringAttribute(kAttrId, &name) != XML_SUCCESS) {
            return Error() << "Failed to read '" << kAttrId << "' attribute";
        } else if (threshold.name = name; threshold.name.empty()) {
            return Error() << "Must provide non-empty value in '" << kAttrId << "' attribute";
        }
        if (const auto result = readPerStateBytes(childElement); result.ok()) {
            threshold.perStateWriteBytes = *result;
        } else {
            return Error() << "Failed to read thresholds for id '" << threshold.name
                           << "': " << result.error();
        }
        thresholds.push_back(threshold);
    }
    return thresholds;
}

Result<std::vector<PerStateIoOveruseThreshold>> readPackageSpecificThresholds(
        const XMLElement* rootElement) {
    std::vector<PerStateIoOveruseThreshold> thresholds;
    for (const XMLElement* childElement =
                 rootElement->FirstChildElement(kTagPackageSpecificThresholds);
         childElement != nullptr;
         childElement = childElement->NextSiblingElement(kTagPackageSpecificThresholds)) {
        if (const auto result = readPerStateThresholds(childElement); result.ok()) {
            thresholds.insert(thresholds.end(), result->begin(), result->end());
        } else {
            return Error() << "Failed to read package specific thresholds from tag'"
                           << kTagPackageSpecificThresholds << "': " << result.error();
        }
    }
    return thresholds;
}

Result<std::vector<PerStateIoOveruseThreshold>> readAppCategorySpecificThresholds(
        const XMLElement* rootElement) {
    std::vector<PerStateIoOveruseThreshold> thresholds;
    for (const XMLElement* childElement =
                 rootElement->FirstChildElement(kTagAppCategorySpecificThresholds);
         childElement != nullptr;
         childElement = childElement->NextSiblingElement(kTagAppCategorySpecificThresholds)) {
        if (const auto result = readPerStateThresholds(childElement); result.ok()) {
            thresholds.insert(thresholds.end(), result->begin(), result->end());
        } else {
            return Error() << "Failed to read app category specific thresholds from tag'"
                           << kTagAppCategorySpecificThresholds << "': " << result.error();
        }
    }
    return thresholds;
}

Result<IoOveruseAlertThreshold> readIoOveruseAlertThreshold(const XMLElement* rootElement) {
    IoOveruseAlertThreshold alertThreshold;
    std::unordered_set<std::string> seenParams;
    for (const XMLElement* childElement = rootElement->FirstChildElement(kTagParam);
         childElement != nullptr; childElement = childElement->NextSiblingElement(kTagParam)) {
        const char* param = nullptr;
        if (childElement->QueryStringAttribute(kAttrId, &param) != XML_SUCCESS) {
            return Error() << "Failed to read '" << kAttrId << "' attribute in '" << kTagParam
                           << "' tag";
        }
        if (seenParams.find(param) != seenParams.end()) {
            return Error() << "Duplicate threshold specified for param '" << param << "'";
        }
        int64_t value = 0;
        if (const auto text = childElement->GetText(); text == nullptr) {
            return Error() << "Must specify non-empty threshold for param '" << param << "'";
        } else if (const auto valueStr = Trim(text); !ParseInt(valueStr.c_str(), &value)) {
            return Error() << "Failed to parse threshold for the param '" << param
                           << "': Received threshold value '" << valueStr << "'";
        }
        if (!strcmp(param, kParamIdDurationSeconds)) {
            seenParams.insert(kParamIdDurationSeconds);
            alertThreshold.durationInSeconds = value;
        } else if (!strcmp(param, kParamIdWrittenBytesPerSecond)) {
            seenParams.insert(kParamIdWrittenBytesPerSecond);
            alertThreshold.writtenBytesPerSecond = value;
        } else {
            return Error() << "Invalid param '" << param << "' in I/O overuse alert thresholds";
        }
    }
    if (seenParams.size() != kNumParams) {
        return Error() << "Thresholds not specified for all params. Specified only for ["
                       << Join(seenParams, ", ") << "] params";
    }
    return alertThreshold;
}

Result<std::vector<IoOveruseAlertThreshold>> readSystemWideThresholds(
        const XMLElement* rootElement) {
    std::vector<IoOveruseAlertThreshold> alertThresholds;
    for (const XMLElement* outerElement = rootElement->FirstChildElement(kTagSystemWideThresholds);
         outerElement != nullptr;
         outerElement = outerElement->NextSiblingElement(kTagSystemWideThresholds)) {
        for (const XMLElement* innerElement = outerElement->FirstChildElement(kTagAlertThreshold);
             innerElement != nullptr;
             innerElement = innerElement->NextSiblingElement(kTagAlertThreshold)) {
            const auto result = readIoOveruseAlertThreshold(innerElement);
            if (!result.ok()) {
                return Error() << "Failed to system wide thresholds from tag '"
                               << kTagAlertThreshold << "': " << result.error();
            }
            alertThresholds.push_back(*result);
        }
    }
    return alertThresholds;
}

Result<IoOveruseConfiguration> readIoOveruseConfiguration(ComponentType componentType,
                                                          const XMLElement* rootElement) {
    const XMLElement* childElement = nullptr;
    if (const auto result = readExactlyOneElement(kTagIoOveruseConfiguration, rootElement);
        result.ok()) {
        childElement = *result;
    } else {
        return Error() << "Failed to read tag '" << kTagIoOveruseConfiguration
                       << "': " << result.error();
    }
    IoOveruseConfiguration configuration;
    if (const auto result = readComponentLevelThresholds(componentType, childElement);
        result.ok()) {
        configuration.componentLevelThresholds = *result;
    } else {
        return Error() << "Failed to read component-level thresholds: " << result.error();
    }
    if (const auto result = readPackageSpecificThresholds(childElement); result.ok()) {
        configuration.packageSpecificThresholds = *result;
    } else {
        return Error() << "Failed to read package specific thresholds: " << result.error();
    }
    if (const auto result = readAppCategorySpecificThresholds(childElement); result.ok()) {
        configuration.categorySpecificThresholds = *result;
    } else {
        return Error() << "Failed to read category specific thresholds: " << result.error();
    }
    if (const auto result = readSystemWideThresholds(childElement); result.ok()) {
        configuration.systemWideThresholds = *result;
    } else {
        return Error() << "Failed to read system-wide thresholds: " << result.error();
    }
    return configuration;
}

Result<void> writeComponentType(ComponentType componentType, XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagComponentType);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '" << kTagComponentType
                       << "'";
    }
    childElement->SetText(toString(componentType).c_str());
    return {};
}

Result<void> writeSafeToKillPackages(const std::vector<std::string>& safeToKillPackages,
                                     XMLElement* rootElement) {
    if (safeToKillPackages.empty()) {
        return {};
    }
    XMLElement* outerElement = rootElement->InsertNewChildElement(kTagSafeToKillPackages);
    if (!outerElement) {
        return Error() << "Failed to insert new child element with tag '" << kTagSafeToKillPackages
                       << "'";
    }
    for (const auto& package : safeToKillPackages) {
        XMLElement* innerElement = outerElement->InsertNewChildElement(kTagPackage);
        if (!innerElement) {
            return Error() << "Failed to insert new child element with tag '" << kTagPackage << "'";
        }
        innerElement->SetText(package.c_str());
    }
    return {};
}

Result<void> writeVendorPackagePrefixes(const std::vector<std::string>& vendorPackagePrefixes,
                                        XMLElement* rootElement) {
    if (vendorPackagePrefixes.empty()) {
        return {};
    }
    XMLElement* outerElement = rootElement->InsertNewChildElement(kTagVendorPackagePrefixes);
    if (!outerElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagVendorPackagePrefixes << "'";
    }
    for (const auto& packagePrefix : vendorPackagePrefixes) {
        XMLElement* innerElement = outerElement->InsertNewChildElement(kTagPackagePrefix);
        if (!innerElement) {
            return Error() << "Failed to insert new child element with tag '" << kTagPackagePrefix
                           << "'";
        }
        innerElement->SetText(packagePrefix.c_str());
    }
    return {};
}

Result<void> writePackageToAppCategoryTypes(const std::vector<PackageMetadata>& packageMetadata,
                                            XMLElement* rootElement) {
    if (packageMetadata.empty()) {
        return {};
    }
    XMLElement* outerElement = rootElement->InsertNewChildElement(kTagPackageToAppCategoryTypes);
    if (!outerElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagPackageToAppCategoryTypes << "'";
    }
    for (const auto& meta : packageMetadata) {
        XMLElement* innerElement = outerElement->InsertNewChildElement(kTagPackageAppCategory);
        if (!innerElement) {
            return Error() << "Failed to insert new child element with tag '"
                           << kTagPackageAppCategory << "'";
        }
        innerElement->SetAttribute(kAttrType, toString(meta.appCategoryType).c_str());
        innerElement->SetText(meta.packageName.c_str());
    }
    return {};
}

Result<void> writePerStateBytes(const PerStateBytes& perStateBytes, XMLElement* rootElement) {
    const auto writeStateElement = [&](const char* state, int64_t value) -> Result<void> {
        XMLElement* childElement = rootElement->InsertNewChildElement(kTagState);
        if (!childElement) {
            return Error() << "Failed to insert new child element with tag '" << kTagState << "'";
        }
        childElement->SetAttribute(kAttrId, state);
        childElement->SetText(value);
        return {};
    };
    if (const auto result = writeStateElement(kStateIdForegroundMode,
                                              perStateBytes.foregroundBytes / kOneMegaByte);
        !result.ok()) {
        return Error() << "Failed to write bytes for state '" << kStateIdForegroundMode
                       << "': " << result.error();
    }
    if (const auto result = writeStateElement(kStateIdBackgroundMode,
                                              perStateBytes.backgroundBytes / kOneMegaByte);
        !result.ok()) {
        return Error() << "Failed to write bytes for state '" << kStateIdBackgroundMode
                       << "': " << result.error();
    }
    if (const auto result =
                writeStateElement(kStateIdGarageMode, perStateBytes.garageModeBytes / kOneMegaByte);
        !result.ok()) {
        return Error() << "Failed to write bytes for state '" << kStateIdGarageMode
                       << "': " << result.error();
    }
    return {};
}

Result<void> writeComponentLevelThresholds(const PerStateIoOveruseThreshold& thresholds,
                                           XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagComponentLevelThresholds);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagComponentLevelThresholds << "'";
    }
    if (const auto result = writePerStateBytes(thresholds.perStateWriteBytes, childElement);
        !result.ok()) {
        return Error() << "Failed to write per-state bytes: " << result.error();
    }
    return {};
}

Result<void> writePerStateThresholds(const PerStateIoOveruseThreshold& thresholds,
                                     XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagPerStateThreshold);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '" << kTagPerStateThreshold
                       << "'";
    }
    childElement->SetAttribute(kAttrId, thresholds.name.c_str());
    if (const auto result = writePerStateBytes(thresholds.perStateWriteBytes, childElement);
        !result.ok()) {
        return Error() << "Failed to write per-state bytes: " << result.error();
    }
    return {};
}

Result<void> writePackageSpecificThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds, XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagPackageSpecificThresholds);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagPackageSpecificThresholds << "'";
    }
    for (const auto threshold : thresholds) {
        if (const auto result = writePerStateThresholds(threshold, childElement); !result.ok()) {
            return Error() << "Failed to write per-state thresholds for '" << threshold.name
                           << "': " << result.error();
        }
    }
    return {};
}

Result<void> writeAppCategorySpecificThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds, XMLElement* rootElement) {
    XMLElement* childElement =
            rootElement->InsertNewChildElement(kTagAppCategorySpecificThresholds);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagAppCategorySpecificThresholds << "'";
    }
    for (const auto threshold : thresholds) {
        if (const auto result = writePerStateThresholds(threshold, childElement); !result.ok()) {
            return Error() << "Failed to write per-state thresholds for '" << threshold.name
                           << "': " << result.error();
        }
    }
    return {};
}

Result<void> writeAlertThresholds(const IoOveruseAlertThreshold& alertThresholds,
                                  XMLElement* rootElement) {
    XMLElement* outerElement = rootElement->InsertNewChildElement(kTagAlertThreshold);
    if (!outerElement) {
        return Error() << "Failed to insert new child element with tag '" << kTagAlertThreshold
                       << "'";
    }
    const auto writeParamElement = [&](const char* param, int64_t value) -> Result<void> {
        XMLElement* innerElement = outerElement->InsertNewChildElement(kTagParam);
        if (!innerElement) {
            return Error() << "Failed to insert new child element with tag '" << kTagParam << "'";
        }
        innerElement->SetAttribute(kAttrId, param);
        innerElement->SetText(value);
        return {};
    };
    if (const auto result =
                writeParamElement(kParamIdDurationSeconds, alertThresholds.durationInSeconds);
        !result.ok()) {
        return Error() << "Failed to write duration for param '" << kParamIdDurationSeconds
                       << "': " << result.error();
    }
    if (const auto result = writeParamElement(kParamIdWrittenBytesPerSecond,
                                              alertThresholds.writtenBytesPerSecond);
        !result.ok()) {
        return Error() << "Failed to write bps for param '" << kParamIdWrittenBytesPerSecond
                       << "': " << result.error();
    }
    return {};
}

Result<void> writeSystemWideThresholds(const std::vector<IoOveruseAlertThreshold>& thresholds,
                                       XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagSystemWideThresholds);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagSystemWideThresholds << "'";
    }
    for (const auto threshold : thresholds) {
        if (const auto result = writeAlertThresholds(threshold, childElement); !result.ok()) {
            return Error() << "Failed to write I/O overuse alert thresholds:" << result.error();
        }
    }
    return {};
}

Result<void> writeIoOveruseConfiguration(const IoOveruseConfiguration& configuration,
                                         XMLElement* rootElement) {
    XMLElement* childElement = rootElement->InsertNewChildElement(kTagIoOveruseConfiguration);
    if (!childElement) {
        return Error() << "Failed to insert new child element with tag '"
                       << kTagIoOveruseConfiguration << "'";
    }
    if (const auto result =
                writeComponentLevelThresholds(configuration.componentLevelThresholds, childElement);
        !result.ok()) {
        return Error() << "Failed to write component-wide thresholds: " << result.error();
    }
    if (const auto result = writePackageSpecificThresholds(configuration.packageSpecificThresholds,
                                                           childElement);
        !result.ok()) {
        return Error() << "Failed to write package specific thresholds: " << result.error();
    }
    if (const auto result =
                writeAppCategorySpecificThresholds(configuration.categorySpecificThresholds,
                                                   childElement);
        !result.ok()) {
        return Error() << "Failed to write app category specific thresholds: " << result.error();
    }
    if (const auto result =
                writeSystemWideThresholds(configuration.systemWideThresholds, childElement);
        !result.ok()) {
        return Error() << "Failed to write system-wide thresholds: " << result.error();
    }
    return {};
}

}  // namespace

Result<ResourceOveruseConfiguration> OveruseConfigurationXmlHelper::parseXmlFile(
        const char* filePath) {
    XMLDocument xmlDoc;
    xmlDoc.LoadFile(filePath);
    if (xmlDoc.ErrorID() != XML_SUCCESS) {
        return Error() << "Failed to read and/or parse '" << filePath << "'";
    }
    ResourceOveruseConfiguration configuration;
    const XMLElement* rootElement = xmlDoc.RootElement();
    if (!rootElement || strcmp(rootElement->Name(), kTagResourceOveruseConfiguration)) {
        return Error() << "XML file doesn't have the root element '"
                       << kTagResourceOveruseConfiguration << "'";
    }
    if (const auto result = readComponentType(rootElement); result.ok()) {
        configuration.componentType = *result;
    } else {
        return Error() << "Failed to read component type: " << result.error();
    }
    if (const auto result = readSafeToKillPackages(rootElement); result.ok()) {
        configuration.safeToKillPackages = *result;
    } else {
        return Error() << "Failed to read safe-to-kill packages: " << result.error();
    }
    if (const auto result = readVendorPackagePrefixes(rootElement); result.ok()) {
        configuration.vendorPackagePrefixes = *result;
    } else {
        return Error() << "Failed to read vendor package prefixes: " << result.error();
    }
    if (const auto result = readPackageToAppCategoryTypes(rootElement); result.ok()) {
        configuration.packageMetadata = *result;
    } else {
        return Error() << "Failed to read package to app category types: " << result.error();
    }
    if (const auto result = readIoOveruseConfiguration(configuration.componentType, rootElement);
        result.ok()) {
        configuration.resourceSpecificConfigurations.emplace_back(
                ResourceSpecificConfiguration(*result));
    } else {
        return Error() << "Failed to read I/O overuse configuration: " << result.error();
    }
    return configuration;
}

Result<void> OveruseConfigurationXmlHelper::writeXmlFile(
        const ResourceOveruseConfiguration& configuration, const char* filePath) {
    XMLDocument xmlDoc;
    if (XMLDeclaration* declaration = xmlDoc.NewDeclaration(); declaration) {
        xmlDoc.InsertEndChild(declaration);
    } else {
        return Error() << "Failed to create new xml declaration";
    }
    XMLElement* rootElement = xmlDoc.NewElement(kTagResourceOveruseConfiguration);
    if (!rootElement) {
        return Error() << "Failed to create new xml element for tag '"
                       << kTagResourceOveruseConfiguration << "'";
    }
    rootElement->SetAttribute(kAttrVersion, kVersionNumber);
    xmlDoc.InsertEndChild(rootElement);
    if (const auto result = writeComponentType(configuration.componentType, rootElement);
        !result.ok()) {
        return Error() << "Failed to write component type: " << result.error();
    }
    if (const auto result = writeSafeToKillPackages(configuration.safeToKillPackages, rootElement);
        !result.ok()) {
        return Error() << "Failed to write safe-to-kill packages: " << result.error();
    }
    if (const auto result =
                writeVendorPackagePrefixes(configuration.vendorPackagePrefixes, rootElement);
        !result.ok()) {
        return Error() << "Failed to write vendor package prefixes: " << result.error();
    }
    if (const auto result =
                writePackageToAppCategoryTypes(configuration.packageMetadata, rootElement);
        !result.ok()) {
        return Error() << "Failed to write package to app category types: " << result.error();
    }
    if (configuration.resourceSpecificConfigurations.size() != 1 ||
        configuration.resourceSpecificConfigurations[0].getTag() !=
                ResourceSpecificConfiguration::ioOveruseConfiguration) {
        return Error() << "Must provide exactly one I/O overuse configuration";
    }
    IoOveruseConfiguration ioOveruseConfig =
            configuration.resourceSpecificConfigurations[0]
                    .get<ResourceSpecificConfiguration::ioOveruseConfiguration>();
    if (const auto result = writeIoOveruseConfiguration(ioOveruseConfig, rootElement);
        !result.ok()) {
        return Error() << "Failed to write I/O overuse configuration: " << result.error();
    }
    if (const auto xmlError = xmlDoc.SaveFile(filePath); xmlError != XML_SUCCESS) {
        return Error() << "Failed to write XML configuration to file '" << filePath
                       << "': " << XMLDocument::ErrorIDToName(xmlError);
    }
    return {};
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
