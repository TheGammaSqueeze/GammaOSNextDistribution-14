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
namespace modem {
enum class NvItem : int32_t {
  CDMA_MEID = 1,
  CDMA_MIN = 2,
  CDMA_MDN = 3,
  CDMA_ACCOLC = 4,
  DEVICE_MSL = 11,
  RTN_RECONDITIONED_STATUS = 12,
  RTN_ACTIVATION_DATE = 13,
  RTN_LIFE_TIMER = 14,
  RTN_LIFE_CALLS = 15,
  RTN_LIFE_DATA_TX = 16,
  RTN_LIFE_DATA_RX = 17,
  OMADM_HFA_LEVEL = 18,
  MIP_PROFILE_NAI = 31,
  MIP_PROFILE_HOME_ADDRESS = 32,
  MIP_PROFILE_AAA_AUTH = 33,
  MIP_PROFILE_HA_AUTH = 34,
  MIP_PROFILE_PRI_HA_ADDR = 35,
  MIP_PROFILE_SEC_HA_ADDR = 36,
  MIP_PROFILE_REV_TUN_PREF = 37,
  MIP_PROFILE_HA_SPI = 38,
  MIP_PROFILE_AAA_SPI = 39,
  MIP_PROFILE_MN_HA_SS = 40,
  MIP_PROFILE_MN_AAA_SS = 41,
  CDMA_PRL_VERSION = 51,
  CDMA_BC10 = 52,
  CDMA_BC14 = 53,
  CDMA_SO68 = 54,
  CDMA_SO73_COP0 = 55,
  CDMA_SO73_COP1TO7 = 56,
  CDMA_1X_ADVANCED_ENABLED = 57,
  CDMA_EHRPD_ENABLED = 58,
  CDMA_EHRPD_FORCED = 59,
  LTE_BAND_ENABLE_25 = 71,
  LTE_BAND_ENABLE_26 = 72,
  LTE_BAND_ENABLE_41 = 73,
  LTE_SCAN_PRIORITY_25 = 74,
  LTE_SCAN_PRIORITY_26 = 75,
  LTE_SCAN_PRIORITY_41 = 76,
  LTE_HIDDEN_BAND_PRIORITY_25 = 77,
  LTE_HIDDEN_BAND_PRIORITY_26 = 78,
  LTE_HIDDEN_BAND_PRIORITY_41 = 79,
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
[[nodiscard]] static inline std::string toString(NvItem val) {
  switch(val) {
  case NvItem::CDMA_MEID:
    return "CDMA_MEID";
  case NvItem::CDMA_MIN:
    return "CDMA_MIN";
  case NvItem::CDMA_MDN:
    return "CDMA_MDN";
  case NvItem::CDMA_ACCOLC:
    return "CDMA_ACCOLC";
  case NvItem::DEVICE_MSL:
    return "DEVICE_MSL";
  case NvItem::RTN_RECONDITIONED_STATUS:
    return "RTN_RECONDITIONED_STATUS";
  case NvItem::RTN_ACTIVATION_DATE:
    return "RTN_ACTIVATION_DATE";
  case NvItem::RTN_LIFE_TIMER:
    return "RTN_LIFE_TIMER";
  case NvItem::RTN_LIFE_CALLS:
    return "RTN_LIFE_CALLS";
  case NvItem::RTN_LIFE_DATA_TX:
    return "RTN_LIFE_DATA_TX";
  case NvItem::RTN_LIFE_DATA_RX:
    return "RTN_LIFE_DATA_RX";
  case NvItem::OMADM_HFA_LEVEL:
    return "OMADM_HFA_LEVEL";
  case NvItem::MIP_PROFILE_NAI:
    return "MIP_PROFILE_NAI";
  case NvItem::MIP_PROFILE_HOME_ADDRESS:
    return "MIP_PROFILE_HOME_ADDRESS";
  case NvItem::MIP_PROFILE_AAA_AUTH:
    return "MIP_PROFILE_AAA_AUTH";
  case NvItem::MIP_PROFILE_HA_AUTH:
    return "MIP_PROFILE_HA_AUTH";
  case NvItem::MIP_PROFILE_PRI_HA_ADDR:
    return "MIP_PROFILE_PRI_HA_ADDR";
  case NvItem::MIP_PROFILE_SEC_HA_ADDR:
    return "MIP_PROFILE_SEC_HA_ADDR";
  case NvItem::MIP_PROFILE_REV_TUN_PREF:
    return "MIP_PROFILE_REV_TUN_PREF";
  case NvItem::MIP_PROFILE_HA_SPI:
    return "MIP_PROFILE_HA_SPI";
  case NvItem::MIP_PROFILE_AAA_SPI:
    return "MIP_PROFILE_AAA_SPI";
  case NvItem::MIP_PROFILE_MN_HA_SS:
    return "MIP_PROFILE_MN_HA_SS";
  case NvItem::MIP_PROFILE_MN_AAA_SS:
    return "MIP_PROFILE_MN_AAA_SS";
  case NvItem::CDMA_PRL_VERSION:
    return "CDMA_PRL_VERSION";
  case NvItem::CDMA_BC10:
    return "CDMA_BC10";
  case NvItem::CDMA_BC14:
    return "CDMA_BC14";
  case NvItem::CDMA_SO68:
    return "CDMA_SO68";
  case NvItem::CDMA_SO73_COP0:
    return "CDMA_SO73_COP0";
  case NvItem::CDMA_SO73_COP1TO7:
    return "CDMA_SO73_COP1TO7";
  case NvItem::CDMA_1X_ADVANCED_ENABLED:
    return "CDMA_1X_ADVANCED_ENABLED";
  case NvItem::CDMA_EHRPD_ENABLED:
    return "CDMA_EHRPD_ENABLED";
  case NvItem::CDMA_EHRPD_FORCED:
    return "CDMA_EHRPD_FORCED";
  case NvItem::LTE_BAND_ENABLE_25:
    return "LTE_BAND_ENABLE_25";
  case NvItem::LTE_BAND_ENABLE_26:
    return "LTE_BAND_ENABLE_26";
  case NvItem::LTE_BAND_ENABLE_41:
    return "LTE_BAND_ENABLE_41";
  case NvItem::LTE_SCAN_PRIORITY_25:
    return "LTE_SCAN_PRIORITY_25";
  case NvItem::LTE_SCAN_PRIORITY_26:
    return "LTE_SCAN_PRIORITY_26";
  case NvItem::LTE_SCAN_PRIORITY_41:
    return "LTE_SCAN_PRIORITY_41";
  case NvItem::LTE_HIDDEN_BAND_PRIORITY_25:
    return "LTE_HIDDEN_BAND_PRIORITY_25";
  case NvItem::LTE_HIDDEN_BAND_PRIORITY_26:
    return "LTE_HIDDEN_BAND_PRIORITY_26";
  case NvItem::LTE_HIDDEN_BAND_PRIORITY_41:
    return "LTE_HIDDEN_BAND_PRIORITY_41";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::modem::NvItem, 41> enum_values<aidl::android::hardware::radio::modem::NvItem> = {
  aidl::android::hardware::radio::modem::NvItem::CDMA_MEID,
  aidl::android::hardware::radio::modem::NvItem::CDMA_MIN,
  aidl::android::hardware::radio::modem::NvItem::CDMA_MDN,
  aidl::android::hardware::radio::modem::NvItem::CDMA_ACCOLC,
  aidl::android::hardware::radio::modem::NvItem::DEVICE_MSL,
  aidl::android::hardware::radio::modem::NvItem::RTN_RECONDITIONED_STATUS,
  aidl::android::hardware::radio::modem::NvItem::RTN_ACTIVATION_DATE,
  aidl::android::hardware::radio::modem::NvItem::RTN_LIFE_TIMER,
  aidl::android::hardware::radio::modem::NvItem::RTN_LIFE_CALLS,
  aidl::android::hardware::radio::modem::NvItem::RTN_LIFE_DATA_TX,
  aidl::android::hardware::radio::modem::NvItem::RTN_LIFE_DATA_RX,
  aidl::android::hardware::radio::modem::NvItem::OMADM_HFA_LEVEL,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_NAI,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_HOME_ADDRESS,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_AAA_AUTH,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_HA_AUTH,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_PRI_HA_ADDR,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_SEC_HA_ADDR,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_REV_TUN_PREF,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_HA_SPI,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_AAA_SPI,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_MN_HA_SS,
  aidl::android::hardware::radio::modem::NvItem::MIP_PROFILE_MN_AAA_SS,
  aidl::android::hardware::radio::modem::NvItem::CDMA_PRL_VERSION,
  aidl::android::hardware::radio::modem::NvItem::CDMA_BC10,
  aidl::android::hardware::radio::modem::NvItem::CDMA_BC14,
  aidl::android::hardware::radio::modem::NvItem::CDMA_SO68,
  aidl::android::hardware::radio::modem::NvItem::CDMA_SO73_COP0,
  aidl::android::hardware::radio::modem::NvItem::CDMA_SO73_COP1TO7,
  aidl::android::hardware::radio::modem::NvItem::CDMA_1X_ADVANCED_ENABLED,
  aidl::android::hardware::radio::modem::NvItem::CDMA_EHRPD_ENABLED,
  aidl::android::hardware::radio::modem::NvItem::CDMA_EHRPD_FORCED,
  aidl::android::hardware::radio::modem::NvItem::LTE_BAND_ENABLE_25,
  aidl::android::hardware::radio::modem::NvItem::LTE_BAND_ENABLE_26,
  aidl::android::hardware::radio::modem::NvItem::LTE_BAND_ENABLE_41,
  aidl::android::hardware::radio::modem::NvItem::LTE_SCAN_PRIORITY_25,
  aidl::android::hardware::radio::modem::NvItem::LTE_SCAN_PRIORITY_26,
  aidl::android::hardware::radio::modem::NvItem::LTE_SCAN_PRIORITY_41,
  aidl::android::hardware::radio::modem::NvItem::LTE_HIDDEN_BAND_PRIORITY_25,
  aidl::android::hardware::radio::modem::NvItem::LTE_HIDDEN_BAND_PRIORITY_26,
  aidl::android::hardware::radio::modem::NvItem::LTE_HIDDEN_BAND_PRIORITY_41,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
