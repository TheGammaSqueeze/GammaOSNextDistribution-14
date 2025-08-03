#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class Buffer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t slot = 0;
  std::optional<::aidl::android::hardware::common::NativeHandle> handle;
  ::ndk::ScopedFileDescriptor fence;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) != std::tie(rhs.slot, rhs.handle, rhs.fence);
  }
  inline bool operator<(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) < std::tie(rhs.slot, rhs.handle, rhs.fence);
  }
  inline bool operator<=(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) <= std::tie(rhs.slot, rhs.handle, rhs.fence);
  }
  inline bool operator==(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) == std::tie(rhs.slot, rhs.handle, rhs.fence);
  }
  inline bool operator>(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) > std::tie(rhs.slot, rhs.handle, rhs.fence);
  }
  inline bool operator>=(const Buffer& rhs) const {
    return std::tie(slot, handle, fence) >= std::tie(rhs.slot, rhs.handle, rhs.fence);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Buffer{";
    os << "slot: " << ::android::internal::ToString(slot);
    os << ", handle: " << ::android::internal::ToString(handle);
    os << ", fence: " << ::android::internal::ToString(fence);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
