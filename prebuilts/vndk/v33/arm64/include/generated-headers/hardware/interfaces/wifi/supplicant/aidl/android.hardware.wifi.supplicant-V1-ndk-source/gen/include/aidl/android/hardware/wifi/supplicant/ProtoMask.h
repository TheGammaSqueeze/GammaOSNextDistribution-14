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
enum class ProtoMask : int32_t {
  WPA = 1,
  RSN = 2,
  WAPI = 4,
  OSEN = 8,
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
[[nodiscard]] static inline std::string toString(ProtoMask val) {
  switch(val) {
  case ProtoMask::WPA:
    return "WPA";
  case ProtoMask::RSN:
    return "RSN";
  case ProtoMask::WAPI:
    return "WAPI";
  case ProtoMask::OSEN:
    return "OSEN";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::ProtoMask, 4> enum_values<aidl::android::hardware::wifi::supplicant::ProtoMask> = {
  aidl::android::hardware::wifi::supplicant::ProtoMask::WPA,
  aidl::android::hardware::wifi::supplicant::ProtoMask::RSN,
  aidl::android::hardware::wifi::supplicant::ProtoMask::WAPI,
  aidl::android::hardware::wifi::supplicant::ProtoMask::OSEN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
