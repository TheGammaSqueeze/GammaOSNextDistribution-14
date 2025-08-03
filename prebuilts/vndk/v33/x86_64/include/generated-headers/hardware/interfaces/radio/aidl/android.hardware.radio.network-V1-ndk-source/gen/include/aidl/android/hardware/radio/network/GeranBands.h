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
enum class GeranBands : int32_t {
  BAND_T380 = 1,
  BAND_T410 = 2,
  BAND_450 = 3,
  BAND_480 = 4,
  BAND_710 = 5,
  BAND_750 = 6,
  BAND_T810 = 7,
  BAND_850 = 8,
  BAND_P900 = 9,
  BAND_E900 = 10,
  BAND_R900 = 11,
  BAND_DCS1800 = 12,
  BAND_PCS1900 = 13,
  BAND_ER900 = 14,
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
[[nodiscard]] static inline std::string toString(GeranBands val) {
  switch(val) {
  case GeranBands::BAND_T380:
    return "BAND_T380";
  case GeranBands::BAND_T410:
    return "BAND_T410";
  case GeranBands::BAND_450:
    return "BAND_450";
  case GeranBands::BAND_480:
    return "BAND_480";
  case GeranBands::BAND_710:
    return "BAND_710";
  case GeranBands::BAND_750:
    return "BAND_750";
  case GeranBands::BAND_T810:
    return "BAND_T810";
  case GeranBands::BAND_850:
    return "BAND_850";
  case GeranBands::BAND_P900:
    return "BAND_P900";
  case GeranBands::BAND_E900:
    return "BAND_E900";
  case GeranBands::BAND_R900:
    return "BAND_R900";
  case GeranBands::BAND_DCS1800:
    return "BAND_DCS1800";
  case GeranBands::BAND_PCS1900:
    return "BAND_PCS1900";
  case GeranBands::BAND_ER900:
    return "BAND_ER900";
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
constexpr inline std::array<aidl::android::hardware::radio::network::GeranBands, 14> enum_values<aidl::android::hardware::radio::network::GeranBands> = {
  aidl::android::hardware::radio::network::GeranBands::BAND_T380,
  aidl::android::hardware::radio::network::GeranBands::BAND_T410,
  aidl::android::hardware::radio::network::GeranBands::BAND_450,
  aidl::android::hardware::radio::network::GeranBands::BAND_480,
  aidl::android::hardware::radio::network::GeranBands::BAND_710,
  aidl::android::hardware::radio::network::GeranBands::BAND_750,
  aidl::android::hardware::radio::network::GeranBands::BAND_T810,
  aidl::android::hardware::radio::network::GeranBands::BAND_850,
  aidl::android::hardware::radio::network::GeranBands::BAND_P900,
  aidl::android::hardware::radio::network::GeranBands::BAND_E900,
  aidl::android::hardware::radio::network::GeranBands::BAND_R900,
  aidl::android::hardware::radio::network::GeranBands::BAND_DCS1800,
  aidl::android::hardware::radio::network::GeranBands::BAND_PCS1900,
  aidl::android::hardware::radio::network::GeranBands::BAND_ER900,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
