#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessagingResponse.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class BnRadioMessagingResponse : public ::ndk::BnCInterface<IRadioMessagingResponse> {
public:
  BnRadioMessagingResponse();
  virtual ~BnRadioMessagingResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioMessagingResponseDelegator : public BnRadioMessagingResponse {
public:
  explicit IRadioMessagingResponseDelegator(const std::shared_ptr<IRadioMessagingResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioMessagingResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPduResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->acknowledgeIncomingGsmSmsWithPduResponse(in_info);
  }
  ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->acknowledgeLastIncomingCdmaSmsResponse(in_info);
  }
  ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->acknowledgeLastIncomingGsmSmsResponse(in_info);
  }
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus deleteSmsOnRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->deleteSmsOnRuimResponse(in_info);
  }
  ::ndk::ScopedAStatus deleteSmsOnSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->deleteSmsOnSimResponse(in_info);
  }
  ::ndk::ScopedAStatus getCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configs) override {
    return _impl->getCdmaBroadcastConfigResponse(in_info, in_configs);
  }
  ::ndk::ScopedAStatus getGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configs) override {
    return _impl->getGsmBroadcastConfigResponse(in_info, in_configs);
  }
  ::ndk::ScopedAStatus getSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_smsc) override {
    return _impl->getSmscAddressResponse(in_info, in_smsc);
  }
  ::ndk::ScopedAStatus reportSmsMemoryStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->reportSmsMemoryStatusResponse(in_info);
  }
  ::ndk::ScopedAStatus sendCdmaSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override {
    return _impl->sendCdmaSmsExpectMoreResponse(in_info, in_sms);
  }
  ::ndk::ScopedAStatus sendCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override {
    return _impl->sendCdmaSmsResponse(in_info, in_sms);
  }
  ::ndk::ScopedAStatus sendImsSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override {
    return _impl->sendImsSmsResponse(in_info, in_sms);
  }
  ::ndk::ScopedAStatus sendSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override {
    return _impl->sendSmsExpectMoreResponse(in_info, in_sms);
  }
  ::ndk::ScopedAStatus sendSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override {
    return _impl->sendSmsResponse(in_info, in_sms);
  }
  ::ndk::ScopedAStatus setCdmaBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCdmaBroadcastActivationResponse(in_info);
  }
  ::ndk::ScopedAStatus setCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCdmaBroadcastConfigResponse(in_info);
  }
  ::ndk::ScopedAStatus setGsmBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setGsmBroadcastActivationResponse(in_info);
  }
  ::ndk::ScopedAStatus setGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setGsmBroadcastConfigResponse(in_info);
  }
  ::ndk::ScopedAStatus setSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setSmscAddressResponse(in_info);
  }
  ::ndk::ScopedAStatus writeSmsToRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) override {
    return _impl->writeSmsToRuimResponse(in_info, in_index);
  }
  ::ndk::ScopedAStatus writeSmsToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) override {
    return _impl->writeSmsToSimResponse(in_info, in_index);
  }
protected:
private:
  std::shared_ptr<IRadioMessagingResponse> _impl;
};

}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
