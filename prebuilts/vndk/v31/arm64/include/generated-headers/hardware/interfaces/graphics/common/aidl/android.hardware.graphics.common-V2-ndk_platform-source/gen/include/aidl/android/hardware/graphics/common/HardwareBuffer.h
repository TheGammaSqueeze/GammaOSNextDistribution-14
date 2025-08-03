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
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/graphics/common/HardwareBufferDescription.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class HardwareBuffer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::HardwareBufferDescription description;
  ::aidl::android::hardware::common::NativeHandle handle;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) != std::tie(rhs.description, rhs.handle);
  }
  inline bool operator<(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) < std::tie(rhs.description, rhs.handle);
  }
  inline bool operator<=(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) <= std::tie(rhs.description, rhs.handle);
  }
  inline bool operator==(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) == std::tie(rhs.description, rhs.handle);
  }
  inline bool operator>(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) > std::tie(rhs.description, rhs.handle);
  }
  inline bool operator>=(const HardwareBuffer& rhs) const {
    return std::tie(description, handle) >= std::tie(rhs.description, rhs.handle);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HardwareBuffer{";
    os << "description: " << ::android::internal::ToString(description);
    os << ", handle: " << ::android::internal::ToString(handle);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
