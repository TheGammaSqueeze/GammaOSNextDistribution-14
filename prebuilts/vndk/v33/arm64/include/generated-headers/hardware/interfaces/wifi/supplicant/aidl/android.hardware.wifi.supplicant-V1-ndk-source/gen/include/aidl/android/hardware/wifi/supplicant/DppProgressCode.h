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
enum class DppProgressCode : int32_t {
  AUTHENTICATION_SUCCESS = 0,
  RESPONSE_PENDING = 1,
  CONFIGURATION_SENT_WAITING_RESPONSE = 2,
  CONFIGURATION_ACCEPTED = 3,
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
[[nodiscard]] static inline std::string toString(DppProgressCode val) {
  switch(val) {
  case DppProgressCode::AUTHENTICATION_SUCCESS:
    return "AUTHENTICATION_SUCCESS";
  case DppProgressCode::RESPONSE_PENDING:
    return "RESPONSE_PENDING";
  case DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE:
    return "CONFIGURATION_SENT_WAITING_RESPONSE";
  case DppProgressCode::CONFIGURATION_ACCEPTED:
    return "CONFIGURATION_ACCEPTED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppProgressCode, 4> enum_values<aidl::android::hardware::wifi::supplicant::DppProgressCode> = {
  aidl::android::hardware::wifi::supplicant::DppProgressCode::AUTHENTICATION_SUCCESS,
  aidl::android::hardware::wifi::supplicant::DppProgressCode::RESPONSE_PENDING,
  aidl::android::hardware::wifi::supplicant::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE,
  aidl::android::hardware::wifi::supplicant::DppProgressCode::CONFIGURATION_ACCEPTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
