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
enum class MboTransitionReasonCode : int8_t {
  UNSPECIFIED = 0,
  EXCESSIVE_FRAME_LOSS = 1,
  EXCESSIVE_TRAFFIC_DELAY = 2,
  INSUFFICIENT_BANDWIDTH = 3,
  LOAD_BALANCING = 4,
  LOW_RSSI = 5,
  RX_EXCESSIVE_RETRIES = 6,
  HIGH_INTERFERENCE = 7,
  GRAY_ZONE = 8,
  TRANSITION_TO_PREMIUM_AP = 9,
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
[[nodiscard]] static inline std::string toString(MboTransitionReasonCode val) {
  switch(val) {
  case MboTransitionReasonCode::UNSPECIFIED:
    return "UNSPECIFIED";
  case MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS:
    return "EXCESSIVE_FRAME_LOSS";
  case MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY:
    return "EXCESSIVE_TRAFFIC_DELAY";
  case MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH:
    return "INSUFFICIENT_BANDWIDTH";
  case MboTransitionReasonCode::LOAD_BALANCING:
    return "LOAD_BALANCING";
  case MboTransitionReasonCode::LOW_RSSI:
    return "LOW_RSSI";
  case MboTransitionReasonCode::RX_EXCESSIVE_RETRIES:
    return "RX_EXCESSIVE_RETRIES";
  case MboTransitionReasonCode::HIGH_INTERFERENCE:
    return "HIGH_INTERFERENCE";
  case MboTransitionReasonCode::GRAY_ZONE:
    return "GRAY_ZONE";
  case MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP:
    return "TRANSITION_TO_PREMIUM_AP";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode, 10> enum_values<aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode> = {
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::UNSPECIFIED,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::LOAD_BALANCING,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::LOW_RSSI,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::HIGH_INTERFERENCE,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::GRAY_ZONE,
  aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
