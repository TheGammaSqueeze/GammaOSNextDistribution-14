#pragma once

#include "aidl/android/hardware/camera/device/ICameraOfflineSession.h"

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
class BnCameraOfflineSession : public ::ndk::BnCInterface<ICameraOfflineSession> {
public:
  BnCameraOfflineSession();
  virtual ~BnCameraOfflineSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraOfflineSessionDelegator : public BnCameraOfflineSession {
public:
  explicit ICameraOfflineSessionDelegator(const std::shared_ptr<ICameraOfflineSession> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraOfflineSession::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override {
    return _impl->getCaptureResultMetadataQueue(_aidl_return);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_cb) override {
    return _impl->setCallback(in_cb);
  }
protected:
private:
  std::shared_ptr<ICameraOfflineSession> _impl;
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
