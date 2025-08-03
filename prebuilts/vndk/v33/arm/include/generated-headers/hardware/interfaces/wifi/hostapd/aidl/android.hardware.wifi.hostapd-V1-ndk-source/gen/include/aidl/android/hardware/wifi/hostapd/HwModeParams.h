#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/hostapd/ChannelBandwidth.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class HwModeParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool enable80211N = false;
  bool enable80211AC = false;
  bool enable80211AX = false;
  bool enable6GhzBand = false;
  bool enableHeSingleUserBeamformer = false;
  bool enableHeSingleUserBeamformee = false;
  bool enableHeMultiUserBeamformer = false;
  bool enableHeTargetWakeTime = false;
  bool enableEdmg = false;
  bool enable80211BE = false;
  ::aidl::android::hardware::wifi::hostapd::ChannelBandwidth maximumChannelBandwidth = ::aidl::android::hardware::wifi::hostapd::ChannelBandwidth(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) != std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }
  inline bool operator<(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) < std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }
  inline bool operator<=(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) <= std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }
  inline bool operator==(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) == std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }
  inline bool operator>(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) > std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }
  inline bool operator>=(const HwModeParams& rhs) const {
    return std::tie(enable80211N, enable80211AC, enable80211AX, enable6GhzBand, enableHeSingleUserBeamformer, enableHeSingleUserBeamformee, enableHeMultiUserBeamformer, enableHeTargetWakeTime, enableEdmg, enable80211BE, maximumChannelBandwidth) >= std::tie(rhs.enable80211N, rhs.enable80211AC, rhs.enable80211AX, rhs.enable6GhzBand, rhs.enableHeSingleUserBeamformer, rhs.enableHeSingleUserBeamformee, rhs.enableHeMultiUserBeamformer, rhs.enableHeTargetWakeTime, rhs.enableEdmg, rhs.enable80211BE, rhs.maximumChannelBandwidth);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HwModeParams{";
    os << "enable80211N: " << ::android::internal::ToString(enable80211N);
    os << ", enable80211AC: " << ::android::internal::ToString(enable80211AC);
    os << ", enable80211AX: " << ::android::internal::ToString(enable80211AX);
    os << ", enable6GhzBand: " << ::android::internal::ToString(enable6GhzBand);
    os << ", enableHeSingleUserBeamformer: " << ::android::internal::ToString(enableHeSingleUserBeamformer);
    os << ", enableHeSingleUserBeamformee: " << ::android::internal::ToString(enableHeSingleUserBeamformee);
    os << ", enableHeMultiUserBeamformer: " << ::android::internal::ToString(enableHeMultiUserBeamformer);
    os << ", enableHeTargetWakeTime: " << ::android::internal::ToString(enableHeTargetWakeTime);
    os << ", enableEdmg: " << ::android::internal::ToString(enableEdmg);
    os << ", enable80211BE: " << ::android::internal::ToString(enable80211BE);
    os << ", maximumChannelBandwidth: " << ::android::internal::ToString(maximumChannelBandwidth);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
