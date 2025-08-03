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
enum class P2pStatusCode : int32_t {
  SUCCESS = 0,
  FAIL_INFO_CURRENTLY_UNAVAILABLE = 1,
  FAIL_INCOMPATIBLE_PARAMS = 2,
  FAIL_LIMIT_REACHED = 3,
  FAIL_INVALID_PARAMS = 4,
  FAIL_UNABLE_TO_ACCOMMODATE = 5,
  FAIL_PREV_PROTOCOL_ERROR = 6,
  FAIL_NO_COMMON_CHANNELS = 7,
  FAIL_UNKNOWN_GROUP = 8,
  FAIL_BOTH_GO_INTENT_15 = 9,
  FAIL_INCOMPATIBLE_PROV_METHOD = 10,
  FAIL_REJECTED_BY_USER = 11,
  SUCCESS_DEFERRED = 12,
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
[[nodiscard]] static inline std::string toString(P2pStatusCode val) {
  switch(val) {
  case P2pStatusCode::SUCCESS:
    return "SUCCESS";
  case P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE:
    return "FAIL_INFO_CURRENTLY_UNAVAILABLE";
  case P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS:
    return "FAIL_INCOMPATIBLE_PARAMS";
  case P2pStatusCode::FAIL_LIMIT_REACHED:
    return "FAIL_LIMIT_REACHED";
  case P2pStatusCode::FAIL_INVALID_PARAMS:
    return "FAIL_INVALID_PARAMS";
  case P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE:
    return "FAIL_UNABLE_TO_ACCOMMODATE";
  case P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR:
    return "FAIL_PREV_PROTOCOL_ERROR";
  case P2pStatusCode::FAIL_NO_COMMON_CHANNELS:
    return "FAIL_NO_COMMON_CHANNELS";
  case P2pStatusCode::FAIL_UNKNOWN_GROUP:
    return "FAIL_UNKNOWN_GROUP";
  case P2pStatusCode::FAIL_BOTH_GO_INTENT_15:
    return "FAIL_BOTH_GO_INTENT_15";
  case P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD:
    return "FAIL_INCOMPATIBLE_PROV_METHOD";
  case P2pStatusCode::FAIL_REJECTED_BY_USER:
    return "FAIL_REJECTED_BY_USER";
  case P2pStatusCode::SUCCESS_DEFERRED:
    return "SUCCESS_DEFERRED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::P2pStatusCode, 13> enum_values<aidl::android::hardware::wifi::supplicant::P2pStatusCode> = {
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::SUCCESS,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_LIMIT_REACHED,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_INVALID_PARAMS,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_NO_COMMON_CHANNELS,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_UNKNOWN_GROUP,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_BOTH_GO_INTENT_15,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::FAIL_REJECTED_BY_USER,
  aidl::android::hardware::wifi::supplicant::P2pStatusCode::SUCCESS_DEFERRED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
