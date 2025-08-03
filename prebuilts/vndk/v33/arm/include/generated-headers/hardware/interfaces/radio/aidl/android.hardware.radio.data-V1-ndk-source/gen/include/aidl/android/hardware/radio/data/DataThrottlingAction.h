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
namespace radio {
namespace data {
enum class DataThrottlingAction : int8_t {
  NO_DATA_THROTTLING = 0,
  THROTTLE_SECONDARY_CARRIER = 1,
  THROTTLE_ANCHOR_CARRIER = 2,
  HOLD = 3,
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
[[nodiscard]] static inline std::string toString(DataThrottlingAction val) {
  switch(val) {
  case DataThrottlingAction::NO_DATA_THROTTLING:
    return "NO_DATA_THROTTLING";
  case DataThrottlingAction::THROTTLE_SECONDARY_CARRIER:
    return "THROTTLE_SECONDARY_CARRIER";
  case DataThrottlingAction::THROTTLE_ANCHOR_CARRIER:
    return "THROTTLE_ANCHOR_CARRIER";
  case DataThrottlingAction::HOLD:
    return "HOLD";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::data::DataThrottlingAction, 4> enum_values<aidl::android::hardware::radio::data::DataThrottlingAction> = {
  aidl::android::hardware::radio::data::DataThrottlingAction::NO_DATA_THROTTLING,
  aidl::android::hardware::radio::data::DataThrottlingAction::THROTTLE_SECONDARY_CARRIER,
  aidl::android::hardware::radio::data::DataThrottlingAction::THROTTLE_ANCHOR_CARRIER,
  aidl::android::hardware::radio::data::DataThrottlingAction::HOLD,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
