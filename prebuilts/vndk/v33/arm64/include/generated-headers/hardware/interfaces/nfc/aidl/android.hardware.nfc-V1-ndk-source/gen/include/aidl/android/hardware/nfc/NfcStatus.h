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
namespace nfc {
enum class NfcStatus : int32_t {
  OK = 0,
  FAILED = 1,
  ERR_TRANSPORT = 2,
  ERR_CMD_TIMEOUT = 3,
  REFUSED = 4,
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
[[nodiscard]] static inline std::string toString(NfcStatus val) {
  switch(val) {
  case NfcStatus::OK:
    return "OK";
  case NfcStatus::FAILED:
    return "FAILED";
  case NfcStatus::ERR_TRANSPORT:
    return "ERR_TRANSPORT";
  case NfcStatus::ERR_CMD_TIMEOUT:
    return "ERR_CMD_TIMEOUT";
  case NfcStatus::REFUSED:
    return "REFUSED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::nfc::NfcStatus, 5> enum_values<aidl::android::hardware::nfc::NfcStatus> = {
  aidl::android::hardware::nfc::NfcStatus::OK,
  aidl::android::hardware::nfc::NfcStatus::FAILED,
  aidl::android::hardware::nfc::NfcStatus::ERR_TRANSPORT,
  aidl::android::hardware::nfc::NfcStatus::ERR_CMD_TIMEOUT,
  aidl::android::hardware::nfc::NfcStatus::REFUSED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
