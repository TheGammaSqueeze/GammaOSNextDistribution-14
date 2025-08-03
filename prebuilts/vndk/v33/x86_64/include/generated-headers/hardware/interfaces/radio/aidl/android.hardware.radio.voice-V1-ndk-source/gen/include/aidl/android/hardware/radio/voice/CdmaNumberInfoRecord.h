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
class CdmaNumberInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string number;
  int8_t numberType = 0;
  int8_t numberPlan = 0;
  int8_t pi = 0;
  int8_t si = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) != std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }
  inline bool operator<(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) < std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }
  inline bool operator<=(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) <= std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }
  inline bool operator==(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) == std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }
  inline bool operator>(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) > std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }
  inline bool operator>=(const CdmaNumberInfoRecord& rhs) const {
    return std::tie(number, numberType, numberPlan, pi, si) >= std::tie(rhs.number, rhs.numberType, rhs.numberPlan, rhs.pi, rhs.si);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CDMA_NUMBER_INFO_BUFFER_LENGTH = 81 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaNumberInfoRecord{";
    os << "number: " << ::android::internal::ToString(number);
    os << ", numberType: " << ::android::internal::ToString(numberType);
    os << ", numberPlan: " << ::android::internal::ToString(numberPlan);
    os << ", pi: " << ::android::internal::ToString(pi);
    os << ", si: " << ::android::internal::ToString(si);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
