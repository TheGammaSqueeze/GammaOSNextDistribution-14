#pragma once

#include <binder/IInterface.h>
#include <android/gui/IWindowInfosListener.h>

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
  explicit IWindowInfosListenerDelegator(::android::sp<IWindowInfosListener> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onWindowInfosChanged(const ::std::vector<::android::gui::WindowInfo>& windowInfos, const ::std::vector<::android::gui::DisplayInfo>& displayInfos, const ::android::sp<::android::gui::IWindowInfosReportedListener>& windowInfosReportedListener) override {
    return _aidl_delegate->onWindowInfosChanged(windowInfos, displayInfos, windowInfosReportedListener);
  }
private:
  ::android::sp<IWindowInfosListener> _aidl_delegate;
};  // class IWindowInfosListenerDelegator
}  // namespace gui
}  // namespace android
