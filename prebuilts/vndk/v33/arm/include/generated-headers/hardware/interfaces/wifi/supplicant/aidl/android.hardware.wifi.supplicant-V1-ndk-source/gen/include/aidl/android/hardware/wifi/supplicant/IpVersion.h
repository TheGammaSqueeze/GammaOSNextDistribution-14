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
enum class IpVersion : int8_t {
  VERSION_4 = 0,
  VERSION_6 = 1,
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
[[nodiscard]] static inline std::string toString(IpVersion val) {
  switch(val) {
  case IpVersion::VERSION_4:
    return "VERSION_4";
  case IpVersion::VERSION_6:
    return "VERSION_6";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::IpVersion, 2> enum_values<aidl::android::hardware::wifi::supplicant::IpVersion> = {
  aidl::android::hardware::wifi::supplicant::IpVersion::VERSION_4,
  aidl::android::hardware::wifi::supplicant::IpVersion::VERSION_6,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
