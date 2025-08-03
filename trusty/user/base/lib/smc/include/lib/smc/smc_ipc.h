/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <interface/smc/smc.h>
#include <lk/compiler.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * smc_send_request() - send message to SMC service
 * @channel: handle to the channel to send message over
 * @msg: pointer to the message to be sent
 *
 * Return: the total number of bytes sent on success, a negative error code
 * otherwise.
 */
int smc_send_request(handle_t channel, struct smc_msg* msg);

/**
 * smc_read_response() - read message from SMC service
 * @channel: handle to the channel to read message from
 * @msg: pointer to memory where read message is placed
 *
 * Return: the number of bytes stored into memory pointed to by @msg parameter
 * on success, a negative error code otherwise
 */
int smc_read_response(handle_t channel, struct smc_msg* msg);

__END_CDECLS
