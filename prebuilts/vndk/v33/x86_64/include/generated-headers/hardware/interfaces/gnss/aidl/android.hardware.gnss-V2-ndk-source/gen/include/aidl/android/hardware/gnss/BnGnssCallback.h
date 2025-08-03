#pragma once

#include "aidl/android/hardware/gnss/IGnssCallback.h"

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
class BnGnssCallback : public ::ndk::BnCInterface<IGnssCallback> {
public:
  BnGnssCallback();
  virtual ~BnGnssCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssCallbackDelegator : public BnGnssCallback {
public:
  explicit IGnssCallbackDelegator(const std::shared_ptr<IGnssCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) override {
    return _impl->gnssSetCapabilitiesCb(in_capabilities);
  }
  ::ndk::ScopedAStatus gnssStatusCb(::aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue in_status) override {
    return _impl->gnssStatusCb(in_status);
  }
  ::ndk::ScopedAStatus gnssSvStatusCb(const std::vector<::aidl::android::hardware::gnss::IGnssCallback::GnssSvInfo>& in_svInfoList) override {
    return _impl->gnssSvStatusCb(in_svInfoList);
  }
  ::ndk::ScopedAStatus gnssLocationCb(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override {
    return _impl->gnssLocationCb(in_location);
  }
  ::ndk::ScopedAStatus gnssNmeaCb(int64_t in_timestamp, const std::string& in_nmea) override {
    return _impl->gnssNmeaCb(in_timestamp, in_nmea);
  }
  ::ndk::ScopedAStatus gnssAcquireWakelockCb() override {
    return _impl->gnssAcquireWakelockCb();
  }
  ::ndk::ScopedAStatus gnssReleaseWakelockCb() override {
    return _impl->gnssReleaseWakelockCb();
  }
  ::ndk::ScopedAStatus gnssSetSystemInfoCb(const ::aidl::android::hardware::gnss::IGnssCallback::GnssSystemInfo& in_info) override {
    return _impl->gnssSetSystemInfoCb(in_info);
  }
  ::ndk::ScopedAStatus gnssRequestTimeCb() override {
    return _impl->gnssRequestTimeCb();
  }
  ::ndk::ScopedAStatus gnssRequestLocationCb(bool in_independentFromGnss, bool in_isUserEmergency) override {
    return _impl->gnssRequestLocationCb(in_independentFromGnss, in_isUserEmergency);
  }
protected:
private:
  std::shared_ptr<IGnssCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
