#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/BufferStatus.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class StreamBuffer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t streamId = 0;
  int64_t bufferId = 0L;
  ::aidl::android::hardware::common::NativeHandle buffer;
  ::aidl::android::hardware::camera::device::BufferStatus status = ::aidl::android::hardware::camera::device::BufferStatus(0);
  ::aidl::android::hardware::common::NativeHandle acquireFence;
  ::aidl::android::hardware::common::NativeHandle releaseFence;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) != std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }
  inline bool operator<(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) < std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }
  inline bool operator<=(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) <= std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }
  inline bool operator==(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) == std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }
  inline bool operator>(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) > std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }
  inline bool operator>=(const StreamBuffer& rhs) const {
    return std::tie(streamId, bufferId, buffer, status, acquireFence, releaseFence) >= std::tie(rhs.streamId, rhs.bufferId, rhs.buffer, rhs.status, rhs.acquireFence, rhs.releaseFence);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StreamBuffer{";
    os << "streamId: " << ::android::internal::ToString(streamId);
    os << ", bufferId: " << ::android::internal::ToString(bufferId);
    os << ", buffer: " << ::android::internal::ToString(buffer);
    os << ", status: " << ::android::internal::ToString(status);
    os << ", acquireFence: " << ::android::internal::ToString(acquireFence);
    os << ", releaseFence: " << ::android::internal::ToString(releaseFence);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
