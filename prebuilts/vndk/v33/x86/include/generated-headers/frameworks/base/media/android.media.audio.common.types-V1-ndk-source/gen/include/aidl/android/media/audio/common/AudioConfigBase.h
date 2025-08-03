#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioChannelLayout.h>
#include <aidl/android/media/audio/common/AudioFormatDescription.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioConfigBase {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sampleRate = 0;
  ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
  ::aidl::android::media::audio::common::AudioFormatDescription format;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) != std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }
  inline bool operator<(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) < std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }
  inline bool operator<=(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) <= std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }
  inline bool operator==(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) == std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }
  inline bool operator>(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) > std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }
  inline bool operator>=(const AudioConfigBase& rhs) const {
    return std::tie(sampleRate, channelMask, format) >= std::tie(rhs.sampleRate, rhs.channelMask, rhs.format);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioConfigBase{";
    os << "sampleRate: " << ::android::internal::ToString(sampleRate);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
    os << ", format: " << ::android::internal::ToString(format);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
