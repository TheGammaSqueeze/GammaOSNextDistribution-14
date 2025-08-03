#pragma once

#include "aidl/android/hardware/camera/device/ICameraDevice.h"

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
class BnCameraDevice : public ::ndk::BnCInterface<ICameraDevice> {
public:
  BnCameraDevice();
  virtual ~BnCameraDevice();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraDeviceDelegator : public BnCameraDevice {
public:
  explicit ICameraDeviceDelegator(const std::shared_ptr<ICameraDevice> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraDevice::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getCameraCharacteristics(::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override {
    return _impl->getCameraCharacteristics(_aidl_return);
  }
  ::ndk::ScopedAStatus getPhysicalCameraCharacteristics(const std::string& in_physicalCameraId, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override {
    return _impl->getPhysicalCameraCharacteristics(in_physicalCameraId, _aidl_return);
  }
  ::ndk::ScopedAStatus getResourceCost(::aidl::android::hardware::camera::common::CameraResourceCost* _aidl_return) override {
    return _impl->getResourceCost(_aidl_return);
  }
  ::ndk::ScopedAStatus isStreamCombinationSupported(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_streams, bool* _aidl_return) override {
    return _impl->isStreamCombinationSupported(in_streams, _aidl_return);
  }
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override {
    return _impl->open(in_callback, _aidl_return);
  }
  ::ndk::ScopedAStatus openInjectionSession(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraInjectionSession>* _aidl_return) override {
    return _impl->openInjectionSession(in_callback, _aidl_return);
  }
  ::ndk::ScopedAStatus setTorchMode(bool in_on) override {
    return _impl->setTorchMode(in_on);
  }
  ::ndk::ScopedAStatus turnOnTorchWithStrengthLevel(int32_t in_torchStrength) override {
    return _impl->turnOnTorchWithStrengthLevel(in_torchStrength);
  }
  ::ndk::ScopedAStatus getTorchStrengthLevel(int32_t* _aidl_return) override {
    return _impl->getTorchStrengthLevel(_aidl_return);
  }
protected:
private:
  std::shared_ptr<ICameraDevice> _impl;
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
