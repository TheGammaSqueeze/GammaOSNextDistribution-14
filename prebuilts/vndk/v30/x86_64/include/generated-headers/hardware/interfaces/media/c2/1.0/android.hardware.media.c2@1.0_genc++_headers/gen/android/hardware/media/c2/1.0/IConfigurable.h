#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICONFIGURABLE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICONFIGURABLE_H

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
 * Generic configuration interface presented by all configurable Codec2 objects.
 *
 * This interface must be supported in all states of the owning object, and must
 * not change the state of the owning object.
 */
struct IConfigurable : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.c2@1.0::IConfigurable"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Returns the id of the object. This must be unique among all objects of
     * the same type hosted by the same store.
     *
     * @return id Id of the object.
     */
    virtual ::android::hardware::Return<uint32_t> getId() = 0;

    /**
     * Return callback for getName
     */
    using getName_cb = std::function<void(const ::android::hardware::hidl_string& name)>;
    /**
     * Returns the name of the object.
     *
     * This must match the name that was supplied during the creation of the
     * object.
     *
     * @return name Name of the object.
     */
    virtual ::android::hardware::Return<void> getName(getName_cb _hidl_cb) = 0;

    /**
     * Return callback for query
     */
    using query_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& params)>;
    /**
     * Queries a set of parameters from the object.
     *
     * Querying is performed at best effort: the object must query all supported
     * parameters and skip unsupported ones (which may include parameters that
     * could not be allocated). Any errors are communicated in the return value.
     *
     * If @p mayBlock is false, this method must not block. All parameter
     * queries that require blocking must be skipped.
     *
     * If @p mayBlock is true, a query may block, but the whole method call
     * has to complete in a timely manner, or `status = TIMED_OUT` is returned.
     *
     * If @p mayBlock is false, this method must not block. Otherwise, this
     * method is allowed to block for a certain period of time before completing
     * the operation. If the operation is not completed in a timely manner,
     * `status = TIMED_OUT` is returned.
     *
     * @note The order of C2Param objects in @p param does not depend on the
     *     order of C2Param structure indices in @p indices.
     *
     * \par For IComponent
     *
     * When the object type is @ref IComponent, this method must be supported in
     * any state except released. This call must not change the state nor the
     * internal configuration of the component.
     *
     * The blocking behavior of this method differs among states:
     *   - In the stopped state, this must be non-blocking. @p mayBlock is
     *     ignored. (The method operates as if @p mayBlock was false.)
     *   - In any of the running states, this method may block momentarily if
     *     @p mayBlock is true. However, if the call cannot be completed in a
     *     timely manner, `status = TIMED_OUT` is returned.
     *
     * @param indices List of C2Param structure indices to query.
     * @param mayBlock Whether this call may block or not.
     * @return status Status of the call, which may be
     *   - `OK`        - All parameters could be queried.
     *   - `BAD_INDEX` - All supported parameters could be queried, but some
     *                   parameters were not supported.
     *   - `NO_MEMORY` - Could not allocate memory for a supported parameter.
     *   - `BLOCKING`  - Querying some parameters requires blocking, but
     *                   @p mayBlock is false.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return params Flattened representation of C2Param objects.
     *
     * @sa Params.
     */
    virtual ::android::hardware::Return<void> query(const ::android::hardware::hidl_vec<uint32_t>& indices, bool mayBlock, query_cb _hidl_cb) = 0;

    /**
     * Return callback for config
     */
    using config_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::SettingResult>& failures, const ::android::hardware::hidl_vec<uint8_t>& outParams)>;
    /**
     * Sets a set of parameters for the object.
     *
     * Tuning is performed at best effort: the object must update all supported
     * configurations at best effort and skip unsupported parameters. Any errors
     * are communicated in the return value and in @p failures.
     *
     * A non-strict parameter update with an unsupported value shall cause an
     * update to the closest supported value. A strict parameter update with an
     * unsupported value shall be skipped and a failure shall be returned.
     *
     * If @p mayBlock is false, this method must not block. An update that
     * requires blocking shall be skipped and a failure shall be returned.
     *
     * If @p mayBlock is true, an update may block, but the whole method call
     * has to complete in a timely manner, or `status = TIMED_OUT` is returned.
     *
     * The final values for all parameters set are propagated back to the caller
     * in @p params.
     *
     * \par For IComponent
     *
     * When the object type is @ref IComponent, this method must be supported in
     * any state except released.
     *
     * The blocking behavior of this method differs among states:
     *   - In the stopped state, this must be non-blocking. @p mayBlock is
     *     ignored. (The method operates as if @p mayBlock was false.)
     *   - In any of the running states, this method may block momentarily if
     *     @p mayBlock is true. However, if the call cannot be completed in a
     *     timely manner, `status = TIMED_OUT` is returned.
     *
     * @note Parameter tuning @e does depend on the order of the tuning
     * parameters, e.g., some parameter update may enable some subsequent
     * parameter update.
     *
     * @param inParams Requested parameter updates.
     * @param mayBlock Whether this call may block or not.
     * @return status Status of the call, which may be
     *   - `OK`        - All parameters could be updated successfully.
     *   - `BAD_INDEX` - All supported parameters could be updated successfully,
     *                   but some parameters were not supported.
     *   - `NO_MEMORY` - Some supported parameters could not be updated
     *                   successfully because they contained unsupported values.
     *                   These are returned in @p failures.
     *   - `BLOCKING`  - Setting some parameters requires blocking, but
     *                   @p mayBlock is false.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return failures List of update failures.
     * @return outParams Flattened representation of configured parameters. The
     *     order of parameters in @p outParams is based on the order of
     *     requested updates in @p inParams.
     *
     * @sa SettingResult.
     */
    virtual ::android::hardware::Return<void> config(const ::android::hardware::hidl_vec<uint8_t>& inParams, bool mayBlock, config_cb _hidl_cb) = 0;

    /**
     * Return callback for querySupportedParams
     */
    using querySupportedParams_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::ParamDescriptor>& params)>;
    /**
     * Returns a list of supported parameters within a selected range of C2Param
     * structure indices.
     *
     * @param start The first index of the selected range.
     * @param count The length of the selected range.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `NO_MEMORY` - Not enough memory to complete this method.
     * @return params List of supported parameters in the selected range. This
     *     list may have fewer than @p count elements if some indices in the
     *     range are not supported.
     *
     * @sa ParamDescriptor.
     */
    virtual ::android::hardware::Return<void> querySupportedParams(uint32_t start, uint32_t count, querySupportedParams_cb _hidl_cb) = 0;

    /**
     * Return callback for querySupportedValues
     */
    using querySupportedValues_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult>& outFields)>;
    /**
     * Retrieves the supported values for the queried fields.
     *
     * The object must process all fields queried even if some queries fail.
     *
     * If @p mayBlock is false, this method must not block. Otherwise, this
     * method is allowed to block for a certain period of time before completing
     * the operation. If the operation cannot be completed in a timely manner,
     * `status = TIMED_OUT` is returned.
     *
     * \par For IComponent
     *
     * When the object type is @ref IComponent, this method must be supported in
     * any state except released.
     *
     * The blocking behavior of this method differs among states:
     *   - In the stopped state, this must be non-blocking. @p mayBlock is
     *     ignored. (The method operates as if @p mayBlock was false.)
     *   - In any of the running states, this method may block momentarily if
     *     @p mayBlock is true. However, if the call cannot be completed in a
     *     timely manner, `status = TIMED_OUT` is returned.
     *
     * @param inFields List of field queries.
     * @param mayBlock Whether this call may block or not.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `BLOCKING`  - Querying some parameters requires blocking, but
     *                   @p mayBlock is false.
     *   - `NO_MEMORY` - Not enough memory to complete this method.
     *   - `BAD_INDEX` - At least one field was not recognized as a component
     *                   field.
     *   - `BLOCKING`  - Querying some fields requires blocking, but @p mayblock
     *                   is false.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return outFields List of supported values and results for the
     *     supplied queries.
     *
     * @sa FieldSupportedValuesQuery, FieldSupportedValuesQueryResult.
     */
    virtual ::android::hardware::Return<void> querySupportedValues(const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery>& inFields, bool mayBlock, querySupportedValues_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>> castFrom(const ::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IConfigurable> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IConfigurable> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IConfigurable> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IConfigurable> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IConfigurable> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IConfigurable> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IConfigurable> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IConfigurable> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::c2::V1_0::IConfigurable::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICONFIGURABLE_H
