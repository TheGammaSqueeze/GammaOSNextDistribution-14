#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/audio/common/AudioConfig.h>
#include <aidl/android/media/soundtrigger/RecognitionStatus.h>
#include <aidl/android/media/soundtrigger/SoundModelType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class RecognitionEvent {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::soundtrigger::RecognitionStatus status = ::aidl::android::media::soundtrigger::RecognitionStatus::INVALID;
  ::aidl::android::media::soundtrigger::SoundModelType type = ::aidl::android::media::soundtrigger::SoundModelType::INVALID;
  bool captureAvailable = false;
  int32_t captureDelayMs = 0;
  int32_t capturePreambleMs = 0;
  bool triggerInData = false;
  std::optional<::aidl::android::media::audio::common::AudioConfig> audioConfig;
  std::vector<uint8_t> data;
  bool recognitionStillActive = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) != std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }
  inline bool operator<(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) < std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }
  inline bool operator<=(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) <= std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }
  inline bool operator==(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) == std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }
  inline bool operator>(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) > std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }
  inline bool operator>=(const RecognitionEvent& rhs) const {
    return std::tie(status, type, captureAvailable, captureDelayMs, capturePreambleMs, triggerInData, audioConfig, data, recognitionStillActive) >= std::tie(rhs.status, rhs.type, rhs.captureAvailable, rhs.captureDelayMs, rhs.capturePreambleMs, rhs.triggerInData, rhs.audioConfig, rhs.data, rhs.recognitionStillActive);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RecognitionEvent{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", captureAvailable: " << ::android::internal::ToString(captureAvailable);
    os << ", captureDelayMs: " << ::android::internal::ToString(captureDelayMs);
    os << ", capturePreambleMs: " << ::android::internal::ToString(capturePreambleMs);
    os << ", triggerInData: " << ::android::internal::ToString(triggerInData);
    os << ", audioConfig: " << ::android::internal::ToString(audioConfig);
    os << ", data: " << ::android::internal::ToString(data);
    os << ", recognitionStillActive: " << ::android::internal::ToString(recognitionStillActive);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
