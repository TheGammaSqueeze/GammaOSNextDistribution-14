#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICEC_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICEC_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/cec/2.0/IHwHdmiCec.h>

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V2_0 {

struct BpHwHdmiCec : public ::android::hardware::BpInterface<IHdmiCec>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwHdmiCec(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IHdmiCec Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::cec::V2_0::IHdmiCec follow.
    static ::android::hardware::Return<::android::hardware::tv::cec::V2_0::Result>  _hidl_addDeviceType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::cec::V2_0::CecDeviceType deviceType);
    static ::android::hardware::Return<void>  _hidl_clearDeviceTypes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_setAllDeviceTypes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> allDeviceTypes);
    static ::android::hardware::Return<void>  _hidl_setDeviceFeatures(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::cec::V2_0::CecDeviceType deviceType, ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceFeature> deviceFeatures);
    static ::android::hardware::Return<void>  _hidl_setRcProfile(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::cec::V2_0::CecDeviceType deviceType, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rcProfile);
    static ::android::hardware::Return<void>  _hidl_readDeviceInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::cec::V2_0::CecLogicalAddress logicalAddress, uint16_t physicalAddress, readDeviceInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::tv::cec::V2_0::SendMessageResult>  _hidl_sendMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::cec::V2_0::CecMessage& message);
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tv::cec::V2_0::IHdmiCecCallback>& callback);
    static ::android::hardware::Return<void>  _hidl_getPortInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPortInfo_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setOption(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::cec::V2_0::OptionKey key, bool value);
    static ::android::hardware::Return<void>  _hidl_setLanguage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& language);
    static ::android::hardware::Return<void>  _hidl_enableAudioReturnChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portId, bool enable);
    static ::android::hardware::Return<bool>  _hidl_isConnected(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portId);

    // Methods from ::android::hardware::tv::cec::V2_0::IHdmiCec follow.
    ::android::hardware::Return<::android::hardware::tv::cec::V2_0::Result> addDeviceType(::android::hardware::tv::cec::V2_0::CecDeviceType deviceType) override;
    ::android::hardware::Return<void> clearDeviceTypes() override;
    ::android::hardware::Return<void> setAllDeviceTypes(::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> allDeviceTypes) override;
    ::android::hardware::Return<void> setDeviceFeatures(::android::hardware::tv::cec::V2_0::CecDeviceType deviceType, ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceFeature> deviceFeatures) override;
    ::android::hardware::Return<void> setRcProfile(::android::hardware::tv::cec::V2_0::CecDeviceType deviceType, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rcProfile) override;
    ::android::hardware::Return<void> readDeviceInfo(::android::hardware::tv::cec::V2_0::CecLogicalAddress logicalAddress, uint16_t physicalAddress, readDeviceInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::tv::cec::V2_0::SendMessageResult> sendMessage(const ::android::hardware::tv::cec::V2_0::CecMessage& message) override;
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::tv::cec::V2_0::IHdmiCecCallback>& callback) override;
    ::android::hardware::Return<void> getPortInfo(getPortInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> setOption(::android::hardware::tv::cec::V2_0::OptionKey key, bool value) override;
    ::android::hardware::Return<void> setLanguage(const ::android::hardware::hidl_string& language) override;
    ::android::hardware::Return<void> enableAudioReturnChannel(uint32_t portId, bool enable) override;
    ::android::hardware::Return<bool> isConnected(uint32_t portId) override;

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

}  // namespace V2_0
}  // namespace cec
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICEC_H
