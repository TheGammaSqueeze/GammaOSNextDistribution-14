#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXNODE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXNODE_H

#include <android/hardware/media/omx/1.0/IOmxBufferSource.h>
#include <android/hardware/media/omx/1.0/types.h>
#include <android/hardware/media/1.0/types.h>
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
 * Ref: frameworks/av/include/media/IOMX.h: IOMXNode
 * Ref: https://www.khronos.org/registry/omxil/specs/OpenMAX_IL_1_1_2_Specification.pdf
 *
 *
 * IOmxNode is an interface for communicating with an OMX component (called
 * "node" here) that has been previously obtained by calling
 * IOmx::allocateNode().
 */
struct IOmxNode : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.omx@1.0::IOmxNode"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Free the node.
     *
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> freeNode() = 0;

    /**
     * Invoke a command on the node.
     *
     * @param cmd Type of the command.
     * @param param Parameter for the command.
     * @return status Status of the call.
     *
     * @see OMX_SendCommand() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> sendCommand(uint32_t cmd, int32_t param) = 0;

    /**
     * Return callback for getParameter
     */
    using getParameter_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& outParams)>;
    /**
     * Retrieve a parameter setting from the node.
     *
     * @param index Type of the parameter to retrieve.
     * @param inParams Information about the retrieval.
     * @return status Status of the call.
     * @return outParams Current parameter setting.
     *
     * @see OMX_GetParameter() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<void> getParameter(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inParams, getParameter_cb _hidl_cb) = 0;

    /**
     * Change a parameter setting of the node.
     *
     * @param index Type of the parameter to change.
     * @param params New parameter setting.
     * @return status Status of the call.
     *
     * @see OMX_SetParameter() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setParameter(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& params) = 0;

    /**
     * Return callback for getConfig
     */
    using getConfig_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& outConfig)>;
    /**
     * Retrieve a configuration from the node.
     *
     * @param index Type of the configuration to retrieve.
     * @param inConfig Information about the retrieval.
     * @return status Status of the call.
     * @return outConfig Current configuration.
     *
     * @see OMX_GetConfig() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<void> getConfig(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& inConfig, getConfig_cb _hidl_cb) = 0;

    /**
     * Change a configuration of the node.
     *
     * @param index Type of the configuration to change.
     * @param config New configuration.
     * @return status Status of the call.
     *
     * @see OMX_SetConfig() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setConfig(uint32_t index, const ::android::hardware::hidl_vec<uint8_t>& config) = 0;

    /**
     * Set the mode of a port on the node.
     *
     * @param portIndex Index of the port.
     * @param mode Target mode on the specified port.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setPortMode(uint32_t portIndex, ::android::hardware::media::omx::V1_0::PortMode mode) = 0;

    /**
     * Prepare a port for adaptive playback. This is based on the extension
     * "OMX.google.android.index.prepareForAdaptivePlayback".
     *
     * @param portIndex Index of the port.
     * @param enable Whether the adaptive playback is enabled or not.
     * @param maxFrameWidth Maximum frame width.
     * @param maxFrameHeight Maximum frame height.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> prepareForAdaptivePlayback(uint32_t portIndex, bool enable, uint32_t maxFrameWidth, uint32_t maxFrameHeight) = 0;

    /**
     * Return callback for configureVideoTunnelMode
     */
    using configureVideoTunnelMode_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, const ::android::hardware::hidl_handle& sidebandHandle)>;
    /**
     * Configure a port for a tunneled playback mode. This is based on the
     * extension "OMX.google.android.index.configureVideoTunnelMode".
     *
     * @param portIndex Index of the port.
     * @param tunneled Whether the tunneled mode is used or not.
     * @param audioHwSync HW SYNC ID of the audio HAL output stream to sync
     * the video with.
     * @return status Status of the call.
     * @return sidebandHandle Codec-allocated sideband window handle.
     */
    virtual ::android::hardware::Return<void> configureVideoTunnelMode(uint32_t portIndex, bool tunneled, uint32_t audioHwSync, configureVideoTunnelMode_cb _hidl_cb) = 0;

    /**
     * Return callback for getGraphicBufferUsage
     */
    using getGraphicBufferUsage_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, uint32_t usage)>;
    /**
     * Retrieve the buffer usage on a port. This is based on the extension
     * "OMX.google.android.index.getAndroidNativeBufferUsage".
     *
     * @param portIndex Index of the port.
     * @return status Status of the call.
     * @return usage Current graphic buffer usage.
     */
    virtual ::android::hardware::Return<void> getGraphicBufferUsage(uint32_t portIndex, getGraphicBufferUsage_cb _hidl_cb) = 0;

    /**
     * Set up a listener to events related to the input surface.
     *
     * @param bufferSource Listener object that implements
     * IOmxBufferSource.
     * @return status Status of the call.
     *
     * @see IOmxBufferSource.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> setInputSurface(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxBufferSource>& bufferSource) = 0;

    /**
     * Return callback for allocateSecureBuffer
     */
    using allocateSecureBuffer_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, uint32_t buffer, const ::android::hardware::hidl_handle& nativeHandle)>;
    /**
     * Allocate an opaque buffer on a port as a native handle.
     *
     * @param portIndex Index of the port.
     * @param size Desired size of the buffer.
     * @return status Status of the call.
     * @return buffer Id of the allocated buffer, which will be needed in
     * other buffer-related functions.
     * @return nativeHandle Native handle of the allocated buffer.
     *
     * @see OMX_AllocateBuffer() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<void> allocateSecureBuffer(uint32_t portIndex, uint64_t size, allocateSecureBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for useBuffer
     */
    using useBuffer_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, uint32_t buffer)>;
    /**
     * Assign a buffer to a port.
     *
     * @param portIndex Index of the port.
     * @param omxBuffer Buffer to be assigned to the port.
     * @return status Status of the call.
     * @return buffer Id of the assigned buffer, which will be needed in
     * other buffer-related functions.
     *
     * @see OMX_UseBuffer() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<void> useBuffer(uint32_t portIndex, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, useBuffer_cb _hidl_cb) = 0;

    /**
     * Free a buffer previously assigned to a port by allocateSecureBuffer() or
     * useBuffer().
     *
     * @param portIndex Index of the port.
     * @param buffer Id of the buffer to be freed.
     * @return status Status of the call.
     *
     * @see OMX_FreeBuffer() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> freeBuffer(uint32_t portIndex, uint32_t buffer) = 0;

    /**
     * Pass \p fence to the node if it supports fences. Otherwise, it waits on
     * \p fence before calling OMX_FillThisBuffer(). The node will take
     * ownership of the fence even if this call fails.
     *
     * If the port is in metadata mode, the buffer will be updated to point to
     * the new buffer passed in via \p omxBuffer before OMX_FillThisBuffer() is
     * called. Otherwise, \p omxBuffer is not used.
     *
     * @param buffer Id of the buffer to fill.
     * @param omxBuffer New buffer information (in metadata mode).
     * @param fence Fence to wait for (if not null).
     * @return status Status of the call.
     *
     * @see OMX_FillThisBuffer() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> fillBuffer(uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, const ::android::hardware::hidl_handle& fence) = 0;

    /**
     * Pass \p fence to the node if it supports fences. Otherwise, wait on
     * \p fence before calling OMX_EmptyThisBuffer(). The node will take
     * ownership of the fence even if this call fails.
     *
     * If the port is in metadata mode, the buffer will be updated to point to
     * the new buffer passed in via \p omxBuffer before OMX_EmptyThisBuffer() is
     * called. Otherwise, \p omxBuffer is not used.
     *
     * @param buffer Id of the buffer to fill.
     * @param omxBuffer New buffer information (in metadata mode).
     * @param flags Flags to be passed to OMX_EmptyBuffer().
     * @param timestampUs Timestamp OMX_EmptyBuffer().
     * @param fence Fence to wait for (if not null).
     * @return status Status of the call.
     *
     * @see OMX_EmptyThisBuffer() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> emptyBuffer(uint32_t buffer, const ::android::hardware::media::omx::V1_0::CodecBuffer& omxBuffer, uint32_t flags, uint64_t timestampUs, const ::android::hardware::hidl_handle& fence) = 0;

    /**
     * Return callback for getExtensionIndex
     */
    using getExtensionIndex_cb = std::function<void(::android::hardware::media::omx::V1_0::Status status, uint32_t index)>;
    /**
     * Request the node to translate an extension string to an index.
     *
     * @param parameterName Requested extension string.
     * @return status Status of the call.
     * @return index Translated index.
     *
     * @see OMX_GetExtensionIndex() in the OpenMax IL standard.
     */
    virtual ::android::hardware::Return<void> getExtensionIndex(const ::android::hardware::hidl_string& parameterName, getExtensionIndex_cb _hidl_cb) = 0;

    /**
     * Add an OMX message on the node's message queue. The instance of
     * IOmxObserver that was obtained during the creation of the node will
     * receive the message in batches by the callback
     * IOmxObserver::onMessages().
     *
     * @param msg Message to send.
     * @return status Status of the call.
     *
     * @see IOmxObserver::onMessages().
     */
    virtual ::android::hardware::Return<::android::hardware::media::omx::V1_0::Status> dispatchMessage(const ::android::hardware::media::omx::V1_0::Message& msg) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>> castFrom(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IOmxNode> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IOmxNode> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IOmxNode> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IOmxNode> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IOmxNode> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IOmxNode> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IOmxNode> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IOmxNode> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::media::omx::V1_0::IOmxNode>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::omx::V1_0::IOmxNode::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IOMXNODE_H
