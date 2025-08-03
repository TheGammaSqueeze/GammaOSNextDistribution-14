/*
 * Copyright (C) 2018 Knowles Electronics
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

#define LOG_TAG "ia_tunneling_hal"
#define LOG_NDEBUG 0

#include <fcntl.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <log/log.h>
#include <linux/mfd/adnc/iaxxx-tunnel-intf.h>
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>
#include "tunnel.h"

#define TUNNELING_DEVICE "/dev/tunnel0"
#define FUNCTION_ENTRY_LOG ALOGV("Entering %s", __func__);
#define FUNCTION_EXIT_LOG ALOGV("Exiting %s", __func__);

struct ia_tunneling_hal {
    int tunnel_dev;
};

struct ia_tunneling_hal* ia_start_tunneling(int buffering_size __unused)
{
    struct ia_tunneling_hal *thdl;

    FUNCTION_ENTRY_LOG;

    thdl = (struct ia_tunneling_hal *)malloc(sizeof(struct ia_tunneling_hal));
    if (thdl == NULL) {
        ALOGE("%s: ERROR Failed to allocate memory of ia_tunneling_hal",
            __func__);
        return NULL;
    }

    thdl->tunnel_dev = open(TUNNELING_DEVICE, O_RDONLY);
    if (-1 == thdl->tunnel_dev) {
        ALOGE("%s: ERROR Failed to open the tunneling device - %s",
            __func__, strerror(errno));
        free(thdl);
        return NULL;
    }

    return thdl;
}

int ia_stop_tunneling(struct ia_tunneling_hal *thdl)
{
    FUNCTION_ENTRY_LOG;

    if (thdl) {
        close(thdl->tunnel_dev);
        thdl->tunnel_dev = 0;
        free(thdl);
    }

    return 0;
}

int ia_enable_tunneling_source(struct ia_tunneling_hal *thdl,
                            unsigned int src_id,
                            unsigned int tnl_mode,
                            unsigned int tnl_encode)
{
    FUNCTION_ENTRY_LOG;
    struct tunlMsg tm;
    int err = 0;

    if (thdl == NULL) {
        ALOGE("%s: ERROR Tunneling hdl is NULL", __func__);
        err = -EIO;
        goto exit;
    }

    tm.tunlSrc = src_id;
    tm.tunlMode = tnl_mode;
    tm.tunlEncode = tnl_encode;
    err = ioctl(thdl->tunnel_dev, TUNNEL_SETUP, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel setup failed %s", __func__, strerror(errno));
    }

exit:
    return err;
}

int ia_disable_tunneling_source(struct ia_tunneling_hal *thdl,
                                unsigned int src_id,
                                unsigned int tunl_mode,
                                unsigned int tunl_encode)
{
    FUNCTION_ENTRY_LOG;
    struct tunlMsg tm;
    int err = 0;

    if (thdl == NULL) {
        ALOGE("%s: ERROR Tunneling hdl is NULL", __func__);
        err = -EIO;
        goto exit;
    }

    tm.tunlSrc = src_id;
    tm.tunlMode = tunl_mode;
    tm.tunlEncode = tunl_encode;
    err = ioctl(thdl->tunnel_dev, TUNNEL_TERMINATE, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel terminate failed %s",
            __func__, strerror(errno));
    }

exit:
    return err;
}

int ia_read_tunnel_data(struct ia_tunneling_hal *thdl,
                        void *buf,
                        int buf_sz)
{
    int read_bytes;

    if ((buf == NULL) || (buf_sz <= 0)) {
        ALOGE("%s: ERROR Invalid buffer or buffer size", __func__);
        return -EINVAL;
    }

    if (thdl == NULL) {
        ALOGE("%s: ERROR Tunneling hdl is NULL", __func__);
        return -EIO;
    }

    read_bytes = read(thdl->tunnel_dev, buf, buf_sz);
    if (read_bytes == 0) {
        ALOGE("%s: Warning zero bytes read from tunneling device, "
            "trying again..", __func__);
    }

    return read_bytes;
}

int ia_set_tunnel_out_buf_threshold(struct ia_tunneling_hal *thdl,
                                    uint32_t threshold)
{
    int err;

    FUNCTION_ENTRY_LOG;

    if (thdl == NULL) {
        ALOGE("%s: ERROR Tunneling hdl is NULL", __func__);
        err = -EIO;
        goto exit;
    }

    err = ioctl(thdl->tunnel_dev, TUNNEL_SET_EVENT_THRESHOLD,
                threshold);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel terminate failed %s",
            __func__, strerror(errno));
    }

exit:
    return err;
}
