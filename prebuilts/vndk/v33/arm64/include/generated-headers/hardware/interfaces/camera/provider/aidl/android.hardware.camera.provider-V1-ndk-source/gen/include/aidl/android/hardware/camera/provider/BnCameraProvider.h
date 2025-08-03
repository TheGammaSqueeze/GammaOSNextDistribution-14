#pragma once

#include "aidl/android/hardware/camera/provider/ICameraProvider.h"

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
namespace provider {
class BnCameraProvider : public ::ndk::BnCInterface<ICameraProvider> {
public:
  BnCameraProvider();
  virtual ~BnCameraProvider();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraProviderDelegator : public BnCameraProvider {
public:
  explicit ICameraProviderDelegator(const std::shared_ptr<ICameraProvider> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraProvider::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::provider::ICameraProviderCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus getVendorTags(std::vector<::aidl::android::hardware::camera::common::VendorTagSection>* _aidl_return) override {
    return _impl->getVendorTags(_aidl_return);
  }
  ::ndk::ScopedAStatus getCameraIdList(std::vector<std::string>* _aidl_return) override {
    return _impl->getCameraIdList(_aidl_return);
  }
  ::ndk::ScopedAStatus getCameraDeviceInterface(const std::string& in_cameraDeviceName, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDevice>* _aidl_return) override {
    return _impl->getCameraDeviceInterface(in_cameraDeviceName, _aidl_return);
  }
  ::ndk::ScopedAStatus notifyDeviceStateChange(int64_t in_deviceState) override {
    return _impl->notifyDeviceStateChange(in_deviceState);
  }
  ::ndk::ScopedAStatus getConcurrentCameraIds(std::vector<::aidl::android::hardware::camera::provider::ConcurrentCameraIdCombination>* _aidl_return) override {
    return _impl->getConcurrentCameraIds(_aidl_return);
  }
  ::ndk::ScopedAStatus isConcurrentStreamCombinationSupported(const std::vector<::aidl::android::hardware::camera::provider::CameraIdAndStreamCombination>& in_configs, bool* _aidl_return) override {
    return _impl->isConcurrentStreamCombinationSupported(in_configs, _aidl_return);
  }
protected:
private:
  std::shared_ptr<ICameraProvider> _impl;
};

}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
