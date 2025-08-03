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

#include <errno.h>
#include <iostream>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>

#include <libProxyConfig/libProxyConfig.h>

#include <linux/vm_sockets.h>

#define MAX(x, y) (((x) > (y)) ? (x) : (y))
#define BUFFER_SIZE 16384
#define CLIENT_QUEUE_SIZE 128

int setupServerSocket(sockaddr_vm& addr) {
    int vsock_socket = socket(AF_VSOCK, SOCK_STREAM, 0);

    if (vsock_socket == -1) {
        std::cerr << "Failed to create server VSOCK socket, ERROR = "
                  << strerror(errno) << std::endl;
        return -1;
    }

    if (bind(vsock_socket, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        std::cerr << "Failed to bind to server VSOCK socket, ERROR = "
                  << strerror(errno) << std::endl;
        return -1;
    }

    if (listen(vsock_socket, CLIENT_QUEUE_SIZE) != 0) {
        std::cerr << "Failed to listen on server VSOCK socket, ERROR = "
                  << strerror(errno) << std::endl;
        return -1;
    }

    return vsock_socket;
}

void closeFileDescriptor(int fd) {
    close(fd);
    shutdown(fd, SHUT_RDWR);
}

// transfers a max of BUFFER_SIZE bytes between a source file descriptor and a
// destination file descriptor. Returns true on success, false otherwise
bool transferBytes(int src_fd, int dst_fd) {
    char buf[BUFFER_SIZE];
    int readBytes = read(src_fd, buf, BUFFER_SIZE);
    if (readBytes <= 0) {
        return false;
    }
    int writtenBytes = write(dst_fd, buf, readBytes);
    return writtenBytes >= 0;
}

// Handles a client requesting to connect with the forwarding address
void* handleConnection(int client_sock, int fwd_cid, int fwd_port) {
    int server_sock = socket(AF_VSOCK, SOCK_STREAM, 0);

    if (server_sock < 0) {
        std::cerr << "Failed to create forwarding VSOCK socket, ERROR = "
                  <<  strerror(errno) << std::endl;
        closeFileDescriptor(server_sock);
        closeFileDescriptor(client_sock);
        return nullptr;
    }

    sockaddr_vm fwd_addr{};
    fwd_addr.svm_family = AF_VSOCK;
    fwd_addr.svm_cid = fwd_cid;
    fwd_addr.svm_port = fwd_port;

    if (connect(server_sock, reinterpret_cast<sockaddr*>(&fwd_addr),
              sizeof(fwd_addr)) < 0) {
        std::cerr << "Failed to connect to forwarding vsock socket, ERROR = "
                  <<  strerror(errno) << std::endl;
        closeFileDescriptor(server_sock);
        closeFileDescriptor(client_sock);
        return nullptr;
    }

    bool connected = true;
    while (connected) {
      fd_set file_descriptors;
      FD_ZERO(&file_descriptors);
      FD_SET(client_sock, &file_descriptors);
      FD_SET(server_sock, &file_descriptors);

      int rv = select(MAX(client_sock, server_sock) + 1, &file_descriptors, nullptr, nullptr,
                    nullptr);
      if (rv == -1) {
          std::cerr << "ERROR in Select!. Error = " << strerror(errno) << std::endl;
          break;
      }

      if (FD_ISSET(client_sock, &file_descriptors)) {
          // transfer bytes from client to forward address
          connected = transferBytes(client_sock, server_sock);
      }

      if (FD_ISSET(server_sock, &file_descriptors)) {
          // transfer bytes from forward address to client
          connected = transferBytes(server_sock, client_sock);
      }
    }

    closeFileDescriptor(client_sock);
    closeFileDescriptor(server_sock);

    return nullptr;
}

void setupRoute(int cid, const android::automotive::proxyconfig::Service& service) {

    sockaddr_vm addr{};
    addr.svm_family = AF_VSOCK;
    addr.svm_cid = 2;
    addr.svm_port = service.port;

    int fwd_cid = cid;
    int fwd_port = service.port;

    int proxy_socket = setupServerSocket(addr);

    if (proxy_socket == -1) {
        std::cerr << "Failed to set up proxy server VSOCK socket, ERROR = " <<
           strerror(errno) << std::endl;
        return;
    }

    int client_sock;
    int len = sizeof(addr);

    while (true) {
        if ((client_sock = accept(proxy_socket, reinterpret_cast<sockaddr*>(&addr),
                              reinterpret_cast<socklen_t*>(&len))) < 0) {
            std::cerr << "Failed to accept VSOCK connection, ERROR = " <<
               strerror(errno) << std::endl;
            closeFileDescriptor(client_sock);
            continue;
        }

        std::thread t(handleConnection, client_sock, fwd_cid, fwd_port);
        t.detach();
    }

    closeFileDescriptor(proxy_socket);
}


static constexpr const char *kProxyConfigFile =
    "../etc/automotive/proxy_config.json";

int main(int argc, char **argv ) {
    android::automotive::proxyconfig::setProxyConfigFile(
        (argc >= 2)?argv[1]:kProxyConfigFile);

    auto vmConfigs = android::automotive::proxyconfig::getAllVmProxyConfigs();

    std::vector<std::thread> routeThreads;
    for (const auto& vmConfig: vmConfigs) {
        for (const auto& service: vmConfig.services) {
            routeThreads.push_back(std::thread(setupRoute, vmConfig.cid, service));
        }
    }

    for(auto& t: routeThreads) {
        t.join();
    }

    return 0;
}
