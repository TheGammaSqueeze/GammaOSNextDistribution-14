#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_ICANCONTROLLER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_ICANCONTROLLER_H

#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {

/**
 * Represents a CAN controller that's capable of configuring CAN bus interfaces.
 *
 * The goal of this service is to configure CAN interfaces and bring up HIDL
 * server instances of ICanBus for each one that's up.
 *
 * Providing an ICanController interface to configure CAN buses is optional.
 * A system can elect to publish only ICanBus if the hardware is hardcoded
 * for a specific application.
 */
struct ICanController : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.automotive.can@1.0::ICanController"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class InterfaceType : uint8_t;
    enum class Result : uint8_t;
    struct BusConfig;

    /**
     * Type of an interface, an equivalent to BusConfig::InterfaceId
     * union discriminator. Defines a number of specific standard hardware
     * families and a generic catch-all type of {@see INDEXED}.
     */
    enum class InterfaceType : uint8_t {
        /**
         * Virtual SocketCAN interface.
         */
        VIRTUAL = 0,
        /**
         * Native SocketCAN interface.
         */
        SOCKETCAN = 1 /* ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType.VIRTUAL implicitly + 1 */,
        /**
         * Serial line CAN interface.
         */
        SLCAN = 2 /* ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType.SOCKETCAN implicitly + 1 */,
        /**
         * Proprietary, device-specific interface.
         */
        INDEXED = 3 /* ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType.SLCAN implicitly + 1 */,
    };

    enum class Result : uint8_t {
        OK = 0,
        /**
         * General error class, if others are not applicable.
         */
        UNKNOWN_ERROR = 1 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.OK implicitly + 1 */,
        /**
         * Up request was called out of order (i.e. trying to up the
         * interface twice).
         */
        INVALID_STATE = 2 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.UNKNOWN_ERROR implicitly + 1 */,
        /**
         * Interface type is not supported.
         */
        NOT_SUPPORTED = 3 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.INVALID_STATE implicitly + 1 */,
        /**
         * Provided interface ID (index, name, device path) doesn't exist or
         * there is no device with a given serial number.
         */
        BAD_INTERFACE_ID = 4 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.NOT_SUPPORTED implicitly + 1 */,
        /**
         * Provided bit rate is not supported by the hardware.
         */
        BAD_BITRATE = 5 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.BAD_INTERFACE_ID implicitly + 1 */,
        /**
         * Provided service name ({@see BusConfig#name}) either has invalid
         * format or is not listed in device manifest file.
         */
        BAD_SERVICE_NAME = 6 /* ::android::hardware::automotive::can::V1_0::ICanController::Result.BAD_BITRATE implicitly + 1 */,
    };

    /**
     * Configuration of the (physical or virtual) CAN bus.
     *
     * ISO TP and CAN FD are currently not supported.
     */
    struct BusConfig final {
        // Forward declaration for forward reference support:
        struct InterfaceId;

        struct InterfaceId final {
            // Forward declaration for forward reference support:
            struct Virtual;
            struct Socketcan;
            struct Slcan;
            struct Indexed;

            struct Virtual final {
                /**
                 * Interface name, such as vcan0. If the interface doesn't
                 * exist, HAL server must create it.
                 */
                ::android::hardware::hidl_string ifname __attribute__ ((aligned(8)));
            };

            static_assert(offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual, ifname) == 0, "wrong offset");
            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual) == 16, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual) == 8, "wrong alignment");

            struct Socketcan final {
                enum class hidl_discriminator : uint8_t {
                    /**
                     * Interface name, such as can0.
                     */
                    ifname = 0,  // ::android::hardware::hidl_string
                    /**
                     * Alternatively to providing {@see ifname}, one may provide a
                     * list of interface serial number suffixes. If there happens to
                     * be a device (like USB2CAN) with a matching serial number
                     * suffix, the HAL service will have to select it.
                     *
                     * Client may utilize this in two ways: by matching against the
                     * entire serial number, or the last few characters (usually
                     * one). The former is better for small-scale test deployments
                     * (with just a handful of vehicles), the latter is good for
                     * larger scale (where a small suffix list may support large
                     * test fleet).
                     */
                    serialno = 1,  // ::android::hardware::hidl_vec<::android::hardware::hidl_string>
                };

                Socketcan();
                ~Socketcan();
                Socketcan(Socketcan&&);
                Socketcan(const Socketcan&);
                Socketcan& operator=(Socketcan&&);
                Socketcan& operator=(const Socketcan&);

                void ifname(const ::android::hardware::hidl_string&);
                void ifname(::android::hardware::hidl_string&&);
                ::android::hardware::hidl_string& ifname();
                const ::android::hardware::hidl_string& ifname() const;

                void serialno(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>&);
                void serialno(::android::hardware::hidl_vec<::android::hardware::hidl_string>&&);
                ::android::hardware::hidl_vec<::android::hardware::hidl_string>& serialno();
                const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& serialno() const;

                // Utility methods
                hidl_discriminator getDiscriminator() const;

                constexpr size_t hidl_getUnionOffset() const {
                    return offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan, hidl_u);
                }

            private:
                void hidl_destructUnion();

                hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
                union hidl_union final {
                    ::android::hardware::hidl_string ifname __attribute__ ((aligned(8)));
                    ::android::hardware::hidl_vec<::android::hardware::hidl_string> serialno __attribute__ ((aligned(8)));

                    hidl_union();
                    ~hidl_union();
                } hidl_u;

                static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_union) == 16, "wrong size");
                static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_union) == 8, "wrong alignment");
                static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator) == 1, "wrong size");
                static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator) == 1, "wrong alignment");
            };

            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan) == 24, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan) == 8, "wrong alignment");

            struct Slcan final {
                enum class hidl_discriminator : uint8_t {
                    /**
                     * Path to a device, such as /dev/ttyUSB0.
                     */
                    ttyname = 0,  // ::android::hardware::hidl_string
                    /**
                     * List of interface serial number suffixes.
                     * {@see Socketcan::serialno}
                     */
                    serialno = 1,  // ::android::hardware::hidl_vec<::android::hardware::hidl_string>
                };

                Slcan();
                ~Slcan();
                Slcan(Slcan&&);
                Slcan(const Slcan&);
                Slcan& operator=(Slcan&&);
                Slcan& operator=(const Slcan&);

                void ttyname(const ::android::hardware::hidl_string&);
                void ttyname(::android::hardware::hidl_string&&);
                ::android::hardware::hidl_string& ttyname();
                const ::android::hardware::hidl_string& ttyname() const;

                void serialno(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>&);
                void serialno(::android::hardware::hidl_vec<::android::hardware::hidl_string>&&);
                ::android::hardware::hidl_vec<::android::hardware::hidl_string>& serialno();
                const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& serialno() const;

                // Utility methods
                hidl_discriminator getDiscriminator() const;

                constexpr size_t hidl_getUnionOffset() const {
                    return offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan, hidl_u);
                }

            private:
                void hidl_destructUnion();

                hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
                union hidl_union final {
                    ::android::hardware::hidl_string ttyname __attribute__ ((aligned(8)));
                    ::android::hardware::hidl_vec<::android::hardware::hidl_string> serialno __attribute__ ((aligned(8)));

                    hidl_union();
                    ~hidl_union();
                } hidl_u;

                static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_union) == 16, "wrong size");
                static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_union) == 8, "wrong alignment");
                static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator) == 1, "wrong size");
                static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator) == 1, "wrong alignment");
            };

            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan) == 24, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan) == 8, "wrong alignment");

            struct Indexed final {
                /**
                 * Interface number, 0-based.
                 */
                uint8_t index __attribute__ ((aligned(1)));
            };

            static_assert(offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed, index) == 0, "wrong offset");
            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed) == 1, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed) == 1, "wrong alignment");

            enum class hidl_discriminator : uint8_t {
                /**
                 * Virtual SocketCAN interface.
                 */
                virtualif = 0,  // ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual
                /**
                 * Native SocketCAN interface.
                 */
                socketcan = 1,  // ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan
                /**
                 * Serial line CAN interface.
                 */
                slcan = 2,  // ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan
                /**
                 * Proprietary, device-specific interface.
                 *
                 * Non-SocketCAN interfaces should use this variant.
                 */
                indexed = 3,  // ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed
            };

            InterfaceId();
            ~InterfaceId();
            InterfaceId(InterfaceId&&);
            InterfaceId(const InterfaceId&);
            InterfaceId& operator=(InterfaceId&&);
            InterfaceId& operator=(const InterfaceId&);

            void virtualif(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual&);
            void virtualif(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual&&);
            ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& virtualif();
            const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& virtualif() const;

            void socketcan(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan&);
            void socketcan(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan&&);
            ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& socketcan();
            const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& socketcan() const;

            void slcan(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan&);
            void slcan(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan&&);
            ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& slcan();
            const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& slcan() const;

            void indexed(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed&);
            void indexed(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed&&);
            ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& indexed();
            const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& indexed() const;

            // Utility methods
            hidl_discriminator getDiscriminator() const;

            constexpr size_t hidl_getUnionOffset() const {
                return offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId, hidl_u);
            }

        private:
            void hidl_destructUnion();

            hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
            union hidl_union final {
                ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual virtualif __attribute__ ((aligned(8)));
                ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan socketcan __attribute__ ((aligned(8)));
                ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan slcan __attribute__ ((aligned(8)));
                ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed indexed __attribute__ ((aligned(1)));

                hidl_union();
                ~hidl_union();
            } hidl_u;

            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_union) == 24, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_union) == 8, "wrong alignment");
            static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator) == 1, "wrong size");
            static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator) == 1, "wrong alignment");
        };

        static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId) == 32, "wrong size");
        static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId) == 8, "wrong alignment");

        /**
         * Name under which ICanBus HIDL service should be published.
         *
         * It must consist of only alphanumeric characters and underscore
         * (a-z, A-Z, 0-9, '_'), at least 1 and at most 32 characters long.
         *
         * This field is *not* meant to distinguish between hardware interfaces
         * nor preselect parameters like bitrate. The only intended side-effect
         * of changing it should be a different ICanBus HIDL service name and
         * the HIDL service should make no assumptions on its contents.
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * Hardware interface configuration.
         *
         * This union's discriminator has an equivalent enum
         * {@see InterfaceType} to express compatibility via
         * getSupportedInterfaceTypes().
         */
        ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId interfaceId __attribute__ ((aligned(8)));
        /**
         * Bit rate for CAN communication.
         *
         * Typical bit rates are: 100000, 125000, 250000, 500000.
         *
         * For {@see interfaceId#virtual} and pre-configured
         * {@see interfaceId#indexed} interfaces this value is ignored.
         */
        uint32_t bitrate __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig, interfaceId) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig, bitrate) == 48, "wrong offset");
    static_assert(sizeof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::automotive::can::V1_0::ICanController::BusConfig) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getSupportedInterfaceTypes
     */
    using getSupportedInterfaceTypes_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::can::V1_0::ICanController::InterfaceType>& iftypes)>;
    /**
     * Fetches the list of interface types supported by this HAL server.
     *
     * @return iftypes The list of supported interface types.
     */
    virtual ::android::hardware::Return<void> getSupportedInterfaceTypes(getSupportedInterfaceTypes_cb _hidl_cb) = 0;

    /**
     * Bring up the CAN interface and publish ICanBus server instance.
     *
     * @param config Configuration of the CAN interface.
     * @return result OK if the operation succeeded; error code otherwise.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::can::V1_0::ICanController::Result> upInterface(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& config) = 0;

    /**
     * Unpublish ICanBus server instance and bring down the CAN interface.
     *
     * In case of failure, at least the ICanBus server instance must be
     * unpublished and resources freed on best-effort basis.
     *
     * @param name Name of the interface (@see BusConfig#name} to
     * bring down.
     * @return success true in case of success, false otherwise.
     */
    virtual ::android::hardware::Return<bool> downInterface(const ::android::hardware::hidl_string& name) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::can::V1_0::ICanController>> castFrom(const ::android::sp<::android::hardware::automotive::can::V1_0::ICanController>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::can::V1_0::ICanController>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICanController> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICanController> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICanController> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICanController> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICanController> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICanController> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICanController> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICanController> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType o);
static inline void PrintTo(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType lhs, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType lhs, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::automotive::can::V1_0::ICanController::Result o);
static inline void PrintTo(::android::hardware::automotive::can::V1_0::ICanController::Result o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ICanController::Result lhs, const ::android::hardware::automotive::can::V1_0::ICanController::Result rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ICanController::Result rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ICanController::Result lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ICanController::Result lhs, const ::android::hardware::automotive::can::V1_0::ICanController::Result rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ICanController::Result rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ICanController::Result lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ICanController::Result e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ICanController::Result e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& rhs);

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& rhs);

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& rhs);

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& rhs);

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& rhs);

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::automotive::can::V1_0::ICanController>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::automotive::can::V1_0::ICanController::InterfaceType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::can::V1_0::ICanController::InterfaceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::VIRTUAL) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::VIRTUAL)) {
        os += (first ? "" : " | ");
        os += "VIRTUAL";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::VIRTUAL;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SOCKETCAN) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SOCKETCAN)) {
        os += (first ? "" : " | ");
        os += "SOCKETCAN";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SOCKETCAN;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SLCAN) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SLCAN)) {
        os += (first ? "" : " | ");
        os += "SLCAN";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SLCAN;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::INDEXED) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::INDEXED)) {
        os += (first ? "" : " | ");
        os += "INDEXED";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::INDEXED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::VIRTUAL) {
        return "VIRTUAL";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SOCKETCAN) {
        return "SOCKETCAN";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SLCAN) {
        return "SLCAN";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::INDEXED) {
        return "INDEXED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::can::V1_0::ICanController::InterfaceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::can::V1_0::ICanController::Result>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::can::V1_0::ICanController::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::OK) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::OK;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::UNKNOWN_ERROR) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::UNKNOWN_ERROR;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::INVALID_STATE) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::NOT_SUPPORTED) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_INTERFACE_ID) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_INTERFACE_ID)) {
        os += (first ? "" : " | ");
        os += "BAD_INTERFACE_ID";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_INTERFACE_ID;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_BITRATE) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_BITRATE)) {
        os += (first ? "" : " | ");
        os += "BAD_BITRATE";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_BITRATE;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_SERVICE_NAME) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_SERVICE_NAME)) {
        os += (first ? "" : " | ");
        os += "BAD_SERVICE_NAME";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_SERVICE_NAME;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::can::V1_0::ICanController::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_INTERFACE_ID) {
        return "BAD_INTERFACE_ID";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_BITRATE) {
        return "BAD_BITRATE";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_SERVICE_NAME) {
        return "BAD_SERVICE_NAME";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::can::V1_0::ICanController::Result o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ifname = ";
    os += ::android::hardware::toString(o.ifname);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& rhs) {
    if (lhs.ifname != rhs.ifname) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Virtual& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator::ifname: {
            os += ".ifname = ";
            os += toString(o.ifname());
            break;
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator::serialno: {
            os += ".serialno = ";
            os += toString(o.serialno());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator::ifname: {
            return (lhs.ifname() == rhs.ifname());
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan::hidl_discriminator::serialno: {
            return (lhs.serialno() == rhs.serialno());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Socketcan& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator::ttyname: {
            os += ".ttyname = ";
            os += toString(o.ttyname());
            break;
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator::serialno: {
            os += ".serialno = ";
            os += toString(o.serialno());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator::ttyname: {
            return (lhs.ttyname() == rhs.ttyname());
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan::hidl_discriminator::serialno: {
            return (lhs.serialno() == rhs.serialno());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Slcan& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::Indexed& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::virtualif: {
            os += ".virtualif = ";
            os += toString(o.virtualif());
            break;
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::socketcan: {
            os += ".socketcan = ";
            os += toString(o.socketcan());
            break;
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::slcan: {
            os += ".slcan = ";
            os += toString(o.slcan());
            break;
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::indexed: {
            os += ".indexed = ";
            os += toString(o.indexed());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::virtualif: {
            return (lhs.virtualif() == rhs.virtualif());
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::socketcan: {
            return (lhs.socketcan() == rhs.socketcan());
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::slcan: {
            return (lhs.slcan() == rhs.slcan());
        }
        case ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId::hidl_discriminator::indexed: {
            return (lhs.indexed() == rhs.indexed());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig::InterfaceId& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .interfaceId = ";
    os += ::android::hardware::automotive::can::V1_0::toString(o.interfaceId);
    os += ", .bitrate = ";
    os += ::android::hardware::toString(o.bitrate);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.interfaceId != rhs.interfaceId) {
        return false;
    }
    if (lhs.bitrate != rhs.bitrate) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& lhs, const ::android::hardware::automotive::can::V1_0::ICanController::BusConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::automotive::can::V1_0::ICanController>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::automotive::can::V1_0::ICanController::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace can
}  // namespace automotive
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
template<> inline constexpr std::array<::android::hardware::automotive::can::V1_0::ICanController::InterfaceType, 4> hidl_enum_values<::android::hardware::automotive::can::V1_0::ICanController::InterfaceType> = {
    ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::VIRTUAL,
    ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SOCKETCAN,
    ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::SLCAN,
    ::android::hardware::automotive::can::V1_0::ICanController::InterfaceType::INDEXED,
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
template<> inline constexpr std::array<::android::hardware::automotive::can::V1_0::ICanController::Result, 7> hidl_enum_values<::android::hardware::automotive::can::V1_0::ICanController::Result> = {
    ::android::hardware::automotive::can::V1_0::ICanController::Result::OK,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::UNKNOWN_ERROR,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::INVALID_STATE,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::NOT_SUPPORTED,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_INTERFACE_ID,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_BITRATE,
    ::android::hardware::automotive::can::V1_0::ICanController::Result::BAD_SERVICE_NAME,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_ICANCONTROLLER_H
