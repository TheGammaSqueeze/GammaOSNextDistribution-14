#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICECCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICECCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/cec/2.0/IHwHdmiCecCallback.h>

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V2_0 {

struct BpHwHdmiCecCallback : public ::android::hardware::BpInterface<IHdmiCecCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwHdmiCecCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IHdmiCecCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::cec::V2_0::IHdmiCecCallback follow.
    static ::android::hardware::Return<void>  _hidl_onCecMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::cec::V2_0::CecMessage& message);
    static ::android::hardware::Return<void>  _hidl_onHotplugEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::cec::V2_0::HotplugEvent& event);
    static ::android::hardware::Return<void>  _hidl_onTopologyEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& event);

    // Methods from ::android::hardware::tv::cec::V2_0::IHdmiCecCallback follow.
    ::android::hardware::Return<void> onCecMessage(const ::android::hardware::tv::cec::V2_0::CecMessage& message) override;
    ::android::hardware::Return<void> onHotplugEvent(const ::android::hardware::tv::cec::V2_0::HotplugEvent& event) override;
    ::android::hardware::Return<void> onTopologyEvent(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& event) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_BPHWHDMICECCALLBACK_H
