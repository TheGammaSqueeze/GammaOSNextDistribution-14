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

#include <string>
#include <qemud.h>
#include <qemu_pipe_bp.h>
#include <debug.h>
#include "qemu_channel.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {
namespace {
const char kServiceName[] = "camera";

int qemuReceiveMessage(const int fd, std::vector<uint8_t>* data) {
    char len16[9];
    int e = qemu_pipe_read_fully(fd, len16, 8);
    if (e < 0) {
        return FAILURE(e);
    }
    len16[8] = 0;

    unsigned len;
    if (sscanf(len16, "%x", &len) != 1) {
        return FAILURE(-EINVAL);
    }

    data->resize(len);
    e = qemu_pipe_read_fully(fd, data->data(), len);
    if (e < 0) {
        return FAILURE(e);
    }

    return 0;
}

} // namespace

base::unique_fd qemuOpenChannel() {
    return base::unique_fd(qemud_channel_open(kServiceName));
}

base::unique_fd qemuOpenChannel(const std::string_view param) {
    if (param.empty()) {
        return qemuOpenChannel();
    } else {
        return base::unique_fd(qemud_channel_open(
            (std::string(kServiceName) + ":" +
             std::string(param.begin(), param.end())).c_str()));
    }
}

int qemuRunQuery(const int fd,
                 const char* const query,
                 const size_t querySize,
                 std::vector<uint8_t>* result) {
    static const uint8_t kZero = 0;
    static const uint8_t kSeparator = ' ';
    static const uint8_t kColon = ':';

    int e = qemu_pipe_write_fully(fd, query, querySize);
    if (e < 0) {
        return FAILURE(e);
    }

    std::vector<uint8_t> reply;
    e = qemuReceiveMessage(fd, &reply);
    if (e < 0) {
        return e;
    }

    if (reply.size() >= 3) {
        bool ok;

        if (!memcmp(reply.data(), "ok", 2)) {
            ok = true;
        } else if (!memcmp(reply.data(), "ko", 2)) {
            ok = false;
        } else {
            return FAILURE(-EBADE);
        }

        switch (reply[2]) {
        case kZero:
            return ok ? 0 : FAILURE(-EBADE);

        case kColon:
            if (!ok) {
                const int msgSize = reply.size() - 3;
                if (msgSize > 0) {
                    return FAILURE_V(-EBADE, "failed to exec '%s' query with %.*s",
                                     query, msgSize, &reply[3]);
                } else {
                    return FAILURE_V(-EBADE, "failed to exec '%s' query", query);
                }
            } else if (result) {
                reply.erase(reply.begin(), reply.begin() + 3);
                *result = std::move(reply);
                return result->size();
            } else {
                return reply.size() - 3;
            }

        default:
            return FAILURE(-EBADE);
        }
    } else {
        return FAILURE(-EBADE);
    }
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
