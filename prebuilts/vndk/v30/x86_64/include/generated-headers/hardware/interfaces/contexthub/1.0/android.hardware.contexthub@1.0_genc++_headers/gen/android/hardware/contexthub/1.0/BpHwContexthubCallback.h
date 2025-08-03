#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUBCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUBCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/contexthub/1.0/IHwContexthubCallback.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {

struct BpHwContexthubCallback : public ::android::hardware::BpInterface<IContexthubCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwContexthubCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IContexthubCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::contexthub::V1_0::IContexthubCallback follow.
    static ::android::hardware::Return<void>  _hidl_handleClientMsg(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::contexthub::V1_0::ContextHubMsg& msg);
    static ::android::hardware::Return<void>  _hidl_handleTxnResult(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t txnId, ::android::hardware::contexthub::V1_0::TransactionResult result);
    static ::android::hardware::Return<void>  _hidl_handleHubEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::contexthub::V1_0::AsyncEventType evt);
    static ::android::hardware::Return<void>  _hidl_handleAppAbort(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t appId, uint32_t abortCode);
    static ::android::hardware::Return<void>  _hidl_handleAppsInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::contexthub::V1_0::HubAppInfo>& appInfo);

    // Methods from ::android::hardware::contexthub::V1_0::IContexthubCallback follow.
    ::android::hardware::Return<void> handleClientMsg(const ::android::hardware::contexthub::V1_0::ContextHubMsg& msg) override;
    ::android::hardware::Return<void> handleTxnResult(uint32_t txnId, ::android::hardware::contexthub::V1_0::TransactionResult result) override;
    ::android::hardware::Return<void> handleHubEvent(::android::hardware::contexthub::V1_0::AsyncEventType evt) override;
    ::android::hardware::Return<void> handleAppAbort(uint64_t appId, uint32_t abortCode) override;
    ::android::hardware::Return<void> handleAppsInfo(const ::android::hardware::hidl_vec<::android::hardware::contexthub::V1_0::HubAppInfo>& appInfo) override;

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
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUBCALLBACK_H
