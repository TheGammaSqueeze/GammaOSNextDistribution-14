#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioDeviceAddress.h>
#include <aidl/android/media/audio/common/AudioDeviceDescription.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioDevice {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioDeviceDescription type;
  ::aidl::android::media::audio::common::AudioDeviceAddress address;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioDevice& rhs) const {
    return std::tie(type, address) != std::tie(rhs.type, rhs.address);
  }
  inline bool operator<(const AudioDevice& rhs) const {
    return std::tie(type, address) < std::tie(rhs.type, rhs.address);
  }
  inline bool operator<=(const AudioDevice& rhs) const {
    return std::tie(type, address) <= std::tie(rhs.type, rhs.address);
  }
  inline bool operator==(const AudioDevice& rhs) const {
    return std::tie(type, address) == std::tie(rhs.type, rhs.address);
  }
  inline bool operator>(const AudioDevice& rhs) const {
    return std::tie(type, address) > std::tie(rhs.type, rhs.address);
  }
  inline bool operator>=(const AudioDevice& rhs) const {
    return std::tie(type, address) >= std::tie(rhs.type, rhs.address);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioDevice{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", address: " << ::android::internal::ToString(address);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
