/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define TLOG_TAG "cfi-crasher"

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <cfi-crasher.h>
#include <cfi_crasher_consts.h>

static int crasher_on_message(const struct tipc_port* port,
                              handle_t chan,
                              void* ctx);

static struct tipc_port_acl crasher_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port crasher_port = {
        .name = CFI_CRASHER_PORT,
        .msg_max_size = sizeof(struct crasher_msg),
        .msg_queue_len = 1,
        .acl = &crasher_port_acl,
        .priv = NULL,
};

typedef int (*int_func)(int);

/*
 * function against which all subsequent functions are compared.
 * cfi-icall ensures that control is only transferred to a function with
 * the same static type.
 */
__NO_INLINE static int crasher_int_func(int arg) {
    TLOG("function with int return type and 1 int arg ran\n");
    return arg;
}

__NO_INLINE static float crasher_float_func(float arg) {
    TLOG("function with float return type and 1 float arg ran\n");
    return arg;
}

/*
 * We need a global variable for the channel handle since crasher_void
 * doesn't take arguments. Additionally, we need a global variable for
 * the program counter in order to calculate the address for jumping
 * partway into crasher_not_entry_func.
 */
handle_t global_chan;
void* volatile global_pc;
__NO_INLINE static void crasher_void(void) {
    TLOG("crasher_not_entry_func ran, logging from crasher_void\n");
    /*
     * We suppress sending a tipc message in the case where global_pc is
     * set since in this case crasher_not_entry_func was properly called
     * so we expect to send CRASHER_ENTRY back to the client and want to
     * suppress a second CRASHER_VOID message. Also, crasher_not_entry_func
     * is called properly by CRASHER_NOT_ENTRY in order to first calculate
     * the offset for jumping partway into the function, so we do not want
     * to send a tipc message in this case. This if statement is placed
     * in crasher_void instead of crasher_not_entry_func to minimize
     * conditionals in a function entered partway, as this could lead
     * to crashing even without CFI enabled.
     */
    if (global_pc) {
        return;
    }

    struct crasher_msg msg;
    msg.cmd = CRASHER_VOID;
    /*
     * Because entering crasher_not_entry_func partway skips the
     * preamble, regardless of whether CFI is enabled the app will
     * crash. We need to know whether the app crashes immediately
     * after jumping to crasher_not_entry_func due to CFI, or if
     * it crashed because the stack is corrupted. So we send a
     * message back to communicate that the app is not crashing
     * due to CFI if crasher_void runs.
     */
    int ret = tipc_send1(global_chan, &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to send message (%d)\n", ret);
    }
}

/*
 * Returns the program counter for the caller's context. We call
 * get_pc from the beginning of crasher_not_entry_func in order to
 * obtain a natural entry point partway through crasher_not_entry_func.
 */
__NO_INLINE static void* get_pc(void) {
    return __builtin_extract_return_addr(__builtin_return_address(0));
}

__NO_INLINE static int crasher_not_entry_func(int arg) {
    global_pc = get_pc();
    /*
     * We place logging in a separate function call since otherwise
     * arguments of TLOG are corrupted by jumping into the function partway.
     */
    crasher_void();

    /*
     * A segfault occurs if the indirect call skips the function preamble.
     * To ensure the test app does not interpret this segfault as CFI
     * crashing the app, we send a message from crasher_void. This way, the
     * test app sees the message before seeing the channel has closed,
     * preventing the test from incorrectly passing. With CFI enabled,
     * crasher_void should never run if a test attempts to enter
     * crasher_not_entry_func partway. We sleep for 100 ms so that test app sees
     * the response message and channel closed event as separate events.
     */
    trusty_nanosleep(0, 0, 100 * 1000 * 1000);

    /* Log point to prevent tail-call optimisation to trusty_nanosleep(),
     * which can cause a crash before the sleep with certain CFI techniques
     * such as PAC.
     */
    TLOGI("crasher_not_entry_func done\n");

    return arg;
}

/*
 * We call this function instead of directly calling crasher_float_func
 * in crasher_on_message since no_sanitize("cfi-icall") must be specified
 * for the function which calls crasher_float_func in order to exempt
 * it from CFI.
 */
__NO_INLINE static void __attribute__((no_sanitize("cfi-icall")))
crasher_call_exclude_float_func(void) {
    TLOG("crasher_call_exclude_float_func ran, calling crasher_float_func\n");
    ((int_func)crasher_float_func)(0);
}

/*
 * Similar to crasher_call_exclude_float_func,
 * crasher_call_exclude_not_entry_func is a wrapper function which ensures
 * a CFI-exempt context when attempting to enter crasher_not_entry_func
 * partway.
 */
__NO_INLINE static void __attribute__((no_sanitize("cfi-icall")))
crasher_call_exclude_not_entry_func(void) {
    TLOG("crasher_call_exclude_not_entry_func ran, entering crasher_not_entry_func partway\n");
    /*
     * Because global_pc is global, we reset it before each test to make sure
     * different tests do not interfere with each other.
     */
    global_pc = NULL;
    crasher_not_entry_func(0); /* set global_pc by calling correctly */
    /*
     * We need to reset global_pc so the crasher_void tipc message is not
     * suppressed. We save global_pc into crasher_not_entry_func_partway before
     * resetting  it.
     */
    void* crasher_not_entry_func_partway = global_pc;
    global_pc = NULL;
    ((int_func)(crasher_not_entry_func_partway))(0);
}

static int crasher_on_message(const struct tipc_port* port,
                              handle_t chan,
                              void* ctx) {
    struct crasher_msg msg;

    int ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to receive message (%d)\n", ret);
        return ret;
    }

    switch (msg.cmd) {
    /*
     * CRASHER_NOP test checks that the internal testing machinery
     * is working properly even when crasher_on_message does not transfer
     * control flow to another internal function. Since the last 2 tests
     * are expected to crash the app when CFI is enabled, we need to make
     * sure the app isn't just always crashing.
     */
    case CRASHER_NOP:
        TLOGI("nop\n");
        break;
    /*
     * Similar to CRASHER_NOP, CRASHER_CORRECT is expected to not crash
     * the app regardless of whether CFI is enabled since we are calling
     * a function with the expected signature. We want to ensure the app
     * is not just always crashing.
     */
    case CRASHER_CORRECT:
        TLOGI("call crasher_int_func\n");
        /*
         * The argument is arbitrary, we just follow a convention of using
         * 0, for simplicity.
         */
        crasher_int_func(0);
        break;
    /*
     * Similar to how CRASHER_CORRECT ensures the app doesn't crash when
     * calling crasher_int_func correctly, CRASHER_ENTRY ensures the app
     * doesn't crash when calling crasher_not_entry_func correctly.
     */
    case CRASHER_ENTRY:
        TLOGI("call crasher_not_entry_func\n");
        global_chan = chan;
        global_pc = NULL;
        crasher_not_entry_func(0);
        break;
    /*
     * Part of testing that CFI is working properly includes testing that
     * exempting certain functions from CFI works as intended.
     * CRASHER_EXCLUDE_WRONG_TYPE ensures that calling crasher_float_func
     * from a CFI-exempt context does not crash the app. This test essentially
     * checks that the no_sanitize("cfi-icall") function decorator on
     * crasher_call_exclude_float_func works as intended.
     */
    case CRASHER_EXCLUDE_WRONG_TYPE:
        TLOGI("call crasher_call_exclude_float_func\n");
        crasher_call_exclude_float_func();
        break;
    /*
     * Similar to CRASHER_EXCLUDE_WRONG_TYPE, CRASHER_EXCLUDE_NOT_ENTRY
     * tests that entering crasher_not_entry_func partway from a CFI-exempt
     * context does not cause a CFI-induced crash.
     */
    case CRASHER_EXCLUDE_NOT_ENTRY:
        TLOGI("call crasher_call_exclude_not_entry_func\n");
        global_chan = chan;
        crasher_call_exclude_not_entry_func();
        break;
    /*
     * CRASHER_WRONG_TYPE is the first test expected to crash when CFI
     * is enabled. CFI ensures that control is only transferred to functions
     * with the expected signature. Since we cast crasher_float_func to an
     * int_func function pointer when it is actually a float_func, we expect
     * CFI to crash the app when transferring control to crasher_float_func.
     * If the app does not crash, then cfi-icall is not enabled.
     */
    case CRASHER_WRONG_TYPE:
        TLOGI("call crasher_float_func\n");
        ((int_func)crasher_float_func)(0);
        break;
    /*
     * In addition to checking the function signature, cfi-icall ensures that
     * control is only transferred to valid function entry points.
     * When CFI is enabled, control should not be transferred to the middle of
     * crasher_not_entry_func.
     */
    case CRASHER_NOT_ENTRY:
        TLOGI("enter crasher_not_entry_func partway\n");
        global_chan = chan;
        global_pc = NULL;
        crasher_not_entry_func(0);
        void* crasher_not_entry_func_partway = global_pc;
        global_pc = NULL;
        ((int_func)(crasher_not_entry_func_partway))(0);
        break;
    default:
        TLOGE("Bad command: %d\n", msg.cmd);
        msg.cmd = CRASHER_BAD_CMD;
    }
    /*
     * We echo the incoming command in the case where the crasher app
     * runs the test without crashing. This is effectively saying "did
     * not crash when executing command X."
     */
    ret = tipc_send1(chan, &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to send message (%d)\n", ret);
        return ret < 0 ? ret : ERR_IO;
    }

    return 0;
}

static struct tipc_srv_ops crasher_ops = {
        .on_message = crasher_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    int rc = tipc_add_service(hset, &crasher_port, 1, 1, &crasher_ops);
    if (rc < 0) {
        TLOGE("Failed to add service (%d)\n", rc);
        return rc;
    }

    /* if app exits, kernel will log that */
    return tipc_run_event_loop(hset);
}
