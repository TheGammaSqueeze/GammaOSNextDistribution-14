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
namespace wifi {
namespace supplicant {
enum class EapMethod : int32_t {
  PEAP = 0,
  TLS = 1,
  TTLS = 2,
  PWD = 3,
  SIM = 4,
  AKA = 5,
  AKA_PRIME = 6,
  WFA_UNAUTH_TLS = 7,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(EapMethod val) {
  switch(val) {
  case EapMethod::PEAP:
    return "PEAP";
  case EapMethod::TLS:
    return "TLS";
  case EapMethod::TTLS:
    return "TTLS";
  case EapMethod::PWD:
    return "PWD";
  case EapMethod::SIM:
    return "SIM";
  case EapMethod::AKA:
    return "AKA";
  case EapMethod::AKA_PRIME:
    return "AKA_PRIME";
  case EapMethod::WFA_UNAUTH_TLS:
    return "WFA_UNAUTH_TLS";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::EapMethod, 8> enum_values<aidl::android::hardware::wifi::supplicant::EapMethod> = {
  aidl::android::hardware::wifi::supplicant::EapMethod::PEAP,
  aidl::android::hardware::wifi::supplicant::EapMethod::TLS,
  aidl::android::hardware::wifi::supplicant::EapMethod::TTLS,
  aidl::android::hardware::wifi::supplicant::EapMethod::PWD,
  aidl::android::hardware::wifi::supplicant::EapMethod::SIM,
  aidl::android::hardware::wifi::supplicant::EapMethod::AKA,
  aidl::android::hardware::wifi::supplicant::EapMethod::AKA_PRIME,
  aidl::android::hardware::wifi::supplicant::EapMethod::WFA_UNAUTH_TLS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
