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
#include <aidl/android/hardware/camera/device/PhysicalCameraSetting.h>
#include <aidl/android/hardware/camera/device/StreamBuffer.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class CaptureRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t frameNumber = 0;
  int64_t fmqSettingsSize = 0L;
  ::aidl::android::hardware::camera::device::CameraMetadata settings;
  ::aidl::android::hardware::camera::device::StreamBuffer inputBuffer;
  int32_t inputWidth = 0;
  int32_t inputHeight = 0;
  std::vector<::aidl::android::hardware::camera::device::StreamBuffer> outputBuffers;
  std::vector<::aidl::android::hardware::camera::device::PhysicalCameraSetting> physicalCameraSettings;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) != std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }
  inline bool operator<(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) < std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }
  inline bool operator<=(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) <= std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }
  inline bool operator==(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) == std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }
  inline bool operator>(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) > std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }
  inline bool operator>=(const CaptureRequest& rhs) const {
    return std::tie(frameNumber, fmqSettingsSize, settings, inputBuffer, inputWidth, inputHeight, outputBuffers, physicalCameraSettings) >= std::tie(rhs.frameNumber, rhs.fmqSettingsSize, rhs.settings, rhs.inputBuffer, rhs.inputWidth, rhs.inputHeight, rhs.outputBuffers, rhs.physicalCameraSettings);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CaptureRequest{";
    os << "frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", fmqSettingsSize: " << ::android::internal::ToString(fmqSettingsSize);
    os << ", settings: " << ::android::internal::ToString(settings);
    os << ", inputBuffer: " << ::android::internal::ToString(inputBuffer);
    os << ", inputWidth: " << ::android::internal::ToString(inputWidth);
    os << ", inputHeight: " << ::android::internal::ToString(inputHeight);
    os << ", outputBuffers: " << ::android::internal::ToString(outputBuffers);
    os << ", physicalCameraSettings: " << ::android::internal::ToString(physicalCameraSettings);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
