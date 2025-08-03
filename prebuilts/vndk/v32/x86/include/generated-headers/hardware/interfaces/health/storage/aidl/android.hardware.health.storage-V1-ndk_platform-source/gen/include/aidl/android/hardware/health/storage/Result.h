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
namespace health {
namespace storage {
enum class Result : int32_t {
  SUCCESS = 0,
  IO_ERROR = 1,
  UNKNOWN_ERROR = 2,
};

[[nodiscard]] static inline std::string toString(Result val) {
  switch(val) {
  case Result::SUCCESS:
    return "SUCCESS";
  case Result::IO_ERROR:
    return "IO_ERROR";
  case Result::UNKNOWN_ERROR:
    return "UNKNOWN_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::health::storage::Result, 3> enum_values<aidl::android::hardware::health::storage::Result> = {
  aidl::android::hardware::health::storage::Result::SUCCESS,
  aidl::android::hardware::health::storage::Result::IO_ERROR,
  aidl::android::hardware::health::storage::Result::UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
