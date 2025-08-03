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
namespace common {
namespace fmq {
enum class SynchronizedReadWrite : int8_t {
  EMPTY = 0,
};

[[nodiscard]] static inline std::string toString(SynchronizedReadWrite val) {
  switch(val) {
  case SynchronizedReadWrite::EMPTY:
    return "EMPTY";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace fmq
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::common::fmq::SynchronizedReadWrite, 1> enum_values<aidl::android::hardware::common::fmq::SynchronizedReadWrite> = {
  aidl::android::hardware::common::fmq::SynchronizedReadWrite::EMPTY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
