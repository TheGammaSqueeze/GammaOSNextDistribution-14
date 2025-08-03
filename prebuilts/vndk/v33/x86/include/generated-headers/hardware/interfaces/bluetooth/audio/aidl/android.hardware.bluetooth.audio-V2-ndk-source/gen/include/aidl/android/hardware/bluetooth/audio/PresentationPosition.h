#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/PresentationPosition.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class PresentationPosition {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class TimeSpec {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t tvSec = 0L;
    int64_t tvNSec = 0L;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) != std::tie(rhs.tvSec, rhs.tvNSec);
    }
    inline bool operator<(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) < std::tie(rhs.tvSec, rhs.tvNSec);
    }
    inline bool operator<=(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) <= std::tie(rhs.tvSec, rhs.tvNSec);
    }
    inline bool operator==(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) == std::tie(rhs.tvSec, rhs.tvNSec);
    }
    inline bool operator>(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) > std::tie(rhs.tvSec, rhs.tvNSec);
    }
    inline bool operator>=(const TimeSpec& rhs) const {
      return std::tie(tvSec, tvNSec) >= std::tie(rhs.tvSec, rhs.tvNSec);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "TimeSpec{";
      os << "tvSec: " << ::android::internal::ToString(tvSec);
      os << ", tvNSec: " << ::android::internal::ToString(tvNSec);
      os << "}";
      return os.str();
    }
  };
  int64_t remoteDeviceAudioDelayNanos = 0L;
  int64_t transmittedOctets = 0L;
  ::aidl::android::hardware::bluetooth::audio::PresentationPosition::TimeSpec transmittedOctetsTimestamp;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) != std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }
  inline bool operator<(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) < std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }
  inline bool operator<=(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) <= std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }
  inline bool operator==(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) == std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }
  inline bool operator>(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) > std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }
  inline bool operator>=(const PresentationPosition& rhs) const {
    return std::tie(remoteDeviceAudioDelayNanos, transmittedOctets, transmittedOctetsTimestamp) >= std::tie(rhs.remoteDeviceAudioDelayNanos, rhs.transmittedOctets, rhs.transmittedOctetsTimestamp);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PresentationPosition{";
    os << "remoteDeviceAudioDelayNanos: " << ::android::internal::ToString(remoteDeviceAudioDelayNanos);
    os << ", transmittedOctets: " << ::android::internal::ToString(transmittedOctets);
    os << ", transmittedOctetsTimestamp: " << ::android::internal::ToString(transmittedOctetsTimestamp);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
