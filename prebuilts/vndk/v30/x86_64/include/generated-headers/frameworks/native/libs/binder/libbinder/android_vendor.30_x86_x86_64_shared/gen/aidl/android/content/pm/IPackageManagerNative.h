#ifndef AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_MANAGER_NATIVE_H_
#define AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_MANAGER_NATIVE_H_

#include <android/content/pm/IPackageChangeObserver.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <vector>

namespace android {

namespace content {

namespace pm {

class IPackageManagerNative : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(PackageManagerNative)
  enum  : int32_t {
    LOCATION_SYSTEM = 1,
    LOCATION_VENDOR = 2,
    LOCATION_PRODUCT = 4,
  };
  virtual ::android::binder::Status getNamesForUids(const ::std::vector<int32_t>& uids, ::std::vector<::std::string>* _aidl_return) = 0;
  virtual ::android::binder::Status getInstallerForPackage(const ::android::String16& packageName, ::std::string* _aidl_return) = 0;
  virtual ::android::binder::Status getVersionCodeForPackage(const ::android::String16& packageName, int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status isAudioPlaybackCaptureAllowed(const ::std::vector<::std::string>& packageNames, ::std::vector<bool>* _aidl_return) = 0;
  virtual ::android::binder::Status getLocationFlags(const ::std::string& packageName, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status getTargetSdkVersionForPackage(const ::android::String16& packageName, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status getModuleMetadataPackageName(::std::string* _aidl_return) = 0;
  virtual ::android::binder::Status getAllPackages(::std::vector<::std::string>* _aidl_return) = 0;
  virtual ::android::binder::Status registerPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>& observer) = 0;
  virtual ::android::binder::Status unregisterPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>& observer) = 0;
};  // class IPackageManagerNative

class IPackageManagerNativeDefault : public IPackageManagerNative {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status getNamesForUids(const ::std::vector<int32_t>&, ::std::vector<::std::string>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getInstallerForPackage(const ::android::String16&, ::std::string*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getVersionCodeForPackage(const ::android::String16&, int64_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status isAudioPlaybackCaptureAllowed(const ::std::vector<::std::string>&, ::std::vector<bool>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getLocationFlags(const ::std::string&, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getTargetSdkVersionForPackage(const ::android::String16&, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getModuleMetadataPackageName(::std::string*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getAllPackages(::std::vector<::std::string>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status registerPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status unregisterPackageChangeObserver(const ::android::sp<::android::content::pm::IPackageChangeObserver>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IPackageManagerNativeDefault

}  // namespace pm

}  // namespace content

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_MANAGER_NATIVE_H_
