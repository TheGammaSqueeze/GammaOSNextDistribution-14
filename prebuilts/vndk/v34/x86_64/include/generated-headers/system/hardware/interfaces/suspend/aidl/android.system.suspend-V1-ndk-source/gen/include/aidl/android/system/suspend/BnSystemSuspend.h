#pragma once

#include "aidl/android/system/suspend/ISystemSuspend.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace system {
namespace suspend {
class BnSystemSuspend : public ::ndk::BnCInterface<ISystemSuspend> {
public:
  BnSystemSuspend();
  virtual ~BnSystemSuspend();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISystemSuspendDelegator : public BnSystemSuspend {
public:
  explicit ISystemSuspendDelegator(const std::shared_ptr<ISystemSuspend> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISystemSuspend::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acquireWakeLock(::aidl::android::system::suspend::WakeLockType in_type, const std::string& in_name, std::shared_ptr<::aidl::android::system::suspend::IWakeLock>* _aidl_return) override {
    return _impl->acquireWakeLock(in_type, in_name, _aidl_return);
  }
protected:
private:
  std::shared_ptr<ISystemSuspend> _impl;
};

}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
