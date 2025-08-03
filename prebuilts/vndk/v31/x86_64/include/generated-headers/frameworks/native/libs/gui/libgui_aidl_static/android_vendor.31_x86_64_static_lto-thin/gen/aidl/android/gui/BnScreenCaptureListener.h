#pragma once

#include <binder/IInterface.h>
#include <android/gui/IScreenCaptureListener.h>

namespace android {

namespace gui {

class BnScreenCaptureListener : public ::android::BnInterface<IScreenCaptureListener> {
public:
  static constexpr uint32_t TRANSACTION_onScreenCaptureCompleted = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnScreenCaptureListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnScreenCaptureListener

}  // namespace gui

}  // namespace android
