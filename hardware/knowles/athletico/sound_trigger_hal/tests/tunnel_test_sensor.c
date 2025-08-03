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
#include <errno.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define LOG_TAG "ia_tunneling_sensor_test"

#include <log/log.h>
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>
#include <linux/mfd/adnc/iaxxx-tunnel-intf.h>
#include <linux/mfd/adnc/iaxxx-sensor-tunnel.h>

#define MAX_TUNNELS                 32
#define BUF_SIZE                    8192
#define OUTPUT_FILE                 "/data/data/tnl_op"
#define UNPARSED_OUTPUT_FILE        "/data/data/unparsed_output"

#define TUNNEL_DEVICE               "/dev/sensor_tunnel"
#define SENSOR_TUNNEL_SOURCE_ID     IAXXX_SYSID_CHANNEL_RX_15_EP_0
#define SENSOR_TUNNEL_MODE          TNL_MODE_ASYNC
#define SENSOR_TUNNEL_ENCODE        TNL_ENC_Q15
#define VSYNC_SENSOR_SOURCE_ID      IAXXX_SYSID_SENSOR_OUTPUT_1
#define VSYNC_SENSOR_MODE           TNL_MODE_ASYNC
#define VSYNC_SENSOR_ENCODE         TNL_ENC_OPAQUE

struct raf_format_type {
    uint16_t frameSizeInBytes;  // Frame length in bytes
    uint8_t encoding;           // Encoding
    uint8_t sampleRate;         // Sample rate
};

struct raf_frame_type {
    uint64_t timeStamp; // Timestamp of the frame
    uint32_t seqNo;     // Optional sequence number of the frame

    struct raf_format_type format; // Format information for the frame
    uint32_t data[0];   /* Start of the variable size payload. It must start at
                           128 bit aligned address for all the frames */
};

volatile int capturing = 1;

void sigint_handler(int sig __unused) {
    ALOGE("Interrupted, setting the exit condition");
    capturing = 0;
}

int main(int argc, char *argv[]) {
    int err = 0;
    FILE *tun_dev = NULL;
    FILE *out_fp[MAX_TUNNELS] = { NULL };
    FILE *unp_out_fp = NULL;
    int bytes_avail = 0, bytes_rem = 0;
    int bytes_read = 0;
    void *buf = NULL;
    // The magic number is ROME in ASCII reversed.
    // So we are looking for EMOR in the byte stream
    const unsigned char magic_num[4]  = {0x45, 0x4D, 0x4F, 0x52};
    int i = 0;
    bool valid_frame = true;
    int timer_signal = 0;
    int lastSeqNum[MAX_TUNNELS] = { 0 };
    int notFirstFrame[MAX_TUNNELS] = { 0 };
    int frameDropCount[MAX_TUNNELS] = { 0 };
    uint64_t tunnel_time_stamps[MAX_TUNNELS] = { 0 };
    unsigned char *frame_start, *buf_itr;
    // Minimum bytes required is the magic number + tunnel id + reserved and
    // crc + raf struct
    int min_bytes_req = 4 + 2 + 6 + sizeof(struct raf_frame_type);
    struct tunlMsg tm;

    if (argc < 2) {
        ALOGE("USAGE: %s <Time in seconds> ", argv[0]);
        return -EINVAL;
    }

    timer_signal= strtol(argv[1], NULL, 0);
    ALOGD("tunnel out timer based req %d", timer_signal);

    tun_dev = fopen(TUNNEL_DEVICE, "r");
    if (NULL == tun_dev) {
        ALOGE("Failed to open the tunnel device node");
        goto exit;
    }

    err = ioctl(fileno(tun_dev), FLICKER_ROUTE_SETUP, NULL);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel setup failed for VSYNC sensor %s", __func__, strerror(errno));
        goto exit;
    }

    tm.tunlSrc = VSYNC_SENSOR_SOURCE_ID;
    tm.tunlMode = VSYNC_SENSOR_MODE;
    tm.tunlEncode = VSYNC_SENSOR_ENCODE;
    err = ioctl(fileno(tun_dev), FLICKER_TUNNEL_SETUP, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel setup failed for VSYNC sensor %s", __func__, strerror(errno));
        goto exit;
    }

    tm.tunlSrc = SENSOR_TUNNEL_SOURCE_ID;
    tm.tunlMode = SENSOR_TUNNEL_MODE;
    tm.tunlEncode = SENSOR_TUNNEL_ENCODE;
    err = ioctl(fileno(tun_dev), FLICKER_TUNNEL_SETUP, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel setup failed %s", __func__, strerror(errno));
        goto exit;
    }

    buf = malloc(BUF_SIZE * 2);
    if (NULL == buf) {
        ALOGE("Failed to allocate memory to read buffer");
        goto exit;
    }

    unp_out_fp = fopen(UNPARSED_OUTPUT_FILE, "wb");
    if (NULL == unp_out_fp) {
        ALOGE("Failed to open the file %s", UNPARSED_OUTPUT_FILE);
        goto exit;
    }

    signal(SIGINT, sigint_handler);

    if (timer_signal) {
        signal(SIGALRM, sigint_handler);
        alarm(timer_signal);
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

        // Ensure that we read BUF_SIZE always otherwise kernel read will hang
        bytes_avail = fread((void *)((unsigned char *)buf + bytes_rem),
                             1, BUF_SIZE, tun_dev);
        if (bytes_avail <= 0) {
            ALOGE("Failed to read data from the tunnel");
            break;
        }

        fwrite((void *)((unsigned char *)buf + bytes_rem),
                bytes_avail, 1, unp_out_fp);
        fflush(unp_out_fp);

        // update the available bytes with the previous reminder if any
        bytes_avail += bytes_rem;
        ALOGD("bytes_avail is after read %d", bytes_avail);
        buf_itr = (unsigned char *)buf;

        do {
            // Check for MagicNumber 0x454D4F52
            if (buf_itr[0] != magic_num[0] || buf_itr[1] != magic_num[1] ||
                    buf_itr[2] != magic_num[2] || buf_itr[3] != magic_num[3]) {
                  ALOGE("Could not find the magic number, reading again");
                  ALOGE("buf_itr[0] %x buf_itr[1] %x buf_itr[2] %x "
                        "buf_itr[3] %x ", buf_itr[0], buf_itr[1], buf_itr[2],
                                          buf_itr[3]);
                  goto exit;
            }
            ALOGD("bytes_avail is after magic %d: prev :%d",
                    bytes_avail, bytes_avail + 540);
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
                    char filename[256];
                    snprintf(filename, 256,
                             "%sid%d-src0x%x-enc0x%x.raw",
                             OUTPUT_FILE, tunnel_id,
                             tunl_src, rft.format.encoding);
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
                ALOGD("Incomplete frame received bytes_avail %d framesize %d",
                        bytes_avail, rft.format.frameSizeInBytes);
                buf_itr = frame_start;
                bytes_avail += min_bytes_req;
                goto read_again;
            }

            if (true == valid_frame) {
                ALOGD("@@@Tunnel id %d encoding %d",
                        tunnel_id, rft.format.encoding);
                fwrite(buf_itr, rft.format.frameSizeInBytes, 1, out_fp[tunnel_id]);
            }

            /* Calculate the frame drop count */
            if (notFirstFrame[tunnel_id]) {
                   frameDropCount[tunnel_id] +=
                            (rft.seqNo - lastSeqNum[tunnel_id] - 1);
            }
            lastSeqNum[tunnel_id] = rft.seqNo;
            notFirstFrame[tunnel_id] = 1;
            // Skip the data
            buf_itr += rft.format.frameSizeInBytes;
            bytes_avail -= rft.format.frameSizeInBytes;
            bytes_read += rft.format.frameSizeInBytes + min_bytes_req;
        } while (bytes_avail > min_bytes_req);
    }

exit:
    for (i = 0; i < MAX_TUNNELS; i++) {
        if (notFirstFrame[i]) {
            ALOGD("drop count tunnel id %u: %u", i, frameDropCount[i]);
        }
    }
    ALOGD("bytes_read so far %d", bytes_read);
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

    tm.tunlSrc = SENSOR_TUNNEL_SOURCE_ID;
    tm.tunlMode = SENSOR_TUNNEL_MODE;
    tm.tunlEncode = SENSOR_TUNNEL_ENCODE;
    err = ioctl(fileno(tun_dev), FLICKER_TUNNEL_TERMINATE, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel terminate failed %s",
                __func__, strerror(errno));
    }

    tm.tunlSrc = VSYNC_SENSOR_SOURCE_ID;
    tm.tunlMode = VSYNC_SENSOR_MODE;
    tm.tunlEncode = VSYNC_SENSOR_ENCODE;
    err = ioctl(fileno(tun_dev), FLICKER_TUNNEL_TERMINATE, &tm);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel terminate failed %s",
                __func__, strerror(errno));
    }

    err = ioctl(fileno(tun_dev), FLICKER_ROUTE_TERMINATE, NULL);
    if (err == -1) {
        ALOGE("%s: ERROR Tunnel terminate failed %s",
                __func__, strerror(errno));
    }

    if (tun_dev)
        fclose(tun_dev);

    return 0;
}
