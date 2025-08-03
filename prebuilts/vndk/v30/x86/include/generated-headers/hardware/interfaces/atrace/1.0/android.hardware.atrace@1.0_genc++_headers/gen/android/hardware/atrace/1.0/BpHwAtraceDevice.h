#ifndef HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_BPHWATRACEDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_BPHWATRACEDEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/atrace/1.0/IHwAtraceDevice.h>

namespace android {
namespace hardware {
namespace atrace {
namespace V1_0 {

struct BpHwAtraceDevice : public ::android::hardware::BpInterface<IAtraceDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwAtraceDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IAtraceDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::atrace::V1_0::IAtraceDevice follow.
    static ::android::hardware::Return<void>  _hidl_listCategories(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, listCategories_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::atrace::V1_0::Status>  _hidl_enableCategories(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& categories);
    static ::android::hardware::Return<::android::hardware::atrace::V1_0::Status>  _hidl_disableAllCategories(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::atrace::V1_0::IAtraceDevice follow.
    ::android::hardware::Return<void> listCategories(listCategories_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::atrace::V1_0::Status> enableCategories(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& categories) override;
    ::android::hardware::Return<::android::hardware::atrace::V1_0::Status> disableAllCategories() override;

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
}  // namespace atrace
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_BPHWATRACEDEVICE_H
