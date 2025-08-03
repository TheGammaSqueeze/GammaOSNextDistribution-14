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
enum class EncryptionType : int32_t {
  NONE = 0,
  WPA = 1,
  WPA2 = 2,
  WPA3_SAE_TRANSITION = 3,
  WPA3_SAE = 4,
  WPA3_OWE_TRANSITION = 5,
  WPA3_OWE = 6,
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
[[nodiscard]] static inline std::string toString(EncryptionType val) {
  switch(val) {
  case EncryptionType::NONE:
    return "NONE";
  case EncryptionType::WPA:
    return "WPA";
  case EncryptionType::WPA2:
    return "WPA2";
  case EncryptionType::WPA3_SAE_TRANSITION:
    return "WPA3_SAE_TRANSITION";
  case EncryptionType::WPA3_SAE:
    return "WPA3_SAE";
  case EncryptionType::WPA3_OWE_TRANSITION:
    return "WPA3_OWE_TRANSITION";
  case EncryptionType::WPA3_OWE:
    return "WPA3_OWE";
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
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::EncryptionType, 7> enum_values<aidl::android::hardware::wifi::hostapd::EncryptionType> = {
  aidl::android::hardware::wifi::hostapd::EncryptionType::NONE,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA2,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA3_SAE_TRANSITION,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA3_SAE,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA3_OWE_TRANSITION,
  aidl::android::hardware::wifi::hostapd::EncryptionType::WPA3_OWE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
