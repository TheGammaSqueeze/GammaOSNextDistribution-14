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
enum class KeyOrigin : int32_t {
  GENERATED = 0,
  DERIVED = 1,
  IMPORTED = 2,
  RESERVED = 3,
  SECURELY_IMPORTED = 4,
};

[[nodiscard]] static inline std::string toString(KeyOrigin val) {
  switch(val) {
  case KeyOrigin::GENERATED:
    return "GENERATED";
  case KeyOrigin::DERIVED:
    return "DERIVED";
  case KeyOrigin::IMPORTED:
    return "IMPORTED";
  case KeyOrigin::RESERVED:
    return "RESERVED";
  case KeyOrigin::SECURELY_IMPORTED:
    return "SECURELY_IMPORTED";
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
constexpr inline std::array<aidl::android::hardware::security::keymint::KeyOrigin, 5> enum_values<aidl::android::hardware::security::keymint::KeyOrigin> = {
  aidl::android::hardware::security::keymint::KeyOrigin::GENERATED,
  aidl::android::hardware::security::keymint::KeyOrigin::DERIVED,
  aidl::android::hardware::security::keymint::KeyOrigin::IMPORTED,
  aidl::android::hardware::security::keymint::KeyOrigin::RESERVED,
  aidl::android::hardware::security::keymint::KeyOrigin::SECURELY_IMPORTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
