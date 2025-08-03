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
enum class RadioAccessFamily : int32_t {
  UNKNOWN = 1,
  GPRS = 2,
  EDGE = 4,
  UMTS = 8,
  IS95A = 16,
  IS95B = 32,
  ONE_X_RTT = 64,
  EVDO_0 = 128,
  EVDO_A = 256,
  HSDPA = 512,
  HSUPA = 1024,
  HSPA = 2048,
  EVDO_B = 4096,
  EHRPD = 8192,
  LTE = 16384,
  HSPAP = 32768,
  GSM = 65536,
  TD_SCDMA = 131072,
  IWLAN = 262144,
  LTE_CA = 524288,
  NR = 1048576,
};

}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
[[nodiscard]] static inline std::string toString(RadioAccessFamily val) {
  switch(val) {
  case RadioAccessFamily::UNKNOWN:
    return "UNKNOWN";
  case RadioAccessFamily::GPRS:
    return "GPRS";
  case RadioAccessFamily::EDGE:
    return "EDGE";
  case RadioAccessFamily::UMTS:
    return "UMTS";
  case RadioAccessFamily::IS95A:
    return "IS95A";
  case RadioAccessFamily::IS95B:
    return "IS95B";
  case RadioAccessFamily::ONE_X_RTT:
    return "ONE_X_RTT";
  case RadioAccessFamily::EVDO_0:
    return "EVDO_0";
  case RadioAccessFamily::EVDO_A:
    return "EVDO_A";
  case RadioAccessFamily::HSDPA:
    return "HSDPA";
  case RadioAccessFamily::HSUPA:
    return "HSUPA";
  case RadioAccessFamily::HSPA:
    return "HSPA";
  case RadioAccessFamily::EVDO_B:
    return "EVDO_B";
  case RadioAccessFamily::EHRPD:
    return "EHRPD";
  case RadioAccessFamily::LTE:
    return "LTE";
  case RadioAccessFamily::HSPAP:
    return "HSPAP";
  case RadioAccessFamily::GSM:
    return "GSM";
  case RadioAccessFamily::TD_SCDMA:
    return "TD_SCDMA";
  case RadioAccessFamily::IWLAN:
    return "IWLAN";
  case RadioAccessFamily::LTE_CA:
    return "LTE_CA";
  case RadioAccessFamily::NR:
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
constexpr inline std::array<aidl::android::hardware::radio::RadioAccessFamily, 21> enum_values<aidl::android::hardware::radio::RadioAccessFamily> = {
  aidl::android::hardware::radio::RadioAccessFamily::UNKNOWN,
  aidl::android::hardware::radio::RadioAccessFamily::GPRS,
  aidl::android::hardware::radio::RadioAccessFamily::EDGE,
  aidl::android::hardware::radio::RadioAccessFamily::UMTS,
  aidl::android::hardware::radio::RadioAccessFamily::IS95A,
  aidl::android::hardware::radio::RadioAccessFamily::IS95B,
  aidl::android::hardware::radio::RadioAccessFamily::ONE_X_RTT,
  aidl::android::hardware::radio::RadioAccessFamily::EVDO_0,
  aidl::android::hardware::radio::RadioAccessFamily::EVDO_A,
  aidl::android::hardware::radio::RadioAccessFamily::HSDPA,
  aidl::android::hardware::radio::RadioAccessFamily::HSUPA,
  aidl::android::hardware::radio::RadioAccessFamily::HSPA,
  aidl::android::hardware::radio::RadioAccessFamily::EVDO_B,
  aidl::android::hardware::radio::RadioAccessFamily::EHRPD,
  aidl::android::hardware::radio::RadioAccessFamily::LTE,
  aidl::android::hardware::radio::RadioAccessFamily::HSPAP,
  aidl::android::hardware::radio::RadioAccessFamily::GSM,
  aidl::android::hardware::radio::RadioAccessFamily::TD_SCDMA,
  aidl::android::hardware::radio::RadioAccessFamily::IWLAN,
  aidl::android::hardware::radio::RadioAccessFamily::LTE_CA,
  aidl::android::hardware::radio::RadioAccessFamily::NR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
