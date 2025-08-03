#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/AptxAdaptiveChannelMode.h>
#include <aidl/android/hardware/bluetooth/audio/AptxAdaptiveInputMode.h>
#include <aidl/android/hardware/bluetooth/audio/AptxAdaptiveTimeToPlay.h>
#include <aidl/android/hardware/bluetooth/audio/AptxMode.h>
#include <aidl/android/hardware/bluetooth/audio/AptxSinkBuffering.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class AptxAdaptiveCapabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<int32_t> sampleRateHz;
  std::vector<::aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode> channelMode;
  std::vector<uint8_t> bitsPerSample;
  std::vector<::aidl::android::hardware::bluetooth::audio::AptxMode> aptxMode;
  ::aidl::android::hardware::bluetooth::audio::AptxSinkBuffering sinkBufferingMs;
  ::aidl::android::hardware::bluetooth::audio::AptxAdaptiveTimeToPlay ttp;
  ::aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode inputMode = ::aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode(0);
  int32_t inputFadeDurationMs = 0;
  std::vector<uint8_t> aptxAdaptiveConfigStream;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) != std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }
  inline bool operator<(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) < std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }
  inline bool operator<=(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) <= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }
  inline bool operator==(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) == std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }
  inline bool operator>(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) > std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }
  inline bool operator>=(const AptxAdaptiveCapabilities& rhs) const {
    return std::tie(sampleRateHz, channelMode, bitsPerSample, aptxMode, sinkBufferingMs, ttp, inputMode, inputFadeDurationMs, aptxAdaptiveConfigStream) >= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.bitsPerSample, rhs.aptxMode, rhs.sinkBufferingMs, rhs.ttp, rhs.inputMode, rhs.inputFadeDurationMs, rhs.aptxAdaptiveConfigStream);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AptxAdaptiveCapabilities{";
    os << "sampleRateHz: " << ::android::internal::ToString(sampleRateHz);
    os << ", channelMode: " << ::android::internal::ToString(channelMode);
    os << ", bitsPerSample: " << ::android::internal::ToString(bitsPerSample);
    os << ", aptxMode: " << ::android::internal::ToString(aptxMode);
    os << ", sinkBufferingMs: " << ::android::internal::ToString(sinkBufferingMs);
    os << ", ttp: " << ::android::internal::ToString(ttp);
    os << ", inputMode: " << ::android::internal::ToString(inputMode);
    os << ", inputFadeDurationMs: " << ::android::internal::ToString(inputFadeDurationMs);
    os << ", aptxAdaptiveConfigStream: " << ::android::internal::ToString(aptxAdaptiveConfigStream);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
