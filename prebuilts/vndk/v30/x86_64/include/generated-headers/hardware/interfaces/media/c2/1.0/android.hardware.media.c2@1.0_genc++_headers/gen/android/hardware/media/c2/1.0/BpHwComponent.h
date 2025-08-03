#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/media/c2/1.0/IHwComponent.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {

struct BpHwComponent : public ::android::hardware::BpInterface<IComponent>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwComponent(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IComponent Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::media::c2::V1_0::IComponent follow.
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_queue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::media::c2::V1_0::WorkBundle& workBundle);
    static ::android::hardware::Return<void>  _hidl_flush(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, flush_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_drain(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool withEos);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_setOutputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t blockPoolId, const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& surface);
    static ::android::hardware::Return<void>  _hidl_connectToInputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurface>& inputSurface, connectToInputSurface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_connectToOmxInputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::graphics::bufferqueue::V1_0::IGraphicBufferProducer>& producer, const ::android::sp<::android::hardware::media::omx::V1_0::IGraphicBufferSource>& source, connectToOmxInputSurface_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_disconnectFromInputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_createBlockPool(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t allocatorId, createBlockPool_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_destroyBlockPool(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t blockPoolId);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_start(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_stop(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_reset(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status>  _hidl_release(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentInterface>>  _hidl_getInterface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IInputSink>>  _hidl_asInputSink(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::media::c2::V1_0::IComponent follow.
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> queue(const ::android::hardware::media::c2::V1_0::WorkBundle& workBundle) override;
    ::android::hardware::Return<void> flush(flush_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> drain(bool withEos) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> setOutputSurface(uint64_t blockPoolId, const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& surface) override;
    ::android::hardware::Return<void> connectToInputSurface(const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurface>& inputSurface, connectToInputSurface_cb _hidl_cb) override;
    ::android::hardware::Return<void> connectToOmxInputSurface(const ::android::sp<::android::hardware::graphics::bufferqueue::V1_0::IGraphicBufferProducer>& producer, const ::android::sp<::android::hardware::media::omx::V1_0::IGraphicBufferSource>& source, connectToOmxInputSurface_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> disconnectFromInputSurface() override;
    ::android::hardware::Return<void> createBlockPool(uint32_t allocatorId, createBlockPool_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> destroyBlockPool(uint64_t blockPoolId) override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> start() override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> stop() override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> reset() override;
    ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> release() override;
    ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentInterface>> getInterface() override;
    ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IInputSink>> asInputSink() override;

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
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BPHWCOMPONENT_H
