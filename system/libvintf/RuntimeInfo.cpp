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


#define LOG_TAG "libvintf"

#include "RuntimeInfo.h"

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <kver/kernel_release.h>

#include "CompatibilityMatrix.h"
#include "parse_string.h"

namespace android {
namespace vintf {

const std::string &RuntimeInfo::osName() const {
    return mOsName;
}

const std::string &RuntimeInfo::nodeName() const {
    return mNodeName;
}

const std::string &RuntimeInfo::osRelease() const {
    return mOsRelease;
}

const std::string &RuntimeInfo::osVersion() const {
    return mOsVersion;
}

const std::string &RuntimeInfo::hardwareId() const {
    return mHardwareId;
}

const KernelVersion &RuntimeInfo::kernelVersion() const {
    return mKernel.version();
}

const std::map<std::string, std::string> &RuntimeInfo::kernelConfigs() const {
    return mKernel.configs();
}

size_t RuntimeInfo::kernelSepolicyVersion() const {
    return mKernelSepolicyVersion;
}

const std::string &RuntimeInfo::cpuInfo() const {
    return mCpuInfo;
}

const Version &RuntimeInfo::bootVbmetaAvbVersion() const {
    return mBootVbmetaAvbVersion;
}

const Version &RuntimeInfo::bootAvbVersion() const {
    return mBootAvbVersion;
}

bool RuntimeInfo::isMainlineKernel() const {
    return mIsMainline;
}

bool RuntimeInfo::checkCompatibility(const CompatibilityMatrix& mat, std::string* error,
                                     CheckFlags::Type flags) const {
    if (mat.mType != SchemaType::FRAMEWORK) {
        if (error != nullptr) {
            *error = "Should not check runtime info against " + to_string(mat.mType)
                    + " compatibility matrix.";
        }
        return false;
    }
    if (kernelSepolicyVersion() < mat.framework.mSepolicy.kernelSepolicyVersion()) {
        if (error != nullptr) {
            *error =
                "kernelSepolicyVersion = " + to_string(kernelSepolicyVersion()) +
                " but required >= " + to_string(mat.framework.mSepolicy.kernelSepolicyVersion());
        }
        return false;
    }

    // mat.mSepolicy.sepolicyVersion() is checked against static
    // HalManifest.device.mSepolicyVersion in HalManifest::checkCompatibility.

    if (flags.isKernelEnabled()) {
        if (!isMainlineKernel() &&
            mKernel.getMatchedKernelRequirements(mat.framework.mKernels, kernelLevel(), error)
                .empty()) {
            return false;
        }
    }

    if (flags.isAvbEnabled()) {
        const Version& matAvb = mat.framework.mAvbMetaVersion;
        if (mBootAvbVersion.majorVer != matAvb.majorVer ||
            mBootAvbVersion.minorVer < matAvb.minorVer) {
            if (error != nullptr) {
                std::stringstream ss;
                ss << "AVB version " << mBootAvbVersion << " does not match framework matrix "
                   << matAvb;
                *error = ss.str();
            }
            return false;
        }
        if (mBootVbmetaAvbVersion.majorVer != matAvb.majorVer ||
            mBootVbmetaAvbVersion.minorVer < matAvb.minorVer) {
            if (error != nullptr) {
                std::stringstream ss;
                ss << "Vbmeta version " << mBootVbmetaAvbVersion
                   << " does not match framework matrix " << matAvb;
                *error = ss.str();
            }
            return false;
        }
    }

    return true;
}

void RuntimeInfo::setKernelLevel(Level level) {
    mKernel.mLevel = level;
}

Level RuntimeInfo::kernelLevel() const {
    return mKernel.mLevel;
}

status_t RuntimeInfo::parseGkiKernelRelease(RuntimeInfo::FetchFlags flags,
                                            const std::string& kernelReleaseString,
                                            KernelVersion* outVersion, Level* outLevel) {
    auto kernelRelease =
        android::kver::KernelRelease::Parse(kernelReleaseString, true /* allow suffix */);
    if (kernelRelease == std::nullopt) {
        return UNKNOWN_ERROR;
    }

    if (flags & RuntimeInfo::FetchFlag::CPU_VERSION) {
        if (kernelRelease->version() > std::numeric_limits<size_t>::max() ||
            kernelRelease->patch_level() > std::numeric_limits<size_t>::max() ||
            kernelRelease->sub_level() > std::numeric_limits<size_t>::max()) {
            LOG(ERROR) << "Overflow : " << kernelRelease->string();
            return UNKNOWN_ERROR;
        }
        *outVersion = {static_cast<size_t>(kernelRelease->version()),
                       static_cast<size_t>(kernelRelease->patch_level()),
                       static_cast<size_t>(kernelRelease->sub_level())};
    }

    if (flags & RuntimeInfo::FetchFlag::KERNEL_FCM) {
        Level kernelLevel = gkiAndroidReleaseToLevel(kernelRelease->android_release());
        if (kernelLevel == Level::UNSPECIFIED) {
            LOG(ERROR) << "Cannot infer level corresponding to Android "
                       << kernelRelease->android_release()
                       << "; update libvintf to recognize this value.";
            return UNKNOWN_ERROR;
        }
        // VintfObject may previously set mRuntimeInfo->mKernel.mLevel to the kernel level
        // from device manifest. Check consistency.
        if (*outLevel != Level::UNSPECIFIED && *outLevel != kernelLevel) {
            LOG(ERROR) << "Kernel level in device manifest (" << *outLevel
                       << ") does not match kernel level in kernel release (" << kernelLevel
                       << " for Android " << kernelRelease->android_release() << ")";
            return UNKNOWN_ERROR;
        }
        *outLevel = kernelLevel;
    }
    return OK;
}

Level RuntimeInfo::gkiAndroidReleaseToLevel(uint64_t androidRelease) {
    constexpr size_t ANDROID_S = 12;
    constexpr size_t ANDROID_V = 15;

    // Values prior to Android 12 is ignored because GKI kernel release format starts
    // at Android 12.
    if (androidRelease < ANDROID_S) return Level::UNSPECIFIED;

    Level ret = Level::UNSPECIFIED;
    if (androidRelease < ANDROID_V) {
        ret = static_cast<Level>(androidRelease - ANDROID_S + static_cast<size_t>(Level::S));
    } else {
        switch (androidRelease) {
            case 15: {
                ret = Level::V;
            } break;
            // Add more levels above this line.
            default: {
                LOG(FATAL) << "Convert Android " << androidRelease << " to level '" << ret
                           << "' goes out of bounds. Fix by editing "
                           << "RuntimeInfo::gkiAndroidReleaseToLevel";
            } break;
        }
    }

    CHECK(IsValid(ret) && ret != Level::UNSPECIFIED)
        << "Convert Android " << androidRelease << " to level '" << ret
        << "' goes out of bounds. Fix by adding a new Level enum.";
    return ret;
}

bool RuntimeInfo::kernelReleaseIsMainline(std::string_view kernelRelease) {
    static constexpr char kMainline[] = "-mainline-";
    static constexpr char kMainlineSuffix[] = "-mainline";

    return kernelRelease.find(kMainline) != std::string::npos ||
           android::base::EndsWith(kernelRelease, kMainlineSuffix);
}

} // namespace vintf
} // namespace android
