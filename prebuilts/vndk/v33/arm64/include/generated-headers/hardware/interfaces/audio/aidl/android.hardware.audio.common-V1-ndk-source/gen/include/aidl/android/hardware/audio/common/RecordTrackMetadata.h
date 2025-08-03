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
#include <aidl/android/media/audio/common/AudioDevice.h>
#include <aidl/android/media/audio/common/AudioSource.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace audio {
namespace common {
class RecordTrackMetadata {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioSource source = ::aidl::android::media::audio::common::AudioSource::SYS_RESERVED_INVALID;
  float gain = 0.000000f;
  std::optional<::aidl::android::media::audio::common::AudioDevice> destinationDevice;
  ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
  std::vector<std::string> tags;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) != std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }
  inline bool operator<(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) < std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }
  inline bool operator<=(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) <= std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }
  inline bool operator==(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) == std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }
  inline bool operator>(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) > std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }
  inline bool operator>=(const RecordTrackMetadata& rhs) const {
    return std::tie(source, gain, destinationDevice, channelMask, tags) >= std::tie(rhs.source, rhs.gain, rhs.destinationDevice, rhs.channelMask, rhs.tags);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RecordTrackMetadata{";
    os << "source: " << ::android::internal::ToString(source);
    os << ", gain: " << ::android::internal::ToString(gain);
    os << ", destinationDevice: " << ::android::internal::ToString(destinationDevice);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
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
