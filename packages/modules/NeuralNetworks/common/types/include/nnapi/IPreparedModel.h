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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IPREPARED_MODEL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IPREPARED_MODEL_H

#include <any>
#include <functional>
#include <memory>
#include <utility>
#include <vector>

#include "nnapi/Types.h"

namespace android::nn {

/**
 * IPreparedModel describes a model that has been prepared for execution and is used to launch
 * executions.
 *
 * This interface is thread-safe, and any class that implements this interface must be thread-safe.
 */
class IPreparedModel {
   public:
    /**
     * Performs a synchronous execution on a prepared model.
     *
     * The execution is performed synchronously with respect to the caller. IPreparedModel::execute
     * must verify the inputs to the function are correct. If there is an error,
     * IPreparedModel::execute must immediately return {@link ErrorStatus::INVALID_ARGUMENT} as a
     * ExecutionError. If the inputs to the function are valid and there is no error,
     * IPreparedModel::execute must perform the execution, and must not return until the execution
     * is complete.
     *
     * The caller must not change the content of any data object referenced by request (described by
     * the {@link DataLocation} of a {@link RequestArgument}) until IPreparedModel::execute returns.
     * IPreparedModel::execute must not change the content of any of the data objects corresponding
     * to request inputs.
     *
     * If the prepared model was prepared from a model wherein all tensor operands have fully
     * specified dimensions, and the inputs to the function are valid, and at execution time every
     * operation's input operands have legal values, then the execution should complete
     * successfully. There must be no failure unless the device itself is in a bad state.
     *
     * IPreparedModel::execute may be called with an optional deadline. If the execution is not
     * able to be completed before the provided deadline, the execution may be aborted, and either
     * {@link ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link
     * ErrorStatus::MISSED_DEADLINE_PERSISTENT} may be returned as a ExecutionError.
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
     * @param hints Specifies the optional device specific execution hints. The same token must not
     *     be repeated. It is allowed for the driver to ignore the user-provided hints.
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
     *       must be reported as UINT64_MAX. A driver may choose to report any time as UINT64_MAX,
     *       indicating that measurement is not available.
     */
    virtual ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> execute(
            const Request& request, MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    /**
     * Launch a fenced asynchronous execution on a prepared model.
     *
     * The execution is performed asynchronously with respect to the caller.
     * IPreparedModel::executeFenced must verify its inputs are correct, and the usages of memory
     * pools allocated by IDevice::allocate are valid. If there is an error,
     * IPreparedModel::executeFenced must immediately return {@link ErrorStatus::INVALID_ARGUMENT}
     * as a GeneralError. If the inputs to the function are valid and there is no error,
     * IPreparedModel::executeFenced must dispatch an asynchronous task to perform the execution in
     * the background, and immediately return with a sync fence that will be signaled once the
     * execution is completed and a callback that can be used by the client to query the duration
     * and runtime error status. If the task has finished before the call returns, an empty handle
     * may be returned for syncFence. The execution must wait for all the sync fences (if any) in
     * waitFor to be signaled before starting the actual execution.
     *
     * When the asynchronous task has finished its execution, it must immediately signal the
     * syncFence returned from the IPreparedModel::executeFenced call. After the syncFence is
     * signaled, the task must not modify the content of any data object referenced by request
     * (described by the {@link DataLocation} of a {@link Request::Argument}).
     *
     * IPreparedModel::executeFenced may be called with an optional deadline and an optional
     * duration. If the execution is not able to be completed before the provided deadline or within
     * the timeout duration (measured from when all sync fences in waitFor are signaled), whichever
     * comes earlier, the execution may be aborted, and either {@link
     * ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link ErrorStatus::MISSED_DEADLINE_PERSISTENT}
     * may be returned as an GeneralError. The error due to an abort must be sent the same way as
     * other errors, described above.
     *
     * If any of the sync fences in waitFor changes to error status after the
     * IPreparedModel::executeFenced call succeeds, or the execution is aborted because it cannot
     * finish before the deadline has been reached or the duration has elapsed, the driver must
     * immediately set the returned syncFence to error status.
     *
     * @param request The input and output information on which the prepared model is to be
     *     executed.
     * @param waitFor A vector of sync fence file descriptors. The execution must wait for all sync
     *     fence to be signaled before starting the task.
     * @param measure Specifies whether or not to measure duration of the execution.
     * @param deadline The time by which execution is expected to complete. If the execution cannot
     *     be finished by the deadline, the execution may be aborted.
     * @param loopTimeoutDuration The maximum amount of time that should be spent executing a {@link
     *     OperationType::WHILE} operation. If a loop condition model does not output `false` within
     *     this duration, the execution must be aborted. If no loop timeout duration is provided,
     *     the maximum amount of time is {@link kControlFlowTimeoutDefault}. When provided, the
     *     duration must not exceed {@link kControlFlowTimeoutMaximum}.
     * @param timeoutDurationAfterFence The timeout duration within which the execution is expected
     *     to complete after all sync fences in waitFor are signaled.
     * @param hints Specifies the optional device specific execution hints. The same token must not
     *     be repeated. It is allowed for the driver to ignore the user-provided hints.
     * @param extensionNameToPrefix The mapping between extension names and prefixes of token
     *.    values.
     * @return A pair consisting of:
     *     - A syncFence that will be triggered when the task is completed. The syncFence will be
     *       set to error if critical error occurs when doing actual evaluation.
     *     - A callback can be used to query information like duration and detailed runtime error
     *       status when the task is completed.
     */
    virtual GeneralResult<std::pair<SyncFence, ExecuteFencedInfoCallback>> executeFenced(
            const Request& request, const std::vector<SyncFence>& waitFor, MeasureTiming measure,
            const OptionalTimePoint& deadline, const OptionalDuration& loopTimeoutDuration,
            const OptionalDuration& timeoutDurationAfterFence,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    /**
     * Create a reusable execution from a request and execution configurations.
     *
     * IPreparedModel::createReusableExecution must verify the inputs to the function are correct.
     * If there is an error, IPreparedModel::createReusableExecution must immediately return
     * {@link ErrorStatus::INVALID_ARGUMENT} as a GeneralError. If the inputs to the function are
     * valid and there is no error, IPreparedModel::createReusableExecution must construct a
     * reusable execution.
     *
     * @param request The input and output information on which the prepared model is to be
     *     executed.
     * @param measure Specifies whether or not to measure duration of the computations performed
     *     with the returned execution.
     * @param loopTimeoutDuration The maximum amount of time that should be spent executing a {@link
     *     OperationType::WHILE} operation. During a computation with the returned execution, if a
     *     loop condition model does not output `false` within this duration, the computation must
     *     be aborted. If no loop timeout duration is provided, the maximum amount of time is
     *     {@link LoopTimeoutDurationNs::DEFAULT}. When provided, the duration must not exceed
     *     {@link LoopTimeoutDurationNs::MAXIMUM}.
     * @param hints Specifies the optional device specific execution hints. It is allowed for the
     *     driver to ignore the user-provided hints.
     * @param extensionNameToPrefix The mapping between extension names and prefixes of token
     *.    values.
     * @return execution An IExecution object representing a reusable execution that has been
     *     specialized for a fixed request, otherwise GeneralError.
     */
    virtual GeneralResult<SharedExecution> createReusableExecution(
            const Request& request, MeasureTiming measure,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<nn::TokenValuePair>& hints,
            const std::vector<nn::ExtensionNameAndPrefix>& extensionNameToPrefix) const = 0;

    /**
     * Creates a burst controller on a prepared model.
     *
     * @return ExecutionBurstController Execution burst controller object, otherwise GeneralError.
     */
    virtual GeneralResult<SharedBurst> configureExecutionBurst() const = 0;

    /**
     * Return the resource that the IPreparedModel wraps, or any empty std::any.
     *
     * This method is used for IDevice::allocate.
     *
     * @return std::any containing the underlying resource.
     */
    virtual std::any getUnderlyingResource() const = 0;

    // Public virtual destructor to allow objects to be stored (and destroyed) as smart pointers.
    // E.g., std::unique_ptr<IPreparedModel>.
    virtual ~IPreparedModel() = default;

   protected:
    // Protect the non-destructor special member functions to prevent object slicing.
    IPreparedModel() = default;
    IPreparedModel(const IPreparedModel&) = default;
    IPreparedModel(IPreparedModel&&) noexcept = default;
    IPreparedModel& operator=(const IPreparedModel&) = default;
    IPreparedModel& operator=(IPreparedModel&&) noexcept = default;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IPREPARED_MODEL_H
