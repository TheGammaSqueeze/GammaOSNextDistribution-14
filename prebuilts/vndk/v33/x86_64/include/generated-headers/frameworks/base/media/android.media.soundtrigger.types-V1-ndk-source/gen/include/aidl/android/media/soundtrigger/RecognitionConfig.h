#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/soundtrigger/PhraseRecognitionExtra.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class RecognitionConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool captureRequested = false;
  std::vector<::aidl::android::media::soundtrigger::PhraseRecognitionExtra> phraseRecognitionExtras;
  int32_t audioCapabilities = 0;
  std::vector<uint8_t> data;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) != std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }
  inline bool operator<(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) < std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }
  inline bool operator<=(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) <= std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }
  inline bool operator==(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) == std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }
  inline bool operator>(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) > std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }
  inline bool operator>=(const RecognitionConfig& rhs) const {
    return std::tie(captureRequested, phraseRecognitionExtras, audioCapabilities, data) >= std::tie(rhs.captureRequested, rhs.phraseRecognitionExtras, rhs.audioCapabilities, rhs.data);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RecognitionConfig{";
    os << "captureRequested: " << ::android::internal::ToString(captureRequested);
    os << ", phraseRecognitionExtras: " << ::android::internal::ToString(phraseRecognitionExtras);
    os << ", audioCapabilities: " << ::android::internal::ToString(audioCapabilities);
    os << ", data: " << ::android::internal::ToString(data);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
