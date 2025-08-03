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
class Lc3Capabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> pcmBitDepth;
  std::vector<int32_t> samplingFrequencyHz;
  std::vector<int32_t> frameDurationUs;
  std::vector<int32_t> octetsPerFrame;
  std::vector<uint8_t> blocksPerSdu;
  std::vector<::aidl::android::hardware::bluetooth::audio::ChannelMode> channelMode;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) != std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator<(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) < std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator<=(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) <= std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator==(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) == std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator>(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) > std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }
  inline bool operator>=(const Lc3Capabilities& rhs) const {
    return std::tie(pcmBitDepth, samplingFrequencyHz, frameDurationUs, octetsPerFrame, blocksPerSdu, channelMode) >= std::tie(rhs.pcmBitDepth, rhs.samplingFrequencyHz, rhs.frameDurationUs, rhs.octetsPerFrame, rhs.blocksPerSdu, rhs.channelMode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Lc3Capabilities{";
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
