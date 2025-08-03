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
namespace sim {
enum class SimLockMultiSimPolicy : int32_t {
  NO_MULTISIM_POLICY = 0,
  ONE_VALID_SIM_MUST_BE_PRESENT = 1,
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
[[nodiscard]] static inline std::string toString(SimLockMultiSimPolicy val) {
  switch(val) {
  case SimLockMultiSimPolicy::NO_MULTISIM_POLICY:
    return "NO_MULTISIM_POLICY";
  case SimLockMultiSimPolicy::ONE_VALID_SIM_MUST_BE_PRESENT:
    return "ONE_VALID_SIM_MUST_BE_PRESENT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::sim::SimLockMultiSimPolicy, 2> enum_values<aidl::android::hardware::radio::sim::SimLockMultiSimPolicy> = {
  aidl::android::hardware::radio::sim::SimLockMultiSimPolicy::NO_MULTISIM_POLICY,
  aidl::android::hardware::radio::sim::SimLockMultiSimPolicy::ONE_VALID_SIM_MUST_BE_PRESENT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
