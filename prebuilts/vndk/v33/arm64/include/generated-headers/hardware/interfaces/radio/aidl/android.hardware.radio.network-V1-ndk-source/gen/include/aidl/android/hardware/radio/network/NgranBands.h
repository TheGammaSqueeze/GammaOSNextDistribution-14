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
enum class NgranBands : int32_t {
  BAND_1 = 1,
  BAND_2 = 2,
  BAND_3 = 3,
  BAND_5 = 5,
  BAND_7 = 7,
  BAND_8 = 8,
  BAND_12 = 12,
  BAND_14 = 14,
  BAND_18 = 18,
  BAND_20 = 20,
  BAND_25 = 25,
  BAND_26 = 26,
  BAND_28 = 28,
  BAND_29 = 29,
  BAND_30 = 30,
  BAND_34 = 34,
  BAND_38 = 38,
  BAND_39 = 39,
  BAND_40 = 40,
  BAND_41 = 41,
  BAND_46 = 46,
  BAND_48 = 48,
  BAND_50 = 50,
  BAND_51 = 51,
  BAND_53 = 53,
  BAND_65 = 65,
  BAND_66 = 66,
  BAND_70 = 70,
  BAND_71 = 71,
  BAND_74 = 74,
  BAND_75 = 75,
  BAND_76 = 76,
  BAND_77 = 77,
  BAND_78 = 78,
  BAND_79 = 79,
  BAND_80 = 80,
  BAND_81 = 81,
  BAND_82 = 82,
  BAND_83 = 83,
  BAND_84 = 84,
  BAND_86 = 86,
  BAND_89 = 89,
  BAND_90 = 90,
  BAND_91 = 91,
  BAND_92 = 92,
  BAND_93 = 93,
  BAND_94 = 94,
  BAND_95 = 95,
  BAND_96 = 96,
  BAND_257 = 257,
  BAND_258 = 258,
  BAND_260 = 260,
  BAND_261 = 261,
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
[[nodiscard]] static inline std::string toString(NgranBands val) {
  switch(val) {
  case NgranBands::BAND_1:
    return "BAND_1";
  case NgranBands::BAND_2:
    return "BAND_2";
  case NgranBands::BAND_3:
    return "BAND_3";
  case NgranBands::BAND_5:
    return "BAND_5";
  case NgranBands::BAND_7:
    return "BAND_7";
  case NgranBands::BAND_8:
    return "BAND_8";
  case NgranBands::BAND_12:
    return "BAND_12";
  case NgranBands::BAND_14:
    return "BAND_14";
  case NgranBands::BAND_18:
    return "BAND_18";
  case NgranBands::BAND_20:
    return "BAND_20";
  case NgranBands::BAND_25:
    return "BAND_25";
  case NgranBands::BAND_26:
    return "BAND_26";
  case NgranBands::BAND_28:
    return "BAND_28";
  case NgranBands::BAND_29:
    return "BAND_29";
  case NgranBands::BAND_30:
    return "BAND_30";
  case NgranBands::BAND_34:
    return "BAND_34";
  case NgranBands::BAND_38:
    return "BAND_38";
  case NgranBands::BAND_39:
    return "BAND_39";
  case NgranBands::BAND_40:
    return "BAND_40";
  case NgranBands::BAND_41:
    return "BAND_41";
  case NgranBands::BAND_46:
    return "BAND_46";
  case NgranBands::BAND_48:
    return "BAND_48";
  case NgranBands::BAND_50:
    return "BAND_50";
  case NgranBands::BAND_51:
    return "BAND_51";
  case NgranBands::BAND_53:
    return "BAND_53";
  case NgranBands::BAND_65:
    return "BAND_65";
  case NgranBands::BAND_66:
    return "BAND_66";
  case NgranBands::BAND_70:
    return "BAND_70";
  case NgranBands::BAND_71:
    return "BAND_71";
  case NgranBands::BAND_74:
    return "BAND_74";
  case NgranBands::BAND_75:
    return "BAND_75";
  case NgranBands::BAND_76:
    return "BAND_76";
  case NgranBands::BAND_77:
    return "BAND_77";
  case NgranBands::BAND_78:
    return "BAND_78";
  case NgranBands::BAND_79:
    return "BAND_79";
  case NgranBands::BAND_80:
    return "BAND_80";
  case NgranBands::BAND_81:
    return "BAND_81";
  case NgranBands::BAND_82:
    return "BAND_82";
  case NgranBands::BAND_83:
    return "BAND_83";
  case NgranBands::BAND_84:
    return "BAND_84";
  case NgranBands::BAND_86:
    return "BAND_86";
  case NgranBands::BAND_89:
    return "BAND_89";
  case NgranBands::BAND_90:
    return "BAND_90";
  case NgranBands::BAND_91:
    return "BAND_91";
  case NgranBands::BAND_92:
    return "BAND_92";
  case NgranBands::BAND_93:
    return "BAND_93";
  case NgranBands::BAND_94:
    return "BAND_94";
  case NgranBands::BAND_95:
    return "BAND_95";
  case NgranBands::BAND_96:
    return "BAND_96";
  case NgranBands::BAND_257:
    return "BAND_257";
  case NgranBands::BAND_258:
    return "BAND_258";
  case NgranBands::BAND_260:
    return "BAND_260";
  case NgranBands::BAND_261:
    return "BAND_261";
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
constexpr inline std::array<aidl::android::hardware::radio::network::NgranBands, 53> enum_values<aidl::android::hardware::radio::network::NgranBands> = {
  aidl::android::hardware::radio::network::NgranBands::BAND_1,
  aidl::android::hardware::radio::network::NgranBands::BAND_2,
  aidl::android::hardware::radio::network::NgranBands::BAND_3,
  aidl::android::hardware::radio::network::NgranBands::BAND_5,
  aidl::android::hardware::radio::network::NgranBands::BAND_7,
  aidl::android::hardware::radio::network::NgranBands::BAND_8,
  aidl::android::hardware::radio::network::NgranBands::BAND_12,
  aidl::android::hardware::radio::network::NgranBands::BAND_14,
  aidl::android::hardware::radio::network::NgranBands::BAND_18,
  aidl::android::hardware::radio::network::NgranBands::BAND_20,
  aidl::android::hardware::radio::network::NgranBands::BAND_25,
  aidl::android::hardware::radio::network::NgranBands::BAND_26,
  aidl::android::hardware::radio::network::NgranBands::BAND_28,
  aidl::android::hardware::radio::network::NgranBands::BAND_29,
  aidl::android::hardware::radio::network::NgranBands::BAND_30,
  aidl::android::hardware::radio::network::NgranBands::BAND_34,
  aidl::android::hardware::radio::network::NgranBands::BAND_38,
  aidl::android::hardware::radio::network::NgranBands::BAND_39,
  aidl::android::hardware::radio::network::NgranBands::BAND_40,
  aidl::android::hardware::radio::network::NgranBands::BAND_41,
  aidl::android::hardware::radio::network::NgranBands::BAND_46,
  aidl::android::hardware::radio::network::NgranBands::BAND_48,
  aidl::android::hardware::radio::network::NgranBands::BAND_50,
  aidl::android::hardware::radio::network::NgranBands::BAND_51,
  aidl::android::hardware::radio::network::NgranBands::BAND_53,
  aidl::android::hardware::radio::network::NgranBands::BAND_65,
  aidl::android::hardware::radio::network::NgranBands::BAND_66,
  aidl::android::hardware::radio::network::NgranBands::BAND_70,
  aidl::android::hardware::radio::network::NgranBands::BAND_71,
  aidl::android::hardware::radio::network::NgranBands::BAND_74,
  aidl::android::hardware::radio::network::NgranBands::BAND_75,
  aidl::android::hardware::radio::network::NgranBands::BAND_76,
  aidl::android::hardware::radio::network::NgranBands::BAND_77,
  aidl::android::hardware::radio::network::NgranBands::BAND_78,
  aidl::android::hardware::radio::network::NgranBands::BAND_79,
  aidl::android::hardware::radio::network::NgranBands::BAND_80,
  aidl::android::hardware::radio::network::NgranBands::BAND_81,
  aidl::android::hardware::radio::network::NgranBands::BAND_82,
  aidl::android::hardware::radio::network::NgranBands::BAND_83,
  aidl::android::hardware::radio::network::NgranBands::BAND_84,
  aidl::android::hardware::radio::network::NgranBands::BAND_86,
  aidl::android::hardware::radio::network::NgranBands::BAND_89,
  aidl::android::hardware::radio::network::NgranBands::BAND_90,
  aidl::android::hardware::radio::network::NgranBands::BAND_91,
  aidl::android::hardware::radio::network::NgranBands::BAND_92,
  aidl::android::hardware::radio::network::NgranBands::BAND_93,
  aidl::android::hardware::radio::network::NgranBands::BAND_94,
  aidl::android::hardware::radio::network::NgranBands::BAND_95,
  aidl::android::hardware::radio::network::NgranBands::BAND_96,
  aidl::android::hardware::radio::network::NgranBands::BAND_257,
  aidl::android::hardware::radio::network::NgranBands::BAND_258,
  aidl::android::hardware::radio::network::NgranBands::BAND_260,
  aidl::android::hardware::radio::network::NgranBands::BAND_261,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
