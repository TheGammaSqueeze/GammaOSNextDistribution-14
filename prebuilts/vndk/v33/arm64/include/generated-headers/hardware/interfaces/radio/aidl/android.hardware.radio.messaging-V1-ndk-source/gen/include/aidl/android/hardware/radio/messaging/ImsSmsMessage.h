#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioTechnologyFamily.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsMessage.h>
#include <aidl/android/hardware/radio/messaging/GsmSmsMessage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class ImsSmsMessage {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::RadioTechnologyFamily tech = ::aidl::android::hardware::radio::RadioTechnologyFamily(0);
  bool retry = false;
  int32_t messageRef = 0;
  std::vector<::aidl::android::hardware::radio::messaging::CdmaSmsMessage> cdmaMessage;
  std::vector<::aidl::android::hardware::radio::messaging::GsmSmsMessage> gsmMessage;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) != std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }
  inline bool operator<(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) < std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }
  inline bool operator<=(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) <= std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }
  inline bool operator==(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) == std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }
  inline bool operator>(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) > std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }
  inline bool operator>=(const ImsSmsMessage& rhs) const {
    return std::tie(tech, retry, messageRef, cdmaMessage, gsmMessage) >= std::tie(rhs.tech, rhs.retry, rhs.messageRef, rhs.cdmaMessage, rhs.gsmMessage);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ImsSmsMessage{";
    os << "tech: " << ::android::internal::ToString(tech);
    os << ", retry: " << ::android::internal::ToString(retry);
    os << ", messageRef: " << ::android::internal::ToString(messageRef);
    os << ", cdmaMessage: " << ::android::internal::ToString(cdmaMessage);
    os << ", gsmMessage: " << ::android::internal::ToString(gsmMessage);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
