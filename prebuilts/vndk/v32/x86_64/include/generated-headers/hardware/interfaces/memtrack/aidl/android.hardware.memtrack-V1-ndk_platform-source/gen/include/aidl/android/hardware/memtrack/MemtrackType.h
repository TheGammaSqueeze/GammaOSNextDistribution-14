#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace memtrack {
enum class MemtrackType : int32_t {
  OTHER = 0,
  GL = 1,
  GRAPHICS = 2,
  MULTIMEDIA = 3,
  CAMERA = 4,
};

[[nodiscard]] static inline std::string toString(MemtrackType val) {
  switch(val) {
  case MemtrackType::OTHER:
    return "OTHER";
  case MemtrackType::GL:
    return "GL";
  case MemtrackType::GRAPHICS:
    return "GRAPHICS";
  case MemtrackType::MULTIMEDIA:
    return "MULTIMEDIA";
  case MemtrackType::CAMERA:
    return "CAMERA";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::memtrack::MemtrackType, 5> enum_values<aidl::android::hardware::memtrack::MemtrackType> = {
  aidl::android::hardware::memtrack::MemtrackType::OTHER,
  aidl::android::hardware::memtrack::MemtrackType::GL,
  aidl::android::hardware::memtrack::MemtrackType::GRAPHICS,
  aidl::android::hardware::memtrack::MemtrackType::MULTIMEDIA,
  aidl::android::hardware::memtrack::MemtrackType::CAMERA,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
