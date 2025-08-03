#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_ISURROUNDVIEW3DSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_ISURROUNDVIEW3DSESSION_H

#include <android/hardware/automotive/sv/1.0/ISurroundViewSession.h>
#include <android/hardware/automotive/sv/1.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

/**
 * Interface representing a surround view 3d session.
 *
 * Surround view 3d provides a virtual view from any desired position in the 3d space around the
 * car. Surround view 3d creates an approximate 3d surface around the car to match the surrounds
 * and provides a virtual view as seen on this surface.
 */
struct ISurroundView3dSession : public ::android::hardware::automotive::sv::V1_0::ISurroundViewSession {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.automotive.sv@1.0::ISurroundView3dSession"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Requests to start receiving surround view frames.
     *
     * For surround view 3d, setViews() must be set before calling startStream().
     *
     * @param stream     Stream to receiving callbacks for the session.
     * @return svResult  Returns OK if successful, returns VIEW_NOT_SET if setViews() is not
     *                   called for surround view 3d, appropriate error results otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> startStream(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundViewStream>& stream) = 0;

    /**
     * Requests to stop stream.
     *
     * Frames may continue to arrive after call returns. Each must be returned until
     * the closure of the stream is signaled by the ISurroundViewStream.
     */
    virtual ::android::hardware::Return<void> stopStream() = 0;

    /**
     * Signal from client that a frame, which was delivered by the stream, has been consumed.
     *
     * @param  svFramesDesc Descriptor to signal done with frame.
     */
    virtual ::android::hardware::Return<void> doneWithFrames(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& svFramesDesc) = 0;

    /**
     * Sets the desired views of surround view 3d.
     *
     * Surround view 3d takes a list of desired virtual view points and provides an output frame
     * for each view. Default view list is a single view from behind the car front facing in the
     * front direction.
     * A call to setViews() results in the views set by a previous call to be discarded.
     * Each view set is identified by an Id which is returned with the corresponding output frame
     * of that view.
     * Clients can call setViews() at any stage of the session (before/after startStream). Client
     * may continue to receive frames of previous views after setViews() call for a while and can
     * identify updated set of views once effective using the view Id provided in the updated
     * views frames.
     *
     * @param views     List of desired views to generate output frames.
     * @return svResult Returns OK if successful, appropriate error result otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> setViews(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::View3d>& views) = 0;

    /**
     * Sets the configuration of 3d surround view.
     *
     * Configuration is used for supported different target use-cases of the surround view eg.
     * fullscreen view or preview. A set config call can be performed at anytime (before or after
     * startStream) of the session.
     * Once config change is complete, a CONFIG_CHANGED event is sent, after which
     * all frames received will be of the updated config.
     *
     * @param sv3dConfig Configuration to set.
     * @return svResult  Returns OK if successful, appropriate error result otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> set3dConfig(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& sv3dConfig) = 0;

    /**
     * Return callback for get3dConfig
     */
    using get3dConfig_cb = std::function<void(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& sv3dConfig)>;
    /**
     * Gets the current configuration of the 3d surround view.
     *
     * Configuration is used for supported different target use-cases of the surround view eg.
     * fullscreen view or preview. Use setConfig call to set a configuration.
     *
     * @return sv3dConfig The current active configuration of the 3d session.
     */
    virtual ::android::hardware::Return<void> get3dConfig(get3dConfig_cb _hidl_cb) = 0;

    /**
     * Updates 3d overlays in scene.
     *
     * updateOverlays() provides a way to set a 3d overlay object in the scene. An overlay is an
     * 3d object in the scene which can be a visual indicator to provide additional information eg.
     * parking sensor distance indicators or overlays for objects in scene.
     *
     * An overlay object is defined by a set of points (forming triangles) with some color and
     * transparency values and each overlay is identified by an overlay Id.
     * When an overlay with a new Id is passed, a new overlay is added to the scene.
     * When an overlay with previous id is passed, its vertices/color are updated with passed data.
     * If the overlay data is empty, the overlay is removed from the scene.
     *
     * @param overlaysData   Object with shared memory containing overlays to add/update in the
     *                       scene. Refer to OverlaysData structure for layout in shared memory.
     * @return svResult      Returns OK if successful, appropriate error result otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> updateOverlays(const ::android::hardware::automotive::sv::V1_0::OverlaysData& overlaysData) = 0;

    /**
     * Return callback for projectCameraPointsTo3dSurface
     */
    using projectCameraPointsTo3dSurface_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point3dFloat>& points3d)>;
    /**
     * Projects points on camera image to surround view 3D surface.
     *
     * Useful for mapping points detected on individual camera frames onto the surround view 3D
     * surface, these 3d points can then be used to set overlays using the updateOverlays() for
     * the detected objects in the scene.
     * Note:
     * 3d points returned are projected on an approximate 3d surface and do not provide the exact
     * 3d location.
     *
     * @param cameraPoints  List of camera pixel points to be projected in range including (0, 0)
     *                      and (width - 1, height -1) of camera frame. If point is outside camera
     * frame INVALID_ARG error is returned.
     * @param cameraId      Id of the EvsCamera to use for projecting points. Id must be one of the
     *                      cameras as returned by getCameraIds() else INVALID_ARG error is returned
     * @return points3d     Returns a list of 3d points on the approximate 3d surface in the
     *                      automotive coordinate system in the same order as cameraPoints.
     *                      Points that do not project to 3d surface are set with inValid true.
     */
    virtual ::android::hardware::Return<void> projectCameraPointsTo3dSurface(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point2dInt>& cameraPoints, const ::android::hardware::hidl_string& cameraId, projectCameraPointsTo3dSurface_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>> castFrom(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>> castFrom(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundViewSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISurroundView3dSession> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISurroundView3dSession> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISurroundView3dSession> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISurroundView3dSession> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISurroundView3dSession> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISurroundView3dSession> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISurroundView3dSession> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISurroundView3dSession> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::automotive::sv::V1_0::ISurroundView3dSession::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace sv
}  // namespace automotive
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_ISURROUNDVIEW3DSESSION_H
