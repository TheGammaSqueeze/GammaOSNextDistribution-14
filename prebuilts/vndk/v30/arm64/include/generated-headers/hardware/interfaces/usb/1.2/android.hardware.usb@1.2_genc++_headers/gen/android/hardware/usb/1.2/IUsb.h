#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_IUSB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_IUSB_H

#include <android/hardware/usb/1.1/IUsb.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_2 {

/**
 * The setCallback function in V1_0 is used to register the V1_2
 * IUsbCallback object as well. The implementation can use the
 * castFrom method to cast the IUsbCallback object.
 */
struct IUsb : public ::android::hardware::usb::V1_1::IUsb {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.usb@1.2::IUsb"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * This function is used to change the port role of a specific port.
     * For example, when PD_SWAP or PR_SWAP is supported.
     * This is function is asynchronous. The status of the role switch
     * will be informed through IUsbCallback object's notifyPortStatusChange
     * method.
     *
     * @param portName name of the port for which the role has to be changed
     * @param role the new port role.
     */
    virtual ::android::hardware::Return<void> switchRole(const ::android::hardware::hidl_string& portName, const ::android::hardware::usb::V1_0::PortRole& role) = 0;

    /**
     * This function is used to register a callback function which is
     * called by the HAL whenever there is a change in the port state.
     * i.e. DATA_ROLE, POWER_ROLE or MODE.
     *
     * Also the same callback object would be called to inform the caller
     * of the roleSwitch status.
     *
     * @param callback IUsbCallback object used to convey status to the
     * userspace.
     */
    virtual ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::usb::V1_0::IUsbCallback>& callback) = 0;

    /**
     * This functions is used to request the hal for the current status
     * status of the Type-C ports. This method is async/oneway. The result of the
     * query would be sent through the IUsbCallback object's notifyRoleSwitchStatus
     * to the caller. This api would would let the caller know of the number
     * of type-c ports that are present and their connection status through the
     * PortStatus type.
     */
    virtual ::android::hardware::Return<void> queryPortStatus() = 0;

    /**
     * When supportsEnableContaminantPresenceDetection is true,
     * enableContaminantPresenceDetection enables/disables contaminant
     * presence detection algorithm. Calling enableContaminantPresenceDetection
     * when supportsEnableContaminantPresenceDetection is false does
     * not have any effect.
     * Change in contantaminant presence status should notify should
     * be notified to the client via notifyPortStatusChange_1_2 through
     * PortStatus.
     *
     * @param portName name of the port.
     * @param enable true Enable contaminant presence detection algorithm.
     *               false Disable contaminant presence detection algorithm.
     */
    virtual ::android::hardware::Return<void> enableContaminantPresenceDetection(const ::android::hardware::hidl_string& portName, bool enable) = 0;

    /**
     * When supportsEnableContaminantPresenceProtection is true,
     * enableContaminantPresenceProtection enables/disables contaminant
     * presence protection algorithm. Calling
     * enableContaminantPresenceProtection
     * when supportsEnableContaminantPresenceProtection is false does
     * not have any effect.
     * Used to enable/disable contaminant presence protection algorithm.
     * Enabling port protection algoritm must make the lower layers to autonomously
     * act on taking the corresponding preventive measure mentioned at
     * ContaminantProtectionModes when contaminant is detected on the USB Port.
     * Calling this method with enable set to true must set to contaminantProtectionEnabled
     * to true upon success and vice versa.
     * currentContaminantProtectionMode should be updated whenever there is a
     * change in the status of contaminant presence protection algorithm.
     *
     * @param portName name of the port.
     * @param enable true Reduce capabilities of the port to protect port
     *                    from damage due to contaminant presence.
     *               false No action is taken upon contaminant presence.
     */
    virtual ::android::hardware::Return<void> enableContaminantPresenceProtection(const ::android::hardware::hidl_string& portName, bool enable) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::usb::V1_2::IUsb>> castFrom(const ::android::sp<::android::hardware::usb::V1_2::IUsb>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::usb::V1_2::IUsb>> castFrom(const ::android::sp<::android::hardware::usb::V1_1::IUsb>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::usb::V1_2::IUsb>> castFrom(const ::android::sp<::android::hardware::usb::V1_0::IUsb>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::usb::V1_2::IUsb>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IUsb> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IUsb> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IUsb> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IUsb> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IUsb> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IUsb> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IUsb> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IUsb> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::usb::V1_2::IUsb>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::usb::V1_2::IUsb>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::usb::V1_2::IUsb::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_2
}  // namespace usb
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_IUSB_H
