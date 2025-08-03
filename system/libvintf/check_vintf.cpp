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

#include <getopt.h>
#include <sysexits.h>
#include <unistd.h>

#include <algorithm>
#include <functional>
#include <iostream>
#include <map>
#include <optional>

#include <aidl/metadata.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/strings.h>
#include <hidl/metadata.h>
#include <kver/kernel_release.h>
#include <utils/Errors.h>
#include <vintf/Dirmap.h>
#include <vintf/HostFileSystem.h>
#include <vintf/KernelConfigParser.h>
#include <vintf/VintfObject.h>
#include <vintf/fcm_exclude.h>
#include <vintf/parse_string.h>
#include <vintf/parse_xml.h>
#include "constants-private.h"
#include "utils.h"

using android::kver::KernelRelease;

namespace android {
namespace vintf {
namespace details {

// fake sysprops
using Properties = std::map<std::string, std::string>;

enum Option : int {
    // Modes
    HELP,
    DUMP_FILE_LIST = 1,
    CHECK_COMPAT,
    CHECK_ONE,

    // Options
    ROOTDIR,
    PROPERTY,
    DIR_MAP,
    KERNEL,
};
// command line arguments
using Args = std::multimap<Option, std::string>;

class PresetPropertyFetcher : public PropertyFetcher {
   public:
    std::string getProperty(const std::string& key,
                            const std::string& defaultValue) const override {
        auto it = mProps.find(key);
        if (it == mProps.end()) {
            LOG(INFO) << "Sysprop " << key << " is missing, default to '" << defaultValue << "'";
            return defaultValue;
        }
        LOG(INFO) << "Sysprop " << key << "=" << it->second;
        return it->second;
    }
    uint64_t getUintProperty(const std::string& key, uint64_t defaultValue,
                             uint64_t max) const override {
        uint64_t result;
        std::string value = getProperty(key, "");
        if (!value.empty() && android::base::ParseUint(value, &result, max)) return result;
        return defaultValue;
    }
    bool getBoolProperty(const std::string& key, bool defaultValue) const override {
        std::string value = getProperty(key, "");
        if (value == "1" || value == "true") {
            return true;
        } else if (value == "0" || value == "false") {
            return false;
        }
        return defaultValue;
    }
    void setProperties(const Properties& props) { mProps.insert(props.begin(), props.end()); }

   private:
    std::map<std::string, std::string> mProps;
};

struct StaticRuntimeInfo : public RuntimeInfo {
    KernelVersion kernelVersion;
    Level kernelLevel = Level::UNSPECIFIED;
    std::string kernelConfigFile;

    status_t fetchAllInformation(FetchFlags flags) override {
        if (flags & RuntimeInfo::FetchFlag::CPU_VERSION) {
            mKernel.mVersion = kernelVersion;
            LOG(INFO) << "fetched kernel version " << kernelVersion;
        }
        if (flags & RuntimeInfo::FetchFlag::KERNEL_FCM) {
            mKernel.mLevel = kernelLevel;
            LOG(INFO) << "fetched kernel level from RuntimeInfo '" << kernelLevel << "'";
        }
        if (flags & RuntimeInfo::FetchFlag::CONFIG_GZ) {
            std::string content;
            if (!android::base::ReadFileToString(kernelConfigFile, &content)) {
                LOG(ERROR) << "ERROR: Cannot read " << kernelConfigFile;
                return UNKNOWN_ERROR;
            }
            KernelConfigParser parser;
            auto status = parser.processAndFinish(content);
            if (status != OK) {
                return status;
            }
            mKernel.mConfigs = std::move(parser.configs());
            LOG(INFO) << "read kernel configs from " << kernelConfigFile;
        }
        if (flags & RuntimeInfo::FetchFlag::POLICYVERS) {
            mKernelSepolicyVersion = SIZE_MAX;
        }
        return OK;
    }

    void setIsMainline(bool value) { mIsMainline = value; }
};

struct StubRuntimeInfo : public RuntimeInfo {
    status_t fetchAllInformation(FetchFlags) override { return UNKNOWN_ERROR; }
};

struct StaticRuntimeInfoFactory : public ObjectFactory<RuntimeInfo> {
    std::shared_ptr<RuntimeInfo> info;
    StaticRuntimeInfoFactory(std::shared_ptr<RuntimeInfo> i) : info(i) {}
    std::shared_ptr<RuntimeInfo> make_shared() const override {
        if (info) return info;
        return std::make_shared<StubRuntimeInfo>();
    }
};

// helper functions
template <typename T>
std::unique_ptr<T> readObject(FileSystem* fileSystem, const std::string& path) {
    std::string xml;
    std::string error;
    status_t err = fileSystem->fetch(path, &xml, &error);
    if (err != OK) {
        LOG(ERROR) << "ERROR: Cannot read '" << path << "' (" << strerror(-err) << "): " << error;
        return nullptr;
    }
    auto ret = std::make_unique<T>();
    ret->setFileName(path);
    if (!fromXml(ret.get(), xml, &error)) {
        LOG(ERROR) << "ERROR: Cannot parse '" << path << "': " << error;
        return nullptr;
    }
    return ret;
}

int checkCompatibilityForFiles(const std::string& manifestPath, const std::string& matrixPath) {
    auto fileSystem = std::make_unique<FileSystemImpl>();
    auto manifest = readObject<HalManifest>(fileSystem.get(), manifestPath);
    auto matrix = readObject<CompatibilityMatrix>(fileSystem.get(), matrixPath);
    if (manifest == nullptr || matrix == nullptr) {
        return -1;
    }

    std::string error;
    if (!manifest->checkCompatibility(*matrix, &error)) {
        LOG(ERROR) << "ERROR: Incompatible: " << error;
        std::cout << "false" << std::endl;
        return 1;
    }

    std::cout << "true" << std::endl;
    return 0;
}

Args parseArgs(int argc, char** argv) {
    int longOptFlag;
    int optionIndex;
    Args ret;
    std::vector<struct option> longopts{
        // Modes
        {"help", no_argument, &longOptFlag, HELP},
        {"dump-file-list", no_argument, &longOptFlag, DUMP_FILE_LIST},
        {"check-compat", no_argument, &longOptFlag, CHECK_COMPAT},
        {"check-one", no_argument, &longOptFlag, CHECK_ONE},
        // Options
        {"rootdir", required_argument, &longOptFlag, ROOTDIR},
        {"property", required_argument, &longOptFlag, PROPERTY},
        {"dirmap", required_argument, &longOptFlag, DIR_MAP},
        {"kernel", required_argument, &longOptFlag, KERNEL},
        {0, 0, 0, 0}};
    std::map<int, Option> shortopts{
        {'h', HELP}, {'D', PROPERTY}, {'c', CHECK_COMPAT},
    };
    for (;;) {
        int c = getopt_long(argc, argv, "hcD:", longopts.data(), &optionIndex);
        if (c == -1) {
            break;
        }
        std::string argValue = optarg ? optarg : std::string{};
        if (c == 0) {
            ret.emplace(static_cast<Option>(longOptFlag), std::move(argValue));
        } else {
            ret.emplace(shortopts[c], std::move(argValue));
        }
    }
    if (optind < argc) {
        // see non option
        LOG(ERROR) << "ERROR: unrecognized option `" << argv[optind] << "'";
        return {{HELP, ""}};
    }
    return ret;
}

template <typename T>
Properties getProperties(const T& args) {
    return splitArgs(args, '=');
}

// Parse a kernel version or a GKI kernel release.
bool parseKernelVersionOrRelease(const std::string& s, StaticRuntimeInfo* ret) {
    // 5.4.42
    if (parse(s, &ret->kernelVersion)) {
        ret->kernelLevel = Level::UNSPECIFIED;
        ret->setIsMainline(false);
        LOG(INFO) << "\"" << s << "\" is not a mainline kernel.";
        return true;
    }
    LOG(INFO) << "Cannot parse \"" << s << "\" as kernel version, parsing as GKI kernel release.";

    // 5.4.42-android12-0-something
    auto kernelRelease = KernelRelease::Parse(s, true /* allow suffix */);
    if (kernelRelease.has_value()) {
        ret->kernelVersion = KernelVersion{kernelRelease->version(), kernelRelease->patch_level(),
                                           kernelRelease->sub_level()};
        ret->kernelLevel = RuntimeInfo::gkiAndroidReleaseToLevel(kernelRelease->android_release());
        ret->setIsMainline(false);
        LOG(INFO) << "\"" << s << "\" is not a mainline kernel.";
        return true;
    }
    LOG(INFO) << "Cannot parse \"" << s << "\" as GKI kernel release, parsing as kernel release";

    // 5.4.42-something
    auto pos = s.find_first_not_of("0123456789.");
    // substr handles pos == npos case
    if (parse(s.substr(0, pos), &ret->kernelVersion)) {
        ret->kernelLevel = Level::UNSPECIFIED;

        bool isMainline = RuntimeInfo::kernelReleaseIsMainline(s);
        ret->setIsMainline(isMainline);
        LOG(INFO) << "\"" << s << "\" is" << (isMainline ? "" : " not") << " a mainline kernel.";

        return true;
    }

    LOG(INFO) << "Cannot parse \"" << s << "\" as kernel release";
    return false;
}

// Parse the first half of --kernel. |s| can either be a kernel version, a GKI kernel release,
// or a file that contains either of them.
bool parseKernelArgFirstHalf(const std::string& s, StaticRuntimeInfo* ret) {
    if (parseKernelVersionOrRelease(s, ret)) {
        LOG(INFO) << "Successfully parsed \"" << s << "\"";
        return true;
    }
    std::string content;
    if (!android::base::ReadFileToString(s, &content)) {
        PLOG(INFO) << "Cannot read file " << s;
        return false;
    }
    if (parseKernelVersionOrRelease(content, ret)) {
        LOG(INFO) << "Successfully parsed content of " << s << ": " << content;
        return true;
    }
    LOG(ERROR) << "ERROR: Cannot parse content of " << s << ": " << content;
    return false;
}

template <typename T>
std::shared_ptr<StaticRuntimeInfo> getRuntimeInfo(const T& args) {
    auto ret = std::make_shared<StaticRuntimeInfo>();
    if (std::distance(args.begin(), args.end()) > 1) {
        LOG(ERROR) << "ERROR: Can't have multiple --kernel options";
        return nullptr;
    }
    const auto& arg = *args.begin();
    auto colonPos = arg.rfind(":");
    if (colonPos == std::string::npos) {
        LOG(ERROR) << "ERROR: Invalid --kernel";
        return nullptr;
    }

    if (!parseKernelArgFirstHalf(arg.substr(0, colonPos), ret.get())) {
        return nullptr;
    }

    ret->kernelConfigFile = arg.substr(colonPos + 1);
    return ret;
}

int usage(const char* me) {
    LOG(ERROR)
        << me << ": check VINTF metadata." << std::endl
        << "    Modes:" << std::endl
        << "        --dump-file-list: Dump a list of directories / files on device" << std::endl
        << "                that is required to be used by --check-compat." << std::endl
        << "        -c, --check-compat: check compatibility for files under the root" << std::endl
        << "                directory specified by --root-dir." << std::endl
        << "        --check-one: check consistency of VINTF metadata for a single partition."
        << std::endl
        << std::endl
        << "    Options:" << std::endl
        << "        --rootdir=<dir>: specify root directory for all metadata. Same as " << std::endl
        << "                --dirmap /:<dir>" << std::endl
        << "        -D, --property <key>=<value>: specify sysprops." << std::endl
        << "        --dirmap </system:/dir/to/system> [--dirmap </vendor:/dir/to/vendor>[...]]"
        << std::endl
        << "                Map partitions to directories. Cannot be specified with --rootdir."
        << "        --kernel <version:path/to/config>" << std::endl
        << "                Use the given kernel version and config to check. If" << std::endl
        << "                unspecified, kernel requirements are skipped." << std::endl
        << "                The first half, version, can be just x.y.z, or a file " << std::endl
        << "                containing the full kernel release string x.y.z-something." << std::endl
        << "        --help: show this message." << std::endl
        << std::endl
        << "    Example:" << std::endl
        << "        # Get the list of required files." << std::endl
        << "        " << me << " --dump-file-list > /tmp/files.txt" << std::endl
        << "        # Pull from ADB, or use your own command to extract files from images"
        << std::endl
        << "        ROOTDIR=/tmp/device/" << std::endl
        << "        cat /tmp/files.txt | xargs -I{} bash -c \"mkdir -p $ROOTDIR`dirname {}` && adb "
           "pull {} $ROOTDIR{}\""
        << std::endl
        << "        # Check compatibility." << std::endl
        << "        " << me << " --check-compat --rootdir=$ROOTDIR \\" << std::endl
        << "            --property ro.product.first_api_level=`adb shell getprop "
           "ro.product.first_api_level` \\"
        << std::endl
        << "            --property ro.boot.product.hardware.sku=`adb shell getprop "
           "ro.boot.product.hardware.sku`";
    return EX_USAGE;
}

class CheckVintfUtils {
   public:
    // Print HALs in the device manifest that are not declared in FCMs <= target FCM version.
    static void logHalsFromNewFcms(VintfObject* vintfObject,
                                   const std::vector<HidlInterfaceMetadata>& hidlMetadata) {
        auto deviceManifest = vintfObject->getDeviceHalManifest();
        if (deviceManifest == nullptr) {
            LOG(WARNING) << "Unable to print HALs from new FCMs: no device HAL manifest.";
            return;
        }
        std::string kernelLevelError;
        auto kernelLevel = vintfObject->getKernelLevel(&kernelLevelError);
        if (kernelLevel == Level::UNSPECIFIED) {
            LOG(WARNING) << "getKernelLevel: " << kernelLevel;
        }
        std::vector<CompatibilityMatrix> matrixFragments;
        std::string error;
        auto status = vintfObject->getAllFrameworkMatrixLevels(&matrixFragments, &error);
        if (status != OK || matrixFragments.empty()) {
            LOG(WARNING) << "Unable to print HALs from new FCMs: " << statusToString(status) << ": "
                         << error;
            return;
        }
        auto it = std::remove_if(matrixFragments.begin(), matrixFragments.end(),
                                 [&](const CompatibilityMatrix& matrix) {
                                     return matrix.level() != Level::UNSPECIFIED &&
                                            matrix.level() > deviceManifest->level();
                                 });
        matrixFragments.erase(it, matrixFragments.end());
        auto combined = CompatibilityMatrix::combine(deviceManifest->level(), kernelLevel,
                                                     &matrixFragments, &error);
        if (combined == nullptr) {
            LOG(WARNING) << "Unable to print HALs from new FCMs: unable to combine matrix "
                            "fragments <= level "
                         << deviceManifest->level() << ": " << error;
        }
        auto unused = deviceManifest->checkUnusedHals(*combined, hidlMetadata);
        if (unused.empty()) {
            LOG(INFO) << "All HALs in device manifest are declared in FCM <= level "
                      << deviceManifest->level();
            return;
        }
        LOG(INFO) << "The following HALs in device manifest are not declared in FCM <= level "
                  << deviceManifest->level() << ": ";
        for (const auto& hal : unused) {
            LOG(INFO) << "  " << hal;
        }
    }
};

// If |result| is already an error, don't do anything. Otherwise, set it to
// an error with |errorCode|. Return reference to Error object for appending
// additional error messages.
android::base::Error<>& SetErrorCode(std::optional<android::base::Error<>>* retError,
                                     int errorCode = 0) {
    if (!retError->has_value()) {
        retError->emplace(errorCode);
    } else {
        // Use existing error code.
        // There should already been an error message appended. Add a new line char for
        // additional messages.
        (**retError) << "\n";
    }
    return **retError;
}

// If |other| is an error, add it to |retError|.
template <typename T>
void AddResult(std::optional<android::base::Error<>>* retError,
               const android::base::Result<T>& other, const char* additionalMessage = "") {
    if (other.ok()) return;
    SetErrorCode(retError, other.error().code()) << other.error() << additionalMessage;
}

static constexpr const char* gCheckMissingHalsSuggestion{
    "\n- If this is a new package, add it to the latest framework compatibility matrix."
    "\n- If no interface should be added to the framework compatibility matrix (e.g. "
    "types-only package), add it to the exempt list in libvintf_fcm_exclude."};

android::base::Result<void> checkAllFiles(const Dirmap& dirmap, const Properties& props,
                                          std::shared_ptr<StaticRuntimeInfo> runtimeInfo) {
    auto hostFileSystem = std::make_unique<HostFileSystem>(dirmap, UNKNOWN_ERROR);
    auto hostPropertyFetcher = std::make_unique<PresetPropertyFetcher>();
    hostPropertyFetcher->setProperties(props);

    CheckFlags::Type flags = CheckFlags::DEFAULT;
    if (!runtimeInfo) flags = flags.disableRuntimeInfo();

    auto vintfObject =
        VintfObject::Builder()
            .setFileSystem(std::move(hostFileSystem))
            .setPropertyFetcher(std::move(hostPropertyFetcher))
            .setRuntimeInfoFactory(std::make_unique<StaticRuntimeInfoFactory>(runtimeInfo))
            .build();

    std::optional<android::base::Error<>> retError = std::nullopt;

    std::string compatibleError;
    int compatibleResult = vintfObject->checkCompatibility(&compatibleError, flags);
    if (compatibleResult == INCOMPATIBLE) {
        SetErrorCode(&retError) << compatibleError;
    } else if (compatibleResult != COMPATIBLE) {
        SetErrorCode(&retError, -compatibleResult) << compatibleError;
    }

    auto hidlMetadata = HidlInterfaceMetadata::all();

    std::string deprecateError;
    int deprecateResult = vintfObject->checkDeprecation(hidlMetadata, &deprecateError);
    if (deprecateResult == DEPRECATED) {
        SetErrorCode(&retError) << deprecateError;
    } else if (deprecateResult != NO_DEPRECATED_HALS) {
        SetErrorCode(&retError, -deprecateResult) << deprecateError;
    }

    auto hasFcmExt = vintfObject->hasFrameworkCompatibilityMatrixExtensions();
    AddResult(&retError, hasFcmExt);

    auto deviceManifest = vintfObject->getDeviceHalManifest();
    Level targetFcm = Level::UNSPECIFIED;
    if (deviceManifest == nullptr) {
        SetErrorCode(&retError, -NAME_NOT_FOUND) << "No device HAL manifest";
    } else {
        targetFcm = deviceManifest->level();
    }

    if (hasFcmExt.value_or(false) || (targetFcm != Level::UNSPECIFIED && targetFcm >= Level::R)) {
        AddResult(&retError, vintfObject->checkUnusedHals(hidlMetadata));
    } else {
        LOG(INFO) << "Skip checking unused HALs.";
    }

    CheckVintfUtils::logHalsFromNewFcms(vintfObject.get(), hidlMetadata);

    if (retError.has_value()) {
        return *retError;
    } else {
        return {};
    }
}

// Checks consistency of VINTF metadata for a single partition.
// For now it supports either /system or /vendor.
int checkOne(const Dirmap& dirmap, const Properties& props) {
    if (dirmap.count("/system") + dirmap.count("/vendor") != 1) {
        LOG(ERROR) << "ERROR: --check-one requires either --dirmap /system or --dirmap /vendor";
        return EX_SOFTWARE;
    }

    auto hostFileSystem = std::make_unique<HostFileSystem>(dirmap, NAME_NOT_FOUND);
    auto hostPropertyFetcher = std::make_unique<PresetPropertyFetcher>();
    hostPropertyFetcher->setProperties(props);

    auto vintfObject =
        VintfObject::Builder()
            .setFileSystem(std::move(hostFileSystem))
            .setPropertyFetcher(std::move(hostPropertyFetcher))
            .setRuntimeInfoFactory(std::make_unique<StaticRuntimeInfoFactory>(nullptr))
            .build();

    if (dirmap.count("/system")) {
        LOG(INFO) << "Checking system manifest.";
        auto manifest = vintfObject->getFrameworkHalManifest();
        if (!manifest) {
            LOG(ERROR) << "ERROR: Cannot fetch system manifest.";
            return EX_SOFTWARE;
        }
        LOG(INFO) << "Checking system matrix.";
        auto matrix = vintfObject->getFrameworkCompatibilityMatrix();
        if (!matrix) {
            LOG(ERROR) << "ERROR: Cannot fetch system matrix.";
            return EX_SOFTWARE;
        }
        auto res = vintfObject->checkMissingHalsInMatrices(
            HidlInterfaceMetadata::all(), AidlInterfaceMetadata::all(),
            ShouldCheckMissingHidlHalsInFcm, ShouldCheckMissingAidlHalsInFcm);
        if (!res.ok()) {
            LOG(ERROR) << "ERROR: " << res.error() << gCheckMissingHalsSuggestion;
            return EX_SOFTWARE;
        }

        res = vintfObject->checkMatrixHalsHasDefinition(HidlInterfaceMetadata::all(),
                                                        AidlInterfaceMetadata::all());
        if (!res.ok()) {
            LOG(ERROR) << "ERROR: " << res.error();
            return EX_SOFTWARE;
        }
        return EX_OK;
    }

    if (dirmap.count("/vendor")) {
        LOG(INFO) << "Checking vendor manifest.";
        auto manifest = vintfObject->getDeviceHalManifest();
        if (!manifest) {
            LOG(ERROR) << "ERROR: Cannot fetch vendor manifest.";
            return EX_SOFTWARE;
        }
        LOG(INFO) << "Checking vendor matrix.";
        auto matrix = vintfObject->getDeviceCompatibilityMatrix();
        if (!matrix) {
            LOG(ERROR) << "ERROR: Cannot fetch vendor matrix.";
            return EX_SOFTWARE;
        }
        return EX_OK;
    }

    __builtin_unreachable();
}

void Logger(android::base::LogId, android::base::LogSeverity severity, const char* /*tag*/,
            const char* /*file*/, unsigned int /*line*/, const char* message) {
    if (severity >= android::base::WARNING) {
        fflush(stdout);
        fprintf(stderr, "%s\n", message);
    } else {
        fprintf(stdout, "%s\n", message);
    }
}

}  // namespace details
}  // namespace vintf
}  // namespace android

int main(int argc, char** argv) {
    android::base::SetLogger(android::vintf::details::Logger);

    using namespace android::vintf;
    using namespace android::vintf::details;
    // legacy usage: check_vintf <manifest.xml> <matrix.xml>
    if (argc == 3 && *argv[1] != '-' && *argv[2] != '-') {
        int ret = checkCompatibilityForFiles(argv[1], argv[2]);
        if (ret >= 0) return ret;
    }

    Args args = parseArgs(argc, argv);

    if (!iterateValues(args, HELP).empty()) {
        return usage(argv[0]);
    }

    auto dirmap = getDirmap(iterateValues(args, DIR_MAP));
    auto properties = getProperties(iterateValues(args, PROPERTY));
    if (!iterateValues(args, DUMP_FILE_LIST).empty()) {
        auto it = properties.find("ro.boot.product.hardware.sku");
        const std::string sku = it == properties.end() ? "" : it->second;
        for (const auto& file : dumpFileList(sku)) {
            std::cout << file << std::endl;
        }
        return 0;
    }

    if (!iterateValues(args, CHECK_ONE).empty()) {
        return checkOne(dirmap, properties);
    }

    auto checkCompat = iterateValues(args, CHECK_COMPAT);
    if (checkCompat.empty()) {
        return usage(argv[0]);
    }

    auto rootdirs = iterateValues(args, ROOTDIR);
    if (!rootdirs.empty()) {
        if (std::distance(rootdirs.begin(), rootdirs.end()) > 1) {
            LOG(ERROR) << "ERROR: Can't have multiple --rootdir options";
            return usage(argv[0]);
        }
        args.emplace(DIR_MAP, "/:" + *rootdirs.begin());
    }

    std::shared_ptr<StaticRuntimeInfo> runtimeInfo;
    auto kernelArgs = iterateValues(args, KERNEL);
    if (!kernelArgs.empty()) {
        runtimeInfo = getRuntimeInfo(kernelArgs);
        if (runtimeInfo == nullptr) {
            return usage(argv[0]);
        }
    }

    if (dirmap.empty()) {
        LOG(ERROR) << "ERROR: Missing --rootdir or --dirmap option.";
        return usage(argv[0]);
    }

    auto compat = checkAllFiles(dirmap, properties, runtimeInfo);

    if (compat.ok()) {
        std::cout << "COMPATIBLE" << std::endl;
        return EX_OK;
    }
    if (compat.error().code() == 0) {
        LOG(ERROR) << "ERROR: files are incompatible: " << compat.error();
        std::cout << "INCOMPATIBLE" << std::endl;
        return EX_DATAERR;
    }
    LOG(ERROR) << "ERROR: " << strerror(compat.error().code()) << ": " << compat.error();
    return EX_SOFTWARE;
}
