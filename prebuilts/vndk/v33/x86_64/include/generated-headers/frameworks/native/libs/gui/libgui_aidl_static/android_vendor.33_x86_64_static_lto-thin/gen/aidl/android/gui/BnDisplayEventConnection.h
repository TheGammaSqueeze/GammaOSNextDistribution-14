#pragma once

#include <binder/IInterface.h>
#include <android/gui/IDisplayEventConnection.h>

namespace android {
namespace gui {
class BnDisplayEventConnection : public ::android::BnInterface<IDisplayEventConnection> {
public:
  static constexpr uint32_t TRANSACTION_stealReceiveChannel = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_setVsyncRate = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_requestNextVsync = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getLatestVsyncEventData = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  explicit BnDisplayEventConnection();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnDisplayEventConnection

class IDisplayEventConnectionDelegator : public BnDisplayEventConnection {
public:
  explicit IDisplayEventConnectionDelegator(::android::sp<IDisplayEventConnection> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status stealReceiveChannel(::android::gui::BitTube* outChannel) override {
    return _aidl_delegate->stealReceiveChannel(outChannel);
  }
  ::android::binder::Status setVsyncRate(int32_t count) override {
    return _aidl_delegate->setVsyncRate(count);
  }
  ::android::binder::Status requestNextVsync() override {
    return _aidl_delegate->requestNextVsync();
  }
  ::android::binder::Status getLatestVsyncEventData(::android::gui::ParcelableVsyncEventData* _aidl_return) override {
    return _aidl_delegate->getLatestVsyncEventData(_aidl_return);
  }
private:
  ::android::sp<IDisplayEventConnection> _aidl_delegate;
};  // class IDisplayEventConnectionDelegator
}  // namespace gui
}  // namespace android
