#pragma once

#include "aidl/android/hardware/sensors/ISensorsCallback.h"

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
namespace sensors {
class BnSensorsCallback : public ::ndk::BnCInterface<ISensorsCallback> {
public:
  BnSensorsCallback();
  virtual ~BnSensorsCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISensorsCallbackDelegator : public BnSensorsCallback {
public:
  explicit ISensorsCallbackDelegator(const std::shared_ptr<ISensorsCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISensorsCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onDynamicSensorsConnected(const std::vector<::aidl::android::hardware::sensors::SensorInfo>& in_sensorInfos) override {
    return _impl->onDynamicSensorsConnected(in_sensorInfos);
  }
  ::ndk::ScopedAStatus onDynamicSensorsDisconnected(const std::vector<int32_t>& in_sensorHandles) override {
    return _impl->onDynamicSensorsDisconnected(in_sensorHandles);
  }
protected:
private:
  std::shared_ptr<ISensorsCallback> _impl;
};

}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
