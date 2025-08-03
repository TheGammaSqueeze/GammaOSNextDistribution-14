#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/CameraBlobId.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class CameraBlob {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::camera::device::CameraBlobId blobId = ::aidl::android::hardware::camera::device::CameraBlobId(0);
  int32_t blobSizeBytes = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) != std::tie(rhs.blobId, rhs.blobSizeBytes);
  }
  inline bool operator<(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) < std::tie(rhs.blobId, rhs.blobSizeBytes);
  }
  inline bool operator<=(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) <= std::tie(rhs.blobId, rhs.blobSizeBytes);
  }
  inline bool operator==(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) == std::tie(rhs.blobId, rhs.blobSizeBytes);
  }
  inline bool operator>(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) > std::tie(rhs.blobId, rhs.blobSizeBytes);
  }
  inline bool operator>=(const CameraBlob& rhs) const {
    return std::tie(blobId, blobSizeBytes) >= std::tie(rhs.blobId, rhs.blobSizeBytes);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CameraBlob{";
    os << "blobId: " << ::android::internal::ToString(blobId);
    os << ", blobSizeBytes: " << ::android::internal::ToString(blobSizeBytes);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
