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
enum class P2pProvDiscStatusCode : int8_t {
  SUCCESS = 0,
  TIMEOUT = 1,
  REJECTED = 2,
  TIMEOUT_JOIN = 3,
  INFO_UNAVAILABLE = 4,
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
[[nodiscard]] static inline std::string toString(P2pProvDiscStatusCode val) {
  switch(val) {
  case P2pProvDiscStatusCode::SUCCESS:
    return "SUCCESS";
  case P2pProvDiscStatusCode::TIMEOUT:
    return "TIMEOUT";
  case P2pProvDiscStatusCode::REJECTED:
    return "REJECTED";
  case P2pProvDiscStatusCode::TIMEOUT_JOIN:
    return "TIMEOUT_JOIN";
  case P2pProvDiscStatusCode::INFO_UNAVAILABLE:
    return "INFO_UNAVAILABLE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode, 5> enum_values<aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode> = {
  aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode::SUCCESS,
  aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode::TIMEOUT,
  aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode::REJECTED,
  aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode::TIMEOUT_JOIN,
  aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode::INFO_UNAVAILABLE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
