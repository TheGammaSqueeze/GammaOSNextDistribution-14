#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/CodecType.h>
#include <aidl/android/hardware/bluetooth/audio/LeAudioBroadcastConfiguration.h>
#include <aidl/android/hardware/bluetooth/audio/LeAudioCodecConfiguration.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class LeAudioBroadcastConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class BroadcastStreamMap {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    char16_t streamHandle = '\0';
    int32_t audioChannelAllocation = 0;
    ::aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration leAudioCodecConfig;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) != std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }
    inline bool operator<(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) < std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }
    inline bool operator<=(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) <= std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }
    inline bool operator==(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) == std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }
    inline bool operator>(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) > std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }
    inline bool operator>=(const BroadcastStreamMap& rhs) const {
      return std::tie(streamHandle, audioChannelAllocation, leAudioCodecConfig) >= std::tie(rhs.streamHandle, rhs.audioChannelAllocation, rhs.leAudioCodecConfig);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "BroadcastStreamMap{";
      os << "streamHandle: " << ::android::internal::ToString(streamHandle);
      os << ", audioChannelAllocation: " << ::android::internal::ToString(audioChannelAllocation);
      os << ", leAudioCodecConfig: " << ::android::internal::ToString(leAudioCodecConfig);
      os << "}";
      return os.str();
    }
  };
  ::aidl::android::hardware::bluetooth::audio::CodecType codecType = ::aidl::android::hardware::bluetooth::audio::CodecType(0);
  std::vector<::aidl::android::hardware::bluetooth::audio::LeAudioBroadcastConfiguration::BroadcastStreamMap> streamMap;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) != std::tie(rhs.codecType, rhs.streamMap);
  }
  inline bool operator<(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) < std::tie(rhs.codecType, rhs.streamMap);
  }
  inline bool operator<=(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) <= std::tie(rhs.codecType, rhs.streamMap);
  }
  inline bool operator==(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) == std::tie(rhs.codecType, rhs.streamMap);
  }
  inline bool operator>(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) > std::tie(rhs.codecType, rhs.streamMap);
  }
  inline bool operator>=(const LeAudioBroadcastConfiguration& rhs) const {
    return std::tie(codecType, streamMap) >= std::tie(rhs.codecType, rhs.streamMap);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LeAudioBroadcastConfiguration{";
    os << "codecType: " << ::android::internal::ToString(codecType);
    os << ", streamMap: " << ::android::internal::ToString(streamMap);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
