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
#include <aidl/android/hardware/gnss/GnssLocation.h>
#include <aidl/android/hardware/gnss/IGnssCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssCallback();
  virtual ~IGnssCallback();

  enum class GnssStatusValue : int32_t {
    NONE = 0,
    SESSION_BEGIN = 1,
    SESSION_END = 2,
    ENGINE_ON = 3,
    ENGINE_OFF = 4,
  };

  enum class GnssSvFlags : int32_t {
    NONE = 0,
    HAS_EPHEMERIS_DATA = 1,
    HAS_ALMANAC_DATA = 2,
    USED_IN_FIX = 4,
    HAS_CARRIER_FREQUENCY = 8,
  };

  class GnssSvInfo {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int32_t svid = 0;
    ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType(0);
    float cN0Dbhz = 0.000000f;
    float basebandCN0DbHz = 0.000000f;
    float elevationDegrees = 0.000000f;
    float azimuthDegrees = 0.000000f;
    int64_t carrierFrequencyHz = 0L;
    int32_t svFlag = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) != std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }
    inline bool operator<(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) < std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }
    inline bool operator<=(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) <= std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }
    inline bool operator==(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) == std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }
    inline bool operator>(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) > std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }
    inline bool operator>=(const GnssSvInfo& rhs) const {
      return std::tie(svid, constellation, cN0Dbhz, basebandCN0DbHz, elevationDegrees, azimuthDegrees, carrierFrequencyHz, svFlag) >= std::tie(rhs.svid, rhs.constellation, rhs.cN0Dbhz, rhs.basebandCN0DbHz, rhs.elevationDegrees, rhs.azimuthDegrees, rhs.carrierFrequencyHz, rhs.svFlag);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "GnssSvInfo{";
      os << "svid: " << ::android::internal::ToString(svid);
      os << ", constellation: " << ::android::internal::ToString(constellation);
      os << ", cN0Dbhz: " << ::android::internal::ToString(cN0Dbhz);
      os << ", basebandCN0DbHz: " << ::android::internal::ToString(basebandCN0DbHz);
      os << ", elevationDegrees: " << ::android::internal::ToString(elevationDegrees);
      os << ", azimuthDegrees: " << ::android::internal::ToString(azimuthDegrees);
      os << ", carrierFrequencyHz: " << ::android::internal::ToString(carrierFrequencyHz);
      os << ", svFlag: " << ::android::internal::ToString(svFlag);
      os << "}";
      return os.str();
    }
  };
  class GnssSystemInfo {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int32_t yearOfHw = 0;
    std::string name;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) != std::tie(rhs.yearOfHw, rhs.name);
    }
    inline bool operator<(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) < std::tie(rhs.yearOfHw, rhs.name);
    }
    inline bool operator<=(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) <= std::tie(rhs.yearOfHw, rhs.name);
    }
    inline bool operator==(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) == std::tie(rhs.yearOfHw, rhs.name);
    }
    inline bool operator>(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) > std::tie(rhs.yearOfHw, rhs.name);
    }
    inline bool operator>=(const GnssSystemInfo& rhs) const {
      return std::tie(yearOfHw, name) >= std::tie(rhs.yearOfHw, rhs.name);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "GnssSystemInfo{";
      os << "yearOfHw: " << ::android::internal::ToString(yearOfHw);
      os << ", name: " << ::android::internal::ToString(name);
      os << "}";
      return os.str();
    }
  };
  enum : int32_t { CAPABILITY_SCHEDULING = 1 };
  enum : int32_t { CAPABILITY_MSB = 2 };
  enum : int32_t { CAPABILITY_MSA = 4 };
  enum : int32_t { CAPABILITY_SINGLE_SHOT = 8 };
  enum : int32_t { CAPABILITY_ON_DEMAND_TIME = 16 };
  enum : int32_t { CAPABILITY_GEOFENCING = 32 };
  enum : int32_t { CAPABILITY_MEASUREMENTS = 64 };
  enum : int32_t { CAPABILITY_NAV_MESSAGES = 128 };
  enum : int32_t { CAPABILITY_LOW_POWER_MODE = 256 };
  enum : int32_t { CAPABILITY_SATELLITE_BLOCKLIST = 512 };
  enum : int32_t { CAPABILITY_MEASUREMENT_CORRECTIONS = 1024 };
  enum : int32_t { CAPABILITY_ANTENNA_INFO = 2048 };
  enum : int32_t { CAPABILITY_CORRELATION_VECTOR = 4096 };
  enum : int32_t { CAPABILITY_SATELLITE_PVT = 8192 };
  enum : int32_t { CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING = 16384 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssSetCapabilitiesCb = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_gnssStatusCb = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_gnssSvStatusCb = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_gnssLocationCb = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_gnssNmeaCb = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_gnssAcquireWakelockCb = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_gnssReleaseWakelockCb = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_gnssSetSystemInfoCb = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_gnssRequestTimeCb = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_gnssRequestLocationCb = FIRST_CALL_TRANSACTION + 9;

  static std::shared_ptr<IGnssCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssCallback>& impl);
  static const std::shared_ptr<IGnssCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) = 0;
  virtual ::ndk::ScopedAStatus gnssStatusCb(::aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue in_status) = 0;
  virtual ::ndk::ScopedAStatus gnssSvStatusCb(const std::vector<::aidl::android::hardware::gnss::IGnssCallback::GnssSvInfo>& in_svInfoList) = 0;
  virtual ::ndk::ScopedAStatus gnssLocationCb(const ::aidl::android::hardware::gnss::GnssLocation& in_location) = 0;
  virtual ::ndk::ScopedAStatus gnssNmeaCb(int64_t in_timestamp, const std::string& in_nmea) = 0;
  virtual ::ndk::ScopedAStatus gnssAcquireWakelockCb() = 0;
  virtual ::ndk::ScopedAStatus gnssReleaseWakelockCb() = 0;
  virtual ::ndk::ScopedAStatus gnssSetSystemInfoCb(const ::aidl::android::hardware::gnss::IGnssCallback::GnssSystemInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus gnssRequestTimeCb() = 0;
  virtual ::ndk::ScopedAStatus gnssRequestLocationCb(bool in_independentFromGnss, bool in_isUserEmergency) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssCallback> default_impl;
};
class IGnssCallbackDefault : public IGnssCallback {
public:
  ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus gnssStatusCb(::aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue in_status) override;
  ::ndk::ScopedAStatus gnssSvStatusCb(const std::vector<::aidl::android::hardware::gnss::IGnssCallback::GnssSvInfo>& in_svInfoList) override;
  ::ndk::ScopedAStatus gnssLocationCb(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override;
  ::ndk::ScopedAStatus gnssNmeaCb(int64_t in_timestamp, const std::string& in_nmea) override;
  ::ndk::ScopedAStatus gnssAcquireWakelockCb() override;
  ::ndk::ScopedAStatus gnssReleaseWakelockCb() override;
  ::ndk::ScopedAStatus gnssSetSystemInfoCb(const ::aidl::android::hardware::gnss::IGnssCallback::GnssSystemInfo& in_info) override;
  ::ndk::ScopedAStatus gnssRequestTimeCb() override;
  ::ndk::ScopedAStatus gnssRequestLocationCb(bool in_independentFromGnss, bool in_isUserEmergency) override;
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
[[nodiscard]] static inline std::string toString(IGnssCallback::GnssStatusValue val) {
  switch(val) {
  case IGnssCallback::GnssStatusValue::NONE:
    return "NONE";
  case IGnssCallback::GnssStatusValue::SESSION_BEGIN:
    return "SESSION_BEGIN";
  case IGnssCallback::GnssStatusValue::SESSION_END:
    return "SESSION_END";
  case IGnssCallback::GnssStatusValue::ENGINE_ON:
    return "ENGINE_ON";
  case IGnssCallback::GnssStatusValue::ENGINE_OFF:
    return "ENGINE_OFF";
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
constexpr inline std::array<aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue, 5> enum_values<aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue> = {
  aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue::NONE,
  aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue::SESSION_BEGIN,
  aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue::SESSION_END,
  aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue::ENGINE_ON,
  aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue::ENGINE_OFF,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnssCallback::GnssSvFlags val) {
  switch(val) {
  case IGnssCallback::GnssSvFlags::NONE:
    return "NONE";
  case IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA:
    return "HAS_EPHEMERIS_DATA";
  case IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA:
    return "HAS_ALMANAC_DATA";
  case IGnssCallback::GnssSvFlags::USED_IN_FIX:
    return "USED_IN_FIX";
  case IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY:
    return "HAS_CARRIER_FREQUENCY";
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
constexpr inline std::array<aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags, 5> enum_values<aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags> = {
  aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags::NONE,
  aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA,
  aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA,
  aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags::USED_IN_FIX,
  aidl::android::hardware::gnss::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
