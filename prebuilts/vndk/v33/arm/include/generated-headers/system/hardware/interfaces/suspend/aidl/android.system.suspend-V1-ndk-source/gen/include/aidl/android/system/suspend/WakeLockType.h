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
namespace system {
namespace suspend {
enum class WakeLockType : int8_t {
  PARTIAL = 0,
  FULL = 1,
};

}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace system {
namespace suspend {
[[nodiscard]] static inline std::string toString(WakeLockType val) {
  switch(val) {
  case WakeLockType::PARTIAL:
    return "PARTIAL";
  case WakeLockType::FULL:
    return "FULL";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::system::suspend::WakeLockType, 2> enum_values<aidl::android::system::suspend::WakeLockType> = {
  aidl::android::system::suspend::WakeLockType::PARTIAL,
  aidl::android::system::suspend::WakeLockType::FULL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
