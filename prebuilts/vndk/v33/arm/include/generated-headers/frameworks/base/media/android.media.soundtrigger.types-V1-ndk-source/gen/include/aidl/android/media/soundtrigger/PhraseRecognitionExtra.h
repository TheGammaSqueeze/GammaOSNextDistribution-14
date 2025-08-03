#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/soundtrigger/ConfidenceLevel.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class PhraseRecognitionExtra {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int32_t recognitionModes = 0;
  int32_t confidenceLevel = 0;
  std::vector<::aidl::android::media::soundtrigger::ConfidenceLevel> levels;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) != std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }
  inline bool operator<(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) < std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }
  inline bool operator<=(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) <= std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }
  inline bool operator==(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) == std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }
  inline bool operator>(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) > std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }
  inline bool operator>=(const PhraseRecognitionExtra& rhs) const {
    return std::tie(id, recognitionModes, confidenceLevel, levels) >= std::tie(rhs.id, rhs.recognitionModes, rhs.confidenceLevel, rhs.levels);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhraseRecognitionExtra{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", recognitionModes: " << ::android::internal::ToString(recognitionModes);
    os << ", confidenceLevel: " << ::android::internal::ToString(confidenceLevel);
    os << ", levels: " << ::android::internal::ToString(levels);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
