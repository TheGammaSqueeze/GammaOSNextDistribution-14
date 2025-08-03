#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioGain.h>
#include <aidl/android/media/audio/common/AudioIoFlags.h>
#include <aidl/android/media/audio/common/AudioPortExt.h>
#include <aidl/android/media/audio/common/AudioProfile.h>
#include <aidl/android/media/audio/common/ExtraAudioDescriptor.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioPort {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  std::string name;
  std::vector<::aidl::android::media::audio::common::AudioProfile> profiles;
  ::aidl::android::media::audio::common::AudioIoFlags flags;
  std::vector<::aidl::android::media::audio::common::ExtraAudioDescriptor> extraAudioDescriptors;
  std::vector<::aidl::android::media::audio::common::AudioGain> gains;
  ::aidl::android::media::audio::common::AudioPortExt ext;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) != std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }
  inline bool operator<(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) < std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }
  inline bool operator<=(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) <= std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }
  inline bool operator==(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) == std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }
  inline bool operator>(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) > std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }
  inline bool operator>=(const AudioPort& rhs) const {
    return std::tie(id, name, profiles, flags, extraAudioDescriptors, gains, ext) >= std::tie(rhs.id, rhs.name, rhs.profiles, rhs.flags, rhs.extraAudioDescriptors, rhs.gains, rhs.ext);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioPort{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", profiles: " << ::android::internal::ToString(profiles);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << ", extraAudioDescriptors: " << ::android::internal::ToString(extraAudioDescriptors);
    os << ", gains: " << ::android::internal::ToString(gains);
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
