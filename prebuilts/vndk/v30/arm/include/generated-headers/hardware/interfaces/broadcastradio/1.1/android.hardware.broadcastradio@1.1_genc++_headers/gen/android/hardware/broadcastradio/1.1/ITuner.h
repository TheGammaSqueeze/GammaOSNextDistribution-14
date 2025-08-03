#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_ITUNER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_ITUNER_H

#include <android/hardware/broadcastradio/1.0/ITuner.h>
#include <android/hardware/broadcastradio/1.1/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {

struct ITuner : public ::android::hardware::broadcastradio::V1_0::ITuner {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.broadcastradio@1.1::ITuner"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Apply current radio band configuration (band, range, channel spacing...).
     * Automatically cancels pending scan, step or tune.
     * ITunerCallback.configChange() method MUST be called once the
     * configuration is applied or a failure occurs or after a time out.
     * @param config The band configuration to apply.
     * @return result OK if configuration could be applied
     *                NOT_INITIALIZED in case of initialization error.
     *                INVALID_ARGUMENTS if configuration requested is invalid
     *
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> setConfiguration(const ::android::hardware::broadcastradio::V1_0::BandConfig& config) = 0;

    /**
     * Return callback for getConfiguration
     */
    using getConfiguration_cb = std::function<void(::android::hardware::broadcastradio::V1_0::Result result, const ::android::hardware::broadcastradio::V1_0::BandConfig& config)>;
    /**
     * Retrieve current radio band configuration.
     * @return result OK if valid configuration is returned,
     *                NOT_INITIALIZED in case of initialization error.
     * @param config Current band configuration
     */
    virtual ::android::hardware::Return<void> getConfiguration(getConfiguration_cb _hidl_cb) = 0;

    /**
     * Start scanning up to next valid station.
     * Shall be called only when a valid configuration has been applied.
     * Automatically cancels pending scan, step or tune.
     * ITunerCallback.tuneComplete() MUST be called once locked on a station
     * or after a time out or full band scan if no station found.
     * The status should indicate if a valid station is tuned or not.
     * @param direction UP or DOWN.
     * @param skipSubChannel valid for HD radio or digital radios only:
     *                       ignore sub channels (e.g SPS for HD radio).
     * @return result OK if scan successfully started
     *                INVALID_STATE if called out of sequence
     *                NOT_INITIALIZED if another error occurs
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> scan(::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel) = 0;

    /**
     * Move one channel spacing up or down.
     * Must be called when a valid configuration has been applied.
     * Automatically cancels pending scan, step or tune.
     * ITunerCallback.tuneComplete() MUST be called once locked on a station
     * or after a time out or full band scan if no station found.
     * The status should indicate if a valid station is tuned or not.
     * @param direction UP or DOWN.
     * @param skipSubChannel valid for HD radio or digital radios only:
     *                       ignore sub channels (e.g SPS for HD radio).
     * @return result OK if scan successfully started
     *                INVALID_STATE if called out of sequence
     *                NOT_INITIALIZED if another error occurs
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> step(::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel) = 0;

    /**
     * Tune to specified channel.
     * Must be called when a valid configuration has been applied.
     * Automatically cancels pending scan, step or tune.
     * ITunerCallback.tuneComplete() MUST be called once locked on a station
     * or after a time out or full band scan if no station found.
     * The status should indicate if a valid station is tuned or not.
     * @param channel Channel to tune to. A frequency in kHz for AM/FM/HD Radio
     *                bands.
     * @param subChannel Valid for HD radio or digital radios only
     *                   (e.g SPS number for HD radio)..
     * @return result OK if scan successfully started
     *                INVALID_ARGUMENTS if invalid arguments are passed
     *                INVALID_STATE if called out of sequence
     *                NOT_INITIALIZED if another error occurs
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> tune(uint32_t channel, uint32_t subChannel) = 0;

    /**
     * Cancel a scan, step or tune operation.
     * Shall be called only while a scan, step or tune operation is pending.
     * ITunerCallback.tuneComplete() MUST NOT be sent by the HAL.
     * @return result OK if scan successfully started
     *                INVALID_STATE if called out of sequence
     *                NOT_INITIALIZED if another error occurs
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> cancel() = 0;

    /**
     * Return callback for getProgramInformation
     */
    using getProgramInformation_cb = std::function<void(::android::hardware::broadcastradio::V1_0::Result result, const ::android::hardware::broadcastradio::V1_0::ProgramInfo& info)>;
    /**
     * Retrieve current station information.
     * @return result OK if scan successfully started
     *                NOT_INITIALIZED if another error occurs
     * @return info Current program information.
     */
    virtual ::android::hardware::Return<void> getProgramInformation(getProgramInformation_cb _hidl_cb) = 0;

    /**
     * Tune to a specified program.
     *
     * For AM/FM, it must be called when a valid configuration has been applied.
     * Automatically cancels pending scan, step or tune.
     *
     * If method returns OK, ITunerCallback.tuneComplete_1_1() MUST be called:
     * - once successfully tuned;
     * - after a time out;
     * - after a full band scan, if no station found.
     *
     * The tuned field of ProgramInfo should indicate if tuned to a valid
     * station or not.
     *
     * @param program Program to tune to.
     * @return result OK if successfully started tunning.
     *                INVALID_ARGUMENTS if invalid arguments are passed.
     *                NOT_INITIALIZED if another error occurs.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> tuneByProgramSelector(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& program) = 0;

    /**
     * Cancels announcement.
     *
     * If it was traffic announcement, trafficAnnouncement(false) callback
     * should be called (just like it was ended in a normal way). Similarly for
     * emergency announcement. If there was no announcement, then no action
     * should be taken.
     *
     * There is a race condition between calling cancelAnnouncement and the
     * actual announcement being finished, so trafficAnnouncement /
     * emergencyAnnouncement callback should be tracked with proper locking.
     *
     * @return result OK if successfully cancelled announcement or there was
     *                no announcement.
     *                NOT_INITIALIZED if another error occurs.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> cancelAnnouncement() = 0;

    /**
     * Return callback for getProgramInformation_1_1
     */
    using getProgramInformation_1_1_cb = std::function<void(::android::hardware::broadcastradio::V1_0::Result result, const ::android::hardware::broadcastradio::V1_1::ProgramInfo& info)>;
    /**
     * Retrieve current station information.
     * @return result OK if scan successfully started
     *                NOT_INITIALIZED if another error occurs
     * @return info Current program information.
     */
    virtual ::android::hardware::Return<void> getProgramInformation_1_1(getProgramInformation_1_1_cb _hidl_cb) = 0;

    /**
     * Initiates a background scan to update internally cached program list.
     *
     * HAL client may not need to initiate the scan explicitly with this call,
     * ie. HAL implementation MAY perform the scan on boot. It's a common
     * practice in devices with two physical tuners with background scanning.
     *
     * Device must call backgroundScanComplete if the result is OK, even if the
     * scan fails later (it must pass proper result through the callback).
     * Otherwise, backgroundScanComplete must not be called as a result of this
     * certain attempt. It may still be called as a response to another call to
     * startBackgroundScan, former or latter.
     *
     * Device may utilize an already running (but not finished) scan for
     * subsequent calls to startBackgroundScan, issuing a single
     * backgroundScanComplete callback.
     *
     * If a device supports continuous background scanning, it may succeed
     * (return OK and call backgroundScanComplete) without any additional
     * operation performed.
     *
     * Foreground scanning may be implemented in the front end app with
     * @1.0::ITuner scan operation.
     *
     * @return result OK if the scan was properly scheduled (this does not mean
     *                it successfully finished).
     *                UNAVAILABLE if the background scan is unavailable,
     *                ie. temporarily due to ongoing foreground
     *                playback in single-tuner device.
     *                NOT_INITIALIZED other error, ie. HW failure.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_1::ProgramListResult> startBackgroundScan() = 0;

    /**
     * Return callback for getProgramList
     */
    using getProgramList_cb = std::function<void(::android::hardware::broadcastradio::V1_1::ProgramListResult result, const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_1::ProgramInfo>& programList)>;
    /**
     * Retrieve station list.
     *
     * This call does not trigger actual scan, but operates on the list cached
     * internally at the driver level.
     *
     * @param vendorFilter vendor-specific filter for the stations to be retrieved.
     *               An empty vector MUST result in full list for a given tuner.
     * @return result OK if the list was successfully retrieved.
     *                INVALID_ARGUMENTS if invalid arguments are passed
     *                NOT_READY if the scan is in progress.
     *                NOT_STARTED if the scan has not been started, client may
     *                call startBackgroundScan to fix this.
     *                NOT_INITIALIZED if any other error occurs.
     * @return programList List of stations available for user.
     */
    virtual ::android::hardware::Return<void> getProgramList(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_1::VendorKeyValue>& vendorFilter, getProgramList_cb _hidl_cb) = 0;

    /**
     * Forces the analog playback for the supporting radio technology.
     *
     * User may disable digital playback for FM HD Radio or hybrid FM/DAB with
     * this option. This is purely user choice, ie. does not reflect digital-
     * analog handover managed from the HAL implementation side.
     *
     * Some radio technologies may not support this, ie. DAB.
     *
     * @param isForced true to force analog, false for a default behaviour.
     * @return result OK if the setting was successfully done.
     *                INVALID_STATE if the switch is not supported at current
     *                configuration.
     *                NOT_INITIALIZED if any other error occurs.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> setAnalogForced(bool isForced) = 0;

    /**
     * Return callback for isAnalogForced
     */
    using isAnalogForced_cb = std::function<void(::android::hardware::broadcastradio::V1_0::Result result, bool isForced)>;
    /**
     * Checks, if the analog playback is forced, see setAnalogForced.
     *
     * The isForced value is only valid if result was OK.
     *
     * @return result OK if the call succeeded and isForced is valid.
     *                INVALID_STATE if the switch is not supported at current
     *                configuration.
     *                NOT_INITIALIZED if any other error occurs.
     * @return isForced true if analog is forced, false otherwise.
     */
    virtual ::android::hardware::Return<void> isAnalogForced(isAnalogForced_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>> castFrom(const ::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>> castFrom(const ::android::sp<::android::hardware::broadcastradio::V1_0::ITuner>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ITuner> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ITuner> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ITuner> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ITuner> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ITuner> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ITuner> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ITuner> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ITuner> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::broadcastradio::V1_1::ITuner>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::broadcastradio::V1_1::ITuner::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_ITUNER_H
