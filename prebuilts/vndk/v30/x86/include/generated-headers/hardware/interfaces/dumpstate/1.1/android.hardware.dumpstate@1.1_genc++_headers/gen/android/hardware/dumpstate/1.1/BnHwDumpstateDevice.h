#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_BNHWDUMPSTATEDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_BNHWDUMPSTATEDEVICE_H

#include <android/hardware/dumpstate/1.1/IHwDumpstateDevice.h>

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_1 {

struct BnHwDumpstateDevice : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDumpstateDevice(const ::android::sp<IDumpstateDevice> &_hidl_impl);
    explicit BnHwDumpstateDevice(const ::android::sp<IDumpstateDevice> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDumpstateDevice();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDumpstateDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDumpstateDevice> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::dumpstate::V1_1::IDumpstateDevice follow.
    static ::android::status_t _hidl_dumpstateBoard_1_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setVerboseLoggingEnabled(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getVerboseLoggingEnabled(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::dumpstate::V1_0::IDumpstateDevice follow.

    // Methods from ::android::hardware::dumpstate::V1_1::IDumpstateDevice follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDumpstateDevice> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_BNHWDUMPSTATEDEVICE_H
