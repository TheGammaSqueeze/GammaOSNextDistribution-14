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
namespace radio {
namespace voice {
enum class EmergencyServiceCategory : int32_t {
  UNSPECIFIED = 0,
  POLICE = 1,
  AMBULANCE = 2,
  FIRE_BRIGADE = 4,
  MARINE_GUARD = 8,
  MOUNTAIN_RESCUE = 16,
  MIEC = 32,
  AIEC = 64,
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
[[nodiscard]] static inline std::string toString(EmergencyServiceCategory val) {
  switch(val) {
  case EmergencyServiceCategory::UNSPECIFIED:
    return "UNSPECIFIED";
  case EmergencyServiceCategory::POLICE:
    return "POLICE";
  case EmergencyServiceCategory::AMBULANCE:
    return "AMBULANCE";
  case EmergencyServiceCategory::FIRE_BRIGADE:
    return "FIRE_BRIGADE";
  case EmergencyServiceCategory::MARINE_GUARD:
    return "MARINE_GUARD";
  case EmergencyServiceCategory::MOUNTAIN_RESCUE:
    return "MOUNTAIN_RESCUE";
  case EmergencyServiceCategory::MIEC:
    return "MIEC";
  case EmergencyServiceCategory::AIEC:
    return "AIEC";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::voice::EmergencyServiceCategory, 8> enum_values<aidl::android::hardware::radio::voice::EmergencyServiceCategory> = {
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::UNSPECIFIED,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::POLICE,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::AMBULANCE,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::FIRE_BRIGADE,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::MARINE_GUARD,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::MOUNTAIN_RESCUE,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::MIEC,
  aidl::android::hardware::radio::voice::EmergencyServiceCategory::AIEC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
