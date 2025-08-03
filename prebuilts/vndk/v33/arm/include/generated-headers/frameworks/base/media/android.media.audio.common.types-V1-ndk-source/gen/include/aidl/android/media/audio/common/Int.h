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
namespace media {
namespace audio {
namespace common {
class Int {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t value = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Int& rhs) const {
    return std::tie(value) != std::tie(rhs.value);
  }
  inline bool operator<(const Int& rhs) const {
    return std::tie(value) < std::tie(rhs.value);
  }
  inline bool operator<=(const Int& rhs) const {
    return std::tie(value) <= std::tie(rhs.value);
  }
  inline bool operator==(const Int& rhs) const {
    return std::tie(value) == std::tie(rhs.value);
  }
  inline bool operator>(const Int& rhs) const {
    return std::tie(value) > std::tie(rhs.value);
  }
  inline bool operator>=(const Int& rhs) const {
    return std::tie(value) >= std::tie(rhs.value);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Int{";
    os << "value: " << ::android::internal::ToString(value);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
