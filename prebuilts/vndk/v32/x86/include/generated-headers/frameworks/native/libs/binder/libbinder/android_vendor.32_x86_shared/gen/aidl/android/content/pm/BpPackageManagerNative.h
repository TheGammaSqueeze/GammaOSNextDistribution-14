#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/content/pm/IPackageManagerNative.h>

namespace android {

namespace content {

namespace pm {

class BpPackageManagerNative : public ::android::BpInterface<IPackageManagerNative> {
public:
  explicit BpPackageManagerNative(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpPackageManagerNative() = default;
  ::android::binder::Status getNamesForUids(const ::std::vector<int32_t>& uids, ::std::vector<::std::string>* _aidl_return) override;
  ::android::binder::Status getInstallerForPackage(const ::android::String16& packageName, ::std::string* _aidl_return) override;
  ::android::binder::Status getVersionCodeForPackage(const ::android::String16& packageName, int64_t* _aidl_return) override;
  ::android::binder::Status isAudioPlaybackCaptureAllowed(const ::std::vector<::std::string>& packageNames, ::std::vector<bool>* _aidl_return) override;
  ::android::binder::Status getLocationFlags(const ::std::string& packageName, int32_t* _aidl_return) override;
  ::android::binder::Status getTargetSdkVersionForPackage(const ::android::String16& packageName, int32_t* _aidl_return) override;
  ::android::binder::Status getModuleMetadataPackageName(::std::string* _aidl_return) override;
  ::android::binder::Status getAllPackages(::std::vector<::std::string>* _aidl_return) override;
  ::android::binder::Status registerPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>& observer) override;
  ::android::binder::Status unregisterPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>& observer) override;
  ::android::binder::Status hasSha256SigningCertificate(const ::std::string& packageName, const ::std::vector<uint8_t>& certificate, bool* _aidl_return) override;
  ::android::binder::Status isPackageDebuggable(const ::android::String16& packageName, bool* _aidl_return) override;
  ::android::binder::Status hasSystemFeature(const ::android::String16& featureName, int32_t version, bool* _aidl_return) override;
};  // class BpPackageManagerNative

}  // namespace pm

}  // namespace content

}  // namespace android
