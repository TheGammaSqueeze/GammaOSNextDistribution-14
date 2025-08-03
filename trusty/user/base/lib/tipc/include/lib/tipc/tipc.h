/*
 * Copyright (C) 2018 The Android Open Source Project
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

#pragma once

#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>

#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * DOC: TIPC helper library
 *
 * This library is a collection of frequently used routines
 * and code patterns related to working with Trusty IPC.
 */

/**
 * tipc_connect() - connect to specified TIPC service
 * @handle_p: - pointer to location to store channel handle
 * @port: - IPC port name to connect to
 *
 * Initiate a synchronous connection to specified port.
 * If port does not exist, wait until it is created.
 *
 * Return: on success, 0 is returned and channel handle is
 * stored at location pointed by @ph. A negative error code
 * is returned otherwise.
 */
int tipc_connect(handle_t* handle_p, const char* port);

/**
 * tipc_send1() - send a message from a single buffer
 * @chan: handle of the channel to send message over
 * @buf: pointer to the buffer containing message to send
 * @len: length of the message pointed by @buf parameter
 *
 * Return: the total number of bytes sent on success, a negative
 * error code otherwise.
 */
int tipc_send1(handle_t chan, const void* buf, size_t len);

/**
 * tipc_recv1() - receive message into single buffer
 * @chan: handle of the channel to receive message from
 * @min_sz: minimum size of the message expected
 * @buf: pointer to the buffer to place received message
 * @buf_sz: size of the buffer pointed by @buf to receive message
 *
 * The received message has to contain at least @min_sz bytes and
 * fully fit into provided buffer
 *
 * Return: the number of bytes stored into buffer pointed by @buf
 * parameter on success, a negative error code otherwise
 */
int tipc_recv1(handle_t chan, size_t min_sz, void* buf, size_t buf_sz);

/**
 * tipc_send2() - send a message consisting of two segments
 * @chan: handle of the channel to send message over
 * @hdr: pointer to buffer containing message header
 * @hdr_len: size of the header pointed by @hdr parameter
 * @payload: pointer to buffer containing payload
 * @payload_len: size of payload pointed by @payload parameter
 *
 * This routine sends a message consisting of two segments, a header
 * and a payload, which are concatenated together to make a single
 * message.
 *
 * Return: the total number of bytes sent on success, a negative
 *         error code otherwise.
 */
int tipc_send2(handle_t chan,
               const void* hdr,
               size_t hdr_len,
               const void* payload,
               size_t payload_len);

/**
 * tipc_recv2() - receive message and split it into two segments
 * @chan: handle of the channel to receive message from
 * @min_sz: minimum size of the message expected
 * @buf1: pointer to buffer to store first segment of the message
 * @buf1_sz: size of the buffer pointed by @buf1 parameter
 * @buf2: pointer to buffer to store second segment of the message
 * @buf2_sz: size of the buffer pointed by @buf2 parameter
 *
 * This function receives a single massage from specified channel and splits
 * it into two separate buffers, The received message has to contain at least
 * @min_sz bytes and should fully fit into provided buffers.
 *
 * Return: the total number of bytes stored into provided buffers on success,
 *         a negative error code otherwise.
 */
int tipc_recv2(handle_t chan,
               size_t min_sz,
               void* buf1,
               size_t buf1_sz,
               void* buf2,
               size_t buf2_sz);

/**
 * tipc_recv_hdr_payload() - receive message and split it into two segments
 * @chan: handle of the channel to receive message from
 * @hdr: pointer to buffer to store mandatory message header
 * @hdr_sz: size of the message header
 * @payload: pointer to buffer to store optional payload
 * @payload_sz: size of the buffer pointed by @payload parameter
 *
 * Not: This is a wrapper on top of tipc_recv2 where min_sz set to hdr_sz
 *
 * Return: the total number of bytes stored into provided buffers on success,
 *         a negative error code otherwise.
 */
static inline int tipc_recv_hdr_payload(handle_t chan,
                                        void* hdr,
                                        size_t hdr_sz,
                                        void* payload,
                                        size_t payload_sz) {
    return tipc_recv2(chan, hdr_sz, hdr, hdr_sz, payload, payload_sz);
}

/**
 * tipc_handle_port_errors() - helper to handle unexpected port events
 * @ev: pointer to event to handle
 *
 * This routine is intended to be called as a part of port event handler
 * to check for unexpected conditions that normally should never
 * happen for a valid port handle. The implementation calls an
 * abort if any of these conditions are encountered.
 *
 * Return: none.
 */
void tipc_handle_port_errors(const struct uevent* ev);

/**
 * tipc_handle_chan_errors() - helper to handle unexpected channel events
 * @ev: pointer to event to handle
 *
 * This routine is intended to be called as a part of channel event handler
 * to check for unexpected conditions. These conditions should never
 * happen for a valid channel handle. The implementation might call an
 * abort if any of these conditions are encountered.
 *
 * Return: none.
 */
void tipc_handle_chan_errors(const struct uevent* ev);

/**
 * typedef event_handler_proc_t - pointer to event handler routine
 * @ev: pointer to event to handle
 * @priv: handle/context specific argument
 *
 * Return: none
 */
typedef void (*event_handler_proc_t)(const struct uevent* ev, void* priv);

/**
 * struct tipc_event_handler - defines event handler for particular handle
 * @proc: pointer to @event_handler_proc_t function to call to handle event
 * @priv: value to pass as @priv parameter for event_handler_proc_t function
 *        pointed by @proc parameters
 */
struct tipc_event_handler {
    event_handler_proc_t proc;
    void* priv;
};

/*
 * struct tipc_hset - opaque structure representing handle set
 */
struct tipc_hset;

/**
 *  tipc_hset_create() - allocate and initialize new handle set
 *
 *  Return: a pointer to &struct tipc_hset on success, PTR_ERR otherwise.
 */
struct tipc_hset* tipc_hset_create(void);

/**
 * tipc_hset_add_entry() - add new existing handle to handle set
 * @hset:        pointer to valid &struct tipc_hset
 * @handle:      handle to add to handle set specified by @hset parameter
 * @evt_mask:    set of events allowed to be handled for @handle
 * @evt_handler: pointer to initialized &struct tipc_event_handler (must not
 *               be NULL) that will be used to handle events associated with
 *               handle specified by @handle parameter and allowed by
 *               @evt_mask parameter.
 *
 * Return: 0 on success, a negative error code otherwise
 */
int tipc_hset_add_entry(struct tipc_hset* hset,
                        handle_t handle,
                        uint32_t evt_mask,
                        struct tipc_event_handler* evt_handler);

/**
 * tipc_hset_mod_entry() - modify parameters of an existing entry in handle set
 * @hset:        pointer to valid &struct tipc_hset
 * @handle:      handle to modify an entry for. It must be previously added by
 *               calling tipc_hset_add_handle() function.
 * @evt_mask:    set of events allowed to be handled for @handle
 * @evt_handler: pointer to initialized &struct tipc_event_handler (must not
 *               be NULL) that will be used to handle events associated with
 *               handle specified by @handle parameter and allowed by
 *               @evt_mask parameter.
 *
 * Return: 0 on success, a negative error code otherwise
 */
int tipc_hset_mod_entry(struct tipc_hset* hset,
                        handle_t handle,
                        uint32_t evt_mask,
                        struct tipc_event_handler* evt_handler);

/**
 * tipc_hset_remove_entry() - remove specified handle from handle set
 * @hset: pointer to &struct tipc_hset to remove handle from
 * @handle: handle to remove from handle set specified by @hset parameter
 *
 * Return: 0 on success, a negative error code otherwise
 */
int tipc_hset_remove_entry(struct tipc_hset* hset, handle_t handle);

/**
 * tipc_handle_event() - wait on handle set and handle single event
 * @hset: pointer to valid &struct tipc_hset set to get events from
 * @timeout: a max amount of time to wait for event before returning to caller
 *
 * Note: It is expected that this routine is called repeatedly from event loop
 * to handle events. The handle set specified as @hset parameter has to be
 * populated with tipc_hset_add_handle() function.
 *
 * Return: 0 if an event has been retrieved and handled, ERR_TIMED_OUT if
 * specified by @timeout parameter time has elapsed without getting new event,
 * negative error code otherwise.
 */
int tipc_handle_event(struct tipc_hset* hset, uint32_t timeout);

/**
 * tipc_run_event_loop() - run standard event loop
 * @hset: handle set to retrieve and handle events from
 *
 * This routine does not return under normal conditions.
 *
 * Return: negative error code if an error is encountered.
 */
int tipc_run_event_loop(struct tipc_hset* hset);

__END_CDECLS
