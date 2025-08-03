#pragma once

#include <binder/IInterface.h>
#include <android/gui/IHdrLayerInfoListener.h>

namespace android {

namespace gui {

class BnHdrLayerInfoListener : public ::android::BnInterface<IHdrLayerInfoListener> {
public:
  static constexpr uint32_t TRANSACTION_onHdrLayerInfoChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnHdrLayerInfoListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnHdrLayerInfoListener

}  // namespace gui

}  // namespace android
