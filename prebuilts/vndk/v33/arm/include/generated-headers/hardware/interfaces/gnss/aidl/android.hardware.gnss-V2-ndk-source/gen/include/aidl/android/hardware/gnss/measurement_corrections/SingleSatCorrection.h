#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/GnssConstellationType.h>
#include <aidl/android/hardware/gnss/measurement_corrections/ReflectingPlane.h>
#include <aidl/android/hardware/gnss/measurement_corrections/SingleSatCorrection.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class SingleSatCorrection {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class ExcessPathInfo {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int32_t excessPathInfoFlags = 0;
    float excessPathLengthMeters = 0.000000f;
    float excessPathLengthUncertaintyMeters = 0.000000f;
    ::aidl::android::hardware::gnss::measurement_corrections::ReflectingPlane reflectingPlane;
    float attenuationDb = 0.000000f;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) != std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }
    inline bool operator<(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) < std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }
    inline bool operator<=(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) <= std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }
    inline bool operator==(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) == std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }
    inline bool operator>(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) > std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }
    inline bool operator>=(const ExcessPathInfo& rhs) const {
      return std::tie(excessPathInfoFlags, excessPathLengthMeters, excessPathLengthUncertaintyMeters, reflectingPlane, attenuationDb) >= std::tie(rhs.excessPathInfoFlags, rhs.excessPathLengthMeters, rhs.excessPathLengthUncertaintyMeters, rhs.reflectingPlane, rhs.attenuationDb);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    enum : int32_t { EXCESS_PATH_INFO_HAS_EXCESS_PATH_LENGTH = 1 };
    enum : int32_t { EXCESS_PATH_INFO_HAS_EXCESS_PATH_LENGTH_UNC = 2 };
    enum : int32_t { EXCESS_PATH_INFO_HAS_REFLECTING_PLANE = 4 };
    enum : int32_t { EXCESS_PATH_INFO_HAS_ATTENUATION = 8 };
    inline std::string toString() const {
      std::ostringstream os;
      os << "ExcessPathInfo{";
      os << "excessPathInfoFlags: " << ::android::internal::ToString(excessPathInfoFlags);
      os << ", excessPathLengthMeters: " << ::android::internal::ToString(excessPathLengthMeters);
      os << ", excessPathLengthUncertaintyMeters: " << ::android::internal::ToString(excessPathLengthUncertaintyMeters);
      os << ", reflectingPlane: " << ::android::internal::ToString(reflectingPlane);
      os << ", attenuationDb: " << ::android::internal::ToString(attenuationDb);
      os << "}";
      return os.str();
    }
  };
  int32_t singleSatCorrectionFlags = 0;
  ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType(0);
  int32_t svid = 0;
  int64_t carrierFrequencyHz = 0L;
  float probSatIsLos = 0.000000f;
  float combinedExcessPathLengthMeters = 0.000000f;
  float combinedExcessPathLengthUncertaintyMeters = 0.000000f;
  float combinedAttenuationDb = 0.000000f;
  std::vector<::aidl::android::hardware::gnss::measurement_corrections::SingleSatCorrection::ExcessPathInfo> excessPathInfos;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) != std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }
  inline bool operator<(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) < std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }
  inline bool operator<=(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) <= std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }
  inline bool operator==(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) == std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }
  inline bool operator>(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) > std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }
  inline bool operator>=(const SingleSatCorrection& rhs) const {
    return std::tie(singleSatCorrectionFlags, constellation, svid, carrierFrequencyHz, probSatIsLos, combinedExcessPathLengthMeters, combinedExcessPathLengthUncertaintyMeters, combinedAttenuationDb, excessPathInfos) >= std::tie(rhs.singleSatCorrectionFlags, rhs.constellation, rhs.svid, rhs.carrierFrequencyHz, rhs.probSatIsLos, rhs.combinedExcessPathLengthMeters, rhs.combinedExcessPathLengthUncertaintyMeters, rhs.combinedAttenuationDb, rhs.excessPathInfos);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SINGLE_SAT_CORRECTION_HAS_SAT_IS_LOS_PROBABILITY = 1 };
  enum : int32_t { SINGLE_SAT_CORRECTION_HAS_COMBINED_EXCESS_PATH_LENGTH = 2 };
  enum : int32_t { SINGLE_SAT_CORRECTION_HAS_COMBINED_EXCESS_PATH_LENGTH_UNC = 4 };
  enum : int32_t { SINGLE_SAT_CORRECTION_HAS_COMBINED_ATTENUATION = 16 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SingleSatCorrection{";
    os << "singleSatCorrectionFlags: " << ::android::internal::ToString(singleSatCorrectionFlags);
    os << ", constellation: " << ::android::internal::ToString(constellation);
    os << ", svid: " << ::android::internal::ToString(svid);
    os << ", carrierFrequencyHz: " << ::android::internal::ToString(carrierFrequencyHz);
    os << ", probSatIsLos: " << ::android::internal::ToString(probSatIsLos);
    os << ", combinedExcessPathLengthMeters: " << ::android::internal::ToString(combinedExcessPathLengthMeters);
    os << ", combinedExcessPathLengthUncertaintyMeters: " << ::android::internal::ToString(combinedExcessPathLengthUncertaintyMeters);
    os << ", combinedAttenuationDb: " << ::android::internal::ToString(combinedAttenuationDb);
    os << ", excessPathInfos: " << ::android::internal::ToString(excessPathInfos);
    os << "}";
    return os.str();
  }
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
