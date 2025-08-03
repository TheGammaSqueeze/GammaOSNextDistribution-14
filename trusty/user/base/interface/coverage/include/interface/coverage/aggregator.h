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

#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

#define COVERAGE_AGGREGATOR_PORT "com.android.trusty.coverage.aggregator"

/**
 * enum coverage_aggregator_cmd - command identifiers for coverage aggregator
 *                                interface
 * @COVERAGE_AGGREGATOR_CMD_RESP_BIT:   response bit set as part of response
 * @COVERAGE_AGGREGATOR_CMD_SHIFT:      number of bits used by response bit
 * @COVERAGE_AGGREGATOR_CMD_REGISTER:   command to register with coverage
 *                                      aggregator
 * @COVERAGE_AGGREGATOR_CMD_GET_RECORD: command to get shared memory region
 *                                      where coverage record will be written to
 */
enum coverage_aggregator_cmd {
    COVERAGE_AGGREGATOR_CMD_RESP_BIT = 1U,
    COVERAGE_AGGREGATOR_CMD_SHIFT = 1U,
    COVERAGE_AGGREGATOR_CMD_REGISTER = (1U << COVERAGE_AGGREGATOR_CMD_SHIFT),
    COVERAGE_AGGREGATOR_CMD_GET_RECORD = (2U << COVERAGE_AGGREGATOR_CMD_SHIFT),
};

/**
 * struct coverage_aggregator_hdr - header for coverage aggregator messages
 * @cmd: command identifier
 *
 * Note that no messages return a status code. Any error on the server side
 * results in the connection being closed. So, operations can be assumed to be
 * successful if they return a response.
 */
struct coverage_aggregator_hdr {
    uint32_t cmd;
};

/**
 * struct coverage_aggregator_register_req - arguments for request to register
 *                                           with coverage aggregator
 * @record_len: length of coverage record that will be emitted by this TA
 */
struct coverage_aggregator_register_req {
    uint32_t record_len;
};

/**
 * struct coverage_aggregator_register_req - arguments for response to register
 *                                           with coverage aggregator
 * @idx:         unique index assigned to this TA
 * @mailbox_len: length of memory region used as a mailbox
 *
 * A handle to a memory region must be sent along with this message. This memory
 * is used by coverage server to drop messages that TAs asynchronously respond
 * to. Possible mailbox messages are defined by &enum coverage_mailbox_event.
 */
struct coverage_aggregator_register_resp {
    uint32_t idx;
    uint32_t mailbox_len;
};

/**
 * struct coverage_aggregator_get_record_req - arguments for response to get
 *                                             shared memory for coverage record
 * @shm_len: length of memory region being shared
 *
 * A handle to a memory region must be sent along with this message. This memory
 * is used to store coverage record.
 */
struct coverage_aggregator_get_record_resp {
    uint32_t shm_len;
};

/**
 * struct coverage_aggregator_req - structure for a coverage aggregator request
 * @hdr:           message header
 * @register_args: arguments for %COVERAGE_AGGREGATOR_CMD_REGISTER request
 */
struct coverage_aggregator_req {
    struct coverage_aggregator_hdr hdr;
    union {
        struct coverage_aggregator_register_req register_args;
    };
};

/**
 * struct coverage_aggregator_resp - structure for a coverage aggregator
 *                                   response
 * @hdr:             message header
 * @register_args:   arguments for %COVERAGE_AGGREGATOR_CMD_REGISTER response
 * @get_record_args: arguments for %COVERAGE_AGGREGATOR_CMD_GET_RECORD response
 */
struct coverage_aggregator_resp {
    struct coverage_aggregator_hdr hdr;
    union {
        struct coverage_aggregator_register_resp register_args;
        struct coverage_aggregator_get_record_resp get_record_args;
    };
};

/**
 * enum coverage_mailbox_event - mailbox messages
 * @COVERAGE_MAILBOX_EMPTY:        mailbox is empty
 * @COVERAGE_MAILBOX_RECORD_READY: shared memory for coverage record is ready
 */
enum coverage_mailbox_event {
    COVERAGE_MAILBOX_EMPTY = 0U,
    COVERAGE_MAILBOX_RECORD_READY = 1U,
};

__END_CDECLS
