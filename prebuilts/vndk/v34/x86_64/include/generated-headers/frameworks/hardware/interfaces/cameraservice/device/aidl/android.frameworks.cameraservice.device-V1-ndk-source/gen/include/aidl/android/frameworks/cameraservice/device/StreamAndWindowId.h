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
namespace frameworks {
namespace cameraservice {
namespace device {
class StreamAndWindowId {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t streamId = 0;
  int32_t windowId = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) != std::tie(rhs.streamId, rhs.windowId);
  }
  inline bool operator<(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) < std::tie(rhs.streamId, rhs.windowId);
  }
  inline bool operator<=(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) <= std::tie(rhs.streamId, rhs.windowId);
  }
  inline bool operator==(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) == std::tie(rhs.streamId, rhs.windowId);
  }
  inline bool operator>(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) > std::tie(rhs.streamId, rhs.windowId);
  }
  inline bool operator>=(const StreamAndWindowId& rhs) const {
    return std::tie(streamId, windowId) >= std::tie(rhs.streamId, rhs.windowId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StreamAndWindowId{";
    os << "streamId: " << ::android::internal::ToString(streamId);
    os << ", windowId: " << ::android::internal::ToString(windowId);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
