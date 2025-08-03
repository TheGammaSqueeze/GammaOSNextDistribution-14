#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWSENSORMANAGER_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWSENSORMANAGER_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/sensorservice/1.0/IHwSensorManager.h>

namespace android {
namespace frameworks {
namespace sensorservice {
namespace V1_0 {

struct BpHwSensorManager : public ::android::hardware::BpInterface<ISensorManager>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSensorManager(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISensorManager Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::sensorservice::V1_0::ISensorManager follow.
    static ::android::hardware::Return<void>  _hidl_getSensorList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSensorList_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDefaultSensor(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::sensors::V1_0::SensorType type, getDefaultSensor_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createAshmemDirectChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_memory& mem, uint64_t size, createAshmemDirectChannel_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createGrallocDirectChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_handle& buffer, uint64_t size, createGrallocDirectChannel_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createEventQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::frameworks::sensorservice::V1_0::IEventQueueCallback>& callback, createEventQueue_cb _hidl_cb);

    // Methods from ::android::frameworks::sensorservice::V1_0::ISensorManager follow.
    ::android::hardware::Return<void> getSensorList(getSensorList_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDefaultSensor(::android::hardware::sensors::V1_0::SensorType type, getDefaultSensor_cb _hidl_cb) override;
    ::android::hardware::Return<void> createAshmemDirectChannel(const ::android::hardware::hidl_memory& mem, uint64_t size, createAshmemDirectChannel_cb _hidl_cb) override;
    ::android::hardware::Return<void> createGrallocDirectChannel(const ::android::hardware::hidl_handle& buffer, uint64_t size, createGrallocDirectChannel_cb _hidl_cb) override;
    ::android::hardware::Return<void> createEventQueue(const ::android::sp<::android::frameworks::sensorservice::V1_0::IEventQueueCallback>& callback, createEventQueue_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BPHWSENSORMANAGER_H
