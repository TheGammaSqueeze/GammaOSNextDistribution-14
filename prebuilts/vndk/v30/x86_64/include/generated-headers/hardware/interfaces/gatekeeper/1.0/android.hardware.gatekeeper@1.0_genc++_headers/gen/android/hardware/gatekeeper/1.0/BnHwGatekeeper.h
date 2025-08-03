#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BNHWGATEKEEPER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BNHWGATEKEEPER_H

#include <android/hardware/gatekeeper/1.0/IHwGatekeeper.h>

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {

struct BnHwGatekeeper : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwGatekeeper(const ::android::sp<IGatekeeper> &_hidl_impl);
    explicit BnHwGatekeeper(const ::android::sp<IGatekeeper> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwGatekeeper();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IGatekeeper Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IGatekeeper> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.
    static ::android::status_t _hidl_enroll(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_verify(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_deleteUser(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_deleteAllUsers(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IGatekeeper> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace gatekeeper
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BNHWGATEKEEPER_H
