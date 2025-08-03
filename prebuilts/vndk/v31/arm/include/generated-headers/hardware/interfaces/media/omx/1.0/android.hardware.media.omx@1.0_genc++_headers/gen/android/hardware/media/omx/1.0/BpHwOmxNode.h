#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXNODE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXNODE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/media/omx/1.0/IHwOmxNode.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {

struct BpHwOmxNode : public ::android::hardware::BpInterface<IOmxNode>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwOmxNode(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IOmxNode Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::media::omx::V1_0::IOmxNode follow.
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_freeNode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_sendCommand(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmd, int32_t param);
    static ::android::hardware::Return<void>  _hidl_getParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inParams, getParameter_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& params);
    static ::android::hardware::Return<void>  _hidl_getConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inConfig, getConfig_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& config);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setPortMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, ::android::hardware::media::omx::V1_0::PortMode mode);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_prepareForAdaptivePlayback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, bool enable, uint32_t maxFrameWidth, uint32_t maxFrameHeight);
    static ::android::hardware::Return<void>  _hidl_configureVideoTunnelMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, bool tunneled, uint32_t audioHwSync, configureVideoTunnelMode_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getGraphicBufferUsage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, getGraphicBufferUsage_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setInputSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::media::omx::V1_0::IOmxBufferSource>& bufferSource);
    static ::android::hardware::Return<void>  _hidl_allocateSecureBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, uint64_t size, allocateSecureBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_useBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, useBuffer_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_freeBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t portIndex, uint32_t buffer);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_fillBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, const ::android::hardware::hidl_handle& fence);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_emptyBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, uint32_t flags, uint64_t timestampUs, const ::android::hardware::hidl_handle& fence);
    static ::android::hardware::Return<void>  _hidl_getExtensionIndex(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& parameterName, getExtensionIndex_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_dispatchMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::media::omx::V1_0::Message& msg);

    // Methods from ::android::hardware::media::omx::V1_0::IOmxNode follow.
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> freeNode() override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> sendCommand(uint32_t cmd, int32_t param) override;
    ::android::hardware::Return<void> getParameter(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inParams, getParameter_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setParameter(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& params) override;
    ::android::hardware::Return<void> getConfig(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inConfig, getConfig_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setConfig(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& config) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setPortMode(uint32_t portIndex, ::android::hardware::media::omx::V1_0::PortMode mode) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> prepareForAdaptivePlayback(uint32_t portIndex, bool enable, uint32_t maxFrameWidth, uint32_t maxFrameHeight) override;
    ::android::hardware::Return<void> configureVideoTunnelMode(uint32_t portIndex, bool tunneled, uint32_t audioHwSync, configureVideoTunnelMode_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGraphicBufferUsage(uint32_t portIndex, getGraphicBufferUsage_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setInputSurface(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxBufferSource>& bufferSource) override;
    ::android::hardware::Return<void> allocateSecureBuffer(uint32_t portIndex, uint64_t size, allocateSecureBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> useBuffer(uint32_t portIndex, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, useBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> freeBuffer(uint32_t portIndex, uint32_t buffer) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> fillBuffer(uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, const ::android::hardware::hidl_handle& fence) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> emptyBuffer(uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, uint32_t flags, uint64_t timestampUs, const ::android::hardware::hidl_handle& fence) override;
    ::android::hardware::Return<void> getExtensionIndex(const ::android::hardware::hidl_string& parameterName, getExtensionIndex_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> dispatchMessage(const ::android::hardware::media::omx::V1_0::Message& msg) override;

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
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXNODE_H
