#pragma once

#include <binder/IInterface.h>
#include <android/gui/IRegionSamplingListener.h>

namespace android {
namespace gui {
class BnRegionSamplingListener : public ::android::BnInterface<IRegionSamplingListener> {
public:
  static constexpr uint32_t TRANSACTION_onSampleCollected = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnRegionSamplingListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnRegionSamplingListener

class IRegionSamplingListenerDelegator : public BnRegionSamplingListener {
public:
  explicit IRegionSamplingListenerDelegator(::android::sp<IRegionSamplingListener> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onSampleCollected(float medianLuma) override {
    return _aidl_delegate->onSampleCollected(medianLuma);
  }
private:
  ::android::sp<IRegionSamplingListener> _aidl_delegate;
};  // class IRegionSamplingListenerDelegator
}  // namespace gui
}  // namespace android
