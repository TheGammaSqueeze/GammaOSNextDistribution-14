/*
 * Copyright (c) 2020, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#pragma once

#include <list.h>
#include <reflist.h>
#include <stdint.h>
#include <sys/types.h>

#include <kernel/spinlock.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/uuid.h>
#include <uapi/trusty_uevent.h>

/**
 * struct event_source_ops - user provided callbacks for event source
 * @open:   invoked when the first client connects to event source
 * @mask:   invoked to mask underlying event source
 * @unmask: invoked to unmask underlying events source
 * @close:  invoked when the last client connected to event source
 *          goes away
 *
 * Note: The @open and @close callbacks are invoked with global mutex held
 * so it is not allowed to create or close another event in that context.
 * The  @mask and @unmask callback are invoked in context with spinlock
 * (private to event source obejct) held and interrupts disabled.
 */
struct event_source_ops {
    void (*open)(const void* priv);
    void (*mask)(const void* priv);
    void (*unmask)(const void* priv);
    void (*close)(const void* priv);
};

/**
 * event_source_create() - create new event source object
 * @name:      name of the object to create (non-empty string)
 * @ops:       pointer to &struct event_source_ops
 * @ops_arg:   @priv argument of callbacks specified by @ops parameeter
 * @uuids:     pointer to array of &struct uuids that represents client
 *             uuids that are allowed to connect to event source object
 * @uuids_num: number of entries in array pointed by @uuids parameter
 * @flags:     reserved, must be set to 0
 * @ph:        pointer to &struct handle to return handle to caller
 *
 * Note1: in general @ops are optional.
 *
 * Note2: if @ops are present both &struct event_source_ops->open and
 * &struct event_source_ops->close callbacks must be provided.
 *
 * Note3: if &struct event_source_ops->mask callback is provided corresponding
 * &struct event_source_ops->unmask callback must be provided too.
 *
 * Note4: the retains an ownership of memory referenced by @name, @ops,
 * @ops_arg and @uuids parameters and should keep this memory around for event
 * source object duration. This memory should be only freed after the last
 * reference to event source handle had been closed.
 *
 * Return: 0 on success, negative error otherwise
 */
int event_source_create(const char* name,
                        const struct event_source_ops* ops,
                        const void* ops_arg,
                        const uuid_t* uuids,
                        unsigned int uuids_num,
                        unsigned int flags,
                        struct handle** ph);

/**
 * event_source_publish() - publish event source event
 * @h: pointer to &struct handle of event source to publish
 *
 * This routine makes an event source object (previously created with
 * event_source_create() call) available for clients.
 *
 * Return: 0 on success, negative error otherwise
 */
int event_source_publish(struct handle* h);

/**
 * event_source_open() - open specified event source
 * @cid:      pointer to client's &struct uuid
 * @name:     pointer to buffer containing event source name
 * @max_name: max size of the buffer containing name
 * @flags:    reserved, must be zero
 * @ph:       pointer to &struct handle to return handle to caller
 *
 * Return: 0 on success, negative error otherwise
 */
int event_source_open(const struct uuid* cid,
                      const char* name,
                      size_t max_name,
                      unsigned int flags,
                      struct handle** ph);

/**
 * event_source_signal() - signal event source
 * @h: pointer to &struct handle to signal
 *
 * Note1: It is safe to call this routine from interrupt context.
 *
 * Note2: if underlying event source object implements
 * &struct event_source_ops->mask callback it is illegal to call this routine
 * again for objects that are already in signaled state. (It is expected that
 * event source is  masked which should prevent signaling it again). For this
 * schema it is guaranteed that all clients would see an event exactly once for
 * each invocation of event_source_signal() routine.
 *
 * Note: for all other valid callback combinations, it is OK to call this
 * routine multiple times in any state, but some events might be lost. This is
 * similar to receiving a new edge triggered interrupt while previous one is
 * not handled yet. It is also guaranteed that all clients will be notified
 * after the last call to event_source_signal() at least once.
 *
 * Return: 0 on success, negative error otherwise
 */
int event_source_signal(struct handle* h);

/**
 * event_client_notify_handled() - change event client state
 * @h:   pointer to &struct handle to change state
 *
 * This routine is called on client handle to indicate that the client has
 * finished handling an event and underlying object has to change it's state
 * accordingly.
 *
 * Return: 0 on success, negative error otherwise
 */
int event_client_notify_handled(struct handle* h);
