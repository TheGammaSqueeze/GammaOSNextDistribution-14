#pragma once

#include "aidl/android/hardware/gnss/IAGnss.h"

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
class BnAGnss : public ::ndk::BnCInterface<IAGnss> {
public:
  BnAGnss();
  virtual ~BnAGnss();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IAGnssDelegator : public BnAGnss {
public:
  explicit IAGnssDelegator(const std::shared_ptr<IAGnss> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IAGnss::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus dataConnClosed() override {
    return _impl->dataConnClosed();
  }
  ::ndk::ScopedAStatus dataConnFailed() override {
    return _impl->dataConnFailed();
  }
  ::ndk::ScopedAStatus setServer(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, const std::string& in_hostname, int32_t in_port) override {
    return _impl->setServer(in_type, in_hostname, in_port);
  }
  ::ndk::ScopedAStatus dataConnOpen(int64_t in_networkHandle, const std::string& in_apn, ::aidl::android::hardware::gnss::IAGnss::ApnIpType in_apnIpType) override {
    return _impl->dataConnOpen(in_networkHandle, in_apn, in_apnIpType);
  }
protected:
private:
  std::shared_ptr<IAGnss> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
