#pragma once

#include <binder/IInterface.h>
#include <android/content/pm/IPackageManagerNative.h>

namespace android {

namespace content {

namespace pm {

class BnPackageManagerNative : public ::android::BnInterface<IPackageManagerNative> {
public:
  static constexpr uint32_t TRANSACTION_getNamesForUids = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getInstallerForPackage = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getVersionCodeForPackage = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_isAudioPlaybackCaptureAllowed = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getLocationFlags = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getTargetSdkVersionForPackage = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getModuleMetadataPackageName = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getAllPackages = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_registerPackageChangeObserver = ::android::IBinder::FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_unregisterPackageChangeObserver = ::android::IBinder::FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_hasSha256SigningCertificate = ::android::IBinder::FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_isPackageDebuggable = ::android::IBinder::FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_hasSystemFeature = ::android::IBinder::FIRST_CALL_TRANSACTION + 12;
  explicit BnPackageManagerNative();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnPackageManagerNative

}  // namespace pm

}  // namespace content

}  // namespace android
