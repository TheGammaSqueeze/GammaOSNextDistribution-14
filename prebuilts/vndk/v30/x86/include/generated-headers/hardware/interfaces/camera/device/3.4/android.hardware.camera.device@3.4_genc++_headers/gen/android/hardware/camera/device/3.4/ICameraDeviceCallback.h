#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_ICAMERADEVICECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_ICAMERADEVICECALLBACK_H

#include <android/hardware/camera/device/3.2/ICameraDeviceCallback.h>
#include <android/hardware/camera/device/3.4/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_4 {

/**
 * Callback methods for the HAL to call into the framework.
 *
 * These methods are used to return metadata and image buffers for a completed
 * or failed captures, and to notify the framework of asynchronous events such
 * as errors.
 *
 * The framework must not call back into the HAL from within these callbacks,
 * and these calls must not block for extended periods.
 *
 */
struct ICameraDeviceCallback : public ::android::hardware::camera::device::V3_2::ICameraDeviceCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.camera.device@3.4::ICameraDeviceCallback"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * processCaptureResult:
     *
     * Send results from one or more completed or partially completed captures
     * to the framework.
     * processCaptureResult() may be invoked multiple times by the HAL in
     * response to a single capture request. This allows, for example, the
     * metadata and low-resolution buffers to be returned in one call, and
     * post-processed JPEG buffers in a later call, once it is available. Each
     * call must include the frame number of the request it is returning
     * metadata or buffers for. Only one call to processCaptureResult
     * may be made at a time by the HAL although the calls may come from
     * different threads in the HAL.
     *
     * A component (buffer or metadata) of the complete result may only be
     * included in one process_capture_result call. A buffer for each stream,
     * and the result metadata, must be returned by the HAL for each request in
     * one of the processCaptureResult calls, even in case of errors producing
     * some of the output. A call to processCaptureResult() with neither
     * output buffers or result metadata is not allowed.
     *
     * The order of returning metadata and buffers for a single result does not
     * matter, but buffers for a given stream must be returned in FIFO order. So
     * the buffer for request 5 for stream A must always be returned before the
     * buffer for request 6 for stream A. This also applies to the result
     * metadata; the metadata for request 5 must be returned before the metadata
     * for request 6.
     *
     * However, different streams are independent of each other, so it is
     * acceptable and expected that the buffer for request 5 for stream A may be
     * returned after the buffer for request 6 for stream B is. And it is
     * acceptable that the result metadata for request 6 for stream B is
     * returned before the buffer for request 5 for stream A is. If multiple
     * capture results are included in a single call, camera framework must
     * process results sequentially from lower index to higher index, as if
     * these results were sent to camera framework one by one, from lower index
     * to higher index.
     *
     * The HAL retains ownership of result structure, which only needs to be
     * valid to access during this call.
     *
     * The output buffers do not need to be filled yet; the framework must wait
     * on the stream buffer release sync fence before reading the buffer
     * data. Therefore, this method should be called by the HAL as soon as
     * possible, even if some or all of the output buffers are still in
     * being filled. The HAL must include valid release sync fences into each
     * output_buffers stream buffer entry, or -1 if that stream buffer is
     * already filled.
     *
     * If the result buffer cannot be constructed for a request, the HAL must
     * return an empty metadata buffer, but still provide the output buffers and
     * their sync fences. In addition, notify() must be called with an
     * ERROR_RESULT message.
     *
     * If an output buffer cannot be filled, its status field must be set to
     * STATUS_ERROR. In this case, notify() isn't required to be called with
     * an ERROR_BUFFER message. The framework will simply treat the notify()
     * call with ERROR_BUFFER as a no-op, and derive whether and when to notify
     * the application of buffer loss based on the buffer status and whether or not
     * the entire capture has failed.
     *
     * If the entire capture has failed, then this method still needs to be
     * called to return the output buffers to the framework. All the buffer
     * statuses must be STATUS_ERROR, and the result metadata must be an
     * empty buffer. In addition, notify() must be called with a ERROR_REQUEST
     * message. In this case, individual ERROR_RESULT/ERROR_BUFFER messages
     * must not be sent. Note that valid partial results are still allowed
     * as long as the final result metadata fails to be generated.
     *
     * Performance requirements:
     *
     * This is a non-blocking call. The framework must handle each CaptureResult
     * within 5ms.
     *
     * The pipeline latency (see S7 for definition) should be less than or equal to
     * 4 frame intervals, and must be less than or equal to 8 frame intervals.
     *
     */
    virtual ::android::hardware::Return<void> processCaptureResult(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::CaptureResult>& results) = 0;

    /**
     * notify:
     *
     * Asynchronous notification callback from the HAL, fired for various
     * reasons. Only for information independent of frame capture, or that
     * require specific timing. Multiple messages may be sent in one call; a
     * message with a higher index must be considered to have occurred after a
     * message with a lower index.
     *
     * Multiple threads may call notify() simultaneously.
     *
     * Buffers delivered to the framework must not be dispatched to the
     * application layer until a start of exposure timestamp (or input image's
     * start of exposure timestamp for a reprocess request) has been received
     * via a SHUTTER notify() call. It is highly recommended to dispatch this
     * call as early as possible.
     *
     * The SHUTTER notify calls for requests with android.control.enableZsl
     * set to TRUE and ANDROID_CONTROL_CAPTURE_INTENT == STILL_CAPTURE may be
     * out-of-order compared to SHUTTER notify for other kinds of requests
     * (including regular, reprocess, or zero-shutter-lag requests with
     * different capture intents).
     *
     * As a result, the capture results of zero-shutter-lag requests with
     * ANDROID_CONTROL_CAPTURE_INTENT == STILL_CAPTURE may be out-of-order
     * compared to capture results for other kinds of requests.
     *
     * Different SHUTTER notify calls for zero-shutter-lag requests with
     * ANDROID_CONTROL_CAPTURE_INTENT == STILL_CAPTURE must be in order between
     * them, as is for other kinds of requests. SHUTTER notify calls for
     * zero-shutter-lag requests with non STILL_CAPTURE intent must be in order
     * with SHUTTER notify calls for regular requests.
     * ------------------------------------------------------------------------
     * Performance requirements:
     *
     * This is a non-blocking call. The framework must handle each message in 5ms.
     */
    virtual ::android::hardware::Return<void> notify(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::NotifyMsg>& msgs) = 0;

    /**
     * processCaptureResult_3_4:
     *
     * Identical to @3.2::ICameraDeviceCallback.processCaptureResult, except
     * that it takes a list of @3.4::CaptureResult, which could contain
     * physical camera metadata for logical multi-camera.
     *
     */
    virtual ::android::hardware::Return<void> processCaptureResult_3_4(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureResult>& results) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDeviceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICameraDeviceCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraDeviceCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceCallback> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::camera::device::V3_4::ICameraDeviceCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V3_4
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_ICAMERADEVICECALLBACK_H
