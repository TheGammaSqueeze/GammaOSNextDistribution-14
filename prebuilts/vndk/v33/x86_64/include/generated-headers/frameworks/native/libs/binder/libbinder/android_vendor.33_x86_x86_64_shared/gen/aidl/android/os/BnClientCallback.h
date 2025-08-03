#pragma once

#include <binder/IInterface.h>
#include <android/os/IClientCallback.h>

namespace android {
namespace os {
class BnClientCallback : public ::android::BnInterface<IClientCallback> {
public:
  static constexpr uint32_t TRANSACTION_onClients = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnClientCallback();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnClientCallback

class IClientCallbackDelegator : public BnClientCallback {
public:
  explicit IClientCallbackDelegator(::android::sp<IClientCallback> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onClients(const ::android::sp<::android::IBinder>& registered, bool hasClients) override {
    return _aidl_delegate->onClients(registered, hasClients);
  }
private:
  ::android::sp<IClientCallback> _aidl_delegate;
};  // class IClientCallbackDelegator
}  // namespace os
}  // namespace android
