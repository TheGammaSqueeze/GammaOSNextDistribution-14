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
enum class StaIfaceCallbackState : int32_t {
  DISCONNECTED = 0,
  IFACE_DISABLED = 1,
  INACTIVE = 2,
  SCANNING = 3,
  AUTHENTICATING = 4,
  ASSOCIATING = 5,
  ASSOCIATED = 6,
  FOURWAY_HANDSHAKE = 7,
  GROUP_HANDSHAKE = 8,
  COMPLETED = 9,
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
[[nodiscard]] static inline std::string toString(StaIfaceCallbackState val) {
  switch(val) {
  case StaIfaceCallbackState::DISCONNECTED:
    return "DISCONNECTED";
  case StaIfaceCallbackState::IFACE_DISABLED:
    return "IFACE_DISABLED";
  case StaIfaceCallbackState::INACTIVE:
    return "INACTIVE";
  case StaIfaceCallbackState::SCANNING:
    return "SCANNING";
  case StaIfaceCallbackState::AUTHENTICATING:
    return "AUTHENTICATING";
  case StaIfaceCallbackState::ASSOCIATING:
    return "ASSOCIATING";
  case StaIfaceCallbackState::ASSOCIATED:
    return "ASSOCIATED";
  case StaIfaceCallbackState::FOURWAY_HANDSHAKE:
    return "FOURWAY_HANDSHAKE";
  case StaIfaceCallbackState::GROUP_HANDSHAKE:
    return "GROUP_HANDSHAKE";
  case StaIfaceCallbackState::COMPLETED:
    return "COMPLETED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState, 10> enum_values<aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState> = {
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::DISCONNECTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::IFACE_DISABLED,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::INACTIVE,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::SCANNING,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::AUTHENTICATING,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::ASSOCIATING,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::ASSOCIATED,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::FOURWAY_HANDSHAKE,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::GROUP_HANDSHAKE,
  aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState::COMPLETED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
