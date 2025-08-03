#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/hostapd/EncryptionType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class NetworkParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> ssid;
  bool isHidden = false;
  ::aidl::android::hardware::wifi::hostapd::EncryptionType encryptionType = ::aidl::android::hardware::wifi::hostapd::EncryptionType(0);
  std::string passphrase;
  bool isMetered = false;
  std::vector<uint8_t> vendorElements;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) != std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }
  inline bool operator<(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) < std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }
  inline bool operator<=(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) <= std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }
  inline bool operator==(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) == std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }
  inline bool operator>(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) > std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }
  inline bool operator>=(const NetworkParams& rhs) const {
    return std::tie(ssid, isHidden, encryptionType, passphrase, isMetered, vendorElements) >= std::tie(rhs.ssid, rhs.isHidden, rhs.encryptionType, rhs.passphrase, rhs.isMetered, rhs.vendorElements);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkParams{";
    os << "ssid: " << ::android::internal::ToString(ssid);
    os << ", isHidden: " << ::android::internal::ToString(isHidden);
    os << ", encryptionType: " << ::android::internal::ToString(encryptionType);
    os << ", passphrase: " << ::android::internal::ToString(passphrase);
    os << ", isMetered: " << ::android::internal::ToString(isMetered);
    os << ", vendorElements: " << ::android::internal::ToString(vendorElements);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
