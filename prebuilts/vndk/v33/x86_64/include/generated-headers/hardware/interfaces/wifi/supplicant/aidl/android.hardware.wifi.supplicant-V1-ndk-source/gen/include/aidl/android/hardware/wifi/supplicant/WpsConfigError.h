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
enum class WpsConfigError : int32_t {
  NO_ERROR = 0,
  OOB_IFACE_READ_ERROR = 1,
  DECRYPTION_CRC_FAILURE = 2,
  CHAN_24_NOT_SUPPORTED = 3,
  CHAN_50_NOT_SUPPORTED = 4,
  SIGNAL_TOO_WEAK = 5,
  NETWORK_AUTH_FAILURE = 6,
  NETWORK_ASSOC_FAILURE = 7,
  NO_DHCP_RESPONSE = 8,
  FAILED_DHCP_CONFIG = 9,
  IP_ADDR_CONFLICT = 10,
  NO_CONN_TO_REGISTRAR = 11,
  MULTIPLE_PBC_DETECTED = 12,
  ROGUE_SUSPECTED = 13,
  DEVICE_BUSY = 14,
  SETUP_LOCKED = 15,
  MSG_TIMEOUT = 16,
  REG_SESS_TIMEOUT = 17,
  DEV_PASSWORD_AUTH_FAILURE = 18,
  CHAN_60G_NOT_SUPPORTED = 19,
  PUBLIC_KEY_HASH_MISMATCH = 20,
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
[[nodiscard]] static inline std::string toString(WpsConfigError val) {
  switch(val) {
  case WpsConfigError::NO_ERROR:
    return "NO_ERROR";
  case WpsConfigError::OOB_IFACE_READ_ERROR:
    return "OOB_IFACE_READ_ERROR";
  case WpsConfigError::DECRYPTION_CRC_FAILURE:
    return "DECRYPTION_CRC_FAILURE";
  case WpsConfigError::CHAN_24_NOT_SUPPORTED:
    return "CHAN_24_NOT_SUPPORTED";
  case WpsConfigError::CHAN_50_NOT_SUPPORTED:
    return "CHAN_50_NOT_SUPPORTED";
  case WpsConfigError::SIGNAL_TOO_WEAK:
    return "SIGNAL_TOO_WEAK";
  case WpsConfigError::NETWORK_AUTH_FAILURE:
    return "NETWORK_AUTH_FAILURE";
  case WpsConfigError::NETWORK_ASSOC_FAILURE:
    return "NETWORK_ASSOC_FAILURE";
  case WpsConfigError::NO_DHCP_RESPONSE:
    return "NO_DHCP_RESPONSE";
  case WpsConfigError::FAILED_DHCP_CONFIG:
    return "FAILED_DHCP_CONFIG";
  case WpsConfigError::IP_ADDR_CONFLICT:
    return "IP_ADDR_CONFLICT";
  case WpsConfigError::NO_CONN_TO_REGISTRAR:
    return "NO_CONN_TO_REGISTRAR";
  case WpsConfigError::MULTIPLE_PBC_DETECTED:
    return "MULTIPLE_PBC_DETECTED";
  case WpsConfigError::ROGUE_SUSPECTED:
    return "ROGUE_SUSPECTED";
  case WpsConfigError::DEVICE_BUSY:
    return "DEVICE_BUSY";
  case WpsConfigError::SETUP_LOCKED:
    return "SETUP_LOCKED";
  case WpsConfigError::MSG_TIMEOUT:
    return "MSG_TIMEOUT";
  case WpsConfigError::REG_SESS_TIMEOUT:
    return "REG_SESS_TIMEOUT";
  case WpsConfigError::DEV_PASSWORD_AUTH_FAILURE:
    return "DEV_PASSWORD_AUTH_FAILURE";
  case WpsConfigError::CHAN_60G_NOT_SUPPORTED:
    return "CHAN_60G_NOT_SUPPORTED";
  case WpsConfigError::PUBLIC_KEY_HASH_MISMATCH:
    return "PUBLIC_KEY_HASH_MISMATCH";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WpsConfigError, 21> enum_values<aidl::android::hardware::wifi::supplicant::WpsConfigError> = {
  aidl::android::hardware::wifi::supplicant::WpsConfigError::NO_ERROR,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::OOB_IFACE_READ_ERROR,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::DECRYPTION_CRC_FAILURE,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::CHAN_24_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::CHAN_50_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::SIGNAL_TOO_WEAK,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::NETWORK_AUTH_FAILURE,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::NETWORK_ASSOC_FAILURE,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::NO_DHCP_RESPONSE,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::FAILED_DHCP_CONFIG,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::IP_ADDR_CONFLICT,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::NO_CONN_TO_REGISTRAR,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::MULTIPLE_PBC_DETECTED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::ROGUE_SUSPECTED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::DEVICE_BUSY,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::SETUP_LOCKED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::MSG_TIMEOUT,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::REG_SESS_TIMEOUT,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::CHAN_60G_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
