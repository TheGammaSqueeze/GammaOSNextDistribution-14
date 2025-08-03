/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_CALLBACK_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_CALLBACK_H

#include <android-base/thread_annotations.h>
#include <nnapi/Types.h>

#include <condition_variable>
#include <functional>
#include <mutex>
#include <thread>
#include <vector>

namespace android::nn {

// This class used to be a HIDL callback class to receive the results of
// IDevice::execute* asynchronously. It's not used for this anymore.
//
// TODO(b/122316159): Replace ExecutionCallback and CallbackEvent with a new
// class like AsyncTaskEvent.
/**
 * The ExecutionCallback class is used to receive the results of the execution
 * from a task executing asynchronously with respect to the runtime. If a
 * calling thread calls wait or get* on a ExecutionCallback object and the
 * corresponding asynchronous task has not finished the execution, the calling
 * thread will block until the asynchronous task has called one of the notify*
 * methods.
 *
 * If the callback object is notified more than once, only the results of the
 * first call to notify* are used, and the results from subsequent calls are
 * discarded.
 */
class ExecutionCallback {
    using ExecutionFinish =
            std::function<ErrorStatus(ErrorStatus, const std::vector<OutputShape>&)>;

   public:
    /**
     * ExecutionCallback::notify marks the callback object with the results
     * (error status, dynamic output shapes, and timing information) of the
     * asynchronous execution that held this callback and enables all prior and
     * future wait calls on the ExecutionCallback object to proceed.
     *
     * If the callback object is notified more than once, only the results of
     * the first call to notify* are used, and the results from subsequent calls
     * are discarded.
     *
     * @param status Error status returned from launching the asynchronous task
     *     (if the launch fails) or from the asynchronous task itself (if the
     *     launch succeeds). Must be:
     *     - NONE if the asynchronous execution was successful
     *     - DEVICE_UNAVAILABLE if driver is offline or busy
     *     - GENERAL_FAILURE if the asynchronous task resulted in an unspecified
     *         error
     *     - OUTPUT_INSUFFICIENT_SIZE if at least one output operand buffer is
     *         not large enough to store the corresponding output
     *     - INVALID_ARGUMENT if one of the input arguments to prepareModel is
     *         invalid
     *     - MISSED_DEADLINE_* if the deadline could not be met
     *     - RESOURCE_EXHAUSTED_* if the execution was aborted by the driver
     * @param outputShapes A list of shape information of model output operands.
     *     The index into "outputShapes" corresponds to the index of the output
     *     operand in the Request outputs vector. outputShapes must be empty
     *     unless the status is either NONE or OUTPUT_INSUFFICIENT_SIZE.
     * @param Timing Duration of execution. Unless MeasureTiming::YES was passed
     *     when launching the execution and status is NONE, all times must be
     *     reported as UINT64_MAX. A driver may choose to report any time as
     *     UINT64_MAX, indicating that particular measurement is not available.
     */
    void notify(ErrorStatus status, const std::vector<OutputShape>& outputShapes,
                const Timing& timing);

    /**
     * ExecutionCallback::wait blocks until notify* has been called on the
     * callback object.
     */
    void wait() const;

    /**
     * Retrieves the error status returned from the asynchronous task launched
     * by IPreparedModel::execute* (but not by
     * IPreparedModel::executeSynchronously*). If IPreparedModel::execute* has
     * not finished asynchronously executing, this call will block until the
     * asynchronous task notifies the object.
     *
     * @return status Error status returned from launching the asynchronous task
     *     (if the launch fails) or from the asynchronous task itself (if the
     *     launch succeeds). Must be:
     *     - NONE if the asynchronous execution was successful
     *     - DEVICE_UNAVAILABLE if driver is offline or busy
     *     - GENERAL_FAILURE if the asynchronous task resulted in an unspecified
     *         error
     *     - OUTPUT_INSUFFICIENT_SIZE if at least one output operand buffer is
     *         not large enough to store the corresponding output
     *     - INVALID_ARGUMENT if one of the input arguments to prepareModel is
     *         invalid
     *     - MISSED_DEADLINE_* if the deadline could not be met
     *     - RESOURCE_EXHAUSTED_* if the task was aborted by the driver
     *     - DEAD_OBJECT if the driver crashed without returning a result
     */
    ErrorStatus getStatus() const;

    /**
     * Retrieves the output shapes returned from the asynchronous task launched
     * by either IPreparedModel::execute_1_2 or IPreparedModel::execute_1_3. If
     * IPreparedModel::execute_1_2 or IPreparedModel::execute_1_3 has not
     * finished asynchronously executing, this call will block until the
     * asynchronous task notifies the object.
     *
     * If the asynchronous task was launched by IPreparedModel::execute, an
     * empty vector will be returned.
     *
     * @return outputShapes A list of shape information of model output
     *     operands. The index into "outputShapes" corresponds to the index of
     *     the output operand in the Request outputs vector. outputShapes must
     *     be empty unless the status is either NONE or
     *     OUTPUT_INSUFFICIENT_SIZE. outputShaps may be empty if the status is
     *     NONE and all model output operands are fully-specified at execution
     *     time. outputShapes must have the same number of elements as the
     *     number of model output operands if the status is
     *     OUTPUT_INSUFFICIENT_SIZE, or if the status is NONE and the model has
     *     at least one output operand that is not fully-specified.
     */
    const std::vector<OutputShape>& getOutputShapes() const;

    /**
     * Retrieves the duration of execution of the asynchronous task launched by
     * by either IPreparedModel::execute_1_2 or IPreparedModel::execute_1_3. If
     * IPreparedModel::execute_1_2 or IPreparedModel::execute_1_3 has not
     * finished asynchronously executing, this call will block until the
     * asynchronous task notifies the object.
     *
     * If the asynchronous task was launched by IPreparedModel::execute, every
     * time must be UINT64_MAX.
     *
     * @return timing Duration of the execution. Every time must be UINT64_MAX
     *     unless the status is NONE.
     */
    Timing getTiming() const;

    /**
     * ExecutionCallback::bindThread binds a thread to the ExecutionCallback
     * object. The bound thread is later joined by ExecutionCallback::wait or
     * ExecutionCallback::get*.
     *
     * Once a thread is bound with ExecutionCallback::bindThread, the client
     * code must ensure that ExecutionCallback::wait or ExecutionCallback::get*
     * has been called before the ExecutionCallback object is destroyed.
     *
     * The bound thread must not call any ExecutionCallback method with the
     * exception of ExecutionCallback::notify*, which it must call when the
     * thread has finished its computation.
     *
     * ExecutionCallback::bindThread can be called at most once on a given
     * callback object.
     *
     * @param asyncThread Thread to be bound to the callback object. The thread
     *     object must represent a thread of execution -- i.e.,
     *     std::thread::joinable() must be true.
     * @return bool True if successful, false if thread was not properly bound.
     */
    bool bindThread(std::thread asyncThread);

    /**
     * ExecutionCallback::setOnFinish binds a callback to the ExecutionCallback
     * object that will be executed during one of the ExecutionCallback::notify*
     * calls but before any calls to wait or get* return. This provided callback
     * is provided with both the ErrorStatus and the output shapes from
     * ExecutionCallback::notify*.
     *
     * The bound function must not synchronize with or otherwise access the
     * callback object it is bound to, as this could cause a deadlock.
     *
     * This call will not bind the provided callback if any of the following
     * occur:
     * (1) the provided callback is invalid (i.e., "(bool) finish" is false)
     * (2) ExecutionCallback already contains a bound callback
     * (3) ExecutionCallback has already been notified with results
     *
     * @param finish Callback to be executed when ExecutionCallback is notified
     *     with results.
     */
    void setOnFinish(const ExecutionFinish& finish);

   private:
    /*
     * ExecutionCallback::notifyInternal stores the results of the execution
     * (status, output shapes, and timing information) in the ExecutionCallback
     * object and invokes the bound callback function "mOnFinish" (if present)
     * before any call to wait or get* return. It then enables all prior and
     * future wait calls on the ExecutionCallback object to proceed.
     */
    void notifyInternal(ErrorStatus errorStatus, std::vector<OutputShape> outputShapes,
                        Timing timing);

    // members
    mutable std::mutex mMutex;
    mutable std::condition_variable mCondition;
    mutable std::thread mThread GUARDED_BY(mMutex);
    ExecutionFinish mOnFinish GUARDED_BY(mMutex);
    bool mNotified GUARDED_BY(mMutex) = false;
    ErrorStatus mErrorStatus = ErrorStatus::GENERAL_FAILURE;
    std::vector<OutputShape> mOutputShapes;
    Timing mTiming = {};
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_CALLBACK_H
