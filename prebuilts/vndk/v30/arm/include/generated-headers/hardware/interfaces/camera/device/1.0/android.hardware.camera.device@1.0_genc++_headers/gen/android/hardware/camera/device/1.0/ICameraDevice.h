#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_ICAMERADEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_ICAMERADEVICE_H

#include <android/hardware/camera/common/1.0/types.h>
#include <android/hardware/camera/device/1.0/ICameraDeviceCallback.h>
#include <android/hardware/camera/device/1.0/ICameraDevicePreviewCallback.h>
#include <android/hardware/camera/device/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

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
namespace V1_0 {

/**
 * Camera device HAL, legacy version
 *
 * DEPRECATED. New devices are strongly recommended to use Camera HAL v3.2 or
 * newer.
 *
 * Supports the android.hardware.Camera API, and the android.hardware.camera2
 * API in LEGACY mode only.
 *
 * Will be removed in the Android P release.
 */
struct ICameraDevice : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.camera.device@1.0::ICameraDevice"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getResourceCost
     */
    using getResourceCost_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::common::V1_0::CameraResourceCost& resourceCost)>;
    /**
     * Get camera device resource cost information.
     *
     * This method may be called at any time, including before open()
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On success.
     *     INTERNAL_ERROR:
     *         An unexpected internal camera HAL error occurred, and the
     *         resource cost is not available.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     * @return resourceCost
     *     The resources required to open this camera device, or unspecified
     *     values if status is not OK.
     */
    virtual ::android::hardware::Return<void> getResourceCost(getResourceCost_cb _hidl_cb) = 0;

    /**
     * Return callback for getCameraInfo
     */
    using getCameraInfo_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::device::V1_0::CameraInfo& info)>;
    /**
     * Get basic camera information.
     *
     * This method may be called at any time, including before open()
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On success.
     *     INTERNAL_ERROR:
     *         An unexpected internal camera HAL error occurred, and the
     *         camera information is not available.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     * @return info Basic information about this camera device, or unspecified
     *     values if status is not OK.
     */
    virtual ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) = 0;

    /**
     * setTorchMode:
     *
     * Turn on or off the torch mode of the flash unit associated with a given
     * camera ID. If the operation is successful, HAL must notify the framework
     * torch state by invoking
     * ICameraProviderCallback::torchModeStatusChange() with the new state.
     *
     * The camera device has a higher priority accessing the flash unit. When
     * there are any resource conflicts, such as when open() is called to fully
     * activate a camera device, the provider must notify the framework through
     * ICameraProviderCallback::torchModeStatusChange() that the torch mode has
     * been turned off and the torch mode state has become
     * TORCH_MODE_STATUS_NOT_AVAILABLE. When resources to turn on torch mode
     * become available again, the provider must notify the framework through
     * ICameraProviderCallback::torchModeStatusChange() that the torch mode
     * state has become TORCH_MODE_STATUS_AVAILABLE_OFF for set_torch_mode() to
     * be called.
     *
     * When the framework calls setTorchMode() to turn on the torch mode of a
     * flash unit, if HAL cannot keep multiple torch modes on simultaneously,
     * HAL must turn off the torch mode that was turned on by
     * a previous setTorchMode() call and notify the framework that the torch
     * mode state of that flash unit has become TORCH_MODE_STATUS_AVAILABLE_OFF.
     *
     * @param torchMode The new mode to set the device flash unit to.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful change to the torch state.
     *     INTERNAL_ERROR:
     *         The flash unit cannot be operated due to an unexpected internal
     *         error.
     *     ILLEGAL_ARGUMENT:
     *         The camera ID is unknown.
     *     CAMERA_IN_USE:
     *         This camera device has been opened, so the torch cannot be
     *         controlled until it is closed.
     *     MAX_CAMERAS_IN_USE:
     *         Due to other camera devices being open, or due to other
     *         resource constraints, the torch cannot be controlled currently.
     *     METHOD_NOT_SUPPORTED:
     *         This provider does not support direct operation of flashlight
     *         torch mode. The framework must open the camera device and turn
     *         the torch on through the device interface.
     *     OPERATION_NOT_SUPPORTED:
     *         This camera device does not have a flash unit. This must
     *         be returned if and only if parameter key flash-mode-values is not present.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     *
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setTorchMode(::android::hardware::camera::common::V1_0::TorchMode mode) = 0;

    /**
     * Dump state of the camera hardware.
     *
     * This must be callable at any time, whether the device is open or not.
     *
     * @param fd A native handle with one valid file descriptor. The descriptor
     *     must be able to be used with dprintf() or equivalent to dump the
     *     state of this camera device into the camera service dumpsys output.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> dumpState(const ::android::hardware::hidl_handle& fd) = 0;

    /**
     * Open the camera device for active use.
     *
     * All methods besides getResourceCost(), getCameraInfo(), setTorchMode(),
     * and dump() must not be called unless open() has been called successfully,
     * and close() has not yet been called.
     *
     * @param callback Interface to invoke by the HAL for device callbacks.
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful open of the camera device.
     *     INTERNAL_ERROR:
     *         The camera device cannot be opened due to an internal
     *         error.
     *     ILLEGAL_ARGUMENT:
     *         The callback handle is invalid (for example, it is null).
     *     CAMERA_IN_USE:
     *         This camera device is already open.
     *     MAX_CAMERAS_IN_USE:
     *         The maximal number of camera devices that can be
     *         opened concurrently were opened already.
     *     CAMERA_DISCONNECTED:
     *         This external camera device has been disconnected, and is no
     *         longer available. This interface is now stale, and a new instance
     *         must be acquired if the device is reconnected. All subsequent
     *         calls on this interface must return CAMERA_DISCONNECTED.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> open(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDeviceCallback>& callback) = 0;

    /**
     * All methods below this point must only be called between a successful
     * open() call and a close() call.
     *
     *
     * Set the callback interface through which preview frames are sent
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setPreviewWindow(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback>& window) = 0;

    /**
     * Enable a message, or set of messages.
     *
     * @param msgType The bitfield of messages to enable.
     */
    virtual ::android::hardware::Return<void> enableMsgType(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) = 0;

    /**
     * Disable a message, or a set of messages.
     *
     * Once received a call to disableMsgType(CAMERA_MSG_VIDEO_FRAME), camera
     * HAL must not rely on its client to call releaseRecordingFrame() to
     * release video recording frames sent out by the cameral HAL before and
     * after the disableMsgType(CAMERA_MSG_VIDEO_FRAME) call. Camera HAL
     * clients must not modify/access any video recording frame after calling
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME).
     *
     * @param msgType The bitfield of messages to disable.
     */
    virtual ::android::hardware::Return<void> disableMsgType(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) = 0;

    /**
     * Query whether a message, or a set of messages, is enabled. Note that
     * this is operates as an AND, if any of the messages queried are off, this
     * must return false.
     *
     * @param msgType The bitfield of messages to query.
     * @return enabled Whether all the specified flags are enabled.
     */
    virtual ::android::hardware::Return<bool> msgTypeEnabled(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) = 0;

    /**
     * Start preview mode.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> startPreview() = 0;

    /**
     * Stop a previously started preview.
     */
    virtual ::android::hardware::Return<void> stopPreview() = 0;

    /**
     * Returns true if preview is enabled.
     *
     * @return enabled Whether preview is currently enabled.
     */
    virtual ::android::hardware::Return<bool> previewEnabled() = 0;

    /**
     * Request the camera HAL to store meta data or real YUV data in the video
     * buffers sent out via CAMERA_MSG_VIDEO_FRAME for a recording session. If
     * it is not called, the default camera HAL behavior is to store real YUV
     * data in the video buffers.
     *
     * This method must be called before startRecording() in order to be
     * effective.
     *
     * If meta data is stored in the video buffers, it is up to the receiver of
     * the video buffers to interpret the contents and to find the actual frame
     * data with the help of the meta data in the buffer. How this is done is
     * outside of the scope of this method.
     *
     * Some camera HALs may not support storing meta data in the video buffers,
     * but all camera HALs must support storing real YUV data in the video
     * buffers. If the camera HAL does not support storing the meta data in the
     * video buffers when it is requested to do do, INVALID_OPERATION must be
     * returned. It is very useful for the camera HAL to pass meta data rather
     * than the actual frame data directly to the video encoder, since the
     * amount of the uncompressed frame data can be very large if video size is
     * large.
     *
     * @param enable Set to true to instruct the camera HAL to store meta data
     *     in the video buffers; false to instruct the camera HAL to store real
     *     YUV data in the video buffers.
     *
     * @return status OK on success.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> storeMetaDataInBuffers(bool enable) = 0;

    /**
     * Start record mode.
     *
     * When a record image is available, a CAMERA_MSG_VIDEO_FRAME message is
     * sent with the corresponding frame. Every record frame must be released by
     * a camera HAL client via releaseRecordingFrame() before the client calls
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME). After the client calls
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames,
     * and the client must not modify/access any video recording frames.
     *
     * @return status The status code for the operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> startRecording() = 0;

    /**
     * Stop a previously started recording.
     */
    virtual ::android::hardware::Return<void> stopRecording() = 0;

    /**
     * Returns true if recording is enabled.
     *
     * @return enabled True if recording is currently active.
     */
    virtual ::android::hardware::Return<bool> recordingEnabled() = 0;

    /**
     * Release a record frame previously returned by CAMERA_MSG_VIDEO_FRAME in
     * dataCallbackTimestamp.
     *
     * It is camera HAL client's responsibility to release video recording
     * frames sent out by the camera HAL before the camera HAL receives a call
     * to disableMsgType(CAMERA_MSG_VIDEO_FRAME). After it receives the call to
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames.
     *
     * @param memId The memory buffer to release a recording frame from.
     * @param bufferIndex The specific buffer index to return to the HAL.
     */
    virtual ::android::hardware::Return<void> releaseRecordingFrame(uint32_t memId, uint32_t bufferIndex) = 0;

    /**
     * Release a record frame previously returned by CAMERA_MSG_VIDEO_FRAME in
     * handleCallbackTimestamp.
     *
     * It is camera HAL client's responsibility to release video recording
     * frames sent out by the camera HAL before the camera HAL receives a call
     * to disableMsgType(CAMERA_MSG_VIDEO_FRAME). After it receives the call to
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames.
     *
     * @param memId The memory buffer to release a recording frame from.
     * @param bufferIndex The specific buffer index to return to the HAL.
     * @param frame The handle for a released video frame
     */
    virtual ::android::hardware::Return<void> releaseRecordingFrameHandle(uint32_t memId, uint32_t bufferIndex, const ::android::hardware::hidl_handle& frame) = 0;

    /**
     * Release a batch of record frames previously returned by CAMERA_MSG_VIDEO_FRAME
     * in handleCallbackTimestampBatch.
     *
     * It is camera HAL client's responsibility to release video recording
     * frames sent out by the camera HAL before the camera HAL receives a call
     * to disableMsgType(CAMERA_MSG_VIDEO_FRAME). After it receives the call to
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames.
     *
     * @param batch A batch of recording frames to be released by camera HAL.
     */
    virtual ::android::hardware::Return<void> releaseRecordingFrameHandleBatch(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V1_0::VideoFrameMessage>& batch) = 0;

    /**
     * Start auto focus.
     *
     * The notification callback routine is called with
     * CAMERA_MSG_FOCUS once when focusing is complete. autoFocus() can be
     * called again after that if another auto focus is needed.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> autoFocus() = 0;

    /**
     * Cancels auto-focus function.
     *
     * If the auto-focus is still in progress, this function must cancel
     * it. Whether the auto-focus is in progress or not, this function must
     * return the focus position to the default. If the camera does not support
     * auto-focus, this is a no-op.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> cancelAutoFocus() = 0;

    /**
     * Take a picture.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> takePicture() = 0;

    /**
     * Cancel a picture that was started with takePicture. Calling this method
     * when no picture is being taken is a no-op.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> cancelPicture() = 0;

    /**
     * Set the camera parameters.
     *
     * @param params The parameter string, consisting of
     *    '<key1>=<value1>; ...;<keyN>=<valueN>'.
     * @return status The status code for this operation:
     *     OK: Parameter update was successful
     *     ILLEGAL_ARGUMENT: At least one parameter was invalid or not supported
     *
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setParameters(const ::android::hardware::hidl_string& params) = 0;

    /**
     * Return callback for getParameters
     */
    using getParameters_cb = std::function<void(const ::android::hardware::hidl_string& parms)>;
    /**
     * Retrieve the camera parameters.
     */
    virtual ::android::hardware::Return<void> getParameters(getParameters_cb _hidl_cb) = 0;

    /**
     * Send command to camera driver.
     * The meaning of the arguments is defined by the value of cmd, documented
     * in the CommandType definition.
     *
     * @param cmd The command to invoke.
     * @param arg1 The first argument for the command, if needed.
     * @param arg2 The second argument for the command, if needed.
     *
     * @return status The status code for this operation.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> sendCommand(::android::hardware::camera::device::V1_0::CommandType cmd, int32_t arg1, int32_t arg2) = 0;

    /**
     * Release the hardware resources owned by this object, shutting down the
     * camera device.
     */
    virtual ::android::hardware::Return<void> close() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V1_0::ICameraDevice>> castFrom(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V1_0::ICameraDevice>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICameraDevice> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDevice> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraDevice> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDevice> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevice>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevice>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::camera::device::V1_0::ICameraDevice::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_ICAMERADEVICE_H
