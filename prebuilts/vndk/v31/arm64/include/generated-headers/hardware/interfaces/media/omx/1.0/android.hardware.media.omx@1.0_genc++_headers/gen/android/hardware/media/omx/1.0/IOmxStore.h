#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXSTORE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXSTORE_H

#include <android/hardware/media/omx/1.0/IOmx.h>
#include <android/hardware/media/omx/1.0/types.h>
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
namespace omx {
namespace V1_0 {

/**
 * Ref: frameworks/av/include/media/IOMX.h: IOMX
 *
 * There will be two instances of IOmxStore: "platform" and "vendor".
 *
 * The IOmxStore service provided by the platform must present "platform" as the
 * interface name.
 *
 * The IOmxStore service provided by the vendor must present "vendor" as the
 * instance name.
 */
struct IOmxStore : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.omx@1.0::IOmxStore"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct Attribute;
    struct NodeInfo;
    struct RoleInfo;

    /**
     * Attribute is a key-value pair of strings. The `value` member is generally
     * a stringified value of the following:
     *   enum<v1,v2,...,vn>:    v1 | v2 | ... | vn
     *   num:                   0 | [1-9][0-9]*
     *   string:                arbitrary string
     *   size:                  <num>x<num>
     *   ratio:                 <num>:<num>
     *   range<type>:           <type> | <type>-<type>
     *   list<type>:            <type> | <type>,<list<type>>
     */
    struct Attribute final {
        ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
        ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::Attribute, key) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::Attribute, value) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::media::omx::V1_0::IOmxStore::Attribute) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::media::omx::V1_0::IOmxStore::Attribute) == 8, "wrong alignment");

    /**
     * Service attribute
     *
     * Optional service attributes:
     *   key: 'max-video-encoder-input-buffers', value-type: num
     *   key: 'supports-multiple-secure-codecs', value-type: enum<0,1>
     *   key: 'supports-secure-with-non-secure-codec', value-type: enum<0,1>
     *
     * For keys with prefix 'supports-', the value of 0 means "no" (not
     * supported) while the value of 1 means "yes" (supported).
     */
    typedef ::android::hardware::media::omx::V1_0::IOmxStore::Attribute ServiceAttribute;

    /**
     * Node attribute
     *
     * Optional node attributes to describe supported values:
     *   key: 'bitrate-range', value-type: range<num>
     *   key: 'max-concurrent-instances', value-type: num
     *   key: 'max-supported-instances', value-type: num
     *
     * Optional node attributes for audio nodes to describe supported values:
     *   key: 'max-channel-count', value-type: num
     *   key: 'sample-rate-ranges', value-type: list<range<num>>
     *
     * Optional node attributes for video nodes to describe supported values:
     *   key: 'alignment', value-type: size
     *   key: 'block-aspect-ratio-range', value-type: range<ratio>
     *   key: 'block-count-range', value-type: range<num>
     *   key: 'block-size', value-type: size
     *   key: 'blocks-per-second-range', value-type: range<num>
     *   key: 'feature-can-swap-width-height', value-type: enum<0,1>
     *   key: 'frame-rate-range', value-type: range<num>
     *   key: 'pixel-aspect-ratio-range', value-type: range<ratio>
     *   key: 'size-range', value-type: range<size>
     *
     * Required node attributes for video nodes that are required by Android to
     * describe measured values for this device:
     *   key: 'measured-frame-rate-<width>x<height>-range',
     *     value-type: range<num>; where width: num, height: num
     *
     * Optional node attributes for decoders to describe supported values:
     *   key: 'feature-adaptive-playback', value: enum<0,1>
     *   key: 'feature-secure-playback', value: enum<0,1>
     *   key: 'feature-tunneled-playback', value: enum<0,1>
     *
     * Optional node attributes for video decoders to describe supported values:
     *   key: 'feature-partial-frame', value: enum<0,1>
     *
     * Optional node attributes for encoders to describe supported values:
     *   key: 'complexity-default', value-type: num
     *   key: 'complexity-range', value-type: range<num>
     *   key: 'feature-bitrate-modes', value-type: list<enum<VBR,CBR,CQ>>
     *   key: 'feature-intra-refresh', value-type: enum<0,1>
     *   key: 'quality-default', value-type: num
     *   key: 'quality-range', value-type: range<num>
     *   key: 'quality-scale', value-type: string
     *
     * For keys with prefix 'feature-' and value type enum<0,1>, the value of 0
     * means "optional", while the value of 1 means "required".
     */
    typedef ::android::hardware::media::omx::V1_0::IOmxStore::Attribute NodeAttribute;

    /**
     * Information for an IOmxNode node.
     */
    struct NodeInfo final {
        /**
         * Name of this node.
         *
         * `name` can be supplied to `IOmx::allocateNode` of a
         * corresponding `IOmx` instance to create the node.
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * Name of the `IOmx` instance that can create this node.
         *
         * To obtain the `IOmx` instance, call `getOmx(owner)`.
         */
        ::android::hardware::hidl_string owner __attribute__ ((aligned(8)));
        /**
         * List of node attributes.
         */
        ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::IOmxStore::Attribute> attributes __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo, owner) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo, attributes) == 32, "wrong offset");
    static_assert(sizeof(::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo) == 8, "wrong alignment");

    /**
     * Information about nodes provided for a supported node role
     */
    struct RoleInfo final {
        /**
         * Standard OMX node role.
         */
        ::android::hardware::hidl_string role __attribute__ ((aligned(8)));
        /**
         * Corresponding media type (as defined in MediaFormat.MIMETYPE_*
         * constants for types required by Android).
         */
        ::android::hardware::hidl_string type __attribute__ ((aligned(8)));
        /**
         * Whether this role is for an encoder or a decoder.
         */
        bool isEncoder __attribute__ ((aligned(1)));
        /**
         * Whether to prefer platform nodes for this role.
         */
        bool preferPlatformNodes __attribute__ ((aligned(1)));
        /**
         * List of nodes that support this role, ordered by preference.
         */
        ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo> nodes __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo, role) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo, type) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo, isEncoder) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo, preferPlatformNodes) == 33, "wrong offset");
    static_assert(offsetof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo, nodes) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for listServiceAttributes
     */
    using listServiceAttributes_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::IOmxStore::Attribute>& attributes)>;
    /**
     * List attributes that are service-specific (not node-specific).
     *
     * @return attributes The list of `Attribute`s that are specific to this
     *                    service.
     */
    virtual ::android::hardware::Return<void> listServiceAttributes(listServiceAttributes_cb _hidl_cb) = 0;

    /**
     * Return callback for getNodePrefix
     */
    using getNodePrefix_cb = std::function<void(const ::android::hardware::hidl_string& prefix)>;
    /**
     * Return the prefix of names of supported nodes.
     *
     * @return prefix The prefix of the names of all nodes supported by this
     *                service.
     */
    virtual ::android::hardware::Return<void> getNodePrefix(getNodePrefix_cb _hidl_cb) = 0;

    /**
     * Return callback for listRoles
     */
    using listRoles_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo>& roleList)>;
    /**
     * List roles of supported nodes.
     *
     * The name of each node inside `NodeInfo` must start with the prefix
     * returned by `getNodePrefix()`.
     *
     * @return roleList The list of `RoleInfo`s.
     *
     * @see RoleInfo
     */
    virtual ::android::hardware::Return<void> listRoles(listRoles_cb _hidl_cb) = 0;

    /**
     * Obtain an `IOmx` instance with a specified name.
     *
     * @param name The name of the instance.
     * @return omx The `IOmx` interface associated with `name`. This must be
     * null if the name is not found.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::media::omx::V1_0::IOmx>> getOmx(const ::android::hardware::hidl_string& name) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::media::omx::V1_0::IOmxStore>> castFrom(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxStore>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::omx::V1_0::IOmxStore>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IOmxStore> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IOmxStore> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IOmxStore> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IOmxStore> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IOmxStore> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IOmxStore> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IOmxStore> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IOmxStore> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& o);
static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& rhs);
static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& rhs);

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& o);
static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& rhs);
static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& rhs);

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& o);
static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& rhs);
static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxStore>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::Attribute& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .owner = ";
    os += ::android::hardware::toString(o.owner);
    os += ", .attributes = ";
    os += ::android::hardware::toString(o.attributes);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.owner != rhs.owner) {
        return false;
    }
    if (lhs.attributes != rhs.attributes) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::NodeInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".role = ";
    os += ::android::hardware::toString(o.role);
    os += ", .type = ";
    os += ::android::hardware::toString(o.type);
    os += ", .isEncoder = ";
    os += ::android::hardware::toString(o.isEncoder);
    os += ", .preferPlatformNodes = ";
    os += ::android::hardware::toString(o.preferPlatformNodes);
    os += ", .nodes = ";
    os += ::android::hardware::toString(o.nodes);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& rhs) {
    if (lhs.role != rhs.role) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.isEncoder != rhs.isEncoder) {
        return false;
    }
    if (lhs.preferPlatformNodes != rhs.preferPlatformNodes) {
        return false;
    }
    if (lhs.nodes != rhs.nodes) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& lhs, const ::android::hardware::media::omx::V1_0::IOmxStore::RoleInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxStore>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::omx::V1_0::IOmxStore::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXSTORE_H
