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
namespace media {
namespace soundtrigger {
enum class Status : int32_t {
  INVALID = -1,
  SUCCESS = 0,
  RESOURCE_CONTENTION = 1,
  OPERATION_NOT_SUPPORTED = 2,
  TEMPORARY_PERMISSION_DENIED = 3,
  DEAD_OBJECT = 4,
  INTERNAL_ERROR = 5,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(Status val) {
  switch(val) {
  case Status::INVALID:
    return "INVALID";
  case Status::SUCCESS:
    return "SUCCESS";
  case Status::RESOURCE_CONTENTION:
    return "RESOURCE_CONTENTION";
  case Status::OPERATION_NOT_SUPPORTED:
    return "OPERATION_NOT_SUPPORTED";
  case Status::TEMPORARY_PERMISSION_DENIED:
    return "TEMPORARY_PERMISSION_DENIED";
  case Status::DEAD_OBJECT:
    return "DEAD_OBJECT";
  case Status::INTERNAL_ERROR:
    return "INTERNAL_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::media::soundtrigger::Status, 7> enum_values<aidl::android::media::soundtrigger::Status> = {
  aidl::android::media::soundtrigger::Status::INVALID,
  aidl::android::media::soundtrigger::Status::SUCCESS,
  aidl::android::media::soundtrigger::Status::RESOURCE_CONTENTION,
  aidl::android::media::soundtrigger::Status::OPERATION_NOT_SUPPORTED,
  aidl::android::media::soundtrigger::Status::TEMPORARY_PERMISSION_DENIED,
  aidl::android::media::soundtrigger::Status::DEAD_OBJECT,
  aidl::android::media::soundtrigger::Status::INTERNAL_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
