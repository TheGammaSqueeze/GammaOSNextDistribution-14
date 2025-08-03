#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_ICAMERADEVICEUSER_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_ICAMERADEVICEUSER_H

#include <android/frameworks/cameraservice/common/2.0/types.h>
#include <android/frameworks/cameraservice/device/2.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
namespace V2_0 {

struct ICameraDeviceUser : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.frameworks.cameraservice.device@2.0::ICameraDeviceUser"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * disconnect from using the camera device.
     * This method must block till in-flight requests are completed and stop
     * all the requests submitted through submitRequestList().
     */
    virtual ::android::hardware::Return<void> disconnect() = 0;

    /**
     * Return callback for getCaptureRequestMetadataQueue
     */
    using getCaptureRequestMetadataQueue_cb = std::function<void(const ::android::hardware::MQDescriptorSync<uint8_t>& queue)>;
    /**
     * Retrieve the fast message queue to be optionally used in CaptureRequests,
     * to pass the settings metadata.
     * If the client decides to use FMQ, it must:
     *  - Call getCaptureRequestMetadataQueue to retrieve the fast message queue
     *  - In submitRequestList calls, for each request set the fmqMetadataSize
     *    in the settings field of physicalCameraSettings, to the size of the
     *    metadata.
     *
     * @return queue the queue that the client writes the request settings
     *         metadata to.
     */
    virtual ::android::hardware::Return<void> getCaptureRequestMetadataQueue(getCaptureRequestMetadataQueue_cb _hidl_cb) = 0;

    /**
     * Return callback for getCaptureResultMetadataQueue
     */
    using getCaptureResultMetadataQueue_cb = std::function<void(const ::android::hardware::MQDescriptorSync<uint8_t>& queue)>;
    /**
     * Retrieve the fast message queue used along with
     * ICameraDeviceCallback.onResultReceived.
     *
     * Note: The client's use of this function does not affect the hidl
     * service's decision to use / not use FMQ to pass result metadata to the
     * cleint.
     *
     * Clients implementing the callback must:
     *  - Retrieve the queue using getCaptureResultMetadataQueue.
     *  - In the implementation of ICameraDeviceCallback.onResultReceived, if
     *    PhysicalCaptureResultInfo.physicalCameraMetadata has a valid
     *    fmqMetadataSize (which is > 0), the metadata must be read from the FMQ,
     *    else, it must be read from the metadata field.
     *    The same applies to resultMetadata.
     *
     * @return queue the queue that the client reads the result metadata from.
     */
    virtual ::android::hardware::Return<void> getCaptureResultMetadataQueue(getCaptureResultMetadataQueue_cb _hidl_cb) = 0;

    /**
     * Return callback for submitRequestList
     */
    using submitRequestList_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& submitInfo)>;
    /**
     * Submit a list of capture requests.
     *
     * Note: Clients must call submitRequestList() serially if they opt
     *       to utilize an fmq (obtained by calling getCaptureRequestMetadataQueue)
     *       for any CaptureRequest's physicalCameraSettings metadata.
     *
     * @param requestList The list of CaptureRequests
     * @param isRepeating Whether the set of requests repeats indefinitely.
     *
     * @return status status code of the operation.
     * @return submitInfo data structure containing the request id of the
     *         capture request and the frame number of the last frame that will
     *         be produced(In case the request is not repeating. Otherwise it
     *         contains the frame number of the last request, of the previus
     *         batch of repeating requests, if any. If there is no previous
     *         batch, the frame number returned will be -1.)
     */
    virtual ::android::hardware::Return<void> submitRequestList(const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::CaptureRequest>& requestList, bool isRepeating, submitRequestList_cb _hidl_cb) = 0;

    /**
     * Return callback for cancelRepeatingRequest
     */
    using cancelRepeatingRequest_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, int64_t frameNumber)>;
    /**
     * Cancel the current repeating request.
     *
     * The current repeating request may be stopped by camera device due to an
     * error.
     *
     * Calling this method when there is no active repeating request, will
     * trigger Status::INVALID_OPERATION.
     *
     * @return status the status code of the opertion.
     * @return frameNumber the frame number of the last frame that will be
     *         produced from this repeating request. If there are no inflight
     *         repeating requests, this will return -1 as the frameNumber.
     *         If the status is not NO_ERROR, the frame number should not be
     *         used.
     */
    virtual ::android::hardware::Return<void> cancelRepeatingRequest(cancelRepeatingRequest_cb _hidl_cb) = 0;

    /*
     * Begin device configuration.
     *
     * @return status the status code of the operation.
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> beginConfigure() = 0;

    /**
     * End the device configuration.
     *
     * endConfigure must be called after stream configuration is complete
     * (i.e. after a call to beginConfigure and subsequent
     * createStream/deleteStream calls). It must be called before any
     * requests can be submitted.
     *
     * @param operatingMode The kind of session to create; either NORMAL_MODE,
     *        CONSTRAINED_HIGH_SPEED_MODE, or one of the vendor modes.
     * @param sessionParams Session-wide camera parameters. Empty session
     *        parameters are legal inputs.
     *
     * @return status the status code of the operation.
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> endConfigure(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode operatingMode, const ::android::hardware::hidl_vec<uint8_t>& sessionParams) = 0;

    /**
     * delete the stream specified by streamId.
     *
     * Note: deleteStream() must only be called within a beginConfigure() and an
     *       endConfigure() block.
     *
     * @param streamId the stream id of the stream to be deleted
     * @return status the status code of the operation
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> deleteStream(int32_t streamId) = 0;

    /**
     * Return callback for createStream
     */
    using createStream_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, int32_t streamID)>;
    /**
     * Create an output stream based on the given output configuration.
     *
     * Note: createStream() must only be called within a beginConfigure() and an
     *       endConfigure() block.
     *
     * @param outputConfiguration size, format, and other parameters for the
     *        stream
     * @return status the status code of the operation.
     * @return streamID new stream ID generated.
     */
    virtual ::android::hardware::Return<void> createStream(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration, createStream_cb _hidl_cb) = 0;

    /**
     * Return callback for createDefaultRequest
     */
    using createDefaultRequest_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& metadata)>;
    /**
     * Create a default capture request for capturing an image.
     *
     * @param templateId the type of capture request to be created.
     *
     * @return status the status code of the operation.
     * @return metadata the settings metadata of the request.
     */
    virtual ::android::hardware::Return<void> createDefaultRequest(::android::frameworks::cameraservice::device::V2_0::TemplateId templateId, createDefaultRequest_cb _hidl_cb) = 0;

    /**
     * Block until the device is idle.
     *
     * Note: This method will not block and instead will fail with
     *       Status::INVALID_OPERATION if there are active repeating requests.
     *
     * @return status the status code of the operation.
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> waitUntilIdle() = 0;

    /**
     * Return callback for flush
     */
    using flush_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, int64_t lastFrameNumber)>;
    /**
     * flush all the requests pending on the device.
     *
     * @return status the status code of the operation.
     * @return lastFrameNumber the frame number of the last frame flushed.
     */
    virtual ::android::hardware::Return<void> flush(flush_cb _hidl_cb) = 0;

    /**
     * Update a previously set output configuration.
     *
     * Note: It is legal to call this method outside of
     *       beginConfigure()/endConfigure() blocks and also when the device
     *       is not idle.
     *
     * @param streamId the stream id whose output configuration needs to be
     *        updated.
     * @param outputConfiguration the new outputConfiguration.
     *
     * @return status the status code of the operation.
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> updateOutputConfiguration(int32_t streamId, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration) = 0;

    /**
     * Return callback for isSessionConfigurationSupported
     */
    using isSessionConfigurationSupported_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, bool supported)>;
    /**
     * Check whether a particular session configuration has camera device
     * support.
     *
     * @param sessionConfiguration Specific session configuration to be verified.
     *
     * @return status the status code of the operation
     * @return true  - in case the stream combination is supported.
     *         false - in case there is no device support.
     */
    virtual ::android::hardware::Return<void> isSessionConfigurationSupported(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& sessionConfiguration, isSessionConfigurationSupported_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>> castFrom(const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICameraDeviceUser> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceUser> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceUser> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceUser> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraDeviceUser> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceUser> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceUser> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceUser> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_ICAMERADEVICEUSER_H
