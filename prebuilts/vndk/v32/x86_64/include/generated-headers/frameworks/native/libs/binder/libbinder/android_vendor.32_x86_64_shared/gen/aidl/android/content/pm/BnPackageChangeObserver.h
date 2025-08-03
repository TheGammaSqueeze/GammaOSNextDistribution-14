#pragma once

#include <binder/IInterface.h>
#include <android/content/pm/IPackageChangeObserver.h>

namespace android {

namespace content {

namespace pm {

class BnPackageChangeObserver : public ::android::BnInterface<IPackageChangeObserver> {
public:
  static constexpr uint32_t TRANSACTION_onPackageChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnPackageChangeObserver();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnPackageChangeObserver

}  // namespace pm

}  // namespace content

}  // namespace android
