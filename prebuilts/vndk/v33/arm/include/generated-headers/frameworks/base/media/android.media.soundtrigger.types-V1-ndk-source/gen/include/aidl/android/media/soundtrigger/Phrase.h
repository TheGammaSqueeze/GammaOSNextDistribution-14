#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class Phrase {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int32_t recognitionModes = 0;
  std::vector<int32_t> users;
  std::string locale;
  std::string text;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) != std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }
  inline bool operator<(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) < std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }
  inline bool operator<=(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) <= std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }
  inline bool operator==(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) == std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }
  inline bool operator>(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) > std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }
  inline bool operator>=(const Phrase& rhs) const {
    return std::tie(id, recognitionModes, users, locale, text) >= std::tie(rhs.id, rhs.recognitionModes, rhs.users, rhs.locale, rhs.text);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Phrase{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", recognitionModes: " << ::android::internal::ToString(recognitionModes);
    os << ", users: " << ::android::internal::ToString(users);
    os << ", locale: " << ::android::internal::ToString(locale);
    os << ", text: " << ::android::internal::ToString(text);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
