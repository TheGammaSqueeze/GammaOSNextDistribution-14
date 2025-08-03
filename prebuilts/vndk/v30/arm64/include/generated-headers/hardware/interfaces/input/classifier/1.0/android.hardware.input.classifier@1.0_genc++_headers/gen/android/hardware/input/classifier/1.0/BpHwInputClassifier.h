#ifndef HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BPHWINPUTCLASSIFIER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BPHWINPUTCLASSIFIER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/input/classifier/1.0/IHwInputClassifier.h>

namespace android {
namespace hardware {
namespace input {
namespace classifier {
namespace V1_0 {

struct BpHwInputClassifier : public ::android::hardware::BpInterface<IInputClassifier>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwInputClassifier(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IInputClassifier Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::input::classifier::V1_0::IInputClassifier follow.
    static ::android::hardware::Return<::android::hardware::input::common::V1_0::Classification>  _hidl_classify(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::input::common::V1_0::MotionEvent& event);
    static ::android::hardware::Return<void>  _hidl_reset(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_resetDevice(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t deviceId);

    // Methods from ::android::hardware::input::classifier::V1_0::IInputClassifier follow.
    ::android::hardware::Return<::android::hardware::input::common::V1_0::Classification> classify(const ::android::hardware::input::common::V1_0::MotionEvent& event) override;
    ::android::hardware::Return<void> reset() override;
    ::android::hardware::Return<void> resetDevice(int32_t deviceId) override;

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
}  // namespace classifier
}  // namespace input
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BPHWINPUTCLASSIFIER_H
