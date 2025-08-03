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
enum class BandMask : int32_t {
  BAND_2_GHZ = 1,
  BAND_5_GHZ = 2,
  BAND_6_GHZ = 4,
  BAND_60_GHZ = 8,
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
[[nodiscard]] static inline std::string toString(BandMask val) {
  switch(val) {
  case BandMask::BAND_2_GHZ:
    return "BAND_2_GHZ";
  case BandMask::BAND_5_GHZ:
    return "BAND_5_GHZ";
  case BandMask::BAND_6_GHZ:
    return "BAND_6_GHZ";
  case BandMask::BAND_60_GHZ:
    return "BAND_60_GHZ";
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
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::BandMask, 4> enum_values<aidl::android::hardware::wifi::hostapd::BandMask> = {
  aidl::android::hardware::wifi::hostapd::BandMask::BAND_2_GHZ,
  aidl::android::hardware::wifi::hostapd::BandMask::BAND_5_GHZ,
  aidl::android::hardware::wifi::hostapd::BandMask::BAND_6_GHZ,
  aidl::android::hardware::wifi::hostapd::BandMask::BAND_60_GHZ,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
