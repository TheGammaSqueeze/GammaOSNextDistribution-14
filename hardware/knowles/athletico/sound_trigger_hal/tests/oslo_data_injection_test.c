/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "oslo_data_injection_test"

#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <limits.h>
#include <log/log.h>
#include <math.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include "oslo_iaxxx_sensor_control.h"

#define FRAME_SIZE_MAX (16 * 1024)
#define FRAME_PERIOD_MS_MAX (1000)
#define INJECT_BYTES_PER_SEC_MAX (400 * 1024)  // Reach: 12672 Bytes * 30 Hz = 380160 Bytes/Sec
#define CHANNEL_NUM 3
#define BYTES_PER_SAMPLE 2

enum oslo_header_index {
    OSLO_HEADER_INDEX_SYNC_0 = 0,
    OSLO_HEADER_INDEX_SYNC_1,
    OSLO_HEADER_INDEX_FRAME_COUNT,
    OSLO_HEADER_INDEX_SHAPE_GROUP_COUNT,
    OSLO_HEADER_INDEX_CHIRP_LENGTH,
    OSLO_HEADER_INDEX_SADC_VAL,
    OSLO_HEADER_INDEX_NUM,
};

const uint16_t SYNC_WORD = 0x0000;

static struct frame_sync {
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    bool ready;
} f_sync = {PTHREAD_MUTEX_INITIALIZER, PTHREAD_COND_INITIALIZER, false};

static void frame_sync_signal(struct frame_sync *fs) {
    pthread_mutex_lock(&fs->mutex);
    fs->ready = true;
    pthread_cond_signal(&fs->condition);
    pthread_mutex_unlock(&fs->mutex);
}

static void frame_sync_wait(struct frame_sync *fs) {
    int ret;
    pthread_mutex_lock(&fs->mutex);
    while (!fs->ready) {
        ret = pthread_cond_wait(&fs->condition, &fs->mutex);
    }
    fs->ready = false;
    pthread_mutex_unlock(&fs->mutex);
}

static void frame_sync_timer_handler(int signum __unused) {
    ALOGV("%s", __func__);
    frame_sync_signal(&f_sync);
}

static void frame_sync_timer_enable(bool en, uint32_t period_ms) {
    struct itimerval tv;
    struct sigaction sa;

    memset(&sa, 0, sizeof(sa));
    memset(&tv, 0, sizeof(tv));

    if (en) {
        sa.sa_handler = frame_sync_timer_handler;
        sigaction(SIGALRM, &sa, NULL);

        tv.it_interval.tv_sec = period_ms / 1000;
        tv.it_interval.tv_usec = period_ms * 1000;
        tv.it_value.tv_sec = period_ms / 1000;
        tv.it_value.tv_usec = period_ms * 1000;
        setitimer(ITIMER_REAL, &tv, NULL);
    } else {
        setitimer(ITIMER_REAL, &tv, NULL);

        sa.sa_handler = SIG_DFL;
        sigaction(SIGALRM, &sa, NULL);
    }
}

static bool frame_data_validate(const uint8_t *data, size_t frame_size) {
    uint16_t *header = (uint16_t *)data;
    uint16_t frame_cnt;
    uint16_t shape_group_count;
    uint16_t chirp_len;
    size_t chirp_size;
    size_t num_chirps_per_frame;

    // Validate 1st header of frame
    if (header[OSLO_HEADER_INDEX_SYNC_0] != SYNC_WORD ||
        header[OSLO_HEADER_INDEX_SYNC_1] != SYNC_WORD ||
        header[OSLO_HEADER_INDEX_SHAPE_GROUP_COUNT] != 0) {
        ALOGE("%s: Invalid frame header: %04x, %04x, %04x, %04x, %04x, %04x\n", __func__,
              header[0], header[1], header[2], header[3], header[4], header[5]);
        fprintf(stderr, "%s: Invalid frame header: %04x, %04x, %04x, %04x, %04x, %04x\n", __func__,
                header[0], header[1], header[2], header[3], header[4], header[5]);
        return false;
    } else {
        frame_cnt = header[OSLO_HEADER_INDEX_FRAME_COUNT];
        shape_group_count = header[OSLO_HEADER_INDEX_SHAPE_GROUP_COUNT];
        chirp_len = header[OSLO_HEADER_INDEX_CHIRP_LENGTH];
        chirp_size = chirp_len * CHANNEL_NUM * BYTES_PER_SAMPLE;
        chirp_size += OSLO_HEADER_INDEX_NUM * BYTES_PER_SAMPLE;
        num_chirps_per_frame = frame_size / chirp_size;
        ALOGD("%s: frame_cnt: 0x%04x, chirp_len: %d, chirp_size: %d, num_chirps_per_frame: %d\n",
              __func__, frame_cnt, chirp_len, chirp_size, num_chirps_per_frame);
    }

    if (frame_size % chirp_size != 0) {
        ALOGE("frame_size (%d) is not a multiple of the chirp_size (%d)!!!\n", frame_size,
              chirp_size);
        fprintf(stderr, "frame_size (%d) is not a multiple of the chirp_size (%d)!!!\n",
                frame_size, chirp_size);
        return false;
    }

    // Validate header of each chirp
    for (int i = 0; i < num_chirps_per_frame; i++) {
        if (header[OSLO_HEADER_INDEX_SYNC_0] != SYNC_WORD ||
            header[OSLO_HEADER_INDEX_SYNC_1] != SYNC_WORD ||
            header[OSLO_HEADER_INDEX_FRAME_COUNT] != frame_cnt ||
            header[OSLO_HEADER_INDEX_SHAPE_GROUP_COUNT] != i ||
            header[OSLO_HEADER_INDEX_CHIRP_LENGTH] != chirp_len) {
            ALOGE("%s: Invalid %dth header: %04x, %04x, %04x, %04x, %04x, %04x\n", __func__, i,
                  header[0], header[1], header[2], header[3], header[4], header[5]);
            fprintf(stderr, "%s: Invalid %dth chirp header: %04x, %04x, %04x, %04x, %04x, %04x\n",
                    __func__, i, header[0], header[1], header[2], header[3], header[4], header[5]);
            return false;
        }
        data += chirp_size;
        header = (uint16_t *)data;
    }

    return true;
}

static int show_help() {
    fprintf(stdout,
            "usage: oslo_data_injection_test <file_name> <frame_period_ms> <frame_size> [-s n] [-c n]\n"
            "\n"
            "-s n  Skip n input frames\n"
            "-c n  Inject only n input frames\n");
    exit(EXIT_FAILURE);
}

int main(int argc, char *argv[]) {
    int ret = 0;
    int frame_size = 0;
    int frame_period_ms = 0;
    char *file_path = NULL;
    uint8_t frame_data_buf[FRAME_SIZE_MAX] = {0};
    uint8_t *data_ptr;
    uint16_t *header = (uint16_t *)frame_data_buf;
    uint32_t frames_injected = 0;
    uint32_t frames_processed_pre_inject = 0;
    uint32_t frames_processed_post_inject = 0;
    size_t bytes_written;
    size_t frame_data_bytes_remaining;
    struct ia_sensor_mgr *smd;
    FILE *fid = NULL;
    struct stat file_stat;
    uint32_t file_size;
    uint32_t inject_bytes_per_sec;
    uint32_t skip_frames = 0;
    uint32_t max_inject_frames = UINT32_MAX;
    int c;

    const struct option long_options[] = {{"skip", required_argument, NULL, 's'},
                                          {"count", required_argument, NULL, 'c'},
                                          {NULL, 0, NULL, 0}};

    if (argc < 4) {
        show_help();
    } else {
        file_path = argv[1];
        frame_period_ms = strtol(argv[2], NULL, 0);
        frame_size = strtol(argv[3], NULL, 0);
    }

    while ((c = getopt_long(argc, argv, "s:c:", long_options, NULL)) != -1) {
        switch (c) {
            case 's':
                if (NULL == optarg) {
                    show_help();
                } else {
                    skip_frames = strtoul(optarg, NULL, 0);
                }
                break;
            case 'c':
                if (NULL == optarg) {
                    show_help();
                } else {
                    max_inject_frames = strtoul(optarg, NULL, 0);
                }
                break;
            default:
                show_help();
                break;
        }
    }

    if (frame_period_ms < 0 || frame_period_ms > FRAME_PERIOD_MS_MAX) {
        fprintf(stderr, "Invalid frame_period_ms:%d\n", frame_period_ms);
        ret = -EINVAL;
        goto out;
    }

    if (frame_size < 0 || frame_size > FRAME_SIZE_MAX) {
        fprintf(stderr, "Invalid frame_size:%d\n", frame_size);
        ret = -EINVAL;
        goto out;
    }

    inject_bytes_per_sec = frame_size * 1000 / frame_period_ms;
    if (inject_bytes_per_sec > INJECT_BYTES_PER_SEC_MAX) {
        fprintf(stderr, "Invalid bytes_per_sec:%d\n", inject_bytes_per_sec);
        ret = -EINVAL;
        goto out;
    }

    if (stat(file_path, &file_stat) == -1) {
        fprintf(stderr, "Could not stat file: %s - %s\n", file_path, strerror(errno));
        ret = -errno;
        goto out;
    }
    file_size = file_stat.st_size;

    smd = iaxxx_sensor_mgr_init();
    if (NULL == smd) {
        ALOGE("%s: ERROR Failed to init ia_sensor_mgr", __func__);
        ret = -ENOMEM;
        goto out;
    }

    frames_processed_pre_inject = oslo_driver_get_param(smd, SENSOR_PARAM_FRAMES_PROCESSED);

    fid = fopen(file_path, "r");
    if (fid == NULL) {
        ALOGE("Cannot open '%s' (%s)\n", file_path, strerror(errno));
        fprintf(stdout, "Cannot open '%s' (%s)\n", file_path, strerror(errno));
        ret = -errno;
        goto out_err_fopen;
    }

    frame_sync_timer_enable(true, frame_period_ms);

    while (!feof(fid)) {
        frame_data_bytes_remaining = fread(frame_data_buf, 1, frame_size, fid);
        if (!frame_data_bytes_remaining) {
            ALOGE("Zero bytes read\n");
            break;
        }
        if (frame_data_bytes_remaining != frame_size ||
            !frame_data_validate(frame_data_buf, frame_size)) {
            ALOGE("Drop invalid frame, header: %04x, %04x, %04x, %04x, %04x, %04x\n", header[0],
                  header[1], header[2], header[3], header[4], header[5]);
            fprintf(stdout, "Drop invalid frame, header: %04x, %04x, %04x, %04x, %04x, %04x\n",
                    header[0], header[1], header[2], header[3], header[4], header[5]);
            continue;
        }
        if (skip_frames > 0) {
            ALOGD("Num of frames to skip %u\n", skip_frames);
            skip_frames--;
            continue;
        }
        if (frames_injected >= max_inject_frames) {
            ALOGD("Already injected %u frames, STOP\n", frames_injected);
            break;
        }
        data_ptr = frame_data_buf;
        while (frame_data_bytes_remaining) {
            bytes_written = oslo_driver_set_param_blk(smd, PARAM_BLK_ID_FRAME_DATA_INJECTION,
                                                      data_ptr, frame_data_bytes_remaining);
            if (bytes_written == 0) {
                ALOGE("Failed to inject data!!!\n");
                break;
            }
            frame_data_bytes_remaining -= bytes_written;
            data_ptr += bytes_written;
        }
        frame_sync_wait(&f_sync);
        if (frames_injected % 10 == 0)
            ALOGD("Process injected frame %d, header: %04x, %04x, %04x, %04x, %04x, %04x\n",
                  frames_injected, header[0], header[1], header[2], header[3], header[4],
                  header[5]);
        oslo_driver_set_param(smd, OSLO_CONTROL_INJECT_FRAME_READY, 1);
        frames_injected++;
    }

    frame_sync_timer_enable(false, 0);

    if (fid)
        fclose(fid);

    frames_processed_post_inject = oslo_driver_get_param(smd, SENSOR_PARAM_FRAMES_PROCESSED);

    ALOGD("A total of %d frames were injected\n", frames_injected);
    ALOGD("Frames processed pre:%d, post:%d\n", frames_processed_pre_inject,
          frames_processed_post_inject);

out_err_fopen:
    iaxxx_sensor_mgr_deinit(smd);
out:

    return ret;
}
