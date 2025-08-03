#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsMessage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class IRadioMessagingIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioMessagingIndication();
  virtual ~IRadioMessagingIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "5237ec5f500627b6b844b155e356e603157f9ba6";
  static constexpr uint32_t TRANSACTION_cdmaNewSms = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cdmaRuimSmsStorageFull = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_newBroadcastSms = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_newSms = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_newSmsOnSim = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_newSmsStatusReport = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_simSmsStorageFull = FIRST_CALL_TRANSACTION + 6;

  static std::shared_ptr<IRadioMessagingIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioMessagingIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioMessagingIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioMessagingIndication>& impl);
  static const std::shared_ptr<IRadioMessagingIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus cdmaNewSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_msg) = 0;
  virtual ::ndk::ScopedAStatus cdmaRuimSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus newBroadcastSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_data) = 0;
  virtual ::ndk::ScopedAStatus newSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) = 0;
  virtual ::ndk::ScopedAStatus newSmsOnSim(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_recordNumber) = 0;
  virtual ::ndk::ScopedAStatus newSmsStatusReport(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) = 0;
  virtual ::ndk::ScopedAStatus simSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioMessagingIndication> default_impl;
};
class IRadioMessagingIndicationDefault : public IRadioMessagingIndication {
public:
  ::ndk::ScopedAStatus cdmaNewSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_msg) override;
  ::ndk::ScopedAStatus cdmaRuimSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus newBroadcastSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus newSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override;
  ::ndk::ScopedAStatus newSmsOnSim(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_recordNumber) override;
  ::ndk::ScopedAStatus newSmsStatusReport(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override;
  ::ndk::ScopedAStatus simSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
