#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/GnssLocation.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssGeofenceCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssGeofenceCallback();
  virtual ~IGnssGeofenceCallback();

  enum : int32_t { ENTERED = 1 };
  enum : int32_t { EXITED = 2 };
  enum : int32_t { UNCERTAIN = 4 };
  enum : int32_t { UNAVAILABLE = 1 };
  enum : int32_t { AVAILABLE = 2 };
  enum : int32_t { OPERATION_SUCCESS = 0 };
  enum : int32_t { ERROR_TOO_MANY_GEOFENCES = -100 };
  enum : int32_t { ERROR_ID_EXISTS = -101 };
  enum : int32_t { ERROR_ID_UNKNOWN = -102 };
  enum : int32_t { ERROR_INVALID_TRANSITION = -103 };
  enum : int32_t { ERROR_GENERIC = -149 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssGeofenceTransitionCb = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_gnssGeofenceStatusCb = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_gnssGeofenceAddCb = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_gnssGeofenceRemoveCb = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_gnssGeofencePauseCb = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_gnssGeofenceResumeCb = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IGnssGeofenceCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssGeofenceCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssGeofenceCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssGeofenceCallback>& impl);
  static const std::shared_ptr<IGnssGeofenceCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssGeofenceTransitionCb(int32_t in_geofenceId, const ::aidl::android::hardware::gnss::GnssLocation& in_location, int32_t in_transition, int64_t in_timestampMillis) = 0;
  virtual ::ndk::ScopedAStatus gnssGeofenceStatusCb(int32_t in_availability, const ::aidl::android::hardware::gnss::GnssLocation& in_lastLocation) = 0;
  virtual ::ndk::ScopedAStatus gnssGeofenceAddCb(int32_t in_geofenceId, int32_t in_status) = 0;
  virtual ::ndk::ScopedAStatus gnssGeofenceRemoveCb(int32_t in_geofenceId, int32_t in_status) = 0;
  virtual ::ndk::ScopedAStatus gnssGeofencePauseCb(int32_t in_geofenceId, int32_t in_status) = 0;
  virtual ::ndk::ScopedAStatus gnssGeofenceResumeCb(int32_t in_geofenceId, int32_t in_status) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssGeofenceCallback> default_impl;
};
class IGnssGeofenceCallbackDefault : public IGnssGeofenceCallback {
public:
  ::ndk::ScopedAStatus gnssGeofenceTransitionCb(int32_t in_geofenceId, const ::aidl::android::hardware::gnss::GnssLocation& in_location, int32_t in_transition, int64_t in_timestampMillis) override;
  ::ndk::ScopedAStatus gnssGeofenceStatusCb(int32_t in_availability, const ::aidl::android::hardware::gnss::GnssLocation& in_lastLocation) override;
  ::ndk::ScopedAStatus gnssGeofenceAddCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofenceRemoveCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofencePauseCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus gnssGeofenceResumeCb(int32_t in_geofenceId, int32_t in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
