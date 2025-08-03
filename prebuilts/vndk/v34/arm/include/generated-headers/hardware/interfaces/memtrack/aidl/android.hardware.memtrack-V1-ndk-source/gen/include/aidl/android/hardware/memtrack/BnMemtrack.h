#pragma once

#include "aidl/android/hardware/memtrack/IMemtrack.h"

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
namespace memtrack {
class BnMemtrack : public ::ndk::BnCInterface<IMemtrack> {
public:
  BnMemtrack();
  virtual ~BnMemtrack();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IMemtrackDelegator : public BnMemtrack {
public:
  explicit IMemtrackDelegator(const std::shared_ptr<IMemtrack> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IMemtrack::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getMemory(int32_t in_pid, ::aidl::android::hardware::memtrack::MemtrackType in_type, std::vector<::aidl::android::hardware::memtrack::MemtrackRecord>* _aidl_return) override {
    return _impl->getMemory(in_pid, in_type, _aidl_return);
  }
  ::ndk::ScopedAStatus getGpuDeviceInfo(std::vector<::aidl::android::hardware::memtrack::DeviceInfo>* _aidl_return) override {
    return _impl->getGpuDeviceInfo(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IMemtrack> _impl;
};

}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
