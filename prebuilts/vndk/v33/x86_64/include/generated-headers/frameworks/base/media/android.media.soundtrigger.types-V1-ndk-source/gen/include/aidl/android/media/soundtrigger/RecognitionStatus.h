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
enum class RecognitionStatus : int32_t {
  INVALID = -1,
  SUCCESS = 0,
  ABORTED = 1,
  FAILURE = 2,
  FORCED = 3,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(RecognitionStatus val) {
  switch(val) {
  case RecognitionStatus::INVALID:
    return "INVALID";
  case RecognitionStatus::SUCCESS:
    return "SUCCESS";
  case RecognitionStatus::ABORTED:
    return "ABORTED";
  case RecognitionStatus::FAILURE:
    return "FAILURE";
  case RecognitionStatus::FORCED:
    return "FORCED";
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
constexpr inline std::array<aidl::android::media::soundtrigger::RecognitionStatus, 5> enum_values<aidl::android::media::soundtrigger::RecognitionStatus> = {
  aidl::android::media::soundtrigger::RecognitionStatus::INVALID,
  aidl::android::media::soundtrigger::RecognitionStatus::SUCCESS,
  aidl::android::media::soundtrigger::RecognitionStatus::ABORTED,
  aidl::android::media::soundtrigger::RecognitionStatus::FAILURE,
  aidl::android::media::soundtrigger::RecognitionStatus::FORCED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
