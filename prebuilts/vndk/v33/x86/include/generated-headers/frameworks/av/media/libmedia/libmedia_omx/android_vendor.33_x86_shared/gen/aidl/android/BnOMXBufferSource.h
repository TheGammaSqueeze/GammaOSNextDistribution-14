#pragma once

#include <binder/IInterface.h>
#include <android/IOMXBufferSource.h>

namespace android {
class BnOMXBufferSource : public ::android::BnInterface<IOMXBufferSource> {
public:
  static constexpr uint32_t TRANSACTION_onOmxExecuting = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onOmxIdle = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onOmxLoaded = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onInputBufferAdded = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onInputBufferEmptied = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  explicit BnOMXBufferSource();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnOMXBufferSource

class IOMXBufferSourceDelegator : public BnOMXBufferSource {
public:
  explicit IOMXBufferSourceDelegator(::android::sp<IOMXBufferSource> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onOmxExecuting() override {
    return _aidl_delegate->onOmxExecuting();
  }
  ::android::binder::Status onOmxIdle() override {
    return _aidl_delegate->onOmxIdle();
  }
  ::android::binder::Status onOmxLoaded() override {
    return _aidl_delegate->onOmxLoaded();
  }
  ::android::binder::Status onInputBufferAdded(int32_t bufferID) override {
    return _aidl_delegate->onInputBufferAdded(bufferID);
  }
  ::android::binder::Status onInputBufferEmptied(int32_t bufferID, const ::android::OMXFenceParcelable& fenceParcel) override {
    return _aidl_delegate->onInputBufferEmptied(bufferID, fenceParcel);
  }
private:
  ::android::sp<IOMXBufferSource> _aidl_delegate;
};  // class IOMXBufferSourceDelegator
}  // namespace android
