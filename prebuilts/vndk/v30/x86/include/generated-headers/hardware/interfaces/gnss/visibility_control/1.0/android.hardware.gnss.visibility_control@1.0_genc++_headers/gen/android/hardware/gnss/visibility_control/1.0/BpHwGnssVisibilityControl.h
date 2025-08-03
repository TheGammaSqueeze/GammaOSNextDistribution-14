#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_BPHWGNSSVISIBILITYCONTROL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_BPHWGNSSVISIBILITYCONTROL_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/visibility_control/1.0/IHwGnssVisibilityControl.h>

namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
namespace V1_0 {

struct BpHwGnssVisibilityControl : public ::android::hardware::BpInterface<IGnssVisibilityControl>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGnssVisibilityControl(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssVisibilityControl Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl follow.
    static ::android::hardware::Return<bool>  _hidl_enableNfwLocationAccess(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& proxyApps);
    static ::android::hardware::Return<bool>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControlCallback>& callback);

    // Methods from ::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl follow.
    ::android::hardware::Return<bool> enableNfwLocationAccess(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& proxyApps) override;
    ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControlCallback>& callback) override;

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
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_BPHWGNSSVISIBILITYCONTROL_H
