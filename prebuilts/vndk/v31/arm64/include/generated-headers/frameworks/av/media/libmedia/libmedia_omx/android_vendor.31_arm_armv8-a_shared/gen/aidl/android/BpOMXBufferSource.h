#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/IOMXBufferSource.h>

namespace android {

class BpOMXBufferSource : public ::android::BpInterface<IOMXBufferSource> {
public:
  explicit BpOMXBufferSource(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpOMXBufferSource() = default;
  ::android::binder::Status onOmxExecuting() override;
  ::android::binder::Status onOmxIdle() override;
  ::android::binder::Status onOmxLoaded() override;
  ::android::binder::Status onInputBufferAdded(int32_t bufferID) override;
  ::android::binder::Status onInputBufferEmptied(int32_t bufferID, const ::android::OMXFenceParcelable& fenceParcel) override;
};  // class BpOMXBufferSource

}  // namespace android
