#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/bluetooth/audio/SbcAllocMethod.h>
#include <aidl/android/hardware/bluetooth/audio/SbcChannelMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class SbcConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sampleRateHz = 0;
  ::aidl::android::hardware::bluetooth::audio::SbcChannelMode channelMode = ::aidl::android::hardware::bluetooth::audio::SbcChannelMode(0);
  int8_t blockLength = 0;
  int8_t numSubbands = 0;
  ::aidl::android::hardware::bluetooth::audio::SbcAllocMethod allocMethod = ::aidl::android::hardware::bluetooth::audio::SbcAllocMethod(0);
  int8_t bitsPerSample = 0;
  int32_t minBitpool = 0;
  int32_t maxBitpool = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) != std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }
  inline bool operator<(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) < std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }
  inline bool operator<=(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) <= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }
  inline bool operator==(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) == std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }
  inline bool operator>(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) > std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }
  inline bool operator>=(const SbcConfiguration& rhs) const {
    return std::tie(sampleRateHz, channelMode, blockLength, numSubbands, allocMethod, bitsPerSample, minBitpool, maxBitpool) >= std::tie(rhs.sampleRateHz, rhs.channelMode, rhs.blockLength, rhs.numSubbands, rhs.allocMethod, rhs.bitsPerSample, rhs.minBitpool, rhs.maxBitpool);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SbcConfiguration{";
    os << "sampleRateHz: " << ::android::internal::ToString(sampleRateHz);
    os << ", channelMode: " << ::android::internal::ToString(channelMode);
    os << ", blockLength: " << ::android::internal::ToString(blockLength);
    os << ", numSubbands: " << ::android::internal::ToString(numSubbands);
    os << ", allocMethod: " << ::android::internal::ToString(allocMethod);
    os << ", bitsPerSample: " << ::android::internal::ToString(bitsPerSample);
    os << ", minBitpool: " << ::android::internal::ToString(minBitpool);
    os << ", maxBitpool: " << ::android::internal::ToString(maxBitpool);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
