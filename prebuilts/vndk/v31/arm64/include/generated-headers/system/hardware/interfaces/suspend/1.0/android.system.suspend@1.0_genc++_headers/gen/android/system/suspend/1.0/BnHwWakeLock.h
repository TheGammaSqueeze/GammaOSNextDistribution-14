#ifndef HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWWAKELOCK_H
#define HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWWAKELOCK_H

#include <android/system/suspend/1.0/IHwWakeLock.h>

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

struct BnHwWakeLock : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwWakeLock(const ::android::sp<IWakeLock> &_hidl_impl);
    explicit BnHwWakeLock(const ::android::sp<IWakeLock> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwWakeLock();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IWakeLock Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IWakeLock> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::system::suspend::V1_0::IWakeLock follow.
    static ::android::status_t _hidl_release(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::system::suspend::V1_0::IWakeLock follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IWakeLock> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BNHWWAKELOCK_H
