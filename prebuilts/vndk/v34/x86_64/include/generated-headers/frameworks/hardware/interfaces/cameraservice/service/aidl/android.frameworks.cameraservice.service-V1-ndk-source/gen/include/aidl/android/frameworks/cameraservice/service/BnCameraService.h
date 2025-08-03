#pragma once

#include "aidl/android/frameworks/cameraservice/service/ICameraService.h"

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
class BnCameraService : public ::ndk::BnCInterface<ICameraService> {
public:
  BnCameraService();
  virtual ~BnCameraService();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraServiceDelegator : public BnCameraService {
public:
  explicit ICameraServiceDelegator(const std::shared_ptr<ICameraService> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraService::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus addListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener, std::vector<::aidl::android::frameworks::cameraservice::service::CameraStatusAndId>* _aidl_return) override {
    return _impl->addListener(in_listener, _aidl_return);
  }
  ::ndk::ScopedAStatus connectDevice(const std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceCallback>& in_callback, const std::string& in_cameraId, std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceUser>* _aidl_return) override {
    return _impl->connectDevice(in_callback, in_cameraId, _aidl_return);
  }
  ::ndk::ScopedAStatus getCameraCharacteristics(const std::string& in_cameraId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) override {
    return _impl->getCameraCharacteristics(in_cameraId, _aidl_return);
  }
  ::ndk::ScopedAStatus getCameraVendorTagSections(std::vector<::aidl::android::frameworks::cameraservice::common::ProviderIdAndVendorTagSections>* _aidl_return) override {
    return _impl->getCameraVendorTagSections(_aidl_return);
  }
  ::ndk::ScopedAStatus removeListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener) override {
    return _impl->removeListener(in_listener);
  }
protected:
private:
  std::shared_ptr<ICameraService> _impl;
};

}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
