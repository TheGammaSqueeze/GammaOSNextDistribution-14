#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIG_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/config/1.1/IHwRadioConfig.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_1 {

struct BpHwRadioConfig : public ::android::hardware::BpInterface<IRadioConfig>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwRadioConfig(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioConfig Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::config::V1_1::IRadioConfig follow.
    static ::android::hardware::Return<void>  _hidl_getPhoneCapability(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setPreferredDataModem(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, uint8_t modemId);
    static ::android::hardware::Return<void>  _hidl_setModemsConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig);
    static ::android::hardware::Return<void>  _hidl_getModemsConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);

    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfig follow.
    ::android::hardware::Return<void> setResponseFunctions(const ::android::sp<::android::hardware::radio::config::V1_0::IRadioConfigResponse>& radioConfigResponse, const ::android::sp<::android::hardware::radio::config::V1_0::IRadioConfigIndication>& radioConfigIndication) override;
    ::android::hardware::Return<void> getSimSlotsStatus(int32_t serial) override;
    ::android::hardware::Return<void> setSimSlotsMapping(int32_t serial, const ::android::hardware::hidl_vec<uint32_t>& slotMap) override;

    // Methods from ::android::hardware::radio::config::V1_1::IRadioConfig follow.
    ::android::hardware::Return<void> getPhoneCapability(int32_t serial) override;
    ::android::hardware::Return<void> setPreferredDataModem(int32_t serial, uint8_t modemId) override;
    ::android::hardware::Return<void> setModemsConfig(int32_t serial, const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig) override;
    ::android::hardware::Return<void> getModemsConfig(int32_t serial) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_BPHWRADIOCONFIG_H
