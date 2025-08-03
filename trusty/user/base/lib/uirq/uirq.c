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

#include <errno.h>
#include <lib/uirq/uirq.h>
#include <trusty_uio.h>
#include <uapi/trusty_uevent.h>

handle_t uirq_open(const char* name, uint32_t flags) {
    return connect(name, flags);
}

static int send_ack(handle_t h, uint32_t cmd) {
    return trusty_write((int)h, &cmd, sizeof(cmd));
}

int uirq_ack_handled(handle_t h) {
    return send_ack(h, EVENT_NOTIFY_CMD_HANDLED);
}
