#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioDevice.h>
#include <aidl/android/media/audio/common/AudioMMapPolicy.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioMMapPolicyInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioDevice device;
  ::aidl::android::media::audio::common::AudioMMapPolicy mmapPolicy = ::aidl::android::media::audio::common::AudioMMapPolicy::UNSPECIFIED;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) != std::tie(rhs.device, rhs.mmapPolicy);
  }
  inline bool operator<(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) < std::tie(rhs.device, rhs.mmapPolicy);
  }
  inline bool operator<=(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) <= std::tie(rhs.device, rhs.mmapPolicy);
  }
  inline bool operator==(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) == std::tie(rhs.device, rhs.mmapPolicy);
  }
  inline bool operator>(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) > std::tie(rhs.device, rhs.mmapPolicy);
  }
  inline bool operator>=(const AudioMMapPolicyInfo& rhs) const {
    return std::tie(device, mmapPolicy) >= std::tie(rhs.device, rhs.mmapPolicy);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioMMapPolicyInfo{";
    os << "device: " << ::android::internal::ToString(device);
    os << ", mmapPolicy: " << ::android::internal::ToString(mmapPolicy);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
