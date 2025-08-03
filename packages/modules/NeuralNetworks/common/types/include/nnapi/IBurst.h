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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBURST_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBURST_H

#include <android-base/scopeguard.h>

#include <functional>
#include <memory>
#include <optional>
#include <utility>
#include <vector>

#include "nnapi/Types.h"

namespace android::nn {

/**
 * IBurst represents a burst execution object.
 *
 * Burst executions are a sequence of executions of the same prepared model that occur in rapid
 * succession, such as frames of a camera capture or successive audio samples. A burst object is
 * used to control a set of burst executions, and to preserve resources between executions, enabling
 * executions to have lower overhead. Burst objects enable some optimizations:
 * (1) A burst object is created before a sequence of executions, and freed when the sequence has
 *     ended. Because of this, the lifetime of the burst object hints to a driver how long it should
 *     remain in a high performance state.
 * (2) A burst object can preserve resources between executions. For example, a driver can map a
 *     memory object on the first execution and cache the mapping in the burst object for reuse in
 *     subsequent executions. Any cached resource can be released when the burst object is destroyed
 *     or when the NNAPI runtime notifies the burst object that the resource is no longer required.
 * (3) A burst object may be used for at most one execution at a time. This enables any transient
 *     execution resources such as intermediate tensors to be allocated once when the burst object
 *     is created and freed when the burst object is destroyed.
 *
 * This interface is thread-safe, and any class that implements this interface must be thread-safe.
 */
class IBurst {
   public:
    using OptionalCacheHold = std::shared_ptr<const base::ScopeGuard<std::function<void()>>>;

    /**
     * Cache a memory object in the burst.
     *
     * This can enable multiple executions that reuse the same memory to be more efficient.
     *
     * @param memory The memory object to be cached as long as CacheHandle is held.
     * @return An optional cache handle that will release the corresponding cahced object once the
     *     cache handle is released, or nullptr.
     */
    virtual OptionalCacheHold cacheMemory(const SharedMemory& memory) const = 0;

    /**
     * Performs a synchronous execution on a prepared model.
     *
     * At most one execution may occur on a burst object at any given time.
     *
     * The execution is performed synchronously with respect to the caller. IBurst::execute must
     * verify the inputs to the function are correct. If there is an error, IBurst::execute must
     * immediately return {@link ErrorStatus::INVALID_ARGUMENT} as a ExecutionError. If the inputs
     * to the function are valid and there is no error, IBurst::execute must perform the execution,
     * and must not return until the execution is complete.
     *
     * The caller must not change the content of any data object referenced by request (described by
     * the {@link DataLocation} of a {@link RequestArgument}) until IBurst::execute returns.
     * IBurst::execute must not change the content of any of the data objects corresponding to
     * request inputs.
     *
     * If the prepared model was prepared from a model wherein all tensor operands have fully
     * specified dimensions, and the inputs to the function are valid, and at execution time every
     * operation's input operands have legal values, then the execution should complete
     * successfully. There must be no failure unless the device itself is in a bad state.
     *
     * @param request The input and output information on which the prepared model is to be
     *     executed.
     * @param measure Specifies whether or not to measure duration of the execution.
     * @param deadline Optional time point. If provided, execute is expected to complete by this
     *     time point. If it is not able to be completed by the deadline, the execution may be
     *     aborted.
     * @param loopTimeoutDuration The maximum amount of time that should be spent executing a {@link
     *     OperationType::WHILE} operation. If a loop condition model does not output `false` within
     *     this duration, the execution must be aborted. If no loop timeout duration is provided,
     *     the maximum amount of time is {@link kControlFlowTimeoutDefault}. When provided, the
     *     duration must not exceed {@link kControlFlowTimeoutMaximum}.
     * @param hints Specifies the optional device specific execution hints. It is allowed for the
     *     driver to ignore the user-provided hints.
     * @param extensionNameToPrefix The mapping between extension names and prefixes of token
     *.    values.
     * @return A pair consisting of:
     *     - A list of shape information of model output operands. The index into "outputShapes"
     *       corresponds to the index of the output operand in the Request outputs vector.
     *       outputShapes must be empty unless the execution is successful or the ExecutionResult is
     *       {@link ErrorStatus::OUTPUT_INSUFFICIENT_SIZE}. outputShapes may be empty if the
     *       execution is successful and all model output operands are fully-specified at execution
     *       time. outputShapes must have the same number of elements as the number of model output
     *       operands if the ExecutionResult is {@link ErrorStatus::OUTPUT_INSUFFICIENT_SIZE}, or if
     *       the execution is successful and the model has at least one output operand that is not
     *       fully-specified.
     *     - Duration of execution. Unless measure is YES and the execution is successful, all times
     *       must be reported as std::nullopt. A driver may choose to report any time as
     *       std::nullopt, indicating that measurement is not available.
     */
    virtual ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> execute(
            const Request& request, MeasureTiming measure, const nn::OptionalTimePoint& deadline,
            const nn::OptionalDuration& loopTimeoutDuration,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    /**
     * Create a reusable burst execution object.
     *
     * IBurst::createReusableExecution must verify the inputs to the function are correct. If there
     * is an error, IBurst::createReusableExecution must immediately return {@link
     * ErrorStatus::INVALID_ARGUMENT} as a GeneralError. If the inputs to the function are valid and
     * there is no error, IBurst::createReusableExecution must construct a reusable execution.
     *
     * @param request The input and output information on which the prepared model is to be
     *     executed.
     * @param measure Specifies whether or not to measure duration of the computation.
     * @param loopTimeoutDuration The maximum amount of time that should be spent executing a {@link
     *     OperationType::WHILE} operation. If a loop condition model does not output `false` within
     *     this duration, the execution must be aborted. If no loop timeout duration is provided,
     *     the maximum amount of time is {@link kControlFlowTimeoutDefault}. When provided, the
     *     duration must not exceed {@link kControlFlowTimeoutMaximum}.
     * @param hints Specifies the optional device specific execution hints. The same token must not
     *     be repeated. It is allowed for the driver to ignore the user-provided hints.
     * @param extensionNameToPrefix The mapping between extension names and prefixes of token
     *.    values.
     * @return execution An IExecution object representing a reusable burst execution that has been
     *     specialized for a fixed request, otherwise GeneralError.
     */
    virtual GeneralResult<SharedExecution> createReusableExecution(
            const Request& request, MeasureTiming measure,
            const nn::OptionalDuration& loopTimeoutDuration,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    // Public virtual destructor to allow objects to be stored (and destroyed) as smart pointers.
    // E.g., std::unique_ptr<IBurst>.
    virtual ~IBurst() = default;

   protected:
    // Protect the non-destructor special member functions to prevent object slicing.
    IBurst() = default;
    IBurst(const IBurst&) = default;
    IBurst(IBurst&&) noexcept = default;
    IBurst& operator=(const IBurst&) = default;
    IBurst& operator=(IBurst&&) noexcept = default;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBURST_H
