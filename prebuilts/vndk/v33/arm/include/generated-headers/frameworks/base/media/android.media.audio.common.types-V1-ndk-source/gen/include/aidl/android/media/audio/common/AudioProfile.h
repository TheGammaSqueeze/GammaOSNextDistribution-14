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
#include <aidl/android/media/audio/common/AudioEncapsulationType.h>
#include <aidl/android/media/audio/common/AudioFormatDescription.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioProfile {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string name;
  ::aidl::android::media::audio::common::AudioFormatDescription format;
  std::vector<::aidl::android::media::audio::common::AudioChannelLayout> channelMasks;
  std::vector<int32_t> sampleRates;
  ::aidl::android::media::audio::common::AudioEncapsulationType encapsulationType = ::aidl::android::media::audio::common::AudioEncapsulationType::NONE;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) != std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }
  inline bool operator<(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) < std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }
  inline bool operator<=(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) <= std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }
  inline bool operator==(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) == std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }
  inline bool operator>(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) > std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }
  inline bool operator>=(const AudioProfile& rhs) const {
    return std::tie(name, format, channelMasks, sampleRates, encapsulationType) >= std::tie(rhs.name, rhs.format, rhs.channelMasks, rhs.sampleRates, rhs.encapsulationType);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioProfile{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", format: " << ::android::internal::ToString(format);
    os << ", channelMasks: " << ::android::internal::ToString(channelMasks);
    os << ", sampleRates: " << ::android::internal::ToString(sampleRates);
    os << ", encapsulationType: " << ::android::internal::ToString(encapsulationType);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
