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
enum class TransitionDisableIndication : int32_t {
  USE_WPA3_PERSONAL = 1,
  USE_SAE_PK = 2,
  USE_WPA3_ENTERPRISE = 4,
  USE_ENHANCED_OPEN = 8,
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
[[nodiscard]] static inline std::string toString(TransitionDisableIndication val) {
  switch(val) {
  case TransitionDisableIndication::USE_WPA3_PERSONAL:
    return "USE_WPA3_PERSONAL";
  case TransitionDisableIndication::USE_SAE_PK:
    return "USE_SAE_PK";
  case TransitionDisableIndication::USE_WPA3_ENTERPRISE:
    return "USE_WPA3_ENTERPRISE";
  case TransitionDisableIndication::USE_ENHANCED_OPEN:
    return "USE_ENHANCED_OPEN";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::TransitionDisableIndication, 4> enum_values<aidl::android::hardware::wifi::supplicant::TransitionDisableIndication> = {
  aidl::android::hardware::wifi::supplicant::TransitionDisableIndication::USE_WPA3_PERSONAL,
  aidl::android::hardware::wifi::supplicant::TransitionDisableIndication::USE_SAE_PK,
  aidl::android::hardware::wifi::supplicant::TransitionDisableIndication::USE_WPA3_ENTERPRISE,
  aidl::android::hardware::wifi::supplicant::TransitionDisableIndication::USE_ENHANCED_OPEN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
