#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/audio/common/RecordTrackMetadata.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace audio {
namespace common {
class SinkMetadata {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::audio::common::RecordTrackMetadata> tracks;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SinkMetadata& rhs) const {
    return std::tie(tracks) != std::tie(rhs.tracks);
  }
  inline bool operator<(const SinkMetadata& rhs) const {
    return std::tie(tracks) < std::tie(rhs.tracks);
  }
  inline bool operator<=(const SinkMetadata& rhs) const {
    return std::tie(tracks) <= std::tie(rhs.tracks);
  }
  inline bool operator==(const SinkMetadata& rhs) const {
    return std::tie(tracks) == std::tie(rhs.tracks);
  }
  inline bool operator>(const SinkMetadata& rhs) const {
    return std::tie(tracks) > std::tie(rhs.tracks);
  }
  inline bool operator>=(const SinkMetadata& rhs) const {
    return std::tie(tracks) >= std::tie(rhs.tracks);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SinkMetadata{";
    os << "tracks: " << ::android::internal::ToString(tracks);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
