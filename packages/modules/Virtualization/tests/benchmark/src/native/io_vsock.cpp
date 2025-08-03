/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "io_vsock.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <linux/vm_sockets.h>
#include <sys/socket.h>

using namespace android::base;

namespace io_vsock {
Result<int> init_vsock_server(unsigned int port) {
    int server_fd(TEMP_FAILURE_RETRY(socket(AF_VSOCK, SOCK_STREAM | SOCK_CLOEXEC, 0)));
    if (server_fd < 0) {
        return Error() << "VM:cannot create socket";
    }
    struct sockaddr_vm server_sa = (struct sockaddr_vm){
            .svm_family = AF_VSOCK,
            .svm_port = port,
            .svm_cid = VMADDR_CID_ANY,
    };
    LOG(INFO) << "VM:Connecting on port " << port << "...";
    int ret = TEMP_FAILURE_RETRY(bind(server_fd, (struct sockaddr *)&server_sa, sizeof(server_sa)));
    if (ret < 0) {
        return Error() << "VM:cannot bind an address with the socket";
    }
    ret = TEMP_FAILURE_RETRY(listen(server_fd, /*backlog=*/1));
    if (ret < 0) {
        return Error() << "VM:cannot listen to port";
    }
    LOG(INFO) << "Server now listening";
    return server_fd;
}

Result<void> run_vsock_server_and_receive_data(int server_fd, int num_bytes_to_receive) {
    LOG(INFO) << "Accepting connection...";
    struct sockaddr_vm client_sa;
    socklen_t client_sa_len = sizeof(client_sa);
    unique_fd client_fd(TEMP_FAILURE_RETRY(
            accept4(server_fd, (struct sockaddr *)&client_sa, &client_sa_len, SOCK_CLOEXEC)));
    if (client_fd < 0) {
        return Error() << "Cannot retrieve connect requests";
    }
    LOG(INFO) << "VM:Connection from CID " << client_sa.svm_cid << " on port "
              << client_sa.svm_port;

    ssize_t total = 0;
    char buf[4096];
    for (;;) {
        ssize_t n = TEMP_FAILURE_RETRY(read(client_fd.get(), buf, sizeof(buf)));
        if (n < 0) {
            return Error() << "Cannot get data from the host.";
        }
        if (n == 0) {
            break;
        }
        total += n;
    }

    if (total != num_bytes_to_receive) {
        return Error() << "Received data length(" << total << ") is not equal to "
                       << num_bytes_to_receive;
    }
    LOG(INFO) << "VM:Finished reading data.";
    return {};
}
} // namespace io_vsock
