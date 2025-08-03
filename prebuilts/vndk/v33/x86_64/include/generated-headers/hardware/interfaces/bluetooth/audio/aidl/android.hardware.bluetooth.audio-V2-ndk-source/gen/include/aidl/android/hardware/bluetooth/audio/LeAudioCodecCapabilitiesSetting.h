#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/BroadcastCapability.h>
#include <aidl/android/hardware/bluetooth/audio/UnicastCapability.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class LeAudioCodecCapabilitiesSetting {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::bluetooth::audio::UnicastCapability unicastEncodeCapability;
  ::aidl::android::hardware::bluetooth::audio::UnicastCapability unicastDecodeCapability;
  ::aidl::android::hardware::bluetooth::audio::BroadcastCapability broadcastCapability;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) != std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }
  inline bool operator<(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) < std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }
  inline bool operator<=(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) <= std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }
  inline bool operator==(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) == std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }
  inline bool operator>(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) > std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }
  inline bool operator>=(const LeAudioCodecCapabilitiesSetting& rhs) const {
    return std::tie(unicastEncodeCapability, unicastDecodeCapability, broadcastCapability) >= std::tie(rhs.unicastEncodeCapability, rhs.unicastDecodeCapability, rhs.broadcastCapability);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LeAudioCodecCapabilitiesSetting{";
    os << "unicastEncodeCapability: " << ::android::internal::ToString(unicastEncodeCapability);
    os << ", unicastDecodeCapability: " << ::android::internal::ToString(unicastDecodeCapability);
    os << ", broadcastCapability: " << ::android::internal::ToString(broadcastCapability);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
