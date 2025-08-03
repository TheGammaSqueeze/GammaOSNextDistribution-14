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

#define LOG_TAG "spi_reliability_test"

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <string.h>
#include <math.h>

#include <cutils/log.h>

#include <linux/mfd/adnc/iaxxx-odsp.h>

#define ODSP_NODE "/dev/iaxxx-odsp-celldrv"
#define PASS_THROUGH_PACKAGE "PassthruPackageSPITest.bin"
#define RANDOM_BYTES_FILE "/data/data/random.bin"

#define PTP_PKG_ID      (9)
#define PTP_PROC_ID     (0)
#define PTP_PLG_IDX     (0)
#define PTP_BLOCK_ID    (1)
#define PTP_INST_ID     (12)
#define PTP_PRIORITY    (1)

int main() {
    FILE *odsp_node = NULL;
    FILE *fp = NULL;
    struct iaxxx_pkg_mgmt_info pkg_info;
    struct iaxxx_plugin_param_blk ppb;
    struct iaxxx_plugin_info pi;
    uint32_t ptp_id;
    int len = 0;
    void *buf = NULL, *read_buf = NULL;
    int err = 0;

    if ((fp = fopen(RANDOM_BYTES_FILE, "rb")) == NULL) {
        ALOGE("Failed to open random bytes file");
        err = -EIO;
        goto exit;
    }
    fseek(fp, 0, SEEK_END);
    len = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    buf = malloc(len);
    if (NULL == buf) {
        ALOGE("Failed to alloc memory");
        err = -ENOMEM;
        goto exit;
    }

    read_buf = malloc(len);
    if (NULL == read_buf) {
        ALOGE("Failed to alloc memory");
        err = -ENOMEM;
        goto exit;
    }

    fread(buf, 1, len, fp);
    fclose(fp);
    ALOGD("Successfully read random byte file");

    if((odsp_node = fopen(ODSP_NODE, "rw")) == NULL) {
        ALOGE("file %s open for write error: %s\n", ODSP_NODE,
                strerror(errno));
        err = -EIO;
        goto exit;
    }

    strcpy(pkg_info.pkg_name, PASS_THROUGH_PACKAGE);
    pkg_info.pkg_id = PTP_PKG_ID;
    pkg_info.proc_id = PTP_PROC_ID;
    err = ioctl(fileno(odsp_node), ODSP_LOAD_PACKAGE, (unsigned long) &pkg_info);
    if (-1 == err && EEXIST != errno) {
        ALOGE("%s: ERROR: ODSP_LOAD_PACKAGE failed %d(%s)", __func__, errno, strerror(errno));
        goto exit;
    }
    ALOGD("%s: Pass through package loaded 0x%x\n", __func__, pkg_info.proc_id);
    ptp_id = pkg_info.proc_id;

    pi.plg_idx  = PTP_PLG_IDX;
    pi.pkg_id   = ptp_id;
    pi.block_id = PTP_BLOCK_ID;
    pi.inst_id  = PTP_INST_ID;
    pi.priority = PTP_PRIORITY;
    err = ioctl(fileno(odsp_node), ODSP_PLG_CREATE, (unsigned long) &pi);
    if (-1 == err && EEXIST != errno) {
        ALOGE("%s: ERROR: ODSP_PLG_CREATE IOCTL failed to create VQ Plugin with error %d(%s)", __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("%s: Pass through plugin created", __func__);

    ppb.block_id = PTP_BLOCK_ID;
    ppb.inst_id = PTP_INST_ID;
    ppb.id = 0;
    ppb.param_size = len;
    ppb.param_blk = (uintptr_t) buf;
    err = ioctl(fileno(odsp_node), ODSP_PLG_SET_PARAM_BLK, &ppb);
    if (-1 == err) {
        ALOGE("%s: ERROR: ODSP_PLG_SET_PARAM_BLK IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("%s: Random byte set param block completed", __func__);

    ppb.block_id = PTP_BLOCK_ID;
    ppb.inst_id = PTP_INST_ID;
    ppb.id = 0;
    ppb.param_size = len;
    ppb.param_blk = (uintptr_t) read_buf;
    err = ioctl(fileno(odsp_node), ODSP_PLG_GET_PARAM_BLK, &ppb);
    if (-1 == err) {
        ALOGE("%s: ERROR: ODSP_PLG_GET_PARAM_BLK IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("%s: Random byte get param block completed", __func__);

    err = memcmp(buf, read_buf, len);
    if (0 != err) {
        ALOGE("ERROR: Set/Get buffers were different");
        err = -EINVAL;
        goto exit;
    }

    fp = fopen("/data/data/getbuf.bin", "wb");
    fwrite(read_buf, 1, len, fp);
    fclose(fp);

    ALOGD("%s: Set/Get buffers matched", __func__);

exit:
    if (odsp_node)
        fclose(odsp_node);

    if (read_buf)
        free (read_buf);

    if (buf)
        free (buf);

    if (fp)
        fclose(fp);

    return err;
}
