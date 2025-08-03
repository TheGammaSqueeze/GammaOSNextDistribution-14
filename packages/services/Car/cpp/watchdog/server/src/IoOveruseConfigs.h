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

#ifndef CPP_WATCHDOG_SERVER_SRC_IOOVERUSECONFIGS_H_
#define CPP_WATCHDOG_SERVER_SRC_IOOVERUSECONFIGS_H_

#include <aidl/android/automotive/watchdog/PerStateBytes.h>
#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/IoOveruseAlertThreshold.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <aidl/android/automotive/watchdog/internal/PerStateIoOveruseThreshold.h>
#include <aidl/android/automotive/watchdog/internal/ResourceOveruseConfiguration.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <utils/RefBase.h>

#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {
constexpr const char kBuildSystemConfigXmlPath[] =
        "/system/etc/automotive/watchdog/system_resource_overuse_configuration.xml";
constexpr const char kBuildThirdPartyConfigXmlPath[] =
        "/system/etc/automotive/watchdog/third_party_resource_overuse_configuration.xml";
constexpr const char kBuildVendorConfigXmlPath[] =
        "/vendor/etc/automotive/watchdog/resource_overuse_configuration.xml";
constexpr const char kLatestSystemConfigXmlPath[] =
        "/data/system/car/watchdog/system_resource_overuse_configuration.xml";
constexpr const char kLatestVendorConfigXmlPath[] =
        "/data/system/car/watchdog/vendor_resource_overuse_configuration.xml";
constexpr const char kLatestThirdPartyConfigXmlPath[] =
        "/data/system/car/watchdog/third_party_resource_overuse_configuration.xml";
constexpr const char kDefaultThresholdName[] = "default";

inline const aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold
defaultThreshold() {
    aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold threshold;
    threshold.name = kDefaultThresholdName;
    threshold.perStateWriteBytes.foregroundBytes = std::numeric_limits<int64_t>::max();
    threshold.perStateWriteBytes.backgroundBytes = std::numeric_limits<int64_t>::max();
    threshold.perStateWriteBytes.garageModeBytes = std::numeric_limits<int64_t>::max();
    return threshold;
}

// Forward declaration for testing use only.
namespace internal {

class IoOveruseConfigsPeer;

}  // namespace internal

/**
 * Defines the methods that the I/O overuse configs module should implement.
 */
class IoOveruseConfigsInterface : virtual public android::RefBase {
public:
    // Overwrites the existing configurations.
    virtual android::base::Result<void> update(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&
                    configs) = 0;
    // Returns the existing configurations.
    virtual void
    get(std::vector<aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*
                resourceOveruseConfigs) const = 0;

    // Writes the cached configs to disk.
    virtual android::base::Result<void> writeToDisk() = 0;

    /**
     * Returns the list of vendor package prefixes. Any pre-installed package matching one of these
     * prefixes should be classified as a vendor package.
     */
    virtual const std::unordered_set<std::string>& vendorPackagePrefixes() = 0;

    /**
     * Returns the package names to application category mappings.
     */
    virtual const std::unordered_map<
            std::string, aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&
    packagesToAppCategories() = 0;

    // Fetches the I/O overuse thresholds for the given package.
    virtual aidl::android::automotive::watchdog::PerStateBytes fetchThreshold(
            const aidl::android::automotive::watchdog::internal::PackageInfo& packageInfo)
            const = 0;

    // Returns whether or not the package is safe to kill on I/O overuse.
    virtual bool isSafeToKill(const aidl::android::automotive::watchdog::internal::PackageInfo&
                                      packageInfo) const = 0;

    struct AlertThresholdHashByDuration {
    public:
        size_t operator()(
                const aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold&
                        threshold) const;
    };

    struct AlertThresholdEqualByDuration {
    public:
        bool operator()(
                const aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold& l,
                const aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold& r)
                const;
    };

    using IoOveruseAlertThresholdSet = ::std::unordered_set<
            aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold,
            AlertThresholdHashByDuration, AlertThresholdEqualByDuration>;

    // Returns system-wide disk I/O overuse thresholds.
    virtual const IoOveruseAlertThresholdSet& systemWideAlertThresholds() = 0;
};

class IoOveruseConfigs;

/**
 * ComponentSpecificConfig represents the I/O overuse config defined per component.
 */
class ComponentSpecificConfig final {
protected:
    ComponentSpecificConfig() : mGeneric(defaultThreshold()) {}

    ~ComponentSpecificConfig() {
        mPerPackageThresholds.clear();
        mSafeToKillPackages.clear();
    }

    /**
     * Updates |mPerPackageThresholds|.
     */
    android::base::Result<void> updatePerPackageThresholds(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold>&
                    thresholds,
            const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes);
    /**
     * Updates |mSafeToKillPackages|.
     */
    android::base::Result<void> updateSafeToKillPackages(
            const std::vector<std::string>& packages,
            const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes);

    /**
     * I/O overuse configurations for all packages under the component that are not covered by
     * |mPerPackageThresholds| or |IoOveruseConfigs.mPerCategoryThresholds|.
     */
    aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold mGeneric;
    /**
     * I/O overuse configurations for specific packages under the component.
     */
    std::unordered_map<std::string,
                       aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold>
            mPerPackageThresholds;
    /**
     * List of safe to kill packages under the component in the event of I/O overuse.
     */
    std::unordered_set<std::string> mSafeToKillPackages;

private:
    friend class IoOveruseConfigs;
};

/**
 * IoOveruseConfigs represents the I/O overuse configuration defined by system and vendor
 * applications. This class is not thread safe for performance purposes. The caller is responsible
 * for calling the methods in a thread safe manner.
 */
class IoOveruseConfigs final : public IoOveruseConfigsInterface {
public:
    IoOveruseConfigs();
    ~IoOveruseConfigs() {
        mPerCategoryThresholds.clear();
        mVendorPackagePrefixes.clear();
        mAlertThresholds.clear();
    }

    android::base::Result<void> update(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&
                    configs) override;

    void
    get(std::vector<aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*
                resourceOveruseConfigs) const override;

    android::base::Result<void> writeToDisk();

    aidl::android::automotive::watchdog::PerStateBytes fetchThreshold(
            const aidl::android::automotive::watchdog::internal::PackageInfo& packageInfo)
            const override;

    bool isSafeToKill(const aidl::android::automotive::watchdog::internal::PackageInfo& packageInfo)
            const override;

    const IoOveruseAlertThresholdSet& systemWideAlertThresholds() override {
        return mAlertThresholds;
    }

    const std::unordered_set<std::string>& vendorPackagePrefixes() override {
        return mVendorPackagePrefixes;
    }

    const std::unordered_map<
            std::string, aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&
    packagesToAppCategories() override {
        return mPackagesToAppCategories;
    }

private:
    enum ConfigUpdateMode {
        OVERWRITE = 0,
        MERGE,
        NO_UPDATE,
    };
    android::base::Result<void> updateFromXml(const char* filename);

    void updateFromAidlConfig(
            const aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration&
                    resourceOveruseConfig);

    android::base::Result<void> update(
            const aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration&
                    resourceOveruseConfiguration,
            const aidl::android::automotive::watchdog::internal::IoOveruseConfiguration&
                    ioOveruseConfiguration,
            int32_t updatableConfigsFilter, ComponentSpecificConfig* targetComponentConfig);

    android::base::Result<void> updatePerCategoryThresholds(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold>&
                    thresholds);
    android::base::Result<void> updateAlertThresholds(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::IoOveruseAlertThreshold>&
                    thresholds);

    std::optional<aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration> get(
            const ComponentSpecificConfig& componentSpecificConfig,
            const int32_t componentFilter) const;

    // System component specific configuration.
    ComponentSpecificConfig mSystemConfig;
    // Vendor component specific configuration.
    ComponentSpecificConfig mVendorConfig;
    // Third-party component specific configuration.
    ComponentSpecificConfig mThirdPartyConfig;
    // Package name to application category mappings.
    std::unordered_map<std::string,
                       aidl::android::automotive::watchdog::internal::ApplicationCategoryType>
            mPackagesToAppCategories;
    ConfigUpdateMode mPackagesToAppCategoryMappingUpdateMode;
    // I/O overuse thresholds per category.
    std::unordered_map<aidl::android::automotive::watchdog::internal::ApplicationCategoryType,
                       aidl::android::automotive::watchdog::internal::PerStateIoOveruseThreshold>
            mPerCategoryThresholds;
    // List of vendor package prefixes.
    std::unordered_set<std::string> mVendorPackagePrefixes;
    // System-wide disk I/O overuse alert thresholds.
    IoOveruseAlertThresholdSet mAlertThresholds;

    // For unit tests.
    using ParseXmlFileFunction = std::function<android::base::Result<
            aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>(
            const char*)>;
    using WriteXmlFileFunction = std::function<
            android::base::Result<void>(const aidl::android::automotive::watchdog::internal::
                                                ResourceOveruseConfiguration&,
                                        const char*)>;
    static ParseXmlFileFunction sParseXmlFile;
    static WriteXmlFileFunction sWriteXmlFile;

    friend class internal::IoOveruseConfigsPeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_IOOVERUSECONFIGS_H_
