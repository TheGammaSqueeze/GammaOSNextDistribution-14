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
namespace supplicant {
enum class StaIfaceStatusCode : int32_t {
  SUCCESS = 0,
  UNSPECIFIED_FAILURE = 1,
  TDLS_WAKEUP_ALTERNATE = 2,
  TDLS_WAKEUP_REJECT = 3,
  SECURITY_DISABLED = 5,
  UNACCEPTABLE_LIFETIME = 6,
  NOT_IN_SAME_BSS = 7,
  CAPS_UNSUPPORTED = 10,
  REASSOC_NO_ASSOC = 11,
  ASSOC_DENIED_UNSPEC = 12,
  NOT_SUPPORTED_AUTH_ALG = 13,
  UNKNOWN_AUTH_TRANSACTION = 14,
  CHALLENGE_FAIL = 15,
  AUTH_TIMEOUT = 16,
  AP_UNABLE_TO_HANDLE_NEW_STA = 17,
  ASSOC_DENIED_RATES = 18,
  ASSOC_DENIED_NOSHORT = 19,
  SPEC_MGMT_REQUIRED = 22,
  PWR_CAPABILITY_NOT_VALID = 23,
  SUPPORTED_CHANNEL_NOT_VALID = 24,
  ASSOC_DENIED_NO_SHORT_SLOT_TIME = 25,
  ASSOC_DENIED_NO_HT = 27,
  R0KH_UNREACHABLE = 28,
  ASSOC_DENIED_NO_PCO = 29,
  ASSOC_REJECTED_TEMPORARILY = 30,
  ROBUST_MGMT_FRAME_POLICY_VIOLATION = 31,
  UNSPECIFIED_QOS_FAILURE = 32,
  DENIED_INSUFFICIENT_BANDWIDTH = 33,
  DENIED_POOR_CHANNEL_CONDITIONS = 34,
  DENIED_QOS_NOT_SUPPORTED = 35,
  REQUEST_DECLINED = 37,
  INVALID_PARAMETERS = 38,
  REJECTED_WITH_SUGGESTED_CHANGES = 39,
  INVALID_IE = 40,
  GROUP_CIPHER_NOT_VALID = 41,
  PAIRWISE_CIPHER_NOT_VALID = 42,
  AKMP_NOT_VALID = 43,
  UNSUPPORTED_RSN_IE_VERSION = 44,
  INVALID_RSN_IE_CAPAB = 45,
  CIPHER_REJECTED_PER_POLICY = 46,
  TS_NOT_CREATED = 47,
  DIRECT_LINK_NOT_ALLOWED = 48,
  DEST_STA_NOT_PRESENT = 49,
  DEST_STA_NOT_QOS_STA = 50,
  ASSOC_DENIED_LISTEN_INT_TOO_LARGE = 51,
  INVALID_FT_ACTION_FRAME_COUNT = 52,
  INVALID_PMKID = 53,
  INVALID_MDIE = 54,
  INVALID_FTIE = 55,
  REQUESTED_TCLAS_NOT_SUPPORTED = 56,
  INSUFFICIENT_TCLAS_PROCESSING_RESOURCES = 57,
  TRY_ANOTHER_BSS = 58,
  GAS_ADV_PROTO_NOT_SUPPORTED = 59,
  NO_OUTSTANDING_GAS_REQ = 60,
  GAS_RESP_NOT_RECEIVED = 61,
  STA_TIMED_OUT_WAITING_FOR_GAS_RESP = 62,
  GAS_RESP_LARGER_THAN_LIMIT = 63,
  REQ_REFUSED_HOME = 64,
  ADV_SRV_UNREACHABLE = 65,
  REQ_REFUSED_SSPN = 67,
  REQ_REFUSED_UNAUTH_ACCESS = 68,
  INVALID_RSNIE = 72,
  U_APSD_COEX_NOT_SUPPORTED = 73,
  U_APSD_COEX_MODE_NOT_SUPPORTED = 74,
  BAD_INTERVAL_WITH_U_APSD_COEX = 75,
  ANTI_CLOGGING_TOKEN_REQ = 76,
  FINITE_CYCLIC_GROUP_NOT_SUPPORTED = 77,
  CANNOT_FIND_ALT_TBTT = 78,
  TRANSMISSION_FAILURE = 79,
  REQ_TCLAS_NOT_SUPPORTED = 80,
  TCLAS_RESOURCES_EXCHAUSTED = 81,
  REJECTED_WITH_SUGGESTED_BSS_TRANSITION = 82,
  REJECT_WITH_SCHEDULE = 83,
  REJECT_NO_WAKEUP_SPECIFIED = 84,
  SUCCESS_POWER_SAVE_MODE = 85,
  PENDING_ADMITTING_FST_SESSION = 86,
  PERFORMING_FST_NOW = 87,
  PENDING_GAP_IN_BA_WINDOW = 88,
  REJECT_U_PID_SETTING = 89,
  REFUSED_EXTERNAL_REASON = 92,
  REFUSED_AP_OUT_OF_MEMORY = 93,
  REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED = 94,
  QUERY_RESP_OUTSTANDING = 95,
  REJECT_DSE_BAND = 96,
  TCLAS_PROCESSING_TERMINATED = 97,
  TS_SCHEDULE_CONFLICT = 98,
  DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL = 99,
  MCCAOP_RESERVATION_CONFLICT = 100,
  MAF_LIMIT_EXCEEDED = 101,
  MCCA_TRACK_LIMIT_EXCEEDED = 102,
  DENIED_DUE_TO_SPECTRUM_MANAGEMENT = 103,
  ASSOC_DENIED_NO_VHT = 104,
  ENABLEMENT_DENIED = 105,
  RESTRICTION_FROM_AUTHORIZED_GDB = 106,
  AUTHORIZATION_DEENABLED = 107,
  FILS_AUTHENTICATION_FAILURE = 112,
  UNKNOWN_AUTHENTICATION_SERVER = 113,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(StaIfaceStatusCode val) {
  switch(val) {
  case StaIfaceStatusCode::SUCCESS:
    return "SUCCESS";
  case StaIfaceStatusCode::UNSPECIFIED_FAILURE:
    return "UNSPECIFIED_FAILURE";
  case StaIfaceStatusCode::TDLS_WAKEUP_ALTERNATE:
    return "TDLS_WAKEUP_ALTERNATE";
  case StaIfaceStatusCode::TDLS_WAKEUP_REJECT:
    return "TDLS_WAKEUP_REJECT";
  case StaIfaceStatusCode::SECURITY_DISABLED:
    return "SECURITY_DISABLED";
  case StaIfaceStatusCode::UNACCEPTABLE_LIFETIME:
    return "UNACCEPTABLE_LIFETIME";
  case StaIfaceStatusCode::NOT_IN_SAME_BSS:
    return "NOT_IN_SAME_BSS";
  case StaIfaceStatusCode::CAPS_UNSUPPORTED:
    return "CAPS_UNSUPPORTED";
  case StaIfaceStatusCode::REASSOC_NO_ASSOC:
    return "REASSOC_NO_ASSOC";
  case StaIfaceStatusCode::ASSOC_DENIED_UNSPEC:
    return "ASSOC_DENIED_UNSPEC";
  case StaIfaceStatusCode::NOT_SUPPORTED_AUTH_ALG:
    return "NOT_SUPPORTED_AUTH_ALG";
  case StaIfaceStatusCode::UNKNOWN_AUTH_TRANSACTION:
    return "UNKNOWN_AUTH_TRANSACTION";
  case StaIfaceStatusCode::CHALLENGE_FAIL:
    return "CHALLENGE_FAIL";
  case StaIfaceStatusCode::AUTH_TIMEOUT:
    return "AUTH_TIMEOUT";
  case StaIfaceStatusCode::AP_UNABLE_TO_HANDLE_NEW_STA:
    return "AP_UNABLE_TO_HANDLE_NEW_STA";
  case StaIfaceStatusCode::ASSOC_DENIED_RATES:
    return "ASSOC_DENIED_RATES";
  case StaIfaceStatusCode::ASSOC_DENIED_NOSHORT:
    return "ASSOC_DENIED_NOSHORT";
  case StaIfaceStatusCode::SPEC_MGMT_REQUIRED:
    return "SPEC_MGMT_REQUIRED";
  case StaIfaceStatusCode::PWR_CAPABILITY_NOT_VALID:
    return "PWR_CAPABILITY_NOT_VALID";
  case StaIfaceStatusCode::SUPPORTED_CHANNEL_NOT_VALID:
    return "SUPPORTED_CHANNEL_NOT_VALID";
  case StaIfaceStatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME:
    return "ASSOC_DENIED_NO_SHORT_SLOT_TIME";
  case StaIfaceStatusCode::ASSOC_DENIED_NO_HT:
    return "ASSOC_DENIED_NO_HT";
  case StaIfaceStatusCode::R0KH_UNREACHABLE:
    return "R0KH_UNREACHABLE";
  case StaIfaceStatusCode::ASSOC_DENIED_NO_PCO:
    return "ASSOC_DENIED_NO_PCO";
  case StaIfaceStatusCode::ASSOC_REJECTED_TEMPORARILY:
    return "ASSOC_REJECTED_TEMPORARILY";
  case StaIfaceStatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION:
    return "ROBUST_MGMT_FRAME_POLICY_VIOLATION";
  case StaIfaceStatusCode::UNSPECIFIED_QOS_FAILURE:
    return "UNSPECIFIED_QOS_FAILURE";
  case StaIfaceStatusCode::DENIED_INSUFFICIENT_BANDWIDTH:
    return "DENIED_INSUFFICIENT_BANDWIDTH";
  case StaIfaceStatusCode::DENIED_POOR_CHANNEL_CONDITIONS:
    return "DENIED_POOR_CHANNEL_CONDITIONS";
  case StaIfaceStatusCode::DENIED_QOS_NOT_SUPPORTED:
    return "DENIED_QOS_NOT_SUPPORTED";
  case StaIfaceStatusCode::REQUEST_DECLINED:
    return "REQUEST_DECLINED";
  case StaIfaceStatusCode::INVALID_PARAMETERS:
    return "INVALID_PARAMETERS";
  case StaIfaceStatusCode::REJECTED_WITH_SUGGESTED_CHANGES:
    return "REJECTED_WITH_SUGGESTED_CHANGES";
  case StaIfaceStatusCode::INVALID_IE:
    return "INVALID_IE";
  case StaIfaceStatusCode::GROUP_CIPHER_NOT_VALID:
    return "GROUP_CIPHER_NOT_VALID";
  case StaIfaceStatusCode::PAIRWISE_CIPHER_NOT_VALID:
    return "PAIRWISE_CIPHER_NOT_VALID";
  case StaIfaceStatusCode::AKMP_NOT_VALID:
    return "AKMP_NOT_VALID";
  case StaIfaceStatusCode::UNSUPPORTED_RSN_IE_VERSION:
    return "UNSUPPORTED_RSN_IE_VERSION";
  case StaIfaceStatusCode::INVALID_RSN_IE_CAPAB:
    return "INVALID_RSN_IE_CAPAB";
  case StaIfaceStatusCode::CIPHER_REJECTED_PER_POLICY:
    return "CIPHER_REJECTED_PER_POLICY";
  case StaIfaceStatusCode::TS_NOT_CREATED:
    return "TS_NOT_CREATED";
  case StaIfaceStatusCode::DIRECT_LINK_NOT_ALLOWED:
    return "DIRECT_LINK_NOT_ALLOWED";
  case StaIfaceStatusCode::DEST_STA_NOT_PRESENT:
    return "DEST_STA_NOT_PRESENT";
  case StaIfaceStatusCode::DEST_STA_NOT_QOS_STA:
    return "DEST_STA_NOT_QOS_STA";
  case StaIfaceStatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE:
    return "ASSOC_DENIED_LISTEN_INT_TOO_LARGE";
  case StaIfaceStatusCode::INVALID_FT_ACTION_FRAME_COUNT:
    return "INVALID_FT_ACTION_FRAME_COUNT";
  case StaIfaceStatusCode::INVALID_PMKID:
    return "INVALID_PMKID";
  case StaIfaceStatusCode::INVALID_MDIE:
    return "INVALID_MDIE";
  case StaIfaceStatusCode::INVALID_FTIE:
    return "INVALID_FTIE";
  case StaIfaceStatusCode::REQUESTED_TCLAS_NOT_SUPPORTED:
    return "REQUESTED_TCLAS_NOT_SUPPORTED";
  case StaIfaceStatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES:
    return "INSUFFICIENT_TCLAS_PROCESSING_RESOURCES";
  case StaIfaceStatusCode::TRY_ANOTHER_BSS:
    return "TRY_ANOTHER_BSS";
  case StaIfaceStatusCode::GAS_ADV_PROTO_NOT_SUPPORTED:
    return "GAS_ADV_PROTO_NOT_SUPPORTED";
  case StaIfaceStatusCode::NO_OUTSTANDING_GAS_REQ:
    return "NO_OUTSTANDING_GAS_REQ";
  case StaIfaceStatusCode::GAS_RESP_NOT_RECEIVED:
    return "GAS_RESP_NOT_RECEIVED";
  case StaIfaceStatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP:
    return "STA_TIMED_OUT_WAITING_FOR_GAS_RESP";
  case StaIfaceStatusCode::GAS_RESP_LARGER_THAN_LIMIT:
    return "GAS_RESP_LARGER_THAN_LIMIT";
  case StaIfaceStatusCode::REQ_REFUSED_HOME:
    return "REQ_REFUSED_HOME";
  case StaIfaceStatusCode::ADV_SRV_UNREACHABLE:
    return "ADV_SRV_UNREACHABLE";
  case StaIfaceStatusCode::REQ_REFUSED_SSPN:
    return "REQ_REFUSED_SSPN";
  case StaIfaceStatusCode::REQ_REFUSED_UNAUTH_ACCESS:
    return "REQ_REFUSED_UNAUTH_ACCESS";
  case StaIfaceStatusCode::INVALID_RSNIE:
    return "INVALID_RSNIE";
  case StaIfaceStatusCode::U_APSD_COEX_NOT_SUPPORTED:
    return "U_APSD_COEX_NOT_SUPPORTED";
  case StaIfaceStatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED:
    return "U_APSD_COEX_MODE_NOT_SUPPORTED";
  case StaIfaceStatusCode::BAD_INTERVAL_WITH_U_APSD_COEX:
    return "BAD_INTERVAL_WITH_U_APSD_COEX";
  case StaIfaceStatusCode::ANTI_CLOGGING_TOKEN_REQ:
    return "ANTI_CLOGGING_TOKEN_REQ";
  case StaIfaceStatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED:
    return "FINITE_CYCLIC_GROUP_NOT_SUPPORTED";
  case StaIfaceStatusCode::CANNOT_FIND_ALT_TBTT:
    return "CANNOT_FIND_ALT_TBTT";
  case StaIfaceStatusCode::TRANSMISSION_FAILURE:
    return "TRANSMISSION_FAILURE";
  case StaIfaceStatusCode::REQ_TCLAS_NOT_SUPPORTED:
    return "REQ_TCLAS_NOT_SUPPORTED";
  case StaIfaceStatusCode::TCLAS_RESOURCES_EXCHAUSTED:
    return "TCLAS_RESOURCES_EXCHAUSTED";
  case StaIfaceStatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION:
    return "REJECTED_WITH_SUGGESTED_BSS_TRANSITION";
  case StaIfaceStatusCode::REJECT_WITH_SCHEDULE:
    return "REJECT_WITH_SCHEDULE";
  case StaIfaceStatusCode::REJECT_NO_WAKEUP_SPECIFIED:
    return "REJECT_NO_WAKEUP_SPECIFIED";
  case StaIfaceStatusCode::SUCCESS_POWER_SAVE_MODE:
    return "SUCCESS_POWER_SAVE_MODE";
  case StaIfaceStatusCode::PENDING_ADMITTING_FST_SESSION:
    return "PENDING_ADMITTING_FST_SESSION";
  case StaIfaceStatusCode::PERFORMING_FST_NOW:
    return "PERFORMING_FST_NOW";
  case StaIfaceStatusCode::PENDING_GAP_IN_BA_WINDOW:
    return "PENDING_GAP_IN_BA_WINDOW";
  case StaIfaceStatusCode::REJECT_U_PID_SETTING:
    return "REJECT_U_PID_SETTING";
  case StaIfaceStatusCode::REFUSED_EXTERNAL_REASON:
    return "REFUSED_EXTERNAL_REASON";
  case StaIfaceStatusCode::REFUSED_AP_OUT_OF_MEMORY:
    return "REFUSED_AP_OUT_OF_MEMORY";
  case StaIfaceStatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED:
    return "REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED";
  case StaIfaceStatusCode::QUERY_RESP_OUTSTANDING:
    return "QUERY_RESP_OUTSTANDING";
  case StaIfaceStatusCode::REJECT_DSE_BAND:
    return "REJECT_DSE_BAND";
  case StaIfaceStatusCode::TCLAS_PROCESSING_TERMINATED:
    return "TCLAS_PROCESSING_TERMINATED";
  case StaIfaceStatusCode::TS_SCHEDULE_CONFLICT:
    return "TS_SCHEDULE_CONFLICT";
  case StaIfaceStatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL:
    return "DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL";
  case StaIfaceStatusCode::MCCAOP_RESERVATION_CONFLICT:
    return "MCCAOP_RESERVATION_CONFLICT";
  case StaIfaceStatusCode::MAF_LIMIT_EXCEEDED:
    return "MAF_LIMIT_EXCEEDED";
  case StaIfaceStatusCode::MCCA_TRACK_LIMIT_EXCEEDED:
    return "MCCA_TRACK_LIMIT_EXCEEDED";
  case StaIfaceStatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT:
    return "DENIED_DUE_TO_SPECTRUM_MANAGEMENT";
  case StaIfaceStatusCode::ASSOC_DENIED_NO_VHT:
    return "ASSOC_DENIED_NO_VHT";
  case StaIfaceStatusCode::ENABLEMENT_DENIED:
    return "ENABLEMENT_DENIED";
  case StaIfaceStatusCode::RESTRICTION_FROM_AUTHORIZED_GDB:
    return "RESTRICTION_FROM_AUTHORIZED_GDB";
  case StaIfaceStatusCode::AUTHORIZATION_DEENABLED:
    return "AUTHORIZATION_DEENABLED";
  case StaIfaceStatusCode::FILS_AUTHENTICATION_FAILURE:
    return "FILS_AUTHENTICATION_FAILURE";
  case StaIfaceStatusCode::UNKNOWN_AUTHENTICATION_SERVER:
    return "UNKNOWN_AUTHENTICATION_SERVER";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode, 97> enum_values<aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode> = {
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::SUCCESS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNSPECIFIED_FAILURE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TDLS_WAKEUP_ALTERNATE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TDLS_WAKEUP_REJECT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::SECURITY_DISABLED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNACCEPTABLE_LIFETIME,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::NOT_IN_SAME_BSS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::CAPS_UNSUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REASSOC_NO_ASSOC,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_UNSPEC,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::NOT_SUPPORTED_AUTH_ALG,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNKNOWN_AUTH_TRANSACTION,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::CHALLENGE_FAIL,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::AUTH_TIMEOUT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::AP_UNABLE_TO_HANDLE_NEW_STA,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_RATES,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_NOSHORT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::SPEC_MGMT_REQUIRED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::PWR_CAPABILITY_NOT_VALID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::SUPPORTED_CHANNEL_NOT_VALID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_NO_HT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::R0KH_UNREACHABLE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_NO_PCO,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_REJECTED_TEMPORARILY,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNSPECIFIED_QOS_FAILURE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DENIED_INSUFFICIENT_BANDWIDTH,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DENIED_POOR_CHANNEL_CONDITIONS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DENIED_QOS_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQUEST_DECLINED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_PARAMETERS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECTED_WITH_SUGGESTED_CHANGES,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_IE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::GROUP_CIPHER_NOT_VALID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::PAIRWISE_CIPHER_NOT_VALID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::AKMP_NOT_VALID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNSUPPORTED_RSN_IE_VERSION,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_RSN_IE_CAPAB,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::CIPHER_REJECTED_PER_POLICY,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TS_NOT_CREATED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DIRECT_LINK_NOT_ALLOWED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DEST_STA_NOT_PRESENT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DEST_STA_NOT_QOS_STA,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_FT_ACTION_FRAME_COUNT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_PMKID,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_MDIE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_FTIE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQUESTED_TCLAS_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TRY_ANOTHER_BSS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::GAS_ADV_PROTO_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::NO_OUTSTANDING_GAS_REQ,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::GAS_RESP_NOT_RECEIVED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::GAS_RESP_LARGER_THAN_LIMIT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQ_REFUSED_HOME,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ADV_SRV_UNREACHABLE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQ_REFUSED_SSPN,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQ_REFUSED_UNAUTH_ACCESS,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::INVALID_RSNIE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::U_APSD_COEX_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::BAD_INTERVAL_WITH_U_APSD_COEX,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ANTI_CLOGGING_TOKEN_REQ,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::CANNOT_FIND_ALT_TBTT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TRANSMISSION_FAILURE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REQ_TCLAS_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TCLAS_RESOURCES_EXCHAUSTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECT_WITH_SCHEDULE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECT_NO_WAKEUP_SPECIFIED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::SUCCESS_POWER_SAVE_MODE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::PENDING_ADMITTING_FST_SESSION,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::PERFORMING_FST_NOW,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::PENDING_GAP_IN_BA_WINDOW,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECT_U_PID_SETTING,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REFUSED_EXTERNAL_REASON,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REFUSED_AP_OUT_OF_MEMORY,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::QUERY_RESP_OUTSTANDING,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::REJECT_DSE_BAND,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TCLAS_PROCESSING_TERMINATED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::TS_SCHEDULE_CONFLICT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::MCCAOP_RESERVATION_CONFLICT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::MAF_LIMIT_EXCEEDED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::MCCA_TRACK_LIMIT_EXCEEDED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ASSOC_DENIED_NO_VHT,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::ENABLEMENT_DENIED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::RESTRICTION_FROM_AUTHORIZED_GDB,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::AUTHORIZATION_DEENABLED,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::FILS_AUTHENTICATION_FAILURE,
  aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode::UNKNOWN_AUTHENTICATION_SERVER,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
