#pragma once

#include "aidl/android/hardware/gnss/IGnssGeofenceCallback.h"

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
class BnGnssGeofenceCallback : public ::ndk::BnCInterface<IGnssGeofenceCallback> {
public:
  BnGnssGeofenceCallback();
  virtual ~BnGnssGeofenceCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssGeofenceCallbackDelegator : public BnGnssGeofenceCallback {
public:
  explicit IGnssGeofenceCallbackDelegator(const std::shared_ptr<IGnssGeofenceCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssGeofenceCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus gnssGeofenceTransitionCb(int32_t in_geofenceId, const ::aidl::android::hardware::gnss::GnssLocation& in_location, int32_t in_transition, int64_t in_timestampMillis) override {
    return _impl->gnssGeofenceTransitionCb(in_geofenceId, in_location, in_transition, in_timestampMillis);
  }
  ::ndk::ScopedAStatus gnssGeofenceStatusCb(int32_t in_availability, const ::aidl::android::hardware::gnss::GnssLocation& in_lastLocation) override {
    return _impl->gnssGeofenceStatusCb(in_availability, in_lastLocation);
  }
  ::ndk::ScopedAStatus gnssGeofenceAddCb(int32_t in_geofenceId, int32_t in_status) override {
    return _impl->gnssGeofenceAddCb(in_geofenceId, in_status);
  }
  ::ndk::ScopedAStatus gnssGeofenceRemoveCb(int32_t in_geofenceId, int32_t in_status) override {
    return _impl->gnssGeofenceRemoveCb(in_geofenceId, in_status);
  }
  ::ndk::ScopedAStatus gnssGeofencePauseCb(int32_t in_geofenceId, int32_t in_status) override {
    return _impl->gnssGeofencePauseCb(in_geofenceId, in_status);
  }
  ::ndk::ScopedAStatus gnssGeofenceResumeCb(int32_t in_geofenceId, int32_t in_status) override {
    return _impl->gnssGeofenceResumeCb(in_geofenceId, in_status);
  }
protected:
private:
  std::shared_ptr<IGnssGeofenceCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
