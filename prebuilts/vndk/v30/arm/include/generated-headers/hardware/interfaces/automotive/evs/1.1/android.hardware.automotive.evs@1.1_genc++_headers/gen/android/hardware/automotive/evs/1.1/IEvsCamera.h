#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSCAMERA_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSCAMERA_H

#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.0/types.h>
#include <android/hardware/automotive/evs/1.1/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

/**
 * Represents a single camera and is the primary interface for capturing images.
 */
struct IEvsCamera : public ::android::hardware::automotive::evs::V1_0::IEvsCamera {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.automotive.evs@1.1::IEvsCamera"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getCameraInfo
     */
    using getCameraInfo_cb = std::function<void(const ::android::hardware::automotive::evs::V1_0::CameraDesc& info)>;
    /**
     * Returns the ID of this camera.
     *
     * @return info The description of this camera.  This must be the same value as
     *              reported by EvsEnumerator::getCameraList().
     */
    virtual ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) = 0;

    /**
     * Specifies the depth of the buffer chain the camera is asked to support.
     *
     * Up to this many frames may be held concurrently by the client of IEvsCamera.
     * If this many frames have been delivered to the receiver without being returned
     * by doneWithFrame, the stream must skip frames until a buffer is returned for reuse.
     * It is legal for this call to come at any time, even while streams are already running,
     * in which case buffers should be added or removed from the chain as appropriate.
     * If no call is made to this entry point, the IEvsCamera must support at least one
     * frame by default. More is acceptable.
     *
     * @param  bufferCount Number of buffers the client of IEvsCamera may hold concurrently.
     * @return result EvsResult::OK is returned if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaxFramesInFlight(uint32_t bufferCount) = 0;

    /**
     * Request to start EVS camera stream from this camera.
     *
     * The IEvsCameraStream must begin receiving calls with various events
     * including new image frame ready until stopVideoStream() is called.
     *
     * @param  receiver IEvsCameraStream implementation.
     * @return result EvsResult::OK is returned if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> startVideoStream(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCameraStream>& receiver) = 0;

    /**
     * Return a frame that was delivered by to the IEvsCameraStream.
     *
     * When done consuming a frame delivered to the IEvsCameraStream
     * interface, it must be returned to the IEvsCamera for reuse.
     * A small, finite number of buffers are available (possibly as small
     * as one), and if the supply is exhausted, no further frames may be
     * delivered until a buffer is returned.
     *
     * @param  buffer A buffer to be returned.
     */
    virtual ::android::hardware::Return<void> doneWithFrame(const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) = 0;

    /**
     * Stop the delivery of EVS camera frames.
     *
     * Because delivery is asynchronous, frames may continue to arrive for
     * some time after this call returns. Each must be returned until the
     * closure of the stream is signaled to the IEvsCameraStream.
     * This function cannot fail and is simply ignored if the stream isn't running.
     */
    virtual ::android::hardware::Return<void> stopVideoStream() = 0;

    /**
     * Request driver specific information from the HAL implementation.
     *
     * The values allowed for opaqueIdentifier are driver specific,
     * but no value passed in may crash the driver. The driver should
     * return 0 for any unrecognized opaqueIdentifier.
     *
     * @param  opaqueIdentifier An unique identifier of the information to
     *                          request.
     * @return value            Requested information.  Zero is returned if the
     *                          driver does not recognize a given identifier.
     */
    virtual ::android::hardware::Return<int32_t> getExtendedInfo(uint32_t opaqueIdentifier) = 0;

    /**
     * Send a driver specific value to the HAL implementation.
     *
     * This extension is provided to facilitate car specific
     * extensions, but no HAL implementation may require this call
     * in order to function in a default state.
     * INVALID_ARG is returned if the opaqueValue is not meaningful to
     * the driver implementation.
     *
     * @param  opaqueIdentifier An unique identifier of the information to
     *                          program.
     *         opaqueValue      A value to program.
     * @return result           EvsResult::OK is returned if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue) = 0;

    /**
     * Return callback for getCameraInfo_1_1
     */
    using getCameraInfo_1_1_cb = std::function<void(const ::android::hardware::automotive::evs::V1_1::CameraDesc& info)>;
    /**
     * Returns the description of this camera.
     *
     * @return info The description of this camera.  This must be the same value as
     *              reported by EvsEnumerator::getCameraList_1_1().
     */
    virtual ::android::hardware::Return<void> getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) = 0;

    /**
     * Return callback for getPhysicalCameraInfo
     */
    using getPhysicalCameraInfo_cb = std::function<void(const ::android::hardware::automotive::evs::V1_1::CameraDesc& info)>;
    /**
     * Returns the description of the physical camera device that backs this
     * logical camera.
     *
     * If a requested device does not either exist or back this logical device,
     * this method returns a null camera descriptor.  And, if this is called on
     * a physical camera device, this method is the same as getCameraInfo_1_1()
     * method if a given device ID is matched.  Otherwise, this will return a
     * null camera descriptor.
     *
     * @param  deviceId Physical camera device identifier string.
     * @return info     The description of a member physical camera device.
     *                  This must be the same value as reported by
     *                  EvsEnumerator::getCameraList_1_1().
     */
    virtual ::android::hardware::Return<void> getPhysicalCameraInfo(const ::android::hardware::hidl_string& deviceId, getPhysicalCameraInfo_cb _hidl_cb) = 0;

    /**
     * Requests to pause EVS camera stream events.
     *
     * Like stopVideoStream(), events may continue to arrive for some time
     * after this call returns. Delivered frame buffers must be returned.
     *
     * @return result EvsResult::OK is returned if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> pauseVideoStream() = 0;

    /**
     * Requests to resume EVS camera stream.
     *
     * @return result EvsResult::OK is returned if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> resumeVideoStream() = 0;

    /**
     * Returns frame that were delivered by to the IEvsCameraStream.
     *
     * When done consuming a frame delivered to the IEvsCameraStream
     * interface, it must be returned to the IEvsCamera for reuse.
     * A small, finite number of buffers are available (possibly as small
     * as one), and if the supply is exhausted, no further frames may be
     * delivered until a buffer is returned.
     *
     * @param  buffer Buffers to be returned.
     * @return result Return EvsResult::OK if this call is successful.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> doneWithFrame_1_1(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffer) = 0;

    /**
     * Requests to be a master client.
     *
     * When multiple clients subscribe to a single camera hardware and one of
     * them adjusts a camera parameter such as the contrast, it may disturb
     * other clients' operations.  Therefore, the client must call this method
     * to be a master client.  Once it becomes a master, it will be able to
     * change camera parameters until either it dies or explicitly gives up the
     * role.
     *
     * @return result EvsResult::OK if a master role is granted.
     *                EvsResult::OWNERSHIP_LOST if there is already a
     *                master client.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaster() = 0;

    /**
     * Sets to be a master client forcibly.
     *
     * The client, which owns the display, has a high priority and can take over
     * a master role from other clients without the display.
     *
     * @param  display IEvsDisplay handle.  If a given display is in either
     *                 NOT_VISIBLE, VISIBLE_ON_NEXT_FRAME, or VISIBLE state, the
     *                 calling client is considered as the high priority client
     *                 and therefore allowed to take over a master role from
     *                 existing master client.
     *
     * @return result  EvsResult::OK if a master role is granted.
     *                 EvsResult::INVALID_ARG if a given display handle is null
     *                 or in valid states.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> forceMaster(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display) = 0;

    /**
     * Retires from a master client role.
     *
     * @return result EvsResult::OK if this call is successful.
     *                EvsResult::INVALID_ARG if the caller client is not a
     *                master client.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> unsetMaster() = 0;

    /**
     * Return callback for getParameterList
     */
    using getParameterList_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::CameraParam>& params)>;
    /**
     * Retrieves a list of parameters this camera supports.
     *
     * @return params A list of CameraParam that this camera supports.
     */
    virtual ::android::hardware::Return<void> getParameterList(getParameterList_cb _hidl_cb) = 0;

    /**
     * Return callback for getIntParameterRange
     */
    using getIntParameterRange_cb = std::function<void(int32_t min, int32_t max, int32_t step)>;
    /**
     * Requests a valid value range of a camera parameter
     *
     * @param  id    The identifier of camera parameter, CameraParam enum.
     *
     * @return min   The lower bound of valid parameter value range.
     * @return max   The upper bound of valid parameter value range.
     * @return step  The resolution of values in valid range.
     */
    virtual ::android::hardware::Return<void> getIntParameterRange(::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameterRange_cb _hidl_cb) = 0;

    /**
     * Return callback for setIntParameter
     */
    using setIntParameter_cb = std::function<void(::android::hardware::automotive::evs::V1_0::EvsResult result, const ::android::hardware::hidl_vec<int32_t>& effectiveValue)>;
    /**
     * Requests to set a camera parameter.
     *
     * Only a request from the master client will be processed successfully.
     * When this method is called on a logical camera device, it will be forwarded
     * to each physical device and, if it fails to program any physical device,
     * it will return an error code with the same number of effective values as
     * the number of backing camera devices.
     *
     * @param  id             The identifier of camera parameter, CameraParam enum.
     *         value          A desired parameter value.
     * @return result         EvsResult::OK if it succeeds to set a parameter.
     *                        EvsResult::INVALID_ARG if either the request is
     *                        not made by a master client, or a requested
     *                        parameter is not supported.
     *                        EvsResult::UNDERLYING_SERVICE_ERROR if it fails to
     *                        program a value by any other reason.
     *         effectiveValue Programmed parameter values.  This may differ
     *                        from what the client gives if, for example, the
     *                        driver does not support a target parameter.
     */
    virtual ::android::hardware::Return<void> setIntParameter(::android::hardware::automotive::evs::V1_1::CameraParam id, int32_t value, setIntParameter_cb _hidl_cb) = 0;

    /**
     * Return callback for getIntParameter
     */
    using getIntParameter_cb = std::function<void(::android::hardware::automotive::evs::V1_0::EvsResult result, const ::android::hardware::hidl_vec<int32_t>& value)>;
    /**
     * Retrieves values of given camera parameter.
     *
     * @param  id     The identifier of camera parameter, CameraParam enum.
     * @return result EvsResult::OK if it succeeds to read a parameter.
     *                EvsResult::INVALID_ARG if either a requested parameter is
     *                not supported.
     *         value  Values of requested camera parameter, the same number of
     *                values as backing camera devices.
     */
    virtual ::android::hardware::Return<void> getIntParameter(::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameter_cb _hidl_cb) = 0;

    /**
     * Return callback for getExtendedInfo_1_1
     */
    using getExtendedInfo_1_1_cb = std::function<void(::android::hardware::automotive::evs::V1_0::EvsResult result, const ::android::hardware::hidl_vec<uint8_t>& value)>;
    /**
     * Request driver specific information from the HAL implementation.
     *
     * The values allowed for opaqueIdentifier are driver specific,
     * but no value passed in may crash the driver. The driver should
     * return EvsResult::INVALID_ARG for any unrecognized opaqueIdentifier.
     *
     * @param  opaqueIdentifier An unique identifier of the information to
     *                          request.
     * @return result           EvsResult::OK if the driver recognizes a given
     *                          identifier.
     *                          EvsResult::INVALID_ARG, otherwise.
     * @return value            Requested information.  Zero-size vector is
     *                          returned if the driver does not recognize a
     *                          given identifier.
     */
    virtual ::android::hardware::Return<void> getExtendedInfo_1_1(uint32_t opaqueIdentifier, getExtendedInfo_1_1_cb _hidl_cb) = 0;

    /**
     * Send a driver specific value to the HAL implementation.
     *
     * This extension is provided to facilitate car specific
     * extensions, but no HAL implementation may require this call
     * in order to function in a default state.
     * INVALID_ARG is returned if the opaqueValue is not meaningful to
     * the driver implementation.
     *
     * @param  opaqueIdentifier An unique identifier of the information to
     *                          program.
     *         opaqueValue      A value to program.
     * @return result           EvsResult::OK is returned if this call is successful.
     *                          EvsResult::INVALID_ARG, otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo_1_1(uint32_t opaqueIdentifier, const ::android::hardware::hidl_vec<uint8_t>& opaqueValue) = 0;

    /**
     * Return callback for importExternalBuffers
     */
    using importExternalBuffers_cb = std::function<void(::android::hardware::automotive::evs::V1_0::EvsResult result, int32_t delta)>;
    /**
     * Import external buffers to capture frames
     *
     * This API must be called with a physical camera device identifier.
     *
     * @param  buffers  A list of buffers allocated by the caller.  EvsCamera
     *                  will use these buffers to capture frames, in addition to
     *                  other buffers already in its buffer pool.
     * @return result   EvsResult::OK if it succeeds to import buffers.
     *                  EvsResult::UNDERLYING_SERVICE_ERROR if this is called
     *                  for logical camera devices or EVS fails to import
     *                  buffers.
     *         delta    The amount of buffer pool size changes after importing
     *                  given buffers.
     */
    virtual ::android::hardware::Return<void> importExternalBuffers(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffers, importExternalBuffers_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>> castFrom(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>> castFrom(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IEvsCamera> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEvsCamera> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEvsCamera> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEvsCamera> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IEvsCamera> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEvsCamera> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEvsCamera> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEvsCamera> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::automotive::evs::V1_1::IEvsCamera::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSCAMERA_H
