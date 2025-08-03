#pragma once

#include "aidl/android/hardware/gnss/IGnssConfiguration.h"

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
class BnGnssConfiguration : public ::ndk::BnCInterface<IGnssConfiguration> {
public:
  BnGnssConfiguration();
  virtual ~BnGnssConfiguration();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssConfigurationDelegator : public BnGnssConfiguration {
public:
  explicit IGnssConfigurationDelegator(const std::shared_ptr<IGnssConfiguration> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssConfiguration::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setSuplVersion(int32_t in_version) override {
    return _impl->setSuplVersion(in_version);
  }
  ::ndk::ScopedAStatus setSuplMode(int32_t in_mode) override {
    return _impl->setSuplMode(in_mode);
  }
  ::ndk::ScopedAStatus setLppProfile(int32_t in_lppProfile) override {
    return _impl->setLppProfile(in_lppProfile);
  }
  ::ndk::ScopedAStatus setGlonassPositioningProtocol(int32_t in_protocol) override {
    return _impl->setGlonassPositioningProtocol(in_protocol);
  }
  ::ndk::ScopedAStatus setEmergencySuplPdn(bool in_enable) override {
    return _impl->setEmergencySuplPdn(in_enable);
  }
  ::ndk::ScopedAStatus setEsExtensionSec(int32_t in_emergencyExtensionSeconds) override {
    return _impl->setEsExtensionSec(in_emergencyExtensionSeconds);
  }
  ::ndk::ScopedAStatus setBlocklist(const std::vector<::aidl::android::hardware::gnss::BlocklistedSource>& in_blocklist) override {
    return _impl->setBlocklist(in_blocklist);
  }
protected:
private:
  std::shared_ptr<IGnssConfiguration> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
