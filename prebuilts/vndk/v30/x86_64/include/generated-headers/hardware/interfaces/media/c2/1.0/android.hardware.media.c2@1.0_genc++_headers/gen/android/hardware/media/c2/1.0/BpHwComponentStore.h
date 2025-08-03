#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENTSTORE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENTSTORE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/media/c2/1.0/IHwComponentStore.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {

struct BpHwComponentStore : public ::android::hardware::BpInterface<IComponentStore>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwComponentStore(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IComponentStore Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::media::c2::V1_0::IComponentStore follow.
    static ::android::hardware::Return<void>  _hidl_createComponent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& name, const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& listener, const ::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>& pool, createComponent_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createInterface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& name, createInterface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_listComponents(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, listComponents_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createInputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createInputSurface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getStructDescriptors(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint32_t>& indices, getStructDescriptors_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_copyBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::media::c2::V1_0::Buffer& src, const ::android::hardware::media::c2::V1_0::Buffer& dst);
    static ::android::hardware::Return<::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>>  _hidl_getPoolClientManager(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>>  _hidl_getConfigurable(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::media::c2::V1_0::IComponentStore follow.
    ::android::hardware::Return<void> createComponent(const ::android::hardware::hidl_string& name, const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& listener, const ::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>& pool, createComponent_cb _hidl_cb) override;
    ::android::hardware::Return<void> createInterface(const ::android::hardware::hidl_string& name, createInterface_cb _hidl_cb) override;
    ::android::hardware::Return<void> listComponents(listComponents_cb _hidl_cb) override;
    ::android::hardware::Return<void> createInputSurface(createInputSurface_cb _hidl_cb) override;
    ::android::hardware::Return<void> getStructDescriptors(const ::android::hardware::hidl_vec<uint32_t>& indices, getStructDescriptors_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> copyBuffer(const ::android::hardware::media::c2::V1_0::Buffer& src, const ::android::hardware::media::c2::V1_0::Buffer& dst) override;
    ::android::hardware::Return<::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>> getPoolClientManager() override;
    ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>> getConfigurable() override;

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
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENTSTORE_H
