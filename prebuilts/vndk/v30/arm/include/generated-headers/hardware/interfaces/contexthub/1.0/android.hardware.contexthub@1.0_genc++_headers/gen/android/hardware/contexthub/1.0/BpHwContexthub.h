#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUB_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/contexthub/1.0/IHwContexthub.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {

struct BpHwContexthub : public ::android::hardware::BpInterface<IContexthub>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwContexthub(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IContexthub Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::contexthub::V1_0::IContexthub follow.
    static ::android::hardware::Return<void>  _hidl_getHubs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getHubs_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_registerCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, const ::android::sp<::android::hardware::contexthub::V1_0::IContexthubCallback>& cb);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_sendMessageToHub(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, const ::android::hardware::contexthub::V1_0::ContextHubMsg& msg);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_loadNanoApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, const ::android::hardware::contexthub::V1_0::NanoAppBinary& appBinary, uint32_t transactionId);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_unloadNanoApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, uint64_t appId, uint32_t transactionId);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_enableNanoApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, uint64_t appId, uint32_t transactionId);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_disableNanoApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId, uint64_t appId, uint32_t transactionId);
    static ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result>  _hidl_queryApps(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hubId);

    // Methods from ::android::hardware::contexthub::V1_0::IContexthub follow.
    ::android::hardware::Return<void> getHubs(getHubs_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> registerCallback(uint32_t hubId, const ::android::sp<::android::hardware::contexthub::V1_0::IContexthubCallback>& cb) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> sendMessageToHub(uint32_t hubId, const ::android::hardware::contexthub::V1_0::ContextHubMsg& msg) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> loadNanoApp(uint32_t hubId, const ::android::hardware::contexthub::V1_0::NanoAppBinary& appBinary, uint32_t transactionId) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> unloadNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> enableNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> disableNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
    ::android::hardware::Return<::android::hardware::contexthub::V1_0::Result> queryApps(uint32_t hubId) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BPHWCONTEXTHUB_H
