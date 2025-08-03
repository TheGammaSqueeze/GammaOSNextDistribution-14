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
enum class AccessNetwork : int32_t {
  UNKNOWN = 0,
  GERAN = 1,
  UTRAN = 2,
  EUTRAN = 3,
  CDMA2000 = 4,
  IWLAN = 5,
  NGRAN = 6,
};

}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
[[nodiscard]] static inline std::string toString(AccessNetwork val) {
  switch(val) {
  case AccessNetwork::UNKNOWN:
    return "UNKNOWN";
  case AccessNetwork::GERAN:
    return "GERAN";
  case AccessNetwork::UTRAN:
    return "UTRAN";
  case AccessNetwork::EUTRAN:
    return "EUTRAN";
  case AccessNetwork::CDMA2000:
    return "CDMA2000";
  case AccessNetwork::IWLAN:
    return "IWLAN";
  case AccessNetwork::NGRAN:
    return "NGRAN";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::AccessNetwork, 7> enum_values<aidl::android::hardware::radio::AccessNetwork> = {
  aidl::android::hardware::radio::AccessNetwork::UNKNOWN,
  aidl::android::hardware::radio::AccessNetwork::GERAN,
  aidl::android::hardware::radio::AccessNetwork::UTRAN,
  aidl::android::hardware::radio::AccessNetwork::EUTRAN,
  aidl::android::hardware::radio::AccessNetwork::CDMA2000,
  aidl::android::hardware::radio::AccessNetwork::IWLAN,
  aidl::android::hardware::radio::AccessNetwork::NGRAN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
