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
namespace voice {
enum class LastCallFailCause : int32_t {
  UNOBTAINABLE_NUMBER = 1,
  NO_ROUTE_TO_DESTINATION = 3,
  CHANNEL_UNACCEPTABLE = 6,
  OPERATOR_DETERMINED_BARRING = 8,
  NORMAL = 16,
  BUSY = 17,
  NO_USER_RESPONDING = 18,
  NO_ANSWER_FROM_USER = 19,
  CALL_REJECTED = 21,
  NUMBER_CHANGED = 22,
  PREEMPTION = 25,
  DESTINATION_OUT_OF_ORDER = 27,
  INVALID_NUMBER_FORMAT = 28,
  FACILITY_REJECTED = 29,
  RESP_TO_STATUS_ENQUIRY = 30,
  NORMAL_UNSPECIFIED = 31,
  CONGESTION = 34,
  NETWORK_OUT_OF_ORDER = 38,
  TEMPORARY_FAILURE = 41,
  SWITCHING_EQUIPMENT_CONGESTION = 42,
  ACCESS_INFORMATION_DISCARDED = 43,
  REQUESTED_CIRCUIT_OR_CHANNEL_NOT_AVAILABLE = 44,
  RESOURCES_UNAVAILABLE_OR_UNSPECIFIED = 47,
  QOS_UNAVAILABLE = 49,
  REQUESTED_FACILITY_NOT_SUBSCRIBED = 50,
  INCOMING_CALLS_BARRED_WITHIN_CUG = 55,
  BEARER_CAPABILITY_NOT_AUTHORIZED = 57,
  BEARER_CAPABILITY_UNAVAILABLE = 58,
  SERVICE_OPTION_NOT_AVAILABLE = 63,
  BEARER_SERVICE_NOT_IMPLEMENTED = 65,
  ACM_LIMIT_EXCEEDED = 68,
  REQUESTED_FACILITY_NOT_IMPLEMENTED = 69,
  ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE = 70,
  SERVICE_OR_OPTION_NOT_IMPLEMENTED = 79,
  INVALID_TRANSACTION_IDENTIFIER = 81,
  USER_NOT_MEMBER_OF_CUG = 87,
  INCOMPATIBLE_DESTINATION = 88,
  INVALID_TRANSIT_NW_SELECTION = 91,
  SEMANTICALLY_INCORRECT_MESSAGE = 95,
  INVALID_MANDATORY_INFORMATION = 96,
  MESSAGE_TYPE_NON_IMPLEMENTED = 97,
  MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 98,
  INFORMATION_ELEMENT_NON_EXISTENT = 99,
  CONDITIONAL_IE_ERROR = 100,
  MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101,
  RECOVERY_ON_TIMER_EXPIRED = 102,
  PROTOCOL_ERROR_UNSPECIFIED = 111,
  INTERWORKING_UNSPECIFIED = 127,
  CALL_BARRED = 240,
  FDN_BLOCKED = 241,
  IMSI_UNKNOWN_IN_VLR = 242,
  IMEI_NOT_ACCEPTED = 243,
  DIAL_MODIFIED_TO_USSD = 244,
  DIAL_MODIFIED_TO_SS = 245,
  DIAL_MODIFIED_TO_DIAL = 246,
  RADIO_OFF = 247,
  OUT_OF_SERVICE = 248,
  NO_VALID_SIM = 249,
  RADIO_INTERNAL_ERROR = 250,
  NETWORK_RESP_TIMEOUT = 251,
  NETWORK_REJECT = 252,
  RADIO_ACCESS_FAILURE = 253,
  RADIO_LINK_FAILURE = 254,
  RADIO_LINK_LOST = 255,
  RADIO_UPLINK_FAILURE = 256,
  RADIO_SETUP_FAILURE = 257,
  RADIO_RELEASE_NORMAL = 258,
  RADIO_RELEASE_ABNORMAL = 259,
  ACCESS_CLASS_BLOCKED = 260,
  NETWORK_DETACH = 261,
  CDMA_LOCKED_UNTIL_POWER_CYCLE = 1000,
  CDMA_DROP = 1001,
  CDMA_INTERCEPT = 1002,
  CDMA_REORDER = 1003,
  CDMA_SO_REJECT = 1004,
  CDMA_RETRY_ORDER = 1005,
  CDMA_ACCESS_FAILURE = 1006,
  CDMA_PREEMPTED = 1007,
  CDMA_NOT_EMERGENCY = 1008,
  CDMA_ACCESS_BLOCKED = 1009,
  OEM_CAUSE_1 = 61441,
  OEM_CAUSE_2 = 61442,
  OEM_CAUSE_3 = 61443,
  OEM_CAUSE_4 = 61444,
  OEM_CAUSE_5 = 61445,
  OEM_CAUSE_6 = 61446,
  OEM_CAUSE_7 = 61447,
  OEM_CAUSE_8 = 61448,
  OEM_CAUSE_9 = 61449,
  OEM_CAUSE_10 = 61450,
  OEM_CAUSE_11 = 61451,
  OEM_CAUSE_12 = 61452,
  OEM_CAUSE_13 = 61453,
  OEM_CAUSE_14 = 61454,
  OEM_CAUSE_15 = 61455,
  ERROR_UNSPECIFIED = 65535,
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
[[nodiscard]] static inline std::string toString(LastCallFailCause val) {
  switch(val) {
  case LastCallFailCause::UNOBTAINABLE_NUMBER:
    return "UNOBTAINABLE_NUMBER";
  case LastCallFailCause::NO_ROUTE_TO_DESTINATION:
    return "NO_ROUTE_TO_DESTINATION";
  case LastCallFailCause::CHANNEL_UNACCEPTABLE:
    return "CHANNEL_UNACCEPTABLE";
  case LastCallFailCause::OPERATOR_DETERMINED_BARRING:
    return "OPERATOR_DETERMINED_BARRING";
  case LastCallFailCause::NORMAL:
    return "NORMAL";
  case LastCallFailCause::BUSY:
    return "BUSY";
  case LastCallFailCause::NO_USER_RESPONDING:
    return "NO_USER_RESPONDING";
  case LastCallFailCause::NO_ANSWER_FROM_USER:
    return "NO_ANSWER_FROM_USER";
  case LastCallFailCause::CALL_REJECTED:
    return "CALL_REJECTED";
  case LastCallFailCause::NUMBER_CHANGED:
    return "NUMBER_CHANGED";
  case LastCallFailCause::PREEMPTION:
    return "PREEMPTION";
  case LastCallFailCause::DESTINATION_OUT_OF_ORDER:
    return "DESTINATION_OUT_OF_ORDER";
  case LastCallFailCause::INVALID_NUMBER_FORMAT:
    return "INVALID_NUMBER_FORMAT";
  case LastCallFailCause::FACILITY_REJECTED:
    return "FACILITY_REJECTED";
  case LastCallFailCause::RESP_TO_STATUS_ENQUIRY:
    return "RESP_TO_STATUS_ENQUIRY";
  case LastCallFailCause::NORMAL_UNSPECIFIED:
    return "NORMAL_UNSPECIFIED";
  case LastCallFailCause::CONGESTION:
    return "CONGESTION";
  case LastCallFailCause::NETWORK_OUT_OF_ORDER:
    return "NETWORK_OUT_OF_ORDER";
  case LastCallFailCause::TEMPORARY_FAILURE:
    return "TEMPORARY_FAILURE";
  case LastCallFailCause::SWITCHING_EQUIPMENT_CONGESTION:
    return "SWITCHING_EQUIPMENT_CONGESTION";
  case LastCallFailCause::ACCESS_INFORMATION_DISCARDED:
    return "ACCESS_INFORMATION_DISCARDED";
  case LastCallFailCause::REQUESTED_CIRCUIT_OR_CHANNEL_NOT_AVAILABLE:
    return "REQUESTED_CIRCUIT_OR_CHANNEL_NOT_AVAILABLE";
  case LastCallFailCause::RESOURCES_UNAVAILABLE_OR_UNSPECIFIED:
    return "RESOURCES_UNAVAILABLE_OR_UNSPECIFIED";
  case LastCallFailCause::QOS_UNAVAILABLE:
    return "QOS_UNAVAILABLE";
  case LastCallFailCause::REQUESTED_FACILITY_NOT_SUBSCRIBED:
    return "REQUESTED_FACILITY_NOT_SUBSCRIBED";
  case LastCallFailCause::INCOMING_CALLS_BARRED_WITHIN_CUG:
    return "INCOMING_CALLS_BARRED_WITHIN_CUG";
  case LastCallFailCause::BEARER_CAPABILITY_NOT_AUTHORIZED:
    return "BEARER_CAPABILITY_NOT_AUTHORIZED";
  case LastCallFailCause::BEARER_CAPABILITY_UNAVAILABLE:
    return "BEARER_CAPABILITY_UNAVAILABLE";
  case LastCallFailCause::SERVICE_OPTION_NOT_AVAILABLE:
    return "SERVICE_OPTION_NOT_AVAILABLE";
  case LastCallFailCause::BEARER_SERVICE_NOT_IMPLEMENTED:
    return "BEARER_SERVICE_NOT_IMPLEMENTED";
  case LastCallFailCause::ACM_LIMIT_EXCEEDED:
    return "ACM_LIMIT_EXCEEDED";
  case LastCallFailCause::REQUESTED_FACILITY_NOT_IMPLEMENTED:
    return "REQUESTED_FACILITY_NOT_IMPLEMENTED";
  case LastCallFailCause::ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE:
    return "ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE";
  case LastCallFailCause::SERVICE_OR_OPTION_NOT_IMPLEMENTED:
    return "SERVICE_OR_OPTION_NOT_IMPLEMENTED";
  case LastCallFailCause::INVALID_TRANSACTION_IDENTIFIER:
    return "INVALID_TRANSACTION_IDENTIFIER";
  case LastCallFailCause::USER_NOT_MEMBER_OF_CUG:
    return "USER_NOT_MEMBER_OF_CUG";
  case LastCallFailCause::INCOMPATIBLE_DESTINATION:
    return "INCOMPATIBLE_DESTINATION";
  case LastCallFailCause::INVALID_TRANSIT_NW_SELECTION:
    return "INVALID_TRANSIT_NW_SELECTION";
  case LastCallFailCause::SEMANTICALLY_INCORRECT_MESSAGE:
    return "SEMANTICALLY_INCORRECT_MESSAGE";
  case LastCallFailCause::INVALID_MANDATORY_INFORMATION:
    return "INVALID_MANDATORY_INFORMATION";
  case LastCallFailCause::MESSAGE_TYPE_NON_IMPLEMENTED:
    return "MESSAGE_TYPE_NON_IMPLEMENTED";
  case LastCallFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE:
    return "MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
  case LastCallFailCause::INFORMATION_ELEMENT_NON_EXISTENT:
    return "INFORMATION_ELEMENT_NON_EXISTENT";
  case LastCallFailCause::CONDITIONAL_IE_ERROR:
    return "CONDITIONAL_IE_ERROR";
  case LastCallFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE:
    return "MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
  case LastCallFailCause::RECOVERY_ON_TIMER_EXPIRED:
    return "RECOVERY_ON_TIMER_EXPIRED";
  case LastCallFailCause::PROTOCOL_ERROR_UNSPECIFIED:
    return "PROTOCOL_ERROR_UNSPECIFIED";
  case LastCallFailCause::INTERWORKING_UNSPECIFIED:
    return "INTERWORKING_UNSPECIFIED";
  case LastCallFailCause::CALL_BARRED:
    return "CALL_BARRED";
  case LastCallFailCause::FDN_BLOCKED:
    return "FDN_BLOCKED";
  case LastCallFailCause::IMSI_UNKNOWN_IN_VLR:
    return "IMSI_UNKNOWN_IN_VLR";
  case LastCallFailCause::IMEI_NOT_ACCEPTED:
    return "IMEI_NOT_ACCEPTED";
  case LastCallFailCause::DIAL_MODIFIED_TO_USSD:
    return "DIAL_MODIFIED_TO_USSD";
  case LastCallFailCause::DIAL_MODIFIED_TO_SS:
    return "DIAL_MODIFIED_TO_SS";
  case LastCallFailCause::DIAL_MODIFIED_TO_DIAL:
    return "DIAL_MODIFIED_TO_DIAL";
  case LastCallFailCause::RADIO_OFF:
    return "RADIO_OFF";
  case LastCallFailCause::OUT_OF_SERVICE:
    return "OUT_OF_SERVICE";
  case LastCallFailCause::NO_VALID_SIM:
    return "NO_VALID_SIM";
  case LastCallFailCause::RADIO_INTERNAL_ERROR:
    return "RADIO_INTERNAL_ERROR";
  case LastCallFailCause::NETWORK_RESP_TIMEOUT:
    return "NETWORK_RESP_TIMEOUT";
  case LastCallFailCause::NETWORK_REJECT:
    return "NETWORK_REJECT";
  case LastCallFailCause::RADIO_ACCESS_FAILURE:
    return "RADIO_ACCESS_FAILURE";
  case LastCallFailCause::RADIO_LINK_FAILURE:
    return "RADIO_LINK_FAILURE";
  case LastCallFailCause::RADIO_LINK_LOST:
    return "RADIO_LINK_LOST";
  case LastCallFailCause::RADIO_UPLINK_FAILURE:
    return "RADIO_UPLINK_FAILURE";
  case LastCallFailCause::RADIO_SETUP_FAILURE:
    return "RADIO_SETUP_FAILURE";
  case LastCallFailCause::RADIO_RELEASE_NORMAL:
    return "RADIO_RELEASE_NORMAL";
  case LastCallFailCause::RADIO_RELEASE_ABNORMAL:
    return "RADIO_RELEASE_ABNORMAL";
  case LastCallFailCause::ACCESS_CLASS_BLOCKED:
    return "ACCESS_CLASS_BLOCKED";
  case LastCallFailCause::NETWORK_DETACH:
    return "NETWORK_DETACH";
  case LastCallFailCause::CDMA_LOCKED_UNTIL_POWER_CYCLE:
    return "CDMA_LOCKED_UNTIL_POWER_CYCLE";
  case LastCallFailCause::CDMA_DROP:
    return "CDMA_DROP";
  case LastCallFailCause::CDMA_INTERCEPT:
    return "CDMA_INTERCEPT";
  case LastCallFailCause::CDMA_REORDER:
    return "CDMA_REORDER";
  case LastCallFailCause::CDMA_SO_REJECT:
    return "CDMA_SO_REJECT";
  case LastCallFailCause::CDMA_RETRY_ORDER:
    return "CDMA_RETRY_ORDER";
  case LastCallFailCause::CDMA_ACCESS_FAILURE:
    return "CDMA_ACCESS_FAILURE";
  case LastCallFailCause::CDMA_PREEMPTED:
    return "CDMA_PREEMPTED";
  case LastCallFailCause::CDMA_NOT_EMERGENCY:
    return "CDMA_NOT_EMERGENCY";
  case LastCallFailCause::CDMA_ACCESS_BLOCKED:
    return "CDMA_ACCESS_BLOCKED";
  case LastCallFailCause::OEM_CAUSE_1:
    return "OEM_CAUSE_1";
  case LastCallFailCause::OEM_CAUSE_2:
    return "OEM_CAUSE_2";
  case LastCallFailCause::OEM_CAUSE_3:
    return "OEM_CAUSE_3";
  case LastCallFailCause::OEM_CAUSE_4:
    return "OEM_CAUSE_4";
  case LastCallFailCause::OEM_CAUSE_5:
    return "OEM_CAUSE_5";
  case LastCallFailCause::OEM_CAUSE_6:
    return "OEM_CAUSE_6";
  case LastCallFailCause::OEM_CAUSE_7:
    return "OEM_CAUSE_7";
  case LastCallFailCause::OEM_CAUSE_8:
    return "OEM_CAUSE_8";
  case LastCallFailCause::OEM_CAUSE_9:
    return "OEM_CAUSE_9";
  case LastCallFailCause::OEM_CAUSE_10:
    return "OEM_CAUSE_10";
  case LastCallFailCause::OEM_CAUSE_11:
    return "OEM_CAUSE_11";
  case LastCallFailCause::OEM_CAUSE_12:
    return "OEM_CAUSE_12";
  case LastCallFailCause::OEM_CAUSE_13:
    return "OEM_CAUSE_13";
  case LastCallFailCause::OEM_CAUSE_14:
    return "OEM_CAUSE_14";
  case LastCallFailCause::OEM_CAUSE_15:
    return "OEM_CAUSE_15";
  case LastCallFailCause::ERROR_UNSPECIFIED:
    return "ERROR_UNSPECIFIED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::voice::LastCallFailCause, 96> enum_values<aidl::android::hardware::radio::voice::LastCallFailCause> = {
  aidl::android::hardware::radio::voice::LastCallFailCause::UNOBTAINABLE_NUMBER,
  aidl::android::hardware::radio::voice::LastCallFailCause::NO_ROUTE_TO_DESTINATION,
  aidl::android::hardware::radio::voice::LastCallFailCause::CHANNEL_UNACCEPTABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::OPERATOR_DETERMINED_BARRING,
  aidl::android::hardware::radio::voice::LastCallFailCause::NORMAL,
  aidl::android::hardware::radio::voice::LastCallFailCause::BUSY,
  aidl::android::hardware::radio::voice::LastCallFailCause::NO_USER_RESPONDING,
  aidl::android::hardware::radio::voice::LastCallFailCause::NO_ANSWER_FROM_USER,
  aidl::android::hardware::radio::voice::LastCallFailCause::CALL_REJECTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::NUMBER_CHANGED,
  aidl::android::hardware::radio::voice::LastCallFailCause::PREEMPTION,
  aidl::android::hardware::radio::voice::LastCallFailCause::DESTINATION_OUT_OF_ORDER,
  aidl::android::hardware::radio::voice::LastCallFailCause::INVALID_NUMBER_FORMAT,
  aidl::android::hardware::radio::voice::LastCallFailCause::FACILITY_REJECTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::RESP_TO_STATUS_ENQUIRY,
  aidl::android::hardware::radio::voice::LastCallFailCause::NORMAL_UNSPECIFIED,
  aidl::android::hardware::radio::voice::LastCallFailCause::CONGESTION,
  aidl::android::hardware::radio::voice::LastCallFailCause::NETWORK_OUT_OF_ORDER,
  aidl::android::hardware::radio::voice::LastCallFailCause::TEMPORARY_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::SWITCHING_EQUIPMENT_CONGESTION,
  aidl::android::hardware::radio::voice::LastCallFailCause::ACCESS_INFORMATION_DISCARDED,
  aidl::android::hardware::radio::voice::LastCallFailCause::REQUESTED_CIRCUIT_OR_CHANNEL_NOT_AVAILABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RESOURCES_UNAVAILABLE_OR_UNSPECIFIED,
  aidl::android::hardware::radio::voice::LastCallFailCause::QOS_UNAVAILABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::REQUESTED_FACILITY_NOT_SUBSCRIBED,
  aidl::android::hardware::radio::voice::LastCallFailCause::INCOMING_CALLS_BARRED_WITHIN_CUG,
  aidl::android::hardware::radio::voice::LastCallFailCause::BEARER_CAPABILITY_NOT_AUTHORIZED,
  aidl::android::hardware::radio::voice::LastCallFailCause::BEARER_CAPABILITY_UNAVAILABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::SERVICE_OPTION_NOT_AVAILABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::BEARER_SERVICE_NOT_IMPLEMENTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::ACM_LIMIT_EXCEEDED,
  aidl::android::hardware::radio::voice::LastCallFailCause::REQUESTED_FACILITY_NOT_IMPLEMENTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::SERVICE_OR_OPTION_NOT_IMPLEMENTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::INVALID_TRANSACTION_IDENTIFIER,
  aidl::android::hardware::radio::voice::LastCallFailCause::USER_NOT_MEMBER_OF_CUG,
  aidl::android::hardware::radio::voice::LastCallFailCause::INCOMPATIBLE_DESTINATION,
  aidl::android::hardware::radio::voice::LastCallFailCause::INVALID_TRANSIT_NW_SELECTION,
  aidl::android::hardware::radio::voice::LastCallFailCause::SEMANTICALLY_INCORRECT_MESSAGE,
  aidl::android::hardware::radio::voice::LastCallFailCause::INVALID_MANDATORY_INFORMATION,
  aidl::android::hardware::radio::voice::LastCallFailCause::MESSAGE_TYPE_NON_IMPLEMENTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
  aidl::android::hardware::radio::voice::LastCallFailCause::INFORMATION_ELEMENT_NON_EXISTENT,
  aidl::android::hardware::radio::voice::LastCallFailCause::CONDITIONAL_IE_ERROR,
  aidl::android::hardware::radio::voice::LastCallFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RECOVERY_ON_TIMER_EXPIRED,
  aidl::android::hardware::radio::voice::LastCallFailCause::PROTOCOL_ERROR_UNSPECIFIED,
  aidl::android::hardware::radio::voice::LastCallFailCause::INTERWORKING_UNSPECIFIED,
  aidl::android::hardware::radio::voice::LastCallFailCause::CALL_BARRED,
  aidl::android::hardware::radio::voice::LastCallFailCause::FDN_BLOCKED,
  aidl::android::hardware::radio::voice::LastCallFailCause::IMSI_UNKNOWN_IN_VLR,
  aidl::android::hardware::radio::voice::LastCallFailCause::IMEI_NOT_ACCEPTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::DIAL_MODIFIED_TO_USSD,
  aidl::android::hardware::radio::voice::LastCallFailCause::DIAL_MODIFIED_TO_SS,
  aidl::android::hardware::radio::voice::LastCallFailCause::DIAL_MODIFIED_TO_DIAL,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_OFF,
  aidl::android::hardware::radio::voice::LastCallFailCause::OUT_OF_SERVICE,
  aidl::android::hardware::radio::voice::LastCallFailCause::NO_VALID_SIM,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_INTERNAL_ERROR,
  aidl::android::hardware::radio::voice::LastCallFailCause::NETWORK_RESP_TIMEOUT,
  aidl::android::hardware::radio::voice::LastCallFailCause::NETWORK_REJECT,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_ACCESS_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_LINK_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_LINK_LOST,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_UPLINK_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_SETUP_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_RELEASE_NORMAL,
  aidl::android::hardware::radio::voice::LastCallFailCause::RADIO_RELEASE_ABNORMAL,
  aidl::android::hardware::radio::voice::LastCallFailCause::ACCESS_CLASS_BLOCKED,
  aidl::android::hardware::radio::voice::LastCallFailCause::NETWORK_DETACH,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_LOCKED_UNTIL_POWER_CYCLE,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_DROP,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_INTERCEPT,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_REORDER,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_SO_REJECT,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_RETRY_ORDER,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_ACCESS_FAILURE,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_PREEMPTED,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_NOT_EMERGENCY,
  aidl::android::hardware::radio::voice::LastCallFailCause::CDMA_ACCESS_BLOCKED,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_1,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_2,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_3,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_4,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_5,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_6,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_7,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_8,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_9,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_10,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_11,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_12,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_13,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_14,
  aidl::android::hardware::radio::voice::LastCallFailCause::OEM_CAUSE_15,
  aidl::android::hardware::radio::voice::LastCallFailCause::ERROR_UNSPECIFIED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
