#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSULTRASONICSARRAY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSULTRASONICSARRAY_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/evs/1.1/IHwEvsUltrasonicsArray.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

struct BpHwEvsUltrasonicsArray : public ::android::hardware::BpInterface<IEvsUltrasonicsArray>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEvsUltrasonicsArray(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEvsUltrasonicsArray Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray follow.
    static ::android::hardware::Return<void>  _hidl_getUltrasonicArrayInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getUltrasonicArrayInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_setMaxFramesInFlight(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t bufferCount);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_startStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArrayStream>& stream);
    static ::android::hardware::Return<void>  _hidl_stopStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_doneWithDataFrame(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& dataFrameDesc);

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray follow.
    ::android::hardware::Return<void> getUltrasonicArrayInfo(getUltrasonicArrayInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaxFramesInFlight(uint32_t bufferCount) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> startStream(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArrayStream>& stream) override;
    ::android::hardware::Return<void> stopStream() override;
    ::android::hardware::Return<void> doneWithDataFrame(const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& dataFrameDesc) override;

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

}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSULTRASONICSARRAY_H
