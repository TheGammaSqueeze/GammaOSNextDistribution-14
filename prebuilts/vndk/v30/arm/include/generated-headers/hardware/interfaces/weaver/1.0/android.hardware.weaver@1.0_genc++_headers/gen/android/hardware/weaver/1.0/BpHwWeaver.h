#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BPHWWEAVER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BPHWWEAVER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/weaver/1.0/IHwWeaver.h>

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {

struct BpHwWeaver : public ::android::hardware::BpInterface<IWeaver>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWeaver(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWeaver Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::weaver::V1_0::IWeaver follow.
    static ::android::hardware::Return<void>  _hidl_getConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConfig_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::weaver::V1_0::WeaverStatus>  _hidl_write(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t slotId, const ::android::hardware::hidl_vec<uint8_t>& key, const ::android::hardware::hidl_vec<uint8_t>& value);
    static ::android::hardware::Return<void>  _hidl_read(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t slotId, const ::android::hardware::hidl_vec<uint8_t>& key, read_cb _hidl_cb);

    // Methods from ::android::hardware::weaver::V1_0::IWeaver follow.
    ::android::hardware::Return<void> getConfig(getConfig_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::weaver::V1_0::WeaverStatus> write(uint32_t slotId, const ::android::hardware::hidl_vec<uint8_t>& key, const ::android::hardware::hidl_vec<uint8_t>& value) override;
    ::android::hardware::Return<void> read(uint32_t slotId, const ::android::hardware::hidl_vec<uint8_t>& key, read_cb _hidl_cb) override;

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
}  // namespace weaver
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BPHWWEAVER_H
