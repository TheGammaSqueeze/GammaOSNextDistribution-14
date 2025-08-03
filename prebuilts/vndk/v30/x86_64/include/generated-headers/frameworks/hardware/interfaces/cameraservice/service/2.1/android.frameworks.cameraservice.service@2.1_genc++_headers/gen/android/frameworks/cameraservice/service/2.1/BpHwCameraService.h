#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BPHWCAMERASERVICE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BPHWCAMERASERVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/cameraservice/service/2.1/IHwCameraService.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
namespace V2_1 {

struct BpHwCameraService : public ::android::hardware::BpInterface<ICameraService>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraService(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraService Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::cameraservice::service::V2_1::ICameraService follow.
    static ::android::hardware::Return<void>  _hidl_addListener_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::frameworks::cameraservice::service::V2_1::ICameraServiceListener>& listener, addListener_2_1_cb _hidl_cb);

    // Methods from ::android::frameworks::cameraservice::service::V2_0::ICameraService follow.
    ::android::hardware::Return<void> connectDevice(const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceCallback>& callback, const ::android::hardware::hidl_string& cameraId, connectDevice_cb _hidl_cb) override;
    ::android::hardware::Return<void> addListener(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraServiceListener>& listener, addListener_cb _hidl_cb) override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> removeListener(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraServiceListener>& listener) override;
    ::android::hardware::Return<void> getCameraCharacteristics(const ::android::hardware::hidl_string& cameraId, getCameraCharacteristics_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCameraVendorTagSections(getCameraVendorTagSections_cb _hidl_cb) override;

    // Methods from ::android::frameworks::cameraservice::service::V2_1::ICameraService follow.
    ::android::hardware::Return<void> addListener_2_1(const ::android::sp<::android::frameworks::cameraservice::service::V2_1::ICameraServiceListener>& listener, addListener_2_1_cb _hidl_cb) override;

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

}  // namespace V2_1
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BPHWCAMERASERVICE_H
