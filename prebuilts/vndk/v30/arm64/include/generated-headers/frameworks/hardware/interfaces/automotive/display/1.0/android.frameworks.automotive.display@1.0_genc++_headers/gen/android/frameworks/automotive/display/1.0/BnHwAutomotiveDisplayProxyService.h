#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BNHWAUTOMOTIVEDISPLAYPROXYSERVICE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BNHWAUTOMOTIVEDISPLAYPROXYSERVICE_H

#include <android/frameworks/automotive/display/1.0/IHwAutomotiveDisplayProxyService.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace display {
namespace V1_0 {

struct BnHwAutomotiveDisplayProxyService : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwAutomotiveDisplayProxyService(const ::android::sp<IAutomotiveDisplayProxyService> &_hidl_impl);
    explicit BnHwAutomotiveDisplayProxyService(const ::android::sp<IAutomotiveDisplayProxyService> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwAutomotiveDisplayProxyService();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IAutomotiveDisplayProxyService Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IAutomotiveDisplayProxyService> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::frameworks::automotive::display::V1_0::IAutomotiveDisplayProxyService follow.
    static ::android::status_t _hidl_getIGraphicBufferProducer(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_showWindow(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_hideWindow(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getDisplayIdList(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getDisplayInfo(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::frameworks::automotive::display::V1_0::IAutomotiveDisplayProxyService follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IAutomotiveDisplayProxyService> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace display
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_AUTOMOTIVE_DISPLAY_V1_0_BNHWAUTOMOTIVEDISPLAYPROXYSERVICE_H
