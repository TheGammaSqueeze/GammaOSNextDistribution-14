#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWDEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/neuralnetworks/1.2/IHwDevice.h>

namespace android {
namespace hardware {
namespace neuralnetworks {
namespace V1_2 {

struct BpHwDevice : public ::android::hardware::BpInterface<IDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::neuralnetworks::V1_2::IDevice follow.
    static ::android::hardware::Return<void>  _hidl_getVersionString(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getVersionString_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getType_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCapabilities_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCapabilities_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSupportedExtensions(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSupportedExtensions_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSupportedOperations_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::neuralnetworks::V1_2::Model& model, getSupportedOperations_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getNumberOfCacheFilesNeeded(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getNumberOfCacheFilesNeeded_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus>  _hidl_prepareModel_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::neuralnetworks::V1_2::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback);
    static ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus>  _hidl_prepareModelFromCache(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback);

    // Methods from ::android::hardware::neuralnetworks::V1_0::IDevice follow.
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedOperations(const ::android::hardware::neuralnetworks::V1_0::Model& model, getSupportedOperations_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel(const ::android::hardware::neuralnetworks::V1_0::Model& model, const ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::DeviceStatus> getStatus() override;

    // Methods from ::android::hardware::neuralnetworks::V1_1::IDevice follow.
    ::android::hardware::Return<void> getCapabilities_1_1(getCapabilities_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedOperations_1_1(const ::android::hardware::neuralnetworks::V1_1::Model& model, getSupportedOperations_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel_1_1(const ::android::hardware::neuralnetworks::V1_1::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, const ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback>& callback) override;

    // Methods from ::android::hardware::neuralnetworks::V1_2::IDevice follow.
    ::android::hardware::Return<void> getVersionString(getVersionString_cb _hidl_cb) override;
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCapabilities_1_2(getCapabilities_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedExtensions(getSupportedExtensions_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedOperations_1_2(const ::android::hardware::neuralnetworks::V1_2::Model& model, getSupportedOperations_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNumberOfCacheFilesNeeded(getNumberOfCacheFilesNeeded_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel_1_2(const ::android::hardware::neuralnetworks::V1_2::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModelFromCache(const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_2_BPHWDEVICE_H
