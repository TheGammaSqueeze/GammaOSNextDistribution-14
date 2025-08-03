#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioConfigBase.h>
#include <aidl/android/media/audio/common/AudioEncapsulationMode.h>
#include <aidl/android/media/audio/common/AudioStreamType.h>
#include <aidl/android/media/audio/common/AudioUsage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioOffloadInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::audio::common::AudioConfigBase base;
  ::aidl::android::media::audio::common::AudioStreamType streamType = ::aidl::android::media::audio::common::AudioStreamType::INVALID;
  int32_t bitRatePerSecond = 0;
  int64_t durationUs = 0L;
  bool hasVideo = false;
  bool isStreaming = false;
  int32_t bitWidth = 16;
  int32_t offloadBufferSize = 0;
  ::aidl::android::media::audio::common::AudioUsage usage = ::aidl::android::media::audio::common::AudioUsage::INVALID;
  ::aidl::android::media::audio::common::AudioEncapsulationMode encapsulationMode = ::aidl::android::media::audio::common::AudioEncapsulationMode::INVALID;
  int32_t contentId = 0;
  int32_t syncId = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) != std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }
  inline bool operator<(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) < std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }
  inline bool operator<=(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) <= std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }
  inline bool operator==(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) == std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }
  inline bool operator>(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) > std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }
  inline bool operator>=(const AudioOffloadInfo& rhs) const {
    return std::tie(base, streamType, bitRatePerSecond, durationUs, hasVideo, isStreaming, bitWidth, offloadBufferSize, usage, encapsulationMode, contentId, syncId) >= std::tie(rhs.base, rhs.streamType, rhs.bitRatePerSecond, rhs.durationUs, rhs.hasVideo, rhs.isStreaming, rhs.bitWidth, rhs.offloadBufferSize, rhs.usage, rhs.encapsulationMode, rhs.contentId, rhs.syncId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioOffloadInfo{";
    os << "base: " << ::android::internal::ToString(base);
    os << ", streamType: " << ::android::internal::ToString(streamType);
    os << ", bitRatePerSecond: " << ::android::internal::ToString(bitRatePerSecond);
    os << ", durationUs: " << ::android::internal::ToString(durationUs);
    os << ", hasVideo: " << ::android::internal::ToString(hasVideo);
    os << ", isStreaming: " << ::android::internal::ToString(isStreaming);
    os << ", bitWidth: " << ::android::internal::ToString(bitWidth);
    os << ", offloadBufferSize: " << ::android::internal::ToString(offloadBufferSize);
    os << ", usage: " << ::android::internal::ToString(usage);
    os << ", encapsulationMode: " << ::android::internal::ToString(encapsulationMode);
    os << ", contentId: " << ::android::internal::ToString(contentId);
    os << ", syncId: " << ::android::internal::ToString(syncId);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
