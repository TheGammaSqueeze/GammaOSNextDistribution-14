#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGINLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGINLISTENER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/drm/1.2/IHwDrmPluginListener.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_2 {

struct BpHwDrmPluginListener : public ::android::hardware::BpInterface<IDrmPluginListener>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDrmPluginListener(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDrmPluginListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::drm::V1_2::IDrmPluginListener follow.
    static ::android::hardware::Return<void>  _hidl_sendSessionLostState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId);
    static ::android::hardware::Return<void>  _hidl_sendKeysChange_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_2::KeyStatus>& keyStatusList, bool hasNewUsableKey);

    // Methods from ::android::hardware::drm::V1_0::IDrmPluginListener follow.
    ::android::hardware::Return<void> sendEvent(::android::hardware::drm::V1_0::EventType eventType, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> sendExpirationUpdate(const ::android::hardware::hidl_vec<uint8_t>& sessionId, int64_t expiryTimeInMS) override;
    ::android::hardware::Return<void> sendKeysChange(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyStatus>& keyStatusList, bool hasNewUsableKey) override;

    // Methods from ::android::hardware::drm::V1_2::IDrmPluginListener follow.
    ::android::hardware::Return<void> sendSessionLostState(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<void> sendKeysChange_1_2(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_2::KeyStatus>& keyStatusList, bool hasNewUsableKey) override;

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

}  // namespace V1_2
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGINLISTENER_H
