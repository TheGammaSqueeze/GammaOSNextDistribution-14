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
enum class OccupantAwarenessStatus : int8_t {
  READY = 0,
  NOT_SUPPORTED = 1,
  NOT_INITIALIZED = 2,
  FAILURE = 3,
};

static inline std::string toString(OccupantAwarenessStatus val) {
  switch(val) {
  case OccupantAwarenessStatus::READY:
    return "READY";
  case OccupantAwarenessStatus::NOT_SUPPORTED:
    return "NOT_SUPPORTED";
  case OccupantAwarenessStatus::NOT_INITIALIZED:
    return "NOT_INITIALIZED";
  case OccupantAwarenessStatus::FAILURE:
    return "FAILURE";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus, 4> enum_values<aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus> = {
  aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus::READY,
  aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus::NOT_SUPPORTED,
  aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus::NOT_INITIALIZED,
  aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus::FAILURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
