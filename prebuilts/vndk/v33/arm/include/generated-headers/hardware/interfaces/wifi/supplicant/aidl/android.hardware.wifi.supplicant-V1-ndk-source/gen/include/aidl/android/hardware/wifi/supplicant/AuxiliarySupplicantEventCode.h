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
enum class AuxiliarySupplicantEventCode : int32_t {
  EAP_METHOD_SELECTED = 0,
  SSID_TEMP_DISABLED = 1,
  OPEN_SSL_FAILURE = 2,
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
[[nodiscard]] static inline std::string toString(AuxiliarySupplicantEventCode val) {
  switch(val) {
  case AuxiliarySupplicantEventCode::EAP_METHOD_SELECTED:
    return "EAP_METHOD_SELECTED";
  case AuxiliarySupplicantEventCode::SSID_TEMP_DISABLED:
    return "SSID_TEMP_DISABLED";
  case AuxiliarySupplicantEventCode::OPEN_SSL_FAILURE:
    return "OPEN_SSL_FAILURE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode, 3> enum_values<aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode> = {
  aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode::EAP_METHOD_SELECTED,
  aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode::SSID_TEMP_DISABLED,
  aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode::OPEN_SSL_FAILURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
