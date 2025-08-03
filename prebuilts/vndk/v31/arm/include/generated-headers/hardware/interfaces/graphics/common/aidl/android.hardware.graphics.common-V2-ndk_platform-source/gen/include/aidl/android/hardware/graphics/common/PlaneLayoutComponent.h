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
#include <aidl/android/hardware/graphics/common/ExtendableType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class PlaneLayoutComponent {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::ExtendableType type;
  int64_t offsetInBits = 0L;
  int64_t sizeInBits = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) != std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }
  inline bool operator<(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) < std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }
  inline bool operator<=(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) <= std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }
  inline bool operator==(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) == std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }
  inline bool operator>(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) > std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }
  inline bool operator>=(const PlaneLayoutComponent& rhs) const {
    return std::tie(type, offsetInBits, sizeInBits) >= std::tie(rhs.type, rhs.offsetInBits, rhs.sizeInBits);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PlaneLayoutComponent{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", offsetInBits: " << ::android::internal::ToString(offsetInBits);
    os << ", sizeInBits: " << ::android::internal::ToString(sizeInBits);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
