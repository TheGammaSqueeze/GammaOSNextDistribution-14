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

#include <stdlib.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>

#include <aidl/metadata.h>
#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <libvts_vintf_test_common/common.h>
#include <vintf/AssembleVintf.h>
#include <vintf/KernelConfigParser.h>
#include <vintf/parse_string.h>
#include <vintf/parse_xml.h>
#include "constants-private.h"
#include "utils.h"

#define BUFFER_SIZE sysconf(_SC_PAGESIZE)

namespace android {
namespace vintf {

static const std::string gConfigPrefix = "android-base-";
static const std::string gConfigSuffix = ".config";
static const std::string gBaseConfig = "android-base.config";

// An input stream with a name.
// The input stream may be an actual file, or a stringstream for testing.
// It takes ownership on the istream.
class NamedIstream {
   public:
    NamedIstream() = default;
    NamedIstream(const std::string& name, std::unique_ptr<std::istream>&& stream)
        : mName(name), mStream(std::move(stream)) {}
    const std::string& name() const { return mName; }
    std::istream& stream() { return *mStream; }
    bool hasStream() { return mStream != nullptr; }

   private:
    std::string mName;
    std::unique_ptr<std::istream> mStream;
};

/**
 * Slurps the device manifest file and add build time flag to it.
 */
class AssembleVintfImpl : public AssembleVintf {
    using Condition = std::unique_ptr<KernelConfig>;
    using ConditionedConfig = std::pair<Condition, std::vector<KernelConfig> /* configs */>;

   public:
    void setFakeAidlMetadata(const std::vector<AidlInterfaceMetadata>& metadata) override {
        mFakeAidlMetadata = metadata;
    }

    std::vector<AidlInterfaceMetadata> getAidlMetadata() const {
        if (!mFakeAidlMetadata.empty()) {
            return mFakeAidlMetadata;
        } else {
            return AidlInterfaceMetadata::all();
        }
    }

    void setFakeAidlUseUnfrozen(const std::optional<bool>& use) override {
        mFakeAidlUseUnfrozen = use;
    }

    bool getAidlUseUnfrozen() const {
        if (mFakeAidlUseUnfrozen.has_value()) {
            return *mFakeAidlUseUnfrozen;
        } else {
#ifdef AIDL_USE_UNFROZEN
            return true;
#else
            return false;
#endif
        }
    }

    void setFakeEnv(const std::string& key, const std::string& value) { mFakeEnv[key] = value; }

    std::string getEnv(const std::string& key) const {
        auto it = mFakeEnv.find(key);
        if (it != mFakeEnv.end()) {
            return it->second;
        }
        const char* envValue = getenv(key.c_str());
        return envValue != nullptr ? std::string(envValue) : std::string();
    }

    // Get environment variable and split with space.
    std::vector<std::string> getEnvList(const std::string& key) const {
        std::vector<std::string> ret;
        for (auto&& v : base::Split(getEnv(key), " ")) {
            v = base::Trim(v);
            if (!v.empty()) {
                ret.push_back(v);
            }
        }
        return ret;
    }

    template <typename T>
    bool getFlag(const std::string& key, T* value, bool log = true) const {
        std::string envValue = getEnv(key);
        if (envValue.empty()) {
            if (log) {
                err() << "Warning: " << key << " is missing, defaulted to " << (*value) << "."
                      << std::endl;
            }
            return true;
        }

        if (!parse(envValue, value)) {
            err() << "Cannot parse " << envValue << "." << std::endl;
            return false;
        }
        return true;
    }

    /**
     * Set *out to environment variable only if *out is default constructed.
     * Return false if a fatal error has occurred:
     * - The environment variable has an unknown format
     * - The value of the environment variable does not match a predefined variable in the files
     */
    template <typename T>
    bool getFlagIfUnset(const std::string& envKey, T* out) const {
        bool hasExistingValue = !(*out == T{});

        bool hasEnvValue = false;
        T envValue;
        std::string envStrValue = getEnv(envKey);
        if (!envStrValue.empty()) {
            if (!parse(envStrValue, &envValue)) {
                err() << "Cannot parse " << envValue << "." << std::endl;
                return false;
            }
            hasEnvValue = true;
        }

        if (hasExistingValue) {
            if (hasEnvValue && (*out != envValue)) {
                err() << "Cannot override existing value " << *out << " with " << envKey
                      << " (which is " << envValue << ")." << std::endl;
                return false;
            }
            return true;
        }
        if (hasEnvValue) {
            *out = envValue;
        }
        return true;
    }

    bool getBooleanFlag(const std::string& key) const { return getEnv(key) == std::string("true"); }

    size_t getIntegerFlag(const std::string& key, size_t defaultValue = 0) const {
        std::string envValue = getEnv(key);
        if (envValue.empty()) {
            return defaultValue;
        }
        size_t value;
        if (!base::ParseUint(envValue, &value)) {
            err() << "Error: " << key << " must be a number." << std::endl;
            return defaultValue;
        }
        return value;
    }

    static std::string read(std::basic_istream<char>& is) {
        std::stringstream ss;
        ss << is.rdbuf();
        return ss.str();
    }

    // Return true if name of file is "android-base.config". This file must be specified
    // exactly once for each kernel version. These requirements do not have any conditions.
    static bool isCommonConfig(const std::string& path) {
        return ::android::base::Basename(path) == gBaseConfig;
    }

    // Return true if name of file matches "android-base-foo.config".
    // Zero or more conditional configs may be specified for each kernel version. These
    // requirements are conditional on CONFIG_FOO=y.
    static bool isConditionalConfig(const std::string& path) {
        auto fname = ::android::base::Basename(path);
        return ::android::base::StartsWith(fname, gConfigPrefix) &&
               ::android::base::EndsWith(fname, gConfigSuffix);
    }

    // Return true for all other file names (i.e. not android-base.config, and not conditional
    // configs.)
    // Zero or more conditional configs may be specified for each kernel version.
    // These requirements do not have any conditions.
    static bool isExtraCommonConfig(const std::string& path) {
        return !isCommonConfig(path) && !isConditionalConfig(path);
    }

    // nullptr on any error, otherwise the condition.
    Condition generateCondition(const std::string& path) {
        if (!isConditionalConfig(path)) {
            return nullptr;
        }
        auto fname = ::android::base::Basename(path);
        std::string sub = fname.substr(gConfigPrefix.size(),
                                       fname.size() - gConfigPrefix.size() - gConfigSuffix.size());
        if (sub.empty()) {
            return nullptr;  // should not happen
        }
        for (size_t i = 0; i < sub.size(); ++i) {
            if (sub[i] == '-') {
                sub[i] = '_';
                continue;
            }
            if (isalnum(sub[i])) {
                sub[i] = toupper(sub[i]);
                continue;
            }
            err() << "'" << fname << "' (in " << path
                  << ") is not a valid kernel config file name. Must match regex: "
                  << "android-base(-[0-9a-zA-Z-]+)?\\" << gConfigSuffix << std::endl;
            return nullptr;
        }
        sub.insert(0, "CONFIG_");
        return std::make_unique<KernelConfig>(std::move(sub), Tristate::YES);
    }

    bool parseFileForKernelConfigs(std::basic_istream<char>& stream,
                                   std::vector<KernelConfig>* out) {
        KernelConfigParser parser(true /* processComments */, true /* relaxedFormat */);
        status_t status = parser.processAndFinish(read(stream));
        if (status != OK) {
            err() << parser.error();
            return false;
        }

        for (auto& configPair : parser.configs()) {
            out->push_back({});
            KernelConfig& config = out->back();
            config.first = std::move(configPair.first);
            if (!parseKernelConfigTypedValue(configPair.second, &config.second)) {
                err() << "Unknown value type for key = '" << config.first << "', value = '"
                      << configPair.second << "'\n";
                return false;
            }
        }
        return true;
    }

    bool parseFilesForKernelConfigs(std::vector<NamedIstream>* streams,
                                    std::vector<ConditionedConfig>* out) {
        out->clear();
        ConditionedConfig commonConfig;
        bool foundCommonConfig = false;
        bool ret = true;

        for (auto& namedStream : *streams) {
            if (isCommonConfig(namedStream.name()) || isExtraCommonConfig(namedStream.name())) {
                if (!parseFileForKernelConfigs(namedStream.stream(), &commonConfig.second)) {
                    err() << "Failed to generate common configs for file " << namedStream.name();
                    ret = false;
                }
                if (isCommonConfig(namedStream.name())) {
                    foundCommonConfig = true;
                }
            } else {
                Condition condition = generateCondition(namedStream.name());
                if (condition == nullptr) {
                    err() << "Failed to generate conditional configs for file "
                          << namedStream.name();
                    ret = false;
                }

                std::vector<KernelConfig> kernelConfigs;
                if ((ret &= parseFileForKernelConfigs(namedStream.stream(), &kernelConfigs)))
                    out->emplace_back(std::move(condition), std::move(kernelConfigs));
            }
        }

        if (!foundCommonConfig) {
            err() << "No " << gBaseConfig << " is found in these paths:" << std::endl;
            for (auto& namedStream : *streams) {
                err() << "    " << namedStream.name() << std::endl;
            }
            ret = false;
        }
        // first element is always common configs (no conditions).
        out->insert(out->begin(), std::move(commonConfig));
        return ret;
    }

    std::basic_ostream<char>& out() const { return mOutRef == nullptr ? std::cout : *mOutRef; }
    std::basic_ostream<char>& err() const override {
        return mErrRef == nullptr ? std::cerr : *mErrRef;
    }

    // If -c is provided, check it.
    bool checkDualFile(const HalManifest& manifest, const CompatibilityMatrix& matrix) {
        if (getBooleanFlag("PRODUCT_ENFORCE_VINTF_MANIFEST")) {
            std::string error;
            if (!manifest.checkCompatibility(matrix, &error, mCheckFlags)) {
                err() << "Not compatible: " << error << std::endl;
                return false;
            }
        }
        return true;
    }

    using HalManifests = std::vector<HalManifest>;
    using CompatibilityMatrices = std::vector<CompatibilityMatrix>;

    template <typename M>
    void outputInputs(const std::vector<M>& inputs) {
        out() << "<!--" << std::endl;
        out() << "    Input:" << std::endl;
        for (const auto& e : inputs) {
            if (!e.fileName().empty()) {
                out() << "        " << e.fileName() << std::endl;
            }
        }
        out() << "-->" << std::endl;
    }

    // Parse --kernel arguments and write to output manifest.
    bool setDeviceManifestKernel(HalManifest* manifest) {
        if (mKernels.empty()) {
            return true;
        }
        if (mKernels.size() > 1) {
            err() << "Warning: multiple --kernel is specified when building device manifest. "
                  << "Only the first one will be used." << std::endl;
        }
        auto& kernelArg = *mKernels.begin();
        const auto& kernelVer = kernelArg.first;
        auto& kernelConfigFiles = kernelArg.second;
        // addKernel() guarantees that !kernelConfigFiles.empty().
        if (kernelConfigFiles.size() > 1) {
            err() << "Warning: multiple config files are specified in --kernel when building "
                  << "device manfiest. Only the first one will be used." << std::endl;
        }

        KernelConfigParser parser(true /* processComments */, false /* relaxedFormat */);
        status_t status = parser.processAndFinish(read(kernelConfigFiles[0].stream()));
        if (status != OK) {
            err() << parser.error();
            return false;
        }

        // Set version and configs in manifest.
        auto kernel_info = std::make_optional<KernelInfo>();
        kernel_info->mVersion = kernelVer;
        kernel_info->mConfigs = parser.configs();
        std::string error;
        if (!manifest->mergeKernel(&kernel_info, &error)) {
            err() << error << "\n";
            return false;
        }
        return true;
    }

    // Check to see if each HAL manifest entry only contains interfaces from the
    // same aidl_interface module by finding the AidlInterfaceMetadata object
    // associated with the interfaces in the manifest entry.
    bool verifyAidlMetadataPerManifestEntry(const HalManifest& halManifest) {
        const std::vector<AidlInterfaceMetadata> aidlMetadata = getAidlMetadata();
        for (const auto& hal : halManifest.getHals()) {
            if (hal.format != HalFormat::AIDL) continue;
            for (const auto& metadata : aidlMetadata) {
                std::map<std::string, bool> isInterfaceInMetadata;
                // get the types of each instance
                hal.forEachInstance([&](const ManifestInstance& instance) -> bool {
                    std::string interfaceName = instance.package() + "." + instance.interface();
                    // check if that instance is covered by this metadata object
                    if (std::find(metadata.types.begin(), metadata.types.end(), interfaceName) !=
                        metadata.types.end()) {
                        isInterfaceInMetadata[interfaceName] = true;
                    } else {
                        isInterfaceInMetadata[interfaceName] = false;
                    }
                    // Keep going through the rest of the instances
                    return true;
                });
                bool found = false;
                if (!isInterfaceInMetadata.empty()) {
                    // Check that all of these entries were found or not
                    // found in this metadata entry.
                    found = isInterfaceInMetadata.begin()->second;
                    if (!std::all_of(
                            isInterfaceInMetadata.begin(), isInterfaceInMetadata.end(),
                            [&](const auto& entry) -> bool { return found == entry.second; })) {
                        err() << "HAL manifest entries must only contain interfaces from the same "
                                 "aidl_interface"
                              << std::endl;
                        for (auto& [interface, isIn] : isInterfaceInMetadata) {
                            if (isIn) {
                                err() << "    " << interface << " is in " << metadata.name
                                      << std::endl;
                            } else {
                                err() << "    "
                                      << interface << " is from another AIDL interface module "
                                      << std::endl;
                            }
                        }
                        return false;
                    }
                }
                // If we found the AidlInterfaceMetadata associated with this
                // HAL, then there is no need to keep looking.
                if (found) break;
            }
        }
        return true;
    }

    // get the first interface name including the package.
    // Example: android.foo.IFoo
    static std::string getFirstInterfaceName(const ManifestHal& manifestHal) {
        std::string interfaceName;
        manifestHal.forEachInstance([&](const ManifestInstance& instance) -> bool {
            interfaceName = instance.package() + "." + instance.interface();
            return false;
        });
        return interfaceName;
    }

    // Check if this HAL is covered by this metadata entry. The name field in
    // AidlInterfaceMetadata is the module name, which isn't the same as the
    // package that would be found in the manifest, so we check all of the types
    // in the metadata.
    // Implementation detail: Returns true if the interface of the first
    // <fqname> is in `aidlMetadata.types`
    static bool isInMetadata(const ManifestHal& manifestHal,
                             const AidlInterfaceMetadata& aidlMetadata) {
        // Get the first interface type. The instance isn't
        // needed to find a matching AidlInterfaceMetadata
        std::string interfaceName = getFirstInterfaceName(manifestHal);
        return std::find(aidlMetadata.types.begin(), aidlMetadata.types.end(), interfaceName) !=
               aidlMetadata.types.end();
    }

    // Set the manifest version for AIDL interfaces to 'version - 1' if the HAL is
    // implementing the latest unfrozen version and the release configuration
    // prevents the use of the unfrozen version.
    // If the AIDL interface has no previous frozen version, then the HAL
    // manifest entry is removed entirely.
    bool setManifestAidlHalVersion(HalManifest* manifest) {
        if (getAidlUseUnfrozen()) {
            // If we are using unfrozen interfaces, then we have no work to do.
            return true;
        }
        const std::vector<AidlInterfaceMetadata> aidlMetadata = getAidlMetadata();
        std::vector<std::string> halsToRemove;
        for (ManifestHal& hal : manifest->getHals()) {
            if (hal.format != HalFormat::AIDL) continue;
            if (hal.versions.size() != 1) {
                err() << "HAL manifest entries must only contain one version of an AIDL HAL but "
                         "found "
                      << hal.versions.size() << " for " << hal.getName() << std::endl;
                return false;
            }
            size_t halVersion = hal.versions.front().minorVer;
            bool foundMetadata = false;
            for (const AidlInterfaceMetadata& metadata : aidlMetadata) {
                if (!isInMetadata(hal, metadata)) continue;
                foundMetadata = true;
                if (!metadata.has_development) continue;
                if (metadata.use_unfrozen) {
                    err() << "INFO: " << hal.getName()
                          << " is explicitly marked to use unfrozen version, so it will not be "
                             "downgraded. If this interface is used, it will fail "
                             "vts_treble_vintf_vendor_test.";
                    continue;
                }

                auto it = std::max_element(metadata.versions.begin(), metadata.versions.end());
                if (it == metadata.versions.end()) {
                    // v1 manifest entries that are declaring unfrozen versions must be removed
                    // from the manifest when the release configuration prevents the use of
                    // unfrozen versions. this ensures service manager will deny registration.
                    halsToRemove.push_back(hal.getName());
                } else {
                    size_t latestVersion = *it;
                    if (latestVersion < halVersion) {
                        if (halVersion - latestVersion != 1) {
                            err()
                                << "The declared version of " << hal.getName() << " (" << halVersion
                                << ") can't be more than one greater than its last frozen version ("
                                << latestVersion << ")." << std::endl;
                            return false;
                        }
                        err() << "INFO: Downgrading HAL " << hal.getName()
                              << " in the manifest from V" << halVersion << " to V"
                              << halVersion - 1
                              << " because it is unfrozen and unfrozen interfaces "
                              << "are not allowed in this release configuration." << std::endl;
                        hal.versions[0] = hal.versions[0].withMinor(halVersion - 1);
                    }
                }
            }
            if (!foundMetadata) {
                // This can happen for prebuilt interfaces from partners that we
                // don't know about. We can ignore them here since the AIDL tool
                // is not going to build the libraries differently anyways.
                err() << "INFO: Couldn't find AIDL metadata for: " << getFirstInterfaceName(hal)
                      << " in file " << hal.fileName() << ". Check spelling? This is expected"
                      << " for prebuilt interfaces." << std::endl;
            }
        }
        for (const auto& name : halsToRemove) {
            // These services should not be installed on the device, but there
            // are cases where the service is also service other HAL interfaces
            // and will remain on the device.
            err() << "INFO: Removing HAL from the manifest because it is declaring V1 of a new "
                     "unfrozen interface which is not allowed in this release configuration: "
                  << name << std::endl;
            manifest->removeHals(name, details::kDefaultAidlVersion.majorVer);
        }
        return true;
    }

    bool checkDeviceManifestNoKernelLevel(const HalManifest& manifest) {
        if (manifest.level() != Level::UNSPECIFIED &&
            manifest.level() >= details::kEnforceDeviceManifestNoKernelLevel &&
            // Use manifest.kernel()->level() directly because inferredKernelLevel()
            // reads manifest.level().
            manifest.kernel().has_value() && manifest.kernel()->level() != Level::UNSPECIFIED) {
            err() << "Error: Device manifest with level " << manifest.level()
                  << " must not set kernel level " << manifest.kernel()->level() << std::endl;
            return false;
        }
        return true;
    }

    bool assembleHalManifest(HalManifests* halManifests) {
        std::string error;
        HalManifest* halManifest = &halManifests->front();
        HalManifest* manifestWithLevel = nullptr;
        if (halManifest->level() != Level::UNSPECIFIED) {
            manifestWithLevel = halManifest;
        }

        for (auto it = halManifests->begin() + 1; it != halManifests->end(); ++it) {
            const std::string& path = it->fileName();
            HalManifest& manifestToAdd = *it;

            if (manifestToAdd.level() != Level::UNSPECIFIED) {
                if (halManifest->level() == Level::UNSPECIFIED) {
                    halManifest->mLevel = manifestToAdd.level();
                    manifestWithLevel = &manifestToAdd;
                } else if (halManifest->level() != manifestToAdd.level()) {
                    err() << "Inconsistent FCM Version in HAL manifests:" << std::endl
                          << "    File '"
                          << (manifestWithLevel ? manifestWithLevel->fileName() : "<unknown>")
                          << "' has level " << halManifest->level() << std::endl
                          << "    File '" << path << "' has level " << manifestToAdd.level()
                          << std::endl;
                    return false;
                }
            }

            if (!halManifest->addAll(&manifestToAdd, &error)) {
                err() << "File \"" << path << "\" cannot be added: " << error << std::endl;
                return false;
            }
        }

        if (halManifest->mType == SchemaType::DEVICE) {
            if (!getFlagIfUnset("BOARD_SEPOLICY_VERS", &halManifest->device.mSepolicyVersion)) {
                return false;
            }

            if (!getBooleanFlag("VINTF_IGNORE_TARGET_FCM_VERSION") &&
                !getBooleanFlag("PRODUCT_ENFORCE_VINTF_MANIFEST")) {
                halManifest->mLevel = Level::LEGACY;
            }

            if (!setDeviceManifestKernel(halManifest)) {
                return false;
            }

            if (!checkDeviceManifestNoKernelLevel(*halManifest)) {
                return false;
            }
        }

        if (halManifest->mType == SchemaType::FRAMEWORK) {
            for (auto&& v : getEnvList("PROVIDED_VNDK_VERSIONS")) {
                halManifest->framework.mVendorNdks.emplace_back(std::move(v));
            }

            for (auto&& v : getEnvList("PLATFORM_SYSTEMSDK_VERSIONS")) {
                halManifest->framework.mSystemSdk.mVersions.emplace(std::move(v));
            }
        }

        if (!verifyAidlMetadataPerManifestEntry(*halManifest)) {
            return false;
        }

        if (!setManifestAidlHalVersion(halManifest)) {
            return false;
        }

        outputInputs(*halManifests);

        if (mOutputMatrix) {
            CompatibilityMatrix generatedMatrix = halManifest->generateCompatibleMatrix();
            if (!halManifest->checkCompatibility(generatedMatrix, &error, mCheckFlags)) {
                err() << "FATAL ERROR: cannot generate a compatible matrix: " << error << std::endl;
            }
            out() << "<!-- \n"
                     "    Autogenerated skeleton compatibility matrix. \n"
                     "    Use with caution. Modify it to suit your needs.\n"
                     "    All HALs are set to optional.\n"
                     "    Many entries other than HALs are zero-filled and\n"
                     "    require human attention. \n"
                     "-->\n"
                  << toXml(generatedMatrix, mSerializeFlags);
        } else {
            out() << toXml(*halManifest, mSerializeFlags);
        }
        out().flush();

        if (mCheckFile.hasStream()) {
            CompatibilityMatrix checkMatrix;
            checkMatrix.setFileName(mCheckFile.name());
            if (!fromXml(&checkMatrix, read(mCheckFile.stream()), &error)) {
                err() << "Cannot parse check file as a compatibility matrix: " << error
                      << std::endl;
                return false;
            }
            if (!checkDualFile(*halManifest, checkMatrix)) {
                return false;
            }
        }

        return true;
    }

    // Parse --kernel arguments and write to output matrix.
    bool assembleFrameworkCompatibilityMatrixKernels(CompatibilityMatrix* matrix) {
        for (auto& pair : mKernels) {
            std::vector<ConditionedConfig> conditionedConfigs;
            if (!parseFilesForKernelConfigs(&pair.second, &conditionedConfigs)) {
                return false;
            }
            for (ConditionedConfig& conditionedConfig : conditionedConfigs) {
                MatrixKernel kernel(KernelVersion{pair.first}, std::move(conditionedConfig.second));
                if (conditionedConfig.first != nullptr)
                    kernel.mConditions.push_back(std::move(*conditionedConfig.first));
                std::string error;
                if (!matrix->addKernel(std::move(kernel), &error)) {
                    err() << "Error:" << error << std::endl;
                    return false;
                };
            }
        }
        return true;
    }

    Level getLowestFcmVersion(const CompatibilityMatrices& matrices) {
        Level ret = Level::UNSPECIFIED;
        for (const auto& e : matrices) {
            if (ret == Level::UNSPECIFIED || ret > e.level()) {
                ret = e.level();
            }
        }
        return ret;
    }

    bool assembleCompatibilityMatrix(CompatibilityMatrices* matrices) {
        std::string error;
        CompatibilityMatrix* matrix = nullptr;
        std::unique_ptr<HalManifest> checkManifest;
        std::unique_ptr<CompatibilityMatrix> builtMatrix;

        if (mCheckFile.hasStream()) {
            checkManifest = std::make_unique<HalManifest>();
            checkManifest->setFileName(mCheckFile.name());
            if (!fromXml(checkManifest.get(), read(mCheckFile.stream()), &error)) {
                err() << "Cannot parse check file as a HAL manifest: " << error << std::endl;
                return false;
            }
        }

        if (matrices->front().mType == SchemaType::DEVICE) {
            builtMatrix = CompatibilityMatrix::combineDeviceMatrices(matrices, &error);
            matrix = builtMatrix.get();

            if (matrix == nullptr) {
                err() << error << std::endl;
                return false;
            }

            auto vndkVersion = base::Trim(getEnv("REQUIRED_VNDK_VERSION"));
            if (!vndkVersion.empty()) {
                auto& valueInMatrix = matrix->device.mVendorNdk;
                if (!valueInMatrix.version().empty() && valueInMatrix.version() != vndkVersion) {
                    err() << "Hard-coded <vendor-ndk> version in device compatibility matrix ("
                          << matrices->front().fileName() << "), '" << valueInMatrix.version()
                          << "', does not match value inferred "
                          << "from BOARD_VNDK_VERSION '" << vndkVersion << "'" << std::endl;
                    return false;
                }
                valueInMatrix = VendorNdk{std::move(vndkVersion)};
            }

            for (auto&& v : getEnvList("BOARD_SYSTEMSDK_VERSIONS")) {
                matrix->device.mSystemSdk.mVersions.emplace(std::move(v));
            }
        }

        if (matrices->front().mType == SchemaType::FRAMEWORK) {
            Level deviceLevel =
                checkManifest != nullptr ? checkManifest->level() : Level::UNSPECIFIED;
            if (deviceLevel == Level::UNSPECIFIED) {
                deviceLevel = getLowestFcmVersion(*matrices);
                if (checkManifest != nullptr && deviceLevel != Level::UNSPECIFIED) {
                    err() << "Warning: No Target FCM Version for device. Assuming \""
                          << to_string(deviceLevel)
                          << "\" when building final framework compatibility matrix." << std::endl;
                }
            }
            // No <kernel> tags to assemble at this point
            const auto kernelLevel = Level::UNSPECIFIED;
            builtMatrix = CompatibilityMatrix::combine(deviceLevel, kernelLevel, matrices, &error);
            matrix = builtMatrix.get();

            if (matrix == nullptr) {
                err() << error << std::endl;
                return false;
            }

            if (!assembleFrameworkCompatibilityMatrixKernels(matrix)) {
                return false;
            }

            // Add PLATFORM_SEPOLICY_* to sepolicy.sepolicy-version. Remove dupes.
            std::set<SepolicyVersion> sepolicyVersions;
            auto sepolicyVersionStrings = getEnvList("PLATFORM_SEPOLICY_COMPAT_VERSIONS");
            auto currentSepolicyVersionString = getEnv("PLATFORM_SEPOLICY_VERSION");
            if (!currentSepolicyVersionString.empty()) {
                sepolicyVersionStrings.push_back(currentSepolicyVersionString);
            }
            for (auto&& s : sepolicyVersionStrings) {
                SepolicyVersion v;
                if (!parse(s, &v)) {
                    err() << "Error: unknown sepolicy version '" << s << "' specified by "
                          << (s == currentSepolicyVersionString
                                  ? "PLATFORM_SEPOLICY_VERSION"
                                  : "PLATFORM_SEPOLICY_COMPAT_VERSIONS")
                          << ".";
                    return false;
                }
                sepolicyVersions.insert(v);
            }
            for (auto&& v : sepolicyVersions) {
                matrix->framework.mSepolicy.mSepolicyVersionRanges.emplace_back(v.majorVer,
                                                                                v.minorVer);
            }

            if (!getFlagIfUnset("POLICYVERS",
                                &matrix->framework.mSepolicy.mKernelSepolicyVersion)) {
                return false;
            }
            if (!getFlagIfUnset("FRAMEWORK_VBMETA_VERSION", &matrix->framework.mAvbMetaVersion)) {
                return false;
            }
            // Hard-override existing AVB version
            getFlag("FRAMEWORK_VBMETA_VERSION_OVERRIDE", &matrix->framework.mAvbMetaVersion,
                    false /* log */);
        }
        outputInputs(*matrices);
        out() << toXml(*matrix, mSerializeFlags);
        out().flush();

        if (checkManifest != nullptr && !checkDualFile(*checkManifest, *matrix)) {
            return false;
        }

        return true;
    }

    enum AssembleStatus { SUCCESS, FAIL_AND_EXIT, TRY_NEXT };
    template <typename Schema, typename AssembleFunc>
    AssembleStatus tryAssemble(const std::string& schemaName, AssembleFunc assemble,
                               std::string* error) {
        std::vector<Schema> schemas;
        Schema schema;
        schema.setFileName(mInFiles.front().name());
        if (!fromXml(&schema, read(mInFiles.front().stream()), error)) {
            return TRY_NEXT;
        }
        auto firstType = schema.type();
        schemas.emplace_back(std::move(schema));

        for (auto it = mInFiles.begin() + 1; it != mInFiles.end(); ++it) {
            Schema additionalSchema;
            const std::string& fileName = it->name();
            additionalSchema.setFileName(fileName);
            if (!fromXml(&additionalSchema, read(it->stream()), error)) {
                err() << "File \"" << fileName << "\" is not a valid " << firstType << " "
                      << schemaName << " (but the first file is a valid " << firstType << " "
                      << schemaName << "). Error: " << *error << std::endl;
                return FAIL_AND_EXIT;
            }
            if (additionalSchema.type() != firstType) {
                err() << "File \"" << fileName << "\" is a " << additionalSchema.type() << " "
                      << schemaName << " (but a " << firstType << " " << schemaName
                      << " is expected)." << std::endl;
                return FAIL_AND_EXIT;
            }

            schemas.emplace_back(std::move(additionalSchema));
        }
        return assemble(&schemas) ? SUCCESS : FAIL_AND_EXIT;
    }

    bool assemble() override {
        using std::placeholders::_1;
        if (mInFiles.empty()) {
            err() << "Missing input file." << std::endl;
            return false;
        }

        std::string manifestError;
        auto status = tryAssemble<HalManifest>(
            "manifest", std::bind(&AssembleVintfImpl::assembleHalManifest, this, _1),
            &manifestError);
        if (status == SUCCESS) return true;
        if (status == FAIL_AND_EXIT) return false;

        resetInFiles();

        std::string matrixError;
        status = tryAssemble<CompatibilityMatrix>(
            "compatibility matrix",
            std::bind(&AssembleVintfImpl::assembleCompatibilityMatrix, this, _1), &matrixError);
        if (status == SUCCESS) return true;
        if (status == FAIL_AND_EXIT) return false;

        err() << "Input file has unknown format." << std::endl
              << "Error when attempting to convert to manifest: " << manifestError << std::endl
              << "Error when attempting to convert to compatibility matrix: " << matrixError
              << std::endl;
        return false;
    }

    std::ostream& setOutputStream(Ostream&& out) override {
        mOutRef = std::move(out);
        return *mOutRef;
    }

    std::ostream& setErrorStream(Ostream&& err) override {
        mErrRef = std::move(err);
        return *mErrRef;
    }

    std::istream& addInputStream(const std::string& name, Istream&& in) override {
        auto it = mInFiles.emplace(mInFiles.end(), name, std::move(in));
        return it->stream();
    }

    std::istream& setCheckInputStream(const std::string& name, Istream&& in) override {
        mCheckFile = NamedIstream(name, std::move(in));
        return mCheckFile.stream();
    }

    bool hasKernelVersion(const KernelVersion& kernelVer) const override {
        return mKernels.find(kernelVer) != mKernels.end();
    }

    std::istream& addKernelConfigInputStream(const KernelVersion& kernelVer,
                                             const std::string& name, Istream&& in) override {
        auto&& kernel = mKernels[kernelVer];
        auto it = kernel.emplace(kernel.end(), name, std::move(in));
        return it->stream();
    }

    void resetInFiles() {
        for (auto& inFile : mInFiles) {
            inFile.stream().clear();
            inFile.stream().seekg(0);
        }
    }

    void setOutputMatrix() override { mOutputMatrix = true; }

    bool setHalsOnly() override {
        if (mHasSetHalsOnlyFlag) {
            err() << "Error: Cannot set --hals-only with --no-hals." << std::endl;
            return false;
        }
        // Just override it with HALS_ONLY because other flags that modify mSerializeFlags
        // does not interfere with this (except --no-hals).
        mSerializeFlags = SerializeFlags::HALS_ONLY;
        mHasSetHalsOnlyFlag = true;
        return true;
    }

    bool setNoHals() override {
        if (mHasSetHalsOnlyFlag) {
            err() << "Error: Cannot set --hals-only with --no-hals." << std::endl;
            return false;
        }
        mSerializeFlags = mSerializeFlags.disableHals();
        mHasSetHalsOnlyFlag = true;
        return true;
    }

    bool setNoKernelRequirements() override {
        mSerializeFlags = mSerializeFlags.disableKernelConfigs().disableKernelMinorRevision();
        mCheckFlags = mCheckFlags.disableKernel();
        return true;
    }

   private:
    std::vector<NamedIstream> mInFiles;
    Ostream mOutRef;
    Ostream mErrRef;
    NamedIstream mCheckFile;
    bool mOutputMatrix = false;
    bool mHasSetHalsOnlyFlag = false;
    SerializeFlags::Type mSerializeFlags = SerializeFlags::EVERYTHING;
    std::map<KernelVersion, std::vector<NamedIstream>> mKernels;
    std::map<std::string, std::string> mFakeEnv;
    std::vector<AidlInterfaceMetadata> mFakeAidlMetadata;
    std::optional<bool> mFakeAidlUseUnfrozen;
    CheckFlags::Type mCheckFlags = CheckFlags::DEFAULT;
};

bool AssembleVintf::openOutFile(const std::string& path) {
    return static_cast<std::ofstream&>(setOutputStream(std::make_unique<std::ofstream>(path)))
        .is_open();
}

bool AssembleVintf::openInFile(const std::string& path) {
    return static_cast<std::ifstream&>(addInputStream(path, std::make_unique<std::ifstream>(path)))
        .is_open();
}

bool AssembleVintf::openCheckFile(const std::string& path) {
    return static_cast<std::ifstream&>(
               setCheckInputStream(path, std::make_unique<std::ifstream>(path)))
        .is_open();
}

bool AssembleVintf::addKernel(const std::string& kernelArg) {
    auto tokens = base::Split(kernelArg, ":");
    if (tokens.size() <= 1) {
        err() << "Unrecognized --kernel option '" << kernelArg << "'" << std::endl;
        return false;
    }
    KernelVersion kernelVer;
    if (!parse(tokens.front(), &kernelVer)) {
        err() << "Unrecognized kernel version '" << tokens.front() << "'" << std::endl;
        return false;
    }
    if (hasKernelVersion(kernelVer)) {
        err() << "Multiple --kernel for " << kernelVer << " is specified." << std::endl;
        return false;
    }
    for (auto it = tokens.begin() + 1; it != tokens.end(); ++it) {
        bool opened =
            static_cast<std::ifstream&>(
                addKernelConfigInputStream(kernelVer, *it, std::make_unique<std::ifstream>(*it)))
                .is_open();
        if (!opened) {
            err() << "Cannot open file '" << *it << "'." << std::endl;
            return false;
        }
    }
    return true;
}

std::unique_ptr<AssembleVintf> AssembleVintf::newInstance() {
    return std::make_unique<AssembleVintfImpl>();
}

}  // namespace vintf
}  // namespace android
