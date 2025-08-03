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
enum class IfaceType : int32_t {
  STA = 0,
  P2P = 1,
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
[[nodiscard]] static inline std::string toString(IfaceType val) {
  switch(val) {
  case IfaceType::STA:
    return "STA";
  case IfaceType::P2P:
    return "P2P";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::IfaceType, 2> enum_values<aidl::android::hardware::wifi::supplicant::IfaceType> = {
  aidl::android::hardware::wifi::supplicant::IfaceType::STA,
  aidl::android::hardware::wifi::supplicant::IfaceType::P2P,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
