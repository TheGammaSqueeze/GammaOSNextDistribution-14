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
namespace network {
enum class CellConnectionStatus : int32_t {
  NONE = 0,
  PRIMARY_SERVING = 1,
  SECONDARY_SERVING = 2,
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
[[nodiscard]] static inline std::string toString(CellConnectionStatus val) {
  switch(val) {
  case CellConnectionStatus::NONE:
    return "NONE";
  case CellConnectionStatus::PRIMARY_SERVING:
    return "PRIMARY_SERVING";
  case CellConnectionStatus::SECONDARY_SERVING:
    return "SECONDARY_SERVING";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::network::CellConnectionStatus, 3> enum_values<aidl::android::hardware::radio::network::CellConnectionStatus> = {
  aidl::android::hardware::radio::network::CellConnectionStatus::NONE,
  aidl::android::hardware::radio::network::CellConnectionStatus::PRIMARY_SERVING,
  aidl::android::hardware::radio::network::CellConnectionStatus::SECONDARY_SERVING,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
