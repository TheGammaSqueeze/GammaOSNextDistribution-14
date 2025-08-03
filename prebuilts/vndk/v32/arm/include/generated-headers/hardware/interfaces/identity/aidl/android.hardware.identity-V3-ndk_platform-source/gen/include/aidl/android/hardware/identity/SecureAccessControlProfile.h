#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/identity/Certificate.h>
namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class SecureAccessControlProfile {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  ::aidl::android::hardware::identity::Certificate readerCertificate;
  bool userAuthenticationRequired = false;
  int64_t timeoutMillis = 0L;
  int64_t secureUserId = 0L;
  std::vector<uint8_t> mac;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) != std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }
  inline bool operator<(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) < std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }
  inline bool operator<=(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) <= std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }
  inline bool operator==(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) == std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }
  inline bool operator>(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) > std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }
  inline bool operator>=(const SecureAccessControlProfile& rhs) const {
    return std::tie(id, readerCertificate, userAuthenticationRequired, timeoutMillis, secureUserId, mac) >= std::tie(rhs.id, rhs.readerCertificate, rhs.userAuthenticationRequired, rhs.timeoutMillis, rhs.secureUserId, rhs.mac);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SecureAccessControlProfile{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", readerCertificate: " << ::android::internal::ToString(readerCertificate);
    os << ", userAuthenticationRequired: " << ::android::internal::ToString(userAuthenticationRequired);
    os << ", timeoutMillis: " << ::android::internal::ToString(timeoutMillis);
    os << ", secureUserId: " << ::android::internal::ToString(secureUserId);
    os << ", mac: " << ::android::internal::ToString(mac);
    os << "}";
    return os.str();
  }
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
