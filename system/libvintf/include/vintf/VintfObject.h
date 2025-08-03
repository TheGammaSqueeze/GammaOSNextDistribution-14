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

#ifndef ANDROID_VINTF_VINTF_OBJECT_H_
#define ANDROID_VINTF_VINTF_OBJECT_H_

#include <map>
#include <memory>
#include <optional>
#include <string>
#include <tuple>
#include <vector>

#include <aidl/metadata.h>
#include <android-base/result.h>
#include <hidl/metadata.h>

#include <vintf/Apex.h>
#include <vintf/CheckFlags.h>
#include <vintf/CompatibilityMatrix.h>
#include <vintf/FileSystem.h>
#include <vintf/HalManifest.h>
#include <vintf/Level.h>
#include <vintf/ObjectFactory.h>
#include <vintf/PropertyFetcher.h>
#include <vintf/RuntimeInfo.h>

namespace android {
namespace vintf {

class VintfObject;

namespace details {
class CheckVintfUtils;
class FmOnlyVintfObject;
class VintfObjectBuilder;

template <typename T>
struct LockedSharedPtr {
    std::shared_ptr<T> object;
    std::mutex mutex;
    bool fetchedOnce = false;
};

struct LockedRuntimeInfoCache {
    std::shared_ptr<RuntimeInfo> object;
    std::mutex mutex;
    RuntimeInfo::FetchFlags fetchedFlags = RuntimeInfo::FetchFlag::NONE;
};

}  // namespace details

namespace testing {
class VintfObjectTestBase;
class VintfObjectRecoveryTest;
class VintfObjectRuntimeInfoTest;
class VintfObjectCompatibleTest;
}  // namespace testing

/*
 * The top level class for libvintf.
 * An overall diagram of the public API:
 * VintfObject
 *   + GetDeviceHalManfiest
 *   |   + getHidlTransport
 *   |   + checkCompatibility
 *   + GetFrameworkHalManifest
 *   |   + getHidlTransport
 *   |   + checkCompatibility
 *   + GetRuntimeInfo
 *       + checkCompatibility
 *
 * Each of the function gathers all information and encapsulate it into the object.
 * If no error, it return the same singleton object in the future, and the HAL manifest
 * file won't be touched again.
 * If any error, nullptr is returned, and Get will try to parse the HAL manifest
 * again when it is called again.
 * All these operations are thread-safe.
 */
class VintfObject {
   public:
    virtual ~VintfObject() = default;

    /*
     * Return the API that access the device-side HAL manifests built from component pieces on the
     * vendor partition.
     */
    virtual std::shared_ptr<const HalManifest> getDeviceHalManifest();

    /*
     * Return the API that access the framework-side HAL manifest built from component pieces on the
     * system partition.
     */
    virtual std::shared_ptr<const HalManifest> getFrameworkHalManifest();

    /*
     * Return the API that access the device-side compatibility matrix built from component pieces
     * on the vendor partition.
     */
    virtual std::shared_ptr<const CompatibilityMatrix> getDeviceCompatibilityMatrix();

    /*
     * Return the API that access the framework-side compatibility matrix built from component
     * pieces on the system partition.
     *
     * This automatically selects the right compatibility matrix according to the target-level
     * specified by the device.
     */
    virtual std::shared_ptr<const CompatibilityMatrix> getFrameworkCompatibilityMatrix();

    /*
     * Return the API that access device runtime info.
     *
     * {skipCache == true, flags == ALL}: re-fetch everything
     * {skipCache == false, flags == ALL}: fetch everything if not previously fetched
     * {skipCache == true, flags == selected info}: re-fetch selected information
     *                                if not previously fetched.
     * {skipCache == false, flags == selected info}: fetch selected information
     *                                if not previously fetched.
     *
     * @param skipCache do not fetch if previously fetched
     * @param flags bitwise-or of RuntimeInfo::FetchFlag
     */
    std::shared_ptr<const RuntimeInfo> getRuntimeInfo(
        RuntimeInfo::FetchFlags flags = RuntimeInfo::FetchFlag::ALL);

    /**
     * Check compatibility on the device.
     *
     * @param error error message
     * @param flags flags to disable certain checks. See CheckFlags.
     *
     * @return = 0 if success (compatible)
     *         > 0 if incompatible
     *         < 0 if any error (mount partition fails, illformed XML, etc.)
     */
    int32_t checkCompatibility(std::string* error = nullptr,
                               CheckFlags::Type flags = CheckFlags::DEFAULT);

    /**
     * Check deprecation on existing VINTF metadata. Use Device Manifest as the
     * predicate to check if a HAL is in use.
     *
     * @return = 0 if success (no deprecated HALs)
     *         > 0 if there is at least one deprecated HAL
     *         < 0 if any error (mount partition fails, illformed XML, etc.)
     */
    int32_t checkDeprecation(const std::vector<HidlInterfaceMetadata>& hidlMetadata,
                             std::string* error = nullptr);

    /**
     * Return kernel FCM version.
     *
     * If any error, UNSPECIFIED is returned, and error is set to an error message.
     */
    Level getKernelLevel(std::string* error = nullptr);

    /**
     * Returns true if the framework compatibility matrix has extensions. In
     * other words, returns true if any of the following exists on the device:
     * - device framework compatibility matrix
     * - product framework compatibility matrix
     * - system_ext framework compatibility matrix
     *
     * Return result:
     * - true if framework compatibility matrix has extensions
     * - false if framework compatibility
     *     matrix does not have extensions.
     * - !result.has_value() if any error. Check
     *     result.error() for detailed message.
     */
    android::base::Result<bool> hasFrameworkCompatibilityMatrixExtensions();

    /**
     * Check that there are no unused HALs in HAL manifests. Currently, only
     * device manifest is checked against framework compatibility matrix.
     *
     * Return result:
     * - result.ok() if no unused HALs
     * - !result.ok() && result.error().code() == 0 if with unused HALs. Check
     *     result.error() for detailed message.
     * - !result.ok() && result.error().code() != 0 if any error. Check
     *     result.error() for detailed message.
     */
    android::base::Result<void> checkUnusedHals(
        const std::vector<HidlInterfaceMetadata>& hidlMetadata);

    // Check that all HALs are added to any framework compatibility matrix.
    // If shouldCheck is set, only check if:
    // - For HIDL, shouldCheck(packageAndVersion) (e.g. android.hardware.foo@1.0)
    // - For AIDL and native, shouldCheck(package) (e.g. android.hardware.foo)
    android::base::Result<void> checkMissingHalsInMatrices(
        const std::vector<HidlInterfaceMetadata>& hidlMetadata,
        const std::vector<AidlInterfaceMetadata>& aidlMetadata,
        std::function<bool(const std::string&)> shouldCheckHidl,
        std::function<bool(const std::string&)> shouldCheckAidl);

    // Check that all HALs in all framework compatibility matrices have the
    // proper interface definition (HIDL / AIDL files).
    android::base::Result<void> checkMatrixHalsHasDefinition(
        const std::vector<HidlInterfaceMetadata>& hidlMetadata,
        const std::vector<AidlInterfaceMetadata>& aidlMetadata);

    // Get the latest <kernel> minlts for compatibility matrix level |fcmVersion|.
    android::base::Result<KernelVersion> getLatestMinLtsAtFcmVersion(Level fcmVersion);

   private:
    std::unique_ptr<FileSystem> mFileSystem;
    std::unique_ptr<ObjectFactory<RuntimeInfo>> mRuntimeInfoFactory;
    std::unique_ptr<PropertyFetcher> mPropertyFetcher;
    std::unique_ptr<ApexInterface> mApex;
    details::LockedSharedPtr<HalManifest> mDeviceManifest;
    details::LockedSharedPtr<HalManifest> mFrameworkManifest;
    details::LockedSharedPtr<CompatibilityMatrix> mDeviceMatrix;

    // Parent lock of the following fields. It should be acquired before locking the child locks.
    std::mutex mFrameworkCompatibilityMatrixMutex;
    details::LockedSharedPtr<CompatibilityMatrix> mFrameworkMatrix;
    details::LockedSharedPtr<CompatibilityMatrix> mCombinedFrameworkMatrix;
    // End of mFrameworkCompatibilityMatrixMutex

    details::LockedRuntimeInfoCache mDeviceRuntimeInfo;

    bool getCheckAidlCompatMatrix();
    std::optional<bool> mFakeCheckAidlCompatibilityMatrix;

    // Expose functions for testing and recovery
    friend class testing::VintfObjectTestBase;
    friend class testing::VintfObjectRecoveryTest;
    friend class testing::VintfObjectRuntimeInfoTest;
    friend class testing::VintfObjectCompatibleTest;

    // Expose functions to simulate dependency injection.
    friend class details::VintfObjectBuilder;
    friend class details::CheckVintfUtils;
    friend class details::FmOnlyVintfObject;

   protected:
    void setFakeCheckAidlCompatMatrix(bool check) { mFakeCheckAidlCompatibilityMatrix = check; }
    virtual const std::unique_ptr<FileSystem>& getFileSystem();
    virtual const std::unique_ptr<PropertyFetcher>& getPropertyFetcher();
    virtual const std::unique_ptr<ObjectFactory<RuntimeInfo>>& getRuntimeInfoFactory();
    virtual const std::unique_ptr<ApexInterface>& getApex();

   public:
    /*
     * Get global instance. Results are cached.
     */
    static std::shared_ptr<VintfObject> GetInstance();

    // Static variants of member functions.

    /*
     * Return the API that access the device-side HAL manifest built from component pieces on the
     * vendor partition.
     */
    static std::shared_ptr<const HalManifest> GetDeviceHalManifest();

    /*
     * Return the API that access the framework-side HAL manifest built from component pieces on the
     * system partition.
     */
    static std::shared_ptr<const HalManifest> GetFrameworkHalManifest();

    /*
     * Return the API that access the device-side compatibility matrix built from component pieces
     * on the vendor partition.
     */
    static std::shared_ptr<const CompatibilityMatrix> GetDeviceCompatibilityMatrix();

    /*
     * Return the API that access the framework-side compatibility matrix built from component
     * pieces on the system partition.
     */
    static std::shared_ptr<const CompatibilityMatrix> GetFrameworkCompatibilityMatrix();

    /*
     * Return the API that access device runtime info.
     *
     * @param flags bitwise-or of RuntimeInfo::FetchFlag
     *   flags == ALL: fetch everything if not previously fetched
     *   flags == selected info: fetch selected information if not previously fetched.
     */
    static std::shared_ptr<const RuntimeInfo> GetRuntimeInfo(
        RuntimeInfo::FetchFlags flags = RuntimeInfo::FetchFlag::ALL);

   protected:
    status_t getCombinedFrameworkMatrix(const std::shared_ptr<const HalManifest>& deviceManifest,
                                        Level kernelLevel, CompatibilityMatrix* out,
                                        std::string* error = nullptr);
    status_t getAllFrameworkMatrixLevels(std::vector<CompatibilityMatrix>* out,
                                         std::string* error = nullptr);
    status_t getOneMatrix(const std::string& path, CompatibilityMatrix* out,
                          std::string* error = nullptr);
    status_t addDirectoryManifests(const std::string& directory, HalManifest* manifests,
                                   bool ignoreSchemaType, std::string* error);
    status_t fetchDeviceHalManifest(HalManifest* out, std::string* error = nullptr);
    status_t fetchDeviceHalManifestMinusApex(HalManifest* out, std::string* error = nullptr);
    status_t fetchDeviceHalManifestApex(HalManifest* out, std::string* error = nullptr);
    status_t fetchDeviceMatrix(CompatibilityMatrix* out, std::string* error = nullptr);
    status_t fetchOdmHalManifest(HalManifest* out, std::string* error = nullptr);
    status_t fetchOneHalManifest(const std::string& path, HalManifest* out,
                                 std::string* error = nullptr);
    status_t fetchVendorHalManifest(HalManifest* out, std::string* error = nullptr);
    status_t fetchFrameworkHalManifest(HalManifest* out, std::string* error = nullptr);

    status_t fetchUnfilteredFrameworkHalManifest(HalManifest* out, std::string* error);
    void filterHalsByDeviceManifestLevel(HalManifest* out);

    // Helper for checking matrices against lib*idlmetadata. Wrapper of the other variant of
    // getAllFrameworkMatrixLevels. Treat empty output as an error.
    android::base::Result<std::vector<CompatibilityMatrix>> getAllFrameworkMatrixLevels();

    using ChildrenMap = std::multimap<std::string, std::string>;
    static bool IsHalDeprecated(const MatrixHal& oldMatrixHal,
                                const std::string& oldMatrixHalFileName,
                                const CompatibilityMatrix& targetMatrix,
                                const std::shared_ptr<const HalManifest>& halManifest,
                                const ChildrenMap& childrenMap, std::string* appendedError);
    static bool IsInstanceDeprecated(const MatrixInstance& oldMatrixInstance,
                                     const std::string& oldMatrixInstanceFileName,
                                     const CompatibilityMatrix& targetMatrix,
                                     const std::shared_ptr<const HalManifest>& halManifest,
                                     const ChildrenMap& childrenMap, std::string* appendedError);

    static android::base::Result<std::vector<FqInstance>> GetListedInstanceInheritance(
        HalFormat format, const std::string& package, const Version& version,
        const std::string& interface, const std::string& instance,
        const std::shared_ptr<const HalManifest>& halManifest, const ChildrenMap& childrenMap);
    static bool IsInstanceListed(const std::shared_ptr<const HalManifest>& halManifest,
                                 HalFormat format, const FqInstance& fqInstance);
    static android::base::Result<void> IsFqInstanceDeprecated(
        const CompatibilityMatrix& targetMatrix, HalFormat format, const FqInstance& fqInstance,
        const std::shared_ptr<const HalManifest>& halManifest);

   public:
    class Builder;

   protected:
    /* Empty VintfObject without any dependencies. Used by Builder and subclasses. */
    VintfObject() = default;
};

enum : int32_t {
    COMPATIBLE = 0,
    INCOMPATIBLE = 1,

    NO_DEPRECATED_HALS = 0,
    DEPRECATED = 1,
};

// exposed for testing.
namespace details {

/**
 * DO NOT USE outside of libvintf. This is an implementation detail. Use VintfObject::Builder
 * instead.
 *
 * A builder of VintfObject. If a dependency is not specified, the default behavior is used.
 * - FileSystem fetch from "/" for target and fetch no files for host
 * - ObjectFactory<RuntimeInfo> fetches default RuntimeInfo for target and nothing for host
 * - PropertyFetcher fetches properties for target and nothing for host
 */
class VintfObjectBuilder {
   public:
    VintfObjectBuilder(std::unique_ptr<VintfObject>&& object) : mObject(std::move(object)) {}
    ~VintfObjectBuilder();
    VintfObjectBuilder& setFileSystem(std::unique_ptr<FileSystem>&&);
    VintfObjectBuilder& setRuntimeInfoFactory(std::unique_ptr<ObjectFactory<RuntimeInfo>>&&);
    VintfObjectBuilder& setPropertyFetcher(std::unique_ptr<PropertyFetcher>&&);
    VintfObjectBuilder& setApex(std::unique_ptr<ApexInterface>&&);
    template <typename VintfObjectType = VintfObject>
    std::unique_ptr<VintfObjectType> build() {
        return std::unique_ptr<VintfObjectType>(
            static_cast<VintfObjectType*>(buildInternal().release()));
    }

   private:
    std::unique_ptr<VintfObject> buildInternal();
    std::unique_ptr<VintfObject> mObject;
};

// Convenience function to dump all files and directories that could be read
// by calling Get(Framework|Device)(HalManifest|CompatibilityMatrix). The list
// include files that may not actually be read when the four functions are called
// because some files have a higher priority than others. The list does NOT
// include "files" (including kernel interfaces) that are read when GetRuntimeInfo
// is called.
// The sku string from ro.boot.product.hardware.sku is needed to build the ODM
// manifest file name for legacy devices.
std::vector<std::string> dumpFileList(const std::string& sku);

}  // namespace details

/** Builder of VintfObject. See VintfObjectBuilder for details. */
class VintfObject::Builder : public details::VintfObjectBuilder {
   public:
    Builder();
};

}  // namespace vintf
}  // namespace android

#endif  // ANDROID_VINTF_VINTF_OBJECT_H_
