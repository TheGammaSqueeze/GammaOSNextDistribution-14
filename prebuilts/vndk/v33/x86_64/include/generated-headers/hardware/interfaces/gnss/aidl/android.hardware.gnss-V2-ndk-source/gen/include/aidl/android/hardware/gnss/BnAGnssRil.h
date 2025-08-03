#pragma once

#include "aidl/android/hardware/gnss/IAGnssRil.h"

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
class BnAGnssRil : public ::ndk::BnCInterface<IAGnssRil> {
public:
  BnAGnssRil();
  virtual ~BnAGnssRil();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IAGnssRilDelegator : public BnAGnssRil {
public:
  explicit IAGnssRilDelegator(const std::shared_ptr<IAGnssRil> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IAGnssRil::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRilCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus setRefLocation(const ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocation& in_agnssReflocation) override {
    return _impl->setRefLocation(in_agnssReflocation);
  }
  ::ndk::ScopedAStatus setSetId(::aidl::android::hardware::gnss::IAGnssRil::SetIdType in_type, const std::string& in_setid) override {
    return _impl->setSetId(in_type, in_setid);
  }
  ::ndk::ScopedAStatus updateNetworkState(const ::aidl::android::hardware::gnss::IAGnssRil::NetworkAttributes& in_attributes) override {
    return _impl->updateNetworkState(in_attributes);
  }
protected:
private:
  std::shared_ptr<IAGnssRil> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
