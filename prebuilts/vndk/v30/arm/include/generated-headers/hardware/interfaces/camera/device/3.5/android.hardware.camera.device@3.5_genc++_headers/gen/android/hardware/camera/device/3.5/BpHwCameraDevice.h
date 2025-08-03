#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_BPHWCAMERADEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_BPHWCAMERADEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/3.5/IHwCameraDevice.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_5 {

struct BpHwCameraDevice : public ::android::hardware::BpInterface<ICameraDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V3_5::ICameraDevice follow.
    static ::android::hardware::Return<void>  _hidl_getPhysicalCameraCharacteristics(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& physicalCameraId, getPhysicalCameraCharacteristics_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_isStreamCombinationSupported(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::camera::device::V3_4::StreamConfiguration& streams, isStreamCombinationSupported_cb _hidl_cb);

    // Methods from ::android::hardware::camera::device::V3_2::ICameraDevice follow.
    ::android::hardware::Return<void> getResourceCost(getResourceCost_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCameraCharacteristics(getCameraCharacteristics_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setTorchMode(::android::hardware::camera::common::V1_0::TorchMode mode) override;
    ::android::hardware::Return<void> open(const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDeviceCallback>& callback, open_cb _hidl_cb) override;
    ::android::hardware::Return<void> dumpState(const ::android::hardware::hidl_handle& fd) override;

    // Methods from ::android::hardware::camera::device::V3_5::ICameraDevice follow.
    ::android::hardware::Return<void> getPhysicalCameraCharacteristics(const ::android::hardware::hidl_string& physicalCameraId, getPhysicalCameraCharacteristics_cb _hidl_cb) override;
    ::android::hardware::Return<void> isStreamCombinationSupported(const ::android::hardware::camera::device::V3_4::StreamConfiguration& streams, isStreamCombinationSupported_cb _hidl_cb) override;

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

}  // namespace V3_5
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_BPHWCAMERADEVICE_H
