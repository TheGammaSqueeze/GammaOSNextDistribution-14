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
enum class Role : int32_t {
  INVALID = 0,
  UNKNOWN = 1,
  FRONT_PASSENGER = 2,
  DRIVER = 4,
  ROW_2_PASSENGER_LEFT = 8,
  ROW_2_PASSENGER_CENTER = 16,
  ROW_2_PASSENGER_RIGHT = 32,
  ROW_3_PASSENGER_LEFT = 64,
  ROW_3_PASSENGER_CENTER = 128,
  ROW_3_PASSENGER_RIGHT = 256,
  FRONT_OCCUPANTS = 6,
  ROW_2_OCCUPANTS = 56,
  ROW_3_OCCUPANTS = 448,
  ALL_OCCUPANTS = 511,
};

static inline std::string toString(Role val) {
  switch(val) {
  case Role::INVALID:
    return "INVALID";
  case Role::UNKNOWN:
    return "UNKNOWN";
  case Role::FRONT_PASSENGER:
    return "FRONT_PASSENGER";
  case Role::DRIVER:
    return "DRIVER";
  case Role::ROW_2_PASSENGER_LEFT:
    return "ROW_2_PASSENGER_LEFT";
  case Role::ROW_2_PASSENGER_CENTER:
    return "ROW_2_PASSENGER_CENTER";
  case Role::ROW_2_PASSENGER_RIGHT:
    return "ROW_2_PASSENGER_RIGHT";
  case Role::ROW_3_PASSENGER_LEFT:
    return "ROW_3_PASSENGER_LEFT";
  case Role::ROW_3_PASSENGER_CENTER:
    return "ROW_3_PASSENGER_CENTER";
  case Role::ROW_3_PASSENGER_RIGHT:
    return "ROW_3_PASSENGER_RIGHT";
  case Role::FRONT_OCCUPANTS:
    return "FRONT_OCCUPANTS";
  case Role::ROW_2_OCCUPANTS:
    return "ROW_2_OCCUPANTS";
  case Role::ROW_3_OCCUPANTS:
    return "ROW_3_OCCUPANTS";
  case Role::ALL_OCCUPANTS:
    return "ALL_OCCUPANTS";
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
constexpr inline std::array<aidl::android::hardware::automotive::occupant_awareness::Role, 14> enum_values<aidl::android::hardware::automotive::occupant_awareness::Role> = {
  aidl::android::hardware::automotive::occupant_awareness::Role::INVALID,
  aidl::android::hardware::automotive::occupant_awareness::Role::UNKNOWN,
  aidl::android::hardware::automotive::occupant_awareness::Role::FRONT_PASSENGER,
  aidl::android::hardware::automotive::occupant_awareness::Role::DRIVER,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_2_PASSENGER_LEFT,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_2_PASSENGER_CENTER,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_2_PASSENGER_RIGHT,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_3_PASSENGER_LEFT,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_3_PASSENGER_CENTER,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_3_PASSENGER_RIGHT,
  aidl::android::hardware::automotive::occupant_awareness::Role::FRONT_OCCUPANTS,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_2_OCCUPANTS,
  aidl::android::hardware::automotive::occupant_awareness::Role::ROW_3_OCCUPANTS,
  aidl::android::hardware::automotive::occupant_awareness::Role::ALL_OCCUPANTS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
