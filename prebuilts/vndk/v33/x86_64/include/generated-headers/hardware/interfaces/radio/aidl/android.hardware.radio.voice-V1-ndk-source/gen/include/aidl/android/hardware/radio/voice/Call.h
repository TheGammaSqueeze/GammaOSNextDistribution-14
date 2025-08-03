#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/AudioQuality.h>
#include <aidl/android/hardware/radio/voice/UusInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class Call {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t state = 0;
  int32_t index = 0;
  int32_t toa = 0;
  bool isMpty = false;
  bool isMT = false;
  int8_t als = 0;
  bool isVoice = false;
  bool isVoicePrivacy = false;
  std::string number;
  int32_t numberPresentation = 0;
  std::string name;
  int32_t namePresentation = 0;
  std::vector<::aidl::android::hardware::radio::voice::UusInfo> uusInfo;
  ::aidl::android::hardware::radio::voice::AudioQuality audioQuality = ::aidl::android::hardware::radio::voice::AudioQuality(0);
  std::string forwardedNumber;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) != std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }
  inline bool operator<(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) < std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }
  inline bool operator<=(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) <= std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }
  inline bool operator==(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) == std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }
  inline bool operator>(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) > std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }
  inline bool operator>=(const Call& rhs) const {
    return std::tie(state, index, toa, isMpty, isMT, als, isVoice, isVoicePrivacy, number, numberPresentation, name, namePresentation, uusInfo, audioQuality, forwardedNumber) >= std::tie(rhs.state, rhs.index, rhs.toa, rhs.isMpty, rhs.isMT, rhs.als, rhs.isVoice, rhs.isVoicePrivacy, rhs.number, rhs.numberPresentation, rhs.name, rhs.namePresentation, rhs.uusInfo, rhs.audioQuality, rhs.forwardedNumber);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { PRESENTATION_ALLOWED = 0 };
  enum : int32_t { PRESENTATION_RESTRICTED = 1 };
  enum : int32_t { PRESENTATION_UNKNOWN = 2 };
  enum : int32_t { PRESENTATION_PAYPHONE = 3 };
  enum : int32_t { STATE_ACTIVE = 0 };
  enum : int32_t { STATE_HOLDING = 1 };
  enum : int32_t { STATE_DIALING = 2 };
  enum : int32_t { STATE_ALERTING = 3 };
  enum : int32_t { STATE_INCOMING = 4 };
  enum : int32_t { STATE_WAITING = 5 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "Call{";
    os << "state: " << ::android::internal::ToString(state);
    os << ", index: " << ::android::internal::ToString(index);
    os << ", toa: " << ::android::internal::ToString(toa);
    os << ", isMpty: " << ::android::internal::ToString(isMpty);
    os << ", isMT: " << ::android::internal::ToString(isMT);
    os << ", als: " << ::android::internal::ToString(als);
    os << ", isVoice: " << ::android::internal::ToString(isVoice);
    os << ", isVoicePrivacy: " << ::android::internal::ToString(isVoicePrivacy);
    os << ", number: " << ::android::internal::ToString(number);
    os << ", numberPresentation: " << ::android::internal::ToString(numberPresentation);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", namePresentation: " << ::android::internal::ToString(namePresentation);
    os << ", uusInfo: " << ::android::internal::ToString(uusInfo);
    os << ", audioQuality: " << ::android::internal::ToString(audioQuality);
    os << ", forwardedNumber: " << ::android::internal::ToString(forwardedNumber);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
