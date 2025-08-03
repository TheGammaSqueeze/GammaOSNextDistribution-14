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
enum class UsageSetting : int32_t {
  VOICE_CENTRIC = 1,
  DATA_CENTRIC = 2,
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
[[nodiscard]] static inline std::string toString(UsageSetting val) {
  switch(val) {
  case UsageSetting::VOICE_CENTRIC:
    return "VOICE_CENTRIC";
  case UsageSetting::DATA_CENTRIC:
    return "DATA_CENTRIC";
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
constexpr inline std::array<aidl::android::hardware::radio::network::UsageSetting, 2> enum_values<aidl::android::hardware::radio::network::UsageSetting> = {
  aidl::android::hardware::radio::network::UsageSetting::VOICE_CENTRIC,
  aidl::android::hardware::radio::network::UsageSetting::DATA_CENTRIC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
