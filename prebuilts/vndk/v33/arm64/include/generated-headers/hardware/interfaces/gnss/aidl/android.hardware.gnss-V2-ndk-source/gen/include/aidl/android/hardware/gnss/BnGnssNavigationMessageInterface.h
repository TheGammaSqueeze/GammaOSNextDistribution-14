#pragma once

#include "aidl/android/hardware/gnss/IGnssNavigationMessageInterface.h"

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
class BnGnssNavigationMessageInterface : public ::ndk::BnCInterface<IGnssNavigationMessageInterface> {
public:
  BnGnssNavigationMessageInterface();
  virtual ~BnGnssNavigationMessageInterface();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssNavigationMessageInterfaceDelegator : public BnGnssNavigationMessageInterface {
public:
  explicit IGnssNavigationMessageInterfaceDelegator(const std::shared_ptr<IGnssNavigationMessageInterface> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssNavigationMessageInterface::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssNavigationMessageCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
protected:
private:
  std::shared_ptr<IGnssNavigationMessageInterface> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
