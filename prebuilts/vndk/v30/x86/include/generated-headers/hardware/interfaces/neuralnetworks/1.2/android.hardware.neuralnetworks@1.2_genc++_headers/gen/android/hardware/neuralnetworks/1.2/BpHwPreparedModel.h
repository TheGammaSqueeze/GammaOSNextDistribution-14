#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWPREPAREDMODEL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWPREPAREDMODEL_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/neuralnetworks/1.2/IHwPreparedModel.h>

namespace android {
namespace hardware {
namespace neuralnetworks {
namespace V1_2 {

struct BpHwPreparedModel : public ::android::hardware::BpInterface<IPreparedModel>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwPreparedModel(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IPreparedModel Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::neuralnetworks::V1_2::IPreparedModel follow.
    static ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus>  _hidl_execute_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::neuralnetworks::V1_0::Request& request, ::android::hardware::neuralnetworks::V1_2::MeasureTiming measure, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IExecutionCallback>& callback);
    static ::android::hardware::Return<void>  _hidl_executeSynchronously(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::neuralnetworks::V1_0::Request& request, ::android::hardware::neuralnetworks::V1_2::MeasureTiming measure, executeSynchronously_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_configureExecutionBurst(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IBurstCallback>& callback, const ::android::hardware::MQDescriptorSync<::android::hardware::neuralnetworks::V1_2::FmqRequestDatum>& requestChannel, const ::android::hardware::MQDescriptorSync<::android::hardware::neuralnetworks::V1_2::FmqResultDatum>& resultChannel, configureExecutionBurst_cb _hidl_cb);

    // Methods from ::android::hardware::neuralnetworks::V1_0::IPreparedModel follow.
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> execute(const ::android::hardware::neuralnetworks::V1_0::Request& request, const ::android::sp<::android::hardware::neuralnetworks::V1_0::IExecutionCallback>& callback) override;

    // Methods from ::android::hardware::neuralnetworks::V1_2::IPreparedModel follow.
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> execute_1_2(const ::android::hardware::neuralnetworks::V1_0::Request& request, ::android::hardware::neuralnetworks::V1_2::MeasureTiming measure, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IExecutionCallback>& callback) override;
    ::android::hardware::Return<void> executeSynchronously(const ::android::hardware::neuralnetworks::V1_0::Request& request, ::android::hardware::neuralnetworks::V1_2::MeasureTiming measure, executeSynchronously_cb _hidl_cb) override;
    ::android::hardware::Return<void> configureExecutionBurst(const ::android::sp<::android::hardware::neuralnetworks::V1_2::IBurstCallback>& callback, const ::android::hardware::MQDescriptorSync<::android::hardware::neuralnetworks::V1_2::FmqRequestDatum>& requestChannel, const ::android::hardware::MQDescriptorSync<::android::hardware::neuralnetworks::V1_2::FmqResultDatum>& resultChannel, configureExecutionBurst_cb _hidl_cb) override;

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

}  // namespace V1_2
}  // namespace neuralnetworks
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWPREPAREDMODEL_H
