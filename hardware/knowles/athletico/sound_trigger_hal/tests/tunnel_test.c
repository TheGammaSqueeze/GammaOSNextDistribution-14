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
#include <string.h>
#include <signal.h>
#include <stdbool.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>

#define LOG_TAG "ia_tunneling_hal_test"

#include <log/log.h>
#include "tunnel.h"
#include "conversion_routines.h"
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>
#include <linux/mfd/adnc/iaxxx-tunnel-intf.h>



#define MAX_TUNNELS 32
#define BUF_SIZE 32768
#define MAX_FILE_PATH 256

#define DEFAULT_PATH                "/data/data"
#define FILE_PREFIX                 "/tnl_op"
#define UNPARSED_OUTPUT_FILE        "/unparsed_output"

struct raf_format_type {
    uint16_t frameSizeInBytes; /*!< Frame length in bytes */
    uint8_t encoding;   /*!< Encoding */
    uint8_t sampleRate; /*!< Sample rate */
};

struct raf_frame_type {
    uint64_t timeStamp; /*!< Timestamp of the frame */
    uint32_t seqNo;     /*!< Optional sequence number of the frame */

    struct raf_format_type format; /*!< Format information for the frame */
    uint32_t data[0];   /*!< Start of the variable size payload.
                           It must start at 128 bit aligned address for all the frames */
};

volatile int capturing = 1;

void sigint_handler(int sig __unused) {
    ALOGE("Interrupted, setting the exit condition");
    capturing = 0;
}


void parse_audio_tunnel_data(FILE *out_fp, unsigned char *buf_itr, int frame_sz_in_bytes) {
    char q16_buf[BUF_SIZE]; // This can be smaller but by how much?
    int frameSizeInWords = (frame_sz_in_bytes + 3) >> 2;

    if (NULL == buf_itr || NULL == out_fp) {
        ALOGE("%s: Buffer or file pointer is NULL", __func__);
        return;
    }

    kst_float_to_q15_vector(q16_buf, buf_itr, frameSizeInWords);

    fwrite(q16_buf, (frameSizeInWords * 2), 1, out_fp);
    fflush(out_fp);
}

int main(int argc, char *argv[]) {
    struct ia_tunneling_hal *thdl = NULL;
    int err = 0;
    FILE *out_fp[MAX_TUNNELS] = { NULL };
    FILE *unp_out_fp = NULL;
    int bytes_avail = 0, bytes_rem = 0;
    int bytes_read = 0;
    void *buf = NULL;
    // The magic number is ROME in ASCII reversed. So we are looking for EMOR in the byte stream
    const unsigned char magic_num[4]  = {0x45, 0x4D, 0x4F, 0x52};
    int i = 0;
    bool valid_frame = true;
    char filepath[MAX_FILE_PATH];
    char filename[MAX_FILE_PATH];
    int num_of_tunnels = 0;
    int num_tunnel_params;
    float timer_signal = 0;
    timer_t timer_id;
    int tunnel_src[MAX_TUNNELS] = { 0 };
    int tunnel_mode[MAX_TUNNELS] = { 0 };
    int tunnel_encode[MAX_TUNNELS];
    int lastSeqNum[MAX_TUNNELS] = { 0 };
    int notFirstFrame[MAX_TUNNELS] = { 0 };
    int frameDropCount[MAX_TUNNELS] = { 0 };
    uint64_t tunnel_time_stamps[MAX_TUNNELS] = { 0 };
    unsigned char *frame_start, *buf_itr;
    // Minimum bytes required is the magic number + tunnel id + reserved and crc + raf struct
    int min_bytes_req = 4 + 2 + 6 + sizeof(struct raf_frame_type);
    int instance;

    if (argc < 5) {
        ALOGE("USAGE: %s <instance number> <Number of tunnels> <Time in seconds> <Source End pt 1> <tnl mode> <encode fmt> <Source End pt 2> <tnl mode> <encode fmt>... <output path>", argv[0]);
        return -EINVAL;
    }

    instance = strtol(argv[1], NULL, 0);
    ALOGD("instance %d", instance);

    num_of_tunnels = strtol(argv[2], NULL, 0);
    ALOGD("Number of tunnels %d", num_of_tunnels);

    timer_signal = strtof(argv[3], NULL);
    ALOGD("tunnel out timer based req %f", timer_signal);
    err = timer_create(CLOCK_MONOTONIC, NULL, &timer_id);
    if (err != 0) {
        ALOGE("Couldn't create timer: %s", strerror(errno));
        return -EINVAL;
    }

    num_tunnel_params = num_of_tunnels * 3 + 4;
#ifdef FILENAME_ASSIGN
    char filename_str_format[256] = {0};
    bool is_specified_name = false;
    bool is_specified_path= false;
    for (int i = 0; i < argc ; i++) {
        if (strncmp(argv[i], "-f", sizeof(char)*2) == 0) {
            if ((i+1) < argc) {
                snprintf(filename_str_format, sizeof(filename_str_format), "%s", argv[i+1]);
                ALOGE("specify a output file name %s, argc i = %d, argv[%d] = %s", filename_str_format, i, i+1, argv[i+1]);
                is_specified_name = true;
            }
        }
        if (strncmp(argv[i], "-p", sizeof(char)*2) == 0) {
            if ((i+1) < argc) {
                snprintf(filepath, sizeof(filepath), "%s", argv[i+1]);
                ALOGE("specify a output file path %s, argc i = %d, argv[%d] = %s", filepath, i, i+1, argv[i+1]);
                is_specified_path = true;
            }
        }
    }

    if (is_specified_name || is_specified_path) {
        int spec_number = 2 * ((int) is_specified_name + (int) is_specified_path);

        if (argc != (num_tunnel_params + spec_number)) {
            ALOGE("USAGE: %s <instance number> <Number of tunnels> <Sync Tunnel req> <Source End pt 1> <tnl mode> <encode fmt> <Source End pt 2> <tnl mode> <encode fmt>... [-f filename] [-p filepath]", argv[0]);
            return -EINVAL;
        }
        else {
            ALOGE("is_specified_name = TRUE");
        }
    } else {
        if (argc != num_tunnel_params && argc != num_tunnel_params + 1) {
            ALOGE("USAGE: %s <instance number> <Number of tunnels> <Sync Tunnel req> <Source End pt 1> <tnl mode> <encode fmt> <Source End pt 2> <tnl mode> <encode fmt>...", argv[0]);
            return -EINVAL;
        }
    }
#else
    if (argc != num_tunnel_params && argc != num_tunnel_params + 1) {
        ALOGE("USAGE: %s <instance number> <Number of tunnels> <Sync Tunnel req> <Source End pt 1> <tnl mode> <encode fmt> <Source End pt 2> <tnl mode> <encode fmt>...", argv[0]);
        return -EINVAL;
    }
#endif /* FILENAME_ASSIGN */


    for (i = 0; i < num_of_tunnels; i++) {
        tunnel_src[i] = strtol(argv[i*3+4], NULL, 0);
        tunnel_mode[i] = strtol(argv[i*3+5], NULL, 0);
        tunnel_encode[i] = strtol(argv[i*3+6], NULL, 0);
        ALOGD("Tunnel source 0x%x Tunnel mode %d Tunnel encode %d", tunnel_src[i], tunnel_mode[i], tunnel_encode[i]);
    }

#ifdef FILENAME_ASSIGN
    if (!is_specified_path) {
        strcpy(filepath, DEFAULT_PATH);
    }
#else
    if (argc == num_tunnel_params) {
        strcpy(filepath, DEFAULT_PATH);
    } else {
        strcpy(filepath, argv[argc-1]);
    }
#endif
    ALOGE("Output path %s", filepath);

    thdl = ia_start_tunneling(0);
    if (NULL == thdl) {
        ALOGE("Failed to start tunneling");
        goto exit;
    }

    for (i = 0; i < num_of_tunnels; i++) {
        err = ia_enable_tunneling_source(thdl, tunnel_src[i], tunnel_mode[i], tunnel_encode[i]);
        if (0 != err) {
            ALOGE("Failed to enable tunneling for src_id %u mode %u encode %u", tunnel_src[i], tunnel_mode[i], tunnel_encode[i]);
            goto exit;
        }
    }

    buf = malloc(BUF_SIZE * 2);
    if (NULL == buf) {
        ALOGE("Failed to allocate memory to read buffer");
        goto exit;
    }

    snprintf(filename, MAX_FILE_PATH, "%s%s", filepath, UNPARSED_OUTPUT_FILE);
    unp_out_fp = fopen(filename, "wb");
    if (NULL == unp_out_fp) {
        ALOGE("Failed to open the file %s", filename);
        goto exit;
    }

    signal(SIGINT, sigint_handler);

    if (num_of_tunnels && timer_signal) {
        struct itimerspec timer_spec;
        uint64_t timer_ns = timer_signal * NS_PER_SEC;

        signal(SIGALRM, sigint_handler);

        memset(&timer_spec, 0, sizeof(timer_spec));
        timer_spec.it_value.tv_sec = timer_ns / NS_PER_SEC;
        timer_spec.it_value.tv_nsec = timer_ns % NS_PER_SEC;
        timer_settime(timer_id, 0, &timer_spec, NULL);
        ALOGD("timer_settime %ld %ld",
              timer_spec.it_value.tv_sec,
              timer_spec.it_value.tv_nsec);
    }

    unsigned short int tunnel_id;
    unsigned short int tunl_src;
    while (1) {
read_again:
        if (0 == capturing) {
            ALOGE("Time to bail from here");
            break;
        }

        if (0 != bytes_avail) {
            if (bytes_avail < 0) {
                bytes_rem = 0;
            } else {
                bytes_rem = bytes_avail;
                ALOGD("bytes_avail is %d", bytes_rem);
                memcpy(buf, buf_itr, bytes_rem);
            }
        } else {
            bytes_rem = 0;
        }

        // Ensure that we read BUF_SIZE always otherwise the kernel read will hang
        bytes_avail = ia_read_tunnel_data (thdl,
                                           (void *)((unsigned char *)buf + bytes_rem),
                                           BUF_SIZE);
        if (bytes_avail <= 0) {
            ALOGE("Failed to read data from the tunnel :%d", bytes_avail);
            break;
        }

        fwrite((void *)((unsigned char *)buf + bytes_rem), bytes_avail, 1, unp_out_fp);
        fflush(unp_out_fp);

        bytes_avail += bytes_rem; // update the available bytes with the previous reminder if any
        ALOGD("bytes_avail is after read %d", bytes_avail);
        buf_itr = (unsigned char *)buf;

        do {
            // Check for MagicNumber 0x454D4F52
            if (buf_itr[0] != magic_num[0] || buf_itr[1] != magic_num[1] ||
                    buf_itr[2] != magic_num[2] || buf_itr[3] != magic_num[3]) {
                  ALOGE("Could not find the magic number, reading again");
                  ALOGE("buf_itr[0] %x buf_itr[1] %x buf_itr[2] %x buf_itr[3] %x ",
                            buf_itr[0], buf_itr[1], buf_itr[2], buf_itr[3]);
                  goto exit;
            }
            ALOGD("bytes_avail is after magic %d: prev :%d", bytes_avail, bytes_avail + 540);
            // Bookmark the start of the frame
            frame_start = buf_itr;

            // Skip the magic number
            buf_itr += 4;
            bytes_avail -= 4;

            // Read the tunnelID
            tunnel_id = ((unsigned char) (buf_itr[0]) |
                         (unsigned char) (buf_itr[1]) << 8);

            // Skip tunnelID
            buf_itr += 2;
            bytes_avail -= 2;

            tunl_src = ((unsigned char) (buf_itr[0]) |
                         (unsigned char) (buf_itr[1]) << 8);

            // Skip src id field and CRC - 6 bytes in total
            buf_itr += 6;
            bytes_avail -= 6;

            valid_frame = true;
            if (tunnel_id > MAX_TUNNELS) {
                ALOGE("Invalid tunnel id %d", tunnel_id);
                valid_frame = false;
            }

            struct raf_frame_type rft;
            memcpy(&rft, buf_itr, sizeof(struct raf_frame_type));
            if (true == valid_frame) {
                if (NULL == out_fp[tunnel_id]) {
#ifdef FILENAME_ASSIGN
                    if (is_specified_name) {
                        snprintf(filename, 256, "%s/%s", filepath, filename_str_format);
                    } else if (TNL_ENC_OPAQUE == rft.format.encoding) {
#else
                    if (TNL_ENC_OPAQUE == rft.format.encoding) {
#endif /* FILENAME_ASSIGN */
                        snprintf(filename, MAX_FILE_PATH, "%s%sid%d-src0x%x-enc0x%x_client%d.raw", filepath, FILE_PREFIX, tunnel_id, tunl_src, rft.format.encoding, instance);
                    } else {
                        snprintf(filename, MAX_FILE_PATH, "%s%sid%d-src0x%x-enc0x%x_client%d.pcm", filepath, FILE_PREFIX, tunnel_id, tunl_src, rft.format.encoding, instance);
                    }
                    // Open the file to dump
                    out_fp[tunnel_id] = fopen(filename, "wb");
                    if (NULL == out_fp[tunnel_id]) {
                        ALOGE("ERROR: Failed to open the file %s", filename);
                        goto exit;
                    }
                }
            }

            ALOGD("Tunnel id %d timestamp %llu", tunnel_id, rft.timeStamp);
            tunnel_time_stamps[tunnel_id] = rft.timeStamp;

            // Skip the raf_frame_type
            buf_itr += sizeof(struct raf_frame_type);
            bytes_avail -= sizeof(struct raf_frame_type);

            if (bytes_avail < rft.format.frameSizeInBytes) {
                ALOGD("Incomplete frame received bytes_avail %d framesize %d", bytes_avail, rft.format.frameSizeInBytes);
                buf_itr = frame_start;
                bytes_avail += min_bytes_req;
                goto read_again;
            }

            if (true == valid_frame) {
                ALOGD("@@@Tunnel id %d encoding %d", tunnel_id, rft.format.encoding);
                if (TNL_ENC_AFLOAT == rft.format.encoding) {
                    parse_audio_tunnel_data(out_fp[tunnel_id], buf_itr, rft.format.frameSizeInBytes);
                } else {
                    fwrite(buf_itr, rft.format.frameSizeInBytes, 1, out_fp[tunnel_id]);
                }
            }

            /* Calculate the frame drop count */
            if (notFirstFrame[tunnel_id]) {
                   frameDropCount[tunnel_id] += (rft.seqNo - lastSeqNum[tunnel_id] - 1);
            }
            lastSeqNum[tunnel_id] = rft.seqNo;
            notFirstFrame[tunnel_id] = 1;
            // Skip the data
            buf_itr += rft.format.frameSizeInBytes;
            bytes_avail -= rft.format.frameSizeInBytes;
            /* For debugging the tunnel read errors or wrong magic numbers or bus errors*/
            bytes_read += rft.format.frameSizeInBytes + min_bytes_req;
        } while (bytes_avail > min_bytes_req);
    }

exit:
    for (i = 0; i < MAX_TUNNELS; i++) {
        if (notFirstFrame[i]) {
            ALOGE("drop count tunnel id %u: %u", i, frameDropCount[i]);
        }
    }
    ALOGE("bytes_read so far %d", bytes_read);
    if (buf) {
        free(buf);
        buf = NULL;
    }

    if (unp_out_fp) {
        fflush(unp_out_fp);
        fclose(unp_out_fp);
    }

    for (i = 0; i < MAX_TUNNELS; i++) {
        if (out_fp[i]) {
            fflush(out_fp[i]);
            fclose(out_fp[i]);
        }
    }

    for (i = 0; i < num_of_tunnels; i++) {
        err = ia_disable_tunneling_source(thdl, tunnel_src[i], tunnel_mode[i], tunnel_encode[i]);
        if (0 != err) {
            ALOGE("Failed to disable tunneling for tunl_id %u src_id %u", i, tunnel_src[i]);
        }
    }

    err = ia_stop_tunneling(thdl);
    if (0 != err) {
        ALOGE("Failed to stop tunneling");
    }

    timer_delete(timer_id);

    return 0;
}
