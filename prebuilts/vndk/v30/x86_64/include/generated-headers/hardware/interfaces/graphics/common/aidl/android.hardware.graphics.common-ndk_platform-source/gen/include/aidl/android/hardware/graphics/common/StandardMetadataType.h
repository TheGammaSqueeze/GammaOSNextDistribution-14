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
enum class StandardMetadataType : int64_t {
  INVALID = 0L,
  BUFFER_ID = 1L,
  NAME = 2L,
  WIDTH = 3L,
  HEIGHT = 4L,
  LAYER_COUNT = 5L,
  PIXEL_FORMAT_REQUESTED = 6L,
  PIXEL_FORMAT_FOURCC = 7L,
  PIXEL_FORMAT_MODIFIER = 8L,
  USAGE = 9L,
  ALLOCATION_SIZE = 10L,
  PROTECTED_CONTENT = 11L,
  COMPRESSION = 12L,
  INTERLACED = 13L,
  CHROMA_SITING = 14L,
  PLANE_LAYOUTS = 15L,
  CROP = 16L,
  DATASPACE = 17L,
  BLEND_MODE = 18L,
  SMPTE2086 = 19L,
  CTA861_3 = 20L,
  SMPTE2094_40 = 21L,
};

static inline std::string toString(StandardMetadataType val) {
  switch(val) {
  case StandardMetadataType::INVALID:
    return "INVALID";
  case StandardMetadataType::BUFFER_ID:
    return "BUFFER_ID";
  case StandardMetadataType::NAME:
    return "NAME";
  case StandardMetadataType::WIDTH:
    return "WIDTH";
  case StandardMetadataType::HEIGHT:
    return "HEIGHT";
  case StandardMetadataType::LAYER_COUNT:
    return "LAYER_COUNT";
  case StandardMetadataType::PIXEL_FORMAT_REQUESTED:
    return "PIXEL_FORMAT_REQUESTED";
  case StandardMetadataType::PIXEL_FORMAT_FOURCC:
    return "PIXEL_FORMAT_FOURCC";
  case StandardMetadataType::PIXEL_FORMAT_MODIFIER:
    return "PIXEL_FORMAT_MODIFIER";
  case StandardMetadataType::USAGE:
    return "USAGE";
  case StandardMetadataType::ALLOCATION_SIZE:
    return "ALLOCATION_SIZE";
  case StandardMetadataType::PROTECTED_CONTENT:
    return "PROTECTED_CONTENT";
  case StandardMetadataType::COMPRESSION:
    return "COMPRESSION";
  case StandardMetadataType::INTERLACED:
    return "INTERLACED";
  case StandardMetadataType::CHROMA_SITING:
    return "CHROMA_SITING";
  case StandardMetadataType::PLANE_LAYOUTS:
    return "PLANE_LAYOUTS";
  case StandardMetadataType::CROP:
    return "CROP";
  case StandardMetadataType::DATASPACE:
    return "DATASPACE";
  case StandardMetadataType::BLEND_MODE:
    return "BLEND_MODE";
  case StandardMetadataType::SMPTE2086:
    return "SMPTE2086";
  case StandardMetadataType::CTA861_3:
    return "CTA861_3";
  case StandardMetadataType::SMPTE2094_40:
    return "SMPTE2094_40";
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
constexpr inline std::array<aidl::android::hardware::graphics::common::StandardMetadataType, 22> enum_values<aidl::android::hardware::graphics::common::StandardMetadataType> = {
  aidl::android::hardware::graphics::common::StandardMetadataType::INVALID,
  aidl::android::hardware::graphics::common::StandardMetadataType::BUFFER_ID,
  aidl::android::hardware::graphics::common::StandardMetadataType::NAME,
  aidl::android::hardware::graphics::common::StandardMetadataType::WIDTH,
  aidl::android::hardware::graphics::common::StandardMetadataType::HEIGHT,
  aidl::android::hardware::graphics::common::StandardMetadataType::LAYER_COUNT,
  aidl::android::hardware::graphics::common::StandardMetadataType::PIXEL_FORMAT_REQUESTED,
  aidl::android::hardware::graphics::common::StandardMetadataType::PIXEL_FORMAT_FOURCC,
  aidl::android::hardware::graphics::common::StandardMetadataType::PIXEL_FORMAT_MODIFIER,
  aidl::android::hardware::graphics::common::StandardMetadataType::USAGE,
  aidl::android::hardware::graphics::common::StandardMetadataType::ALLOCATION_SIZE,
  aidl::android::hardware::graphics::common::StandardMetadataType::PROTECTED_CONTENT,
  aidl::android::hardware::graphics::common::StandardMetadataType::COMPRESSION,
  aidl::android::hardware::graphics::common::StandardMetadataType::INTERLACED,
  aidl::android::hardware::graphics::common::StandardMetadataType::CHROMA_SITING,
  aidl::android::hardware::graphics::common::StandardMetadataType::PLANE_LAYOUTS,
  aidl::android::hardware::graphics::common::StandardMetadataType::CROP,
  aidl::android::hardware::graphics::common::StandardMetadataType::DATASPACE,
  aidl::android::hardware::graphics::common::StandardMetadataType::BLEND_MODE,
  aidl::android::hardware::graphics::common::StandardMetadataType::SMPTE2086,
  aidl::android::hardware::graphics::common::StandardMetadataType::CTA861_3,
  aidl::android::hardware::graphics::common::StandardMetadataType::SMPTE2094_40,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
