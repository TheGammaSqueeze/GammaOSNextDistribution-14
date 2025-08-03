/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <algorithm>
#include <map>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <vintf/Dirmap.h>
#include <vintf/HostFileSystem.h>
#include <vintf/VintfFm.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>
#include <vintf/parse_xml.h>

#include "utils.h"

namespace android::vintf {

namespace {

int usage() {
    LOG(ERROR) << R"(
vintffm: Utility to deprecate framework manifest.
usage:
vintffm <-c|--check> <--dirmap /system:system_dir> frozen_dir
  Check framework manifest under system_root against frozen dir. root is the
  root directory of the device, e.g. $ANDROID_PRODUCT_OUT.
vintffm <-u|--update> <--dirmap /system:system_dir> <-l|--level>=current_level output_frozen_dir
  Update ${output_frozen_dir}/${current_level}.xml using framework manifest.
vintffm <-h|--help>
  Print help message.

Example:

# Freeze a framework manifest for Android R.
m check-vintf-all # Build framework manifest.
vintffm --update --dirmap /system:$ANDROID_PRODUCT_OUT/system --level 5 \
  system/libhidl/vintfdata/frozen

# Check that the framework manifest is aligned with the frozen data.
vintffm --check --dirmap /system:$ANDROID_PRODUCT_OUT/system \
  system/libhidl/vintfdata/frozen
)";
    return EX_USAGE;
}

class WritableFileSystemImpl : public WritableFileSystem {
   public:
    status_t fetch(const std::string& path, std::string* fetched,
                   std::string* error) const override {
        return mRoFileSystem.fetch(path, fetched, error);
    }
    status_t listFiles(const std::string& path, std::vector<std::string>* out,
                       std::string* error) const override {
        return mRoFileSystem.listFiles(path, out, error);
    }
    status_t modifiedTime(const std::string& path, TimeSpec* out,
                          std::string* error) const override {
        return mRoFileSystem.modifiedTime(path, out, error);
    }
    status_t write(const std::string& path, const std::string& content,
                   std::string* error) const override {
        if (!android::base::WriteStringToFile(content, path)) {
            int saved_errno = errno;
            if (error) {
                *error = "Can't write to " + path + ": " + strerror(saved_errno);
            }
            return saved_errno == 0 ? UNKNOWN_ERROR : -saved_errno;
        }
        return OK;
    }
    status_t deleteFile(const std::string& path, std::string* error) const override {
        if (unlink(path.c_str()) == -1) {
            int saved_errno = errno;
            if (error) {
                *error = "Can't unlink " + path + ": " + strerror(saved_errno);
            }
            return saved_errno == 0 ? UNKNOWN_ERROR : -saved_errno;
        }
        return OK;
    }

   private:
    details::FileSystemImpl mRoFileSystem;
};

}  // namespace

namespace details {

// A VintfObject with a proper framework manifest and a fake device manifest with
// only targetFcmVersion.
class FmOnlyVintfObject : public VintfObject {
   public:
    FmOnlyVintfObject(std::unique_ptr<FileSystem>&& fs, Level targetFcmVersion)
        : mFs(std::move(fs)) {
        mDeviceManifest = std::make_shared<HalManifest>();
        mDeviceManifest->mLevel = targetFcmVersion;
    }

    std::shared_ptr<const HalManifest> getDeviceHalManifest() override { return mDeviceManifest; }
    std::shared_ptr<const CompatibilityMatrix> getFrameworkCompatibilityMatrix() override {
        return nullptr;
    }
    std::shared_ptr<const CompatibilityMatrix> getDeviceCompatibilityMatrix() override {
        return nullptr;
    }

   protected:
    const std::unique_ptr<FileSystem>& getFileSystem() override { return mFs; }
    // Set environment to empty to prevent accidentally reading other things.
    const std::unique_ptr<PropertyFetcher>& getPropertyFetcher() override { return mNoOpProp; }

   private:
    std::unique_ptr<FileSystem> mFs;
    std::shared_ptr<HalManifest> mDeviceManifest;
    std::unique_ptr<PropertyFetcher> mNoOpProp = std::make_unique<details::PropertyFetcherNoOp>();
};

}  // namespace details

VintfFm::VintfFm() : VintfFm(std::make_unique<WritableFileSystemImpl>()) {}

int VintfFm::main(int argc, char** argv) {
    // clang-format off
    const struct option longopts[] = {
        {"check", no_argument, nullptr, 'c'},
        {"dirmap", required_argument, nullptr, 'd'},
        {"help", no_argument, nullptr, 'h'},
        {"level", required_argument, nullptr, 'l'},
        {"update", no_argument, nullptr, 'u'},
        {0, 0, 0, 0}};
    // clang-format on

    bool checking = false;
    bool updating = false;
    Level current = Level::UNSPECIFIED;
    std::vector<std::string> dirmapVec;

    int res;
    optind = 1;
    while ((res = getopt_long(argc, argv, "cdhlu", longopts, nullptr)) >= 0) {
        switch (res) {
            case 'c': {
                checking = true;
            } break;

            case 'd': {
                dirmapVec.push_back(optarg);
            } break;

            case 'l': {
                if (!parse(optarg, &current)) {
                    LOG(ERROR) << "Unable to parse '" << optarg << "' as level.";
                    return usage();
                }
            } break;

            case 'u': {
                updating = true;
            } break;

            case 'h':
            default: {
                return usage();
            } break;
        }
    }

    if ((checking + updating) != 1) {
        LOG(ERROR) << "Exactly one of --check or --update must be set.";
        return usage();
    }

    auto dirmap = details::getDirmap(dirmapVec);
    auto vintfFsFactory = [&] {
        return std::make_unique<details::HostFileSystem>(dirmap, NAME_NOT_FOUND, mFs.get());
    };

    argc -= optind;
    argv += optind;

    if (argc != 1) {
        LOG(ERROR) << "There must be exactly 1 positional arguments.";
        return usage();
    }
    auto dir = argv[0];

    if (updating) {
        return update(vintfFsFactory, dir, current);
    }
    return check(vintfFsFactory, dir);
}

int VintfFm::update(const FsFactory& vintfFsFactory, const std::string& dir, Level level) {
    if (level == Level::UNSPECIFIED) {
        LOG(ERROR) << "Must specify last frozen level with --level for --update option.";
        return usage();
    }

    auto manifest = getManifestForLevel(vintfFsFactory, level);
    if (manifest == nullptr) {
        LOG(ERROR) << "Unable to determine manifests for level " << level;
        return EX_SOFTWARE;
    }

    if (!dumpMatrix(*manifest, dir, level)) {
        return EX_SOFTWARE;
    }

    return EX_OK;
}

int VintfFm::check(const FsFactory& vintfFsFactory, const std::string& dir) {
    auto frozenMatrices = loadMatrices(dir);
    if (!frozenMatrices.has_value()) {
        return EX_SOFTWARE;
    }
    for (const auto& [level, matrix] : *frozenMatrices) {
        auto manifest = getManifestForLevel(vintfFsFactory, level);
        if (manifest == nullptr) {
            LOG(ERROR) << "Unable to determine manifests for level " << level;
            return EX_SOFTWARE;
        }
        std::string error;
        if (!manifest->checkCompatibility(matrix, &error)) {
            LOG(ERROR) << "Framework manifest is incompatible with frozen matrix at level " << level
                       << ": " << error;
            return EX_SOFTWARE;
        }
    }
    return OK;
}

std::shared_ptr<const HalManifest> VintfFm::getManifestForLevel(const FsFactory& vintfFsFactory,
                                                                Level level) {
    auto vintfObject = std::make_unique<details::FmOnlyVintfObject>(vintfFsFactory(), level);
    auto frameworkManifest = vintfObject->getFrameworkHalManifest();
    if (frameworkManifest == nullptr) {
        LOG(ERROR) << "Unable to get framework HAL manifest for target FCM version " << level;
    }
    return frameworkManifest;
}

bool VintfFm::dumpMatrix(const HalManifest& frameworkManifest, const std::string& dir,
                         Level level) {
    auto matrix = frameworkManifest.generateCompatibleMatrix(false /*optional*/);
    std::string path = dir + "/" + to_string(level) + ".xml";
    std::string error;
    if (OK != mFs->write(path, toXml(matrix), &error)) {
        LOG(ERROR) << "Unable to dump matrix to " << path << ": " << error;
        return false;
    }
    return true;
}

std::optional<VintfFm::FrozenMatrices> VintfFm::loadMatrices(const std::string& dir) {
    std::string error;
    std::vector<std::string> allFiles;
    if (OK != mFs->listFiles(dir, &allFiles, &error)) {
        LOG(ERROR) << "Unable to list files under " << dir << ": " << error;
        return std::nullopt;
    }
    if (allFiles.empty()) {
        LOG(ERROR) << "Unable to load frozen interfaces under " << dir << ": directory is empty.";
        return std::nullopt;
    }
    auto ret = std::make_optional<FrozenMatrices>();
    for (const auto& filename : allFiles) {
        std::string path = dir + "/" + filename;
        std::string xmlString;
        if (OK != mFs->fetch(path, &xmlString, &error)) {
            LOG(ERROR) << "Unable to read " << path << ": " << error;
            return std::nullopt;
        }
        CompatibilityMatrix matrix;
        if (!fromXml(&matrix, xmlString, &error)) {
            LOG(ERROR) << "Unable to parse " << path << ": " << error;
            return std::nullopt;
        }
        std::string_view filenameSv{filename};
        (void)android::base::ConsumeSuffix(&filenameSv, ".xml");
        std::string levelString{filenameSv};
        Level matrixLevel;
        if (!parse(levelString, &matrixLevel)) {
            LOG(ERROR) << "Unable to parse " << path << ": " << levelString << " is not a level.";
            return std::nullopt;
        }
        if (ret->find(matrixLevel) != ret->end()) {
            LOG(ERROR) << "Duplicated level " << matrixLevel << ", second one is at " << path;
            return std::nullopt;
        }
        ret->emplace(matrixLevel, std::move(matrix));
    }
    return ret;
}

}  // namespace android::vintf
