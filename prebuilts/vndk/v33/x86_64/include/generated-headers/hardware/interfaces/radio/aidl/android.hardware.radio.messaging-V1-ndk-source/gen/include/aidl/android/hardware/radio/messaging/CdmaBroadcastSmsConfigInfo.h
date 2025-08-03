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
namespace hardware {
namespace radio {
namespace messaging {
class CdmaBroadcastSmsConfigInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t serviceCategory = 0;
  int32_t language = 0;
  bool selected = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) != std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }
  inline bool operator<(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) < std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }
  inline bool operator<=(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) <= std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }
  inline bool operator==(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) == std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }
  inline bool operator>(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) > std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }
  inline bool operator>=(const CdmaBroadcastSmsConfigInfo& rhs) const {
    return std::tie(serviceCategory, language, selected) >= std::tie(rhs.serviceCategory, rhs.language, rhs.selected);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaBroadcastSmsConfigInfo{";
    os << "serviceCategory: " << ::android::internal::ToString(serviceCategory);
    os << ", language: " << ::android::internal::ToString(language);
    os << ", selected: " << ::android::internal::ToString(selected);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
