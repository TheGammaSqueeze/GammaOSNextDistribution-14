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
namespace oemlock {
enum class OemLockSecureStatus : int32_t {
  OK = 0,
  FAILED = 1,
  INVALID_SIGNATURE = 2,
};

[[nodiscard]] static inline std::string toString(OemLockSecureStatus val) {
  switch(val) {
  case OemLockSecureStatus::OK:
    return "OK";
  case OemLockSecureStatus::FAILED:
    return "FAILED";
  case OemLockSecureStatus::INVALID_SIGNATURE:
    return "INVALID_SIGNATURE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace oemlock
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::oemlock::OemLockSecureStatus, 3> enum_values<aidl::android::hardware::oemlock::OemLockSecureStatus> = {
  aidl::android::hardware::oemlock::OemLockSecureStatus::OK,
  aidl::android::hardware::oemlock::OemLockSecureStatus::FAILED,
  aidl::android::hardware::oemlock::OemLockSecureStatus::INVALID_SIGNATURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
