#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/ApnAuthType.h>
#include <aidl/android/hardware/radio/data/PdpProtocolType.h>
#include <aidl/android/hardware/radio/data/TrafficDescriptor.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class DataProfileInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t profileId = 0;
  std::string apn;
  ::aidl::android::hardware::radio::data::PdpProtocolType protocol = ::aidl::android::hardware::radio::data::PdpProtocolType(0);
  ::aidl::android::hardware::radio::data::PdpProtocolType roamingProtocol = ::aidl::android::hardware::radio::data::PdpProtocolType(0);
  ::aidl::android::hardware::radio::data::ApnAuthType authType = ::aidl::android::hardware::radio::data::ApnAuthType(0);
  std::string user;
  std::string password;
  int32_t type = 0;
  int32_t maxConnsTime = 0;
  int32_t maxConns = 0;
  int32_t waitTime = 0;
  bool enabled = false;
  int32_t supportedApnTypesBitmap = 0;
  int32_t bearerBitmap = 0;
  int32_t mtuV4 = 0;
  int32_t mtuV6 = 0;
  bool preferred = false;
  bool persistent = false;
  bool alwaysOn = false;
  ::aidl::android::hardware::radio::data::TrafficDescriptor trafficDescriptor;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) != std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }
  inline bool operator<(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) < std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }
  inline bool operator<=(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) <= std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }
  inline bool operator==(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) == std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }
  inline bool operator>(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) > std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }
  inline bool operator>=(const DataProfileInfo& rhs) const {
    return std::tie(profileId, apn, protocol, roamingProtocol, authType, user, password, type, maxConnsTime, maxConns, waitTime, enabled, supportedApnTypesBitmap, bearerBitmap, mtuV4, mtuV6, preferred, persistent, alwaysOn, trafficDescriptor) >= std::tie(rhs.profileId, rhs.apn, rhs.protocol, rhs.roamingProtocol, rhs.authType, rhs.user, rhs.password, rhs.type, rhs.maxConnsTime, rhs.maxConns, rhs.waitTime, rhs.enabled, rhs.supportedApnTypesBitmap, rhs.bearerBitmap, rhs.mtuV4, rhs.mtuV6, rhs.preferred, rhs.persistent, rhs.alwaysOn, rhs.trafficDescriptor);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { ID_DEFAULT = 0 };
  enum : int32_t { ID_TETHERED = 1 };
  enum : int32_t { ID_IMS = 2 };
  enum : int32_t { ID_FOTA = 3 };
  enum : int32_t { ID_CBS = 4 };
  enum : int32_t { ID_OEM_BASE = 1000 };
  enum : int32_t { ID_INVALID = -1 };
  enum : int32_t { TYPE_COMMON = 0 };
  enum : int32_t { TYPE_3GPP = 1 };
  enum : int32_t { TYPE_3GPP2 = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "DataProfileInfo{";
    os << "profileId: " << ::android::internal::ToString(profileId);
    os << ", apn: " << ::android::internal::ToString(apn);
    os << ", protocol: " << ::android::internal::ToString(protocol);
    os << ", roamingProtocol: " << ::android::internal::ToString(roamingProtocol);
    os << ", authType: " << ::android::internal::ToString(authType);
    os << ", user: " << ::android::internal::ToString(user);
    os << ", password: " << ::android::internal::ToString(password);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", maxConnsTime: " << ::android::internal::ToString(maxConnsTime);
    os << ", maxConns: " << ::android::internal::ToString(maxConns);
    os << ", waitTime: " << ::android::internal::ToString(waitTime);
    os << ", enabled: " << ::android::internal::ToString(enabled);
    os << ", supportedApnTypesBitmap: " << ::android::internal::ToString(supportedApnTypesBitmap);
    os << ", bearerBitmap: " << ::android::internal::ToString(bearerBitmap);
    os << ", mtuV4: " << ::android::internal::ToString(mtuV4);
    os << ", mtuV6: " << ::android::internal::ToString(mtuV6);
    os << ", preferred: " << ::android::internal::ToString(preferred);
    os << ", persistent: " << ::android::internal::ToString(persistent);
    os << ", alwaysOn: " << ::android::internal::ToString(alwaysOn);
    os << ", trafficDescriptor: " << ::android::internal::ToString(trafficDescriptor);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
