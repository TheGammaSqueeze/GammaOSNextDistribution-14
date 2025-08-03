#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/ErrorCode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class ErrorMsg {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t frameNumber = 0;
  int32_t errorStreamId = 0;
  ::aidl::android::hardware::camera::device::ErrorCode errorCode = ::aidl::android::hardware::camera::device::ErrorCode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) != std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }
  inline bool operator<(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) < std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }
  inline bool operator<=(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) <= std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }
  inline bool operator==(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) == std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }
  inline bool operator>(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) > std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }
  inline bool operator>=(const ErrorMsg& rhs) const {
    return std::tie(frameNumber, errorStreamId, errorCode) >= std::tie(rhs.frameNumber, rhs.errorStreamId, rhs.errorCode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ErrorMsg{";
    os << "frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", errorStreamId: " << ::android::internal::ToString(errorStreamId);
    os << ", errorCode: " << ::android::internal::ToString(errorCode);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
