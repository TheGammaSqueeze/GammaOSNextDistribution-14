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
enum class EapErrorCode : int32_t {
  SIM_GENERAL_FAILURE_AFTER_AUTH = 0,
  SIM_TEMPORARILY_DENIED = 1026,
  SIM_NOT_SUBSCRIBED = 1031,
  SIM_GENERAL_FAILURE_BEFORE_AUTH = 16384,
  SIM_VENDOR_SPECIFIC_EXPIRED_CERT = 16385,
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
[[nodiscard]] static inline std::string toString(EapErrorCode val) {
  switch(val) {
  case EapErrorCode::SIM_GENERAL_FAILURE_AFTER_AUTH:
    return "SIM_GENERAL_FAILURE_AFTER_AUTH";
  case EapErrorCode::SIM_TEMPORARILY_DENIED:
    return "SIM_TEMPORARILY_DENIED";
  case EapErrorCode::SIM_NOT_SUBSCRIBED:
    return "SIM_NOT_SUBSCRIBED";
  case EapErrorCode::SIM_GENERAL_FAILURE_BEFORE_AUTH:
    return "SIM_GENERAL_FAILURE_BEFORE_AUTH";
  case EapErrorCode::SIM_VENDOR_SPECIFIC_EXPIRED_CERT:
    return "SIM_VENDOR_SPECIFIC_EXPIRED_CERT";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::EapErrorCode, 5> enum_values<aidl::android::hardware::wifi::supplicant::EapErrorCode> = {
  aidl::android::hardware::wifi::supplicant::EapErrorCode::SIM_GENERAL_FAILURE_AFTER_AUTH,
  aidl::android::hardware::wifi::supplicant::EapErrorCode::SIM_TEMPORARILY_DENIED,
  aidl::android::hardware::wifi::supplicant::EapErrorCode::SIM_NOT_SUBSCRIBED,
  aidl::android::hardware::wifi::supplicant::EapErrorCode::SIM_GENERAL_FAILURE_BEFORE_AUTH,
  aidl::android::hardware::wifi::supplicant::EapErrorCode::SIM_VENDOR_SPECIFIC_EXPIRED_CERT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
