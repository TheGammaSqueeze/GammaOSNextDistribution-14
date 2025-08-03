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


#ifndef ANDROID_VINTF_MANIFEST_HAL_H
#define ANDROID_VINTF_MANIFEST_HAL_H

#include <map>
#include <optional>
#include <set>
#include <string>
#include <vector>

#include <vintf/FqInstance.h>
#include <vintf/HalFormat.h>
#include <vintf/HalInterface.h>
#include <vintf/Level.h>
#include <vintf/ManifestInstance.h>
#include <vintf/TransportArch.h>
#include <vintf/Version.h>
#include <vintf/WithFileName.h>

namespace android {
namespace vintf {

// A component of HalManifest.
struct ManifestHal : public WithFileName {
    using InstanceType = ManifestInstance;

    ManifestHal() = default;

    bool operator==(const ManifestHal &other) const;

    HalFormat format = HalFormat::HIDL;
    std::string name;
    std::vector<Version> versions;
    TransportArch transportArch;

    inline Transport transport() const {
        return transportArch.transport;
    }

    inline Arch arch() const { return transportArch.arch; }
    inline std::optional<std::string> ip() const { return transportArch.ip; }
    inline std::optional<uint64_t> port() const { return transportArch.port; }

    inline const std::string& getName() const { return name; }

    // Assume isValid().
    bool forEachInstance(const std::function<bool(const ManifestInstance&)>& func) const;

    bool isOverride() const { return mIsOverride; }
    const std::optional<std::string>& updatableViaApex() const { return mUpdatableViaApex; }

    // When true, the existence of this <hal> tag means the component does NOT
    // exist on the device. This is useful for ODM manifests to specify that
    // a HAL is disabled on certain products.
    bool isDisabledHal() const;

    Level getMaxLevel() const { return mMaxLevel; }
    Level getMinLevel() const { return mMinLevel; }

   private:
    friend struct LibVintfTest;
    friend struct ManifestHalConverter;
    friend struct HalManifest;
    friend bool parse(const std::string &s, ManifestHal *hal);

    // Whether this hal is a valid one. Note that an empty ManifestHal
    // (constructed via ManifestHal()) is valid.
    bool isValid(std::string* error = nullptr) const;

    // Return all versions mentioned by <version>s and <fqname>s.
    void appendAllVersions(std::set<Version>* ret) const;

    // insert instances to mManifestInstances.
    // Existing instances will be ignored.
    // Pre: all instances to be inserted must satisfy
    // !hasPackage() && hasVersion() && hasInterface() && hasInstance()
    bool insertInstance(const FqInstance& fqInstance, bool allowDupMajorVersion,
                        std::string* error = nullptr);
    bool insertInstances(const std::set<FqInstance>& fqInstances, bool allowDupMajorVersion,
                         std::string* error = nullptr);

    // Verify instance before inserting.
    bool verifyInstance(const FqInstance& fqInstance, std::string* error = nullptr) const;

    bool mIsOverride = false;
    std::optional<std::string> mUpdatableViaApex;
    // All instances specified with <fqname> and <version> x <interface> x <instance>
    std::set<ManifestInstance> mManifestInstances;

    // Max level of this HAL (inclusive). Only valid for framework manifest HALs.
    // If set, HALs with max-level < target FCM version in device manifest is
    // disabled.
    Level mMaxLevel = Level::UNSPECIFIED;
    // Min level of this HAL (inclusive). Only valid for framework manifest HALs.
    // If set, HALs with max-level > target FCM version in device manifest is
    // disabled.
    Level mMinLevel = Level::UNSPECIFIED;
};

} // namespace vintf
} // namespace android

#endif // ANDROID_VINTF_MANIFEST_HAL_H
