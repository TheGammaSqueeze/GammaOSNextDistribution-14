#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#include <aidl/android/hardware/camera/device/Stream.h>
#include <aidl/android/hardware/camera/device/StreamConfigurationMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class StreamConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::camera::device::Stream> streams;
  ::aidl::android::hardware::camera::device::StreamConfigurationMode operationMode = ::aidl::android::hardware::camera::device::StreamConfigurationMode(0);
  ::aidl::android::hardware::camera::device::CameraMetadata sessionParams;
  int32_t streamConfigCounter = 0;
  bool multiResolutionInputImage = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) != std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }
  inline bool operator<(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) < std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }
  inline bool operator<=(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) <= std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }
  inline bool operator==(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) == std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }
  inline bool operator>(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) > std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }
  inline bool operator>=(const StreamConfiguration& rhs) const {
    return std::tie(streams, operationMode, sessionParams, streamConfigCounter, multiResolutionInputImage) >= std::tie(rhs.streams, rhs.operationMode, rhs.sessionParams, rhs.streamConfigCounter, rhs.multiResolutionInputImage);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StreamConfiguration{";
    os << "streams: " << ::android::internal::ToString(streams);
    os << ", operationMode: " << ::android::internal::ToString(operationMode);
    os << ", sessionParams: " << ::android::internal::ToString(sessionParams);
    os << ", streamConfigCounter: " << ::android::internal::ToString(streamConfigCounter);
    os << ", multiResolutionInputImage: " << ::android::internal::ToString(multiResolutionInputImage);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
