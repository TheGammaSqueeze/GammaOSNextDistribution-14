#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class ImsiEncryptionInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mcc;
  std::string mnc;
  std::vector<uint8_t> carrierKey;
  std::string keyIdentifier;
  int64_t expirationTime = 0L;
  int8_t keyType = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) != std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }
  inline bool operator<(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) < std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }
  inline bool operator<=(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) <= std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }
  inline bool operator==(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) == std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }
  inline bool operator>(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) > std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }
  inline bool operator>=(const ImsiEncryptionInfo& rhs) const {
    return std::tie(mcc, mnc, carrierKey, keyIdentifier, expirationTime, keyType) >= std::tie(rhs.mcc, rhs.mnc, rhs.carrierKey, rhs.keyIdentifier, rhs.expirationTime, rhs.keyType);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { PUBLIC_KEY_TYPE_EPDG = 1 };
  enum : int8_t { PUBLIC_KEY_TYPE_WLAN = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "ImsiEncryptionInfo{";
    os << "mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", carrierKey: " << ::android::internal::ToString(carrierKey);
    os << ", keyIdentifier: " << ::android::internal::ToString(keyIdentifier);
    os << ", expirationTime: " << ::android::internal::ToString(expirationTime);
    os << ", keyType: " << ::android::internal::ToString(keyType);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
