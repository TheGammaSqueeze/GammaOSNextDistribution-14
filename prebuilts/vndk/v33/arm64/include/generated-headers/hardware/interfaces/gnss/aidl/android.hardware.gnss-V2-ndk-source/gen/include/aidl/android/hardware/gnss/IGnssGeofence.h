#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/IGnssGeofenceCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssGeofence : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssGeofence();
  virtual ~IGnssGeofence();

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_addGeofence = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_pauseGeofence = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_resumeGeofence = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_removeGeofence = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IGnssGeofence> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssGeofence>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssGeofence>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssGeofence>& impl);
  static const std::shared_ptr<IGnssGeofence>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofenceCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus addGeofence(int32_t in_geofenceId, double in_latitudeDegrees, double in_longitudeDegrees, double in_radiusMeters, int32_t in_lastTransition, int32_t in_monitorTransitions, int32_t in_notificationResponsivenessMs, int32_t in_unknownTimerMs) = 0;
  virtual ::ndk::ScopedAStatus pauseGeofence(int32_t in_geofenceId) = 0;
  virtual ::ndk::ScopedAStatus resumeGeofence(int32_t in_geofenceId, int32_t in_monitorTransitions) = 0;
  virtual ::ndk::ScopedAStatus removeGeofence(int32_t in_geofenceId) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssGeofence> default_impl;
};
class IGnssGeofenceDefault : public IGnssGeofence {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofenceCallback>& in_callback) override;
  ::ndk::ScopedAStatus addGeofence(int32_t in_geofenceId, double in_latitudeDegrees, double in_longitudeDegrees, double in_radiusMeters, int32_t in_lastTransition, int32_t in_monitorTransitions, int32_t in_notificationResponsivenessMs, int32_t in_unknownTimerMs) override;
  ::ndk::ScopedAStatus pauseGeofence(int32_t in_geofenceId) override;
  ::ndk::ScopedAStatus resumeGeofence(int32_t in_geofenceId, int32_t in_monitorTransitions) override;
  ::ndk::ScopedAStatus removeGeofence(int32_t in_geofenceId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
