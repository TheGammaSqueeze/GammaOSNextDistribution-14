#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/security/keymint/KeyParameterValue.h>
#include <aidl/android/hardware/security/keymint/Tag.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class KeyParameter {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::security::keymint::Tag tag = ::aidl::android::hardware::security::keymint::Tag::INVALID;
  ::aidl::android::hardware::security::keymint::KeyParameterValue value;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyParameter& rhs) const {
    return std::tie(tag, value) != std::tie(rhs.tag, rhs.value);
  }
  inline bool operator<(const KeyParameter& rhs) const {
    return std::tie(tag, value) < std::tie(rhs.tag, rhs.value);
  }
  inline bool operator<=(const KeyParameter& rhs) const {
    return std::tie(tag, value) <= std::tie(rhs.tag, rhs.value);
  }
  inline bool operator==(const KeyParameter& rhs) const {
    return std::tie(tag, value) == std::tie(rhs.tag, rhs.value);
  }
  inline bool operator>(const KeyParameter& rhs) const {
    return std::tie(tag, value) > std::tie(rhs.tag, rhs.value);
  }
  inline bool operator>=(const KeyParameter& rhs) const {
    return std::tie(tag, value) >= std::tie(rhs.tag, rhs.value);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyParameter{";
    os << "tag: " << ::android::internal::ToString(tag);
    os << ", value: " << ::android::internal::ToString(value);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
