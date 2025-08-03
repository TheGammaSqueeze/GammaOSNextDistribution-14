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
enum class StreamType : int32_t {
  OUTPUT = 0,
  INPUT = 1,
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
[[nodiscard]] static inline std::string toString(StreamType val) {
  switch(val) {
  case StreamType::OUTPUT:
    return "OUTPUT";
  case StreamType::INPUT:
    return "INPUT";
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
constexpr inline std::array<aidl::android::hardware::camera::device::StreamType, 2> enum_values<aidl::android::hardware::camera::device::StreamType> = {
  aidl::android::hardware::camera::device::StreamType::OUTPUT,
  aidl::android::hardware::camera::device::StreamType::INPUT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
