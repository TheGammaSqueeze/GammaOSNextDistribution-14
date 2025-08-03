#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_ISENSORS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_ISENSORS_H

#include <android/hardware/sensors/1.0/types.h>
#include <android/hardware/sensors/2.0/ISensorsCallback.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_0 {

struct ISensors : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.sensors@2.0::ISensors"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getSensorsList
     */
    using getSensorsList_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::sensors::V1_0::SensorInfo>& list)>;
    /**
     * Enumerate all available (static) sensors.
     *
     * The SensorInfo for each sensor returned by getSensorsList must be stable
     * from the initial call to getSensorsList after a device boot until the
     * entire system restarts. The SensorInfo for each sensor must not change
     * between subsequent calls to getSensorsList, even across restarts of the
     * HAL and its dependencies (for example, the sensor handle for a given
     * sensor must not change across HAL restarts).
     */
    virtual ::android::hardware::Return<void> getSensorsList(getSensorsList_cb _hidl_cb) = 0;

    /**
     * Place the module in a specific mode. The following modes are defined
     *
     *  SENSOR_HAL_NORMAL_MODE - Normal operation. Default state of the module.
     *
     *  SENSOR_HAL_DATA_INJECTION_MODE - Loopback mode.
     *    Data is injected for the supported sensors by the sensor service in
     *    this mode.
     *
     * @return OK on success
     *     BAD_VALUE if requested mode is not supported
     *     PERMISSION_DENIED if operation is not allowed
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> setOperationMode(::android::hardware::sensors::V1_0::OperationMode mode) = 0;

    /**
     * Activate/de-activate one sensor.
     *
     * After sensor de-activation, existing sensor events that have not
     * been written to the event queue must be abandoned immediately so that
     * subsequent activations do not get stale sensor events (events
     * that are generated prior to the latter activation).
     *
     * @param sensorHandle is the handle of the sensor to change.
     * @param enabled set to true to enable, or false to disable the sensor.
     * @return result OK on success, BAD_VALUE if sensorHandle is invalid.
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> activate(int32_t sensorHandle, bool enabled) = 0;

    /**
     * Initialize the Sensors HAL's Fast Message Queues (FMQ) and callback.
     *
     * The Fast Message Queues (FMQ) that are used to send data between the
     * framework and the HAL. The callback is used by the HAL to notify the
     * framework of asynchronous events, such as a dynamic sensor connection.
     *
     * The Event FMQ is used to transport sensor events from the HAL to the
     * framework. The Event FMQ is created using the eventQueueDescriptor.
     * Data may only be written to the Event FMQ. Data must not be read from
     * the Event FMQ since the framework is the only reader. Upon receiving
     * sensor events, the HAL writes the sensor events to the Event FMQ.
     *
     * Once the HAL is finished writing sensor events to the Event FMQ, the HAL
     * must notify the framework that sensor events are available to be read and
     * processed. This is accomplished by either:
     *     1) Calling the Event FMQ’s EventFlag::wake() function with
     * EventQueueFlagBits::READ_AND_PROCESS
     *     2) Setting the write notification in the Event FMQ’s writeBlocking()
     *        function to EventQueueFlagBits::READ_AND_PROCESS.
     *
     * If the Event FMQ’s writeBlocking() function is used, the read
     * notification must be set to EventQueueFlagBits::EVENTS_READ in order to
     * be notified and unblocked when the framework has successfully read events
     * from the Event FMQ.
     *
     * The Wake Lock FMQ is used by the framework to notify the HAL when it is
     * safe to release its wake_lock. When the framework receives WAKE_UP events
     * from the Event FMQ and the framework has acquired a wake_lock, the
     * framework must write the number of WAKE_UP events processed to the Wake
     * Lock FMQ. When the HAL reads the data from the Wake Lock FMQ, the HAL
     * decrements its current count of unprocessed WAKE_UP events and releases
     * its wake_lock if the current count of unprocessed WAKE_UP events is
     * zero. It is important to note that the HAL must acquire the wake lock and
     * update its internal state regarding the number of outstanding WAKE_UP
     * events _before_ posting the event to the Wake Lock FMQ, in order to avoid
     * a race condition that can lead to loss of wake lock synchronization with
     * the framework.
     *
     * The framework must use the WakeLockQueueFlagBits::DATA_WRITTEN value to
     * notify the HAL that data has been written to the Wake Lock FMQ and must
     * be read by HAL.
     *
     * The ISensorsCallback is used by the HAL to notify the framework of
     * asynchronous events, such as a dynamic sensor connection.
     *
     * The name of any wake_lock acquired by the Sensors HAL for WAKE_UP events
     * must begin with "SensorsHAL_WAKEUP".
     *
     * If WAKE_LOCK_TIMEOUT_SECONDS has elapsed since the most recent WAKE_UP
     * event was written to the Event FMQ without receiving a message on the
     * Wake Lock FMQ, then any held wake_lock for WAKE_UP events must be
     * released.
     *
     * If either the Event FMQ or the Wake Lock FMQ is already initialized when
     * initialize is invoked, then both existing FMQs must be discarded and the
     * new descriptors must be used to create new FMQs within the HAL. The
     * number of outstanding WAKE_UP events should also be reset to zero, and
     * any outstanding wake_locks held as a result of WAKE_UP events should be
     * released.
     *
     * All active sensor requests and direct channels must be closed and
     * properly cleaned up when initialize is called in order to ensure that the
     * HAL and framework's state is consistent (e.g. after a runtime restart).
     *
     * initialize must be thread safe and prevent concurrent calls
     * to initialize from simultaneously modifying state.
     *
     * @param eventQueueDescriptor Fast Message Queue descriptor that is used to
     *     create the Event FMQ which is where sensor events are written. The
     *     descriptor is obtained from the framework's FMQ that is used to read
     *     sensor events.
     * @param wakeLockDescriptor Fast Message Queue descriptor that is used to
     *     create the Wake Lock FMQ which is where wake_lock events are read
     *     from. The descriptor is obtained from the framework's FMQ that is
     *     used to write wake_lock events.
     * @param sensorsCallback sensors callback that receives asynchronous data
     *     from the Sensors HAL.
     * @return result OK on success; BAD_VALUE if descriptor is invalid (such
     *     as null)
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> initialize(const ::android::hardware::MQDescriptorSync<::android::hardware::sensors::V1_0::Event>& eventQueueDescriptor, const ::android::hardware::MQDescriptorSync<uint32_t>& wakeLockDescriptor, const ::android::sp<::android::hardware::sensors::V2_0::ISensorsCallback>& sensorsCallback) = 0;

    /**
     * Sets a sensor’s parameters, including sampling frequency and maximum
     * report latency. This function can be called while the sensor is
     * activated, in which case it must not cause any sensor measurements to
     * be lost: transitioning from one sampling rate to the other cannot cause
     * lost events, nor can transitioning from a high maximum report latency to
     * a low maximum report latency.
     *
     * @param sensorHandle handle of sensor to be changed.
     * @param samplingPeriodNs specifies sensor sample period in nanoseconds.
     * @param maxReportLatencyNs allowed delay time before an event is sampled
     *     to time of report.
     * @return result OK on success, BAD_VALUE if any parameters are invalid.
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> batch(int32_t sensorHandle, int64_t samplingPeriodNs, int64_t maxReportLatencyNs) = 0;

    /**
     * Trigger a flush of internal FIFO.
     *
     * Flush adds a FLUSH_COMPLETE metadata event to the end of the "batch mode"
     * FIFO for the specified sensor and flushes the FIFO.  If the FIFO is empty
     * or if the sensor doesn't support batching (FIFO size zero), return
     * SUCCESS and add a trivial FLUSH_COMPLETE event added to the event stream.
     * This applies to all sensors other than one-shot sensors. If the sensor
     * is a one-shot sensor, flush must return BAD_VALUE and not generate any
     * flush complete metadata.  If the sensor is not active at the time flush()
     * is called, flush() return BAD_VALUE.
     *
     * @param sensorHandle handle of sensor to be flushed.
     * @return result OK on success and BAD_VALUE if sensorHandle is invalid.
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> flush(int32_t sensorHandle) = 0;

    /**
     * Inject a single sensor event or push operation environment parameters to
     * device.
     *
     * When device is in NORMAL mode, this function is called to push operation
     * environment data to device. In this operation, Event is always of
     * SensorType::AdditionalInfo type. See operation evironment parameters
     * section in AdditionalInfoType.
     *
     * When device is in DATA_INJECTION mode, this function is also used for
     * injecting sensor events.
     *
     * Regardless of OperationMode, injected SensorType::ADDITIONAL_INFO
     * type events should not be routed back to the sensor event queue.
     *
     * @see AdditionalInfoType
     * @see OperationMode
     * @param event sensor event to be injected
     * @return result OK on success; PERMISSION_DENIED if operation is not
     *     allowed; INVALID_OPERATION, if this functionality is unsupported;
     *     BAD_VALUE if sensor event cannot be injected.
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> injectSensorData(const ::android::hardware::sensors::V1_0::Event& event) = 0;

    /**
     * Return callback for registerDirectChannel
     */
    using registerDirectChannel_cb = std::function<void(::android::hardware::sensors::V1_0::Result result, int32_t channelHandle)>;
    /**
     * Register direct report channel.
     *
     * Register a direct channel with supplied shared memory information. Upon
     * return, the sensor hardware is responsible for resetting the memory
     * content to initial value (depending on memory format settings).
     *
     * @param mem shared memory info data structure.
     * @return result OK on success; BAD_VALUE if shared memory information is
     *     not consistent; NO_MEMORY if shared memory cannot be used by sensor
     *     system; INVALID_OPERATION if functionality is not supported.
     * @return channelHandle a positive integer used for referencing registered
     *     direct channel (>0) in configureDirectReport and
     *     unregisterDirectChannel if result is OK, -1 otherwise.
     */
    virtual ::android::hardware::Return<void> registerDirectChannel(const ::android::hardware::sensors::V1_0::SharedMemInfo& mem, registerDirectChannel_cb _hidl_cb) = 0;

    /**
     * Unregister direct report channel.
     *
     * Unregister a direct channel previously registered using
     * registerDirectChannel, and remove all active sensor report configured in
     * still active sensor report configured in the direct channel.
     *
     * @param channelHandle handle of direct channel to be unregistered.
     * @return result OK if direct report is supported; INVALID_OPERATION
     *     otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> unregisterDirectChannel(int32_t channelHandle) = 0;

    /**
     * Return callback for configDirectReport
     */
    using configDirectReport_cb = std::function<void(::android::hardware::sensors::V1_0::Result result, int32_t reportToken)>;
    /**
     * Configure direct sensor event report in direct channel.
     *
     * This function start, modify rate or stop direct report of a sensor in a
     * certain direct channel.
     *
     * @param sensorHandle handle of sensor to be configured. When combined
     *     with STOP rate, sensorHandle can be -1 to denote all active sensors
     *     in the direct channel specified by channel Handle.
     * @param channelHandle handle of direct channel to be configured.
     * @param rate rate level, see RateLevel enum.
     * @return result OK on success; BAD_VALUE if parameter is invalid (such as
     *     rate level is not supported by sensor, channelHandle does not exist,
     *     etc); INVALID_OPERATION if functionality is not supported.
     * @return reportToken positive integer to identify multiple sensors of
     *     the same type in a single direct channel. Ignored if rate is STOP.
     *     See SharedMemFormat.
     */
    virtual ::android::hardware::Return<void> configDirectReport(int32_t sensorHandle, int32_t channelHandle, ::android::hardware::sensors::V1_0::RateLevel rate, configDirectReport_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::sensors::V2_0::ISensors>> castFrom(const ::android::sp<::android::hardware::sensors::V2_0::ISensors>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::sensors::V2_0::ISensors>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISensors> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISensors> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISensors> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISensors> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISensors> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISensors> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISensors> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISensors> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::sensors::V2_0::ISensors>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::sensors::V2_0::ISensors>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::sensors::V2_0::ISensors::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace sensors
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_ISENSORS_H
