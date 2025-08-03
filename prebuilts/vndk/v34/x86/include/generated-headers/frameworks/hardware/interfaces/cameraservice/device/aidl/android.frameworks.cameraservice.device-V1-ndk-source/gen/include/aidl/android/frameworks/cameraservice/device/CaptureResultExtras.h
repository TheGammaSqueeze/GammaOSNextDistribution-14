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
class CaptureResultExtras {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t requestId = 0;
  int32_t burstId = 0;
  int64_t frameNumber = 0L;
  int32_t partialResultCount = 0;
  int32_t errorStreamId = 0;
  std::string errorPhysicalCameraId;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) != std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }
  inline bool operator<(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) < std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }
  inline bool operator<=(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) <= std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }
  inline bool operator==(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) == std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }
  inline bool operator>(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) > std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }
  inline bool operator>=(const CaptureResultExtras& rhs) const {
    return std::tie(requestId, burstId, frameNumber, partialResultCount, errorStreamId, errorPhysicalCameraId) >= std::tie(rhs.requestId, rhs.burstId, rhs.frameNumber, rhs.partialResultCount, rhs.errorStreamId, rhs.errorPhysicalCameraId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CaptureResultExtras{";
    os << "requestId: " << ::android::internal::ToString(requestId);
    os << ", burstId: " << ::android::internal::ToString(burstId);
    os << ", frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", partialResultCount: " << ::android::internal::ToString(partialResultCount);
    os << ", errorStreamId: " << ::android::internal::ToString(errorStreamId);
    os << ", errorPhysicalCameraId: " << ::android::internal::ToString(errorPhysicalCameraId);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
