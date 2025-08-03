#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessagingIndication.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class BpRadioMessagingIndication : public ::ndk::BpCInterface<IRadioMessagingIndication> {
public:
  explicit BpRadioMessagingIndication(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioMessagingIndication();

  ::ndk::ScopedAStatus cdmaNewSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_msg) override;
  ::ndk::ScopedAStatus cdmaRuimSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus newBroadcastSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus newSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override;
  ::ndk::ScopedAStatus newSmsOnSim(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_recordNumber) override;
  ::ndk::ScopedAStatus newSmsStatusReport(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override;
  ::ndk::ScopedAStatus simSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
