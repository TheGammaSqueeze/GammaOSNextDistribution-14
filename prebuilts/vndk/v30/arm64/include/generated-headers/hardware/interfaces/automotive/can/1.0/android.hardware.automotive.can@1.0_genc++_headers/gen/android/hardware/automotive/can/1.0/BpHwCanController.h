#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BPHWCANCONTROLLER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BPHWCANCONTROLLER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/can/1.0/IHwCanController.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {

struct BpHwCanController : public ::android::hardware::BpInterface<ICanController>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCanController(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICanController Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::can::V1_0::ICanController follow.
    static ::android::hardware::Return<void>  _hidl_getSupportedInterfaceTypes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSupportedInterfaceTypes_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::can::V1_0::ICanController::Result>  _hidl_upInterface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& config);
    static ::android::hardware::Return<bool>  _hidl_downInterface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& name);

    // Methods from ::android::hardware::automotive::can::V1_0::ICanController follow.
    ::android::hardware::Return<void> getSupportedInterfaceTypes(getSupportedInterfaceTypes_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::can::V1_0::ICanController::Result> upInterface(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& config) override;
    ::android::hardware::Return<bool> downInterface(const ::android::hardware::hidl_string& name) override;

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
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BPHWCANCONTROLLER_H
