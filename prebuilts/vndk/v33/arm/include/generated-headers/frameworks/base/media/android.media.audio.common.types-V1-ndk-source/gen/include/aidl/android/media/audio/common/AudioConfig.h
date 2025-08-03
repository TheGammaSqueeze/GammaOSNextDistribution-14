#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioConfigBase.h>
#include <aidl/android/media/audio/common/AudioOffloadInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioConfigBase base;
  ::aidl::android::media::audio::common::AudioOffloadInfo offloadInfo;
  int64_t frameCount = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) != std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }
  inline bool operator<(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) < std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }
  inline bool operator<=(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) <= std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }
  inline bool operator==(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) == std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }
  inline bool operator>(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) > std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }
  inline bool operator>=(const AudioConfig& rhs) const {
    return std::tie(base, offloadInfo, frameCount) >= std::tie(rhs.base, rhs.offloadInfo, rhs.frameCount);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioConfig{";
    os << "base: " << ::android::internal::ToString(base);
    os << ", offloadInfo: " << ::android::internal::ToString(offloadInfo);
    os << ", frameCount: " << ::android::internal::ToString(frameCount);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
