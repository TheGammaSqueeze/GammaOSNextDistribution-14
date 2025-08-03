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
namespace drm {
class Uuid {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::array<uint8_t, 16> uuid = {{}};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Uuid& rhs) const {
    return std::tie(uuid) != std::tie(rhs.uuid);
  }
  inline bool operator<(const Uuid& rhs) const {
    return std::tie(uuid) < std::tie(rhs.uuid);
  }
  inline bool operator<=(const Uuid& rhs) const {
    return std::tie(uuid) <= std::tie(rhs.uuid);
  }
  inline bool operator==(const Uuid& rhs) const {
    return std::tie(uuid) == std::tie(rhs.uuid);
  }
  inline bool operator>(const Uuid& rhs) const {
    return std::tie(uuid) > std::tie(rhs.uuid);
  }
  inline bool operator>=(const Uuid& rhs) const {
    return std::tie(uuid) >= std::tie(rhs.uuid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Uuid{";
    os << "uuid: " << ::android::internal::ToString(uuid);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
