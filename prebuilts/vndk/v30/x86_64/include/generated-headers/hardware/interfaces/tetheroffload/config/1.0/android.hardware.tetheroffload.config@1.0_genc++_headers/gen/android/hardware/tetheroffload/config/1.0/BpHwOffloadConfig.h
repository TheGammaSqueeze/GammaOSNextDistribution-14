#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BPHWOFFLOADCONFIG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BPHWOFFLOADCONFIG_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tetheroffload/config/1.0/IHwOffloadConfig.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace config {
namespace V1_0 {

struct BpHwOffloadConfig : public ::android::hardware::BpInterface<IOffloadConfig>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwOffloadConfig(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IOffloadConfig Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tetheroffload::config::V1_0::IOffloadConfig follow.
    static ::android::hardware::Return<void>  _hidl_setHandles(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_handle& fd1, const ::android::hardware::hidl_handle& fd2, setHandles_cb _hidl_cb);

    // Methods from ::android::hardware::tetheroffload::config::V1_0::IOffloadConfig follow.
    ::android::hardware::Return<void> setHandles(const ::android::hardware::hidl_handle& fd1, const ::android::hardware::hidl_handle& fd2, setHandles_cb _hidl_cb) override;

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
}  // namespace config
}  // namespace tetheroffload
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BPHWOFFLOADCONFIG_H
