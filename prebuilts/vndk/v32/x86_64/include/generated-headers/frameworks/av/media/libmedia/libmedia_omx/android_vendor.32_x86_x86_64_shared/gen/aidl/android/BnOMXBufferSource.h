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

}  // namespace android
