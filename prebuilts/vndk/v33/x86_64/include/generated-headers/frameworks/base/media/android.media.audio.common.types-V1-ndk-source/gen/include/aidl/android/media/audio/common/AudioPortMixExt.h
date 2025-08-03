#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioPortMixExtUseCase.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioPortMixExt {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t handle = 0;
  ::aidl::android::media::audio::common::AudioPortMixExtUseCase usecase;
  int32_t maxOpenStreamCount = 0;
  int32_t maxActiveStreamCount = 0;
  int32_t recommendedMuteDurationMs = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) != std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }
  inline bool operator<(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) < std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }
  inline bool operator<=(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) <= std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }
  inline bool operator==(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) == std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }
  inline bool operator>(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) > std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }
  inline bool operator>=(const AudioPortMixExt& rhs) const {
    return std::tie(handle, usecase, maxOpenStreamCount, maxActiveStreamCount, recommendedMuteDurationMs) >= std::tie(rhs.handle, rhs.usecase, rhs.maxOpenStreamCount, rhs.maxActiveStreamCount, rhs.recommendedMuteDurationMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioPortMixExt{";
    os << "handle: " << ::android::internal::ToString(handle);
    os << ", usecase: " << ::android::internal::ToString(usecase);
    os << ", maxOpenStreamCount: " << ::android::internal::ToString(maxOpenStreamCount);
    os << ", maxActiveStreamCount: " << ::android::internal::ToString(maxActiveStreamCount);
    os << ", recommendedMuteDurationMs: " << ::android::internal::ToString(recommendedMuteDurationMs);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
