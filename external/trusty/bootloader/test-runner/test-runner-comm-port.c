/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <stddef.h>
#include <test-runner-arch.h>
#include <trusty/sysdeps.h>
#include <utils.h>
#include <virtio-console.h>
#include <virtio.h>

enum msg_type {
    MSG_LOG,
    MSG_EXIT,
};

struct msg_log {
    unsigned char size;
    char data[];
} __attribute__((__packed__));

struct msg_exit {
    char status;
} __attribute__((__packed__));

struct msg {
    unsigned char type;
    union {
        struct msg_log log;
        struct msg_exit exit;
    };
} __attribute__((__packed__));

static const char* testruner_msg_devname = "testrunner0";

static struct virtq input;
static struct virtq_raw input_raw;
static struct virtq output;
static struct virtq_raw output_raw;

#define TESTRUNNER0_BUF_SIZE 64
static char msg_buf[TESTRUNNER0_BUF_SIZE];

static int testrunner_msg_scan(struct virtio_console* console) {
    for (size_t i = 0; i < MAX_PORTS; i++) {
        if (!trusty_strcmp(testruner_msg_devname, console->ports[i].name)) {
            if (!console->ports[i].host_connected) {
                return -1;
            }

            return i;
        }
    }

    return -1;
}

int init_log(struct virtio_console* console) {
    int port_id = testrunner_msg_scan(console);
    if (port_id < 0) {
        return -1;
    }

    vq_init(&input, &input_raw, console->vio, true);
    vq_init(&output, &output_raw, console->vio, false);

    virtio_console_connect_port(console, port_id, &input, &output);

    return 0;
}

size_t host_get_cmdline(char* buf, size_t size) {
    uint32_t len;

    if (input.raw) {
        len = recv_vq(&input, buf, size);

        /*
         * Make sure message is always null terminated, message buffer
         * exceed maxium size would be dropped.
         */
        len = MIN(size - 1, len);
        buf[len] = '\0';

        return len;
    } else {
        return 0;
    }
}

void log_buf(const char* buf, size_t size) {
    size_t remain = size;
    size_t offset = 0;
    size_t copied = 0;
    struct msg* log_msg = (struct msg*)&msg_buf;

    log_msg->type = MSG_LOG;
    while (remain) {
        copied = MIN((TESTRUNNER0_BUF_SIZE - sizeof(struct msg)), remain);
        log_msg->log.size = copied;

        trusty_memcpy(log_msg->log.data, buf + offset, copied);

        /*
         * Always send all message buffers to host each time, it makes
         * host side easy to handle messages. With this logic, each
         * message received in host side contains a message header and
         * a message body only.
         */
        send_vq(&output, msg_buf, TESTRUNNER0_BUF_SIZE);

        remain -= copied;
        offset += copied;
    }
}

void host_exit(uint32_t code) {
    struct msg* exit_msg = (struct msg*)&msg_buf;

    exit_msg->type = MSG_EXIT;
    exit_msg->exit.status = code;

    send_vq(&output, msg_buf, TESTRUNNER0_BUF_SIZE);
}

void log_msg(const char* msg) {
    log_buf(msg, trusty_strlen(msg));
}

void abort_msg(const char* msg) {
    log_msg(msg);
    host_exit(2);
}
