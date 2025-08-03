#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWDEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/renderscript/1.0/IHwDevice.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {

struct BpHwDevice : public ::android::hardware::BpInterface<IDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::renderscript::V1_0::IDevice follow.
    static ::android::hardware::Return<::android::sp<::android::hardware::renderscript::V1_0::IContext>>  _hidl_contextCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t sdkVersion, ::android::hardware::renderscript::V1_0::ContextType ct, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ContextFlags> flags);

    // Methods from ::android::hardware::renderscript::V1_0::IDevice follow.
    ::android::hardware::Return<::android::sp<::android::hardware::renderscript::V1_0::IContext>> contextCreate(uint32_t sdkVersion, ::android::hardware::renderscript::V1_0::ContextType ct, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ContextFlags> flags) override;

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

}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWDEVICE_H
