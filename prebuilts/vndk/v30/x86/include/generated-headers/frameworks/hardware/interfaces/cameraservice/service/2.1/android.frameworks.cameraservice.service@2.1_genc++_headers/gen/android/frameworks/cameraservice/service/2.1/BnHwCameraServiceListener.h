#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BNHWCAMERASERVICELISTENER_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BNHWCAMERASERVICELISTENER_H

#include <android/frameworks/cameraservice/service/2.1/IHwCameraServiceListener.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
namespace V2_1 {

struct BnHwCameraServiceListener : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCameraServiceListener(const ::android::sp<ICameraServiceListener> &_hidl_impl);
    explicit BnHwCameraServiceListener(const ::android::sp<ICameraServiceListener> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCameraServiceListener();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraServiceListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICameraServiceListener> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::frameworks::cameraservice::service::V2_1::ICameraServiceListener follow.
    static ::android::status_t _hidl_onPhysicalCameraStatusChanged(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::frameworks::cameraservice::service::V2_0::ICameraServiceListener follow.

    // Methods from ::android::frameworks::cameraservice::service::V2_1::ICameraServiceListener follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICameraServiceListener> _hidl_mImpl;
};

}  // namespace V2_1
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_BNHWCAMERASERVICELISTENER_H
