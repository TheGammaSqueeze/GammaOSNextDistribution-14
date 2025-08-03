#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BNHWCONTEXTHUBCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BNHWCONTEXTHUBCALLBACK_H

#include <android/hardware/contexthub/1.0/IHwContexthubCallback.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {

struct BnHwContexthubCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwContexthubCallback(const ::android::sp<IContexthubCallback> &_hidl_impl);
    explicit BnHwContexthubCallback(const ::android::sp<IContexthubCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwContexthubCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IContexthubCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IContexthubCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::contexthub::V1_0::IContexthubCallback follow.
    static ::android::status_t _hidl_handleClientMsg(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_handleTxnResult(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_handleHubEvent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_handleAppAbort(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_handleAppsInfo(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::contexthub::V1_0::IContexthubCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IContexthubCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_BNHWCONTEXTHUBCALLBACK_H
