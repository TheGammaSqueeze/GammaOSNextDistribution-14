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
namespace keymaster {
enum class SecurityLevel : int32_t {
  SOFTWARE = 0,
  TRUSTED_ENVIRONMENT = 1,
  STRONGBOX = 2,
};

static inline std::string toString(SecurityLevel val) {
  switch(val) {
  case SecurityLevel::SOFTWARE:
    return "SOFTWARE";
  case SecurityLevel::TRUSTED_ENVIRONMENT:
    return "TRUSTED_ENVIRONMENT";
  case SecurityLevel::STRONGBOX:
    return "STRONGBOX";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::keymaster::SecurityLevel, 3> enum_values<aidl::android::hardware::keymaster::SecurityLevel> = {
  aidl::android::hardware::keymaster::SecurityLevel::SOFTWARE,
  aidl::android::hardware::keymaster::SecurityLevel::TRUSTED_ENVIRONMENT,
  aidl::android::hardware::keymaster::SecurityLevel::STRONGBOX,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
