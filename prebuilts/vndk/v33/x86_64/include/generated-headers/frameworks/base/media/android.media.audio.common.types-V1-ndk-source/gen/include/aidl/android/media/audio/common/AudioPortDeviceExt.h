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
#include <aidl/android/media/audio/common/AudioFormatDescription.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioPortDeviceExt {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioDevice device;
  int32_t flags = 0;
  std::vector<::aidl::android::media::audio::common::AudioFormatDescription> encodedFormats;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) != std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }
  inline bool operator<(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) < std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }
  inline bool operator<=(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) <= std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }
  inline bool operator==(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) == std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }
  inline bool operator>(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) > std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }
  inline bool operator>=(const AudioPortDeviceExt& rhs) const {
    return std::tie(device, flags, encodedFormats) >= std::tie(rhs.device, rhs.flags, rhs.encodedFormats);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { FLAG_INDEX_DEFAULT_DEVICE = 0 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioPortDeviceExt{";
    os << "device: " << ::android::internal::ToString(device);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << ", encodedFormats: " << ::android::internal::ToString(encodedFormats);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
