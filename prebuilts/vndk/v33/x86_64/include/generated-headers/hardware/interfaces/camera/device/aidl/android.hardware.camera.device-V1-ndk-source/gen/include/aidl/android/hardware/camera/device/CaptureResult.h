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
#include <aidl/android/hardware/camera/device/PhysicalCameraMetadata.h>
#include <aidl/android/hardware/camera/device/StreamBuffer.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class CaptureResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t frameNumber = 0;
  int64_t fmqResultSize = 0L;
  ::aidl::android::hardware::camera::device::CameraMetadata result;
  std::vector<::aidl::android::hardware::camera::device::StreamBuffer> outputBuffers;
  ::aidl::android::hardware::camera::device::StreamBuffer inputBuffer;
  int32_t partialResult = 0;
  std::vector<::aidl::android::hardware::camera::device::PhysicalCameraMetadata> physicalCameraMetadata;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) != std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }
  inline bool operator<(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) < std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }
  inline bool operator<=(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) <= std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }
  inline bool operator==(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) == std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }
  inline bool operator>(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) > std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }
  inline bool operator>=(const CaptureResult& rhs) const {
    return std::tie(frameNumber, fmqResultSize, result, outputBuffers, inputBuffer, partialResult, physicalCameraMetadata) >= std::tie(rhs.frameNumber, rhs.fmqResultSize, rhs.result, rhs.outputBuffers, rhs.inputBuffer, rhs.partialResult, rhs.physicalCameraMetadata);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CaptureResult{";
    os << "frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", fmqResultSize: " << ::android::internal::ToString(fmqResultSize);
    os << ", result: " << ::android::internal::ToString(result);
    os << ", outputBuffers: " << ::android::internal::ToString(outputBuffers);
    os << ", inputBuffer: " << ::android::internal::ToString(inputBuffer);
    os << ", partialResult: " << ::android::internal::ToString(partialResult);
    os << ", physicalCameraMetadata: " << ::android::internal::ToString(physicalCameraMetadata);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
