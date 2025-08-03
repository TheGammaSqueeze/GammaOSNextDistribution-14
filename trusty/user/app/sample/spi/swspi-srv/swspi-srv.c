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

#include <interface/spi/spi.h>
#include <interface/spi/spi_loopback.h>
#include <interface/spi/spi_test.h>
#include <lib/spi/srv/srv.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>

#include "driver/swspi.h"

#define TLOG_TAG "swspi-srv"
#include <trusty_log.h>

#define SPI_MAX_MSG_SIZE 1024

/*
 * Software SPI buses/devices are connected the following way:
 * First SPI bus is shared. It has a fake and a test device connected to it.
 * Second SPI bus is dedicated. Only software loopback device is connected to
 * it.
 */
static struct spi_bus_ctx test_bus = {
        .num_devs = 2, /* pretend this bus is shared */
};

static struct spi_dev_ctx test_dev = {
        .bus = &test_bus,
};

#if WITH_SW_SPI_LOOPBACK
static struct spi_bus_ctx loopback_bus = {
        .num_devs = 1,
};

static struct spi_dev_ctx loopback_dev = {
        .bus = &loopback_bus,
        .loopback = true,
};
#endif

static const struct tipc_port_acl port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = NULL,
        .uuid_num = 0, /* allow any app to connect */
        .extra_data = NULL,
};

static const struct tipc_port ports[] = {
        {
                .name = SPI_TEST_PORT,
                .msg_max_size = SPI_MAX_MSG_SIZE,
                .msg_queue_len = 1,
                .acl = &port_acl,
                .priv = &test_dev,
        },
#if WITH_SW_SPI_LOOPBACK
        {
                .name = SPI_LOOPBACK_PORT,
                .msg_max_size = SPI_MAX_MSG_SIZE,
                .msg_queue_len = 1,
                .acl = &port_acl,
                .priv = &loopback_dev,
        },
#endif
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    rc = add_spi_service(hset, ports, countof(ports));
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize SPI test service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
