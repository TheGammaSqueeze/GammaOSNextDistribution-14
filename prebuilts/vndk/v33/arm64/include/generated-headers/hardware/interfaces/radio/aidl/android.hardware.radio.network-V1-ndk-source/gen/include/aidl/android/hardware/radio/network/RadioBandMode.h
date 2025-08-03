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
enum class RadioBandMode : int32_t {
  BAND_MODE_UNSPECIFIED = 0,
  BAND_MODE_EURO = 1,
  BAND_MODE_USA = 2,
  BAND_MODE_JPN = 3,
  BAND_MODE_AUS = 4,
  BAND_MODE_AUS_2 = 5,
  BAND_MODE_CELL_800 = 6,
  BAND_MODE_PCS = 7,
  BAND_MODE_JTACS = 8,
  BAND_MODE_KOREA_PCS = 9,
  BAND_MODE_5_450M = 10,
  BAND_MODE_IMT2000 = 11,
  BAND_MODE_7_700M_2 = 12,
  BAND_MODE_8_1800M = 13,
  BAND_MODE_9_900M = 14,
  BAND_MODE_10_800M_2 = 15,
  BAND_MODE_EURO_PAMR_400M = 16,
  BAND_MODE_AWS = 17,
  BAND_MODE_USA_2500M = 18,
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
[[nodiscard]] static inline std::string toString(RadioBandMode val) {
  switch(val) {
  case RadioBandMode::BAND_MODE_UNSPECIFIED:
    return "BAND_MODE_UNSPECIFIED";
  case RadioBandMode::BAND_MODE_EURO:
    return "BAND_MODE_EURO";
  case RadioBandMode::BAND_MODE_USA:
    return "BAND_MODE_USA";
  case RadioBandMode::BAND_MODE_JPN:
    return "BAND_MODE_JPN";
  case RadioBandMode::BAND_MODE_AUS:
    return "BAND_MODE_AUS";
  case RadioBandMode::BAND_MODE_AUS_2:
    return "BAND_MODE_AUS_2";
  case RadioBandMode::BAND_MODE_CELL_800:
    return "BAND_MODE_CELL_800";
  case RadioBandMode::BAND_MODE_PCS:
    return "BAND_MODE_PCS";
  case RadioBandMode::BAND_MODE_JTACS:
    return "BAND_MODE_JTACS";
  case RadioBandMode::BAND_MODE_KOREA_PCS:
    return "BAND_MODE_KOREA_PCS";
  case RadioBandMode::BAND_MODE_5_450M:
    return "BAND_MODE_5_450M";
  case RadioBandMode::BAND_MODE_IMT2000:
    return "BAND_MODE_IMT2000";
  case RadioBandMode::BAND_MODE_7_700M_2:
    return "BAND_MODE_7_700M_2";
  case RadioBandMode::BAND_MODE_8_1800M:
    return "BAND_MODE_8_1800M";
  case RadioBandMode::BAND_MODE_9_900M:
    return "BAND_MODE_9_900M";
  case RadioBandMode::BAND_MODE_10_800M_2:
    return "BAND_MODE_10_800M_2";
  case RadioBandMode::BAND_MODE_EURO_PAMR_400M:
    return "BAND_MODE_EURO_PAMR_400M";
  case RadioBandMode::BAND_MODE_AWS:
    return "BAND_MODE_AWS";
  case RadioBandMode::BAND_MODE_USA_2500M:
    return "BAND_MODE_USA_2500M";
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
constexpr inline std::array<aidl::android::hardware::radio::network::RadioBandMode, 19> enum_values<aidl::android::hardware::radio::network::RadioBandMode> = {
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_UNSPECIFIED,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_EURO,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_USA,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_JPN,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_AUS,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_AUS_2,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_CELL_800,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_PCS,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_JTACS,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_KOREA_PCS,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_5_450M,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_IMT2000,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_7_700M_2,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_8_1800M,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_9_900M,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_10_800M_2,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_EURO_PAMR_400M,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_AWS,
  aidl::android::hardware::radio::network::RadioBandMode::BAND_MODE_USA_2500M,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
