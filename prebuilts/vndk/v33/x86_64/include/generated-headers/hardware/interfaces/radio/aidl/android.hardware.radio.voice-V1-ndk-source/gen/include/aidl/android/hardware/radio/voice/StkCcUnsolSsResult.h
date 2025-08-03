#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioError.h>
#include <aidl/android/hardware/radio/voice/CfData.h>
#include <aidl/android/hardware/radio/voice/SsInfoData.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class StkCcUnsolSsResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t serviceType = 0;
  int32_t requestType = 0;
  int32_t teleserviceType = 0;
  int32_t serviceClass = 0;
  ::aidl::android::hardware::radio::RadioError result = ::aidl::android::hardware::radio::RadioError(0);
  std::vector<::aidl::android::hardware::radio::voice::SsInfoData> ssInfo;
  std::vector<::aidl::android::hardware::radio::voice::CfData> cfData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) != std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }
  inline bool operator<(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) < std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }
  inline bool operator<=(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) <= std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }
  inline bool operator==(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) == std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }
  inline bool operator>(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) > std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }
  inline bool operator>=(const StkCcUnsolSsResult& rhs) const {
    return std::tie(serviceType, requestType, teleserviceType, serviceClass, result, ssInfo, cfData) >= std::tie(rhs.serviceType, rhs.requestType, rhs.teleserviceType, rhs.serviceClass, rhs.result, rhs.ssInfo, rhs.cfData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { REQUEST_TYPE_ACTIVATION = 0 };
  enum : int32_t { REQUEST_TYPE_DEACTIVATION = 1 };
  enum : int32_t { REQUEST_TYPE_INTERROGATION = 2 };
  enum : int32_t { REQUEST_TYPE_REGISTRATION = 3 };
  enum : int32_t { REQUEST_TYPE_ERASURE = 4 };
  enum : int32_t { SERVICE_TYPE_CFU = 0 };
  enum : int32_t { SERVICE_TYPE_CF_BUSY = 1 };
  enum : int32_t { SERVICE_TYPE_CF_NO_REPLY = 2 };
  enum : int32_t { SERVICE_TYPE_CF_NOT_REACHABLE = 3 };
  enum : int32_t { SERVICE_TYPE_CF_ALL = 4 };
  enum : int32_t { SERVICE_TYPE_CF_ALL_CONDITIONAL = 5 };
  enum : int32_t { SERVICE_TYPE_CLIP = 6 };
  enum : int32_t { SERVICE_TYPE_CLIR = 7 };
  enum : int32_t { SERVICE_TYPE_COLP = 8 };
  enum : int32_t { SERVICE_TYPE_COLR = 9 };
  enum : int32_t { SERVICE_TYPE_WAIT = 10 };
  enum : int32_t { SERVICE_TYPE_BAOC = 11 };
  enum : int32_t { SERVICE_TYPE_BAOIC = 12 };
  enum : int32_t { SERVICE_TYPE_BAOIC_EXC_HOME = 13 };
  enum : int32_t { SERVICE_TYPE_BAIC = 14 };
  enum : int32_t { SERVICE_TYPE_BAIC_ROAMING = 15 };
  enum : int32_t { SERVICE_TYPE_ALL_BARRING = 16 };
  enum : int32_t { SERVICE_TYPE_OUTGOING_BARRING = 17 };
  enum : int32_t { SERVICE_TYPE_INCOMING_BARRING = 18 };
  enum : int32_t { TELESERVICE_TYPE_ALL_TELE_AND_BEARER_SERVICES = 0 };
  enum : int32_t { TELESERVICE_TYPE_ALL_TELESEVICES = 1 };
  enum : int32_t { TELESERVICE_TYPE_TELEPHONY = 2 };
  enum : int32_t { TELESERVICE_TYPE_ALL_DATA_TELESERVICES = 3 };
  enum : int32_t { TELESERVICE_TYPE_SMS_SERVICES = 4 };
  enum : int32_t { TELESERVICE_TYPE_ALL_TELESERVICES_EXCEPT_SMS = 5 };
  enum : int32_t { SUPP_SERVICE_CLASS_NONE = 0 };
  enum : int32_t { SUPP_SERVICE_CLASS_VOICE = 1 };
  enum : int32_t { SUPP_SERVICE_CLASS_DATA = 2 };
  enum : int32_t { SUPP_SERVICE_CLASS_FAX = 4 };
  enum : int32_t { SUPP_SERVICE_CLASS_SMS = 8 };
  enum : int32_t { SUPP_SERVICE_CLASS_DATA_SYNC = 16 };
  enum : int32_t { SUPP_SERVICE_CLASS_DATA_ASYNC = 32 };
  enum : int32_t { SUPP_SERVICE_CLASS_PACKET = 64 };
  enum : int32_t { SUPP_SERVICE_CLASS_PAD = 128 };
  enum : int32_t { SUPP_SERVICE_CLASS_MAX = 128 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "StkCcUnsolSsResult{";
    os << "serviceType: " << ::android::internal::ToString(serviceType);
    os << ", requestType: " << ::android::internal::ToString(requestType);
    os << ", teleserviceType: " << ::android::internal::ToString(teleserviceType);
    os << ", serviceClass: " << ::android::internal::ToString(serviceClass);
    os << ", result: " << ::android::internal::ToString(result);
    os << ", ssInfo: " << ::android::internal::ToString(ssInfo);
    os << ", cfData: " << ::android::internal::ToString(cfData);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
