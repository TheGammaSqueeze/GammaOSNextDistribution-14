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

class IHdrLayerInfoListenerDelegator : public BnHdrLayerInfoListener {
public:
  explicit IHdrLayerInfoListenerDelegator(::android::sp<IHdrLayerInfoListener> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onHdrLayerInfoChanged(int32_t numberOfHdrLayers, int32_t maxW, int32_t maxH, int32_t flags) override {
    return _aidl_delegate->onHdrLayerInfoChanged(numberOfHdrLayers, maxW, maxH, flags);
  }
private:
  ::android::sp<IHdrLayerInfoListener> _aidl_delegate;
};  // class IHdrLayerInfoListenerDelegator
}  // namespace gui
}  // namespace android
