#pragma once

#include "aidl/android/hardware/camera/device/ICameraInjectionSession.h"

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
class BnCameraInjectionSession : public ::ndk::BnCInterface<ICameraInjectionSession> {
public:
  BnCameraInjectionSession();
  virtual ~BnCameraInjectionSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraInjectionSessionDelegator : public BnCameraInjectionSession {
public:
  explicit ICameraInjectionSessionDelegator(const std::shared_ptr<ICameraInjectionSession> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraInjectionSession::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus configureInjectionStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, const ::aidl::android::hardware::camera::device::CameraMetadata& in_characteristics) override {
    return _impl->configureInjectionStreams(in_requestedConfiguration, in_characteristics);
  }
  ::ndk::ScopedAStatus getCameraDeviceSession(std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override {
    return _impl->getCameraDeviceSession(_aidl_return);
  }
protected:
private:
  std::shared_ptr<ICameraInjectionSession> _impl;
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
