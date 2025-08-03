#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/CdmaSignalInfoRecord.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class CdmaCallWaiting {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string number;
  int32_t numberPresentation = 0;
  std::string name;
  ::aidl::android::hardware::radio::voice::CdmaSignalInfoRecord signalInfoRecord;
  int32_t numberType = 0;
  int32_t numberPlan = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) != std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }
  inline bool operator<(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) < std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }
  inline bool operator<=(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) <= std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }
  inline bool operator==(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) == std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }
  inline bool operator>(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) > std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }
  inline bool operator>=(const CdmaCallWaiting& rhs) const {
    return std::tie(number, numberPresentation, name, signalInfoRecord, numberType, numberPlan) >= std::tie(rhs.number, rhs.numberPresentation, rhs.name, rhs.signalInfoRecord, rhs.numberType, rhs.numberPlan);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { NUMBER_PLAN_UNKNOWN = 0 };
  enum : int32_t { NUMBER_PLAN_ISDN = 1 };
  enum : int32_t { NUMBER_PLAN_DATA = 3 };
  enum : int32_t { NUMBER_PLAN_TELEX = 4 };
  enum : int32_t { NUMBER_PLAN_NATIONAL = 8 };
  enum : int32_t { NUMBER_PLAN_PRIVATE = 9 };
  enum : int32_t { NUMBER_PRESENTATION_ALLOWED = 0 };
  enum : int32_t { NUMBER_PRESENTATION_RESTRICTED = 1 };
  enum : int32_t { NUMBER_PRESENTATION_UNKNOWN = 2 };
  enum : int32_t { NUMBER_TYPE_UNKNOWN = 0 };
  enum : int32_t { NUMBER_TYPE_INTERNATIONAL = 1 };
  enum : int32_t { NUMBER_TYPE_NATIONAL = 2 };
  enum : int32_t { NUMBER_TYPE_NETWORK_SPECIFIC = 3 };
  enum : int32_t { NUMBER_TYPE_SUBSCRIBER = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaCallWaiting{";
    os << "number: " << ::android::internal::ToString(number);
    os << ", numberPresentation: " << ::android::internal::ToString(numberPresentation);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", signalInfoRecord: " << ::android::internal::ToString(signalInfoRecord);
    os << ", numberType: " << ::android::internal::ToString(numberType);
    os << ", numberPlan: " << ::android::internal::ToString(numberPlan);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
