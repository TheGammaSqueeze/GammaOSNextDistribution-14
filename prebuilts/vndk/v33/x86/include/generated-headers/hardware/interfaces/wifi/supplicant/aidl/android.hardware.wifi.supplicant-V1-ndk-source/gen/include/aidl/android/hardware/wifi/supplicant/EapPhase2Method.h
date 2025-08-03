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
enum class EapPhase2Method : int32_t {
  NONE = 0,
  PAP = 1,
  MSPAP = 2,
  MSPAPV2 = 3,
  GTC = 4,
  SIM = 5,
  AKA = 6,
  AKA_PRIME = 7,
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
[[nodiscard]] static inline std::string toString(EapPhase2Method val) {
  switch(val) {
  case EapPhase2Method::NONE:
    return "NONE";
  case EapPhase2Method::PAP:
    return "PAP";
  case EapPhase2Method::MSPAP:
    return "MSPAP";
  case EapPhase2Method::MSPAPV2:
    return "MSPAPV2";
  case EapPhase2Method::GTC:
    return "GTC";
  case EapPhase2Method::SIM:
    return "SIM";
  case EapPhase2Method::AKA:
    return "AKA";
  case EapPhase2Method::AKA_PRIME:
    return "AKA_PRIME";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::EapPhase2Method, 8> enum_values<aidl::android::hardware::wifi::supplicant::EapPhase2Method> = {
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::NONE,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::PAP,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::MSPAP,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::MSPAPV2,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::GTC,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::SIM,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::AKA,
  aidl::android::hardware::wifi::supplicant::EapPhase2Method::AKA_PRIME,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
