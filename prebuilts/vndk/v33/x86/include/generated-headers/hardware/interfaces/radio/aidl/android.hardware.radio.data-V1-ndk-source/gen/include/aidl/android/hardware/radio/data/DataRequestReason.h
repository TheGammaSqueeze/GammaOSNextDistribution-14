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
namespace data {
enum class DataRequestReason : int32_t {
  NORMAL = 1,
  SHUTDOWN = 2,
  HANDOVER = 3,
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
[[nodiscard]] static inline std::string toString(DataRequestReason val) {
  switch(val) {
  case DataRequestReason::NORMAL:
    return "NORMAL";
  case DataRequestReason::SHUTDOWN:
    return "SHUTDOWN";
  case DataRequestReason::HANDOVER:
    return "HANDOVER";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::data::DataRequestReason, 3> enum_values<aidl::android::hardware::radio::data::DataRequestReason> = {
  aidl::android::hardware::radio::data::DataRequestReason::NORMAL,
  aidl::android::hardware::radio::data::DataRequestReason::SHUTDOWN,
  aidl::android::hardware::radio::data::DataRequestReason::HANDOVER,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
