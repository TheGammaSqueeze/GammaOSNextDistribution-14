#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/config/SlotPortMapping.h>
#include <aidl/android/hardware/radio/sim/AppStatus.h>
#include <aidl/android/hardware/radio/sim/PinState.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class CardStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t cardState = 0;
  ::aidl::android::hardware::radio::sim::PinState universalPinState = ::aidl::android::hardware::radio::sim::PinState(0);
  int32_t gsmUmtsSubscriptionAppIndex = 0;
  int32_t cdmaSubscriptionAppIndex = 0;
  int32_t imsSubscriptionAppIndex = 0;
  std::vector<::aidl::android::hardware::radio::sim::AppStatus> applications;
  std::string atr;
  std::string iccid;
  std::string eid;
  ::aidl::android::hardware::radio::config::SlotPortMapping slotMap;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) != std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }
  inline bool operator<(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) < std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }
  inline bool operator<=(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) <= std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }
  inline bool operator==(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) == std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }
  inline bool operator>(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) > std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }
  inline bool operator>=(const CardStatus& rhs) const {
    return std::tie(cardState, universalPinState, gsmUmtsSubscriptionAppIndex, cdmaSubscriptionAppIndex, imsSubscriptionAppIndex, applications, atr, iccid, eid, slotMap) >= std::tie(rhs.cardState, rhs.universalPinState, rhs.gsmUmtsSubscriptionAppIndex, rhs.cdmaSubscriptionAppIndex, rhs.imsSubscriptionAppIndex, rhs.applications, rhs.atr, rhs.iccid, rhs.eid, rhs.slotMap);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATE_ABSENT = 0 };
  enum : int32_t { STATE_PRESENT = 1 };
  enum : int32_t { STATE_ERROR = 2 };
  enum : int32_t { STATE_RESTRICTED = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CardStatus{";
    os << "cardState: " << ::android::internal::ToString(cardState);
    os << ", universalPinState: " << ::android::internal::ToString(universalPinState);
    os << ", gsmUmtsSubscriptionAppIndex: " << ::android::internal::ToString(gsmUmtsSubscriptionAppIndex);
    os << ", cdmaSubscriptionAppIndex: " << ::android::internal::ToString(cdmaSubscriptionAppIndex);
    os << ", imsSubscriptionAppIndex: " << ::android::internal::ToString(imsSubscriptionAppIndex);
    os << ", applications: " << ::android::internal::ToString(applications);
    os << ", atr: " << ::android::internal::ToString(atr);
    os << ", iccid: " << ::android::internal::ToString(iccid);
    os << ", eid: " << ::android::internal::ToString(eid);
    os << ", slotMap: " << ::android::internal::ToString(slotMap);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
