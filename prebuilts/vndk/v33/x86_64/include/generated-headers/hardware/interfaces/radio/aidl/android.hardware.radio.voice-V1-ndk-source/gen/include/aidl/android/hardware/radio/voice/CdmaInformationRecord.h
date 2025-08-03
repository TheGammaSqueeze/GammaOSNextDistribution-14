#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/CdmaDisplayInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaLineControlInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaNumberInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaRedirectingNumberInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaSignalInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaT53AudioControlInfoRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaT53ClirInfoRecord.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class CdmaInformationRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t name = 0;
  std::vector<::aidl::android::hardware::radio::voice::CdmaDisplayInfoRecord> display;
  std::vector<::aidl::android::hardware::radio::voice::CdmaNumberInfoRecord> number;
  std::vector<::aidl::android::hardware::radio::voice::CdmaSignalInfoRecord> signal;
  std::vector<::aidl::android::hardware::radio::voice::CdmaRedirectingNumberInfoRecord> redir;
  std::vector<::aidl::android::hardware::radio::voice::CdmaLineControlInfoRecord> lineCtrl;
  std::vector<::aidl::android::hardware::radio::voice::CdmaT53ClirInfoRecord> clir;
  std::vector<::aidl::android::hardware::radio::voice::CdmaT53AudioControlInfoRecord> audioCtrl;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) != std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }
  inline bool operator<(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) < std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }
  inline bool operator<=(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) <= std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }
  inline bool operator==(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) == std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }
  inline bool operator>(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) > std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }
  inline bool operator>=(const CdmaInformationRecord& rhs) const {
    return std::tie(name, display, number, signal, redir, lineCtrl, clir, audioCtrl) >= std::tie(rhs.name, rhs.display, rhs.number, rhs.signal, rhs.redir, rhs.lineCtrl, rhs.clir, rhs.audioCtrl);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CDMA_MAX_NUMBER_OF_INFO_RECS = 10 };
  enum : int32_t { NAME_DISPLAY = 0 };
  enum : int32_t { NAME_CALLED_PARTY_NUMBER = 1 };
  enum : int32_t { NAME_CALLING_PARTY_NUMBER = 2 };
  enum : int32_t { NAME_CONNECTED_NUMBER = 3 };
  enum : int32_t { NAME_SIGNAL = 4 };
  enum : int32_t { NAME_REDIRECTING_NUMBER = 5 };
  enum : int32_t { NAME_LINE_CONTROL = 6 };
  enum : int32_t { NAME_EXTENDED_DISPLAY = 7 };
  enum : int32_t { NAME_T53_CLIR = 8 };
  enum : int32_t { NAME_T53_RELEASE = 9 };
  enum : int32_t { NAME_T53_AUDIO_CONTROL = 10 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaInformationRecord{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", display: " << ::android::internal::ToString(display);
    os << ", number: " << ::android::internal::ToString(number);
    os << ", signal: " << ::android::internal::ToString(signal);
    os << ", redir: " << ::android::internal::ToString(redir);
    os << ", lineCtrl: " << ::android::internal::ToString(lineCtrl);
    os << ", clir: " << ::android::internal::ToString(clir);
    os << ", audioCtrl: " << ::android::internal::ToString(audioCtrl);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
