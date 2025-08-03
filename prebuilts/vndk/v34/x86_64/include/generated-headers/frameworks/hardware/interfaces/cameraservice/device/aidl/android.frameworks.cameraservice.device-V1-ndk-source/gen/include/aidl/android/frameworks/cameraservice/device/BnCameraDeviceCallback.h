#pragma once

#include "aidl/android/frameworks/cameraservice/device/ICameraDeviceCallback.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
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

  ::ndk::ScopedAStatus onCaptureStarted(const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, int64_t in_timestamp) override {
    return _impl->onCaptureStarted(in_resultExtras, in_timestamp);
  }
  ::ndk::ScopedAStatus onDeviceError(::aidl::android::frameworks::cameraservice::device::ErrorCode in_errorCode, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras) override {
    return _impl->onDeviceError(in_errorCode, in_resultExtras);
  }
  ::ndk::ScopedAStatus onDeviceIdle() override {
    return _impl->onDeviceIdle();
  }
  ::ndk::ScopedAStatus onPrepared(int32_t in_streamId) override {
    return _impl->onPrepared(in_streamId);
  }
  ::ndk::ScopedAStatus onRepeatingRequestError(int64_t in_lastFrameNumber, int32_t in_repeatingRequestId) override {
    return _impl->onRepeatingRequestError(in_lastFrameNumber, in_repeatingRequestId);
  }
  ::ndk::ScopedAStatus onResultReceived(const ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo& in_result, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, const std::vector<::aidl::android::frameworks::cameraservice::device::PhysicalCaptureResultInfo>& in_physicalCaptureResultInfos) override {
    return _impl->onResultReceived(in_result, in_resultExtras, in_physicalCaptureResultInfos);
  }
protected:
private:
  std::shared_ptr<ICameraDeviceCallback> _impl;
};

}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
