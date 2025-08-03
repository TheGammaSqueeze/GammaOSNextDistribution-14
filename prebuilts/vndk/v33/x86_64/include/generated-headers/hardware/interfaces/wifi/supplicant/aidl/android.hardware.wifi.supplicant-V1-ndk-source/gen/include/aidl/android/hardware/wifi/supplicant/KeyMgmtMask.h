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
enum class KeyMgmtMask : int32_t {
  WPA_EAP = 1,
  WPA_PSK = 2,
  NONE = 4,
  IEEE8021X = 8,
  FT_EAP = 32,
  FT_PSK = 64,
  OSEN = 32768,
  WPA_EAP_SHA256 = 128,
  WPA_PSK_SHA256 = 256,
  SAE = 1024,
  SUITE_B_192 = 131072,
  OWE = 4194304,
  DPP = 8388608,
  WAPI_PSK = 4096,
  WAPI_CERT = 8192,
  FILS_SHA256 = 262144,
  FILS_SHA384 = 524288,
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
[[nodiscard]] static inline std::string toString(KeyMgmtMask val) {
  switch(val) {
  case KeyMgmtMask::WPA_EAP:
    return "WPA_EAP";
  case KeyMgmtMask::WPA_PSK:
    return "WPA_PSK";
  case KeyMgmtMask::NONE:
    return "NONE";
  case KeyMgmtMask::IEEE8021X:
    return "IEEE8021X";
  case KeyMgmtMask::FT_EAP:
    return "FT_EAP";
  case KeyMgmtMask::FT_PSK:
    return "FT_PSK";
  case KeyMgmtMask::OSEN:
    return "OSEN";
  case KeyMgmtMask::WPA_EAP_SHA256:
    return "WPA_EAP_SHA256";
  case KeyMgmtMask::WPA_PSK_SHA256:
    return "WPA_PSK_SHA256";
  case KeyMgmtMask::SAE:
    return "SAE";
  case KeyMgmtMask::SUITE_B_192:
    return "SUITE_B_192";
  case KeyMgmtMask::OWE:
    return "OWE";
  case KeyMgmtMask::DPP:
    return "DPP";
  case KeyMgmtMask::WAPI_PSK:
    return "WAPI_PSK";
  case KeyMgmtMask::WAPI_CERT:
    return "WAPI_CERT";
  case KeyMgmtMask::FILS_SHA256:
    return "FILS_SHA256";
  case KeyMgmtMask::FILS_SHA384:
    return "FILS_SHA384";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::KeyMgmtMask, 17> enum_values<aidl::android::hardware::wifi::supplicant::KeyMgmtMask> = {
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WPA_EAP,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WPA_PSK,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::NONE,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::IEEE8021X,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::FT_EAP,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::FT_PSK,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::OSEN,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WPA_EAP_SHA256,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WPA_PSK_SHA256,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::SAE,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::SUITE_B_192,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::OWE,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::DPP,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WAPI_PSK,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::WAPI_CERT,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::FILS_SHA256,
  aidl::android::hardware::wifi::supplicant::KeyMgmtMask::FILS_SHA384,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
