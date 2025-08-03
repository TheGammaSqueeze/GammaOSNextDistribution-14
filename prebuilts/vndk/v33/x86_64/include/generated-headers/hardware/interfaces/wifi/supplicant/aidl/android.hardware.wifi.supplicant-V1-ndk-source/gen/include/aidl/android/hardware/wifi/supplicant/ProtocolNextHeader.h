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
enum class ProtocolNextHeader : int8_t {
  TCP = 6,
  UDP = 17,
  ESP = 50,
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
[[nodiscard]] static inline std::string toString(ProtocolNextHeader val) {
  switch(val) {
  case ProtocolNextHeader::TCP:
    return "TCP";
  case ProtocolNextHeader::UDP:
    return "UDP";
  case ProtocolNextHeader::ESP:
    return "ESP";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::ProtocolNextHeader, 3> enum_values<aidl::android::hardware::wifi::supplicant::ProtocolNextHeader> = {
  aidl::android::hardware::wifi::supplicant::ProtocolNextHeader::TCP,
  aidl::android::hardware::wifi::supplicant::ProtocolNextHeader::UDP,
  aidl::android::hardware::wifi::supplicant::ProtocolNextHeader::ESP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
