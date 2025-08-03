#pragma once

#include "aidl/android/hardware/gnss/IGnssDebug.h"

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
namespace gnss {
class BnGnssDebug : public ::ndk::BnCInterface<IGnssDebug> {
public:
  BnGnssDebug();
  virtual ~BnGnssDebug();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssDebugDelegator : public BnGnssDebug {
public:
  explicit IGnssDebugDelegator(const std::shared_ptr<IGnssDebug> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssDebug::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getDebugData(::aidl::android::hardware::gnss::IGnssDebug::DebugData* _aidl_return) override {
    return _impl->getDebugData(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IGnssDebug> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
