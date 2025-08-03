#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/MboAssocDisallowedReasonCode.h>
#include <aidl/android/hardware/wifi/supplicant/OceRssiBasedAssocRejectAttr.h>
#include <aidl/android/hardware/wifi/supplicant/StaIfaceStatusCode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class AssociationRejectionData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> ssid;
  std::vector<uint8_t> bssid;
  ::aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode statusCode = ::aidl::android::hardware::wifi::supplicant::StaIfaceStatusCode(0);
  bool timedOut = false;
  bool isMboAssocDisallowedReasonCodePresent = false;
  ::aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode mboAssocDisallowedReason = ::aidl::android::hardware::wifi::supplicant::MboAssocDisallowedReasonCode(0);
  bool isOceRssiBasedAssocRejectAttrPresent = false;
  ::aidl::android::hardware::wifi::supplicant::OceRssiBasedAssocRejectAttr oceRssiBasedAssocRejectData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) != std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }
  inline bool operator<(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) < std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }
  inline bool operator<=(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) <= std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }
  inline bool operator==(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) == std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }
  inline bool operator>(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) > std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }
  inline bool operator>=(const AssociationRejectionData& rhs) const {
    return std::tie(ssid, bssid, statusCode, timedOut, isMboAssocDisallowedReasonCodePresent, mboAssocDisallowedReason, isOceRssiBasedAssocRejectAttrPresent, oceRssiBasedAssocRejectData) >= std::tie(rhs.ssid, rhs.bssid, rhs.statusCode, rhs.timedOut, rhs.isMboAssocDisallowedReasonCodePresent, rhs.mboAssocDisallowedReason, rhs.isOceRssiBasedAssocRejectAttrPresent, rhs.oceRssiBasedAssocRejectData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AssociationRejectionData{";
    os << "ssid: " << ::android::internal::ToString(ssid);
    os << ", bssid: " << ::android::internal::ToString(bssid);
    os << ", statusCode: " << ::android::internal::ToString(statusCode);
    os << ", timedOut: " << ::android::internal::ToString(timedOut);
    os << ", isMboAssocDisallowedReasonCodePresent: " << ::android::internal::ToString(isMboAssocDisallowedReasonCodePresent);
    os << ", mboAssocDisallowedReason: " << ::android::internal::ToString(mboAssocDisallowedReason);
    os << ", isOceRssiBasedAssocRejectAttrPresent: " << ::android::internal::ToString(isOceRssiBasedAssocRejectAttrPresent);
    os << ", oceRssiBasedAssocRejectData: " << ::android::internal::ToString(oceRssiBasedAssocRejectData);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
