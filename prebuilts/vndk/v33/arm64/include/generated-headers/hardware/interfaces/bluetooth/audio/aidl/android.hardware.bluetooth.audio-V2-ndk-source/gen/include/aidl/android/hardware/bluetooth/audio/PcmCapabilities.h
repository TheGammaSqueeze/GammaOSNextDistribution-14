#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/ChannelMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class PcmCapabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<int32_t> sampleRateHz;
  std::vector<::aidl::android::hardware::bluetooth::audio::ChannelMode> channelMode;
  std::vector<uint8_t> bitsPerSample;
  std::vector<int32_t> dataIntervalUs;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) != std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }
  inline bool operator<(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) < std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }
  inline bool operator<=(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) <= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }
  inline bool operator==(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) == std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }
  inline bool operator>(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) > std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }
  inline bool operator>=(const PcmCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, dataIntervalUs) >= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.dataIntervalUs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PcmCapabilities{";
    os << "sampleRateHz: " << ::android::internal::ToString(sampleRateHz);
    os << ", channelMode: " << ::android::internal::ToString(channelMode);
    os << ", bitsPerSample: " << ::android::internal::ToString(bitsPerSample);
    os << ", dataIntervalUs: " << ::android::internal::ToString(dataIntervalUs);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
