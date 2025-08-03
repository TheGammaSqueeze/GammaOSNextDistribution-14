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
enum class RxFilterType : int8_t {
  V4_MULTICAST = 0,
  V6_MULTICAST = 1,
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
[[nodiscard]] static inline std::string toString(RxFilterType val) {
  switch(val) {
  case RxFilterType::V4_MULTICAST:
    return "V4_MULTICAST";
  case RxFilterType::V6_MULTICAST:
    return "V6_MULTICAST";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::RxFilterType, 2> enum_values<aidl::android::hardware::wifi::supplicant::RxFilterType> = {
  aidl::android::hardware::wifi::supplicant::RxFilterType::V4_MULTICAST,
  aidl::android::hardware::wifi::supplicant::RxFilterType::V6_MULTICAST,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
