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
namespace sim {
class SelectUiccSub {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t slot = 0;
  int32_t appIndex = 0;
  int32_t subType = 0;
  int32_t actStatus = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) != std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }
  inline bool operator<(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) < std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }
  inline bool operator<=(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) <= std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }
  inline bool operator==(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) == std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }
  inline bool operator>(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) > std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }
  inline bool operator>=(const SelectUiccSub& rhs) const {
    return std::tie(slot, appIndex, subType, actStatus) >= std::tie(rhs.slot, rhs.appIndex, rhs.subType, rhs.actStatus);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SUBSCRIPTION_TYPE_1 = 0 };
  enum : int32_t { SUBSCRIPTION_TYPE_2 = 1 };
  enum : int32_t { SUBSCRIPTION_TYPE_3 = 2 };
  enum : int32_t { ACT_STATUS_DEACTIVATE = 0 };
  enum : int32_t { ACT_STATUS_ACTIVATE = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SelectUiccSub{";
    os << "slot: " << ::android::internal::ToString(slot);
    os << ", appIndex: " << ::android::internal::ToString(appIndex);
    os << ", subType: " << ::android::internal::ToString(subType);
    os << ", actStatus: " << ::android::internal::ToString(actStatus);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
