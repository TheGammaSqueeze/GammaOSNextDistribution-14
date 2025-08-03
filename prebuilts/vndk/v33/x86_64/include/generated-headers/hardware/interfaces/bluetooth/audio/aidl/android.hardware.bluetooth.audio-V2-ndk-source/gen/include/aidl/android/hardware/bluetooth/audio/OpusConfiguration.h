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
class OpusConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t pcmBitDepth = 0;
  int32_t samplingFrequencyHz = 0;
  int32_t frameDurationUs = 0;
  int32_t octetsPerFrame = 0;
  int8_t blocksPerSdu = 0;
  ::aidl::android::hardware::bluetooth::audio::ChannelMode channelMode = ::aidl::android::hardware::bluetooth::audio::ChannelMode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) != std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator<(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) < std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator<=(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) <= std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator==(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) == std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator>(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) > std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator>=(const OpusConfiguration& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) >= std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OpusConfiguration{";
    os << "pcmBitDepth: " << ::android::internal::ToString(pcmBitDepth);
    os << ", samplingFrequencyHz: " << ::android::internal::ToString(samplingFrequencyHz);
    os << ", frameDurationUs: " << ::android::internal::ToString(frameDurationUs);
    os << ", octetsPerFrame: " << ::android::internal::ToString(octetsPerFrame);
    os << ", blocksPerSdu: " << ::android::internal::ToString(blocksPerSdu);
    os << ", channelMode: " << ::android::internal::ToString(channelMode);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
