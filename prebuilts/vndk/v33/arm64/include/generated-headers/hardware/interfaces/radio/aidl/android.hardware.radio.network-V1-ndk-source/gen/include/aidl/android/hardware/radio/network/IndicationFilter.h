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
namespace network {
enum class IndicationFilter : int32_t {
  NONE = 0,
  ALL = -1,
  SIGNAL_STRENGTH = 1,
  FULL_NETWORK_STATE = 2,
  DATA_CALL_DORMANCY_CHANGED = 4,
  LINK_CAPACITY_ESTIMATE = 8,
  PHYSICAL_CHANNEL_CONFIG = 16,
  REGISTRATION_FAILURE = 32,
  BARRING_INFO = 64,
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
[[nodiscard]] static inline std::string toString(IndicationFilter val) {
  switch(val) {
  case IndicationFilter::NONE:
    return "NONE";
  case IndicationFilter::ALL:
    return "ALL";
  case IndicationFilter::SIGNAL_STRENGTH:
    return "SIGNAL_STRENGTH";
  case IndicationFilter::FULL_NETWORK_STATE:
    return "FULL_NETWORK_STATE";
  case IndicationFilter::DATA_CALL_DORMANCY_CHANGED:
    return "DATA_CALL_DORMANCY_CHANGED";
  case IndicationFilter::LINK_CAPACITY_ESTIMATE:
    return "LINK_CAPACITY_ESTIMATE";
  case IndicationFilter::PHYSICAL_CHANNEL_CONFIG:
    return "PHYSICAL_CHANNEL_CONFIG";
  case IndicationFilter::REGISTRATION_FAILURE:
    return "REGISTRATION_FAILURE";
  case IndicationFilter::BARRING_INFO:
    return "BARRING_INFO";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::network::IndicationFilter, 9> enum_values<aidl::android::hardware::radio::network::IndicationFilter> = {
  aidl::android::hardware::radio::network::IndicationFilter::NONE,
  aidl::android::hardware::radio::network::IndicationFilter::ALL,
  aidl::android::hardware::radio::network::IndicationFilter::SIGNAL_STRENGTH,
  aidl::android::hardware::radio::network::IndicationFilter::FULL_NETWORK_STATE,
  aidl::android::hardware::radio::network::IndicationFilter::DATA_CALL_DORMANCY_CHANGED,
  aidl::android::hardware::radio::network::IndicationFilter::LINK_CAPACITY_ESTIMATE,
  aidl::android::hardware::radio::network::IndicationFilter::PHYSICAL_CHANNEL_CONFIG,
  aidl::android::hardware::radio::network::IndicationFilter::REGISTRATION_FAILURE,
  aidl::android::hardware::radio::network::IndicationFilter::BARRING_INFO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
