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
enum class SupplicantStatusCode : int32_t {
  SUCCESS = 0,
  FAILURE_UNKNOWN = 1,
  FAILURE_ARGS_INVALID = 2,
  FAILURE_IFACE_INVALID = 3,
  FAILURE_IFACE_UNKNOWN = 4,
  FAILURE_IFACE_EXISTS = 5,
  FAILURE_IFACE_DISABLED = 6,
  FAILURE_IFACE_NOT_DISCONNECTED = 7,
  FAILURE_NETWORK_INVALID = 8,
  FAILURE_NETWORK_UNKNOWN = 9,
  FAILURE_UNSUPPORTED = 10,
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
[[nodiscard]] static inline std::string toString(SupplicantStatusCode val) {
  switch(val) {
  case SupplicantStatusCode::SUCCESS:
    return "SUCCESS";
  case SupplicantStatusCode::FAILURE_UNKNOWN:
    return "FAILURE_UNKNOWN";
  case SupplicantStatusCode::FAILURE_ARGS_INVALID:
    return "FAILURE_ARGS_INVALID";
  case SupplicantStatusCode::FAILURE_IFACE_INVALID:
    return "FAILURE_IFACE_INVALID";
  case SupplicantStatusCode::FAILURE_IFACE_UNKNOWN:
    return "FAILURE_IFACE_UNKNOWN";
  case SupplicantStatusCode::FAILURE_IFACE_EXISTS:
    return "FAILURE_IFACE_EXISTS";
  case SupplicantStatusCode::FAILURE_IFACE_DISABLED:
    return "FAILURE_IFACE_DISABLED";
  case SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED:
    return "FAILURE_IFACE_NOT_DISCONNECTED";
  case SupplicantStatusCode::FAILURE_NETWORK_INVALID:
    return "FAILURE_NETWORK_INVALID";
  case SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN:
    return "FAILURE_NETWORK_UNKNOWN";
  case SupplicantStatusCode::FAILURE_UNSUPPORTED:
    return "FAILURE_UNSUPPORTED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::SupplicantStatusCode, 11> enum_values<aidl::android::hardware::wifi::supplicant::SupplicantStatusCode> = {
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::SUCCESS,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_UNKNOWN,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_ARGS_INVALID,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_IFACE_INVALID,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_IFACE_EXISTS,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_IFACE_DISABLED,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_NETWORK_INVALID,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN,
  aidl::android::hardware::wifi::supplicant::SupplicantStatusCode::FAILURE_UNSUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
