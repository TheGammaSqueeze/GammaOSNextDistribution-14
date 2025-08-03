/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef AVB_IPC_H_
#define AVB_IPC_H_

#include "avb_messages.h"

namespace avb {

enum avb_command {
    AVB_REQ_SHIFT = 1,
    AVB_RESP_BIT = 1,

    READ_ROLLBACK_INDEX = (0 << AVB_REQ_SHIFT),
    WRITE_ROLLBACK_INDEX = (1 << AVB_REQ_SHIFT),
    AVB_GET_VERSION = (2 << AVB_REQ_SHIFT),
    READ_PERMANENT_ATTRIBUTES = (3 << AVB_REQ_SHIFT),
    WRITE_PERMANENT_ATTRIBUTES = (4 << AVB_REQ_SHIFT),
    READ_LOCK_STATE = (5 << AVB_REQ_SHIFT),
    WRITE_LOCK_STATE = (6 << AVB_REQ_SHIFT),
    LOCK_BOOT_STATE = (7 << AVB_REQ_SHIFT),
};

// struct avb_message - Generic message format for communicating with AVB server
// @cmd:              one of enum avb_command
// @result:           one of enum AvbError
// @payload:          start of the serialized command specific message
struct avb_message {
    uint32_t cmd;
    AvbError result;
    uint8_t payload[0];
};

}  // namespace avb

#endif  // AVB_IPC_H_
