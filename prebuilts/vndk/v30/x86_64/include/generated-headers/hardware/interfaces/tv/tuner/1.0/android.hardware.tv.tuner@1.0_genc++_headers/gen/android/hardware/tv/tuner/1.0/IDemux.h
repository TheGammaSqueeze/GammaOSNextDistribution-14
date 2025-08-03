#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IDEMUX_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IDEMUX_H

#include <android/hardware/tv/tuner/1.0/IDvr.h>
#include <android/hardware/tv/tuner/1.0/IDvrCallback.h>
#include <android/hardware/tv/tuner/1.0/IFilter.h>
#include <android/hardware/tv/tuner/1.0/IFilterCallback.h>
#include <android/hardware/tv/tuner/1.0/ITimeFilter.h>
#include <android/hardware/tv/tuner/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

/**
 * Demultiplexer(Demux) takes a single multiplexed input and splits it into
 * one or more output.
 */
struct IDemux : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.tv.tuner@1.0::IDemux"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Set a frontend resource as data input of the demux
     *
     * It is used by the client to specify a hardware frontend as data source of
     * this demux instance. A demux instance can have only one data source.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if failed for wrong state.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setFrontendDataSource(uint32_t frontendId) = 0;

    /**
     * Return callback for openFilter
     */
    using openFilter_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilter>& filter)>;
    /**
     * Open a new filter in the demux
     *
     * It is used by the client to open a filter in the demux.
     *
     * @param type the type of the filter to be added.
     * @param bufferSize the buffer size of the filter to be opened. It's used
     * to create a FMQ(Fast Message Queue) to hold data output from the filter.
     * @param cb the callback for the filter to be used to send notifications
     * back to the client.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if failed for wrong state.
     *         UNKNOWN_ERROR if failed for other reasons.
     * @return filter the filter instance of the newly added.
     */
    virtual ::android::hardware::Return<void> openFilter(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilterCallback>& cb, openFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for openTimeFilter
     */
    using openTimeFilter_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, const ::android::sp<::android::hardware::tv::tuner::V1_0::ITimeFilter>& timeFilter)>;
    /**
     * Open time filter of the demux
     *
     * It is used by the client to open time filter of the demux.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         UNAVAILABLE if time filter is not supported.
     *         INVALID_STATE if failed for wrong state.
     *         UNKNOWN_ERROR if failed for other reasons.
     * @return timeFilter the time filter instance of the newly added.
     */
    virtual ::android::hardware::Return<void> openTimeFilter(openTimeFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for getAvSyncHwId
     */
    using getAvSyncHwId_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, uint32_t avSyncHwId)>;
    /**
     * Get hardware sync ID for audio and video.
     *
     * It is used by the client to get the hardware sync ID for audio and video.
     *
     * @param filter the filter instance.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_ARGUMENT if failed for a wrong filter ID.
     *         UNKNOWN_ERROR if failed for other reasons.
     * @return avSyncHwId the id of hardware A/V sync.
     */
    virtual ::android::hardware::Return<void> getAvSyncHwId(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilter>& filter, getAvSyncHwId_cb _hidl_cb) = 0;

    /**
     * Return callback for getAvSyncTime
     */
    using getAvSyncTime_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, uint64_t time)>;
    /**
     * Get current time stamp to use for A/V sync
     *
     * It is used by the client to get current time stamp for A/V sync. HW is
     * supported to increment and maintain current time stamp.
     *
     * @param avSyncHwId the hardware id of A/V sync.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_ARGUMENT if failed for a wrong hardware ID of A/V sync.
     *         UNKNOWN_ERROR if failed for other reasons.
     * @return time the current time stamp of hardware A/V sync. The time stamp
     * based on 90KHz has the same format as PTS (Presentation Time Stamp).
     */
    virtual ::android::hardware::Return<void> getAvSyncTime(uint32_t avSyncHwId, getAvSyncTime_cb _hidl_cb) = 0;

    /**
     * Close the Demux instance
     *
     * It is used by the client to release the demux instance. HAL clear
     * underneath resource. client mustn't access the instance any more.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> close() = 0;

    /**
     * Return callback for openDvr
     */
    using openDvr_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, const ::android::sp<::android::hardware::tv::tuner::V1_0::IDvr>& dvr)>;
    /**
     * Open a DVR (Digital Video Record) instance in the demux
     *
     * It is used by the client to record and playback.
     *
     * @param type specify which kind of DVR to open.
     * @param bufferSize the buffer size of the output to be added. It's used to
     * create a FMQ(Fast Message Queue) to hold data from selected filters.
     * @param cb the callback for the DVR to be used to send notifications
     * back to the client.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         OUT_OF_MEMORY if failed for not enough memory.
     *         UNKNOWN_ERROR if failed for other reasons.
     * @return dvr a DVR instance.
     */
    virtual ::android::hardware::Return<void> openDvr(::android::hardware::tv::tuner::V1_0::DvrType type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IDvrCallback>& cb, openDvr_cb _hidl_cb) = 0;

    /**
     * Connect Conditional Access Modules (CAM) through Common Interface (CI)
     *
     * It is used by the client to connect CI-CAM. The demux uses the output
     * from the frontend as the input by default, and must change to use the
     * output from CI-CAM as the input after this call take place.
     *
     * @param ciCamId specify CI-CAM Id to connect.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> connectCiCam(uint32_t ciCamId) = 0;

    /**
     * Disconnect Conditional Access Modules (CAM)
     *
     * It is used by the client to disconnect CI-CAM. The demux will use the
     * output from the frontend as the input after this call take place.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> disconnectCiCam() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::IDemux>> castFrom(const ::android::sp<::android::hardware::tv::tuner::V1_0::IDemux>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::IDemux>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IDemux> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDemux> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDemux> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDemux> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IDemux> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDemux> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDemux> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDemux> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::IDemux>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::IDemux>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::tv::tuner::V1_0::IDemux::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace tuner
}  // namespace tv
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IDEMUX_H
