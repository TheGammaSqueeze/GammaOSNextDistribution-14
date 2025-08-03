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
namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class HardwareInformation {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string credentialStoreName;
  std::string credentialStoreAuthorName;
  int32_t dataChunkSize = 0;
  bool isDirectAccess = false;
  std::vector<std::string> supportedDocTypes;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) != std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }
  inline bool operator<(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) < std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }
  inline bool operator<=(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) <= std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }
  inline bool operator==(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) == std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }
  inline bool operator>(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) > std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }
  inline bool operator>=(const HardwareInformation& rhs) const {
    return std::tie(credentialStoreName, credentialStoreAuthorName, dataChunkSize, isDirectAccess, supportedDocTypes) >= std::tie(rhs.credentialStoreName, rhs.credentialStoreAuthorName, rhs.dataChunkSize, rhs.isDirectAccess, rhs.supportedDocTypes);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HardwareInformation{";
    os << "credentialStoreName: " << ::android::internal::ToString(credentialStoreName);
    os << ", credentialStoreAuthorName: " << ::android::internal::ToString(credentialStoreAuthorName);
    os << ", dataChunkSize: " << ::android::internal::ToString(dataChunkSize);
    os << ", isDirectAccess: " << ::android::internal::ToString(isDirectAccess);
    os << ", supportedDocTypes: " << ::android::internal::ToString(supportedDocTypes);
    os << "}";
    return os.str();
  }
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
