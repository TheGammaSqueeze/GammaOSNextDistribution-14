#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/security/dice/Config.h>
#include <aidl/android/hardware/security/dice/Mode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
class InputValues {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::array<uint8_t, 64> codeHash = {{}};
  ::aidl::android::hardware::security::dice::Config config;
  std::array<uint8_t, 64> authorityHash = {{}};
  std::optional<std::vector<uint8_t>> authorityDescriptor;
  ::aidl::android::hardware::security::dice::Mode mode = ::aidl::android::hardware::security::dice::Mode::NOT_INITIALIZED;
  std::array<uint8_t, 64> hidden = {{}};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) != std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }
  inline bool operator<(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) < std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }
  inline bool operator<=(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) <= std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }
  inline bool operator==(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) == std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }
  inline bool operator>(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) > std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }
  inline bool operator>=(const InputValues& rhs) const {
    return std::tie(codeHash, config, authorityHash, authorityDescriptor, mode, hidden) >= std::tie(rhs.codeHash, rhs.config, rhs.authorityHash, rhs.authorityDescriptor, rhs.mode, rhs.hidden);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "InputValues{";
    os << "codeHash: " << ::android::internal::ToString(codeHash);
    os << ", config: " << ::android::internal::ToString(config);
    os << ", authorityHash: " << ::android::internal::ToString(authorityHash);
    os << ", authorityDescriptor: " << ::android::internal::ToString(authorityDescriptor);
    os << ", mode: " << ::android::internal::ToString(mode);
    os << ", hidden: " << ::android::internal::ToString(hidden);
    os << "}";
    return os.str();
  }
};
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
