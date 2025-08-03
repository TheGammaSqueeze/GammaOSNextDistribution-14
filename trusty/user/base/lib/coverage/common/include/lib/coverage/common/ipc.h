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

#pragma once

#include <interface/coverage/aggregator.h>
#include <interface/coverage/client.h>
#include <lk/compiler.h>
#include <stdint.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * coverage_send() - send a buffer and a handle
 * @chan:    channel to send message over
 * @msg:     buffer containing message
 * @msg_len: length of @msg
 * @h:       pointer to handle to be sent
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_send(handle_t chan, void* msg, size_t msg_len, handle_t* h);

/**
 * coverage_recv() - receive a buffers and a handle
 * @chan:    channel to receive message over
 * @msg:     buffer containing message
 * @msg_len: length of @msg
 * @h:       pointer to handle to be received
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_recv(handle_t chan, void* msg, size_t msg_len, handle_t* h);

/**
 * coverage_aggregator_rpc() - make an RPC to coverage aggregator
 * @chan:   channel to perform RPC over
 * @req:    request to be sent
 * @req_h:  pointer to handle to be sent
 * @resp:   response to be received
 * @resp_h: pointer to handle to be received
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_aggregator_rpc(handle_t chan,
                            struct coverage_aggregator_req* req,
                            handle_t* req_h,
                            struct coverage_aggregator_resp* resp,
                            handle_t* resp_h);

/**
 * coverage_client_rpc() - make an RPC to coverage client
 * @chan:   channel to perform RPC over
 * @req:    request to be sent
 * @req_h:  pointer to handle to be sent
 * @resp:   response to be received
 * @resp_h: pointer to handle to be received
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_client_rpc(handle_t chan,
                        struct coverage_client_req* req,
                        handle_t* req_h,
                        struct coverage_client_resp* resp,
                        handle_t* resp_h);

__END_CDECLS
