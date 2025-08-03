#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioDeviceType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioDeviceDescription {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioDeviceType type = ::aidl::android::media::audio::common::AudioDeviceType::NONE;
  std::string connection;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) != std::tie(rhs.type, rhs.connection);
  }
  inline bool operator<(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) < std::tie(rhs.type, rhs.connection);
  }
  inline bool operator<=(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) <= std::tie(rhs.type, rhs.connection);
  }
  inline bool operator==(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) == std::tie(rhs.type, rhs.connection);
  }
  inline bool operator>(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) > std::tie(rhs.type, rhs.connection);
  }
  inline bool operator>=(const AudioDeviceDescription& rhs) const {
    return std::tie(type, connection) >= std::tie(rhs.type, rhs.connection);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  static const char* CONNECTION_ANALOG;
  static const char* CONNECTION_BT_A2DP;
  static const char* CONNECTION_BT_LE;
  static const char* CONNECTION_BT_SCO;
  static const char* CONNECTION_BUS;
  static const char* CONNECTION_HDMI;
  static const char* CONNECTION_HDMI_ARC;
  static const char* CONNECTION_HDMI_EARC;
  static const char* CONNECTION_IP_V4;
  static const char* CONNECTION_SPDIF;
  static const char* CONNECTION_WIRELESS;
  static const char* CONNECTION_USB;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioDeviceDescription{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", connection: " << ::android::internal::ToString(connection);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
