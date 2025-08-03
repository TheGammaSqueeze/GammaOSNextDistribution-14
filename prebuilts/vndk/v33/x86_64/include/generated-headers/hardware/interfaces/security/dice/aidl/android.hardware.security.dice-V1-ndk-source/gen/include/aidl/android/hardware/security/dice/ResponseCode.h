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
namespace security {
namespace dice {
enum class ResponseCode : int32_t {
  PERMISSION_DENIED = 1,
  SYSTEM_ERROR = 2,
  NOT_IMPLEMENTED = 3,
  DEMOTION_FAILED = 4,
};

}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
[[nodiscard]] static inline std::string toString(ResponseCode val) {
  switch(val) {
  case ResponseCode::PERMISSION_DENIED:
    return "PERMISSION_DENIED";
  case ResponseCode::SYSTEM_ERROR:
    return "SYSTEM_ERROR";
  case ResponseCode::NOT_IMPLEMENTED:
    return "NOT_IMPLEMENTED";
  case ResponseCode::DEMOTION_FAILED:
    return "DEMOTION_FAILED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::security::dice::ResponseCode, 4> enum_values<aidl::android::hardware::security::dice::ResponseCode> = {
  aidl::android::hardware::security::dice::ResponseCode::PERMISSION_DENIED,
  aidl::android::hardware::security::dice::ResponseCode::SYSTEM_ERROR,
  aidl::android::hardware::security::dice::ResponseCode::NOT_IMPLEMENTED,
  aidl::android::hardware::security::dice::ResponseCode::DEMOTION_FAILED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
