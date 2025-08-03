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
enum class CdmaSubscriptionSource : int32_t {
  RUIM_SIM = 0,
  NV = 1,
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
[[nodiscard]] static inline std::string toString(CdmaSubscriptionSource val) {
  switch(val) {
  case CdmaSubscriptionSource::RUIM_SIM:
    return "RUIM_SIM";
  case CdmaSubscriptionSource::NV:
    return "NV";
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
constexpr inline std::array<aidl::android::hardware::radio::sim::CdmaSubscriptionSource, 2> enum_values<aidl::android::hardware::radio::sim::CdmaSubscriptionSource> = {
  aidl::android::hardware::radio::sim::CdmaSubscriptionSource::RUIM_SIM,
  aidl::android::hardware::radio::sim::CdmaSubscriptionSource::NV,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
