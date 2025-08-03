/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <vintf/Level.h>
#include <vintf/Version.h>
#include <vintf/VersionRange.h>
#include <vintf/constants.h>

namespace android {
namespace vintf {
namespace details {

// All <version> tags in <hal format="aidl"> tags are ignored, and an implicit version
// is inserted so that compatibility checks for different HAL formats can be unified.
// This is an implementation detail of libvintf and won't be written to actual XML files.
// 0.0 is not used because FQName / FqInstance consider it an invalid value.
static constexpr size_t kFakeAidlMajorVersion = SIZE_MAX;
static constexpr VersionRange kDefaultAidlVersionRange{kFakeAidlMajorVersion,
                                                       kDefaultAidlMinorVersion};
static constexpr Version kDefaultAidlVersion = kDefaultAidlVersionRange.minVer();

#define VINTF_SUB_DIR "etc/vintf/"
constexpr const char* kVintfSubDir = VINTF_SUB_DIR;
#define SYSTEM_VINTF_DIR "/system/etc/vintf/"
constexpr const char* kSystemVintfDir = SYSTEM_VINTF_DIR;
#define VENDOR_VINTF_DIR "/vendor/etc/vintf/"
constexpr const char* kVendorVintfDir = VENDOR_VINTF_DIR;
#define ODM_VINTF_DIR "/odm/etc/vintf/"
constexpr const char* kOdmVintfDir = ODM_VINTF_DIR;
#define PRODUCT_VINTF_DIR "/product/etc/vintf/"
constexpr const char* kProductVintfDir = PRODUCT_VINTF_DIR;
#define SYSTEM_EXT_VINTF_DIR "/system_ext/etc/vintf/"
constexpr const char* kSystemExtVintfDir = SYSTEM_EXT_VINTF_DIR;

constexpr const char* kVendorManifest = VENDOR_VINTF_DIR "manifest.xml";
constexpr const char* kSystemManifest = SYSTEM_VINTF_DIR "manifest.xml";
constexpr const char* kVendorMatrix = VENDOR_VINTF_DIR "compatibility_matrix.xml";
constexpr const char* kOdmManifest = ODM_VINTF_DIR "manifest.xml";
constexpr const char* kProductMatrix = PRODUCT_VINTF_DIR "compatibility_matrix.xml";
constexpr const char* kProductManifest = PRODUCT_VINTF_DIR "manifest.xml";
constexpr const char* kSystemExtManifest = SYSTEM_EXT_VINTF_DIR "manifest.xml";

constexpr const char* kVendorManifestFragmentDir = VENDOR_VINTF_DIR "manifest/";
constexpr const char* kSystemManifestFragmentDir = SYSTEM_VINTF_DIR "manifest/";
constexpr const char* kOdmManifestFragmentDir = ODM_VINTF_DIR "manifest/";
constexpr const char* kProductManifestFragmentDir = PRODUCT_VINTF_DIR "manifest/";
constexpr const char* kSystemExtManifestFragmentDir = SYSTEM_EXT_VINTF_DIR "manifest/";

constexpr const char* kVendorLegacyManifest = "/vendor/manifest.xml";
constexpr const char* kVendorLegacyMatrix = "/vendor/compatibility_matrix.xml";
constexpr const char* kSystemLegacyManifest = "/system/manifest.xml";
constexpr const char* kSystemLegacyMatrix = "/system/compatibility_matrix.xml";
#define ODM_LEGACY_VINTF_DIR "/odm/etc/"
constexpr const char* kOdmLegacyVintfDir = ODM_LEGACY_VINTF_DIR;
constexpr const char* kOdmLegacyManifest = ODM_LEGACY_VINTF_DIR "manifest.xml";

constexpr const char* kApexInfoFile = "/apex/apex-info-list.xml";
constexpr const char* kBootstrapApexInfoFile = "/bootstrap-apex/apex-info-list.xml";

#undef SYSTEM_VINTF_DIR
#undef VENDOR_VINTF_DIR
#undef ODM_VINTF_DIR
#undef PRODUCT_VINTF_DIR
#undef SYSTEM_EXT_VINTF_DIR
#undef ODM_LEGACY_VINTF_DIR

// Device manifest with level T must not set kernel level.
constexpr Level kEnforceDeviceManifestNoKernelLevel = Level::T;

}  // namespace details
}  // namespace vintf
}  // namespace android
