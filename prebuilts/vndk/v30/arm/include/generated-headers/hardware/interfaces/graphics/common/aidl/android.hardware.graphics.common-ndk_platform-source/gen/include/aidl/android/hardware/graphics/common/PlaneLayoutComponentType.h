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
namespace graphics {
namespace common {
enum class PlaneLayoutComponentType : int64_t {
  Y = 1L,
  CB = 2L,
  CR = 4L,
  R = 1024L,
  G = 2048L,
  B = 4096L,
  RAW = 1048576L,
  A = 1073741824L,
};

static inline std::string toString(PlaneLayoutComponentType val) {
  switch(val) {
  case PlaneLayoutComponentType::Y:
    return "Y";
  case PlaneLayoutComponentType::CB:
    return "CB";
  case PlaneLayoutComponentType::CR:
    return "CR";
  case PlaneLayoutComponentType::R:
    return "R";
  case PlaneLayoutComponentType::G:
    return "G";
  case PlaneLayoutComponentType::B:
    return "B";
  case PlaneLayoutComponentType::RAW:
    return "RAW";
  case PlaneLayoutComponentType::A:
    return "A";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::common::PlaneLayoutComponentType, 8> enum_values<aidl::android::hardware::graphics::common::PlaneLayoutComponentType> = {
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::Y,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::CB,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::CR,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::R,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::G,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::B,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::RAW,
  aidl::android::hardware::graphics::common::PlaneLayoutComponentType::A,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
