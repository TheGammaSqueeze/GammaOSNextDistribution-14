#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BPHWTVINPUT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BPHWTVINPUT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/input/1.0/IHwTvInput.h>

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {

struct BpHwTvInput : public ::android::hardware::BpInterface<ITvInput>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwTvInput(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ITvInput Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::input::V1_0::ITvInput follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tv::input::V1_0::ITvInputCallback>& callback);
    static ::android::hardware::Return<void>  _hidl_getStreamConfigurations(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t deviceId, getStreamConfigurations_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t deviceId, int32_t streamId, openStream_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::tv::input::V1_0::Result>  _hidl_closeStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t deviceId, int32_t streamId);

    // Methods from ::android::hardware::tv::input::V1_0::ITvInput follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::tv::input::V1_0::ITvInputCallback>& callback) override;
    ::android::hardware::Return<void> getStreamConfigurations(int32_t deviceId, getStreamConfigurations_cb _hidl_cb) override;
    ::android::hardware::Return<void> openStream(int32_t deviceId, int32_t streamId, openStream_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::tv::input::V1_0::Result> closeStream(int32_t deviceId, int32_t streamId) override;

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
}  // namespace input
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BPHWTVINPUT_H
