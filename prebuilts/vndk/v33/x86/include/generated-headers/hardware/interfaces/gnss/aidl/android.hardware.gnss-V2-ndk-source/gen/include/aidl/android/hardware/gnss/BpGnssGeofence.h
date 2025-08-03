#pragma once

#include "aidl/android/hardware/gnss/IGnssGeofence.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssGeofence : public ::ndk::BpCInterface<IGnssGeofence> {
public:
  explicit BpGnssGeofence(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssGeofence();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofenceCallback>& in_callback) override;
  ::ndk::ScopedAStatus addGeofence(int32_t in_geofenceId, double in_latitudeDegrees, double in_longitudeDegrees, double in_radiusMeters, int32_t in_lastTransition, int32_t in_monitorTransitions, int32_t in_notificationResponsivenessMs, int32_t in_unknownTimerMs) override;
  ::ndk::ScopedAStatus pauseGeofence(int32_t in_geofenceId) override;
  ::ndk::ScopedAStatus resumeGeofence(int32_t in_geofenceId, int32_t in_monitorTransitions) override;
  ::ndk::ScopedAStatus removeGeofence(int32_t in_geofenceId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
