/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IEXECUTION_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IEXECUTION_H

#include <utility>
#include <vector>

#include "nnapi/Types.h"

namespace android::nn {

/**
 * IExecution describes a reusable execution with request and several other execution configurations
 * fixed. It is used to launch computations.
 *
 * IExecution manages a sequence of computations of the same prepared model with the same request
 * and configurations. An IExecution object is used to control a set of computation, and to preserve
 * resources between computations, enabling computations to have lower overhead. IExecution objects
 * enable some optimizations:
 * (1) An IExecution object can preserve resources between computations. For example, a driver can
 *     map a memory object when the IExecution object is created and cache the mapping for reuse in
 *     subsequent computations. Any cached resource can be released when the IExecution object is
 *     destroyed.
 * (2) An IExecution object may be used for at most one computation at a time. This enables any
 *     transient computation resources such as intermediate tensors to be allocated once when the
 *     IExecution object is created and freed when the IExecution object is destroyed.
 * (3) An IExecution object is created for a fixed request. This enables the implementation to apply
 *     request-specific optimizations. For example, an implementation can avoid request validation
 *     and conversions when the IExecution object is reused. An implementation may also choose to
 *     specialize the dynamic tensor shapes in a prepared model according to the request.
 *
 * This interface is thread-safe, and any class that implements this interface must be thread-safe.
 */
class IExecution {
   public:
    /**
     * Performs a synchronous computation on a reusable execution.
     *
     * At most one computation may occur on a execution object at any given time, either by means of
     * IExecution::compute or IExecution::computeFenced.
     *
     * The computation is performed synchronously with respect to the caller.
     * IExecution::compute must verify the inputs to the function are correct. If there is an
     * error, IExecution::compute must immediately return {@link ErrorStatus::INVALID_ARGUMENT}
     * as a ExecutionError. If the inputs to the function are valid and there is no error,
     * IExecution::compute must perform the computation, and must not return until the computation
     * is complete.
     *
     * The caller must not change the content of any data object referenced by the request provided
     * in IPreparedModel::createReusableExecution (described by the {@link DataLocation} of a {@link
     * RequestArgument}) until IExecution::compute returns. IExecution::compute must not change the
     * content of any of the data objects corresponding to request inputs.
     *
     * If the prepared model that the execution is created from was prepared from a model wherein
     * all tensor operands have fully specified dimensions, and the inputs to the function are
     * valid, and at computation time every operation's input operands have legal values, then the
     * computation should complete successfully. There must be no failure unless the device itself
     * is in a bad state.
     *
     * IExecution::compute may be called with an optional deadline. If the computation is not
     * able to be completed before the provided deadline, the computation may be aborted, and either
     * {@link ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link
     * ErrorStatus::MISSED_DEADLINE_PERSISTENT} may be returned as a ExecutionError.
     *
     * @param deadline Optional time point. If provided, compute is expected to complete by this
     *     time point. If it is not able to be completed by the deadline, the computation may be
     *     aborted.
     * @return A pair consisting of:
     *     - A list of shape information of model output operands. The index into "outputShapes"
     *       corresponds to the index of the output operand in the Request outputs vector.
     *       outputShapes must be empty unless the computation is successful or the ExecutionResult
     *       is {@link ErrorStatus::OUTPUT_INSUFFICIENT_SIZE}. outputShapes may be empty if the
     *       computation is successful and all model output operands are fully-specified at
     *       computation time. outputShapes must have the same number of elements as the number of
     *       model output operands if the ExecutionResult is
     *       {@link ErrorStatus::OUTPUT_INSUFFICIENT_SIZE}, or if the computation is successful and
     *       the model has at least one output operand that is not fully-specified.
     *     - Duration of computation. Unless measure is YES and the computation is successful, all
     *       times must be reported as UINT64_MAX. A driver may choose to report any time as
     *       UINT64_MAX, indicating that measurement is not available.
     */
    virtual ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> compute(
            const OptionalTimePoint& deadline) const = 0;

    /**
     * Launch a fenced asynchronous computation on a reusabled execution.
     *
     * At most one computation may occur on a execution object at any given time, either by means of
     * IExecution::compute or IExecution::computeFenced.
     *
     * The computation is performed asynchronously with respect to the caller.
     * IExecution::computeFenced must verify its inputs are correct, and the usages of memory
     * pools allocated by IDevice::allocate are valid. If there is an error,
     * IExecution::computeFenced must immediately return {@link ErrorStatus::INVALID_ARGUMENT}
     * as a GeneralError. If the inputs to the function are valid and there is no error,
     * IExecution::computeFenced must dispatch an asynchronous task to perform the computation in
     * the background, and immediately return with a sync fence that will be signaled once the
     * computation is completed and a callback that can be used by the client to query the duration
     * and runtime error status. If the task has finished before the call returns, an empty handle
     * may be returned for syncFence. The computation must wait for all the sync fences (if any) in
     * waitFor to be signaled before starting the actual computation.
     *
     * When the asynchronous task has finished its computation, it must immediately signal the
     * syncFence returned from the IExecution::computeFenced call. After the syncFence is
     * signaled, the task must not modify the content of any data object referenced by request
     * (described by the {@link DataLocation} of a {@link Request::Argument}).
     *
     * IExecution::computeFenced may be called with an optional deadline and an optional
     * duration. If the computation is not able to be completed before the provided deadline or
     * within the timeout duration (measured from when all sync fences in waitFor are signaled),
     * whichever comes earlier, the computation may be aborted, and either {@link
     * ErrorStatus::MISSED_DEADLINE_TRANSIENT} or {@link ErrorStatus::MISSED_DEADLINE_PERSISTENT}
     * may be returned as an GeneralError. The error due to an abort must be sent the same way as
     * other errors, described above.
     *
     * If any of the sync fences in waitFor changes to error status after the
     * IExecution::computeFenced call succeeds, or the computation is aborted because it cannot
     * finish before the deadline has been reached or the duration has elapsed, the driver must
     * immediately set the returned syncFence to error status.
     *
     * @param waitFor A vector of sync fence file descriptors. The computation must wait for all
     *     sync fence to be signaled before starting the task.
     * @param deadline The time by which computation is expected to complete. If the computation
     *     cannot be finished by the deadline, the computation may be aborted.
     * @param timeoutDurationAfterFence The timeout duration within which the computation is
     *     expected to complete after all sync fences in waitFor are signaled.
     * @return A pair consisting of:
     *     - A syncFence that will be triggered when the task is completed. The syncFence will be
     *       set to error if critical error occurs when doing actual evaluation.
     *     - A callback can be used to query information like duration and detailed runtime error
     *       status when the task is completed.
     */
    virtual GeneralResult<std::pair<SyncFence, ExecuteFencedInfoCallback>> computeFenced(
            const std::vector<SyncFence>& waitFor, const OptionalTimePoint& deadline,
            const OptionalDuration& timeoutDurationAfterFence) const = 0;

    // Public virtual destructor to allow objects to be stored (and destroyed) as smart pointers.
    // E.g., std::unique_ptr<IExecution>.
    virtual ~IExecution() = default;

   protected:
    // Protect the non-destructor special member functions to prevent object slicing.
    IExecution() = default;
    IExecution(const IExecution&) = default;
    IExecution(IExecution&&) noexcept = default;
    IExecution& operator=(const IExecution&) = default;
    IExecution& operator=(IExecution&&) noexcept = default;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IEXECUTION_H
