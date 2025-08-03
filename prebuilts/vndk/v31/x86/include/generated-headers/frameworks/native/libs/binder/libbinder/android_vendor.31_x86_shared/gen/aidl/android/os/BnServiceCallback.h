#pragma once

#include <binder/IInterface.h>
#include <android/os/IServiceCallback.h>

namespace android {

namespace os {

class BnServiceCallback : public ::android::BnInterface<IServiceCallback> {
public:
  static constexpr uint32_t TRANSACTION_onRegistration = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnServiceCallback();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnServiceCallback

}  // namespace os

}  // namespace android
