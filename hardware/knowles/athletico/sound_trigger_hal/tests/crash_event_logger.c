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
#include <stdlib.h>
#include <poll.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cutils/properties.h>

#define LOG_TAG "ia_crash_event_logger"
#include <cutils/log.h>
#include <cutils/uevent.h>
#include "crash_analyzer.h"

#define UEVENT_MSG_LEN          (1024)
#define BUF_SIZE                (4096)
#define CRASH_LOGGER_DEV        "/dev/crashdump"
#define REGDUMP_LOGGER_DEV	"/dev/regdump"
#define CRASH_DUMP_FILE_PREFIX  "/data/data/dump_crash_"
#define REG_ACCESS_FILE_PREFIX	"/data/data/dump_reg_access_history_"
#define CRASH_REASON_FILE_PREFIX  "/data/data/dump_crash_reason_"
#define SSR_CRASH_REASON_PREFIX  "ia_dump_crash_reason_"
#define BIN_EXTN                ".bin"
#define TXT_EXTN                ".txt"
#define MAX_FILENAME_LEN        512
#define MAX_TIMESTR_LEN         64
#define CRASH_DUMP_ANALYZER_MAX_STR_LEN  512

#define SSR_RAMDUMP_PREFIX      "ramdump_audio_codec_"
#define SSR_CRASH_FILE_PREFIX   "ia_crash_dump_"
#define SSR_REG_FILE_PREFIX     "ia_reg_access_history_"
#define SSR_DUMP_PATH           "/data/vendor/ssrdump/"

int g_exit_socket[2];

void sigint_handler(int sig __unused) {
    ALOGE("Interrupted, setting the exit condition");
    if (g_exit_socket[0] >= 0)
        write(g_exit_socket[0], "T", 1);
}

char *crash_dump_split_file_names[] =
    {"/data/data/dump_debug_CM4_",
    "/data/data/dump_debug_HMD_",
    "/data/data/dump_debug_DMX_",
    "/data/data/dump_crash_CM4_",
    "/data/data/dump_crash_HMD_",
    "/data/data/dump_crash_DMX_",
    "/data/data/dump_crash_SSP_RAM0_",
    "/data/data/dump_crash_SSP_RAM1_",
    "/data/data/dump_crash_SSP_ROM0_",
    CRASH_REASON_FILE_PREFIX
    };

char *ssr_crash_dump_split_file_names[] = {
    "ia_dump_debug_CM4_",
    "ia_dump_debug_HMD_",
    "ia_dump_debug_DMX_",
    "ia_dump_crash_CM4_",
    "ia_dump_crash_HMD_",
    "ia_dump_crash_DMX_",
    "ia_dump_crash_SSP_RAM0_",
    "ia_dump_crash_SSP_RAM1_",
    "ia_dump_crash_SSP_ROM0_",
    SSR_CRASH_REASON_PREFIX
};

void dump_crash_reason(const unsigned char *crash_dump_buf,
                       const int crash_dump_len,
                       const unsigned char *crash_reason_buf,
                       const int crash_reason_len,
                       const char *time_stamp, bool is_ssr)
{

    FILE *out_fp = NULL;
    char file_name[MAX_FILENAME_LEN] = {0};
    char crash_dump_analyzer_str[CRASH_DUMP_ANALYZER_MAX_STR_LEN] = {0};
    int len = 0;
    const char *crash_dump_title = " crash_analysis:";

    if (is_ssr) {
        snprintf(file_name, MAX_FILENAME_LEN, "%s%s%s%s%s",
                 SSR_DUMP_PATH, SSR_RAMDUMP_PREFIX,
                 SSR_CRASH_REASON_PREFIX,
                 time_stamp, BIN_EXTN);
    } else {
        snprintf(file_name, MAX_FILENAME_LEN, "%s%s%s",
                 CRASH_REASON_FILE_PREFIX, time_stamp,
                 TXT_EXTN);
    }

    out_fp = fopen(file_name, "w");
    if (out_fp == NULL) {
        ALOGE("Failed to open %s for writting", file_name);
        goto exit;
    }

    len = strnlen((const char *)crash_reason_buf, crash_reason_len);

    if (fwrite(crash_reason_buf, 1, len, out_fp) != len) {
        ALOGE("%s: ERROR writing to CRASH REASON FILE", __func__);
        goto exit;
    }

    len = analyse_crash_info(
            crash_dump_buf, crash_dump_len, crash_dump_analyzer_str,
            CRASH_DUMP_ANALYZER_MAX_STR_LEN);
    if (len > 0) {
        fwrite(crash_dump_title, 1, strlen(crash_dump_title), out_fp);
        fwrite(crash_dump_analyzer_str, 1,
                strlen(crash_dump_analyzer_str), out_fp);
    }
    ALOGI("Crash logs saved to %s", file_name);
exit:
    if (out_fp != NULL) {
        fclose(out_fp);
    }

}

int split_crash_dump_buffer(unsigned char *buf, const int len,
                            const char* time_stamp)
{

    unsigned int file_index = 0, size = 0, tot_len = 0, flen = 0;
    int fcount = 0;
    unsigned char *ptr = NULL;
    FILE *fp = NULL;
    char file_name[MAX_FILENAME_LEN] = {0};
    int number_crashdump_files = sizeof(crash_dump_split_file_names) /
            sizeof(crash_dump_split_file_names[0]);

    if (buf == NULL || time_stamp == NULL || len <= 0) {
        ALOGE("%s: Bad parameters", __func__);
        return -1;
    }

    while ((tot_len + STEP_LENGTH - 1 < len) &&
           (fcount++ < number_crashdump_files)) {
        file_index = buf[tot_len];

        size = buf[tot_len + 8] |
               buf[tot_len + 9] << 8 |
               buf[tot_len + 10] << 16 |
               buf[tot_len + 11] << 24;

        tot_len += STEP_LENGTH;

        if (file_index >= number_crashdump_files || size > len - tot_len) {
            continue;
        }

        /* Some special handling is needed for crash reason file */
        if (!strcmp(crash_dump_split_file_names[file_index],
                CRASH_REASON_FILE_PREFIX)) {
            dump_crash_reason(buf, len, buf + tot_len, size, time_stamp,
                              false);
        }
        else {
            snprintf(file_name, MAX_FILENAME_LEN, "%s%s%s",
                     crash_dump_split_file_names[file_index],
                     time_stamp, BIN_EXTN);

            fp = fopen(file_name, "w+");

            ptr = buf + tot_len;

            flen = fwrite(ptr , 1, size, fp);
            tot_len += size;
            fclose(fp);
            ALOGI("Crash logs saved to %s", file_name);
        }
    }
    return 0;
}

int split_crash_dump_file (const char* crash_dump_filename,
                        const char* time_stamp)
{
    int fd, fil_len;
    FILE *fp;
    struct stat st;
    unsigned char *buf;
    int len,ret ;

    fp = fopen(crash_dump_filename, "r");
    if (!fp)
    {
        ALOGE("File open error %s \n", crash_dump_filename);
        return -1;
    }

    fd = fileno(fp);
    fstat(fd, &st);
    fil_len = st.st_size;
    buf = (unsigned char*) malloc(fil_len);

    if (NULL == buf) {
        ALOGE("Failed to allocate buffer exiting");
        ret = -1;
        goto exit;
    }

    len = fread(buf,1,  fil_len, fp);
    if (len <=0) {
        ALOGE("file reading error %s\n", crash_dump_filename);
        ret = -1;
        goto exit;
    }
    ret = split_crash_dump_buffer(buf, len, time_stamp);

exit:
    if (fp)
        fclose (fp);
    if (buf)
        free(buf);
    return ret;
}

void dump_crash_log() {
    void *buf = NULL;
    int inp_fp = -1, out_fp = -1;
    int bytes_read = 0;
    int err = 0;
    time_t t;
    struct tm *tm;
    char file_name[MAX_FILENAME_LEN];
    char curr_time[MAX_TIMESTR_LEN];

    buf = malloc(BUF_SIZE);
    if (NULL == buf) {
        ALOGE("Failed to allocate buffer exiting");
        err = -1;
        goto exit;
    }

    inp_fp = open(CRASH_LOGGER_DEV, O_RDONLY);
    if (inp_fp == -1) {
        ALOGE("Failed to open %s with error %d(%s)",
                CRASH_LOGGER_DEV, errno, strerror(errno));
        goto exit;
    }

    strcpy(file_name, CRASH_DUMP_FILE_PREFIX);
    t = time(NULL);
    tm = localtime(&t);
    strftime(curr_time, 64, "%F_%H_%M_%S", tm);
    strcat(file_name, curr_time);
    strcat(file_name, BIN_EXTN);

    out_fp = open(file_name, O_WRONLY | O_CREAT, 0644);
    if (out_fp == -1) {
        ALOGE("Failed to open %s for writing", file_name);
        goto exit;
    }

    do {
        bytes_read = read(inp_fp, buf, BUF_SIZE);
        if (bytes_read > 0)
            write(out_fp, buf, bytes_read);
    } while (bytes_read > 0);

    ALOGI("Crash logs has been dumped to %s", file_name);
    close(out_fp);
    out_fp = -1;
    close(inp_fp);
    inp_fp = -1;
    free(buf);
    buf = NULL;
    split_crash_dump_file(file_name, curr_time);

exit:
    if (out_fp != -1) {
        close(out_fp);
    }

    if (inp_fp != -1) {
        close(inp_fp);
    }

    if (buf) {
        free(buf);
    }
}

void dump_reg_access_hist_log() {
    void *buf = NULL;
    int inp_fp = -1, out_fp = -1;
    int bytes_read = 0;
    int err = 0;
    time_t t;
    struct tm *tm;
    char file_name[MAX_FILENAME_LEN];
    char curr_time[MAX_TIMESTR_LEN];

    buf = malloc(BUF_SIZE);
    if (!buf) {
        ALOGE("Failed to allocate buffer exiting");
        err = -1;
        goto exit;
    }

    inp_fp = open(REGDUMP_LOGGER_DEV, O_RDONLY);
    if (inp_fp == -1) {
        ALOGE("Failed to open %s with error %d(%s)",
                REGDUMP_LOGGER_DEV, errno, strerror(errno));
        goto exit;
    }

    strcpy(file_name, REG_ACCESS_FILE_PREFIX);
    t = time(NULL);
    tm = localtime(&t);
    strftime(curr_time, 64, "%F_%H_%M_%S", tm);
    strcat(file_name, curr_time);
    strcat(file_name, TXT_EXTN);

    out_fp = open(file_name, O_WRONLY | O_CREAT, 0644);
    if (out_fp == -1) {
        ALOGE("Failed to open %s for writing", file_name);
        goto exit;
    }

    do {
        bytes_read = read(inp_fp, buf, BUF_SIZE);
        if (bytes_read > 0)
            write(out_fp, buf, bytes_read);
    } while (bytes_read > 0);

    ALOGI("Register access history has been dumped to %s", file_name);

exit:
    if (out_fp != -1) {
        close(out_fp);
    }

    if (inp_fp != -1) {
        close(inp_fp);
    }

    if (buf) {
        free(buf);
    }
}

/* --- functions for SSR detector ---*/
int ssr_split_bin(unsigned char *buf, int len, const char* time_stamp) {
    unsigned int file_index = 0, size = 0, tot_len = 0, flen = 0;
    unsigned char *ptr = NULL;
    char file_name[MAX_FILENAME_LEN] = {0};
    FILE *fp = NULL;
    int fcount = 0;
    int number_crashdump_files = sizeof(ssr_crash_dump_split_file_names) /
            sizeof(ssr_crash_dump_split_file_names[0]);

    if (buf == NULL || time_stamp == NULL || len <= 0) {
        ALOGE("%s: Bad parameters", __func__);
        return -1;
    }

    while ((tot_len + STEP_LENGTH - 1 < len) &&
           (fcount++ < number_crashdump_files)) {

        file_index = buf[tot_len];

        size = buf[tot_len + 8] |
                buf[tot_len + 9]  << 8  |
                buf[tot_len + 10] << 16 |
                buf[tot_len + 11] << 24 ;

        tot_len += STEP_LENGTH;

        if (file_index >= number_crashdump_files || size > len - tot_len) {
            continue;
        }

        /* Some special handling is needed for crash reason file */
        if (!strcmp(ssr_crash_dump_split_file_names[file_index],
                SSR_CRASH_REASON_PREFIX)) {
            dump_crash_reason(buf, len, buf + tot_len, size, time_stamp, true);
            continue;
        }

        snprintf(file_name, MAX_FILENAME_LEN, "%s%s%s%s%s",
                SSR_DUMP_PATH, SSR_RAMDUMP_PREFIX,
                ssr_crash_dump_split_file_names[file_index],
                time_stamp, BIN_EXTN);

        fp = fopen(file_name, "w+");

        ptr = buf + tot_len;
        flen = fwrite(ptr, 1, size, fp);
        tot_len += size;
        fclose(fp);
        ALOGI("SSR Crash logs saved to %s", file_name);
    }
    return 0;
}

int ssr_split_crash_dump_file(const char* crash_dump_filename,
                               const char* time_stamp) {
    int fd = -1, fil_len = 0;
    FILE *fp = NULL;
    struct stat st;
    unsigned char *buf = NULL;
    int len = 0, ret = 0;

    fp = fopen(crash_dump_filename, "r");
    if (!fp) {
        ALOGE("File open error %s \n", crash_dump_filename);
        return -1;
    }

    fd = fileno(fp);
    fstat(fd, &st);
    fil_len = st.st_size;
    buf = (unsigned char *)malloc(fil_len);

    if (NULL == buf) {
        ALOGE("Failed to allocate buffer exiting");
        ret = -1;
        goto exit;
    }

    len = fread(buf, 1, fil_len, fp);
    if (len <= 0) {
        ALOGE("file reading error %s\n", crash_dump_filename);
        ret = -1;
        goto exit;
    }
    ret = ssr_split_bin(buf, len, time_stamp);

exit:
    if (fp) {
        fclose(fp);
    }

    if (buf) {
        free(buf);
    }
    return ret;
}

void ssr_copy_log(const char* src_path, const char* dis_path) {
    int src_fp = -1, dis_fp = -1;
    int bytes_read = 0;
    void *temp_buf = NULL;

    // allocate temp buf
    temp_buf = malloc(BUF_SIZE);
    if (!temp_buf) {
        ALOGE("Failed to allocate buffer exiting");
        goto exit;
    }

    // open src file
    src_fp = open(src_path, O_RDONLY);
    if (src_fp == -1) {
        ALOGE("Failed to open %s with error %d(%s)",
                src_path, errno, strerror(errno));
        goto exit;
    }

    // open dis file and append
    dis_fp = open(dis_path, O_CREAT | O_SYNC | O_WRONLY,
                  S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
    if (dis_fp == -1) {
        ALOGE("Failed to open %s with error %d(%s)",
                dis_path, errno, strerror(errno));
        goto exit;
    }

    // copy data
    do {
        bytes_read = read(src_fp, temp_buf, BUF_SIZE);
        if (bytes_read > 0)
            write(dis_fp, temp_buf, bytes_read);
    } while (bytes_read > 0);

    ALOGI("Write data successfully from %s to %s", src_path, dis_path);

exit:
    if (src_fp != -1) {
        close(src_fp);
    }

    if (dis_fp != -1) {
        close(dis_fp);
    }

    if (temp_buf) {
        free(temp_buf);
    }
    return;
}

void check_crash_reason_file(const char *time_stamp)
{
    FILE *out_fp = NULL;
    char file_name[MAX_FILENAME_LEN] = {0};
    const char *default_crash_reason = "Iaxxx Firmware Crashed";

    snprintf(file_name, MAX_FILENAME_LEN, "%s%s%s%s%s",
             SSR_DUMP_PATH, SSR_RAMDUMP_PREFIX,
             SSR_CRASH_REASON_PREFIX,
             time_stamp, BIN_EXTN);

    if (access(file_name, F_OK) == -1) {
        ALOGE("Write default crash reason into the crash reason file");

        out_fp = fopen(file_name, "w");

        if (out_fp == NULL) {
            ALOGE("%s: Failed to open: %s , errno: %s", __func__,
                   file_name, strerror(errno));
            return;
        }

        fwrite(default_crash_reason, 1,
               strlen(default_crash_reason), out_fp);

        fclose(out_fp);
    }
}

void ssr_dump_log() {
    time_t t;
    struct tm *tm = NULL;
    char curr_time_for_property[MAX_TIMESTR_LEN] = {0};
    char curr_time_for_dump[MAX_TIMESTR_LEN] = {0};
    char out_crash_file_name[MAX_FILENAME_LEN] = {0};
    char out_reg_file_name[MAX_FILENAME_LEN] = {0};

    // get current time
    t = time(NULL);
    tm = localtime(&t);
    snprintf(curr_time_for_property, MAX_TIMESTR_LEN,
            "%.4d-%.2d-%.2d %.2d-%.2d-%.2d",
            tm->tm_year + 1900, tm->tm_mon + 1, tm->tm_mday,
            tm->tm_hour, tm->tm_min, tm->tm_sec);
    snprintf(curr_time_for_dump, MAX_TIMESTR_LEN,
            "%.02d-%.02d-%.02d_%.02d-%.02d-%.02d",
            tm->tm_year + 1900, tm->tm_mon + 1, tm->tm_mday,
            tm->tm_hour, tm->tm_min, tm->tm_sec);
    // strftime(curr_time_for_dump, MAX_TIMESTR_LEN, "%F_%H_%M_%S", tm);

    // set property
    property_set("vendor.debug.ssrdump.subsys", "audio_codec");
    property_set("vendor.debug.ssrdump.timestamp", curr_time_for_property);

    // copy crash log only
    snprintf(out_crash_file_name, MAX_FILENAME_LEN, "%s%s%s%s%s",
            SSR_DUMP_PATH, SSR_RAMDUMP_PREFIX, SSR_CRASH_FILE_PREFIX,
            curr_time_for_dump, BIN_EXTN);
    ssr_copy_log(CRASH_LOGGER_DEV, out_crash_file_name);
    ssr_split_crash_dump_file(out_crash_file_name, curr_time_for_dump);
    ALOGI("Crash logs has been dumped to %s", out_crash_file_name);

    // copy reg
    snprintf(out_reg_file_name, MAX_FILENAME_LEN, "%s%s%s%s%s",
            SSR_DUMP_PATH, SSR_RAMDUMP_PREFIX, SSR_REG_FILE_PREFIX,
            curr_time_for_dump, TXT_EXTN);
    ssr_copy_log(REGDUMP_LOGGER_DEV, out_reg_file_name);
    ALOGI("Register access history has been dumped %s", out_reg_file_name);

    // Check the crash reason file
    check_crash_reason_file(curr_time_for_dump);
}

/* --- main function --- */
int main(int argc, char** argv) {
    int err = 0;
    int timeout = -1; // Wait for event indefinitely
    struct pollfd fds[2];
    char msg[UEVENT_MSG_LEN];
    int i, n;
    bool ssr_monitor = false;

    if ((argc == 2) && !strcmp(argv[1], "-m")) {
        ALOGD("Monitor the crash logs");
        (void)umask(S_IWGRP | S_IWOTH);
        ssr_monitor = true;
    }

    signal(SIGINT, sigint_handler);

    if ( (argc == 2) && !strcmp(argv[1], "-f")) {
        ALOGD("Read to get the crash logs");
        dump_reg_access_hist_log();
        dump_crash_log();
        return 0;
    }

    if (socketpair(AF_UNIX, SOCK_STREAM, 0, g_exit_socket) == -1) {
        ALOGE("%s: Failed to create termination socket", __func__);
        err = -1;
        goto exit;
    }


    fds[0].events = POLLIN;
    fds[0].fd = uevent_open_socket(64*1024, true);
    if (fds[0].fd == -1) {
        ALOGE("Error opening socket for hotplug uevent errno %d(%s)",
                errno, strerror(errno));
        goto exit;
    }
    fds[1].events = POLLIN;
    fds[1].fd = g_exit_socket[1];

    while (1) {
        err = poll (fds, 2, timeout);

        if (fds[0].revents & POLLIN) {
            n = uevent_kernel_multicast_recv(fds[0].fd, msg, UEVENT_MSG_LEN);
            if (n <= 0) {
                continue;
            }

            for (i = 0; i < n;) {
                if (strstr(msg + i, "IAXXX_CRASH_EVENT")) {
                    ALOGD("IAXXX_CRASH_EVENT received trying to get the crash logs");
                    if (ssr_monitor) {
                        ssr_dump_log();
                    } else {
                        dump_reg_access_hist_log();
                        dump_crash_log();
                    }
                }

                i += strlen(msg + i) + 1;
            }
        } else if (fds[1].revents & POLLIN) {
            read(fds[1].fd, &n, sizeof(n)); /* clear the socket */
            ALOGE("Interrupt received, exiting");
            break;
        } else {
            ALOGI("Message ignored");
        }
    }

exit:
    if (g_exit_socket[0] >= 0) {
        close(g_exit_socket[0]);
    }

    if (g_exit_socket[1] >= 0) {
        close(g_exit_socket[1]);
    }

    return err;
}
