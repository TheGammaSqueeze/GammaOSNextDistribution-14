#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class Carrier {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mcc;
  std::string mnc;
  int32_t matchType = 0;
  std::string matchData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) != std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }
  inline bool operator<(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) < std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }
  inline bool operator<=(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) <= std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }
  inline bool operator==(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) == std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }
  inline bool operator>(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) > std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }
  inline bool operator>=(const Carrier& rhs) const {
    return std::tie(mcc, mnc, matchType, matchData) >= std::tie(rhs.mcc, rhs.mnc, rhs.matchType, rhs.matchData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { MATCH_TYPE_ALL = 0 };
  enum : int32_t { MATCH_TYPE_SPN = 1 };
  enum : int32_t { MATCH_TYPE_IMSI_PREFIX = 2 };
  enum : int32_t { MATCH_TYPE_GID1 = 3 };
  enum : int32_t { MATCH_TYPE_GID2 = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "Carrier{";
    os << "mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", matchType: " << ::android::internal::ToString(matchType);
    os << ", matchData: " << ::android::internal::ToString(matchData);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
