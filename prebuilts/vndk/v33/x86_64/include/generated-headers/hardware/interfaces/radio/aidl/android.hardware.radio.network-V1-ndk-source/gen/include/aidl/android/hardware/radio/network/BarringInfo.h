#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/BarringTypeSpecificInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class BarringInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t serviceType = 0;
  int32_t barringType = 0;
  std::optional<::aidl::android::hardware::radio::network::BarringTypeSpecificInfo> barringTypeSpecificInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) != std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }
  inline bool operator<(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) < std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }
  inline bool operator<=(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) <= std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }
  inline bool operator==(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) == std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }
  inline bool operator>(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) > std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }
  inline bool operator>=(const BarringInfo& rhs) const {
    return std::tie(serviceType, barringType, barringTypeSpecificInfo) >= std::tie(rhs.serviceType, rhs.barringType, rhs.barringTypeSpecificInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { BARRING_TYPE_NONE = 0 };
  enum : int32_t { BARRING_TYPE_CONDITIONAL = 1 };
  enum : int32_t { BARRING_TYPE_UNCONDITIONAL = 2 };
  enum : int32_t { SERVICE_TYPE_CS_SERVICE = 0 };
  enum : int32_t { SERVICE_TYPE_PS_SERVICE = 1 };
  enum : int32_t { SERVICE_TYPE_CS_VOICE = 2 };
  enum : int32_t { SERVICE_TYPE_MO_SIGNALLING = 3 };
  enum : int32_t { SERVICE_TYPE_MO_DATA = 4 };
  enum : int32_t { SERVICE_TYPE_CS_FALLBACK = 5 };
  enum : int32_t { SERVICE_TYPE_MMTEL_VOICE = 6 };
  enum : int32_t { SERVICE_TYPE_MMTEL_VIDEO = 7 };
  enum : int32_t { SERVICE_TYPE_EMERGENCY = 8 };
  enum : int32_t { SERVICE_TYPE_SMS = 9 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_1 = 1001 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_2 = 1002 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_3 = 1003 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_4 = 1004 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_5 = 1005 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_6 = 1006 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_7 = 1007 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_8 = 1008 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_9 = 1009 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_10 = 1010 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_11 = 1011 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_12 = 1012 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_13 = 1013 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_14 = 1014 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_15 = 1015 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_16 = 1016 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_17 = 1017 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_18 = 1018 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_19 = 1019 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_20 = 1020 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_21 = 1021 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_22 = 1022 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_23 = 1023 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_24 = 1024 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_25 = 1025 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_26 = 1026 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_27 = 1027 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_28 = 1028 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_29 = 1029 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_30 = 1030 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_31 = 1031 };
  enum : int32_t { SERVICE_TYPE_OPERATOR_32 = 1032 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "BarringInfo{";
    os << "serviceType: " << ::android::internal::ToString(serviceType);
    os << ", barringType: " << ::android::internal::ToString(barringType);
    os << ", barringTypeSpecificInfo: " << ::android::internal::ToString(barringTypeSpecificInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
