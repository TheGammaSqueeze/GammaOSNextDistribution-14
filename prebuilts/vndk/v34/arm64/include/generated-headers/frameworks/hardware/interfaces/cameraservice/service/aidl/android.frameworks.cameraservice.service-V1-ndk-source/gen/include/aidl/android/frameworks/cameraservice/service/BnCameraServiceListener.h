#pragma once

#include "aidl/android/frameworks/cameraservice/service/ICameraServiceListener.h"

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
namespace service {
class BnCameraServiceListener : public ::ndk::BnCInterface<ICameraServiceListener> {
public:
  BnCameraServiceListener();
  virtual ~BnCameraServiceListener();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraServiceListenerDelegator : public BnCameraServiceListener {
public:
  explicit ICameraServiceListenerDelegator(const std::shared_ptr<ICameraServiceListener> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraServiceListener::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId, const std::string& in_physicalCameraId) override {
    return _impl->onPhysicalCameraStatusChanged(in_status, in_cameraId, in_physicalCameraId);
  }
  ::ndk::ScopedAStatus onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId) override {
    return _impl->onStatusChanged(in_status, in_cameraId);
  }
protected:
private:
  std::shared_ptr<ICameraServiceListener> _impl;
};

}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
