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

#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <getopt.h>
#include <stdbool.h>
#include <arpa/inet.h>
#include <limits.h>
#include <unistd.h>
#include <linux/mfd/adnc/iaxxx-debug-intf.h>

#define LOG_TAG "ia_dump_registers"

#include <log/log.h>

#define DEBUG_DEV                   "/dev/debug0"
#define GETOPT_HELP_CHAR            (CHAR_MIN - 2)
#define REG_ADDR                    "Register Address"
#define REG_VAL                     "Register Value"
#define SRB_REG                     "SRB Registers"
#define ARB_REG                     "ARB Registers"
#define REG_OUT_FILE                "/data/data/reg_dump.txt"
#define CIRC_OUT_FILE_CM4           "/data/data/circular_buf_cm4.bin"
#define CIRC_OUT_FILE_DMX           "/data/data/circular_buf_dmx.bin"
#define CIRC_OUT_FILE_HMD           "/data/data/circular_buf_hmd.bin"

//#define ENABLE_DEBUG_PRINTS

static struct option const long_options[] = {
    {"stdout", no_argument, NULL, 'o'},
    {"regdump", no_argument, NULL, 'r'},
    {"circbuf", no_argument, NULL, 'c'},
    {"help", no_argument, NULL, GETOPT_HELP_CHAR},
    {NULL, 0, NULL, 0}
};

void usage(void)
{
    fputs("\
    USAGE -\n\
    -------\n\
    1) dump_debug_info -c\n\
    2) dump_debug_info -r\n\
    3) dump_debug_info -l <module id> <log level>\n\
    4) dump_debug_info -l <module id>\n\
    5) dump_debug_info -m <processor id> <mode>\n\
    6) dump_debug_info -m <processor id>\n\
    7) dump_debug_info -t <tunnel id>\n\
    8) dump_debug_info -n <channel id>\n\
    9) dump_debug_info -s <stream id>\n\
    \n\
    In the first form, it dumps the circular buffer logging for Rome, the output is\n\
    dumped to the file /data/data/circular_buf_(cm4/dmx/hmd).bin\n\
    In the second form, it dumps all the SRB and ARB registers, the output is\n\
    dumped to the file /data/data/reg_dump.txt\n\
    In the third form, it sets the log level to a particular module id\n\
    In the fourth form, it gets the log level for the requested modele id\n\
    In the fifth form, it sets the mode to a particular processor id\n\
    In the sixth form, it gets the mode for the requested processor id\n\
    In the seventh form, it gets the tunnel information for the requested tunnel id [0, 31]\n\
    In the eighth form, it gets the channel information for the requested channel id [0, 31]\n\
    In the ninth form, it gets the stream information for the requested stream id [0, 15]\n\
    ", stdout);

    fputs("\
    Options -\n\
    ---------\n\
    -c      Dump the circular buffer\n\
    -r      Dump all SRB and ARB registers\n\
    -o      Dump to stdout (Works only with -r option)\n\
    -l      Set/Get the log level for a particular module\n\
    -m      Set/Get the mode for a particular processor id\n\
    -t      Dump the tunnel information for a particular tunnel id [0, 31]\n\
    -n      Dump the channel information for a particular channel id [0, 31]\n\
    -s      Dump the stream information for a particular stream id [0, 15]\n\
\n\
    Module ids -\n\
    -----------\n\
    enum iaxxx_debug_module_ids {\n\
        IAXXX_DBG_MODULE_ID_ASSERT_LOG = 0,\n\
        IAXXX_DBG_MODULE_ID_ACCDETMGR_LOG = 1,\n\
        IAXXX_DBG_MODULE_ID_BATTERYMGR_LOG = 2,\n\
        IAXXX_DBG_MODULE_ID_BLUETOOTHMGR_LOG = 3,\n\
        IAXXX_DBG_MODULE_ID_BUTTONMGR_LOG = 4,\n\
        IAXXX_DBG_MODULE_ID_CODECMGR_LOG = 5,\n\
        IAXXX_DBG_MODULE_ID_CTRLMGR_LOG = 6,\n\
        IAXXX_DBG_MODULE_ID_DMAMGR_LOG = 7,\n\
        IAXXX_DBG_MODULE_ID_EVTMGR_LOG = 8,\n\
        IAXXX_DBG_MODULE_ID_FLASHMGR_LOG = 9,\n\
        IAXXX_DBG_MODULE_ID_LEDMGR_LOG = 10,\n\
        IAXXX_DBG_MODULE_ID_POWERMGR_LOG = 11,\n\
        IAXXX_DBG_MODULE_ID_STREAMMGR_LOG = 12,\n\
        IAXXX_DBG_MODULE_ID_SENSORMGR_LOG = 13,\n\
        IAXXX_DBG_MODULE_ID_TUNNELMGR_LOG = 14,\n\
        IAXXX_DBG_MODULE_ID_USBMGR_LOG = 15,\n\
        IAXXX_DBG_MODULE_ID_PLUGINMGR_LOG = 16,\n\
        IAXXX_DBG_MODULE_ID_PLUGINVM_LOG = 17,\n\
        IAXXX_DBG_MODULE_ID_PACKAGEUTILS_LOG = 18,\n\
        IAXXX_DBG_MODULE_ID_ENDPOINT_LOG = 19,\n\
        IAXXX_DBG_MODULE_ID_PUTMSG_LOG = 20,\n\
        IAXXX_DBG_MODULE_ID_CONTROLLER_LOG = 21,\n\
        IAXXX_DBG_MODULE_ID_MIPSPROFILER_LOG = 22,\n\
        IAXXX_DBG_MODULE_ID_DEBUGMONITOR_LOG = 23,\n\
        IAXXX_DBG_MODULE_ID_SSPDRV_LOG = 24,\n\
        IAXXX_DBG_MODULE_ID_AFDRV_LOG = 25,\n\
        IAXXX_DBG_MODULE_ID_SPIDRV_LOG = 26,\n\
        IAXXX_DBG_MODULE_ID_I2CDRV_LOG = 27,\n\
        IAXXX_DBG_MODULE_ID_A400DRV_LOG = 28,\n\
        IAXXX_DBG_MODULE_ID_ADAU1361DRV_LOG = 29,\n\
        IAXXX_DBG_MODULE_ID_MAX98090DRV_LOG = 30,\n\
        IAXXX_DBG_MODULE_ID_BQ27425DRV_LOG = 31,\n\
        IAXXX_DBG_MODULE_ID_USBDRV_LOG = 32,\n\
        IAXXX_DBG_MODULE_ID_CSR8811_LOG = 33,\n\
        IAXXX_DBG_MODULE_ID_CYW20707DRV_LOG = 34,\n\
        IAXXX_DBG_MODULE_ID_BUTTONDRV_LOG = 35,\n\
        IAXXX_DBG_MODULE_ID_LEDDRV_LOG = 36,\n\
        IAXXX_DBG_MODULE_ID_TIMERDRV_LOG = 37,\n\
        IAXXX_DBG_MODULE_ID_UARTDRV_LOG = 38,\n\
        IAXXX_DBG_MODULE_ID_FLASHDRV_LOG = 39,\n\
        IAXXX_DBG_MODULE_ID_DMADRV_LOG = 40,\n\
        IAXXX_DBG_MODULE_ID_GPIODRV_LOG = 41,\n\
        IAXXX_DBG_MODULE_ID_MACDRV_LOG = 42,\n\
        IAXXX_DBG_MODULE_ID_STMRDRV_LOG = 43,\n\
        IAXXX_DBG_MODULE_ID_STMRPTDRV_LOG = 44,\n\
        IAXXX_DBG_MODULE_ID_SLIMBUSDRV_LOG = 45,\n\
        IAXXX_DBG_MODULE_ID_SSENSORDRV_LOG = 46,\n\
        IAXXX_DBG_MODULE_ID_STRMDRV_LOG = 47,\n\
        IAXXX_DBG_MODULE_ID_CPUSTRMDRV_LOG = 48,\n\
        IAXXX_DBG_MODULE_ID_CLKTREEUTILS_LOG = 49,\n\
        IAXXX_DBG_MODULE_ID_SCRIPTMGR_LOG = 50,\n\
    };\n\
\n\
    Log Levels -\n\
    ------------\n\
    enum iaxxx_fw_debug_log_level {\n\
        IAXXX_DBG_LOG_LVL_OFF = 0,\n\
        IAXXX_DBG_LOG_LVL_CUSTOM = 1,\n\
        IAXXX_DBG_LOG_LVL_FATAL = 2,\n\
        IAXXX_DBG_LOG_LVL_ERROR = 3,\n\
        IAXXX_DBG_LOG_LVL_WARN = 4,\n\
        IAXXX_DBG_LOG_LVL_INFO = 5,\n\
        IAXXX_DBG_LOG_LVL_DEBUG = 6,\n\
        IAXXX_DBG_LOG_LVL_TRACE = 7,\n\
    };\n\
\n\
    Modes -\n\
    -------\n\
    enum iaxxx_fw_debug_log_mode {\n\
        IAXXX_FROM_MEMORY = 0,\n\
        IAXXX_FROM_ENDPOINT = 1,\n\
    };\n\
\n\
    Processor ids -\n\
    ---------------\n\
    enum iaxxx_proc_id_e {\n\
        IAXXX_NO_PROC = 0,\n\
        IAXXX_BOSS_ID = 1,\n\
        IAXXX_SSP_ID = 2,\n\
        IAXXX_CM4_ID = 3,\n\
        IAXXX_HMD_ID = 4,\n\
        IAXXX_DMX_ID = 5,\n\
    };\n\
    ", stdout);

    exit(EXIT_FAILURE);
}

void log_lvl(int fd, int module_id, int log_level) {
    int err = 0;
    struct iaxxx_log_level_info log_level_info;

    log_level_info.module_id = module_id;

    if (log_level == -1) {
        // We need to get the log level for the module id
        log_level_info.log_level = 0;
        err = ioctl(fd, IAXXX_GET_DBG_LOG_LEVEL,
                    (unsigned long) &log_level_info);
        if (err == -1) {
            ALOGE("%s: ERROR: IAXXX_GET_DBG_LOG_LEVEL failed %d(%s)", __func__,
                                                    errno, strerror(errno));
            return;
        }

        ALOGD("%s: Module level %d Log level %u", __func__, module_id,
                                                log_level_info.log_level);
    } else {
        // We need to set the Log level for the module id
        log_level_info.log_level = log_level;
        err = ioctl(fd, IAXXX_SET_DBG_LOG_LEVEL,
                    (unsigned long) &log_level_info);
        if (err == -1) {
            ALOGE("%s: ERROR: IAXXX_SET_DBG_LOG_LEVEL failed %d(%s)", __func__,
                                                       errno, strerror(errno));
            return;
        }
    }
}

void pmode(int fd, int proc_id, int mode) {
    int err = 0;
    struct iaxxx_log_mode_info log_mode_info;

    log_mode_info.proc_id = proc_id;

    if (mode == -1) {
        // We need to get the mode for the processor id
        log_mode_info.mode = 0;
        err = ioctl(fd, IAXXX_GET_DBG_LOG_MODE,
                    (unsigned long) &log_mode_info);
        if (err == -1) {
            ALOGE("%s: ERROR: IAXXX_GET_DBG_LOG_MODE failed %d(%s)", __func__,
                                                        errno, strerror(errno));
            return;
        }

        ALOGD("%s: Processor ID %d Mode %u", __func__, proc_id,
                                            log_mode_info.mode);
    } else {
        // We need to set the mode for the processor id
        log_mode_info.mode = mode;
        err = ioctl(fd, IAXXX_SET_DBG_LOG_MODE,
                    (unsigned long) &log_mode_info);
        if (err == -1) {
            ALOGE("%s: ERROR: IAXXX_SET_DBG_LOG_MODE failed %d(%s)", __func__,
                                                        errno, strerror(errno));
            return;
        }
    }
}

void dump_all_regs(const struct iaxxx_registers_dump *info, bool use_stdout)
{
    uint32_t addr;
    int i, j;
    FILE *out_fp = NULL;
    const struct iaxxx_srb_info *srb_info = &info->srb_info;
    const struct iaxxx_arb_info *arb_info = &info->arb_info;

    if (use_stdout) {
        out_fp = stdout;
    } else {
        out_fp = fopen(REG_OUT_FILE, "w");
        if (NULL == out_fp) {
            ALOGE("Couldn't open the file %s for writing", REG_OUT_FILE);
            return;
        }
    }

    /* SRB */
#ifdef ENABLE_DEBUG_PRINTS
    ALOGD("%s", SRB_REG);
    ALOGD("%s\t%s", REG_ADDR, REG_VAL);
#endif
    fprintf(out_fp, "%s\n", SRB_REG);
    fprintf(out_fp, "%s\t%s\n", REG_ADDR, REG_VAL);

    addr = srb_info->reg_start_addr;
    for (i = 0; i < srb_info->reg_num; ++i) {
        uint32_t val = srb_info->reg_vals[i];

#ifdef ENABLE_DEBUG_PRINTS
        ALOGD("0x%X\t\t\t0x%08X", addr, val);
#endif
        fprintf(out_fp, "0x%X\t\t\t0x%08X\n", addr, val);

        addr += sizeof(uint32_t);
    }

    /* ARB */
    addr = arb_info->reg_start_addr;
    /* Dump all the ARB start address and the size at the beginning
     * before dumping all the ARB information
     */
    for (i = 0, j = 0; i < arb_info->reg_num; i += 2, j++) {
        uint32_t arb_phy_addr = arb_info->reg_vals[i];
        uint32_t arb_sz = arb_info->reg_vals[i + 1];

#ifdef ENABLE_DEBUG_PRINTS
        ALOGD("0x%X\t\t\t0x%08X", addr, arb_phy_addr);
        ALOGD("0x%X\t\t\t0x%08X", (addr + 4), arb_sz);
#endif
        fprintf(out_fp, "0x%X\t\t\t0x%08X\n", addr, arb_phy_addr);
        fprintf(out_fp, "0x%X\t\t\t0x%08X\n", (addr + sizeof(uint32_t)), arb_sz);
        addr += (sizeof(uint32_t) * 2);
    }

    addr = arb_info->reg_start_addr;
    for (i = 0, j = 0; i < arb_info->reg_num; i += 2, j++) {
        const struct iaxxx_arb_block *block = &arb_info->blocks[j];
        uint32_t k;
        uint32_t temp_arb_addr;
#ifdef ENABLE_DEBUG_PRINTS
        uint32_t arb_phy_addr = arb_info->reg_vals[i];
        uint32_t arb_sz = arb_info->reg_vals[i + 1];

        ALOGD("0x%X \t\t arb phy addr 0x%08X arb size 0x%08X", addr, arb_phy_addr, arb_sz);
        ALOGD("%s %d", ARB_REG, j);
        ALOGD("%s\t%s", REG_ADDR, REG_VAL);
#endif
        addr += (sizeof(uint32_t) * 2);

        fprintf(out_fp, "%s %d\n", ARB_REG, j);
        fprintf(out_fp, "%s\t%s\n", REG_ADDR, REG_VAL);

        if (0 == block->reg_start_addr || 0 == block->reg_num) {
            ALOGD("------------- NA -------------");
            fprintf(out_fp, "------------- NA -------------\n");
            continue;
        }

        temp_arb_addr = block->reg_start_addr;
        for (k = 0; k < block->reg_num; k++) {
            uint32_t temp_val = block->reg_vals[k];
#ifdef ENABLE_DEBUG_PRINTS
            ALOGD("0x%X\t\t\t0x%08X", temp_arb_addr, temp_val);
#endif
            fprintf(out_fp, "0x%X\t\t\t0x%08X\n", temp_arb_addr, temp_val);

            temp_arb_addr += sizeof(uint32_t);
        }
    }

    fflush(out_fp);
    if (!use_stdout)
        fclose(out_fp);
}

/*
 * Register Information -
 * All circular buffers start address and size is stored in ARB10
 * ARB10 - physical address in SRB is 0x580001bc
 *      Start Address location      Size location
 * CM4    ARB10 + 0                    ARB10 + 4
 * HMD    ARB10 + 8                    ARB10 + C
 * DMX    ARB10 + 10                   ARB10 + 14
 */
void dump_circ_buf(const struct iaxxx_circ_buffer_info *circ_buffer_info)
{
    uint32_t i = 0, j = 0;
    const char *out_file_names[IAXXX_MAX_CIRC_BUFS] = {CIRC_OUT_FILE_CM4, CIRC_OUT_FILE_HMD, CIRC_OUT_FILE_DMX};

    if (circ_buffer_info->buf_num > IAXXX_MAX_CIRC_BUFS) {
        ALOGE("Too large buf_num: %d", circ_buffer_info->buf_num);
        return;
    }

    for (i = 0, j = 0; j < circ_buffer_info->buf_num; i += 2, j++) {
        const struct iaxxx_circ_buffer *buf = &circ_buffer_info->bufs[j];
        FILE *fp = NULL;
        uint32_t k = 0;

        // Get the physical address and size for each of the circular buffers
        if (0 == buf->reg_start_addr || 0 == buf->reg_num) {
            ALOGE("Couldn't find physical address and size");
            ALOGE("phy_addr 0x%X size 0x%X", buf->reg_start_addr, buf->reg_num);
            continue;
        }

        fp = fopen(out_file_names[j], "wb");
        if (NULL == fp) {
            ALOGE("Couldn't open the file for writing");
            continue;
        }

        for (k = 0; k < buf->reg_num; k++)
            fwrite(buf->reg_vals + k, sizeof(uint32_t), 1, fp);

        fflush(fp);
        fclose(fp);
    }
}

static int dump_tunnel(int fd, int tunnel_id)
{
    int ret;
    struct iaxxx_tunnel_info_dump dump = {
        .id = tunnel_id
    };

    if ((ret = ioctl(fd, IAXXX_IOCTL_GET_TUNNEL_INFO_DUMP, &dump)) != 0) {
        ALOGE("Failed to ioctl %s IAXXX_IOCTL_GET_TUNNEL_INFO_DUMP, ret=%d", DEBUG_DEV, ret);
        ret = errno;
        return ret;
    }

    fprintf(stdout, "header.en=0x%08x\n"
                    "header.st=0x%08x\n"
                    "header.out_buf_size=%u\n"
                    "header.out_buf_addr=0x%08x\n"
                    "header.out_buf_head=%u\n"
                    "header.out_buf_tail=%u\n"
                    "header.out_buf_threshold=%u\n"
                    "tunnel%u.nframe_drops=%u\n"
                    "tunnel%u.nsent_to_host=%u\n"
                    "tunnel%u.nsent=%u\n"
                    "tunnel%u.nrecvd=%u\n"
                    "tunnel%u.ctrl=%u\n"
                    "tunnel%u.format=%u\n",
                    dump.en,
                    dump.st,
                    dump.out_buf_size,
                    dump.out_buf_addr,
                    dump.out_buf_head,
                    dump.out_buf_tail,
                    dump.out_buf_threshold,
                    dump.id, dump.nframe_drops,
                    dump.id, dump.nsent_to_host,
                    dump.id, dump.nsent,
                    dump.id, dump.nrecvd,
                    dump.id, dump.ctrl,
                    dump.id, dump.format);

    return ret;
}

static int dump_channel(int fd, int channel_id)
{
    int ret;
    struct iaxxx_channel_info_dump dump = {
        .id = channel_id
    };

    if ((ret = ioctl(fd, IAXXX_IOCTL_GET_CHANNEL_INFO_DUMP, &dump)) != 0) {
        ALOGE("Failed to ioctl %s IAXXX_IOCTL_GET_CHANNEL_INFO_DUMP, ret=%d", DEBUG_DEV, ret);
        ret = errno;
        return ret;
    }

    fprintf(stdout, "header.st=0x%08x\n"
                    "header.direction=%u\n"
                    "header.gain=%u\n"
                    "channel%u.nsent=%u\n"
                    "channel%u.nrecvd=%u\n"
                    "channel%u.endpoint_state=%u\n"
                    "channel%u.intr_cnt=%u\n"
                    "channel%u.drop_cnt=%u\n"
                    "channel%u.gain_ctrl=%u\n"
                    "channel%u.gain_status=%u\n"
                    "channel%u.""%s""=%u\n",
                    dump.st,
                    dump.direction,
                    dump.gain,
                    dump.id, dump.nsent,
                    dump.id, dump.nrecvd,
                    dump.id, dump.endpoint_state,
                    dump.id, dump.intr_cnt,
                    dump.id, dump.drop_cnt,
                    dump.id, dump.gain_ctrl,
                    dump.id, dump.gain_status,
                    dump.id, (dump.id >= 0 && dump.id < 16)
                                ? "out_fmt" : "in_connect", dump.in_connect);

    return ret;
}

static int dump_stream(int fd, int stream_id)
{
    int ret;
    struct iaxxx_stream_info_dump dump = {
        .id = stream_id
    };

    if ((ret = ioctl(fd, IAXXX_IOCTL_GET_STREAM_INFO_DUMP, &dump)) != 0) {
        ALOGE("Failed to ioctl %s IAXXX_IOCTL_GET_STREAM_INFO_DUMP, ret=%d", DEBUG_DEV, ret);
        ret = errno;
        return ret;
    }

    fprintf(stdout, "header.en=0x%08x\n"
                    "header.st=0x%08x\n"
                    "stream%u.af_error_afs_fifo_overflow_cnt=%u\n"
                    "stream%u.af_error_afs_fifo_underflow_cnt=%u\n"
                    "stream%u.af_error_tus_fifo_overflow_cnt=%u\n"
                    "stream%u.af_error_tus_fifo_underflow_cnt=%u\n"
                    "stream%u.af_error_tus_fifo_coherency_cnt=%u\n"
                    "stream%u.af_error_deadline_cnt=%u\n"
                    "stream%u.af_error_phy_cnt=%u\n"
                    "stream%u.af_error_timeout_cnt=%u\n"
                    "stream%u.af_error_access_cnt=%u\n"
                    "stream%u.ctrl=%u\n"
                    "stream%u.status=%u\n"
                    "stream%u.format=%u\n"
                    "stream%u.port=%u\n"
                    "stream%u.channel=%u\n"
                    "stream%u.sync=%u\n",
                    dump.en,
                    dump.st,
                    dump.id, dump.af_error_afs_fifo_overflow_cnt,
                    dump.id, dump.af_error_afs_fifo_underflow_cnt,
                    dump.id, dump.af_error_tus_fifo_overflow_cnt,
                    dump.id, dump.af_error_tus_fifo_underflow_cnt,
                    dump.id, dump.af_error_tus_fifo_coherency_cnt,
                    dump.id, dump.af_error_deadline_cnt,
                    dump.id, dump.af_error_phy_cnt,
                    dump.id, dump.af_error_timeout_cnt,
                    dump.id, dump.af_error_access_cnt,
                    dump.id, dump.ctrl,
                    dump.id, dump.status,
                    dump.id, dump.format,
                    dump.id, dump.port,
                    dump.id, dump.channel,
                    dump.id, dump.sync);

    return ret;
}

int main(int argc, char *argv[])
{
    int fd = 0, ret = 0;
    int c;
    bool dump_all_registers = false;
    bool dump_circ_buffer = false;
    bool dump_tunnel_info = false;
    bool dump_channel_info = false;
    bool dump_stream_info = false;
    bool log_mode = false;
    bool use_stdout = false;
    bool proc_mode = false;
    // Log level set will require 2 arguments while get needs one
    const int log_lvl_req_arg_get = 1;
    const int log_lvl_req_arg_set = 2;
    // Mode level set will required 2 arguments while set needs one
    const int mode_req_arg_get = 1;
    const int mode_req_arg_set = 2;
    int module_id = 0, log_level = -1;
    int proc_id = 0, mode = -1;
    int tunnel_id = -1, channel_id = -1, stream_id = -1;

    if (argc <= 1) {
        usage();
        return 0;
    }

    while ((c = getopt_long(argc, argv, "rcot:n:s:l:m:", long_options, NULL)) != -1) {
        switch (c) {
        case 'r':
            dump_all_registers = true;
            break;
        case 'c':
            dump_circ_buffer = true;
            break;
        case 'o':
            use_stdout = true;
            break;
        case 't':
            dump_tunnel_info = true;
            tunnel_id = atoi(optarg);
            break;
        case 'n':
            dump_channel_info = true;
            channel_id = atoi(optarg);
            break;
        case 's':
            dump_stream_info = true;
            stream_id = atoi(optarg);
            break;
        case 'l':
            log_mode = true;
            // reset optind by 1
            --optind;
            if ((optind + log_lvl_req_arg_get) != argc &&
                (optind + log_lvl_req_arg_set) != argc) {
                usage();
            }

            module_id = strtol(argv[optind], NULL, 0);

            if ((optind + log_lvl_req_arg_set) == argc) {
                // Move to the next before fetching the value
                optind++;
                log_level = strtol(argv[optind], NULL, 0);
                optind++;
            }
            break;
        case 'm':
            proc_mode = true;
            // reset optind by 1
            --optind;
            if ((optind + mode_req_arg_get) != argc &&
                (optind + mode_req_arg_set) != argc) {
                usage();
            }

            proc_id = strtol(argv[optind], NULL, 0);

            if ((optind + mode_req_arg_set) == argc) {
                // Move to the next before fetching the value
                optind++;
                mode = strtol(argv[optind], NULL, 0);
                optind++;
            }
            break;
        default:
            // Do nothing
            break;
        }
    }

    fd = open(DEBUG_DEV, O_RDONLY);
    if (-1 == fd) {
        fprintf(stderr, "Failed to open %s", DEBUG_DEV);
        ret = errno;
        goto exit;
    }

    if(log_mode == true) {
        log_lvl(fd, module_id, log_level);
    }

    if (proc_mode == true) {
        pmode(fd, proc_id, mode);
    }


    if (dump_all_registers || dump_circ_buffer) {
        static struct iaxxx_registers_dump dump;

        if ((ret = ioctl(fd, IAXXX_IOCTL_GET_REGISTERS_DUMP, &dump)) != 0) {
            fprintf(stderr, "Failed to ioctl %s IAXXX_IOCTL_GET_REGISTERS_DUMP, ret=%d", DEBUG_DEV, ret);
            ret = errno;
        } else {
            if (dump_all_registers)
                dump_all_regs(&dump, use_stdout);

            if (dump_circ_buffer)
                dump_circ_buf(&dump.circ_buffer_info);
        }
    }

    if (dump_tunnel_info) {
        if (tunnel_id >= 0 && tunnel_id < IAXXX_TUNNEL_MAX) {
            ret = dump_tunnel(fd, tunnel_id);
            if (ret) {
                fprintf(stderr, "Failed to dump_tunnel(), ret=%d", ret);
            }
        } else {
            fprintf(stderr, "Invalid tunnel_id %d\n", tunnel_id);
        }
    }

    if (dump_channel_info) {
        if (channel_id >= 0 && channel_id < IAXXX_CHANNEL_MAX) {
            ret = dump_channel(fd, channel_id);
            if (ret) {
                fprintf(stderr, "Failed to dump_channel(), ret=%d", ret);
            }
        } else {
            fprintf(stderr, "Invalid channel_id %d\n", channel_id);
        }
    }

    if (dump_stream_info) {
        if (stream_id >= 0 && stream_id < IAXXX_STREAM_MAX) {
            ret = dump_stream(fd, stream_id);
            if (ret) {
                fprintf(stderr, "Failed to dump_stream(), ret=%d", ret);
            }
        } else {
            fprintf(stderr, "Invalid stream_id %d\n", stream_id);
        }
    }

exit:
    if (-1 != fd)
        close(fd);

    return ret;
}
