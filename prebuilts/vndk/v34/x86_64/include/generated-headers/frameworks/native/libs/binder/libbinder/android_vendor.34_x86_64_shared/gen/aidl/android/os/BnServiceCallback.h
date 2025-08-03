#pragma once

#include <binder/IInterface.h>
#include <android/os/IServiceCallback.h>
#include <android/os/BnServiceCallback.h>
#include <binder/Delegate.h>


namespace android {
namespace os {
class BnServiceCallback : public ::android::BnInterface<IServiceCallback> {
public:
  static constexpr uint32_t TRANSACTION_onRegistration = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnServiceCallback();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnServiceCallback

class IServiceCallbackDelegator : public BnServiceCallback {
public:
  explicit IServiceCallbackDelegator(const ::android::sp<IServiceCallback> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IServiceCallback> getImpl() { return _aidl_delegate; }
  ::android::binder::Status onRegistration(const ::std::string& name, const ::android::sp<::android::IBinder>& binder) override {
    return _aidl_delegate->onRegistration(name, binder);
  }
private:
  ::android::sp<IServiceCallback> _aidl_delegate;
};  // class IServiceCallbackDelegator
}  // namespace os
}  // namespace android
