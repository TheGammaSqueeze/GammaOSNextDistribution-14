/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define KEYMASTER_SECURE_PORT "com.android.trusty.keymaster.secure"

/**
 * keymaster_command - command identifier for secure KM operations
 * @GET_AUTH_TOKEN_KEY: retrieves the auth token key from KM.
 *                      Payload not required.
 */
#define KM_RESP_BIT 1U
#define KM_STOP_BIT 2U
#define KM_REQ_SHIFT 2U
#define KM_GET_AUTH_TOKEN_KEY (0U << KM_REQ_SHIFT)
#define KM_GET_DEVICE_INFO (1U << KM_REQ_SHIFT)
#define KM_SET_ATTESTATION_IDS_SECURE (0xc000 << KM_REQ_SHIFT)

/**
 * keymaster_message - Serial header for communicating with KM server
 * @cmd: the command, one of keymaster_command.
 * @payload: start of the serialized command specific payload
 */
struct keymaster_message {
    uint32_t cmd;
    uint8_t payload[0];
};
