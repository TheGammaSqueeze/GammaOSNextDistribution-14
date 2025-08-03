#pragma once

#include "aidl/android/hardware/gnss/IGnssGeofence.h"

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
class BnGnssGeofence : public ::ndk::BnCInterface<IGnssGeofence> {
public:
  BnGnssGeofence();
  virtual ~BnGnssGeofence();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssGeofenceDelegator : public BnGnssGeofence {
public:
  explicit IGnssGeofenceDelegator(const std::shared_ptr<IGnssGeofence> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssGeofence::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofenceCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus addGeofence(int32_t in_geofenceId, double in_latitudeDegrees, double in_longitudeDegrees, double in_radiusMeters, int32_t in_lastTransition, int32_t in_monitorTransitions, int32_t in_notificationResponsivenessMs, int32_t in_unknownTimerMs) override {
    return _impl->addGeofence(in_geofenceId, in_latitudeDegrees, in_longitudeDegrees, in_radiusMeters, in_lastTransition, in_monitorTransitions, in_notificationResponsivenessMs, in_unknownTimerMs);
  }
  ::ndk::ScopedAStatus pauseGeofence(int32_t in_geofenceId) override {
    return _impl->pauseGeofence(in_geofenceId);
  }
  ::ndk::ScopedAStatus resumeGeofence(int32_t in_geofenceId, int32_t in_monitorTransitions) override {
    return _impl->resumeGeofence(in_geofenceId, in_monitorTransitions);
  }
  ::ndk::ScopedAStatus removeGeofence(int32_t in_geofenceId) override {
    return _impl->removeGeofence(in_geofenceId);
  }
protected:
private:
  std::shared_ptr<IGnssGeofence> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
