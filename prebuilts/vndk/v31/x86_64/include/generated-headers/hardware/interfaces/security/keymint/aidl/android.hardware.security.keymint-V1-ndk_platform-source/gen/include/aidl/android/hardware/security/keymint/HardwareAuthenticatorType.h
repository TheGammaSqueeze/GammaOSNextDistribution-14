#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
enum class HardwareAuthenticatorType : int32_t {
  NONE = 0,
  PASSWORD = 1,
  FINGERPRINT = 2,
  ANY = -1,
};

[[nodiscard]] static inline std::string toString(HardwareAuthenticatorType val) {
  switch(val) {
  case HardwareAuthenticatorType::NONE:
    return "NONE";
  case HardwareAuthenticatorType::PASSWORD:
    return "PASSWORD";
  case HardwareAuthenticatorType::FINGERPRINT:
    return "FINGERPRINT";
  case HardwareAuthenticatorType::ANY:
    return "ANY";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::security::keymint::HardwareAuthenticatorType, 4> enum_values<aidl::android::hardware::security::keymint::HardwareAuthenticatorType> = {
  aidl::android::hardware::security::keymint::HardwareAuthenticatorType::NONE,
  aidl::android::hardware::security::keymint::HardwareAuthenticatorType::PASSWORD,
  aidl::android::hardware::security::keymint::HardwareAuthenticatorType::FINGERPRINT,
  aidl::android::hardware::security::keymint::HardwareAuthenticatorType::ANY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
