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
enum class WpsErrorIndication : int32_t {
  NO_ERROR = 0,
  SECURITY_TKIP_ONLY_PROHIBITED = 1,
  SECURITY_WEP_PROHIBITED = 2,
  AUTH_FAILURE = 3,
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
[[nodiscard]] static inline std::string toString(WpsErrorIndication val) {
  switch(val) {
  case WpsErrorIndication::NO_ERROR:
    return "NO_ERROR";
  case WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED:
    return "SECURITY_TKIP_ONLY_PROHIBITED";
  case WpsErrorIndication::SECURITY_WEP_PROHIBITED:
    return "SECURITY_WEP_PROHIBITED";
  case WpsErrorIndication::AUTH_FAILURE:
    return "AUTH_FAILURE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WpsErrorIndication, 4> enum_values<aidl::android::hardware::wifi::supplicant::WpsErrorIndication> = {
  aidl::android::hardware::wifi::supplicant::WpsErrorIndication::NO_ERROR,
  aidl::android::hardware::wifi::supplicant::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED,
  aidl::android::hardware::wifi::supplicant::WpsErrorIndication::SECURITY_WEP_PROHIBITED,
  aidl::android::hardware::wifi::supplicant::WpsErrorIndication::AUTH_FAILURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
