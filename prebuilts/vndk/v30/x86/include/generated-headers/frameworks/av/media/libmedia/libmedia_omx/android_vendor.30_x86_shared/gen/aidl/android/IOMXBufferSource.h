#ifndef AIDL_GENERATED_ANDROID_I_O_M_X_BUFFER_SOURCE_H_
#define AIDL_GENERATED_ANDROID_I_O_M_X_BUFFER_SOURCE_H_

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <media/OMXFenceParcelable.h>
#include <utils/StrongPointer.h>

namespace android {

class IOMXBufferSource : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(OMXBufferSource)
  virtual ::android::binder::Status onOmxExecuting() = 0;
  virtual ::android::binder::Status onOmxIdle() = 0;
  virtual ::android::binder::Status onOmxLoaded() = 0;
  virtual ::android::binder::Status onInputBufferAdded(int32_t bufferID) = 0;
  virtual ::android::binder::Status onInputBufferEmptied(int32_t bufferID, const ::android::OMXFenceParcelable& fenceParcel) = 0;
};  // class IOMXBufferSource

class IOMXBufferSourceDefault : public IOMXBufferSource {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onOmxExecuting() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onOmxIdle() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onOmxLoaded() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onInputBufferAdded(int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onInputBufferEmptied(int32_t, const ::android::OMXFenceParcelable&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IOMXBufferSourceDefault

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_I_O_M_X_BUFFER_SOURCE_H_
