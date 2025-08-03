#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModemResponse.h"

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
namespace modem {
class BnRadioModemResponse : public ::ndk::BnCInterface<IRadioModemResponse> {
public:
  BnRadioModemResponse();
  virtual ~BnRadioModemResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioModemResponseDelegator : public BnRadioModemResponse {
public:
  explicit IRadioModemResponseDelegator(const std::shared_ptr<IRadioModemResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioModemResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus enableModemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->enableModemResponse(in_info);
  }
  ::ndk::ScopedAStatus getBasebandVersionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_version) override {
    return _impl->getBasebandVersionResponse(in_info, in_version);
  }
  ::ndk::ScopedAStatus getDeviceIdentityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imei, const std::string& in_imeisv, const std::string& in_esn, const std::string& in_meid) override {
    return _impl->getDeviceIdentityResponse(in_info, in_imei, in_imeisv, in_esn, in_meid);
  }
  ::ndk::ScopedAStatus getHardwareConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_config) override {
    return _impl->getHardwareConfigResponse(in_info, in_config);
  }
  ::ndk::ScopedAStatus getModemActivityInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::ActivityStatsInfo& in_activityInfo) override {
    return _impl->getModemActivityInfoResponse(in_info, in_activityInfo);
  }
  ::ndk::ScopedAStatus getModemStackStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) override {
    return _impl->getModemStackStatusResponse(in_info, in_isEnabled);
  }
  ::ndk::ScopedAStatus getRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override {
    return _impl->getRadioCapabilityResponse(in_info, in_rc);
  }
  ::ndk::ScopedAStatus nvReadItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_result) override {
    return _impl->nvReadItemResponse(in_info, in_result);
  }
  ::ndk::ScopedAStatus nvResetConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->nvResetConfigResponse(in_info);
  }
  ::ndk::ScopedAStatus nvWriteCdmaPrlResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->nvWriteCdmaPrlResponse(in_info);
  }
  ::ndk::ScopedAStatus nvWriteItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->nvWriteItemResponse(in_info);
  }
  ::ndk::ScopedAStatus requestShutdownResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->requestShutdownResponse(in_info);
  }
  ::ndk::ScopedAStatus sendDeviceStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendDeviceStateResponse(in_info);
  }
  ::ndk::ScopedAStatus setRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override {
    return _impl->setRadioCapabilityResponse(in_info, in_rc);
  }
  ::ndk::ScopedAStatus setRadioPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setRadioPowerResponse(in_info);
  }
protected:
private:
  std::shared_ptr<IRadioModemResponse> _impl;
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
