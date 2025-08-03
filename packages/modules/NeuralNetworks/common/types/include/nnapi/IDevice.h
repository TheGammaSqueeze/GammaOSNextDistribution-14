/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IDEVICE_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IDEVICE_H

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "nnapi/Result.h"
#include "nnapi/Types.h"

namespace android::nn {

// Forward declarations
class IBuffer;
class IPreparedModel;

/**
 * This interface represents a device driver.
 *
 * This interface is thread-safe, and any class that implements this interface must be thread-safe.
 */
class IDevice {
   public:
    /**
     * Returns the name of the driver.
     *
     * @return Name of the driver.
     */
    virtual const std::string& getName() const = 0;

    /**
     * Get the version string of the driver implementation.
     *
     * The version string must be a unique token among the set of version strings of drivers of a
     * specific device. The token identifies the device driver's implementation. The token must not
     * be confused with the feature level which is solely defined by the interface version. This API
     * is opaque to the Android framework, but the Android framework may use the information for
     * debugging or to pass on to NNAPI applications.
     *
     * Application developers sometimes have specific requirements to ensure good user experiences,
     * and they need more information to make intelligent decisions when the Android framework
     * cannot. For example, combined with the device name and other information, the token can help
     * NNAPI applications filter devices based on their needs:
     * - An application demands a certain level of performance, but a specific version of the driver
     *   cannot meet that requirement because of a performance regression. The application can
     *   disallow the driver based on the version provided.
     * - An application has a minimum precision requirement, but certain versions of the driver
     *   cannot meet that requirement because of bugs or certain optimizations. The application can
     *   filter out versions of these drivers.
     *
     * @return version The version string of the device implementation. Must have nonzero length.
     */
    virtual const std::string& getVersionString() const = 0;

    /**
     * Returns the feature level of a driver.
     *
     * @return featureLevel The Version of the NNAPI specification this driver implements.
     *                      See {@link ANeuralNetworks_getFeatureLevel} and {@link FeatureLevelCode}
     *                      for NNAPI specification versioning information.
     */
    virtual Version getFeatureLevel() const = 0;

    /**
     * Returns the device type of a driver.
     *
     * The device type can be used to help application developers to distribute Machine Learning
     * workloads and other workloads such as graphical rendering. E.g., for an app which renders AR
     * scenes based on real time object detection results, the developer could choose an ACCELERATOR
     * type device for ML workloads, and reserve GPU for graphical rendering.
     *
     * @return type The DeviceType of the device. Please note, this is not a bitfield of
     *     DeviceTypes. Each device must only be of a single DeviceType.
     */
    virtual DeviceType getType() const = 0;

    /**
     * Gets information about extensions supported by the driver implementation.
     *
     * Extensions of category ExtensionCategory::BASE must not appear in the list.
     *
     * All extension operations and operands must be fully supported for the extension to appear in
     * the list of supported extensions.
     *
     * @return extensions A list of supported extensions.
     */
    virtual const std::vector<Extension>& getSupportedExtensions() const = 0;

    /**
     * Gets the capabilities of a driver.
     *
     * @return capabilities Capabilities of the driver.
     */
    virtual const Capabilities& getCapabilities() const = 0;

    /**
     * Gets the caching requirements of the driver implementation.
     *
     * There are two types of cache file descriptors provided to the driver: model cache and data
     * cache.
     *
     * The data cache is for caching constant data, possibly including preprocessed and transformed
     * tensor buffers. Any modification to the data cache should have no worse effect than
     * generating bad output values at execution time.
     *
     * The model cache is for caching security-sensitive data such as compiled executable machine
     * code in the device's native binary format. A modification to the model cache may affect the
     * driver's execution behavior, and a malicious client could make use of this to execute beyond
     * the granted permission. Thus, the driver must always check whether the model cache is
     * corrupted before preparing the model from cache.
     *
     * IDevice::getNumberOfCacheFilesNeeded returns how many of each type of cache files the driver
     * implementation needs to cache a single prepared model. Returning 0 for both types indicates
     * compilation caching is not supported by this driver. The driver may still choose not to cache
     * certain compiled models even if it reports that caching is supported.
     *
     * If the device reports that caching is not supported, the user may avoid calling
     * IDevice::prepareModelFromCache or providing cache file descriptors to IDevice::prepareModel.
     *
     * @return A pair of:
     *     - numModelCache An unsigned integer indicating how many files for model cache the driver
     *       needs to cache a single prepared model. It must be less than or equal to
     *       ::android::nn::kMaxNumberOfCacheFiles.
     *     - numDataCache An unsigned integer indicating how many files for data cache the driver
     *       needs to cache a single prepared model. It must be less than or equal to
     *       ::android::nn::kMaxNumberOfCacheFiles.
     */
    virtual std::pair<uint32_t, uint32_t> getNumberOfCacheFilesNeeded() const = 0;

    /**
     * Blocks until the device is not in a bad state.
     *
     * @return Nothing on success, otherwise GeneralError.
     */
    virtual GeneralResult<void> wait() const = 0;

    /**
     * Gets the supported operations in a model.
     *
     * IDevice::getSupportedOperations indicates which operations of the top-level subgraph are
     * fully supported by the vendor driver. If an operation may not be supported for any reason,
     * IDevice::getSupportedOperations must return `false` for that operation.
     *
     * The {@link OperationType::IF} and {@link OperationType::WHILE} operations may only be fully
     * supported if the vendor driver fully supports all operations in the referenced subgraphs.
     *
     * @param model A Model whose operations--and their corresponding operands--are to be verified
     *     by the driver.
     * @return supportedOperations A list of supported operations, where `true` indicates the
     *     operation is supported and `false` indicates the operation is not supported. The index of
     *     "supported" corresponds with the index of the operation it is describing.
     */
    virtual GeneralResult<std::vector<bool>> getSupportedOperations(const Model& model) const = 0;

    /**
     * Creates a prepared model for execution.
     *
     * IDevice::prepareModel is used to make any necessary transformations or alternative
     * representations to a model for execution, possibly including transformations on the constant
     * data, optimization on the model's graph, or compilation into the device's native binary
     * format. The model itself is not changed.
     *
     * Optionally, caching information may be provided for the driver to save the prepared model to
     * cache files for faster model compilation time when the same model preparation is requested in
     * the future. There are two types of cache file handles provided to the driver: model cache and
     * data cache. For more information on the two types of cache handles, refer to
     * IDevice::getNumberOfCacheFilesNeeded.
     *
     * The file descriptors must be opened with read and write permission. A file may have any size,
     * and the corresponding file descriptor may have any offset. The driver must truncate a file to
     * zero size before writing to that file. The file descriptors may be closed by the client once
     * the preparation has finished. The driver must dup a file descriptor if it wants to get access
     * to the cache file later.
     *
     * IDevice::prepareModel must verify its inputs related to preparing the model (as opposed to
     * saving the prepared model to cache) are correct. If there is an error, IDevice::prepareModel
     * must immediately return {@link ErrorStatus::INVALID_ARGUMENT} as a GeneralError. If the
     * inputs to IDevice::prepareModel are valid and there is no error, IDevice::prepareModel must
     * prepare the model.
     *
     * The model is prepared with a priority. This priority is relative to other prepared models
     * owned by the same client. Higher priority executions may use more compute resources than
     * lower priority executions, and may preempt or starve lower priority executions.
     *
     * IDevice::prepareModel can be called with an optional deadline. If the model is not able to be
     * prepared before the provided deadline, the model preparation may be aborted, and either
     * {@link ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link
     * ErrorStatus::MISSED_DEADLINE_PERSISTENT} may be returned as a GeneralError.
     *
     * Optionally, the driver may save the prepared model to cache during the preparation. Any error
     * that occurs when saving to cache must not affect the status of preparing the model. Even if
     * the input arguments related to the cache may be invalid, or the driver may fail to save to
     * cache, IDevice::prepareModel must finish preparing the model. The driver may choose not to
     * save to cache even if the caching information is provided and valid.
     *
     * The only information that may be unknown to the model at this stage is the shape of the
     * tensors, which may only be known at execution time. As such, some driver services may return
     * partially prepared models, where the prepared model may only be finished when it is paired
     * with a set of inputs to the model. Note that the same prepared model object may be used with
     * different shapes of inputs on different (possibly concurrent) executions.
     *
     * @param model The model to be prepared for execution.
     * @param preference Indicates the intended execution behavior of a prepared model.
     * @param priority Priority of the prepared model relative to other prepared models owned by an
     *     application.
     * @param deadline Optional time point. If provided, prepareModel is expected to complete by
     *     this time point. If it is not able to be completed by the deadline, the execution may be
     *     aborted.
     * @param modelCache A vector of handles with each entry holding exactly one cache file
     *     descriptor for the security-sensitive cache. The length of the vector must either be 0
     *     indicating that caching information is not provided, or match numModelCache returned from
     *     IDevice::getNumberOfCacheFilesNeeded. The cache handles will be provided in the same
     *     order when retrieving the preparedModel from cache files with
     *     IDevice::prepareModelFromCache.
     * @param dataCache A vector of handles with each entry holding exactly one cache file
     *     descriptor for the constants' cache. The length of the vector must either be 0 indicating
     *     that caching information is not provided, or match numDataCache returned from
     *     IDevice::getNumberOfCacheFilesNeeded. The cache handles will be provided in the same
     *     order when retrieving the preparedModel from cache files with
     *     IDevice::prepareModelFromCache.
     * @param token An caching token of length ::android::nn::kByteSizeOfCacheToken identifying the
     *     prepared model. The same token will be provided when retrieving the prepared model from
     *     the cache files with IDevice::prepareModelFromCache. Tokens should be chosen to have a
     *     low rate of collision for a particular application. The driver cannot detect a collision;
     *     a collision will result in a failed execution or in a successful execution that produces
     *     incorrect output values. If both modelCache and dataCache are empty indicating that
     *     caching information is not provided, this token must be ignored.
     * @param hints Specifies the optional device specific compilation hints. The same token must
     *     not be repeated. It is allowed for the driver to ignore the user-provided hints.
     * @param extensionNameToPrefix The mapping between extension names and prefixes of token
     *.    values.
     * @return preparedModel An IPreparedModel object representing a model that has been prepared
     *     for execution, otherwise GeneralError.
     */
    virtual GeneralResult<SharedPreparedModel> prepareModel(
            const Model& model, ExecutionPreference preference, Priority priority,
            OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
            const std::vector<SharedHandle>& dataCache, const CacheToken& token,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    /**
     * Creates a prepared model from cache files for execution.
     *
     * IDevice::prepareModelFromCache is used to retrieve a prepared model directly from cache files
     * to avoid slow model compilation time. There are two types of cache file handles provided to
     * the driver: model cache and data cache. For more information on the two types of cache
     * handles, refer to IDevice::getNumberOfCacheFilesNeeded.
     *
     * The file descriptors must be opened with read and write permission. A file may have any size,
     * and the corresponding file descriptor may have any offset. The driver must truncate a file to
     * zero size before writing to that file. The file descriptors may be closed by the client once
     * the preparation has finished. The driver must dup a file descriptor if it wants to get access
     * to the cache file later.
     *
     * IDevice::prepareModelFromCache must verify its inputs are correct, and that the
     * security-sensitive cache has not been modified since it was last written by the driver. If
     * there is an error, or if compilation caching is not supported, or if the security-sensitive
     * cache has been modified, IDevice::prepareModelFromCache must immediately return {@link
     * ErrorStatus::INVALID_ARGUMENT} as a GeneralError. If the inputs to
     * IDevice::prepareModelFromCache are valid, the security-sensitive cache is not modified, and
     * there is no error, IDevice::prepareModelFromCache must prepare the model
     *
     * IDevice::prepareModelFromCache can be called with an optional deadline. If the model is not
     * able to prepared before the provided deadline, the model preparation may be aborted, and
     * either {@link ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link
     * ErrorStatus::MISSED_DEADLINE_PERSISTENT} may be returned as a GeneralError.
     *
     * The only information that may be unknown to the model at this stage is the shape of the
     * tensors, which may only be known at execution time. As such, some driver services may return
     * partially prepared models, where the prepared model may only be finished when it is paired
     * with a set of inputs to the model. Note that the same prepared model object may be used with
     * different shapes of inputs on different (possibly concurrent) executions.
     *
     * @param deadline Optional time point. If provided, prepareModel is expected to complete by
     *     this time point. If it is not able to be completed by the deadline, the execution may be
     *     aborted.
     * @param modelCache A vector of handles with each entry holding exactly one cache file
     *     descriptor for the security-sensitive cache. The length of the vector must match the
     *     numModelCache returned from IDevice::getNumberOfCacheFilesNeeded. The cache handles will
     *     be provided in the same order as with IDevice::prepareModel.
     * @param dataCache A vector of handles with each entry holding exactly one cache file
     *     descriptor for the constants' cache. The length of the vector must match the numDataCache
     *     returned from IDevice::getNumberOfCacheFilesNeeded. The cache handles will be provided in
     *     the same order as with IDevice::prepareModel.
     * @param token A caching token of length ::android::nn::kByteSizeOfCacheToken identifying the
     *     prepared model. It is the same token provided when saving the cache files with
     *     IDevice::prepareModel. Tokens should be chosen to have a low rate of collision for a
     *     particular application. The driver cannot detect a collision; a collision will result in
     *     a failed execution or in a successful execution that produces incorrect output values.
     * @return preparedModel An IPreparedModel object representing a model that has been prepared
     *     for execution, otherwise GeneralError.
     */
    virtual GeneralResult<SharedPreparedModel> prepareModelFromCache(
            OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
            const std::vector<SharedHandle>& dataCache, const CacheToken& token) const = 0;

    /**
     * Allocates a driver-managed buffer with the properties specified by the descriptor as well as
     * the input and output roles of prepared models.
     *
     * IDevice::allocate must verify its inputs are correct. If there is an error, or if a certain
     * role or property is not supported by the driver, IDevice::allocate must return with {@link
     * ErrorStatus::INVALID_ARGUMENT} as a GeneralError. If the allocation is successful, this
     * method must return the produced IBuffer. A successful allocation must accommodate all of the
     * specified roles and buffer properties.
     *
     * The buffer is allocated as an uninitialized state. An uninitialized buffer may only be used
     * in ways that are specified by outputRoles. A buffer is initialized after it is used as an
     * output in a successful execution, or after a successful invocation of IBuffer::copyFrom on
     * the buffer. An initialized buffer may be used according to all roles specified in inputRoles
     * and outputRoles. A buffer will return to the uninitialized state if it is used as an output
     * in a failed execution, or after a failed invocation of IBuffer::copyFrom on the buffer.
     *
     * The driver may deduce the dimensions of the buffer according to the buffer descriptor as well
     * as the input and output roles. The dimensions or rank of the buffer may be unknown at this
     * stage. As such, some driver services may only create a placeholder and defer the actual
     * allocation until execution time. Note that the same buffer may be used for different shapes
     * of outputs on different executions. When the buffer is used as an input, the input shape must
     * be the same as the output shape from the last execution using this buffer as an output.
     *
     * The driver must apply proper validatation upon every usage of the buffer, and fail the
     * execution immediately if the usage is illegal.
     *
     * @param desc A buffer descriptor specifying the properties of the buffer to allocate.
     * @param preparedModels A vector of IPreparedModel objects. Must only contain IPreparedModel
     *     objects from the same IDevice as this method invoked on.
     * @param inputRoles A vector of roles with each specifying an input to a prepared model.
     * @param outputRoles A vector of roles with each specifying an output to a prepared model.
     *     Each role specified in inputRoles and outputRoles must be unique. The corresponding model
     *     operands of the roles must have the same OperandType, scale, zero point, and ExtraParams.
     *     The dimensions of the operands and the dimensions specified in the buffer descriptor must
     *     be compatible with each other. Two dimensions are incompatible if there is at least one
     *     axis that is fully specified in both but has different values.
     * @return The allocated IBuffer object. If the buffer was unable to be allocated due to an
     *     error, a GeneralError is returned instead.
     */
    virtual GeneralResult<SharedBuffer> allocate(
            const BufferDesc& desc, const std::vector<SharedPreparedModel>& preparedModels,
            const std::vector<BufferRole>& inputRoles,
            const std::vector<BufferRole>& outputRoles) const = 0;

    // Public virtual destructor to allow objects to be stored (and destroyed) as smart pointers.
    // E.g., std::unique_ptr<IDevice>.
    virtual ~IDevice() = default;

   protected:
    // Protect the non-destructor special member functions to prevent object slicing.
    IDevice() = default;
    IDevice(const IDevice&) = default;
    IDevice(IDevice&&) noexcept = default;
    IDevice& operator=(const IDevice&) = default;
    IDevice& operator=(IDevice&&) noexcept = default;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IDEVICE_H
