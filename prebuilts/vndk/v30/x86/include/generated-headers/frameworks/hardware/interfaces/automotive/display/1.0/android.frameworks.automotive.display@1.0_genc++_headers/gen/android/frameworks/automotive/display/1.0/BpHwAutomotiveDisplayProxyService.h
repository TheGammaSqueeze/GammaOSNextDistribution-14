#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BPHWAUTOMOTIVEDISPLAYPROXYSERVICE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BPHWAUTOMOTIVEDISPLAYPROXYSERVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/automotive/display/1.0/IHwAutomotiveDisplayProxyService.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace display {
namespace V1_0 {

struct BpHwAutomotiveDisplayProxyService : public ::android::hardware::BpInterface<IAutomotiveDisplayProxyService>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwAutomotiveDisplayProxyService(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IAutomotiveDisplayProxyService Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::automotive::display::V1_0::IAutomotiveDisplayProxyService follow.
    static ::android::hardware::Return<::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>>  _hidl_getIGraphicBufferProducer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t id);
    static ::android::hardware::Return<bool>  _hidl_showWindow(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t id);
    static ::android::hardware::Return<bool>  _hidl_hideWindow(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t id);
    static ::android::hardware::Return<void>  _hidl_getDisplayIdList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDisplayIdList_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDisplayInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t id, getDisplayInfo_cb _hidl_cb);

    // Methods from ::android::frameworks::automotive::display::V1_0::IAutomotiveDisplayProxyService follow.
    ::android::hardware::Return<::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>> getIGraphicBufferProducer(uint64_t id) override;
    ::android::hardware::Return<bool> showWindow(uint64_t id) override;
    ::android::hardware::Return<bool> hideWindow(uint64_t id) override;
    ::android::hardware::Return<void> getDisplayIdList(getDisplayIdList_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDisplayInfo(uint64_t id, getDisplayInfo_cb _hidl_cb) override;

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
}  // namespace display
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BPHWAUTOMOTIVEDISPLAYPROXYSERVICE_H
