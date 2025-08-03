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
#include <aidl/android/media/audio/common/AudioContentType.h>
#include <aidl/android/media/audio/common/AudioDevice.h>
#include <aidl/android/media/audio/common/AudioUsage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace audio {
namespace common {
class PlaybackTrackMetadata {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioUsage usage = ::aidl::android::media::audio::common::AudioUsage::INVALID;
  ::aidl::android::media::audio::common::AudioContentType contentType = ::aidl::android::media::audio::common::AudioContentType::UNKNOWN;
  float gain = 0.000000f;
  ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
  std::optional<::aidl::android::media::audio::common::AudioDevice> sourceDevice;
  std::vector<std::string> tags;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) != std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }
  inline bool operator<(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) < std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }
  inline bool operator<=(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) <= std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }
  inline bool operator==(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) == std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }
  inline bool operator>(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) > std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }
  inline bool operator>=(const PlaybackTrackMetadata& rhs) const {
    return std::tie(usage, contentType, gain, channelMask, sourceDevice, tags) >= std::tie(rhs.usage, rhs.contentType, rhs.gain, rhs.channelMask, rhs.sourceDevice, rhs.tags);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PlaybackTrackMetadata{";
    os << "usage: " << ::android::internal::ToString(usage);
    os << ", contentType: " << ::android::internal::ToString(contentType);
    os << ", gain: " << ::android::internal::ToString(gain);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
    os << ", sourceDevice: " << ::android::internal::ToString(sourceDevice);
    os << ", tags: " << ::android::internal::ToString(tags);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
