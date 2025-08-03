#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.3/IHwSupplicant.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_3 {

struct BpHwSupplicant : public ::android::hardware::BpInterface<ISupplicant>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicant(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicant Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;


    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicant follow.
    ::android::hardware::Return<void> getInterface(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& ifaceInfo, getInterface_cb _hidl_cb) override;
    ::android::hardware::Return<void> listInterfaces(listInterfaces_cb _hidl_cb) override;
    ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantCallback>& callback, registerCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> setDebugParams(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel level, bool showTimestamp, bool showKeys, setDebugParams_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel> getDebugLevel() override;
    ::android::hardware::Return<bool> isDebugShowTimestampEnabled() override;
    ::android::hardware::Return<bool> isDebugShowKeysEnabled() override;
    ::android::hardware::Return<void> setConcurrencyPriority(::android::hardware::wifi::supplicant::V1_0::IfaceType type, setConcurrencyPriority_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicant follow.
    ::android::hardware::Return<void> addInterface(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& ifaceInfo, addInterface_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeInterface(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& ifaceInfo, removeInterface_cb _hidl_cb) override;
    ::android::hardware::Return<void> terminate() override;

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

}  // namespace V1_3
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANT_H
