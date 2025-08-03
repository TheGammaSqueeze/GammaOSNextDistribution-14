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
enum class EmergencyCallRouting : int32_t {
  UNKNOWN = 0,
  EMERGENCY = 1,
  NORMAL = 2,
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
[[nodiscard]] static inline std::string toString(EmergencyCallRouting val) {
  switch(val) {
  case EmergencyCallRouting::UNKNOWN:
    return "UNKNOWN";
  case EmergencyCallRouting::EMERGENCY:
    return "EMERGENCY";
  case EmergencyCallRouting::NORMAL:
    return "NORMAL";
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
constexpr inline std::array<aidl::android::hardware::radio::voice::EmergencyCallRouting, 3> enum_values<aidl::android::hardware::radio::voice::EmergencyCallRouting> = {
  aidl::android::hardware::radio::voice::EmergencyCallRouting::UNKNOWN,
  aidl::android::hardware::radio::voice::EmergencyCallRouting::EMERGENCY,
  aidl::android::hardware::radio::voice::EmergencyCallRouting::NORMAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
