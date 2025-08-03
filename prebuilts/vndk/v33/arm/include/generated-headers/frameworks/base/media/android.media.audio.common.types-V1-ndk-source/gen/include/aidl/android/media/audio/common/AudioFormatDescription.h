#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioFormatType.h>
#include <aidl/android/media/audio/common/PcmType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioFormatDescription {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioFormatType type = ::aidl::android::media::audio::common::AudioFormatType::DEFAULT;
  ::aidl::android::media::audio::common::PcmType pcm = ::aidl::android::media::audio::common::PcmType::DEFAULT;
  std::string encoding;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) != std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }
  inline bool operator<(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) < std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }
  inline bool operator<=(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) <= std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }
  inline bool operator==(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) == std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }
  inline bool operator>(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) > std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }
  inline bool operator>=(const AudioFormatDescription& rhs) const {
    return std::tie(type, pcm, encoding) >= std::tie(rhs.type, rhs.pcm, rhs.encoding);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioFormatDescription{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", pcm: " << ::android::internal::ToString(pcm);
    os << ", encoding: " << ::android::internal::ToString(encoding);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
