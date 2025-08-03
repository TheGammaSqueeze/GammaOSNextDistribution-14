#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BPHWFOCUSLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BPHWFOCUSLISTENER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/audiocontrol/2.0/IHwFocusListener.h>

namespace android {
namespace hardware {
namespace automotive {
namespace audiocontrol {
namespace V2_0 {

struct BpHwFocusListener : public ::android::hardware::BpInterface<IFocusListener>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwFocusListener(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IFocusListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::audiocontrol::V2_0::IFocusListener follow.
    static ::android::hardware::Return<void>  _hidl_requestAudioFocus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioUsage> usage, int32_t zoneId, ::android::hardware::hidl_bitfield<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange> focusGain);
    static ::android::hardware::Return<void>  _hidl_abandonAudioFocus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioUsage> usage, int32_t zoneId);

    // Methods from ::android::hardware::automotive::audiocontrol::V2_0::IFocusListener follow.
    ::android::hardware::Return<void> requestAudioFocus(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioUsage> usage, int32_t zoneId, ::android::hardware::hidl_bitfield<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange> focusGain) override;
    ::android::hardware::Return<void> abandonAudioFocus(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioUsage> usage, int32_t zoneId) override;

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
}  // namespace audiocontrol
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BPHWFOCUSLISTENER_H
