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
class CdmaSignalInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool isPresent = false;
  int8_t signalType = 0;
  int8_t alertPitch = 0;
  int8_t signal = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) != std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }
  inline bool operator<(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) < std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }
  inline bool operator<=(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) <= std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }
  inline bool operator==(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) == std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }
  inline bool operator>(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) > std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }
  inline bool operator>=(const CdmaSignalInfoRecord& rhs) const {
    return std::tie(isPresent, signalType, alertPitch, signal) >= std::tie(rhs.isPresent, rhs.signalType, rhs.alertPitch, rhs.signal);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSignalInfoRecord{";
    os << "isPresent: " << ::android::internal::ToString(isPresent);
    os << ", signalType: " << ::android::internal::ToString(signalType);
    os << ", alertPitch: " << ::android::internal::ToString(alertPitch);
    os << ", signal: " << ::android::internal::ToString(signal);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
