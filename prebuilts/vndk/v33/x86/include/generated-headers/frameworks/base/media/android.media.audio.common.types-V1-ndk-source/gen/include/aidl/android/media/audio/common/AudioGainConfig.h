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
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioGainConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t index = 0;
  int32_t mode = 0;
  ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
  std::vector<int32_t> values;
  int32_t rampDurationMs = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) != std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }
  inline bool operator<(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) < std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }
  inline bool operator<=(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) <= std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }
  inline bool operator==(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) == std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }
  inline bool operator>(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) > std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }
  inline bool operator>=(const AudioGainConfig& rhs) const {
    return std::tie(index, mode, channelMask, values, rampDurationMs) >= std::tie(rhs.index, rhs.mode, rhs.channelMask, rhs.values, rhs.rampDurationMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioGainConfig{";
    os << "index: " << ::android::internal::ToString(index);
    os << ", mode: " << ::android::internal::ToString(mode);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
    os << ", values: " << ::android::internal::ToString(values);
    os << ", rampDurationMs: " << ::android::internal::ToString(rampDurationMs);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
