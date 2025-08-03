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
enum class KeyFormat : int32_t {
  X509 = 0,
  PKCS8 = 1,
  RAW = 3,
};

[[nodiscard]] static inline std::string toString(KeyFormat val) {
  switch(val) {
  case KeyFormat::X509:
    return "X509";
  case KeyFormat::PKCS8:
    return "PKCS8";
  case KeyFormat::RAW:
    return "RAW";
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
constexpr inline std::array<aidl::android::hardware::security::keymint::KeyFormat, 3> enum_values<aidl::android::hardware::security::keymint::KeyFormat> = {
  aidl::android::hardware::security::keymint::KeyFormat::X509,
  aidl::android::hardware::security::keymint::KeyFormat::PKCS8,
  aidl::android::hardware::security::keymint::KeyFormat::RAW,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
