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
namespace system {
namespace keystore2 {
enum class ResponseCode : int32_t {
  LOCKED = 2,
  UNINITIALIZED = 3,
  SYSTEM_ERROR = 4,
  PERMISSION_DENIED = 6,
  KEY_NOT_FOUND = 7,
  VALUE_CORRUPTED = 8,
  KEY_PERMANENTLY_INVALIDATED = 17,
  BACKEND_BUSY = 18,
  OPERATION_BUSY = 19,
  INVALID_ARGUMENT = 20,
  TOO_MUCH_DATA = 21,
  OUT_OF_KEYS = 22,
};

[[nodiscard]] static inline std::string toString(ResponseCode val) {
  switch(val) {
  case ResponseCode::LOCKED:
    return "LOCKED";
  case ResponseCode::UNINITIALIZED:
    return "UNINITIALIZED";
  case ResponseCode::SYSTEM_ERROR:
    return "SYSTEM_ERROR";
  case ResponseCode::PERMISSION_DENIED:
    return "PERMISSION_DENIED";
  case ResponseCode::KEY_NOT_FOUND:
    return "KEY_NOT_FOUND";
  case ResponseCode::VALUE_CORRUPTED:
    return "VALUE_CORRUPTED";
  case ResponseCode::KEY_PERMANENTLY_INVALIDATED:
    return "KEY_PERMANENTLY_INVALIDATED";
  case ResponseCode::BACKEND_BUSY:
    return "BACKEND_BUSY";
  case ResponseCode::OPERATION_BUSY:
    return "OPERATION_BUSY";
  case ResponseCode::INVALID_ARGUMENT:
    return "INVALID_ARGUMENT";
  case ResponseCode::TOO_MUCH_DATA:
    return "TOO_MUCH_DATA";
  case ResponseCode::OUT_OF_KEYS:
    return "OUT_OF_KEYS";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::system::keystore2::ResponseCode, 12> enum_values<aidl::android::system::keystore2::ResponseCode> = {
  aidl::android::system::keystore2::ResponseCode::LOCKED,
  aidl::android::system::keystore2::ResponseCode::UNINITIALIZED,
  aidl::android::system::keystore2::ResponseCode::SYSTEM_ERROR,
  aidl::android::system::keystore2::ResponseCode::PERMISSION_DENIED,
  aidl::android::system::keystore2::ResponseCode::KEY_NOT_FOUND,
  aidl::android::system::keystore2::ResponseCode::VALUE_CORRUPTED,
  aidl::android::system::keystore2::ResponseCode::KEY_PERMANENTLY_INVALIDATED,
  aidl::android::system::keystore2::ResponseCode::BACKEND_BUSY,
  aidl::android::system::keystore2::ResponseCode::OPERATION_BUSY,
  aidl::android::system::keystore2::ResponseCode::INVALID_ARGUMENT,
  aidl::android::system::keystore2::ResponseCode::TOO_MUCH_DATA,
  aidl::android::system::keystore2::ResponseCode::OUT_OF_KEYS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
