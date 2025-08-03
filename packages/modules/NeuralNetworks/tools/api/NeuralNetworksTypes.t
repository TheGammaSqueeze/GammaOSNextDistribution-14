%% template file for generating NeuralNetworksTypes.h.
%% see README.md.
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

/**
 * @addtogroup NeuralNetworks
 * @{
 */

/**
 * @file NeuralNetworksTypes.h
 */

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_NEURAL_NETWORKS_TYPES_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_NEURAL_NETWORKS_TYPES_H

/******************************************************************
 *
 * IMPORTANT NOTICE:
 *
 *   This file is part of Android's set of stable system headers
 *   exposed by the Android NDK (Native Development Kit).
 *
 *   Third-party source AND binary code relies on the definitions
 *   here to be FROZEN ON ALL UPCOMING PLATFORM RELEASES.
 *
 *   - DO NOT MODIFY ENUMS (EXCEPT IF YOU ADD NEW 32-BIT VALUES)
 *   - DO NOT MODIFY CONSTANTS OR FUNCTIONAL MACROS
 *   - DO NOT CHANGE THE SIGNATURE OF FUNCTIONS IN ANY WAY
 *   - DO NOT CHANGE THE LAYOUT OR SIZE OF STRUCTURES
 */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/cdefs.h>

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#endif  // __ANDROID__

__BEGIN_DECLS

%insert Operand_1.0_Comment
typedef enum {
%insert Operand_1.0
%insert Operand_1.2
%insert Operand_1.3
} OperandCode;

%insert Operation_1.0_Comment
typedef enum {
    // Operations below are available since NNAPI feature level 1.

%insert Operation_1.0

    // Operations below are available since NNAPI feature level 2.

%insert Operation_1.1

    // Operations below are available since NNAPI feature level 3.

%insert Operation_1.2

    // Operations below are available since NNAPI feature level 4.

%insert Operation_1.3

    // Operations below are available since NNAPI feature level 6.

%insert Operation_fl6

    // Operations below are available since NNAPI feature level 7.

%insert Operation_fl7
} OperationCode;

%insert FusedActivationFunc

/**
 * Implicit padding algorithms.
 *
 *
 * Available since NNAPI feature level 1.
 */
typedef enum {
    /**
     * SAME padding.
     * Padding on both ends are the "same":
     *     padding_to_beginning =  total_padding / 2
     *     padding_to_end       = (total_padding + 1)/2.
     * i.e., for even number of padding, padding to both ends are exactly
     * the same; for odd number of padding, padding to the ending is bigger
     * than the padding to the beginning by 1.
     *
     * total_padding is a function of input, stride, dilation and filter size.
     * It could be computed as follows:
     *    out_size = (input + stride - 1) / stride
     *    effective_filter_size = (filter_size - 1) * dilation + 1
     *    needed_input = (out_size - 1) * stride + effective_filter_size
     *    total_padding = max(0, needed_input - input_size)
     *  The computation is the same for the horizontal and vertical directions.
     */
    ANEURALNETWORKS_PADDING_SAME = 1,

    /**
     * VALID padding.
     * No padding. When the input size is not evenly divisible by
     * the filter size, the input at the end that could not fill
     * the whole filter tile will simply be ignored.
     */
    ANEURALNETWORKS_PADDING_VALID = 2,
} PaddingCode;

%insert ExecutionPreference

%insert DeviceType

/**
 * NNAPI feature levels.
 *
 * Each update of the NNAPI specification yields a new NNAPI feature level enum value.
 * NNAPI feature level corrseponds to an NNAPI specification version that a driver
 * and/or the NNAPI runtime can implement.
 *
 * A feature level up to and including "FEATURE_LEVEL_5" maps directly to
 * the Android API level that introduced the corresponding update of the NNAPI
 * specification. Feature levels after Android API level 31 have no association with
 * API level because the NNAPI specification can be updated between Android API
 * releases. Outputs of {@link ANeuralNetworksDevice_getFeatureLevel} and
 * {@link ANeuralNetworks_getRuntimeFeatureLevel} must be compared against
 * these enum values instead of the Android API level.
 */
typedef enum {
    /** NNAPI specification available in Android O-MR1, Android NNAPI feature level 1 */
    ANEURALNETWORKS_FEATURE_LEVEL_1 = 27,
    /** NNAPI specification available in Android P, Android NNAPI feature level 2 */
    ANEURALNETWORKS_FEATURE_LEVEL_2 = 28,
    /** NNAPI specification available in Android Q, Android NNAPI feature level 3 */
    ANEURALNETWORKS_FEATURE_LEVEL_3 = 29,
    /** NNAPI specification available in Android R, Android NNAPI feature level 4 */
    ANEURALNETWORKS_FEATURE_LEVEL_4 = 30,
    /**
     * NNAPI specification available in Android S, Android NNAPI feature level 5.
     * After Android S, the NNAPI specification can be updated between Android
     * API releases.
     */
    ANEURALNETWORKS_FEATURE_LEVEL_5 = 31,
    /** Android NNAPI feature level 6 */
    ANEURALNETWORKS_FEATURE_LEVEL_6 = 1000006,
    /** Android NNAPI feature level 7 */
    ANEURALNETWORKS_FEATURE_LEVEL_7 = 1000007,
    /** Android NNAPI feature level 8 */
    ANEURALNETWORKS_FEATURE_LEVEL_8 = 1000008,
} FeatureLevelCode;

/**
 * Result codes.
 *
 * <p>Any NNAPI function can return any result code, including result codes not
 * currently documented. Any value other than {@link ANEURALNETWORKS_NO_ERROR}
 * indicates a failure of some kind.</p>
 *
 * <p>Additional information about the nature of a failure can be obtained from
 * the device log after enabling NNAPI debugging by setting the debug.nn.vlog
 * property to 1, e.g., by calling "adb shell setprop debug.nn.vlog 1".</p>
 *
 * Available since NNAPI feature level 1.
 */
typedef enum {
    /**
     * Operation was successful.
     */
    ANEURALNETWORKS_NO_ERROR = 0,

    /**
     * Failure caused by not enough available memory.
     */
    ANEURALNETWORKS_OUT_OF_MEMORY = 1,

    ANEURALNETWORKS_INCOMPLETE = 2,

    /**
     * Failure caused by unexpected null argument.
     */
    ANEURALNETWORKS_UNEXPECTED_NULL = 3,

    /**
     * Failure caused by invalid function arguments, invalid model definition,
     * invalid execution definition or invalid data at execution time.
     */
    ANEURALNETWORKS_BAD_DATA = 4,

    /**
     * Failure caused by failed model execution.
     */
    ANEURALNETWORKS_OP_FAILED = 5,

    /**
     * Failure caused by object being in the wrong state.
     */
    ANEURALNETWORKS_BAD_STATE = 6,

    /**
     * Failure caused by not being able to map a file into memory.
     * This may be caused by a file descriptor not being mappable, or an AHardwareBuffer
     * not supported by the device.
     * Mitigate by reading its content into memory.
     */
    ANEURALNETWORKS_UNMAPPABLE = 7,

    /**
     * Failure caused by insufficient buffer size provided to a model output.
     */
    ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE = 8,

    /**
     * Failure caused by a device not being available.
     */
    ANEURALNETWORKS_UNAVAILABLE_DEVICE = 9,

    /**
     * Failure because a deadline could not be met for a task, but future
     * deadlines may still be met for the same task after a short delay.
     *
     * Available since NNAPI feature level 4.
     */
    ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT = 10,

    /**
     * Failure because a deadline could not be met for a task, and future
     * deadlines will likely also not be met for the same task even after a
     * short delay.
     *
     * Available since NNAPI feature level 4.
     */
    ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT = 11,

    /**
     * Failure because of a resource limitation within the driver, but future
     * calls for the same task may still succeed after a short delay.
     *
     * Available since NNAPI feature level 4.
     */
    ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT = 12,

    /**
     * Failure because of a resource limitation within the driver, and future
     * calls for the same task will likely also fail even after a short
     * delay.
     *
     * Available since NNAPI feature level 4.
     */
    ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT = 13,

    /**
     * Failure indicating an object is in a dead state.
     *
     * Available since NNAPI feature level 4.
     */
    ANEURALNETWORKS_DEAD_OBJECT = 14,
} ResultCode;

/**
 * For {@link ANeuralNetworksModel_setOperandValue}, values with a
 * length smaller or equal to this will be immediately copied into
 * the model. The size is in bytes.
 *
 * Available since NNAPI feature level 1.
 */
enum { ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES = 128 };

/**
 * For {@link ANeuralNetworksCompilation_setCaching}, specify the size
 * of the cache token required from the application. The size is in bytes.
 *
 * Available since NNAPI feature level 3.
 */
enum { ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN = 32 };

/**
 * Different duration measurements.
 *
 * Durations are measured in nanoseconds.
 *
 * Available since NNAPI feature level 3.
 */
typedef enum {
    // Execution time on hardware (not driver, which runs on host processor).
    ANEURALNETWORKS_DURATION_ON_HARDWARE = 0,
    // Execution time in driver (including time on hardware).  Excludes overhead
    // such as that of the runtime itself and the IPC needed for the runtime to
    // communicate with the driver.
    ANEURALNETWORKS_DURATION_IN_DRIVER = 1,
    // Execution time on hardware, after all dependencies have been signaled.
    // If no dependencies specified (for example, if the execution was scheduled other
    // than with {@link ANeuralNetworksExecution_startComputeWithDependencies}), the
    // reported time will be the same as ANEURALNETWORKS_DURATION_ON_HARDWARE.
    // Available since NNAPI feature level 4.
    ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE = 2,
    // Execution time in driver, after all dependencies have been signaled. Excludes
    // overhead such as that of the runtime itself and the IPC needed for the runtime
    // to communicate with the driver.
    // If no dependencies specified (for example, if the execution was scheduled other
    // than with {@link ANeuralNetworksExecution_startComputeWithDependencies}), the
    // reported time will be the same as ANEURALNETWORKS_DURATION_IN_DRIVER.
    // Available since NNAPI feature level 4.
    ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER = 3,
} DurationCode;

%insert Priority

/**
 * ANeuralNetworksMemory is an opaque type that represents memory.
 *
 * This type is used to represent shared memory, memory mapped files,
 * and similar memories.
 *
 * By using shared memory, a program can efficiently communicate to the
 * runtime and drivers the tensors that define a model. See
 * {@link ANeuralNetworksModel_setOperandValueFromMemory}. An application
 * should typically create one shared memory object that contains every constant tensor
 * needed to define a model. {@link ANeuralNetworksMemory_createFromFd} can be used to
 * create shared memory from a file handle.
 * {@link ANeuralNetworksMemory_createFromAHardwareBuffer} can be used to
 * create shared memory from an AHardwareBuffer handle.
 *
 * Memory objects can also be used to specify the input and output arguments of
 * an execution. See {@link ANeuralNetworksExecution_setInputFromMemory}
 * and {@link ANeuralNetworksExecution_setOutputFromMemory}.
 *
 * When calling {@link ANeuralNetworksModel_setOperandValueFromMemory},
 * {@link ANeuralNetworksExecution_setInputFromMemory} and
 * {@link ANeuralNetworksExecution_setOutputFromMemory}, each operand in the shared
 * memory object must be aligned on a boundary of a byte size that is a multiple
 * of the element type byte size, e.g., a tensor with
 * {@link ANEURALNETWORKS_TENSOR_FLOAT32} type must be aligned on 4-byte boundary.
 *
 * It is the application's responsibility to ensure that there are no uses of
 * the memory after calling {@link ANeuralNetworksMemory_free}. This includes
 * any model which references this memory because of a call to
 * {@link ANeuralNetworksModel_setOperandValueFromMemory}, any compilation
 * created using such a model, any execution object or burst object created
 * using such a compilation, or any execution which references this memory
 * because of a call to {@link ANeuralNetworksExecution_setInputFromMemory} or
 * {@link ANeuralNetworksExecution_setOutputFromMemory}.
 *
 * Available since NNAPI feature level 1.
 *
 * Starting at NNAPI feature level 4, the application may request creation of device native memory
 * from {@link ANeuralNetworksMemoryDesc} to avoid potential memory copying and transformation
 * overhead between executions. See also {@link ANeuralNetworksMemoryDesc} and
 * {@link ANeuralNetworksMemory_createFromDesc}.
 */
typedef struct ANeuralNetworksMemory ANeuralNetworksMemory;

/**
 * ANeuralNetworksModel is an opaque type that contains a description of the
 * mathematical operations that constitute the model.
 *
 * <p>Build the model by calling<ul>
 * <li>{@link ANeuralNetworksModel_create}</li>
 * <li>{@link ANeuralNetworksModel_addOperation}</li>
 * <li>{@link ANeuralNetworksModel_addOperand}</li>
 * </ul>
 *
 * This forms a graph in which each operation and operand is a node, a
 * directed edge from an operand to an operation indicates that the
 * operand is an input to the operation, and a directed edge from an
 * operation to an operand indicates that the operand is an output
 * from the operation. This graph must be acyclic.
 *
 * A model is completed by calling {@link ANeuralNetworksModel_finish}.
 * A model is destroyed by calling {@link ANeuralNetworksModel_free}.
 *
 * <p>A model cannot be modified once {@link ANeuralNetworksModel_finish}
 * has been called on it.</p>
 *
 * <p>It is the application's responsibility to make sure that only one thread
 * modifies a model at a given time. It is however safe for more than one
 * thread to use the model once {@link ANeuralNetworksModel_finish} has returned.</p>
 *
 * <p>It is also the application's responsibility to ensure that there are no
 * other uses of the model after calling {@link ANeuralNetworksModel_free}.
 * This includes any compilation, execution object or burst object created using
 * the model.</p>
 *
 * Available since NNAPI feature level 1.
 */
typedef struct ANeuralNetworksModel ANeuralNetworksModel;

/**
 * ANeuralNetworksCompilation is an opaque type that can be used to compile
 * a machine learning model.
 *
 * <p>To use:<ul>
 *    <li>Create a new compilation instance by calling the
 *        {@link ANeuralNetworksCompilation_create} function or
 *        {@link ANeuralNetworksCompilation_createForDevices}.</li>
 *    <li>Set any desired properties on the compilation (for example,
 *        {@link ANeuralNetworksCompilation_setPreference}).</li>
 *    <li>Optionally, set the caching signature and the cache directory on the
 *        compilation by calling {@link ANeuralNetworksCompilation_setCaching}.</li>
 *    <li>Complete the compilation with {@link ANeuralNetworksCompilation_finish}.</li>
 *    <li>Use the compilation as many times as needed
 *        with {@link ANeuralNetworksExecution_create} and
 *        {@link ANeuralNetworksBurst_create}.</li>
 *    <li>Destroy the compilation with {@link ANeuralNetworksCompilation_free}
 *        once all executions using the compilation have completed.</li></ul></p>
 *
 * A compilation is completed by calling {@link ANeuralNetworksCompilation_finish}.
 * A compilation is destroyed by calling {@link ANeuralNetworksCompilation_free}.
 *
 * <p>A compilation cannot be modified once {@link ANeuralNetworksCompilation_finish}
 * has been called on it.</p>
 *
 * <p>It is the application's responsibility to make sure that only
 * one thread modifies a compilation at a given time. It is however
 * safe for more than one thread to use the compilation once
 * {@link ANeuralNetworksCompilation_finish} has returned.</p>
 *
 * <p>It is also the application's responsibility to ensure that there are no other
 * uses of the compilation after calling {@link ANeuralNetworksCompilation_free}.
 * This includes any execution object or burst object created using the compilation,
 * or any memory descriptor with the compilation as part of one of the roles specified by
 * {@link ANeuralNetworksMemoryDesc_addInputRole} or
 * {@link ANeuralNetworksMemoryDesc_addOutputRole}.</p>
 *
 * Available since NNAPI feature level 1.
 */
typedef struct ANeuralNetworksCompilation ANeuralNetworksCompilation;

/**
 * ANeuralNetworksExecution is an opaque type that can be used to apply a machine
 * learning model to a set of inputs.
 *
 * <p>To use:<ul>
 *    <li>Create a new execution instance by calling the
 *        {@link ANeuralNetworksExecution_create} function.</li>
 *    <li>Associate input buffers or memory regions to the model inputs with
 *        {@link ANeuralNetworksExecution_setInput} or
 *        {@link ANeuralNetworksExecution_setInputFromMemory}.</li>
 *    <li>Associate output buffers or memory regions to the model outputs with
 *        {@link ANeuralNetworksExecution_setOutput} or
 *        {@link ANeuralNetworksExecution_setOutputFromMemory}.</li>
 *    <li>Optionally, configure the execution with
 *        {@link ANeuralNetworksExecution_setLoopTimeout},
 *        {@link ANeuralNetworksExecution_setMeasureTiming},
 *        {@link ANeuralNetworksExecution_setReusable}, or
 *        {@link ANeuralNetworksExecution_setTimeout}.
 *    <li>Apply the model with one of the following:</li><ul>
 *        <li>Asynchronously with {@link ANeuralNetworksExecution_startCompute}
 *            or with {@link ANeuralNetworksExecution_startComputeWithDependencies},
 *            waiting for the execution to complete with
 *            {@link ANeuralNetworksEvent_wait}.</li>
 *        <li>Synchronously with {@link ANeuralNetworksExecution_compute}.</li>
 *        <li>Synchronously as part of an execution burst with
 *            {@link ANeuralNetworksExecution_burstCompute}.</li></ul>
 *        If the execution has been marked as reusable, then you can
 *        apply the model more than once.
 *    <li>Destroy the execution with
 *        {@link ANeuralNetworksExecution_free}.</li></ul></p>
 *
 * <p>An output buffer or memory region must not overlap with any
 * other output buffer or memory region, with an input buffer or
 * memory region, or with an operand value in a memory object
 * ({@link ANeuralNetworksModel_setOperandValueFromMemory}).</p>
 *
 * <p>An execution is in the preparation state after it is created by
 * {@link ANeuralNetworksExecution_create}. An execution may only be modified in the preparation
 * state. Scheduling a computation by calling {@link ANeuralNetworksExecution_burstCompute},
 * {@link ANeuralNetworksExecution_compute}, {@link ANeuralNetworksExecution_startCompute},
 * or {@link ANeuralNetworksExecution_startComputeWithDependencies} will change the state of
 * the execution object to the computation state. When the computation completes, the state of
 * the execution object will change from the computation state to the completed state.
 * The computation is completed when {@link ANeuralNetworksExecution_compute},
 * {@link ANeuralNetworksExecution_burstCompute}, or {@link ANeuralNetworksEvent_wait}
 * has returned.</p>
 *
 * <p>An execution can be applied to a model with
 * {@link ANeuralNetworksExecution_burstCompute},
 * {@link ANeuralNetworksExecution_compute},
 * {@link ANeuralNetworksExecution_startCompute} or
 * {@link ANeuralNetworksExecution_startComputeWithDependencies} only once. Create new
 * executions to do new evaluations of the model.</p>
 *
 * <p>Starting at NNAPI feature level 5, the application may call
 * {@link ANeuralNetworksExecution_setReusable} to set an execution to be reusable for multiple
 * computations. The application may schedule and evaluate a computation again from the completed
 * state of a reusable execution. The execution cannot be modified between computations.</p>
 *
 * <p>It is the application's responsibility to make sure that only one thread
 * modifies an execution at a given time. It is however safe for more than one
 * thread to use {@link ANeuralNetworksEvent_wait} at the same time.</p>
 *
 * <p>It is also the application's responsibility to ensure that the execution
 * either has never been scheduled or has completed (i.e., that
 * {@link ANeuralNetworksExecution_burstCompute},
 * {@link ANeuralNetworksExecution_compute}, or
 * {@link ANeuralNetworksEvent_wait} has returned) before calling
 * {@link ANeuralNetworksExecution_free}.</p>.
 *
 * <p>It is also the application's responsibility to ensure that there are no other
 * uses of the execution after calling {@link ANeuralNetworksExecution_free}.</p>
 *
 * <p>It is the application's responsibility to ensure that there are no concurrent computations
 * scheduled and evaluated on the same execution, either by means of
 * {@link ANeuralNetworksExecution_compute} or
 * {@link ANeuralNetworksExecution_burstCompute} (which are synchronous)
 * in different threads, or by means of
 * {@link ANeuralNetworksExecution_startCompute} or
 * {@link ANeuralNetworksExecution_startComputeWithDependencies} (which are asynchronous).
 * It is however safe to schedule and evaluate multiple computations on different executions
 * concurrently. (Concurrent uses of {@link ANeuralNetworksExecution_burstCompute} must be on
 * different burst objects.) The runtime makes no guarantee on the ordering of
 * completion of executions. If it's important to the application, the
 * application should enforce the ordering by ensuring that one execution
 * completes before the next is scheduled (for example, by scheduling all
 * executions synchronously within a single thread, or by scheduling all
 * executions asynchronously and using {@link ANeuralNetworksEvent_wait} between
 * calls to {@link ANeuralNetworksExecution_startCompute}); or by using
 * {@link ANeuralNetworksExecution_startComputeWithDependencies} to make the execution wait for a
 * list of events to be signaled before starting the actual evaluation.</p>
 *
 * Available since NNAPI feature level 1.
 */
typedef struct ANeuralNetworksExecution ANeuralNetworksExecution;

%insert SymmPerChannelQuantParams_Comment
typedef struct ANeuralNetworksSymmPerChannelQuantParams {
    /** The index of the channel dimension. */
    uint32_t channelDim;
    /** The size of the scale array. Should be equal to dimension[channelDim] of the Operand. */
    uint32_t scaleCount;
    /** The array of scaling values for each channel. Each value must be greater than zero. */
    const float* scales;
} ANeuralNetworksSymmPerChannelQuantParams;

/**
 * ANeuralNetworksBurst is an opaque type that can be used to reduce the latency
 * of a rapid sequence of executions. It will likely cause overhead if only used
 * for a single execution.
 *
 * ANeuralNetworksBurst serves as a context object for any number of inferences
 * using {@link ANeuralNetworksExecution} objects. An ANeuralNetworksBurst
 * object and the {@link ANeuralNetworksExecution} objects used with it must all
 * have been created from the same {@link ANeuralNetworksCompilation} object.
 *
 * This object is also used as a hint to drivers, providing insight to the
 * lifetime of a rapid sequence of executions. For example, a driver may choose
 * to increase the clock frequency of its accelerator for the lifetime of a
 * burst object.
 *
 * <p>To use:<ul>
 *    <li>Create a new burst object by calling the
 *        {@link ANeuralNetworksBurst_create} function.</li>
 *    <li>For each execution:</li><ul>
 *        <li>Create {@link ANeuralNetworksExecution} and configure its
 *            properties (see {@link ANeuralNetworksExecution} for details).</li>
 *        <li>Apply the model synchronously with
 *            {@link ANeuralNetworksExecution_burstCompute}, reusing the same
 *            {@link ANeuralNetworksBurst} with the new
 *            {@link ANeuralNetworksExecution}.</li>
 *        <li>Use and free the {@link ANeuralNetworksExecution}.</li></ul>
 *    <li>Destroy the burst with
 *        {@link ANeuralNetworksBurst_free}.</li></ul></p>
 *
 * Available since NNAPI feature level 3.
 */
typedef struct ANeuralNetworksBurst ANeuralNetworksBurst;

/**
 * ANeuralNetworksOperandType describes the type of an operand.
 *
 * This structure is used to describe both scalars and tensors.
 *
 * A tensor operand type with all dimensions specified is "fully
 * specified".  Whenever possible (i.e., whenever the dimensions are
 * known at model construction time), a tensor operand type should be
 * (but is not required to be) fully specified, in order to enable the
 * best possible performance.
 *
 * If a tensor operand's type is not fully specified, the dimensions
 * of the operand are deduced from the operand types and values of the
 * operation for which that operand is an output or from the corresponding
 * {@link ANEURALNETWORKS_IF} or {@link ANEURALNETWORKS_WHILE} operation input
 * operand type in the case of referenced model input operands.
 *
 * <p>In the following situations, a tensor operand type must be fully
 * specified:<ul>
 *     <li>The operand has a constant value, set by
 *         {@link ANeuralNetworksModel_setOperandValue} (with a
 *         non-nullptr buffer) or
 *         {@link ANeuralNetworksModel_setOperandValueFromMemory}.</li>
 *     <li>The operand is a model input (see
 *         {@link ANeuralNetworksModel_identifyInputsAndOutputs}) of the main
 *         model within a compilation.  A fully specified tensor operand type
 *         must either be provided to {@link ANeuralNetworksModel_addOperand};
 *         or it must be provided to the corresponding
 *         {@link ANeuralNetworksExecution_setInput}, or
 *         {@link ANeuralNetworksExecution_setInputFromMemory}.
 *         EXCEPTION: If the input is optional and omitted
 *         (by passing nullptr for buffer to
 *         {@link ANeuralNetworksExecution_setInput}) then it need
 *         not have a fully specified tensor operand type.</li>
 *     <li>The operand is a model output (see
 *         {@link ANeuralNetworksModel_identifyInputsAndOutputs}) of the main
 *         model within a compilation and is to be used with {@link
 *         ANeuralNetworksExecution_startComputeWithDependencies}.
 *         A fully specified tensor operand type must either be provided
 *         to {@link ANeuralNetworksModel_addOperand}; or it must be
 *         provided to the corresponding
 *         {@link ANeuralNetworksExecution_setOutput}, or
 *         {@link ANeuralNetworksExecution_setOutputFromMemory}.</li></ul>
 *
 * A tensor operand type of specified rank but some number of
 * unspecified dimensions is represented by setting dimensionCount to
 * the rank and each unspecified dimension to 0.
 *
 * Available since NNAPI feature level 1.
 *
 * Starting at NNAPI feature level 3, a tensor operand type of unspecified rank is
 * represented by setting dimensionCount to 0 and dimensions to NULL (just as if
 * it were a scalar operand type).
 */
typedef struct ANeuralNetworksOperandType {
    /**
     * The data type, e.g ANEURALNETWORKS_FLOAT32.
     */
    int32_t type;

    /**
     * The number of dimensions (rank).
     *
     * Must be 0 for scalars.
     */
    uint32_t dimensionCount;

    /**
     * The dimensions of the tensor.
     *
     * Must be nullptr for scalars.
     */
    const uint32_t* dimensions;

    /**
     * The quantization scale.
     *
     * Must be 0 when not applicable to an operand type.
     *
     * See {@link OperandCode}.
     */
    float scale;

    /**
     * The quantization zero point.
     *
     * Must be 0 when not applicable to an operand type.
     *
     * See {@link OperandCode}.
     */
    int32_t zeroPoint;
} ANeuralNetworksOperandType;

/**
 * Aliasing to {@link OperationCode}, used in function
 * {@link ANeuralNetworksModel_addOperation}.
 */
typedef int32_t ANeuralNetworksOperationType;

/**
 * ANeuralNetworksEvent is an opaque type that represents an event
 * that will be signaled once an execution completes.
 *
 * Available since NNAPI feature level 1.
 */
typedef struct ANeuralNetworksEvent ANeuralNetworksEvent;

/**
 * ANeuralNetworksDevice is an opaque type that represents a device.
 *
 * This type is used to query basic properties and supported operations of the corresponding
 * device, and control which device(s) a model is to be run on.
 *
 * Available since NNAPI feature level 3.
 */
typedef struct ANeuralNetworksDevice ANeuralNetworksDevice;

/**
 * ANeuralNetworksMemoryDesc is an opaque type that represents a memory descriptor.
 *
 * A memory descriptor describes the properties of a memory object, and is used by
 * {@link ANeuralNetworksMemory_createFromDesc}.
 *
 * To use:
 *   - Create a new memory descriptor by calling {@link ANeuralNetworksMemoryDesc_create}.
 *   - Specify all of the intended input and output roles by calling
 *     {@link ANeuralNetworksMemoryDesc_addInputRole} and
 *     {@link ANeuralNetworksMemoryDesc_addOutputRole}.
 *   - Optionally, specify the memory dimensions by calling
 *     {@link ANeuralNetworksMemoryDesc_setDimensions}.
 *   - Complete the memory descriptor with {@link ANeuralNetworksMemoryDesc_finish}.
 *   - Use the memory descriptor as many times as needed with
 *     {@link ANeuralNetworksMemory_createFromDesc}.
 *   - Destroy the memory descriptor with {@link ANeuralNetworksMemoryDesc_free}.
 *
 * A memory descriptor is completed by calling {@link ANeuralNetworksMemoryDesc_finish}.
 * A memory descriptor is destroyed by calling {@link ANeuralNetworksMemoryDesc_free}.
 *
 * A memory descriptor must not be modified once {@link ANeuralNetworksMemoryDesc_finish}
 * has been called on it.
 *
 * It is the application's responsibility to make sure that only
 * one thread modifies a memory descriptor at a given time. It is however
 * safe for more than one thread to use the memory descriptor once
 * {@link ANeuralNetworksMemoryDesc_finish} has returned.
 *
 * It is also the application's responsibility to ensure that there are no other
 * uses of the memory descriptor after calling {@link ANeuralNetworksMemoryDesc_free}.
 * It is however safe to continue using a {@link ANeuralNetworksMemory} object created
 * from the memory descriptor.
 *
 * Available since NNAPI feature level 4.
 */
typedef struct ANeuralNetworksMemoryDesc ANeuralNetworksMemoryDesc;

__END_DECLS

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_NEURAL_NETWORKS_TYPES_H

/** @} */
