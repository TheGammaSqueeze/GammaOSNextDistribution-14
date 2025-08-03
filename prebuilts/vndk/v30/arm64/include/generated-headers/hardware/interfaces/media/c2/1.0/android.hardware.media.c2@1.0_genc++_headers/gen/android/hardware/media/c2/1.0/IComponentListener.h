#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTLISTENER_H

#include <android/hardware/media/c2/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {

/**
 * Callback interface for handling notifications from @ref IComponent.
 */
struct IComponentListener : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.c2@1.0::IComponentListener"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct RenderedFrame;
    struct InputBuffer;

    /**
     * Information about rendering of a frame to a `Surface`.
     */
    struct RenderedFrame final {
        /**
         * Id of the `BufferQueue` containing the rendered buffer.
         *
         * This value must have been obtained by an earlier call to
         * IGraphicBufferProducer::getUniqueId().
         */
        uint64_t bufferQueueId __attribute__ ((aligned(8)));
        /**
         * Id of the slot of the rendered buffer.
         *
         * This value must have been obtained by an earlier call to
         * IGraphicBufferProducer::dequeueBuffer() or
         * IGraphicBufferProducer::attachBuffer().
         */
        int32_t slotId __attribute__ ((aligned(4)));
        /**
         * Timestamp the rendering happened.
         *
         * The reference point for the timestamp is determined by the
         * `BufferQueue` that performed the rendering.
         */
        int64_t timestampNs __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame, bufferQueueId) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame, slotId) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame, timestampNs) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame) == 8, "wrong alignment");

    /**
     * Identifying information for an input buffer previously queued to the
     * component via IComponent::queue().
     */
    struct InputBuffer final {
        /**
         * This value comes from `Work::input.ordinal.frameIndex` in a `Work`
         * object that was previously queued.
         */
        uint64_t frameIndex __attribute__ ((aligned(8)));
        /**
         * This value is an index into `Work::input.buffers` (which is an array)
         * in a `Work` object that was previously queued.
         */
        uint32_t arrayIndex __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer, frameIndex) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer, arrayIndex) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Notify the listener that some `Work` items have been completed.
     *
     * All the input buffers in the returned `Work` objects must not be used by
     * the component after onWorkDone() is called.
     *
     * @param workBundle List of completed `Work` objects.
     */
    virtual ::android::hardware::Return<void> onWorkDone(const ::android::hardware::media::c2::V1_0::WorkBundle& workBundle) = 0;

    /**
     * Notify the listener that the component is tripped.
     *
     * @param settingResults List of failures.
     */
    virtual ::android::hardware::Return<void> onTripped(const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::SettingResult>& settingResults) = 0;

    /**
     * Notify the listener of an error.
     *
     * @param status Error type. @p status may be `OK`, which means that an
     *     error has occurred, but the error type does not fit into the type
     *     `Status`. In this case, additional information is provided by
     *     @p errorCode.
     * @param errorCode Additional error information. The framework may not
     *     recognize the meaning of this value.
     */
    virtual ::android::hardware::Return<void> onError(::android::hardware::media::c2::V1_0::Status status, uint32_t errorCode) = 0;

    /**
     * Notify the listener that frames have been rendered.
     *
     * @param renderedFrames List of @ref RenderedFrame objects.
     */
    virtual ::android::hardware::Return<void> onFramesRendered(const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame>& renderedFrames) = 0;

    /**
     * Notify the listener that some input buffers are no longer needed by the
     * component, and hence can be released or reused by the client.
     *
     * Input buffers that are contained in a `Work` object returned by an
     * earlier onWorkDone() call are assumed released, so they must not appear
     * in any onInputBuffersReleased() calls. That means
     * onInputBuffersReleased() must only report input buffers that are released
     * before the output in the same `Work` item is produced. However, it is
     * possible for an input buffer to be returned by onWorkDone() after it has
     * been reported by onInputBuffersReleased().
     *
     * @note onWorkDone() and onInputBuffersReleased() both notify the client
     * that input buffers are no longer needed. However, in order to minimize
     * IPC calls, onInputBuffersReleased() should be called only when
     * onWorkDone() cannot be called, e.g., the component needs more input
     * before an output can be produced.
     *
     * @param inputBuffers List of `InputBuffer` objects, identifying input
     * buffers that are no longer needed by the component.
     */
    virtual ::android::hardware::Return<void> onInputBuffersReleased(const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer>& inputBuffers) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>> castFrom(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<IComponentListener> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponentListener> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponentListener> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponentListener> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IComponentListener> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponentListener> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponentListener> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponentListener> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bufferQueueId = ";
    os += ::android::hardware::toString(o.bufferQueueId);
    os += ", .slotId = ";
    os += ::android::hardware::toString(o.slotId);
    os += ", .timestampNs = ";
    os += ::android::hardware::toString(o.timestampNs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& rhs) {
    if (lhs.bufferQueueId != rhs.bufferQueueId) {
        return false;
    }
    if (lhs.slotId != rhs.slotId) {
        return false;
    }
    if (lhs.timestampNs != rhs.timestampNs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::RenderedFrame& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameIndex = ";
    os += ::android::hardware::toString(o.frameIndex);
    os += ", .arrayIndex = ";
    os += ::android::hardware::toString(o.arrayIndex);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& rhs) {
    if (lhs.frameIndex != rhs.frameIndex) {
        return false;
    }
    if (lhs.arrayIndex != rhs.arrayIndex) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& lhs, const ::android::hardware::media::c2::V1_0::IComponentListener::InputBuffer& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::c2::V1_0::IComponentListener::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTLISTENER_H
