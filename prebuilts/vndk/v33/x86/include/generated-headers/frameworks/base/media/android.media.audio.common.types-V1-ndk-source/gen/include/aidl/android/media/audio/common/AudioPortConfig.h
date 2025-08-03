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
#include <aidl/android/media/audio/common/AudioGainConfig.h>
#include <aidl/android/media/audio/common/AudioIoFlags.h>
#include <aidl/android/media/audio/common/AudioPortExt.h>
#include <aidl/android/media/audio/common/Int.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioPortConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int32_t portId = 0;
  std::optional<::aidl::android::media::audio::common::Int> sampleRate;
  std::optional<::aidl::android::media::audio::common::AudioChannelLayout> channelMask;
  std::optional<::aidl::android::media::audio::common::AudioFormatDescription> format;
  std::optional<::aidl::android::media::audio::common::AudioGainConfig> gain;
  std::optional<::aidl::android::media::audio::common::AudioIoFlags> flags;
  ::aidl::android::media::audio::common::AudioPortExt ext;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) != std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }
  inline bool operator<(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) < std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }
  inline bool operator<=(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) <= std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }
  inline bool operator==(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) == std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }
  inline bool operator>(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) > std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }
  inline bool operator>=(const AudioPortConfig& rhs) const {
    return std::tie(id, portId, sampleRate, channelMask, format, gain, flags, ext) >= std::tie(rhs.id, rhs.portId, rhs.sampleRate, rhs.channelMask, rhs.format, rhs.gain, rhs.flags, rhs.ext);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioPortConfig{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", portId: " << ::android::internal::ToString(portId);
    os << ", sampleRate: " << ::android::internal::ToString(sampleRate);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
    os << ", format: " << ::android::internal::ToString(format);
    os << ", gain: " << ::android::internal::ToString(gain);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << ", ext: " << ::android::internal::ToString(ext);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
