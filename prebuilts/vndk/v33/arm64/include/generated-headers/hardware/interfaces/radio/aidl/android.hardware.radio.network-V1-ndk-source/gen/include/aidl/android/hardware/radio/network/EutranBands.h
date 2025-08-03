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
enum class EutranBands : int32_t {
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
  BAND_17 = 17,
  BAND_18 = 18,
  BAND_19 = 19,
  BAND_20 = 20,
  BAND_21 = 21,
  BAND_22 = 22,
  BAND_23 = 23,
  BAND_24 = 24,
  BAND_25 = 25,
  BAND_26 = 26,
  BAND_27 = 27,
  BAND_28 = 28,
  BAND_30 = 30,
  BAND_31 = 31,
  BAND_33 = 33,
  BAND_34 = 34,
  BAND_35 = 35,
  BAND_36 = 36,
  BAND_37 = 37,
  BAND_38 = 38,
  BAND_39 = 39,
  BAND_40 = 40,
  BAND_41 = 41,
  BAND_42 = 42,
  BAND_43 = 43,
  BAND_44 = 44,
  BAND_45 = 45,
  BAND_46 = 46,
  BAND_47 = 47,
  BAND_48 = 48,
  BAND_65 = 65,
  BAND_66 = 66,
  BAND_68 = 68,
  BAND_70 = 70,
  BAND_49 = 49,
  BAND_50 = 50,
  BAND_51 = 51,
  BAND_52 = 52,
  BAND_53 = 53,
  BAND_71 = 71,
  BAND_72 = 72,
  BAND_73 = 73,
  BAND_74 = 74,
  BAND_85 = 85,
  BAND_87 = 87,
  BAND_88 = 88,
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
[[nodiscard]] static inline std::string toString(EutranBands val) {
  switch(val) {
  case EutranBands::BAND_1:
    return "BAND_1";
  case EutranBands::BAND_2:
    return "BAND_2";
  case EutranBands::BAND_3:
    return "BAND_3";
  case EutranBands::BAND_4:
    return "BAND_4";
  case EutranBands::BAND_5:
    return "BAND_5";
  case EutranBands::BAND_6:
    return "BAND_6";
  case EutranBands::BAND_7:
    return "BAND_7";
  case EutranBands::BAND_8:
    return "BAND_8";
  case EutranBands::BAND_9:
    return "BAND_9";
  case EutranBands::BAND_10:
    return "BAND_10";
  case EutranBands::BAND_11:
    return "BAND_11";
  case EutranBands::BAND_12:
    return "BAND_12";
  case EutranBands::BAND_13:
    return "BAND_13";
  case EutranBands::BAND_14:
    return "BAND_14";
  case EutranBands::BAND_17:
    return "BAND_17";
  case EutranBands::BAND_18:
    return "BAND_18";
  case EutranBands::BAND_19:
    return "BAND_19";
  case EutranBands::BAND_20:
    return "BAND_20";
  case EutranBands::BAND_21:
    return "BAND_21";
  case EutranBands::BAND_22:
    return "BAND_22";
  case EutranBands::BAND_23:
    return "BAND_23";
  case EutranBands::BAND_24:
    return "BAND_24";
  case EutranBands::BAND_25:
    return "BAND_25";
  case EutranBands::BAND_26:
    return "BAND_26";
  case EutranBands::BAND_27:
    return "BAND_27";
  case EutranBands::BAND_28:
    return "BAND_28";
  case EutranBands::BAND_30:
    return "BAND_30";
  case EutranBands::BAND_31:
    return "BAND_31";
  case EutranBands::BAND_33:
    return "BAND_33";
  case EutranBands::BAND_34:
    return "BAND_34";
  case EutranBands::BAND_35:
    return "BAND_35";
  case EutranBands::BAND_36:
    return "BAND_36";
  case EutranBands::BAND_37:
    return "BAND_37";
  case EutranBands::BAND_38:
    return "BAND_38";
  case EutranBands::BAND_39:
    return "BAND_39";
  case EutranBands::BAND_40:
    return "BAND_40";
  case EutranBands::BAND_41:
    return "BAND_41";
  case EutranBands::BAND_42:
    return "BAND_42";
  case EutranBands::BAND_43:
    return "BAND_43";
  case EutranBands::BAND_44:
    return "BAND_44";
  case EutranBands::BAND_45:
    return "BAND_45";
  case EutranBands::BAND_46:
    return "BAND_46";
  case EutranBands::BAND_47:
    return "BAND_47";
  case EutranBands::BAND_48:
    return "BAND_48";
  case EutranBands::BAND_65:
    return "BAND_65";
  case EutranBands::BAND_66:
    return "BAND_66";
  case EutranBands::BAND_68:
    return "BAND_68";
  case EutranBands::BAND_70:
    return "BAND_70";
  case EutranBands::BAND_49:
    return "BAND_49";
  case EutranBands::BAND_50:
    return "BAND_50";
  case EutranBands::BAND_51:
    return "BAND_51";
  case EutranBands::BAND_52:
    return "BAND_52";
  case EutranBands::BAND_53:
    return "BAND_53";
  case EutranBands::BAND_71:
    return "BAND_71";
  case EutranBands::BAND_72:
    return "BAND_72";
  case EutranBands::BAND_73:
    return "BAND_73";
  case EutranBands::BAND_74:
    return "BAND_74";
  case EutranBands::BAND_85:
    return "BAND_85";
  case EutranBands::BAND_87:
    return "BAND_87";
  case EutranBands::BAND_88:
    return "BAND_88";
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
constexpr inline std::array<aidl::android::hardware::radio::network::EutranBands, 60> enum_values<aidl::android::hardware::radio::network::EutranBands> = {
  aidl::android::hardware::radio::network::EutranBands::BAND_1,
  aidl::android::hardware::radio::network::EutranBands::BAND_2,
  aidl::android::hardware::radio::network::EutranBands::BAND_3,
  aidl::android::hardware::radio::network::EutranBands::BAND_4,
  aidl::android::hardware::radio::network::EutranBands::BAND_5,
  aidl::android::hardware::radio::network::EutranBands::BAND_6,
  aidl::android::hardware::radio::network::EutranBands::BAND_7,
  aidl::android::hardware::radio::network::EutranBands::BAND_8,
  aidl::android::hardware::radio::network::EutranBands::BAND_9,
  aidl::android::hardware::radio::network::EutranBands::BAND_10,
  aidl::android::hardware::radio::network::EutranBands::BAND_11,
  aidl::android::hardware::radio::network::EutranBands::BAND_12,
  aidl::android::hardware::radio::network::EutranBands::BAND_13,
  aidl::android::hardware::radio::network::EutranBands::BAND_14,
  aidl::android::hardware::radio::network::EutranBands::BAND_17,
  aidl::android::hardware::radio::network::EutranBands::BAND_18,
  aidl::android::hardware::radio::network::EutranBands::BAND_19,
  aidl::android::hardware::radio::network::EutranBands::BAND_20,
  aidl::android::hardware::radio::network::EutranBands::BAND_21,
  aidl::android::hardware::radio::network::EutranBands::BAND_22,
  aidl::android::hardware::radio::network::EutranBands::BAND_23,
  aidl::android::hardware::radio::network::EutranBands::BAND_24,
  aidl::android::hardware::radio::network::EutranBands::BAND_25,
  aidl::android::hardware::radio::network::EutranBands::BAND_26,
  aidl::android::hardware::radio::network::EutranBands::BAND_27,
  aidl::android::hardware::radio::network::EutranBands::BAND_28,
  aidl::android::hardware::radio::network::EutranBands::BAND_30,
  aidl::android::hardware::radio::network::EutranBands::BAND_31,
  aidl::android::hardware::radio::network::EutranBands::BAND_33,
  aidl::android::hardware::radio::network::EutranBands::BAND_34,
  aidl::android::hardware::radio::network::EutranBands::BAND_35,
  aidl::android::hardware::radio::network::EutranBands::BAND_36,
  aidl::android::hardware::radio::network::EutranBands::BAND_37,
  aidl::android::hardware::radio::network::EutranBands::BAND_38,
  aidl::android::hardware::radio::network::EutranBands::BAND_39,
  aidl::android::hardware::radio::network::EutranBands::BAND_40,
  aidl::android::hardware::radio::network::EutranBands::BAND_41,
  aidl::android::hardware::radio::network::EutranBands::BAND_42,
  aidl::android::hardware::radio::network::EutranBands::BAND_43,
  aidl::android::hardware::radio::network::EutranBands::BAND_44,
  aidl::android::hardware::radio::network::EutranBands::BAND_45,
  aidl::android::hardware::radio::network::EutranBands::BAND_46,
  aidl::android::hardware::radio::network::EutranBands::BAND_47,
  aidl::android::hardware::radio::network::EutranBands::BAND_48,
  aidl::android::hardware::radio::network::EutranBands::BAND_65,
  aidl::android::hardware::radio::network::EutranBands::BAND_66,
  aidl::android::hardware::radio::network::EutranBands::BAND_68,
  aidl::android::hardware::radio::network::EutranBands::BAND_70,
  aidl::android::hardware::radio::network::EutranBands::BAND_49,
  aidl::android::hardware::radio::network::EutranBands::BAND_50,
  aidl::android::hardware::radio::network::EutranBands::BAND_51,
  aidl::android::hardware::radio::network::EutranBands::BAND_52,
  aidl::android::hardware::radio::network::EutranBands::BAND_53,
  aidl::android::hardware::radio::network::EutranBands::BAND_71,
  aidl::android::hardware::radio::network::EutranBands::BAND_72,
  aidl::android::hardware::radio::network::EutranBands::BAND_73,
  aidl::android::hardware::radio::network::EutranBands::BAND_74,
  aidl::android::hardware::radio::network::EutranBands::BAND_85,
  aidl::android::hardware::radio::network::EutranBands::BAND_87,
  aidl::android::hardware::radio::network::EutranBands::BAND_88,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
