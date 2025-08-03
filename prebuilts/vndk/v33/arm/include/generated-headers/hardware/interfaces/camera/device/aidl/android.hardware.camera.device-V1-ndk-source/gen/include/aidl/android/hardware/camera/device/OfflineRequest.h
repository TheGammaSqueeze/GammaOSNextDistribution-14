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
namespace hardware {
namespace camera {
namespace device {
class OfflineRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t frameNumber = 0;
  std::vector<int32_t> pendingStreams;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) != std::tie(rhs.frameNumber, rhs.pendingStreams);
  }
  inline bool operator<(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) < std::tie(rhs.frameNumber, rhs.pendingStreams);
  }
  inline bool operator<=(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) <= std::tie(rhs.frameNumber, rhs.pendingStreams);
  }
  inline bool operator==(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) == std::tie(rhs.frameNumber, rhs.pendingStreams);
  }
  inline bool operator>(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) > std::tie(rhs.frameNumber, rhs.pendingStreams);
  }
  inline bool operator>=(const OfflineRequest& rhs) const {
    return std::tie(frameNumber, pendingStreams) >= std::tie(rhs.frameNumber, rhs.pendingStreams);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OfflineRequest{";
    os << "frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", pendingStreams: " << ::android::internal::ToString(pendingStreams);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
