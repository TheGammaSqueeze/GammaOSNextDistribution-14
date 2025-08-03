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
namespace allocator {
class AllocationResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t stride = 0;
  std::vector<::aidl::android::hardware::common::NativeHandle> buffers;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) != std::tie(rhs.stride, rhs.buffers);
  }
  inline bool operator<(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) < std::tie(rhs.stride, rhs.buffers);
  }
  inline bool operator<=(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) <= std::tie(rhs.stride, rhs.buffers);
  }
  inline bool operator==(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) == std::tie(rhs.stride, rhs.buffers);
  }
  inline bool operator>(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) > std::tie(rhs.stride, rhs.buffers);
  }
  inline bool operator>=(const AllocationResult& rhs) const {
    return std::tie(stride, buffers) >= std::tie(rhs.stride, rhs.buffers);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AllocationResult{";
    os << "stride: " << ::android::internal::ToString(stride);
    os << ", buffers: " << ::android::internal::ToString(buffers);
    os << "}";
    return os.str();
  }
};
}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
