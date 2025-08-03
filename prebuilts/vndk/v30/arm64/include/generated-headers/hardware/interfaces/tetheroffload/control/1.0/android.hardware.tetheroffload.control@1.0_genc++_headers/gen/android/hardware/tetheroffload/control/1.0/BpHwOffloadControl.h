#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BPHWOFFLOADCONTROL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BPHWOFFLOADCONTROL_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tetheroffload/control/1.0/IHwOffloadControl.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace control {
namespace V1_0 {

struct BpHwOffloadControl : public ::android::hardware::BpInterface<IOffloadControl>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwOffloadControl(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IOffloadControl Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tetheroffload::control::V1_0::IOffloadControl follow.
    static ::android::hardware::Return<void>  _hidl_initOffload(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tetheroffload::control::V1_0::ITetheringOffloadCallback>& cb, initOffload_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_stopOffload(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, stopOffload_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setLocalPrefixes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& prefixes, setLocalPrefixes_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getForwardedStats(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& upstream, getForwardedStats_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setDataLimit(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& upstream, uint64_t limit, setDataLimit_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setUpstreamParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& v4Addr, const ::android::hardware::hidl_string& v4Gw, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& v6Gws, setUpstreamParameters_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_addDownstream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& prefix, addDownstream_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeDownstream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& prefix, removeDownstream_cb _hidl_cb);

    // Methods from ::android::hardware::tetheroffload::control::V1_0::IOffloadControl follow.
    ::android::hardware::Return<void> initOffload(const ::android::sp<::android::hardware::tetheroffload::control::V1_0::ITetheringOffloadCallback>& cb, initOffload_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopOffload(stopOffload_cb _hidl_cb) override;
    ::android::hardware::Return<void> setLocalPrefixes(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& prefixes, setLocalPrefixes_cb _hidl_cb) override;
    ::android::hardware::Return<void> getForwardedStats(const ::android::hardware::hidl_string& upstream, getForwardedStats_cb _hidl_cb) override;
    ::android::hardware::Return<void> setDataLimit(const ::android::hardware::hidl_string& upstream, uint64_t limit, setDataLimit_cb _hidl_cb) override;
    ::android::hardware::Return<void> setUpstreamParameters(const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& v4Addr, const ::android::hardware::hidl_string& v4Gw, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& v6Gws, setUpstreamParameters_cb _hidl_cb) override;
    ::android::hardware::Return<void> addDownstream(const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& prefix, addDownstream_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeDownstream(const ::android::hardware::hidl_string& iface, const ::android::hardware::hidl_string& prefix, removeDownstream_cb _hidl_cb) override;

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
}  // namespace control
}  // namespace tetheroffload
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BPHWOFFLOADCONTROL_H
