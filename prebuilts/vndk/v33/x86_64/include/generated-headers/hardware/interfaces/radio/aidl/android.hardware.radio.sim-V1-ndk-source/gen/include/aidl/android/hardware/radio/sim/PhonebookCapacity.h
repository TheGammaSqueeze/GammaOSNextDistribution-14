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
class PhonebookCapacity {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t maxAdnRecords = 0;
  int32_t usedAdnRecords = 0;
  int32_t maxEmailRecords = 0;
  int32_t usedEmailRecords = 0;
  int32_t maxAdditionalNumberRecords = 0;
  int32_t usedAdditionalNumberRecords = 0;
  int32_t maxNameLen = 0;
  int32_t maxNumberLen = 0;
  int32_t maxEmailLen = 0;
  int32_t maxAdditionalNumberLen = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) != std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }
  inline bool operator<(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) < std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }
  inline bool operator<=(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) <= std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }
  inline bool operator==(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) == std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }
  inline bool operator>(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) > std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }
  inline bool operator>=(const PhonebookCapacity& rhs) const {
    return std::tie(maxAdnRecords, usedAdnRecords, maxEmailRecords, usedEmailRecords, maxAdditionalNumberRecords, usedAdditionalNumberRecords, maxNameLen, maxNumberLen, maxEmailLen, maxAdditionalNumberLen) >= std::tie(rhs.maxAdnRecords, rhs.usedAdnRecords, rhs.maxEmailRecords, rhs.usedEmailRecords, rhs.maxAdditionalNumberRecords, rhs.usedAdditionalNumberRecords, rhs.maxNameLen, rhs.maxNumberLen, rhs.maxEmailLen, rhs.maxAdditionalNumberLen);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhonebookCapacity{";
    os << "maxAdnRecords: " << ::android::internal::ToString(maxAdnRecords);
    os << ", usedAdnRecords: " << ::android::internal::ToString(usedAdnRecords);
    os << ", maxEmailRecords: " << ::android::internal::ToString(maxEmailRecords);
    os << ", usedEmailRecords: " << ::android::internal::ToString(usedEmailRecords);
    os << ", maxAdditionalNumberRecords: " << ::android::internal::ToString(maxAdditionalNumberRecords);
    os << ", usedAdditionalNumberRecords: " << ::android::internal::ToString(usedAdditionalNumberRecords);
    os << ", maxNameLen: " << ::android::internal::ToString(maxNameLen);
    os << ", maxNumberLen: " << ::android::internal::ToString(maxNumberLen);
    os << ", maxEmailLen: " << ::android::internal::ToString(maxEmailLen);
    os << ", maxAdditionalNumberLen: " << ::android::internal::ToString(maxAdditionalNumberLen);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
