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
namespace voice {
class CdmaLineControlInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t lineCtrlPolarityIncluded = 0;
  int8_t lineCtrlToggle = 0;
  int8_t lineCtrlReverse = 0;
  int8_t lineCtrlPowerDenial = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) != std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }
  inline bool operator<(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) < std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }
  inline bool operator<=(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) <= std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }
  inline bool operator==(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) == std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }
  inline bool operator>(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) > std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }
  inline bool operator>=(const CdmaLineControlInfoRecord& rhs) const {
    return std::tie(lineCtrlPolarityIncluded, lineCtrlToggle, lineCtrlReverse, lineCtrlPowerDenial) >= std::tie(rhs.lineCtrlPolarityIncluded, rhs.lineCtrlToggle, rhs.lineCtrlReverse, rhs.lineCtrlPowerDenial);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaLineControlInfoRecord{";
    os << "lineCtrlPolarityIncluded: " << ::android::internal::ToString(lineCtrlPolarityIncluded);
    os << ", lineCtrlToggle: " << ::android::internal::ToString(lineCtrlToggle);
    os << ", lineCtrlReverse: " << ::android::internal::ToString(lineCtrlReverse);
    os << ", lineCtrlPowerDenial: " << ::android::internal::ToString(lineCtrlPowerDenial);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
