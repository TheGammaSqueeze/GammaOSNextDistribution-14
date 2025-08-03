#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsAddress.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsSubaddress.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class CdmaSmsMessage {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t teleserviceId = 0;
  bool isServicePresent = false;
  int32_t serviceCategory = 0;
  ::aidl::android::hardware::radio::messaging::CdmaSmsAddress address;
  ::aidl::android::hardware::radio::messaging::CdmaSmsSubaddress subAddress;
  std::vector<uint8_t> bearerData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) != std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }
  inline bool operator<(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) < std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }
  inline bool operator<=(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) <= std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }
  inline bool operator==(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) == std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }
  inline bool operator>(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) > std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }
  inline bool operator>=(const CdmaSmsMessage& rhs) const {
    return std::tie(teleserviceId, isServicePresent, serviceCategory, address, subAddress, bearerData) >= std::tie(rhs.teleserviceId, rhs.isServicePresent, rhs.serviceCategory, rhs.address, rhs.subAddress, rhs.bearerData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSmsMessage{";
    os << "teleserviceId: " << ::android::internal::ToString(teleserviceId);
    os << ", isServicePresent: " << ::android::internal::ToString(isServicePresent);
    os << ", serviceCategory: " << ::android::internal::ToString(serviceCategory);
    os << ", address: " << ::android::internal::ToString(address);
    os << ", subAddress: " << ::android::internal::ToString(subAddress);
    os << ", bearerData: " << ::android::internal::ToString(bearerData);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
