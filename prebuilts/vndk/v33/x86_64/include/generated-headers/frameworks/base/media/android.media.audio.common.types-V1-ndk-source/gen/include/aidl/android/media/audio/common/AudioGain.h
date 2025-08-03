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
class AudioGain {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t mode = 0;
  ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
  int32_t minValue = 0;
  int32_t maxValue = 0;
  int32_t defaultValue = 0;
  int32_t stepValue = 0;
  int32_t minRampMs = 0;
  int32_t maxRampMs = 0;
  bool useForVolume = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) != std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }
  inline bool operator<(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) < std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }
  inline bool operator<=(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) <= std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }
  inline bool operator==(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) == std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }
  inline bool operator>(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) > std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }
  inline bool operator>=(const AudioGain& rhs) const {
    return std::tie(mode, channelMask, minValue, maxValue, defaultValue, stepValue, minRampMs, maxRampMs, useForVolume) >= std::tie(rhs.mode, rhs.channelMask, rhs.minValue, rhs.maxValue, rhs.defaultValue, rhs.stepValue, rhs.minRampMs, rhs.maxRampMs, rhs.useForVolume);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioGain{";
    os << "mode: " << ::android::internal::ToString(mode);
    os << ", channelMask: " << ::android::internal::ToString(channelMask);
    os << ", minValue: " << ::android::internal::ToString(minValue);
    os << ", maxValue: " << ::android::internal::ToString(maxValue);
    os << ", defaultValue: " << ::android::internal::ToString(defaultValue);
    os << ", stepValue: " << ::android::internal::ToString(stepValue);
    os << ", minRampMs: " << ::android::internal::ToString(minRampMs);
    os << ", maxRampMs: " << ::android::internal::ToString(maxRampMs);
    os << ", useForVolume: " << ::android::internal::ToString(useForVolume);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
