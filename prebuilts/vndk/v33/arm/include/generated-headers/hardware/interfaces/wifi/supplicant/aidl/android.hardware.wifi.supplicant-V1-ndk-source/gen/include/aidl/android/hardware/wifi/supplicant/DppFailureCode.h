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
enum class DppFailureCode : int32_t {
  INVALID_URI = 0,
  AUTHENTICATION = 1,
  NOT_COMPATIBLE = 2,
  CONFIGURATION = 3,
  BUSY = 4,
  TIMEOUT = 5,
  FAILURE = 6,
  NOT_SUPPORTED = 7,
  CONFIGURATION_REJECTED = 8,
  CANNOT_FIND_NETWORK = 9,
  ENROLLEE_AUTHENTICATION = 10,
  URI_GENERATION = 11,
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
[[nodiscard]] static inline std::string toString(DppFailureCode val) {
  switch(val) {
  case DppFailureCode::INVALID_URI:
    return "INVALID_URI";
  case DppFailureCode::AUTHENTICATION:
    return "AUTHENTICATION";
  case DppFailureCode::NOT_COMPATIBLE:
    return "NOT_COMPATIBLE";
  case DppFailureCode::CONFIGURATION:
    return "CONFIGURATION";
  case DppFailureCode::BUSY:
    return "BUSY";
  case DppFailureCode::TIMEOUT:
    return "TIMEOUT";
  case DppFailureCode::FAILURE:
    return "FAILURE";
  case DppFailureCode::NOT_SUPPORTED:
    return "NOT_SUPPORTED";
  case DppFailureCode::CONFIGURATION_REJECTED:
    return "CONFIGURATION_REJECTED";
  case DppFailureCode::CANNOT_FIND_NETWORK:
    return "CANNOT_FIND_NETWORK";
  case DppFailureCode::ENROLLEE_AUTHENTICATION:
    return "ENROLLEE_AUTHENTICATION";
  case DppFailureCode::URI_GENERATION:
    return "URI_GENERATION";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppFailureCode, 12> enum_values<aidl::android::hardware::wifi::supplicant::DppFailureCode> = {
  aidl::android::hardware::wifi::supplicant::DppFailureCode::INVALID_URI,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::AUTHENTICATION,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::NOT_COMPATIBLE,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::CONFIGURATION,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::BUSY,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::TIMEOUT,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::FAILURE,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::CONFIGURATION_REJECTED,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::CANNOT_FIND_NETWORK,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::ENROLLEE_AUTHENTICATION,
  aidl::android::hardware::wifi::supplicant::DppFailureCode::URI_GENERATION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
