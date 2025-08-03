/*
 *
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


#define GRPC_MSG_BUF_MAX 1024

#define MSG_TYPE_REQUEST 1
#define MSG_TYPE_RESPONSE_BASE 2
#define MSG_TYPE_REQUEST_SIZE (sizeof(struct wmediumd_grpc_request_message) - sizeof(long))
#define MSG_TYPE_RESPONSE_SIZE (sizeof(struct wmediumd_grpc_response_message) - sizeof(long))

enum wmediumd_grpc_request_data_type {
    REQUEST_LIST_STATIONS,
    REQUEST_LOAD_CONFIG,
    REQUEST_RELOAD_CONFIG,
    REQUEST_SET_CIVICLOC,
    REQUEST_SET_LCI,
    REQUEST_SET_POSITION,
    REQUEST_SET_SNR,
    REQUEST_SET_TX_POWER,
    REQUEST_START_PCAP,
    REQUEST_STOP_PCAP,
};

enum wmediumd_grpc_response_data_type {
    RESPONSE_INVALID,
    RESPONSE_ACK,
    RESPONSE_ACK_LIST_STATIONS,
};

#pragma pack(push, 1)
struct wmediumd_grpc_request_message {
    // Message queue type
    long msg_type_request;

    // Message queue payload
    long msg_type_response;
    enum wmediumd_grpc_request_data_type data_type;
    ssize_t data_size;
    char data_payload[GRPC_MSG_BUF_MAX];
};

struct wmediumd_grpc_response_message {
    // Message queue type
    long msg_type_response;

    // Message queue payload
    enum wmediumd_grpc_response_data_type data_type;
    ssize_t data_size;
    char data_payload[GRPC_MSG_BUF_MAX];
};
#pragma pack(pop)
