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
enum class UtranBands : int32_t {
  BAND_1 = 1,
  BAND_2 = 2,
  BAND_3 = 3,
  BAND_4 = 4,
  BAND_5 = 5,
  BAND_6 = 6,
  BAND_7 = 7,
  BAND_8 = 8,
  BAND_9 = 9,
  BAND_10 = 10,
  BAND_11 = 11,
  BAND_12 = 12,
  BAND_13 = 13,
  BAND_14 = 14,
  BAND_19 = 19,
  BAND_20 = 20,
  BAND_21 = 21,
  BAND_22 = 22,
  BAND_25 = 25,
  BAND_26 = 26,
  BAND_A = 101,
  BAND_B = 102,
  BAND_C = 103,
  BAND_D = 104,
  BAND_E = 105,
  BAND_F = 106,
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
[[nodiscard]] static inline std::string toString(UtranBands val) {
  switch(val) {
  case UtranBands::BAND_1:
    return "BAND_1";
  case UtranBands::BAND_2:
    return "BAND_2";
  case UtranBands::BAND_3:
    return "BAND_3";
  case UtranBands::BAND_4:
    return "BAND_4";
  case UtranBands::BAND_5:
    return "BAND_5";
  case UtranBands::BAND_6:
    return "BAND_6";
  case UtranBands::BAND_7:
    return "BAND_7";
  case UtranBands::BAND_8:
    return "BAND_8";
  case UtranBands::BAND_9:
    return "BAND_9";
  case UtranBands::BAND_10:
    return "BAND_10";
  case UtranBands::BAND_11:
    return "BAND_11";
  case UtranBands::BAND_12:
    return "BAND_12";
  case UtranBands::BAND_13:
    return "BAND_13";
  case UtranBands::BAND_14:
    return "BAND_14";
  case UtranBands::BAND_19:
    return "BAND_19";
  case UtranBands::BAND_20:
    return "BAND_20";
  case UtranBands::BAND_21:
    return "BAND_21";
  case UtranBands::BAND_22:
    return "BAND_22";
  case UtranBands::BAND_25:
    return "BAND_25";
  case UtranBands::BAND_26:
    return "BAND_26";
  case UtranBands::BAND_A:
    return "BAND_A";
  case UtranBands::BAND_B:
    return "BAND_B";
  case UtranBands::BAND_C:
    return "BAND_C";
  case UtranBands::BAND_D:
    return "BAND_D";
  case UtranBands::BAND_E:
    return "BAND_E";
  case UtranBands::BAND_F:
    return "BAND_F";
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
constexpr inline std::array<aidl::android::hardware::radio::network::UtranBands, 26> enum_values<aidl::android::hardware::radio::network::UtranBands> = {
  aidl::android::hardware::radio::network::UtranBands::BAND_1,
  aidl::android::hardware::radio::network::UtranBands::BAND_2,
  aidl::android::hardware::radio::network::UtranBands::BAND_3,
  aidl::android::hardware::radio::network::UtranBands::BAND_4,
  aidl::android::hardware::radio::network::UtranBands::BAND_5,
  aidl::android::hardware::radio::network::UtranBands::BAND_6,
  aidl::android::hardware::radio::network::UtranBands::BAND_7,
  aidl::android::hardware::radio::network::UtranBands::BAND_8,
  aidl::android::hardware::radio::network::UtranBands::BAND_9,
  aidl::android::hardware::radio::network::UtranBands::BAND_10,
  aidl::android::hardware::radio::network::UtranBands::BAND_11,
  aidl::android::hardware::radio::network::UtranBands::BAND_12,
  aidl::android::hardware::radio::network::UtranBands::BAND_13,
  aidl::android::hardware::radio::network::UtranBands::BAND_14,
  aidl::android::hardware::radio::network::UtranBands::BAND_19,
  aidl::android::hardware::radio::network::UtranBands::BAND_20,
  aidl::android::hardware::radio::network::UtranBands::BAND_21,
  aidl::android::hardware::radio::network::UtranBands::BAND_22,
  aidl::android::hardware::radio::network::UtranBands::BAND_25,
  aidl::android::hardware::radio::network::UtranBands::BAND_26,
  aidl::android::hardware::radio::network::UtranBands::BAND_A,
  aidl::android::hardware::radio::network::UtranBands::BAND_B,
  aidl::android::hardware::radio::network::UtranBands::BAND_C,
  aidl::android::hardware::radio::network::UtranBands::BAND_D,
  aidl::android::hardware::radio::network::UtranBands::BAND_E,
  aidl::android::hardware::radio::network::UtranBands::BAND_F,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
