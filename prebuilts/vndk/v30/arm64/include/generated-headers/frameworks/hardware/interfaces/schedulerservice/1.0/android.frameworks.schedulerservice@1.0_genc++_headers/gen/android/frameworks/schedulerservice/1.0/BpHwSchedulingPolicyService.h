#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_SCHEDULERSERVICE_V1_0_BPHWSCHEDULINGPOLICYSERVICE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_SCHEDULERSERVICE_V1_0_BPHWSCHEDULINGPOLICYSERVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/schedulerservice/1.0/IHwSchedulingPolicyService.h>

namespace android {
namespace frameworks {
namespace schedulerservice {
namespace V1_0 {

struct BpHwSchedulingPolicyService : public ::android::hardware::BpInterface<ISchedulingPolicyService>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSchedulingPolicyService(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISchedulingPolicyService Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::schedulerservice::V1_0::ISchedulingPolicyService follow.
    static ::android::hardware::Return<bool>  _hidl_requestPriority(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t pid, int32_t tid, int32_t priority);
    static ::android::hardware::Return<int32_t>  _hidl_getMaxAllowedPriority(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::frameworks::schedulerservice::V1_0::ISchedulingPolicyService follow.
    ::android::hardware::Return<bool> requestPriority(int32_t pid, int32_t tid, int32_t priority) override;
    ::android::hardware::Return<int32_t> getMaxAllowedPriority() override;

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

}  // namespace V1_0
}  // namespace schedulerservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_SCHEDULERSERVICE_V1_0_BPHWSCHEDULINGPOLICYSERVICE_H
