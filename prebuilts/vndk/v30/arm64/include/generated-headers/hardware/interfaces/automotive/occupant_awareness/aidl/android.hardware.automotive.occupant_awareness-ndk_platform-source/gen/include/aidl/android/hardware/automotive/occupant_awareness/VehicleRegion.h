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
namespace automotive {
namespace occupant_awareness {
enum class VehicleRegion : int32_t {
  UNKNOWN = 0,
  INSTRUMENT_CLUSTER = 1,
  REAR_VIEW_MIRROR = 2,
  LEFT_SIDE_MIRROR = 3,
  RIGHT_SIDE_MIRROR = 4,
  FORWARD_ROADWAY = 5,
  LEFT_ROADWAY = 6,
  RIGHT_ROADWAY = 7,
  HEAD_UNIT_DISPLAY = 8,
  CUSTOM_TARGET = 200,
};

static inline std::string toString(VehicleRegion val) {
  switch(val) {
  case VehicleRegion::UNKNOWN:
    return "UNKNOWN";
  case VehicleRegion::INSTRUMENT_CLUSTER:
    return "INSTRUMENT_CLUSTER";
  case VehicleRegion::REAR_VIEW_MIRROR:
    return "REAR_VIEW_MIRROR";
  case VehicleRegion::LEFT_SIDE_MIRROR:
    return "LEFT_SIDE_MIRROR";
  case VehicleRegion::RIGHT_SIDE_MIRROR:
    return "RIGHT_SIDE_MIRROR";
  case VehicleRegion::FORWARD_ROADWAY:
    return "FORWARD_ROADWAY";
  case VehicleRegion::LEFT_ROADWAY:
    return "LEFT_ROADWAY";
  case VehicleRegion::RIGHT_ROADWAY:
    return "RIGHT_ROADWAY";
  case VehicleRegion::HEAD_UNIT_DISPLAY:
    return "HEAD_UNIT_DISPLAY";
  case VehicleRegion::CUSTOM_TARGET:
    return "CUSTOM_TARGET";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::automotive::occupant_awareness::VehicleRegion, 10> enum_values<aidl::android::hardware::automotive::occupant_awareness::VehicleRegion> = {
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::UNKNOWN,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::INSTRUMENT_CLUSTER,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::REAR_VIEW_MIRROR,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::LEFT_SIDE_MIRROR,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::RIGHT_SIDE_MIRROR,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::FORWARD_ROADWAY,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::LEFT_ROADWAY,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::RIGHT_ROADWAY,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::HEAD_UNIT_DISPLAY,
  aidl::android::hardware::automotive::occupant_awareness::VehicleRegion::CUSTOM_TARGET,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
