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
class PhonebookRecordInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t recordId = 0;
  std::string name;
  std::string number;
  std::vector<std::string> emails;
  std::vector<std::string> additionalNumbers;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) != std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }
  inline bool operator<(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) < std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }
  inline bool operator<=(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) <= std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }
  inline bool operator==(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) == std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }
  inline bool operator>(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) > std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }
  inline bool operator>=(const PhonebookRecordInfo& rhs) const {
    return std::tie(recordId, name, number, emails, additionalNumbers) >= std::tie(rhs.recordId, rhs.name, rhs.number, rhs.emails, rhs.additionalNumbers);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhonebookRecordInfo{";
    os << "recordId: " << ::android::internal::ToString(recordId);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", number: " << ::android::internal::ToString(number);
    os << ", emails: " << ::android::internal::ToString(emails);
    os << ", additionalNumbers: " << ::android::internal::ToString(additionalNumbers);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
