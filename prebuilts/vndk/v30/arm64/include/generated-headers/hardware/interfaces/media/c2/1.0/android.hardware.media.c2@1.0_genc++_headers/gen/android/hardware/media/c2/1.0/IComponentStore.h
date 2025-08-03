#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTSTORE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTSTORE_H

#include <android/hardware/media/bufferpool/2.0/IClientManager.h>
#include <android/hardware/media/c2/1.0/IComponent.h>
#include <android/hardware/media/c2/1.0/IComponentInterface.h>
#include <android/hardware/media/c2/1.0/IComponentListener.h>
#include <android/hardware/media/c2/1.0/IConfigurable.h>
#include <android/hardware/media/c2/1.0/IInputSurface.h>
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
 * Entry point for Codec2 HAL.
 *
 * All methods in `IComponentStore` must not block. If a method call cannot be
 * completed in a timely manner, it must return `TIMED_OUT` in the return
 * status. The only exceptions are getPoolClientManager() and getConfigurable(),
 * which must always return immediately.
 */
struct IComponentStore : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.c2@1.0::IComponentStore"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct ComponentTraits;

    /**
     * Component traits.
     */
    struct ComponentTraits final {
        // Forward declaration for forward reference support:
        enum class Domain : uint32_t;
        enum class Kind : uint32_t;

        enum class Domain : uint32_t {
            OTHER = 0u,
            VIDEO = 1u /* ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain.OTHER implicitly + 1 */,
            AUDIO = 2u /* ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain.VIDEO implicitly + 1 */,
            IMAGE = 3u /* ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain.AUDIO implicitly + 1 */,
        };

        enum class Kind : uint32_t {
            OTHER = 0u,
            DECODER = 1u /* ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind.OTHER implicitly + 1 */,
            ENCODER = 2u /* ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind.DECODER implicitly + 1 */,
        };

        /**
         * Name of the component. This must be unique for each component.
         *
         * This name is use to identify the component to create in
         * createComponent() and createComponentInterface().
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * Component domain.
         */
        ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain domain __attribute__ ((aligned(4)));
        /**
         * Component kind.
         */
        ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind kind __attribute__ ((aligned(4)));
        /**
         * Rank used by `MediaCodecList` to determine component ordering. Lower
         * value means higher priority.
         */
        uint32_t rank __attribute__ ((aligned(4)));
        /**
         * MIME type.
         */
        ::android::hardware::hidl_string mediaType __attribute__ ((aligned(8)));
        /**
         * Aliases for component name for backward compatibility.
         *
         * Multiple components can have the same alias (but not the same
         * component name) as long as their media types differ.
         */
        ::android::hardware::hidl_vec<::android::hardware::hidl_string> aliases __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, domain) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, kind) == 20, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, rank) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, mediaType) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits, aliases) == 48, "wrong offset");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits) == 64, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for createComponent
     */
    using createComponent_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::sp<::android::hardware::media::c2::V1_0::IComponent>& comp)>;
    /**
     * Creates a component by name.
     *
     * @param name Name of the component to create. This must match one of the
     *     names returned by listComponents().
     * @param listener Callback receiver.
     * @param pool `IClientManager` object of the BufferPool in the client
     *     process. This may be null if the client does not own a BufferPool.
     * @return status Status of the call, which may be
     *   - `OK`        - The component was created successfully.
     *   - `NOT_FOUND` - There is no component with the given name.
     *   - `NO_MEMORY` - Not enough memory to create the component.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return comp The created component if @p status is `OK`.
     *
     * @sa IComponentListener.
     */
    virtual ::android::hardware::Return<void> createComponent(const ::android::hardware::hidl_string& name, const ::android::sp<::android::hardware::media::c2::V1_0::IComponentListener>& listener, const ::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>& pool, createComponent_cb _hidl_cb) = 0;

    /**
     * Return callback for createInterface
     */
    using createInterface_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::sp<::android::hardware::media::c2::V1_0::IComponentInterface>& compIntf)>;
    /**
     * Creates a component interface by name.
     *
     * @param name Name of the component interface to create. This should match
     *     one of the names returned by listComponents().
     * @return status Status of the call, which may be
     *   - `OK`        - The component interface was created successfully.
     *   - `NOT_FOUND` - There is no component interface with the given name.
     *   - `NO_MEMORY` - Not enough memory to create the component interface.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return compIntf The created component interface if @p status is `OK`.
     */
    virtual ::android::hardware::Return<void> createInterface(const ::android::hardware::hidl_string& name, createInterface_cb _hidl_cb) = 0;

    /**
     * Return callback for listComponents
     */
    using listComponents_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits>& traits)>;
    /**
     * Returns the list of components supported by this component store.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The operation was successful.
     *   - `NO_MEMORY` - Not enough memory to complete this method.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return traits List of component traits for all components supported by
     *     this store (in no particular order).
     */
    virtual ::android::hardware::Return<void> listComponents(listComponents_cb _hidl_cb) = 0;

    /**
     * Return callback for createInputSurface
     */
    using createInputSurface_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurface>& surface)>;
    /**
     * Creates a persistent input surface that can be used as an input surface
     * for any IComponent instance
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The operation was successful.
     *   - `NO_MEMORY` - Not enough memory to complete this method.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return surface A persistent input surface. This may be null to indicate
     *     an error.
     */
    virtual ::android::hardware::Return<void> createInputSurface(createInputSurface_cb _hidl_cb) = 0;

    /**
     * Return callback for getStructDescriptors
     */
    using getStructDescriptors_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::StructDescriptor>& structs)>;
    /**
     * Returns a list of `StructDescriptor` objects for a set of requested
     * C2Param structure indices that this store is aware of.
     *
     * This operation must be performed at best effort, e.g. the component
     * store must simply ignore all struct indices that it is not aware of.
     *
     * @param indices Indices of C2Param structures to describe.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `NOT_FOUND` - Some indices were not known.
     *   - `NO_MEMORY` - Not enough memory to complete this method.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return structs List of `StructDescriptor` objects.
     */
    virtual ::android::hardware::Return<void> getStructDescriptors(const ::android::hardware::hidl_vec<uint32_t>& indices, getStructDescriptors_cb _hidl_cb) = 0;

    /**
     * Copies the contents of @p src into @p dst without changing the format of
     * @p dst.
     *
     * @param src Source buffer.
     * @param dst Destination buffer.
     * @return status Status of the call, which may be
     *   - `OK`        - The copy is successful.
     *   - `CANNOT_DO` - @p src and @p dst are not compatible.
     *   - `REFUSED`   - No permission to copy.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> copyBuffer(const ::android::hardware::media::c2::V1_0::Buffer& src, const ::android::hardware::media::c2::V1_0::Buffer& dst) = 0;

    /**
     * Returns the `IClientManager` object for the component's BufferPool.
     *
     * @return pool If the component store supports receiving buffers via
     *     BufferPool API, @p pool must be a valid `IClientManager` instance.
     *     Otherwise, @p pool must be null.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::media::bufferpool::V2_0::IClientManager>> getPoolClientManager() = 0;

    /**
     * Returns the @ref IConfigurable instance associated to this component
     * store.
     *
     * @return configurable `IConfigurable` instance. This must not be null.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>> getConfigurable() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentStore>> castFrom(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentStore>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentStore>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IComponentStore> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponentStore> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponentStore> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponentStore> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IComponentStore> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponentStore> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponentStore> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponentStore> getService(bool getStub) { return getService("default", getStub); }
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

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentStore>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::OTHER) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::OTHER;
    }
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::VIDEO) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::VIDEO)) {
        os += (first ? "" : " | ");
        os += "VIDEO";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::VIDEO;
    }
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::AUDIO) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::AUDIO)) {
        os += (first ? "" : " | ");
        os += "AUDIO";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::AUDIO;
    }
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::IMAGE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::IMAGE)) {
        os += (first ? "" : " | ");
        os += "IMAGE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::IMAGE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::OTHER) {
        return "OTHER";
    }
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::VIDEO) {
        return "VIDEO";
    }
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::AUDIO) {
        return "AUDIO";
    }
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::IMAGE) {
        return "IMAGE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::OTHER) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::OTHER;
    }
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::DECODER) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::DECODER)) {
        os += (first ? "" : " | ");
        os += "DECODER";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::DECODER;
    }
    if ((o & ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::ENCODER) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::ENCODER)) {
        os += (first ? "" : " | ");
        os += "ENCODER";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::ENCODER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::OTHER) {
        return "OTHER";
    }
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::DECODER) {
        return "DECODER";
    }
    if (o == ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::ENCODER) {
        return "ENCODER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .domain = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.domain);
    os += ", .kind = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.kind);
    os += ", .rank = ";
    os += ::android::hardware::toString(o.rank);
    os += ", .mediaType = ";
    os += ::android::hardware::toString(o.mediaType);
    os += ", .aliases = ";
    os += ::android::hardware::toString(o.aliases);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.domain != rhs.domain) {
        return false;
    }
    if (lhs.kind != rhs.kind) {
        return false;
    }
    if (lhs.rank != rhs.rank) {
        return false;
    }
    if (lhs.mediaType != rhs.mediaType) {
        return false;
    }
    if (lhs.aliases != rhs.aliases) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& lhs, const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponentStore>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::c2::V1_0::IComponentStore::descriptor;
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

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain, 4> hidl_enum_values<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain> = {
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::OTHER,
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::VIDEO,
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::AUDIO,
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Domain::IMAGE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind, 3> hidl_enum_values<::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind> = {
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::OTHER,
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::DECODER,
    ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits::Kind::ENCODER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENTSTORE_H
