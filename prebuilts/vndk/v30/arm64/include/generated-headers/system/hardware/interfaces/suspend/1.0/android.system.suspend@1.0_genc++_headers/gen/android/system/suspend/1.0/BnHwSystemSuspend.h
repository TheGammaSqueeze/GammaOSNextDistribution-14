#ifndef HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWSYSTEMSUSPEND_H
#define HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWSYSTEMSUSPEND_H

#include <android/system/suspend/1.0/IHwSystemSuspend.h>

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

struct BnHwSystemSuspend : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSystemSuspend(const ::android::sp<ISystemSuspend> &_hidl_impl);
    explicit BnHwSystemSuspend(const ::android::sp<ISystemSuspend> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSystemSuspend();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISystemSuspend Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISystemSuspend> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::system::suspend::V1_0::ISystemSuspend follow.
    static ::android::status_t _hidl_acquireWakeLock(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::system::suspend::V1_0::ISystemSuspend follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISystemSuspend> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWSYSTEMSUSPEND_H
