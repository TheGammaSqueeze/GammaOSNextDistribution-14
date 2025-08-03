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
namespace hostapd {
enum class HostapdStatusCode : int32_t {
  SUCCESS = 0,
  FAILURE_UNKNOWN = 1,
  FAILURE_ARGS_INVALID = 2,
  FAILURE_IFACE_UNKNOWN = 3,
  FAILURE_IFACE_EXISTS = 4,
  FAILURE_CLIENT_UNKNOWN = 5,
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
[[nodiscard]] static inline std::string toString(HostapdStatusCode val) {
  switch(val) {
  case HostapdStatusCode::SUCCESS:
    return "SUCCESS";
  case HostapdStatusCode::FAILURE_UNKNOWN:
    return "FAILURE_UNKNOWN";
  case HostapdStatusCode::FAILURE_ARGS_INVALID:
    return "FAILURE_ARGS_INVALID";
  case HostapdStatusCode::FAILURE_IFACE_UNKNOWN:
    return "FAILURE_IFACE_UNKNOWN";
  case HostapdStatusCode::FAILURE_IFACE_EXISTS:
    return "FAILURE_IFACE_EXISTS";
  case HostapdStatusCode::FAILURE_CLIENT_UNKNOWN:
    return "FAILURE_CLIENT_UNKNOWN";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::HostapdStatusCode, 6> enum_values<aidl::android::hardware::wifi::hostapd::HostapdStatusCode> = {
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::SUCCESS,
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::FAILURE_UNKNOWN,
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::FAILURE_ARGS_INVALID,
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::FAILURE_IFACE_UNKNOWN,
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::FAILURE_IFACE_EXISTS,
  aidl::android::hardware::wifi::hostapd::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
