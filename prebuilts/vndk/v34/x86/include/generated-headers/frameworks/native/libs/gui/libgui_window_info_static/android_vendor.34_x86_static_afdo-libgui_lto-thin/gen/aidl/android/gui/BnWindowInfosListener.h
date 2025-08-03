#pragma once

#include <binder/IInterface.h>
#include <android/gui/IWindowInfosListener.h>
#include <android/gui/BnWindowInfosListener.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnWindowInfosListener : public ::android::BnInterface<IWindowInfosListener> {
public:
  static constexpr uint32_t TRANSACTION_onWindowInfosChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnWindowInfosListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnWindowInfosListener

class IWindowInfosListenerDelegator : public BnWindowInfosListener {
public:
  explicit IWindowInfosListenerDelegator(const ::android::sp<IWindowInfosListener> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IWindowInfosListener> getImpl() { return _aidl_delegate; }
  ::android::binder::Status onWindowInfosChanged(const ::android::gui::WindowInfosUpdate& update) override {
    return _aidl_delegate->onWindowInfosChanged(update);
  }
private:
  ::android::sp<IWindowInfosListener> _aidl_delegate;
};  // class IWindowInfosListenerDelegator
}  // namespace gui
}  // namespace android
