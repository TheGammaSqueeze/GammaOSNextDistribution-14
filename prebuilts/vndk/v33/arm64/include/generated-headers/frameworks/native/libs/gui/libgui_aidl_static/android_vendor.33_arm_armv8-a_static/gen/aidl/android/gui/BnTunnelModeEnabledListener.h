#pragma once

#include <binder/IInterface.h>
#include <android/gui/ITunnelModeEnabledListener.h>

namespace android {
namespace gui {
class BnTunnelModeEnabledListener : public ::android::BnInterface<ITunnelModeEnabledListener> {
public:
  static constexpr uint32_t TRANSACTION_onTunnelModeEnabledChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnTunnelModeEnabledListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnTunnelModeEnabledListener

class ITunnelModeEnabledListenerDelegator : public BnTunnelModeEnabledListener {
public:
  explicit ITunnelModeEnabledListenerDelegator(::android::sp<ITunnelModeEnabledListener> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onTunnelModeEnabledChanged(bool enabled) override {
    return _aidl_delegate->onTunnelModeEnabledChanged(enabled);
  }
private:
  ::android::sp<ITunnelModeEnabledListener> _aidl_delegate;
};  // class ITunnelModeEnabledListenerDelegator
}  // namespace gui
}  // namespace android
