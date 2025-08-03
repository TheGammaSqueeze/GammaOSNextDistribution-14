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
namespace hostapd {
enum class ChannelBandwidth : int32_t {
  BANDWIDTH_INVALID = 0,
  BANDWIDTH_AUTO = 1,
  BANDWIDTH_20_NOHT = 2,
  BANDWIDTH_20 = 3,
  BANDWIDTH_40 = 4,
  BANDWIDTH_80 = 5,
  BANDWIDTH_80P80 = 6,
  BANDWIDTH_160 = 7,
  BANDWIDTH_320 = 8,
  BANDWIDTH_2160 = 9,
  BANDWIDTH_4320 = 10,
  BANDWIDTH_6480 = 11,
  BANDWIDTH_8640 = 12,
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
[[nodiscard]] static inline std::string toString(ChannelBandwidth val) {
  switch(val) {
  case ChannelBandwidth::BANDWIDTH_INVALID:
    return "BANDWIDTH_INVALID";
  case ChannelBandwidth::BANDWIDTH_AUTO:
    return "BANDWIDTH_AUTO";
  case ChannelBandwidth::BANDWIDTH_20_NOHT:
    return "BANDWIDTH_20_NOHT";
  case ChannelBandwidth::BANDWIDTH_20:
    return "BANDWIDTH_20";
  case ChannelBandwidth::BANDWIDTH_40:
    return "BANDWIDTH_40";
  case ChannelBandwidth::BANDWIDTH_80:
    return "BANDWIDTH_80";
  case ChannelBandwidth::BANDWIDTH_80P80:
    return "BANDWIDTH_80P80";
  case ChannelBandwidth::BANDWIDTH_160:
    return "BANDWIDTH_160";
  case ChannelBandwidth::BANDWIDTH_320:
    return "BANDWIDTH_320";
  case ChannelBandwidth::BANDWIDTH_2160:
    return "BANDWIDTH_2160";
  case ChannelBandwidth::BANDWIDTH_4320:
    return "BANDWIDTH_4320";
  case ChannelBandwidth::BANDWIDTH_6480:
    return "BANDWIDTH_6480";
  case ChannelBandwidth::BANDWIDTH_8640:
    return "BANDWIDTH_8640";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::ChannelBandwidth, 13> enum_values<aidl::android::hardware::wifi::hostapd::ChannelBandwidth> = {
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_INVALID,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_AUTO,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_20_NOHT,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_20,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_40,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_80,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_80P80,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_160,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_320,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_2160,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_4320,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_6480,
  aidl::android::hardware::wifi::hostapd::ChannelBandwidth::BANDWIDTH_8640,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
