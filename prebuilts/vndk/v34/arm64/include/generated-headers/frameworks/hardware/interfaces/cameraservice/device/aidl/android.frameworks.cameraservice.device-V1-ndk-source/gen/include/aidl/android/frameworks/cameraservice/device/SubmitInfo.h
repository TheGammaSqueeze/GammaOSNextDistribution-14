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
class SubmitInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t requestId = 0;
  int64_t lastFrameNumber = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) != std::tie(rhs.requestId, rhs.lastFrameNumber);
  }
  inline bool operator<(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) < std::tie(rhs.requestId, rhs.lastFrameNumber);
  }
  inline bool operator<=(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) <= std::tie(rhs.requestId, rhs.lastFrameNumber);
  }
  inline bool operator==(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) == std::tie(rhs.requestId, rhs.lastFrameNumber);
  }
  inline bool operator>(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) > std::tie(rhs.requestId, rhs.lastFrameNumber);
  }
  inline bool operator>=(const SubmitInfo& rhs) const {
    return std::tie(requestId, lastFrameNumber) >= std::tie(rhs.requestId, rhs.lastFrameNumber);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SubmitInfo{";
    os << "requestId: " << ::android::internal::ToString(requestId);
    os << ", lastFrameNumber: " << ::android::internal::ToString(lastFrameNumber);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
