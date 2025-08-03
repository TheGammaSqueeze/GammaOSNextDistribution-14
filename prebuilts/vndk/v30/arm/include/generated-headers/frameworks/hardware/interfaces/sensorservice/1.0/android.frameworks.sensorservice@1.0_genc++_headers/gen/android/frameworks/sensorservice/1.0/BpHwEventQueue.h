#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWEVENTQUEUE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWEVENTQUEUE_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/sensorservice/1.0/IHwEventQueue.h>

namespace android {
namespace frameworks {
namespace sensorservice {
namespace V1_0 {

struct BpHwEventQueue : public ::android::hardware::BpInterface<IEventQueue>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEventQueue(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEventQueue Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::sensorservice::V1_0::IEventQueue follow.
    static ::android::hardware::Return<::android::frameworks::sensorservice::V1_0::Result>  _hidl_enableSensor(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t sensorHandle, int32_t samplingPeriodUs, int64_t maxBatchReportLatencyUs);
    static ::android::hardware::Return<::android::frameworks::sensorservice::V1_0::Result>  _hidl_disableSensor(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t sensorHandle);

    // Methods from ::android::frameworks::sensorservice::V1_0::IEventQueue follow.
    ::android::hardware::Return<::android::frameworks::sensorservice::V1_0::Result> enableSensor(int32_t sensorHandle, int32_t samplingPeriodUs, int64_t maxBatchReportLatencyUs) override;
    ::android::hardware::Return<::android::frameworks::sensorservice::V1_0::Result> disableSensor(int32_t sensorHandle) override;

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
}  // namespace sensorservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWEVENTQUEUE_H
