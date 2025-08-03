#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIGRESPONSE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIGRESPONSE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/config/1.1/IHwRadioConfigResponse.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_1 {

struct BpHwRadioConfigResponse : public ::android::hardware::BpInterface<IRadioConfigResponse>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwRadioConfigResponse(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioConfigResponse Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::config::V1_1::IRadioConfigResponse follow.
    static ::android::hardware::Return<void>  _hidl_getPhoneCapabilityResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::config::V1_1::PhoneCapability& phoneCapability);
    static ::android::hardware::Return<void>  _hidl_setPreferredDataModemResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setModemsConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getModemsConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig);

    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfigResponse follow.
    ::android::hardware::Return<void> getSimSlotsStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::config::V1_0::SimSlotStatus>& slotStatus) override;
    ::android::hardware::Return<void> setSimSlotsMappingResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;

    // Methods from ::android::hardware::radio::config::V1_1::IRadioConfigResponse follow.
    ::android::hardware::Return<void> getPhoneCapabilityResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::config::V1_1::PhoneCapability& phoneCapability) override;
    ::android::hardware::Return<void> setPreferredDataModemResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setModemsConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getModemsConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_1
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIGRESPONSE_H
