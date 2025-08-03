/*
 * Copyright (C) 2013 The Android Open Source Project
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
#define TLOG_TAG "stats-consumer"

#include <cstddef>

#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <lk/err_ptr.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <trusty/time.h>
#include <trusty_log.h>
#include <uapi/mm.h>

#include <binder/RpcServerTrusty.h>
#include <binder/RpcTransportTipcTrusty.h>
#include <stdio.h>

#include <android/trusty/stats/tz/BnStats.h>
#include <android/trusty/stats/tz/IStatsSetter.h>
#include <consumer_consts.h>
#include <consumer_ctl.h>
#include <relayer_consts.h>

using android::String16;
using android::binder::Status;
using android::frameworks::stats::VendorAtom;
using android::frameworks::stats::VendorAtomValue;
using android::trusty::stats::tz::BnStats;
using android::trusty::stats::tz::IStatsSetter;

/* How long to sleep between spin lock checks */
constexpr uint64_t kSpinSleepMs = 10;

/**
 * DOC: The consumer TA is exposing a test control port to share
 * received vendorAtoms with the stats-test TA via shared memory.
 * As soon as the test control port is connected, the consumer will connect
 * to the producer and write the received atoms into the memory address
 * shared with the stats-test TA.
 */

class StatsConsumer;

static tipc_port_acl test_ctl_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = nullptr,
        .extra_data = nullptr,
};

static tipc_port test_ctl_port = {
        .name = CONSUMER_PORT_TEST_CTL,
        .msg_max_size = sizeof(ConsumerCtlMsg),
        .msg_queue_len = 1,
        .acl = &test_ctl_port_acl,
        .priv = nullptr,
};

struct ConsumerCtx {
    void* shm_ptr;
    int atom_cnt;
    android::sp<StatsConsumer> stats_consumer;
    android::sp<IStatsSetter> istats_setter;
};

static ConsumerCtx consumer_ctx = {
        .shm_ptr = nullptr,
        .atom_cnt = -1,
};

/**
 * test_ctl_on_connect() - on test_ctl connect the connection with
 * the producer is made and vendorAtoms can be received
 */
int test_ctl_on_connect(const tipc_port* port,
                        handle_t chan,
                        const uuid* peer,
                        void** ctx_p) {
    if (consumer_ctx.atom_cnt != -1) {
        *ctx_p = nullptr;
        return ERR_BAD_STATE;
    }

    // make sure consumer is ready
    assert(consumer_ctx.stats_consumer);
    assert(!consumer_ctx.istats_setter);
    assert(!consumer_ctx.shm_ptr);
    assert(consumer_ctx.atom_cnt == -1);
    consumer_ctx.atom_cnt = 0;

    // connect to the producer
    android::sp<android::RpcSession> sess_stats_setter;
    android::sp<IStatsSetter> srv_stats_setter;

    TLOGD("Connecting to the producer via IStatsSetter\n");
    int rc = connect(RELAYER_PORT_ISTATS_SETTER_SECURE_WORLD,
                     IPC_CONNECT_WAIT_FOR_PORT);
    TLOGD("connected to IStatsSetter\n");
    if (rc < 0) {
        TLOGE("Couldn't connect to IStatsSetter::PORT\n");
        return rc;
    }
    android::sp<android::RpcSession> sess = android::RpcSession::make(
            android::RpcTransportCtxFactoryTipcTrusty::make());
    if (sess == nullptr) {
        TLOGE("Couldn't make RPC session\n");
        return ERR_GENERIC;
    }
    android::base::unique_fd chan_fd;
    chan_fd.reset(rc);
    android::status_t status = sess->setupPreconnectedClient(
            std::move(chan_fd), []() { return android::base::unique_fd(); });
    if (status != android::OK) {
        TLOGE("error during setupPreconnectedClient\n");
        return ERR_GENERIC;
    }
    android::sp<android::IBinder> root = sess->getRootObject();
    if (root == nullptr) {
        TLOGE("Couldn't get root object\n");
        return ERR_GENERIC;
    }
    consumer_ctx.istats_setter = IStatsSetter::asInterface(root);
    assert(consumer_ctx.istats_setter);
    *ctx_p = &consumer_ctx;

    return NO_ERROR;
}

/**
 * test_ctl_on_message() - test_ctl on_message allows the consumer
 * to receive the shared memory region to which received vendorAtoms will
 * be copied.
 */
static int test_ctl_on_message(const tipc_port* port,
                               handle_t chan,
                               void* ctx_) {
    assert(ctx_);
    assert(port == &test_ctl_port);
    ConsumerCtx* ctx = static_cast<ConsumerCtx*>(ctx_);
    ConsumerCtlMsg request;

    handle_t shm_handle = INVALID_IPC_HANDLE;
    iovec iov = {
            .iov_base = &request,
            .iov_len = sizeof(request),
    };
    ipc_msg msg = {
            .num_iov = 1,
            .iov = &iov,
            .num_handles = 1,
            .handles = &shm_handle,
    };

    ipc_msg_info msg_inf;
    int rc = get_msg(chan, &msg_inf);
    if (rc) {
        return rc;
    }

    if (msg_inf.num_handles != 1) {
        TLOGE("Message had no handles\n");
        put_msg(chan, msg_inf.id);
        return ERR_INVALID_ARGS;
    }

    rc = read_msg(chan, msg_inf.id, 0, &msg);
    put_msg(chan, msg_inf.id);
    if (static_cast<size_t>(rc) != sizeof(request)) {
        TLOGE("Failed to read message (rc=%d)\n", rc);
        close(shm_handle);
        return ERR_INVALID_ARGS;
    }

    size_t page_size = PAGE_SIZE;  // getauxval(AT_PAGESZ);
    switch (request.cmd) {
    case CONSUMER_CTL_SHM_SHARE:
        assert(!ctx->shm_ptr);
        ctx->shm_ptr =
                mmap(0, page_size, PROT_READ | PROT_WRITE, 0, shm_handle, 0);
        if (ctx->shm_ptr == MAP_FAILED) {
            close(shm_handle);
            ctx->shm_ptr = nullptr;
            TLOGE("Failed to mmap handle (rc=%d)\n", rc);
            return ERR_NO_MEMORY;
        }
        break;
    case CONSUMER_CTL_SHM_RECLAIM:
        if (ctx->shm_ptr) {
            munmap((void*)ctx->shm_ptr, page_size);
            ctx->shm_ptr = nullptr;
        }
        break;
    default:
        TLOGE("Invalid cmd %d\n", request.cmd);
    }

    close(shm_handle);

    return NO_ERROR;
}

static void test_ctl_on_channel_cleanup(void* ctx_) {
    return;
}

class StatsConsumer : public BnStats {
public:
    Status reportVendorAtom(const VendorAtom& vendorAtom) {
        TLOGD("reportVendorAtom atomId=%d.\n", vendorAtom.atomId);
        if (consumer_ctx.shm_ptr) {
            volatile ShmContent* content = (ShmContent*)consumer_ctx.shm_ptr;

            // Spin and sleep until the other side consumes the active value
            while (content->full.load(std::memory_order_acquire)) {
                trusty_nanosleep(0, 0, MS_TO_NS(kSpinSleepMs));
            }

            TLOGD("content->atom_id=%d.\n", content->atom_id);
            TLOGD("content->reverse_domain_name=%s.\n",
                  content->reverse_domain_name);
            content->atom_id = vendorAtom.atomId;

            size_t last_atom_str_idx = SHM_CONTENT_VENDOR_ATOM_STR_SIZE - 1;
            strncpy((char*)content->reverse_domain_name,
                    android::String8(vendorAtom.reverseDomainName).c_str(),
                    last_atom_str_idx);
            content->reverse_domain_name[last_atom_str_idx] = '\0';

            int idx = 0;
            for (const auto& input_value : vendorAtom.values) {
                VendorAtomValue::Tag tag = input_value.getTag();
                TLOGD("vendorAtom.values[%d].getTag = %d.\n", idx, tag);

                volatile ShmVendorAtomValue& atom_val =
                        content->vendor_atom_values[idx];
                switch (tag) {
                case VendorAtomValue::intValue:
                    atom_val.i = input_value.get<VendorAtomValue::intValue>();
                    break;
                case VendorAtomValue::longValue:
                    atom_val.l = input_value.get<VendorAtomValue::longValue>();
                    break;
                case VendorAtomValue::floatValue:
                    atom_val.f = input_value.get<VendorAtomValue::floatValue>();
                    break;
                case VendorAtomValue::stringValue: {
                    auto s = android::String8(
                            input_value.get<VendorAtomValue::stringValue>());
                    char* buf = const_cast<char*>(atom_val.s);
                    strncpy(buf, s.c_str(), last_atom_str_idx);
                    buf[last_atom_str_idx] = '\0';
                    break;
                }
                default:
                    TLOGE("Unexpected vendorAtom.values[%d].getTag = %d.\n",
                          idx, tag);
                }
                atom_val.tag = tag;

                if (++idx >= SHM_CONTENT_VENDOR_ATOM_VALUES) {
                    break;
                }
            }
            content->full.store(true, std::memory_order_release);
        }
        return Status::ok();
    }
};

static tipc_srv_ops test_ctl_ops = {
        .on_connect = test_ctl_on_connect,
        .on_message = test_ctl_on_message,
        .on_channel_cleanup = test_ctl_on_channel_cleanup,
};

int main(void) {
    TLOGI("Starting StatsConsumer\n");

    tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    const auto port_acl = android::RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };

    TLOGE("Creating Consumer (exposing IStats)\n");
    consumer_ctx.stats_consumer = android::sp<StatsConsumer>::make();

    int rc = tipc_add_service(hset, &test_ctl_port, 1, 1, &test_ctl_ops);
    if (rc < 0) {
        return rc;
    }

    uint32_t timeout = INFINITE_TIME;
    do {
        // Changing timeout interval from infinite to simulate a timer
        rc = tipc_handle_event(hset, timeout);
        if (consumer_ctx.shm_ptr) {
            TLOGD("interacting with producer\n");
            consumer_ctx.istats_setter->setInterface(
                    consumer_ctx.stats_consumer);
            timeout = TIME_OUT_INTERVAL_MS;
            TLOGD("timeout = TIME_OUT_INTERVAL_MS\n");
        } else {
            timeout = INFINITE_TIME;
            TLOGD("timeout = INFINITE_TIME\n");
        }
    } while (rc == 0 || timeout == TIME_OUT_INTERVAL_MS);

    TLOGE("stats-consumer service died\n");

    return rc;
}
