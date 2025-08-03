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
namespace graphics {
namespace common {
class ExtendableType {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string name;
  int64_t value = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ExtendableType& rhs) const {
    return std::tie(name, value) != std::tie(rhs.name, rhs.value);
  }
  inline bool operator<(const ExtendableType& rhs) const {
    return std::tie(name, value) < std::tie(rhs.name, rhs.value);
  }
  inline bool operator<=(const ExtendableType& rhs) const {
    return std::tie(name, value) <= std::tie(rhs.name, rhs.value);
  }
  inline bool operator==(const ExtendableType& rhs) const {
    return std::tie(name, value) == std::tie(rhs.name, rhs.value);
  }
  inline bool operator>(const ExtendableType& rhs) const {
    return std::tie(name, value) > std::tie(rhs.name, rhs.value);
  }
  inline bool operator>=(const ExtendableType& rhs) const {
    return std::tie(name, value) >= std::tie(rhs.name, rhs.value);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ExtendableType{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", value: " << ::android::internal::ToString(value);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
