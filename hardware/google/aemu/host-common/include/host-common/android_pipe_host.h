// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include "android_pipe_common.h"

#include "aemu/base/c_header.h"
#include "aemu/base/utils/stream.h"

#include <stdbool.h>
#include <stdint.h>

ANDROID_BEGIN_HEADER

// The set of methods implemented by an AndroidPipe instance.
// All these methods are called at runtime by the virtual device
// implementation. Note that transfers are always initiated by the virtual
// device through the sendBuffers() and recvBuffers() callbacks.
//
// More specifically:
//
// - When a pipe has data for the guest, it should signal it by calling
//   'android_pipe_host_signal_wake(pipe, PIPE_WAKE_READ)'. The guest kernel
//   will be signaled and will later initiate a recvBuffers() call.
//
// - When a pipe is ready to accept data from the guest, it should signal
//   it by calling 'android_pipe_host_signal_wake(pipe, PIPE_WAKE_WRITE)'. The
//   guest kernel will be signaled, and will later initiate a sendBuffers()
//   call when the guest client writes data to the pipe.
//
// - Finally, the guest kernel can signal whether it wants to be signaled
//   on read or write events by calling the wakeOn() callback.
//
// - When the emulator wants to close a pipe, it shall call
//   android_pipe_host_close(). This signals the guest kernel which will later
//   initiate a close() call.
//
typedef struct AndroidPipeFuncs {
    // Call to open a new pipe instance. |hwpipe| is a device-side view of the
    // pipe that must be passed to android_pipe_host_signal_wake() and
    // android_pipe_host_close()
    // functions. |serviceOpaque| is the parameter passed to
    // android_pipe_add_type(), and |args| is either NULL
    // or parameters passed to the service when opening the connection.
    // Return a new service pipe instance, or NULL on error.
    void* (*init)(void* hwpipe, void* serviceOpaque, const char* args);

    // Called when the guest kernel has finally closed a pipe connection.
    // This is the only place one should release/free the instance, but never
    // call this directly, use android_pipe_host_close() instead. |pipe| is a
    // client
    // instance returned by init() or load().
    void (*close)(void* service_pipe);

    // Called when the guest wants to write data to the |pipe| client instance,
    // |buffers| points to an array of |numBuffers| descriptors that describe
    // where to copy the data from. Return the number of bytes that were
    // actually transferred, 0 for EOF status, or a negative error value
    // otherwise, including PIPE_ERROR_AGAIN to indicate that the emulator
    // is not ready yet to receive data.
    int (*sendBuffers)(void* service_pipe,
                       const AndroidPipeBuffer* buffers,
                       int numBuffers);

    // Called when the guest wants to read data from the |pipe| client,
    // |buffers| points to an array of |numBuffers| descriptors that describe
    // where to copy the data to. Return the number of bytes that were
    // actually transferred, 0 for EOF status, or a negative error value
    // otherwise, including PIPE_ERROR_AGAIN to indicate that the emulator
    // doesn't have data for the guest yet.
    int (*recvBuffers)(void* service_pipe,
                       AndroidPipeBuffer* buffers,
                       int numBuffers);

    // Called when guest wants to poll the read/write status for the |pipe|
    // client. Should return a combination of PIPE_POLL_XXX flags.
    unsigned (*poll)(void* service_pipe);

    // Called to signal that the guest wants to be woken when the set of
    // PIPE_WAKE_XXX bit-flags in |flags| occur. When the condition occurs,
    // then the |service_pipe| client shall call
    // android_pipe_host_signal_wake().
    void (*wakeOn)(void* service_pipe, int flags);

    // Called to save the |service_pipe| client's state to a Stream, i.e. when
    // saving snapshots.
    void (*save)(void* service_pipe, Stream* file);

    // Called to load the sate of a pipe client from a Stream. This will always
    // correspond to the state of the client as saved by a previous call to
    // the 'save' method. Can be NULL to indicate that the pipe state cannot
    // be loaded. In this case, the emulator will automatically force-close
    // it. Parameters are similar to init(), with the addition of |file|
    // which is the input stream.
    void* (*load)(void* hwpipe, void* serviceOpaque, const char* args,
                  Stream* file);

} AndroidPipeFuncs;

/* Register a new pipe service type. |serviceOpaque| is passed directly
 * to 'init() when a new pipe is connected to.
 */
extern void  android_pipe_add_type(const char* serviceName,
                                   void* serviceOpaque,
                                   const AndroidPipeFuncs* pipeFuncs);

// The following functions are only used during unit-testing.

// Reset the list of services registered through android_pipe_add_type().
// Useful at the start and end of a unit-test.
extern void android_pipe_reset_services(void);

/* This tells the guest system that we want to close the pipe and that
 * further attempts to read or write to it will fail. This will not
 * necessarily destroys the |hwpipe| immediately. The latter will call
 * android_pipe_guest_close() at destruction time though.
 *
 * This will also wake-up any blocked guest threads waiting for i/o.
 * NOTE: This function can be called from any thread.
 */
extern void android_pipe_host_close(void* hwpipe);

/* Signal that the pipe can be woken up. 'flags' must be a combination of
 * PIPE_WAKE_READ and PIPE_WAKE_WRITE.
 * NOTE: This function can be called from any thread.
 */
extern void android_pipe_host_signal_wake(void* hwpipe, unsigned flags);

ANDROID_END_HEADER
