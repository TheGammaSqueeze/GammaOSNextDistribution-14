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
#include <aidl/android/media/soundtrigger/RecognitionEvent.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class PhraseRecognitionEvent {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::soundtrigger::RecognitionEvent common;
  std::vector<::aidl::android::media::soundtrigger::PhraseRecognitionExtra> phraseExtras;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) != std::tie(rhs.common, rhs.phraseExtras);
  }
  inline bool operator<(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) < std::tie(rhs.common, rhs.phraseExtras);
  }
  inline bool operator<=(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) <= std::tie(rhs.common, rhs.phraseExtras);
  }
  inline bool operator==(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) == std::tie(rhs.common, rhs.phraseExtras);
  }
  inline bool operator>(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) > std::tie(rhs.common, rhs.phraseExtras);
  }
  inline bool operator>=(const PhraseRecognitionEvent& rhs) const {
    return std::tie(common, phraseExtras) >= std::tie(rhs.common, rhs.phraseExtras);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhraseRecognitionEvent{";
    os << "common: " << ::android::internal::ToString(common);
    os << ", phraseExtras: " << ::android::internal::ToString(phraseExtras);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
