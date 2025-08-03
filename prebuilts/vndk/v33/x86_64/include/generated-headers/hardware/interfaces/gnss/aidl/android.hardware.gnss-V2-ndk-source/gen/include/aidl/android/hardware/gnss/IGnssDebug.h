#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/GnssConstellationType.h>
#include <aidl/android/hardware/gnss/IGnssDebug.h>
#include <aidl/android/hardware/gnss/SatellitePvt.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssDebug : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssDebug();
  virtual ~IGnssDebug();

  enum class SatelliteEphemerisType : int32_t {
    EPHEMERIS = 0,
    ALMANAC_ONLY = 1,
    NOT_AVAILABLE = 2,
  };

  enum class SatelliteEphemerisHealth : int32_t {
    GOOD = 0,
    BAD = 1,
    UNKNOWN = 2,
  };

  class TimeDebug {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t timeEstimateMs = 0L;
    float timeUncertaintyNs = 0.000000f;
    float frequencyUncertaintyNsPerSec = 0.000000f;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) != std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }
    inline bool operator<(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) < std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }
    inline bool operator<=(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) <= std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }
    inline bool operator==(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) == std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }
    inline bool operator>(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) > std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }
    inline bool operator>=(const TimeDebug& rhs) const {
      return std::tie(timeEstimateMs, timeUncertaintyNs, frequencyUncertaintyNsPerSec) >= std::tie(rhs.timeEstimateMs, rhs.timeUncertaintyNs, rhs.frequencyUncertaintyNsPerSec);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "TimeDebug{";
      os << "timeEstimateMs: " << ::android::internal::ToString(timeEstimateMs);
      os << ", timeUncertaintyNs: " << ::android::internal::ToString(timeUncertaintyNs);
      os << ", frequencyUncertaintyNsPerSec: " << ::android::internal::ToString(frequencyUncertaintyNsPerSec);
      os << "}";
      return os.str();
    }
  };
  class PositionDebug {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    bool valid = false;
    double latitudeDegrees = 0.000000;
    double longitudeDegrees = 0.000000;
    float altitudeMeters = 0.000000f;
    float speedMetersPerSec = 0.000000f;
    float bearingDegrees = 0.000000f;
    double horizontalAccuracyMeters = 0.000000;
    double verticalAccuracyMeters = 0.000000;
    double speedAccuracyMetersPerSecond = 0.000000;
    double bearingAccuracyDegrees = 0.000000;
    float ageSeconds = 0.000000f;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) != std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }
    inline bool operator<(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) < std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }
    inline bool operator<=(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) <= std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }
    inline bool operator==(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) == std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }
    inline bool operator>(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) > std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }
    inline bool operator>=(const PositionDebug& rhs) const {
      return std::tie(valid, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, ageSeconds) >= std::tie(rhs.valid, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.ageSeconds);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "PositionDebug{";
      os << "valid: " << ::android::internal::ToString(valid);
      os << ", latitudeDegrees: " << ::android::internal::ToString(latitudeDegrees);
      os << ", longitudeDegrees: " << ::android::internal::ToString(longitudeDegrees);
      os << ", altitudeMeters: " << ::android::internal::ToString(altitudeMeters);
      os << ", speedMetersPerSec: " << ::android::internal::ToString(speedMetersPerSec);
      os << ", bearingDegrees: " << ::android::internal::ToString(bearingDegrees);
      os << ", horizontalAccuracyMeters: " << ::android::internal::ToString(horizontalAccuracyMeters);
      os << ", verticalAccuracyMeters: " << ::android::internal::ToString(verticalAccuracyMeters);
      os << ", speedAccuracyMetersPerSecond: " << ::android::internal::ToString(speedAccuracyMetersPerSecond);
      os << ", bearingAccuracyDegrees: " << ::android::internal::ToString(bearingAccuracyDegrees);
      os << ", ageSeconds: " << ::android::internal::ToString(ageSeconds);
      os << "}";
      return os.str();
    }
  };
  class SatelliteData {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int32_t svid = 0;
    ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType(0);
    ::aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType ephemerisType = ::aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType(0);
    ::aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource ephemerisSource = ::aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource(0);
    ::aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth ephemerisHealth = ::aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth(0);
    float ephemerisAgeSeconds = 0.000000f;
    bool serverPredictionIsAvailable = false;
    float serverPredictionAgeSeconds = 0.000000f;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) != std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }
    inline bool operator<(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) < std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }
    inline bool operator<=(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) <= std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }
    inline bool operator==(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) == std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }
    inline bool operator>(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) > std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }
    inline bool operator>=(const SatelliteData& rhs) const {
      return std::tie(svid, constellation, ephemerisType, ephemerisSource, ephemerisHealth, ephemerisAgeSeconds, serverPredictionIsAvailable, serverPredictionAgeSeconds) >= std::tie(rhs.svid, rhs.constellation, rhs.ephemerisType, rhs.ephemerisSource, rhs.ephemerisHealth, rhs.ephemerisAgeSeconds, rhs.serverPredictionIsAvailable, rhs.serverPredictionAgeSeconds);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "SatelliteData{";
      os << "svid: " << ::android::internal::ToString(svid);
      os << ", constellation: " << ::android::internal::ToString(constellation);
      os << ", ephemerisType: " << ::android::internal::ToString(ephemerisType);
      os << ", ephemerisSource: " << ::android::internal::ToString(ephemerisSource);
      os << ", ephemerisHealth: " << ::android::internal::ToString(ephemerisHealth);
      os << ", ephemerisAgeSeconds: " << ::android::internal::ToString(ephemerisAgeSeconds);
      os << ", serverPredictionIsAvailable: " << ::android::internal::ToString(serverPredictionIsAvailable);
      os << ", serverPredictionAgeSeconds: " << ::android::internal::ToString(serverPredictionAgeSeconds);
      os << "}";
      return os.str();
    }
  };
  class DebugData {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::hardware::gnss::IGnssDebug::PositionDebug position;
    ::aidl::android::hardware::gnss::IGnssDebug::TimeDebug time;
    std::vector<::aidl::android::hardware::gnss::IGnssDebug::SatelliteData> satelliteDataArray;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) != std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }
    inline bool operator<(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) < std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }
    inline bool operator<=(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) <= std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }
    inline bool operator==(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) == std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }
    inline bool operator>(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) > std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }
    inline bool operator>=(const DebugData& rhs) const {
      return std::tie(position, time, satelliteDataArray) >= std::tie(rhs.position, rhs.time, rhs.satelliteDataArray);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "DebugData{";
      os << "position: " << ::android::internal::ToString(position);
      os << ", time: " << ::android::internal::ToString(time);
      os << ", satelliteDataArray: " << ::android::internal::ToString(satelliteDataArray);
      os << "}";
      return os.str();
    }
  };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_getDebugData = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssDebug> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssDebug>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssDebug>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssDebug>& impl);
  static const std::shared_ptr<IGnssDebug>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getDebugData(::aidl::android::hardware::gnss::IGnssDebug::DebugData* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssDebug> default_impl;
};
class IGnssDebugDefault : public IGnssDebug {
public:
  ::ndk::ScopedAStatus getDebugData(::aidl::android::hardware::gnss::IGnssDebug::DebugData* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnssDebug::SatelliteEphemerisType val) {
  switch(val) {
  case IGnssDebug::SatelliteEphemerisType::EPHEMERIS:
    return "EPHEMERIS";
  case IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY:
    return "ALMANAC_ONLY";
  case IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE:
    return "NOT_AVAILABLE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType, 3> enum_values<aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType> = {
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType::EPHEMERIS,
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY,
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnssDebug::SatelliteEphemerisHealth val) {
  switch(val) {
  case IGnssDebug::SatelliteEphemerisHealth::GOOD:
    return "GOOD";
  case IGnssDebug::SatelliteEphemerisHealth::BAD:
    return "BAD";
  case IGnssDebug::SatelliteEphemerisHealth::UNKNOWN:
    return "UNKNOWN";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth, 3> enum_values<aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth> = {
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth::GOOD,
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth::BAD,
  aidl::android::hardware::gnss::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
