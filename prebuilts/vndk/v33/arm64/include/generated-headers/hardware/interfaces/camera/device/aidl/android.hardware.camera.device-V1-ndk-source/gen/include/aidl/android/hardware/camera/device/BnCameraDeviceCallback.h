#pragma once

#include "aidl/android/hardware/camera/device/ICameraDeviceCallback.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BnCameraDeviceCallback : public ::ndk::BnCInterface<ICameraDeviceCallback> {
public:
  BnCameraDeviceCallback();
  virtual ~BnCameraDeviceCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraDeviceCallbackDelegator : public BnCameraDeviceCallback {
public:
  explicit ICameraDeviceCallbackDelegator(const std::shared_ptr<ICameraDeviceCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraDeviceCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus notify(const std::vector<::aidl::android::hardware::camera::device::NotifyMsg>& in_msgs) override {
    return _impl->notify(in_msgs);
  }
  ::ndk::ScopedAStatus processCaptureResult(const std::vector<::aidl::android::hardware::camera::device::CaptureResult>& in_results) override {
    return _impl->processCaptureResult(in_results);
  }
  ::ndk::ScopedAStatus requestStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::BufferRequest>& in_bufReqs, std::vector<::aidl::android::hardware::camera::device::StreamBufferRet>* out_buffers, ::aidl::android::hardware::camera::device::BufferRequestStatus* _aidl_return) override {
    return _impl->requestStreamBuffers(in_bufReqs, out_buffers, _aidl_return);
  }
  ::ndk::ScopedAStatus returnStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::StreamBuffer>& in_buffers) override {
    return _impl->returnStreamBuffers(in_buffers);
  }
protected:
private:
  std::shared_ptr<ICameraDeviceCallback> _impl;
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
