#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/gnss/GnssConstellationType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssSignalType {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType::UNKNOWN;
  double carrierFrequencyHz = 0.000000;
  std::string codeType;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) != std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }
  inline bool operator<(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) < std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }
  inline bool operator<=(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) <= std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }
  inline bool operator==(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) == std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }
  inline bool operator>(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) > std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }
  inline bool operator>=(const GnssSignalType& rhs) const {
    return std::tie(constellation, carrierFrequencyHz, codeType) >= std::tie(rhs.constellation, rhs.carrierFrequencyHz, rhs.codeType);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  static const char* CODE_TYPE_A;
  static const char* CODE_TYPE_B;
  static const char* CODE_TYPE_C;
  static const char* CODE_TYPE_D;
  static const char* CODE_TYPE_I;
  static const char* CODE_TYPE_L;
  static const char* CODE_TYPE_M;
  static const char* CODE_TYPE_N;
  static const char* CODE_TYPE_P;
  static const char* CODE_TYPE_Q;
  static const char* CODE_TYPE_S;
  static const char* CODE_TYPE_W;
  static const char* CODE_TYPE_X;
  static const char* CODE_TYPE_Y;
  static const char* CODE_TYPE_Z;
  static const char* CODE_TYPE_UNKNOWN;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssSignalType{";
    os << "constellation: " << ::android::internal::ToString(constellation);
    os << ", carrierFrequencyHz: " << ::android::internal::ToString(carrierFrequencyHz);
    os << ", codeType: " << ::android::internal::ToString(codeType);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
