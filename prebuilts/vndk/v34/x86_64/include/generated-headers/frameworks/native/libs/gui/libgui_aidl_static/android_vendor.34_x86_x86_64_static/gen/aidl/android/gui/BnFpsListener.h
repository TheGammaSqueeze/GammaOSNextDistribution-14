#pragma once

#include <binder/IInterface.h>
#include <android/gui/IFpsListener.h>
#include <android/gui/BnFpsListener.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnFpsListener : public ::android::BnInterface<IFpsListener> {
public:
  static constexpr uint32_t TRANSACTION_onFpsReported = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnFpsListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnFpsListener

class IFpsListenerDelegator : public BnFpsListener {
public:
  explicit IFpsListenerDelegator(const ::android::sp<IFpsListener> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IFpsListener> getImpl() { return _aidl_delegate; }
  ::android::binder::Status onFpsReported(float fps) override {
    return _aidl_delegate->onFpsReported(fps);
  }
private:
  ::android::sp<IFpsListener> _aidl_delegate;
};  // class IFpsListenerDelegator
}  // namespace gui
}  // namespace android
