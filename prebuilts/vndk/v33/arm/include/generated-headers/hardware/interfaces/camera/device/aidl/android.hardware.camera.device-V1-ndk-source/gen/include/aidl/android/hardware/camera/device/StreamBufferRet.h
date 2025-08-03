#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/StreamBuffersVal.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class StreamBufferRet {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t streamId = 0;
  ::aidl::android::hardware::camera::device::StreamBuffersVal val;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) != std::tie(rhs.streamId, rhs.val);
  }
  inline bool operator<(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) < std::tie(rhs.streamId, rhs.val);
  }
  inline bool operator<=(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) <= std::tie(rhs.streamId, rhs.val);
  }
  inline bool operator==(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) == std::tie(rhs.streamId, rhs.val);
  }
  inline bool operator>(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) > std::tie(rhs.streamId, rhs.val);
  }
  inline bool operator>=(const StreamBufferRet& rhs) const {
    return std::tie(streamId, val) >= std::tie(rhs.streamId, rhs.val);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StreamBufferRet{";
    os << "streamId: " << ::android::internal::ToString(streamId);
    os << ", val: " << ::android::internal::ToString(val);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
