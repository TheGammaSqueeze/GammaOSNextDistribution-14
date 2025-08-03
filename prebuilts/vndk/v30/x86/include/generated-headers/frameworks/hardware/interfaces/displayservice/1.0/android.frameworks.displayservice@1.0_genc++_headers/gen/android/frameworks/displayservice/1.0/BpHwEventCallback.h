#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWEVENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/displayservice/1.0/IHwEventCallback.h>

namespace android {
namespace frameworks {
namespace displayservice {
namespace V1_0 {

struct BpHwEventCallback : public ::android::hardware::BpInterface<IEventCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEventCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEventCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::displayservice::V1_0::IEventCallback follow.
    static ::android::hardware::Return<void>  _hidl_onVsync(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t timestamp, uint32_t count);
    static ::android::hardware::Return<void>  _hidl_onHotplug(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t timestamp, bool connected);

    // Methods from ::android::frameworks::displayservice::V1_0::IEventCallback follow.
    ::android::hardware::Return<void> onVsync(uint64_t timestamp, uint32_t count) override;
    ::android::hardware::Return<void> onHotplug(uint64_t timestamp, bool connected) override;

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
}  // namespace displayservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWEVENTCALLBACK_H
