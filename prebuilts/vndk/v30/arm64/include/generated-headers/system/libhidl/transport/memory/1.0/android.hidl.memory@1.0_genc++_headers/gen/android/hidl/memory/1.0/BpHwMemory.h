#ifndef HIDL_GENERATED_ANDROID_HIDL_MEMORY_V1_0_BPHWMEMORY_H
#define HIDL_GENERATED_ANDROID_HIDL_MEMORY_V1_0_BPHWMEMORY_H

#include <hidl/HidlTransportSupport.h>

#include <android/hidl/memory/1.0/IHwMemory.h>

namespace android {
namespace hidl {
namespace memory {
namespace V1_0 {

struct BpHwMemory : public ::android::hardware::BpInterface<IMemory>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwMemory(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IMemory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hidl::memory::V1_0::IMemory follow.
    static ::android::hardware::Return<void>  _hidl_update(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_updateRange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t start, uint64_t length);
    static ::android::hardware::Return<void>  _hidl_read(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_readRange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t start, uint64_t length);
    static ::android::hardware::Return<void>  _hidl_commit(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void*>  _hidl_getPointer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<uint64_t>  _hidl_getSize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hidl::memory::V1_0::IMemory follow.
    ::android::hardware::Return<void> update() override;
    ::android::hardware::Return<void> updateRange(uint64_t start, uint64_t length) override;
    ::android::hardware::Return<void> read() override;
    ::android::hardware::Return<void> readRange(uint64_t start, uint64_t length) override;
    ::android::hardware::Return<void> commit() override;
    ::android::hardware::Return<void*> getPointer() override;
    ::android::hardware::Return<uint64_t> getSize() override;

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
}  // namespace memory
}  // namespace hidl
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HIDL_MEMORY_V1_0_BPHWMEMORY_H
