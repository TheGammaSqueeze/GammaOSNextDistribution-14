#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/ElapsedRealtime.h>
#include <aidl/android/hardware/gnss/GnssClock.h>
#include <aidl/android/hardware/gnss/GnssConstellationType.h>
#include <aidl/android/hardware/gnss/GnssData.h>
#include <aidl/android/hardware/gnss/GnssMeasurement.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class GnssAgc {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    double agcLevelDb = 0.000000;
    ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType::UNKNOWN;
    int64_t carrierFrequencyHz = 0L;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) != std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }
    inline bool operator<(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) < std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }
    inline bool operator<=(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) <= std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }
    inline bool operator==(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) == std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }
    inline bool operator>(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) > std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }
    inline bool operator>=(const GnssAgc& rhs) const {
      return std::tie(agcLevelDb, constellation, carrierFrequencyHz) >= std::tie(rhs.agcLevelDb, rhs.constellation, rhs.carrierFrequencyHz);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "GnssAgc{";
      os << "agcLevelDb: " << ::android::internal::ToString(agcLevelDb);
      os << ", constellation: " << ::android::internal::ToString(constellation);
      os << ", carrierFrequencyHz: " << ::android::internal::ToString(carrierFrequencyHz);
      os << "}";
      return os.str();
    }
  };
  std::vector<::aidl::android::hardware::gnss::GnssMeasurement> measurements;
  ::aidl::android::hardware::gnss::GnssClock clock;
  ::aidl::android::hardware::gnss::ElapsedRealtime elapsedRealtime;
  std::vector<::aidl::android::hardware::gnss::GnssData::GnssAgc> gnssAgcs = {};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) != std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }
  inline bool operator<(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) < std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }
  inline bool operator<=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) <= std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }
  inline bool operator==(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) == std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }
  inline bool operator>(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) > std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }
  inline bool operator>=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime, gnssAgcs) >= std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime, rhs.gnssAgcs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssData{";
    os << "measurements: " << ::android::internal::ToString(measurements);
    os << ", clock: " << ::android::internal::ToString(clock);
    os << ", elapsedRealtime: " << ::android::internal::ToString(elapsedRealtime);
    os << ", gnssAgcs: " << ::android::internal::ToString(gnssAgcs);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
