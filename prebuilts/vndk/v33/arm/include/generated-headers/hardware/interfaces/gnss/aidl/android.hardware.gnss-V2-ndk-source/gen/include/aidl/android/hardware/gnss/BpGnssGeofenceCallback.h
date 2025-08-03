#pragma once

#include "aidl/android/hardware/gnss/IGnssGeofenceCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssGeofenceCallback : public ::ndk::BpCInterface<IGnssGeofenceCallback> {
public:
  explicit BpGnssGeofenceCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssGeofenceCallback();

  ::ndk::ScopedAStatus gnssGeofenceTransitionCb(int32_t in_geofenceId, const ::aidl::android::hardware::gnss::GnssLocation& in_location, int32_t in_transition, int64_t in_timestampMillis) override;
  ::ndk::ScopedAStatus gnssGeofenceStatusCb(int32_t in_availability, const ::aidl::android::hardware::gnss::GnssLocation& in_lastLocation) override;
  ::ndk::ScopedAStatus gnssGeofenceAddCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofenceRemoveCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofencePauseCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofenceResumeCb(int32_t in_geofenceId, int32_t in_status) override;
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
