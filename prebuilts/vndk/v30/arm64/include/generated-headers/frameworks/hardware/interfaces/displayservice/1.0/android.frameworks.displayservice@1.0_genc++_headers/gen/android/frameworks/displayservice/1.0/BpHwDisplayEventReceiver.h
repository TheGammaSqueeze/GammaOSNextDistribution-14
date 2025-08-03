#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWDISPLAYEVENTRECEIVER_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWDISPLAYEVENTRECEIVER_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/displayservice/1.0/IHwDisplayEventReceiver.h>

namespace android {
namespace frameworks {
namespace displayservice {
namespace V1_0 {

struct BpHwDisplayEventReceiver : public ::android::hardware::BpInterface<IDisplayEventReceiver>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDisplayEventReceiver(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDisplayEventReceiver Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::displayservice::V1_0::IDisplayEventReceiver follow.
    static ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status>  _hidl_init(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::frameworks::displayservice::V1_0::IEventCallback>& callback);
    static ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status>  _hidl_setVsyncRate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t count);
    static ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status>  _hidl_requestNextVsync(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::frameworks::displayservice::V1_0::IDisplayEventReceiver follow.
    ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status> init(const ::android::sp<::android::frameworks::displayservice::V1_0::IEventCallback>& callback) override;
    ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status> setVsyncRate(int32_t count) override;
    ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status> requestNextVsync() override;
    ::android::hardware::Return<::android::frameworks::displayservice::V1_0::Status> close() override;

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

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_BPHWDISPLAYEVENTRECEIVER_H
