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
enum class BssidChangeReason : int8_t {
  ASSOC_START = 0,
  ASSOC_COMPLETE = 1,
  DISASSOC = 2,
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
[[nodiscard]] static inline std::string toString(BssidChangeReason val) {
  switch(val) {
  case BssidChangeReason::ASSOC_START:
    return "ASSOC_START";
  case BssidChangeReason::ASSOC_COMPLETE:
    return "ASSOC_COMPLETE";
  case BssidChangeReason::DISASSOC:
    return "DISASSOC";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::BssidChangeReason, 3> enum_values<aidl::android::hardware::wifi::supplicant::BssidChangeReason> = {
  aidl::android::hardware::wifi::supplicant::BssidChangeReason::ASSOC_START,
  aidl::android::hardware::wifi::supplicant::BssidChangeReason::ASSOC_COMPLETE,
  aidl::android::hardware::wifi::supplicant::BssidChangeReason::DISASSOC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
