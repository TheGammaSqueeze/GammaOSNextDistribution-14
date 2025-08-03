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
enum class MboAssocDisallowedReasonCode : int8_t {
  RESERVED = 0,
  UNSPECIFIED = 1,
  MAX_NUM_STA_ASSOCIATED = 2,
  AIR_INTERFACE_OVERLOADED = 3,
  AUTH_SERVER_OVERLOADED = 4,
  INSUFFICIENT_RSSI = 5,
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
[[nodiscard]] static inline std::string toString(MboAssocDisallowedReasonCode val) {
  switch(val) {
  case MboAssocDisallowedReasonCode::RESERVED:
    return "RESERVED";
  case MboAssocDisallowedReasonCode::UNSPECIFIED:
    return "UNSPECIFIED";
  case MboAssocDisallowedReasonCode::MAX_NUM_STA_ASSOCIATED:
    return "MAX_NUM_STA_ASSOCIATED";
  case MboAssocDisallowedReasonCode::AIR_INTERFACE_OVERLOADED:
    return "AIR_INTERFACE_OVERLOADED";
  case MboAssocDisallowedReasonCode::AUTH_SERVER_OVERLOADED:
    return "AUTH_SERVER_OVERLOADED";
  case MboAssocDisallowedReasonCode::INSUFFICIENT_RSSI:
    return "INSUFFICIENT_RSSI";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode, 6> enum_values<aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode> = {
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::RESERVED,
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::UNSPECIFIED,
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::MAX_NUM_STA_ASSOCIATED,
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::AIR_INTERFACE_OVERLOADED,
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::AUTH_SERVER_OVERLOADED,
  aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode::INSUFFICIENT_RSSI,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
