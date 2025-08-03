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
enum class ScalerAvailableRecommendedStreamConfigurations : int32_t {
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_PREVIEW = 0,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RECORD = 1,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VIDEO_SNAPSHOT = 2,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_SNAPSHOT = 3,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_ZSL = 4,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RAW = 5,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_LOW_LATENCY_SNAPSHOT = 6,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_10BIT_OUTPUT = 8,
  ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VENDOR_START = 24,
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
[[nodiscard]] static inline std::string toString(ScalerAvailableRecommendedStreamConfigurations val) {
  switch(val) {
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_PREVIEW:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_PREVIEW";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RECORD:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RECORD";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VIDEO_SNAPSHOT:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VIDEO_SNAPSHOT";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_SNAPSHOT:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_SNAPSHOT";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_ZSL:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_ZSL";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RAW:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RAW";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_LOW_LATENCY_SNAPSHOT:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_LOW_LATENCY_SNAPSHOT";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_10BIT_OUTPUT:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_10BIT_OUTPUT";
  case ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VENDOR_START:
    return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VENDOR_START";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations, 9> enum_values<aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations> = {
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_PREVIEW,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RECORD,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VIDEO_SNAPSHOT,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_SNAPSHOT,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_ZSL,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_RAW,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_LOW_LATENCY_SNAPSHOT,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_10BIT_OUTPUT,
  aidl::android::hardware::camera::metadata::ScalerAvailableRecommendedStreamConfigurations::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS_VENDOR_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
