#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENT_H

#include <android/hardware/graphics/bufferqueue/1.0/IGraphicBufferProducer.h>
#include <android/hardware/graphics/bufferqueue/2.0/IGraphicBufferProducer.h>
#include <android/hardware/media/c2/1.0/IComponentInterface.h>
#include <android/hardware/media/c2/1.0/IConfigurable.h>
#include <android/hardware/media/c2/1.0/IInputSink.h>
#include <android/hardware/media/c2/1.0/IInputSurface.h>
#include <android/hardware/media/c2/1.0/IInputSurfaceConnection.h>
#include <android/hardware/media/c2/1.0/types.h>
#include <android/hardware/media/omx/1.0/IGraphicBufferSource.h>
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
 * Interface for a Codec2 component corresponding to API level 1.0 or below.
 * Components have two states: stopped and running. The running state has three
 * sub-states: executing, tripped and error.
 *
 * All methods in `IComponent` must not block. If a method call cannot be
 * completed in a timely manner, it must return `TIMED_OUT` in the return
 * status.
 */
struct IComponent : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.media.c2@1.0::IComponent"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Queues up work for the component.
     *
     * This method must be supported in running (including tripped) states.
     *
     * It is acceptable for this method to return `OK` and return an error value
     * using the IComponentListener::onWorkDone() callback.
     *
     * @param workBundle `WorkBundle` object containing a list of `Work` objects
     *     to queue to the component.
     * @return status Status of the call, which may be
     *   - `OK`        - Works in @p workBundle were successfully queued.
     *   - `BAD_INDEX` - Some component id in some `Worklet` is not valid.
     *   - `CANNOT_DO` - The components are not tunneled but some `Work` object
     *                   contains tunneling information.
     *   - `NO_MEMORY` - Not enough memory to queue @p workBundle.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> queue(const ::android::hardware::media::c2::V1_0::WorkBundle& workBundle) = 0;

    /**
     * Return callback for flush
     */
    using flush_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::hardware::media::c2::V1_0::WorkBundle& flushedWorkBundle)>;
    /**
     * Discards and abandons any pending `Work` items for the component.
     *
     * This method must be supported in running (including tripped) states.
     *
     * `Work` that could be immediately abandoned/discarded must be returned in
     * @p flushedWorkBundle. The order in which queued `Work` items are
     * discarded can be arbitrary.
     *
     * `Work` that could not be abandoned or discarded immediately must be
     * marked to be discarded at the earliest opportunity, and must be returned
     * via IComponentListener::onWorkDone(). This must be completed within
     * 500ms.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The component has been successfully flushed.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return flushedWorkBundle `WorkBundle` object containing flushed `Work`
     *     items.
     */
    virtual ::android::hardware::Return<void> flush(flush_cb _hidl_cb) = 0;

    /**
     * Drains the component, and optionally downstream components. This is a
     * signalling method; as such it does not wait for any work completion.
     *
     * The last `Work` item is marked as "drain-till-here", so the component is
     * notified not to wait for further `Work` before it processes what is
     * already queued. This method can also be used to set the end-of-stream
     * flag after `Work` has been queued. Client can continue to queue further
     * `Work` immediately after this method returns.
     *
     * This method must be supported in running (including tripped) states.
     *
     * `Work` that is completed must be returned via
     * IComponentListener::onWorkDone().
     *
     * @param withEos Whether to drain the component with marking end-of-stream.
     * @return status Status of the call, which may be
     *   - `OK`        - The drain request has been successfully recorded.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> drain(bool withEos) = 0;

    /**
     * Starts using a surface for output.
     *
     * This method must not block.
     *
     * @param blockPoolId Id of the `C2BlockPool` to be associated with the
     *     output surface.
     * @param surface Output surface.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `CANNOT_DO` - The component does not support an output surface.
     *   - `REFUSED`   - The output surface cannot be accessed.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> setOutputSurface(uint64_t blockPoolId, const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& surface) = 0;

    /**
     * Return callback for connectToInputSurface
     */
    using connectToInputSurface_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurfaceConnection>& connection)>;
    /**
     * Starts using an input surface.
     *
     * The component must be in running state.
     *
     * @param inputSurface Input surface to connect to.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `CANNOT_DO` - The component does not support an input surface.
     *   - `BAD_STATE` - The component is not in running state.
     *   - `DUPLICATE` - The component is already connected to an input surface.
     *   - `REFUSED`   - The input surface is already in use.
     *   - `NO_MEMORY` - Not enough memory to start the component.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return connection `IInputSurfaceConnection` object, which can be used to
     *     query and configure properties of the connection. This cannot be
     *     null.
     */
    virtual ::android::hardware::Return<void> connectToInputSurface(const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurface>& inputSurface, connectToInputSurface_cb _hidl_cb) = 0;

    /**
     * Return callback for connectToOmxInputSurface
     */
    using connectToOmxInputSurface_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, const ::android::sp<::android::hardware::media::c2::V1_0::IInputSurfaceConnection>& connection)>;
    /**
     * Starts using an OMX input surface.
     *
     * The component must be in running state.
     *
     * This method is similar to connectToInputSurface(), but it takes an OMX
     * input surface (as a pair of `IGraphicBufferProducer` and
     * `IGraphicBufferSource`) instead of Codec2's own `IInputSurface`.
     *
     * @param producer Producer component of an OMX input surface.
     * @param source Source component of an OMX input surface.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `CANNOT_DO` - The component does not support an OMX input surface.
     *   - `BAD_STATE` - The component is not in running state.
     *   - `DUPLICATE` - The component is already connected to an input surface.
     *   - `REFUSED`   - The input surface is already in use.
     *   - `NO_MEMORY` - Not enough memory to start the component.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return connection `IInputSurfaceConnection` object, which can be used to
     *     query and configure properties of the connection. This cannot be
     *     null.
     */
    virtual ::android::hardware::Return<void> connectToOmxInputSurface(const ::android::sp<::android::hardware::graphics::bufferqueue::V1_0::IGraphicBufferProducer>& producer, const ::android::sp<::android::hardware::media::omx::V1_0::IGraphicBufferSource>& source, connectToOmxInputSurface_cb _hidl_cb) = 0;

    /**
     * Stops using an input surface.
     *
     * The component must be in running state.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `CANNOT_DO` - The component does not support an input surface.
     *   - `BAD_STATE` - The component is not in running state.
     *   - `NOT_FOUND` - The component is not connected to an input surface.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> disconnectFromInputSurface() = 0;

    /**
     * Return callback for createBlockPool
     */
    using createBlockPool_cb = std::function<void(::android::hardware::media::c2::V1_0::Status status, uint64_t blockPoolId, const ::android::sp<::android::hardware::media::c2::V1_0::IConfigurable>& configurable)>;
    /**
     * Creates a local `C2BlockPool` backed by the given allocator and returns
     * its id.
     *
     * The returned @p blockPoolId is the only way the client can refer to a
     * `C2BlockPool` object in the component. The id can be passed to
     * setOutputSurface() or used in some C2Param objects later.
     *
     * The created `C2BlockPool` object can be destroyed by calling
     * destroyBlockPool(), reset() or release(). reset() and release() must
     * destroy all `C2BlockPool` objects that have been created.
     *
     * @param allocatorId Id of a `C2Allocator`.
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `NO_MEMORY` - Not enough memory to create the pool.
     *   - `BAD_VALUE` - @p allocatorId is not recognized.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     * @return blockPoolId Id of the created C2BlockPool object. This may be
     *     used in setOutputSurface() if the allocator
     * @return configurable Configuration interface for the created pool. This
     *     must not be null.
     */
    virtual ::android::hardware::Return<void> createBlockPool(uint32_t allocatorId, createBlockPool_cb _hidl_cb) = 0;

    /**
     * Destroys a local block pool previously created by createBlockPool().
     *
     * @param blockPoolId Id of a `C2BlockPool` that was previously returned by
     *      createBlockPool().
     * @return status Status of the call, which may be
     *   - `OK`        - The operation completed successfully.
     *   - `NOT_FOUND` - The supplied blockPoolId is not valid.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> destroyBlockPool(uint64_t blockPoolId) = 0;

    /**
     * Starts the component.
     *
     * This method must be supported in stopped state as well as tripped state.
     *
     * If the return value is `OK`, the component must be in the running state.
     * If the return value is `BAD_STATE` or `DUPLICATE`, no state change is
     * expected as a response to this call. Otherwise, the component must be in
     * the stopped state.
     *
     * If a component is in the tripped state and start() is called while the
     * component configuration still results in a trip, start() must succeed and
     * a new onTripped() callback must be used to communicate the configuration
     * conflict that results in the new trip.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The component has started successfully.
     *   - `BAD_STATE` - Component is not in stopped or tripped state.
     *   - `DUPLICATE` - When called during another start call from another
     *                   thread.
     *   - `NO_MEMORY` - Not enough memory to start the component.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> start() = 0;

    /**
     * Stops the component.
     *
     * This method must be supported in running (including tripped) state.
     *
     * This method must return withing 500ms.
     *
     * Upon this call, all pending `Work` must be abandoned.
     *
     * If the return value is `BAD_STATE` or `DUPLICATE`, no state change is
     * expected as a response to this call. For all other return values, the
     * component must be in the stopped state.
     *
     * This does not alter any settings and tunings that may have resulted in a
     * tripped state.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The component has stopped successfully.
     *   - `BAD_STATE` - Component is not in running state.
     *   - `DUPLICATE` - When called during another stop call from another
     *                   thread.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> stop() = 0;

    /**
     * Resets the component.
     *
     * This method must be supported in all (including tripped) states other
     * than released.
     *
     * This method must be supported during any other blocking call.
     *
     * This method must return withing 500ms.
     *
     * When this call returns, if @p status is `OK`, all `Work` items must
     * have been abandoned, and all resources (including `C2BlockPool` objects
     * previously created by createBlockPool()) must have been released.
     *
     * If the return value is `BAD_STATE` or `DUPLICATE`, no state change is
     * expected as a response to this call. For all other return values, the
     * component must be in the stopped state.
     *
     * This brings settings back to their default, "guaranteeing" no tripped
     * state.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The component has been reset.
     *   - `BAD_STATE` - Component is in released state.
     *   - `DUPLICATE` - When called during another reset call from another
     *                   thread.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> reset() = 0;

    /**
     * Releases the component.
     *
     * This method must be supported in stopped state.
     *
     * This method destroys the component. Upon return, if @p status is `OK` or
     * `DUPLICATE`, all resources must have been released.
     *
     * @return status Status of the call, which may be
     *   - `OK`        - The component has been released.
     *   - `BAD_STATE` - The component is running.
     *   - `DUPLICATE` - The component is already released.
     *   - `TIMED_OUT` - The operation cannot be finished in a timely manner.
     *   - `CORRUPTED` - Some unknown error occurred.
     */
    virtual ::android::hardware::Return<::android::hardware::media::c2::V1_0::Status> release() = 0;

    /**
     * Returns the @ref IComponentInterface instance associated to this
     * component.
     *
     * An @ref IConfigurable instance for the component can be obtained by calling
     * IComponentInterface::getConfigurable() on the returned @p intf.
     *
     * @return intf `IComponentInterface` instance. This must not be null.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponentInterface>> getInterface() = 0;

    /**
     * Returns an @ref IInputSink instance that has the component as the
     * underlying implementation.
     *
     * @return sink `IInputSink` instance.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IInputSink>> asInputSink() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponent>> castFrom(const ::android::sp<::android::hardware::media::c2::V1_0::IComponent>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::media::c2::V1_0::IComponent>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IComponent> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponent> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IComponent> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponent> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IComponent> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponent> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IComponent> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IComponent> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponent>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::media::c2::V1_0::IComponent>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::media::c2::V1_0::IComponent::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_ICOMPONENT_H
