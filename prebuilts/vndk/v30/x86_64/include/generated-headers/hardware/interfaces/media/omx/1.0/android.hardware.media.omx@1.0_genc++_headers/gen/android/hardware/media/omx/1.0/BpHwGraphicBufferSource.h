#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWGRAPHICBUFFERSOURCE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWGRAPHICBUFFERSOURCE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/media/omx/1.0/IHwGraphicBufferSource.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {

struct BpHwGraphicBufferSource : public ::android::hardware::BpInterface<IGraphicBufferSource>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGraphicBufferSource(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGraphicBufferSource Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::media::omx::V1_0::IGraphicBufferSource follow.
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_configure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>& omxNode, ::android::hardware::graphics::common::V1_0::Dataspace dataspace);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setSuspend(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool suspend, int64_t timeUs);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setRepeatPreviousFrameDelayUs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t repeatAfterUs);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setMaxFps(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, float maxFps);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setTimeLapseConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, double fps, double captureFps);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setStartTimeUs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t startTimeUs);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setStopTimeUs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t stopTimeUs);
    static ::android::hardware::Return<void>  _hidl_getStopTimeOffsetUs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getStopTimeOffsetUs_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setColorAspects(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::media::omx::V1_0::ColorAspects& aspects);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_setTimeOffsetUs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t timeOffsetUs);
    static ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status>  _hidl_signalEndOfInputStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::media::omx::V1_0::IGraphicBufferSource follow.
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> configure(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>& omxNode, ::android::hardware::graphics::common::V1_0::Dataspace dataspace) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setSuspend(bool suspend, int64_t timeUs) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setRepeatPreviousFrameDelayUs(int64_t repeatAfterUs) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setMaxFps(float maxFps) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setTimeLapseConfig(double fps, double captureFps) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setStartTimeUs(int64_t startTimeUs) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setStopTimeUs(int64_t stopTimeUs) override;
    ::android::hardware::Return<void> getStopTimeOffsetUs(getStopTimeOffsetUs_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setColorAspects(const ::android::hardware::media::omx::V1_0::ColorAspects& aspects) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setTimeOffsetUs(int64_t timeOffsetUs) override;
    ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> signalEndOfInputStream() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWGRAPHICBUFFERSOURCE_H
