/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef ANDROID_VINTF_MANIFEST_INSTANCE_H
#define ANDROID_VINTF_MANIFEST_INSTANCE_H

#include <optional>
#include <string>

#include <vintf/FqInstance.h>
#include <vintf/HalFormat.h>
#include <vintf/TransportArch.h>
#include <vintf/Version.h>

namespace android {
namespace vintf {

class ManifestInstance {
   public:
    ManifestInstance();
    ManifestInstance(const ManifestInstance&);
    ManifestInstance(ManifestInstance&&) noexcept;
    ManifestInstance& operator=(const ManifestInstance&);
    ManifestInstance& operator=(ManifestInstance&&) noexcept;

    using VersionType = Version;
    ManifestInstance(FqInstance&& fqInstance, TransportArch&& ta, HalFormat fmt,
                     std::optional<std::string>&& updatableViaApex);
    ManifestInstance(const FqInstance& fqInstance, const TransportArch& ta, HalFormat fmt,
                     const std::optional<std::string>& updatableViaApex);
    const std::string& package() const;
    Version version() const;
    std::string interface() const;
    const std::string& instance() const;
    Transport transport() const;
    Arch arch() const;
    HalFormat format() const;
    const std::optional<std::string>& updatableViaApex() const;
    const std::optional<std::string> ip() const;
    const std::optional<uint64_t> port() const;

    bool operator==(const ManifestInstance& other) const;
    bool operator<(const ManifestInstance& other) const;

    // Convenience methods.
    // return package@version::interface/instance
    const FqInstance& getFqInstance() const;

    // This is for writing the XML <fqname> tag.
    // For AIDL, return "interface/instance".
    // For others, return "@version::interface/instance".
    std::string getSimpleFqInstance() const;

    // For AIDL, return "package.interface/instance (@version)".
    // For others, return "package@version::interface/instance".
    std::string description() const;

    // Similar to description() but without package name.
    // For AIDL, return "interface/instance (@version)".
    // For others, return "@version::interface/instance".
    std::string descriptionWithoutPackage() const;

    // Return a new ManifestInstance that's the same as this, but with the given version.
    ManifestInstance withVersion(const Version& v) const;

   private:
    FqInstance mFqInstance;
    TransportArch mTransportArch;
    HalFormat mHalFormat;
    std::optional<std::string> mUpdatableViaApex;
};

}  // namespace vintf
}  // namespace android

#endif  // ANDROID_VINTF_MANIFEST_INSTANCE_H
