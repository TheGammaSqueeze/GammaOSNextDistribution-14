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

/*
 * This test TA takes an input of uint32_t and checks how close it is to a magic
 * number. For every bit that's correct starting from LSB, a new nop*() function
 * is called, i.e. a new coverage edge is discovered.
 *
 * If the magic number is guessed correctly, this app will abort, bringing down
 * all of Trusty along with it.
 */

#define TLOG_TAG "coverage-test-srv"

#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <stdlib.h>
#include <trusty_log.h>

static const struct tipc_port_acl port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
};

static const struct tipc_port port = {
        .name = "com.android.trusty.sancov.test.srv",
        .msg_max_size = sizeof(uint32_t),
        .msg_queue_len = 1,
        .acl = &port_acl,
};

#define NOP(idx) \
    __attribute__((noinline)) static void nop##idx(void) { return; }

NOP(0);
NOP(1);
NOP(2);
NOP(3);
NOP(4);
NOP(5);
NOP(6);
NOP(7);
NOP(8);
NOP(9);
NOP(10);
NOP(11);
NOP(12);
NOP(13);
NOP(14);
NOP(15);
NOP(16);
NOP(17);
NOP(18);
NOP(19);
NOP(20);
NOP(21);
NOP(22);
NOP(23);
NOP(24);
NOP(25);
NOP(26);
NOP(27);
NOP(28);
NOP(29);
NOP(30);
NOP(31);

static void (*coverage_reward[])(void) = {
        nop0,  nop1,  nop2,  nop3,  nop4,  nop5,  nop6,  nop7,
        nop8,  nop9,  nop10, nop11, nop12, nop13, nop14, nop15,
        nop16, nop17, nop18, nop19, nop20, nop21, nop22, nop23,
        nop24, nop25, nop26, nop27, nop28, nop29, nop30, nop31,
};

/*
 * Magic number is 0xdeadbeef. For every bit that's correct starting from LSB,
 * a new nop*() function is called, i.e. a new coverage edge is discovered.
 */
__attribute__((noinline)) static uint32_t check_key(uint32_t msg) {
    uint32_t magic = 0xdeadbeef;

    for (size_t i = 0; i < sizeof(msg) * 8; i++) {
        uint32_t mask = ~(((uint32_t)-1) << i);
        if ((msg & mask) == (magic & mask)) {
            coverage_reward[i]();
        } else {
            return 0;
        }
    }

    /* Abort if magic number was discovered */
    if (msg == magic) {
        abort();
    }

    return 0;
}

static int on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    int rc;
    uint32_t msg;

    rc = tipc_recv1(chan, 0, &msg, sizeof(msg));
    if (rc != (int)sizeof(msg)) {
        TLOGE("failed (%d) to receive msg\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    msg = check_key(msg);

    rc = tipc_send1(chan, &msg, sizeof(msg));
    if (rc != (int)sizeof(msg)) {
        TLOGE("failed (%d) to send msg\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

static const struct tipc_srv_ops ops = {
        .on_message = on_message,
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    rc = tipc_add_service(hset, &port, 1, 1, &ops);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize coverage test service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
