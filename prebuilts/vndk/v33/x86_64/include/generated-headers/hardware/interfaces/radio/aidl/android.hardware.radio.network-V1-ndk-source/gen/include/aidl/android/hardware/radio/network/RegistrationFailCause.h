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
enum class RegistrationFailCause : int32_t {
  NONE = 0,
  IMSI_UNKNOWN_IN_HLR = 2,
  ILLEGAL_MS = 3,
  IMSI_UNKNOWN_IN_VLR = 4,
  IMEI_NOT_ACCEPTED = 5,
  ILLEGAL_ME = 6,
  GPRS_SERVICES_NOT_ALLOWED = 7,
  GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED = 8,
  MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK = 9,
  IMPLICITLY_DETACHED = 10,
  PLMN_NOT_ALLOWED = 11,
  LOCATION_AREA_NOT_ALLOWED = 12,
  ROAMING_NOT_ALLOWED = 13,
  GPRS_SERVICES_NOT_ALLOWED_IN_PLMN = 14,
  NO_SUITABLE_CELLS = 15,
  MSC_TEMPORARILY_NOT_REACHABLE = 15,
  NETWORK_FAILURE = 17,
  MAC_FAILURE = 20,
  SYNC_FAILURE = 21,
  CONGESTION = 22,
  GSM_AUTHENTICATION_UNACCEPTABLE = 23,
  NOT_AUTHORIZED_FOR_THIS_CSG = 25,
  SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA = 26,
  SERVICE_OPTION_NOT_SUPPORTED = 32,
  SERVICE_OPTION_NOT_SUBSCRIBED = 33,
  SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER = 34,
  CALL_CANNOT_BE_IDENTIFIED = 38,
  NO_PDP_CONTEXT_ACTIVATED = 40,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_1 = 48,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_2 = 49,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_3 = 50,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_4 = 51,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_5 = 52,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_6 = 53,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_7 = 54,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_8 = 55,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_9 = 56,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_10 = 57,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_11 = 58,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_12 = 59,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_13 = 60,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_14 = 61,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_15 = 62,
  RETRY_UPON_ENTRY_INTO_NEW_CELL_16 = 63,
  SEMANTICALLY_INCORRECT_MESSAGE = 95,
  INVALID_MANDATORY_INFORMATION = 96,
  MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED = 97,
  MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 98,
  INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED = 99,
  CONDITIONAL_IE_ERROR = 100,
  MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101,
  PROTOCOL_ERROR_UNSPECIFIED = 111,
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
[[nodiscard]] static inline std::string toString(RegistrationFailCause val) {
  switch(val) {
  case RegistrationFailCause::NONE:
    return "NONE";
  case RegistrationFailCause::IMSI_UNKNOWN_IN_HLR:
    return "IMSI_UNKNOWN_IN_HLR";
  case RegistrationFailCause::ILLEGAL_MS:
    return "ILLEGAL_MS";
  case RegistrationFailCause::IMSI_UNKNOWN_IN_VLR:
    return "IMSI_UNKNOWN_IN_VLR";
  case RegistrationFailCause::IMEI_NOT_ACCEPTED:
    return "IMEI_NOT_ACCEPTED";
  case RegistrationFailCause::ILLEGAL_ME:
    return "ILLEGAL_ME";
  case RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED:
    return "GPRS_SERVICES_NOT_ALLOWED";
  case RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED:
    return "GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED";
  case RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK:
    return "MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK";
  case RegistrationFailCause::IMPLICITLY_DETACHED:
    return "IMPLICITLY_DETACHED";
  case RegistrationFailCause::PLMN_NOT_ALLOWED:
    return "PLMN_NOT_ALLOWED";
  case RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED:
    return "LOCATION_AREA_NOT_ALLOWED";
  case RegistrationFailCause::ROAMING_NOT_ALLOWED:
    return "ROAMING_NOT_ALLOWED";
  case RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN:
    return "GPRS_SERVICES_NOT_ALLOWED_IN_PLMN";
  case RegistrationFailCause::NO_SUITABLE_CELLS:
    return "NO_SUITABLE_CELLS";
  case RegistrationFailCause::NETWORK_FAILURE:
    return "NETWORK_FAILURE";
  case RegistrationFailCause::MAC_FAILURE:
    return "MAC_FAILURE";
  case RegistrationFailCause::SYNC_FAILURE:
    return "SYNC_FAILURE";
  case RegistrationFailCause::CONGESTION:
    return "CONGESTION";
  case RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE:
    return "GSM_AUTHENTICATION_UNACCEPTABLE";
  case RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG:
    return "NOT_AUTHORIZED_FOR_THIS_CSG";
  case RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA:
    return "SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA";
  case RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED:
    return "SERVICE_OPTION_NOT_SUPPORTED";
  case RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED:
    return "SERVICE_OPTION_NOT_SUBSCRIBED";
  case RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER:
    return "SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER";
  case RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED:
    return "CALL_CANNOT_BE_IDENTIFIED";
  case RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED:
    return "NO_PDP_CONTEXT_ACTIVATED";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_1";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_2";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_3";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_4";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_5";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_6";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_7";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_8";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_9";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_10";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_11";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_12";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_13";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_14";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_15";
  case RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16:
    return "RETRY_UPON_ENTRY_INTO_NEW_CELL_16";
  case RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE:
    return "SEMANTICALLY_INCORRECT_MESSAGE";
  case RegistrationFailCause::INVALID_MANDATORY_INFORMATION:
    return "INVALID_MANDATORY_INFORMATION";
  case RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED:
    return "MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED";
  case RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE:
    return "MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
  case RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED:
    return "INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED";
  case RegistrationFailCause::CONDITIONAL_IE_ERROR:
    return "CONDITIONAL_IE_ERROR";
  case RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE:
    return "MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
  case RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED:
    return "PROTOCOL_ERROR_UNSPECIFIED";
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
constexpr inline std::array<aidl::android::hardware::radio::network::RegistrationFailCause, 52> enum_values<aidl::android::hardware::radio::network::RegistrationFailCause> = {
  aidl::android::hardware::radio::network::RegistrationFailCause::NONE,
  aidl::android::hardware::radio::network::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR,
  aidl::android::hardware::radio::network::RegistrationFailCause::ILLEGAL_MS,
  aidl::android::hardware::radio::network::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR,
  aidl::android::hardware::radio::network::RegistrationFailCause::IMEI_NOT_ACCEPTED,
  aidl::android::hardware::radio::network::RegistrationFailCause::ILLEGAL_ME,
  aidl::android::hardware::radio::network::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED,
  aidl::android::hardware::radio::network::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED,
  aidl::android::hardware::radio::network::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK,
  aidl::android::hardware::radio::network::RegistrationFailCause::IMPLICITLY_DETACHED,
  aidl::android::hardware::radio::network::RegistrationFailCause::PLMN_NOT_ALLOWED,
  aidl::android::hardware::radio::network::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED,
  aidl::android::hardware::radio::network::RegistrationFailCause::ROAMING_NOT_ALLOWED,
  aidl::android::hardware::radio::network::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN,
  aidl::android::hardware::radio::network::RegistrationFailCause::NO_SUITABLE_CELLS,
  aidl::android::hardware::radio::network::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE,
  aidl::android::hardware::radio::network::RegistrationFailCause::NETWORK_FAILURE,
  aidl::android::hardware::radio::network::RegistrationFailCause::MAC_FAILURE,
  aidl::android::hardware::radio::network::RegistrationFailCause::SYNC_FAILURE,
  aidl::android::hardware::radio::network::RegistrationFailCause::CONGESTION,
  aidl::android::hardware::radio::network::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE,
  aidl::android::hardware::radio::network::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG,
  aidl::android::hardware::radio::network::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA,
  aidl::android::hardware::radio::network::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED,
  aidl::android::hardware::radio::network::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED,
  aidl::android::hardware::radio::network::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER,
  aidl::android::hardware::radio::network::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED,
  aidl::android::hardware::radio::network::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15,
  aidl::android::hardware::radio::network::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16,
  aidl::android::hardware::radio::network::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE,
  aidl::android::hardware::radio::network::RegistrationFailCause::INVALID_MANDATORY_INFORMATION,
  aidl::android::hardware::radio::network::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED,
  aidl::android::hardware::radio::network::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
  aidl::android::hardware::radio::network::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED,
  aidl::android::hardware::radio::network::RegistrationFailCause::CONDITIONAL_IE_ERROR,
  aidl::android::hardware::radio::network::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
  aidl::android::hardware::radio::network::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
