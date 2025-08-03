#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/drm/KeyStatusType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class KeyStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> keyId;
  ::aidl::android::hardware::drm::KeyStatusType type = ::aidl::android::hardware::drm::KeyStatusType(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyStatus& rhs) const {
    return std::tie(keyId, type) != std::tie(rhs.keyId, rhs.type);
  }
  inline bool operator<(const KeyStatus& rhs) const {
    return std::tie(keyId, type) < std::tie(rhs.keyId, rhs.type);
  }
  inline bool operator<=(const KeyStatus& rhs) const {
    return std::tie(keyId, type) <= std::tie(rhs.keyId, rhs.type);
  }
  inline bool operator==(const KeyStatus& rhs) const {
    return std::tie(keyId, type) == std::tie(rhs.keyId, rhs.type);
  }
  inline bool operator>(const KeyStatus& rhs) const {
    return std::tie(keyId, type) > std::tie(rhs.keyId, rhs.type);
  }
  inline bool operator>=(const KeyStatus& rhs) const {
    return std::tie(keyId, type) >= std::tie(rhs.keyId, rhs.type);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyStatus{";
    os << "keyId: " << ::android::internal::ToString(keyId);
    os << ", type: " << ::android::internal::ToString(type);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
