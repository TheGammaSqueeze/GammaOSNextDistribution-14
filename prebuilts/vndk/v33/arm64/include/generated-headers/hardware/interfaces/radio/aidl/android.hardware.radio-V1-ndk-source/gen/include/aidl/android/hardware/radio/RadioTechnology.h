#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
enum class RadioTechnology : int32_t {
  UNKNOWN = 0,
  GPRS = 1,
  EDGE = 2,
  UMTS = 3,
  IS95A = 4,
  IS95B = 5,
  ONE_X_RTT = 6,
  EVDO_0 = 7,
  EVDO_A = 8,
  HSDPA = 9,
  HSUPA = 10,
  HSPA = 11,
  EVDO_B = 12,
  EHRPD = 13,
  LTE = 14,
  HSPAP = 15,
  GSM = 16,
  TD_SCDMA = 17,
  IWLAN = 18,
  LTE_CA = 19,
  NR = 20,
};

}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
[[nodiscard]] static inline std::string toString(RadioTechnology val) {
  switch(val) {
  case RadioTechnology::UNKNOWN:
    return "UNKNOWN";
  case RadioTechnology::GPRS:
    return "GPRS";
  case RadioTechnology::EDGE:
    return "EDGE";
  case RadioTechnology::UMTS:
    return "UMTS";
  case RadioTechnology::IS95A:
    return "IS95A";
  case RadioTechnology::IS95B:
    return "IS95B";
  case RadioTechnology::ONE_X_RTT:
    return "ONE_X_RTT";
  case RadioTechnology::EVDO_0:
    return "EVDO_0";
  case RadioTechnology::EVDO_A:
    return "EVDO_A";
  case RadioTechnology::HSDPA:
    return "HSDPA";
  case RadioTechnology::HSUPA:
    return "HSUPA";
  case RadioTechnology::HSPA:
    return "HSPA";
  case RadioTechnology::EVDO_B:
    return "EVDO_B";
  case RadioTechnology::EHRPD:
    return "EHRPD";
  case RadioTechnology::LTE:
    return "LTE";
  case RadioTechnology::HSPAP:
    return "HSPAP";
  case RadioTechnology::GSM:
    return "GSM";
  case RadioTechnology::TD_SCDMA:
    return "TD_SCDMA";
  case RadioTechnology::IWLAN:
    return "IWLAN";
  case RadioTechnology::LTE_CA:
    return "LTE_CA";
  case RadioTechnology::NR:
    return "NR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::RadioTechnology, 21> enum_values<aidl::android::hardware::radio::RadioTechnology> = {
  aidl::android::hardware::radio::RadioTechnology::UNKNOWN,
  aidl::android::hardware::radio::RadioTechnology::GPRS,
  aidl::android::hardware::radio::RadioTechnology::EDGE,
  aidl::android::hardware::radio::RadioTechnology::UMTS,
  aidl::android::hardware::radio::RadioTechnology::IS95A,
  aidl::android::hardware::radio::RadioTechnology::IS95B,
  aidl::android::hardware::radio::RadioTechnology::ONE_X_RTT,
  aidl::android::hardware::radio::RadioTechnology::EVDO_0,
  aidl::android::hardware::radio::RadioTechnology::EVDO_A,
  aidl::android::hardware::radio::RadioTechnology::HSDPA,
  aidl::android::hardware::radio::RadioTechnology::HSUPA,
  aidl::android::hardware::radio::RadioTechnology::HSPA,
  aidl::android::hardware::radio::RadioTechnology::EVDO_B,
  aidl::android::hardware::radio::RadioTechnology::EHRPD,
  aidl::android::hardware::radio::RadioTechnology::LTE,
  aidl::android::hardware::radio::RadioTechnology::HSPAP,
  aidl::android::hardware::radio::RadioTechnology::GSM,
  aidl::android::hardware::radio::RadioTechnology::TD_SCDMA,
  aidl::android::hardware::radio::RadioTechnology::IWLAN,
  aidl::android::hardware::radio::RadioTechnology::LTE_CA,
  aidl::android::hardware::radio::RadioTechnology::NR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
