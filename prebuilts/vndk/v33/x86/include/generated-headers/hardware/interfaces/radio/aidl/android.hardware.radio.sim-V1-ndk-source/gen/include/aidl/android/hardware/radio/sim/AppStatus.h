#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/sim/PersoSubstate.h>
#include <aidl/android/hardware/radio/sim/PinState.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class AppStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t appType = 0;
  int32_t appState = 0;
  ::aidl::android::hardware::radio::sim::PersoSubstate persoSubstate = ::aidl::android::hardware::radio::sim::PersoSubstate(0);
  std::string aidPtr;
  std::string appLabelPtr;
  bool pin1Replaced = false;
  ::aidl::android::hardware::radio::sim::PinState pin1 = ::aidl::android::hardware::radio::sim::PinState(0);
  ::aidl::android::hardware::radio::sim::PinState pin2 = ::aidl::android::hardware::radio::sim::PinState(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) != std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }
  inline bool operator<(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) < std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }
  inline bool operator<=(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) <= std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }
  inline bool operator==(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) == std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }
  inline bool operator>(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) > std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }
  inline bool operator>=(const AppStatus& rhs) const {
    return std::tie(appType, appState, persoSubstate, aidPtr, appLabelPtr, pin1Replaced, pin1, pin2) >= std::tie(rhs.appType, rhs.appState, rhs.persoSubstate, rhs.aidPtr, rhs.appLabelPtr, rhs.pin1Replaced, rhs.pin1, rhs.pin2);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { APP_STATE_UNKNOWN = 0 };
  enum : int32_t { APP_STATE_DETECTED = 1 };
  enum : int32_t { APP_STATE_PIN = 2 };
  enum : int32_t { APP_STATE_PUK = 3 };
  enum : int32_t { APP_STATE_SUBSCRIPTION_PERSO = 4 };
  enum : int32_t { APP_STATE_READY = 5 };
  enum : int32_t { APP_TYPE_UNKNOWN = 0 };
  enum : int32_t { APP_TYPE_SIM = 1 };
  enum : int32_t { APP_TYPE_USIM = 2 };
  enum : int32_t { APP_TYPE_RUIM = 3 };
  enum : int32_t { APP_TYPE_CSIM = 4 };
  enum : int32_t { APP_TYPE_ISIM = 5 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "AppStatus{";
    os << "appType: " << ::android::internal::ToString(appType);
    os << ", appState: " << ::android::internal::ToString(appState);
    os << ", persoSubstate: " << ::android::internal::ToString(persoSubstate);
    os << ", aidPtr: " << ::android::internal::ToString(aidPtr);
    os << ", appLabelPtr: " << ::android::internal::ToString(appLabelPtr);
    os << ", pin1Replaced: " << ::android::internal::ToString(pin1Replaced);
    os << ", pin1: " << ::android::internal::ToString(pin1);
    os << ", pin2: " << ::android::internal::ToString(pin2);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
