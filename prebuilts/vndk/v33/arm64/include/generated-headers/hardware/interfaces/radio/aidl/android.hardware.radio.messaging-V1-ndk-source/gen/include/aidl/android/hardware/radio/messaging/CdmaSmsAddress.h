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
namespace messaging {
class CdmaSmsAddress {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t digitMode = 0;
  bool isNumberModeDataNetwork = false;
  int32_t numberType = 0;
  int32_t numberPlan = 0;
  std::vector<uint8_t> digits;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) != std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }
  inline bool operator<(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) < std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }
  inline bool operator<=(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) <= std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }
  inline bool operator==(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) == std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }
  inline bool operator>(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) > std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }
  inline bool operator>=(const CdmaSmsAddress& rhs) const {
    return std::tie(digitMode, isNumberModeDataNetwork, numberType, numberPlan, digits) >= std::tie(rhs.digitMode, rhs.isNumberModeDataNetwork, rhs.numberType, rhs.numberPlan, rhs.digits);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { DIGIT_MODE_FOUR_BIT = 0 };
  enum : int32_t { DIGIT_MODE_EIGHT_BIT = 1 };
  enum : int32_t { NUMBER_PLAN_UNKNOWN = 0 };
  enum : int32_t { NUMBER_PLAN_TELEPHONY = 1 };
  enum : int32_t { NUMBER_PLAN_RESERVED_2 = 2 };
  enum : int32_t { NUMBER_PLAN_DATA = 3 };
  enum : int32_t { NUMBER_PLAN_TELEX = 4 };
  enum : int32_t { NUMBER_PLAN_RESERVED_5 = 5 };
  enum : int32_t { NUMBER_PLAN_RESERVED_6 = 6 };
  enum : int32_t { NUMBER_PLAN_RESERVED_7 = 7 };
  enum : int32_t { NUMBER_PLAN_RESERVED_8 = 8 };
  enum : int32_t { NUMBER_PLAN_PRIVATE = 9 };
  enum : int32_t { NUMBER_PLAN_RESERVED_10 = 10 };
  enum : int32_t { NUMBER_PLAN_RESERVED_11 = 11 };
  enum : int32_t { NUMBER_PLAN_RESERVED_12 = 12 };
  enum : int32_t { NUMBER_PLAN_RESERVED_13 = 13 };
  enum : int32_t { NUMBER_PLAN_RESERVED_14 = 14 };
  enum : int32_t { NUMBER_PLAN_RESERVED_15 = 15 };
  enum : int32_t { NUMBER_TYPE_UNKNOWN = 0 };
  enum : int32_t { NUMBER_TYPE_INTERNATIONAL_OR_DATA_IP = 1 };
  enum : int32_t { NUMBER_TYPE_NATIONAL_OR_INTERNET_MAIL = 2 };
  enum : int32_t { NUMBER_TYPE_NETWORK = 3 };
  enum : int32_t { NUMBER_TYPE_SUBSCRIBER = 4 };
  enum : int32_t { NUMBER_TYPE_ALPHANUMERIC = 5 };
  enum : int32_t { NUMBER_TYPE_ABBREVIATED = 6 };
  enum : int32_t { NUMBER_TYPE_RESERVED_7 = 7 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSmsAddress{";
    os << "digitMode: " << ::android::internal::ToString(digitMode);
    os << ", isNumberModeDataNetwork: " << ::android::internal::ToString(isNumberModeDataNetwork);
    os << ", numberType: " << ::android::internal::ToString(numberType);
    os << ", numberPlan: " << ::android::internal::ToString(numberPlan);
    os << ", digits: " << ::android::internal::ToString(digits);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
