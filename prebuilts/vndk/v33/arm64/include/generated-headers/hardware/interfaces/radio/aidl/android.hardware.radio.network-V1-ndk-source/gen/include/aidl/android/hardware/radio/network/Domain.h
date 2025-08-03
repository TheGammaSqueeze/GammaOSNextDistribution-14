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
enum class Domain : int32_t {
  CS = 1,
  PS = 2,
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
[[nodiscard]] static inline std::string toString(Domain val) {
  switch(val) {
  case Domain::CS:
    return "CS";
  case Domain::PS:
    return "PS";
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
constexpr inline std::array<aidl::android::hardware::radio::network::Domain, 2> enum_values<aidl::android::hardware::radio::network::Domain> = {
  aidl::android::hardware::radio::network::Domain::CS,
  aidl::android::hardware::radio::network::Domain::PS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
