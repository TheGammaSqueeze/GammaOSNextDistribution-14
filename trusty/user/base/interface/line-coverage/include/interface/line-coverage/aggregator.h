/*
 * Copyright (C) 2023 The Android Open Source Project
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

/*
This defines the messages between various secure world applications that
have been instrumented and the coverage aggregator. The shared memory is passed
onto those application via this.
*/

#pragma once

#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

#define LINE_COVERAGE_AGGREGATOR_PORT "com.android.trusty.linecoverage.aggregator"

/**
 * enum line_coverage_aggregator_cmd - command identifiers for coverage aggregator
 *                                interface
 * @LINE_COVERAGE_AGGREGATOR_CMD_RESP_BIT:   response bit set as part of response
 * @LINE_COVERAGE_AGGREGATOR_CMD_SHIFT:      number of bits used by response bit
 * @LINE_COVERAGE_AGGREGATOR_CMD_REGISTER:   command to register with coverage
 *                                      aggregator
 * @LINE_COVERAGE_AGGREGATOR_CMD_GET_RECORD: command to get shared memory region
 *                                      where coverage record will be written to
 */
enum line_coverage_aggregator_cmd {
    LINE_COVERAGE_AGGREGATOR_CMD_RESP_BIT = 1U,
    LINE_COVERAGE_AGGREGATOR_CMD_SHIFT = 1U,
    LINE_COVERAGE_AGGREGATOR_CMD_REGISTER = (1U << LINE_COVERAGE_AGGREGATOR_CMD_SHIFT),
    LINE_COVERAGE_AGGREGATOR_CMD_GET_RECORD = (2U << LINE_COVERAGE_AGGREGATOR_CMD_SHIFT),
};

/**
 * struct line_coverage_aggregator_hdr - header for coverage aggregator messages
 * @cmd: command identifier
 *
 * Note that no messages return a status code. Any error on the server side
 * results in the connection being closed. So, operations can be assumed to be
 * successful if they return a response.
 */
struct line_coverage_aggregator_hdr {
    uint32_t cmd;
};

/**
 * struct line_coverage_aggregator_register_req - arguments for request to register
 *                                           with coverage aggregator
 * @record_len: length of coverage record that will be emitted by this TA
 */
struct line_coverage_aggregator_register_req {
    uint32_t record_len;
};

/**
 * struct line_coverage_aggregator_register_resp - arguments for response to register
 *                                           with coverage aggregator
 * @idx:         unique index assigned to this TA
 * @mailbox_len: length of memory region used as a mailbox
 *
 * A handle to a memory region must be sent along with this message. This memory
 * is used by coverage server to drop messages that TAs asynchronously respond
 * to. Possible mailbox messages are defined by &enum line_coverage_mailbox_event.
 */
struct line_coverage_aggregator_register_resp {
    uint32_t idx;
    uint32_t mailbox_len;
};

/**
 * struct line_coverage_aggregator_get_record_resp - arguments for response to get
 *                                             shared memory for coverage record
 * @shm_len: length of memory region being shared
 *
 * A handle to a memory region must be sent along with this message. This memory
 * is used to identify and send coverage record.
 */
struct line_coverage_aggregator_get_record_resp {
    uint32_t shm_len;
};

/**
 * struct line_coverage_aggregator_req - structure for a coverage aggregator request
 * @hdr:           message header
 * @register_args: arguments for %COVERAGE_AGGREGATOR_CMD_REGISTER request
 */
struct line_coverage_aggregator_req {
    struct line_coverage_aggregator_hdr hdr;
    union {
        struct line_coverage_aggregator_register_req register_args;
    };
};

/**
 * struct line_coverage_aggregator_resp - structure for a coverage aggregator
 *                                   response
 * @hdr:             message header
 * @register_args:   arguments for %COVERAGE_AGGREGATOR_CMD_REGISTER response
 * @get_record_args: arguments for %COVERAGE_AGGREGATOR_CMD_GET_RECORD response
 */
struct line_coverage_aggregator_resp {
    struct line_coverage_aggregator_hdr hdr;
    union {
        struct line_coverage_aggregator_register_resp register_args;
        struct line_coverage_aggregator_get_record_resp get_record_args;
    };
};

/**
 * enum line_coverage_mailbox_event - mailbox messages
 * @LINE_COVERAGE_MAILBOX_EMPTY:        mailbox is empty
 * @LINE_COVERAGE_MAILBOX_RECORD_READY: shared memory for coverage record is ready
 */
enum line_coverage_mailbox_event {
    LINE_COVERAGE_MAILBOX_EMPTY = 0U,
    LINE_COVERAGE_MAILBOX_RECORD_READY = 1U,
};

__END_CDECLS
