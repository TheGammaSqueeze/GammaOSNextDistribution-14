/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "rpmb_dev.h"

#include <errno.h>
#include <fcntl.h>
#include <getopt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

/*
 * Receives data until one of the following is true:
 * - The buffer is full (return will be len)
 * - The connection closed (return > 0, < len)
 * - An error occurred (return will be the negative error code from recv)
 */
ssize_t recv_until(int sock, void* dest, size_t len) {
    size_t bytes_recvd = 0;
    while (bytes_recvd < len) {
        ssize_t ret = recv(sock, dest, len - bytes_recvd, 0);
        if (ret < 0) {
            return ret;
        }
        dest += ret;
        bytes_recvd += ret;
        if (ret == 0) {
            break;
        }
    }
    return bytes_recvd;
}

/*
 * Handles an incoming connection to the rpmb daemon.
 * Returns 0 if the client disconnects without violating the protocol.
 * Returns a negative value if we terminated the connection abnormally.
 *
 * Arguments:
 *   conn_sock - an fd to send/recv on
 *   s - an initialized rpmb device
 */
int handle_conn(struct rpmb_dev_state* s, int conn_sock) {
    int ret;

    while (true) {
        memset(s->res, 0, sizeof(s->res));
        ret = recv_until(conn_sock, &s->res_count, sizeof(s->res_count));

        /*
         * Disconnected while not in the middle of anything.
         */
        if (ret <= 0) {
            return 0;
        }

        if (s->res_count > MAX_PACKET_COUNT) {
            fprintf(stderr, "rpmb_dev: Receive count too large: %d\n",
                    s->res_count);
            return -1;
        }
        if (s->res_count <= 0) {
            fprintf(stderr, "rpmb_dev: Receive count too small: %d\n",
                    s->res_count);
            return -1;
        }

        ret = recv_until(conn_sock, &s->cmd_count, sizeof(s->cmd_count));
        if (ret != sizeof(s->cmd_count)) {
            fprintf(stderr, "rpmb_dev: Failed to read cmd_count");
            return -1;
        }

        if (s->cmd_count == 0) {
            fprintf(stderr, "rpmb_dev: Must contain at least one command\n");
            return -1;
        }

        if (s->cmd_count > MAX_PACKET_COUNT) {
            fprintf(stderr, "rpmb_dev: Command count is too large\n");
            return -1;
        }

        size_t cmd_size = s->cmd_count * sizeof(s->cmd[0]);
        ret = recv_until(conn_sock, s->cmd, cmd_size);
        if (ret != (int)cmd_size) {
            fprintf(stderr,
                    "rpmb_dev: Failed to read command: "
                    "cmd_size: %zu ret: %d, %s\n",
                    cmd_size, ret, strerror(errno));
            return -1;
        }

        rpmb_dev_process_cmd(s);

        size_t resp_size = sizeof(s->res[0]) * s->res_count;
        ret = send(conn_sock, s->res, resp_size, 0);
        if (ret != (int)resp_size) {
            fprintf(stderr, "rpmb_dev: Failed to send response: %d, %s\n", ret,
                    strerror(errno));
            return -1;
        }
    }
}

void usage(const char* argv0) {
    fprintf(stderr, "Usage: %s [-d|--dev] <datafile> [--sock] <socket_path>\n",
            argv0);
    fprintf(stderr,
            "or:    %s [-d|--dev] <datafile> [--size <size>] [--key key]\n",
            argv0);
}

int main(int argc, char** argv) {
    struct rpmb_dev_state s = {0};
    int ret;
    int cmdres_sock;
    struct sockaddr_un cmdres_sockaddr;
    const char* data_file_name = NULL;
    const char* socket_path = NULL;
    int open_flags;
    int init = false;

    struct option long_options[] = {{"size", required_argument, 0, 0},
                                    {"key", required_argument, 0, 0},
                                    {"sock", required_argument, 0, 0},
                                    {"dev", required_argument, 0, 'd'},
                                    {"init", no_argument, &init, true},
                                    {"verbose", no_argument, &verbose, true},
                                    {0, 0, 0, 0}};

    memset(&s.header, 0, sizeof(s.header));

    while (1) {
        int c;
        int option_index = 0;
        c = getopt_long(argc, argv, "d:", long_options, &option_index);
        if (c == -1) {
            break;
        }

        switch (c) {
        /* long args */
        case 0:
            switch (option_index) {
            /* size */
            case 0:
                s.header.max_block = atoi(optarg) - 1;
                break;
            /* key */
            case 1:
                for (size_t i = 0; i < sizeof(s.header.key.byte); i++) {
                    if (!optarg) {
                        break;
                    }
                    s.header.key.byte[i] = strtol(optarg, &optarg, 16);
                    s.header.key_programmed = 1;
                }
                break;
            /* sock */
            case 2:
                socket_path = optarg;
                break;
            }
            break;
        /* dev */
        case 'd':
            data_file_name = optarg;
            break;
        default:
            usage(argv[0]);
            return EXIT_FAILURE;
        }
    }

    /*
     * We always need a data file, and at exactly one of --init or --sock
     * must be specified.
     */
    if (!data_file_name || (!init == !socket_path)) {
        usage(argv[0]);
        return EXIT_FAILURE;
    }

    open_flags = O_RDWR;
    if (init) {
        open_flags |= O_CREAT | O_TRUNC;
    }
    s.data_fd = open(data_file_name, open_flags, S_IWUSR | S_IRUSR);
    if (s.data_fd < 0) {
        fprintf(stderr, "rpmb_dev: Failed to open rpmb data file, %s: %s\n",
                data_file_name, strerror(errno));
        return EXIT_FAILURE;
    }

    if (init) {
        /* Create new rpmb data file */
        if (s.header.max_block == 0) {
            s.header.max_block = 512 - 1;
        }
        ret = write(s.data_fd, &s.header, sizeof(s.header));
        if (ret != sizeof(s.header)) {
            fprintf(stderr,
                    "rpmb_dev: Failed to write rpmb data file: %d, %s\n", ret,
                    strerror(errno));
            return EXIT_FAILURE;
        }
        return EXIT_SUCCESS;
    }

    ret = read(s.data_fd, &s.header, sizeof(s.header));
    if (ret != sizeof(s.header)) {
        fprintf(stderr, "rpmb_dev: Failed to read rpmb data file: %d, %s\n",
                ret, strerror(errno));
        return EXIT_FAILURE;
    }

    cmdres_sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (cmdres_sock < 0) {
        fprintf(stderr,
                "rpmb_dev: Failed to create command/response socket: %s\n",
                strerror(errno));
        return EXIT_FAILURE;
    }

    cmdres_sockaddr.sun_family = AF_UNIX;
    strncpy(cmdres_sockaddr.sun_path, socket_path,
            sizeof(cmdres_sockaddr.sun_path));

    ret = bind(cmdres_sock, (struct sockaddr*)&cmdres_sockaddr,
               sizeof(struct sockaddr_un));
    if (ret < 0) {
        fprintf(stderr,
                "rpmb_dev: Failed to bind command/response socket: %s: %s\n",
                socket_path, strerror(errno));
        return EXIT_FAILURE;
    }

    ret = listen(cmdres_sock, 1);
    if (ret < 0) {
        fprintf(stderr,
                "rpmb_dev: Failed to listen on command/response socket: %s\n",
                strerror(errno));
        return EXIT_FAILURE;
    }

    while (true) {
        int conn_sock = accept(cmdres_sock, NULL, NULL);
        if (conn_sock < 0) {
            fprintf(stderr, "rpmb_dev: Could not accept connection: %s\n",
                    strerror(errno));
            return EXIT_FAILURE;
        }
        ret = handle_conn(&s, conn_sock);
        close(conn_sock);
        if (ret) {
            fprintf(stderr, "rpmb_dev: Connection terminated: %d", ret);
        }
    }
}
