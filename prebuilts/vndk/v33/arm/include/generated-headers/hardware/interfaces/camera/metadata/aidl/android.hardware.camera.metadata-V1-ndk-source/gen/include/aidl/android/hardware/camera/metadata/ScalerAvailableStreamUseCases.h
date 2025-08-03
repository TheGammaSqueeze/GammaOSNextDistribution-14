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
namespace metadata {
enum class ScalerAvailableStreamUseCases : int64_t {
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT = 0L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW = 1L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE = 2L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD = 3L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL = 4L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL = 5L,
  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START = 65536L,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(ScalerAvailableStreamUseCases val) {
  switch(val) {
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL";
  case ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START:
    return "ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases, 7> enum_values<aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases> = {
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL,
  aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases::ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
