#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/AccessNetwork.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class SignalThresholdInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t signalMeasurement = 0;
  int32_t hysteresisMs = 0;
  int32_t hysteresisDb = 0;
  std::vector<int32_t> thresholds;
  bool isEnabled = false;
  ::aidl::android::hardware::radio::AccessNetwork ran = ::aidl::android::hardware::radio::AccessNetwork(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) != std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }
  inline bool operator<(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) < std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }
  inline bool operator<=(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) <= std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }
  inline bool operator==(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) == std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }
  inline bool operator>(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) > std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }
  inline bool operator>=(const SignalThresholdInfo& rhs) const {
    return std::tie(signalMeasurement, hysteresisMs, hysteresisDb, thresholds, isEnabled, ran) >= std::tie(rhs.signalMeasurement, rhs.hysteresisMs, rhs.hysteresisDb, rhs.thresholds, rhs.isEnabled, rhs.ran);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_RSSI = 1 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_RSCP = 2 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_RSRP = 3 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_RSRQ = 4 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_RSSNR = 5 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_SSRSRP = 6 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_SSRSRQ = 7 };
  enum : int32_t { SIGNAL_MEASUREMENT_TYPE_SSSINR = 8 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SignalThresholdInfo{";
    os << "signalMeasurement: " << ::android::internal::ToString(signalMeasurement);
    os << ", hysteresisMs: " << ::android::internal::ToString(hysteresisMs);
    os << ", hysteresisDb: " << ::android::internal::ToString(hysteresisDb);
    os << ", thresholds: " << ::android::internal::ToString(thresholds);
    os << ", isEnabled: " << ::android::internal::ToString(isEnabled);
    os << ", ran: " << ::android::internal::ToString(ran);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
