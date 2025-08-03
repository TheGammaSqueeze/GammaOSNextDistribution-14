#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCASLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCASLISTENER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/cas/1.0/IHwCasListener.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {

struct BpHwCasListener : public ::android::hardware::BpInterface<ICasListener>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCasListener(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICasListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::cas::V1_0::ICasListener follow.
    static ::android::hardware::Return<void>  _hidl_onEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& data);

    // Methods from ::android::hardware::cas::V1_0::ICasListener follow.
    ::android::hardware::Return<void> onEvent(int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& data) override;

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
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCASLISTENER_H
