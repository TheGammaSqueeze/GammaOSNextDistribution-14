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
namespace drm {
class SharedBuffer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t bufferId = 0;
  int64_t offset = 0L;
  int64_t size = 0L;
  ::aidl::android::hardware::common::NativeHandle handle;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) != std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }
  inline bool operator<(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) < std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }
  inline bool operator<=(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) <= std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }
  inline bool operator==(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) == std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }
  inline bool operator>(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) > std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }
  inline bool operator>=(const SharedBuffer& rhs) const {
    return std::tie(bufferId, offset, size, handle) >= std::tie(rhs.bufferId, rhs.offset, rhs.size, rhs.handle);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SharedBuffer{";
    os << "bufferId: " << ::android::internal::ToString(bufferId);
    os << ", offset: " << ::android::internal::ToString(offset);
    os << ", size: " << ::android::internal::ToString(size);
    os << ", handle: " << ::android::internal::ToString(handle);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
