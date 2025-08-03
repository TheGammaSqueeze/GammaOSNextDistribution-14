#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
enum class StreamBufferRequestError : int32_t {
  NO_BUFFER_AVAILABLE = 1,
  MAX_BUFFER_EXCEEDED = 2,
  STREAM_DISCONNECTED = 3,
  UNKNOWN_ERROR = 4,
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
[[nodiscard]] static inline std::string toString(StreamBufferRequestError val) {
  switch(val) {
  case StreamBufferRequestError::NO_BUFFER_AVAILABLE:
    return "NO_BUFFER_AVAILABLE";
  case StreamBufferRequestError::MAX_BUFFER_EXCEEDED:
    return "MAX_BUFFER_EXCEEDED";
  case StreamBufferRequestError::STREAM_DISCONNECTED:
    return "STREAM_DISCONNECTED";
  case StreamBufferRequestError::UNKNOWN_ERROR:
    return "UNKNOWN_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::device::StreamBufferRequestError, 4> enum_values<aidl::android::hardware::camera::device::StreamBufferRequestError> = {
  aidl::android::hardware::camera::device::StreamBufferRequestError::NO_BUFFER_AVAILABLE,
  aidl::android::hardware::camera::device::StreamBufferRequestError::MAX_BUFFER_EXCEEDED,
  aidl::android::hardware::camera::device::StreamBufferRequestError::STREAM_DISCONNECTED,
  aidl::android::hardware::camera::device::StreamBufferRequestError::UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
