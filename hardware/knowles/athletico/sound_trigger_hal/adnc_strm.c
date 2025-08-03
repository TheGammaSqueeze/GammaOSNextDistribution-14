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
#include <stdint.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#define LOG_TAG "SoundTriggerHALAdnc"
//#define LOG_NDEBUG 0
//#define LOG_NDDEBUG 0

#include <log/log.h>
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>
#include "adnc_strm.h"
#include "tunnel.h"

#define MAX_TUNNELS         (32)
#define BUF_SIZE            (8192)

#define CVQ_TUNNEL_ID       (1)
#define TNL_Q15             (0xF)

#define UNPARSED_OUTPUT_FILE "/data/data/unparsed_output"
// By defining this macros, dumps will be enabled at key points to help in debugging
//#define ENABLE_DEBUG_DUMPS
#define HOTWORD_MODEL (0)
#define AMBIENT_MODEL (1)

struct raf_format_type {
    uint16_t frameSizeInBytes;    // Frame length in bytes
    uint8_t encoding;             // Encoding
    uint8_t sampleRate;           // Sample rate
};

struct raf_frame_type {
    uint64_t timeStamp;             // Timestamp of the frame
    uint32_t seqNo;                 // Optional sequence number of the frame
    struct raf_format_type format;  // Format information for the frame
    uint32_t data[0];               /* Start of the variable size payload.
                                       It must start at 128 bit aligned
                                       address for all the frames */
};

struct adnc_strm_device
{
    struct ia_tunneling_hal *tun_hdl;
    int end_point;
    int idx;
    int mode;
    int encode;

    bool enable_stripping;
    unsigned int kw_start_frame;

    void *pcm_buf;
    size_t pcm_buf_size;
    size_t pcm_avail_size;
    size_t pcm_read_offset;

    void *unparsed_buf;
    size_t unparsed_buf_size;
    size_t unparsed_avail_size;

#ifdef DUMP_UNPARSED_OUTPUT
    FILE *dump_file;
#endif

    pthread_mutex_t lock;
};

static void kst_split_aft(uint32_t *pAfloat, int32_t *exp,
                        int64_t *mant, int32_t *sign)
{
    uint32_t uAft = *pAfloat;

    *exp = (uAft >> 25) & 0x3F;
    *mant = uAft & 0x1FFFFFF;
    *sign = uAft >> 31;
    if (*exp || *mant) {
        *mant |= 1 << 25;
    }
}

static void kst_aft_to_dbl(void *pDouble, void *pAfloat)
{
    uint64_t uDbl;
    int32_t exp;
    int32_t sign;
    int64_t mant;

    kst_split_aft((uint32_t *)pAfloat, &exp, &mant, &sign);
    if (exp || mant) {
        uDbl = ((uint64_t)sign << 63) |
               ((uint64_t)(exp + (1023 - (1 << 5))) << 52) |
               ((uint64_t)(mant & ((1 << 25) - 1)) << (52 - 25));
    } else {
        uDbl = (uint64_t)sign << 63;
    }
    *((uint64_t *)pDouble) = uDbl;
}

void kst_float_to_q15_vector(void *pDst, void *pSrc, uint32_t elCnt)
{
    uint32_t *pSrcT;
    int16_t *pDstT;
    uint32_t idx;
    double smp;

    pSrcT = (uint32_t *)pSrc;
    pDstT = (int16_t *)pDst;
    for (idx = 0; idx < elCnt; idx++) {
        kst_aft_to_dbl(&smp, &(pSrcT[idx]));
        smp = smp * 32768.0;
        pDstT[idx] = ((smp < 32767.0) ?
                     ((smp > -32768.0) ?
                     ((int16_t)smp) : -32768) : 32767);
    }
}

void parse_audio_tunnel_data(unsigned char *buf_itr,
                            unsigned char *pcm_buf_itr,
                            int frame_sz_in_bytes,
                            bool is_q15_conversion_required)
{
    //char q16_buf[BUF_SIZE]; // This can be smaller but by how much?
    int frameSizeInWords = (frame_sz_in_bytes + 3) >> 2;

    if (buf_itr == NULL || pcm_buf_itr == NULL) {
        ALOGE("%s: Buffer is NULL", __func__);
        return;
    }

    if (is_q15_conversion_required == true) {
        kst_float_to_q15_vector(pcm_buf_itr, buf_itr, frameSizeInWords);
    } else {
        memcpy(pcm_buf_itr, buf_itr, frame_sz_in_bytes);
    }
#ifdef ENABLE_DEBUG_DUMPS
    out_fp = fopen("/data/data/pcm_dump", "ab");
    if (out_fp) {
        ALOGE("Dumping to pcm_dump");
        fwrite(pcm_buf_itr, (frameSizeInWords * 2), 1, out_fp);
        fflush(out_fp);
        fclose(out_fp);
    } else {
        ALOGE("Failed to open the out_fp file %s", strerror(errno));
        ALOGE("out_fp is NULL");
    }
#endif
}

static int parse_tunnel_buf(struct adnc_strm_device *adnc_strm_dev)
{
    /*
     * The magic number is ROME in ASCII reversed.
     * So we are looking for EMOR in the byte stream
     */
    const unsigned char magic_num[4] = {0x45, 0x4D, 0x4F, 0x52};
    unsigned short int tunnel_id;
    unsigned char *start_frame = NULL;
    bool valid_frame = true;
    unsigned char *buf_itr = adnc_strm_dev->unparsed_buf;
    /*
     * Minimum bytes required is
     * magic number + tunnel id + reserved and crc + raf struct
     */
    int min_bytes_req = 4 + 2 + 6 + sizeof(struct raf_frame_type);
    int bytes_avail = adnc_strm_dev->unparsed_avail_size;
    unsigned char *pcm_buf_itr = NULL;
    int curr_pcm_frame_size;
    bool is_q15_conversion_required = false;

    if (buf_itr == NULL) {
        ALOGE("Invalid input sent to parse_tunnel_buf");
        return 0;
    }

    do {
        // Check for MagicNumber 0x454D4F52
        while (buf_itr[0] != magic_num[0] || buf_itr[1] != magic_num[1] ||
                buf_itr[2] != magic_num[2] || buf_itr[3] != magic_num[3]) {
            buf_itr++;
            bytes_avail--;
            if (bytes_avail <= 0) {
                ALOGE("Could not find the magic number, reading again");
                ALOGE("buf_itr[0] %x buf_itr[1] %x buf_itr[2] %x buf_itr[3] %x",
                        buf_itr[0], buf_itr[1], buf_itr[2], buf_itr[3]);
                return 0;
            }
        }

        start_frame = buf_itr;

        // Skip the magic number
        buf_itr += 4;
        bytes_avail -= 4;

        // Read the tunnelID
        tunnel_id = ((unsigned char) (buf_itr[0]) |
                    (unsigned char) (buf_itr[1]) << 8);

        // Skip tunnelID
        buf_itr += 2;
        bytes_avail -= 2;

        // Skip Reserved field and CRC - 6 bytes in total
        buf_itr += 6;
        bytes_avail -= 6;

        valid_frame = true;
        // There is only one tunnel data we are looking
        if (tunnel_id > MAX_TUNNELS) {
            ALOGE("Invalid tunnel id %d\n", tunnel_id);
            valid_frame = false;
        }

        struct raf_frame_type rft;
        memcpy(&rft, buf_itr, sizeof(struct raf_frame_type));

        bool skip_extra_data = false;
        if ((adnc_strm_dev->enable_stripping == true) &&
            (rft.seqNo < adnc_strm_dev->kw_start_frame)) {
            skip_extra_data = true;
        }

        /*
         * 1 indicates that it is afloat encoding and
         * F indicates it is in q15 encoding
         */
        if (rft.format.encoding == 1) {
            is_q15_conversion_required = true;
            curr_pcm_frame_size = rft.format.frameSizeInBytes / 2;
        } else {
            is_q15_conversion_required = false;
            curr_pcm_frame_size = rft.format.frameSizeInBytes;
        }

        // Skip the raf_frame_type
        buf_itr += sizeof(struct raf_frame_type);
        bytes_avail -= sizeof(struct raf_frame_type);

        if (bytes_avail < rft.format.frameSizeInBytes) {
            ALOGD("Incomplete frame received bytes_avail %d framesize %d",
                    bytes_avail, rft.format.frameSizeInBytes);
            bytes_avail += min_bytes_req;
            break;
        }

        if (valid_frame == true && skip_extra_data == false) {
            if ((adnc_strm_dev->pcm_avail_size + curr_pcm_frame_size) <
                adnc_strm_dev->pcm_buf_size) {
                pcm_buf_itr = (unsigned char *)adnc_strm_dev->pcm_buf +
                                adnc_strm_dev->pcm_avail_size;
                parse_audio_tunnel_data(buf_itr, pcm_buf_itr,
                                        rft.format.frameSizeInBytes,
                                        is_q15_conversion_required);
                adnc_strm_dev->pcm_avail_size += curr_pcm_frame_size;
            } else {
                ALOGD("Not enough PCM buffer available break now");
                bytes_avail += min_bytes_req;
                break;
            }
        }

        // Skip the data
        buf_itr += rft.format.frameSizeInBytes;
        bytes_avail -= rft.format.frameSizeInBytes;
    } while (bytes_avail > min_bytes_req);

    return bytes_avail;
}


__attribute__ ((visibility ("default")))
size_t adnc_strm_read(long handle, void *buffer, size_t bytes)
{
    int ret = 0;
    struct adnc_strm_device *adnc_strm_dev = (struct adnc_strm_device *) handle;
    int bytes_read, bytes_rem;

    if (adnc_strm_dev == NULL) {
        ALOGE("Invalid handle");
        ret = 0;
        goto exit;
    }

    pthread_mutex_lock(&adnc_strm_dev->lock);

    if (bytes > adnc_strm_dev->pcm_avail_size) {
        /*
         * We don't have enough PCM data, read more from the device.
         * First copy the remainder of the PCM buffer to the front
         * of the PCM buffer
         */
        if (adnc_strm_dev->pcm_avail_size != 0) {
            ALOGD("Copying to the front of the buffer pcm_avail_size %zu"
                  " pcm_read_offset %zu", adnc_strm_dev->pcm_avail_size,
                    adnc_strm_dev->pcm_read_offset);
            memcpy(adnc_strm_dev->pcm_buf,
                   ((unsigned char *)adnc_strm_dev->pcm_buf +
                    adnc_strm_dev->pcm_read_offset),
                    adnc_strm_dev->pcm_avail_size);
        }
        // Always read from the start of the PCM buffer at this point of time
        adnc_strm_dev->pcm_read_offset = 0;

read_again:
        // Read data from the kernel, account for the leftover
        // data from previous run
        bytes_read = ia_read_tunnel_data(adnc_strm_dev->tun_hdl,
                                        (void *)((unsigned char *)
                                        adnc_strm_dev->unparsed_buf +
                                        adnc_strm_dev->unparsed_avail_size),
                                        BUF_SIZE);
        if (bytes_read <= 0) {
            ALOGE("Failed to read data from tunnel");
            ret = 0; // TODO should we try to read a couple of times?
            pthread_mutex_unlock(&adnc_strm_dev->lock);
            goto exit;
        }

        // Parse the data to get PCM data
        adnc_strm_dev->unparsed_avail_size += bytes_read;
        bytes_rem = parse_tunnel_buf(adnc_strm_dev);

#ifdef ENABLE_DEBUG_DUMPS
        if (adnc_strm_dev->pcm_avail_size != 0) {
            FILE *out_fp = fopen("/data/data/pcm_dump2", "ab");
            if (out_fp) {
                ALOGE("Dumping to pcm_dump2");
                fwrite(((unsigned char *)adnc_strm_dev->pcm_buf +
                        adnc_strm_dev->pcm_avail_size),
                        adnc_strm_dev->pcm_avail_size, 1, out_fp);
                fflush(out_fp);
                fclose(out_fp);
            } else {
                ALOGE("Failed to open the pcm_dump2 file %s", strerror(errno));
            }
        }
#endif

        // Copy the left over unparsed data to the front of the buffer
        if (bytes_rem != 0) {
            int offset = adnc_strm_dev->unparsed_avail_size - bytes_rem;
            memcpy(adnc_strm_dev->unparsed_buf,
                   ((unsigned char *)adnc_strm_dev->unparsed_buf + offset),
                    bytes_rem);
        }
        adnc_strm_dev->unparsed_avail_size = bytes_rem;

        /*
         * If stripping is enabled then we didn't read anything to the pcm
         * bufferso read again or if we still don't have enough bytes then
         * read data again.
         */
        if (adnc_strm_dev->pcm_avail_size == 0 ||
            adnc_strm_dev->pcm_avail_size < bytes) {
            goto read_again;
        }
    }

    // Copy the PCM data to output buffer and return
    memcpy(buffer,
           ((unsigned char *)adnc_strm_dev->pcm_buf +
            adnc_strm_dev->pcm_read_offset),
            bytes);

#ifdef ENABLE_DEBUG_DUMPS
    char l_buffer[64];
    int cx;
    FILE *out_fp = NULL;
    cx = snprintf(l_buffer, sizeof(l_buffer), "/data/data/adnc_dump_%x",
                  adnc_strm_dev->end_point);
    if (cx >= 0 && cx < 64)
        out_fp = fopen(l_buffer, "ab");
    if (out_fp) {
        ALOGD("Dumping to adnc_dump:%s", l_buffer);
        fwrite(buffer, bytes, 1, out_fp);
        fflush(out_fp);
        fclose(out_fp);
    } else {
        ALOGE("Failed to open the adnc_dump file %s", strerror(errno));
    }
#endif

    adnc_strm_dev->pcm_avail_size -= bytes;
    adnc_strm_dev->pcm_read_offset += bytes;

    pthread_mutex_unlock(&adnc_strm_dev->lock);

exit:

    return bytes;
}


__attribute__ ((visibility ("default")))
long adnc_strm_open(bool enable_stripping,
                    unsigned int kw_start_frame,
                    int stream_end_point)
{
    int ret = 0, err;
    struct adnc_strm_device *adnc_strm_dev = NULL;

    adnc_strm_dev = (struct adnc_strm_device *)
                        calloc(1, sizeof(struct adnc_strm_device));
    if (adnc_strm_dev == NULL) {
        ALOGE("Failed to allocate memory for adnc_strm_dev");
        ret = 0;
        goto exit_no_memory;
    }

    pthread_mutex_init(&adnc_strm_dev->lock, (const pthread_mutexattr_t *) NULL);

    pthread_mutex_lock(&adnc_strm_dev->lock);

    adnc_strm_dev->end_point = stream_end_point;
    adnc_strm_dev->idx = 0;
    adnc_strm_dev->mode = 0;
    adnc_strm_dev->encode = TNL_Q15;
    adnc_strm_dev->enable_stripping = enable_stripping;
    adnc_strm_dev->kw_start_frame = kw_start_frame;
    adnc_strm_dev->tun_hdl = NULL;
    adnc_strm_dev->pcm_buf = NULL;
    adnc_strm_dev->unparsed_buf = NULL;

    adnc_strm_dev->tun_hdl = ia_start_tunneling(640);
    if (adnc_strm_dev->tun_hdl == NULL) {
        ALOGE("Failed to start tunneling");
        ret = 0;
        goto exit_on_error;
    }

    ret = ia_enable_tunneling_source(adnc_strm_dev->tun_hdl,
                                    adnc_strm_dev->end_point,
                                    adnc_strm_dev->mode,
                                    adnc_strm_dev->encode);
    if (ret != 0) {
        ALOGE("Failed to enable tunneling for CVQ tunl_id %u src_id %u mode %u",
                adnc_strm_dev->idx, adnc_strm_dev->end_point, adnc_strm_dev->mode);
        ret = 0;
        goto exit_on_error;
    }

    adnc_strm_dev->unparsed_buf_size = BUF_SIZE * 2;
    adnc_strm_dev->unparsed_avail_size = 0;
    adnc_strm_dev->unparsed_buf = malloc(adnc_strm_dev->unparsed_buf_size);
    if (adnc_strm_dev->unparsed_buf == NULL) {
        ret = 0;
        ALOGE("Failed to allocate memory for unparsed buffer");
        goto exit_on_error;
    }

    adnc_strm_dev->pcm_buf_size = BUF_SIZE * 2;
    adnc_strm_dev->pcm_avail_size = 0;
    adnc_strm_dev->pcm_read_offset = 0;
    adnc_strm_dev->pcm_buf = malloc(adnc_strm_dev->pcm_buf_size);
    if (adnc_strm_dev->pcm_buf == NULL) {
        ret = 0;
        ALOGE("Failed to allocate memory for pcm buffer");
        goto exit_on_error;
    }

    pthread_mutex_unlock(&adnc_strm_dev->lock);

    return (long)adnc_strm_dev;

exit_on_error:
    if (adnc_strm_dev->pcm_buf) {
        free(adnc_strm_dev->pcm_buf);
    }

    if (adnc_strm_dev->unparsed_buf) {
        free(adnc_strm_dev->unparsed_buf);
    }

    err = ia_disable_tunneling_source(adnc_strm_dev->tun_hdl,
                                    adnc_strm_dev->end_point,
                                    adnc_strm_dev->mode,
                                    adnc_strm_dev->encode);
    if (err != 0) {
        ALOGE("Failed to disable the tunneling source");
    }

    err = ia_stop_tunneling(adnc_strm_dev->tun_hdl);
    if (err != 0) {
        ALOGE("Failed to stop tunneling");
    }

    pthread_mutex_unlock(&adnc_strm_dev->lock);

    if (adnc_strm_dev) {
        free(adnc_strm_dev);
    }

exit_no_memory:

    return ret;
}

__attribute__ ((visibility ("default")))
int adnc_strm_close(long handle)
{
    int ret = 0;
    struct adnc_strm_device *adnc_strm_dev = (struct adnc_strm_device *) handle;

    if (adnc_strm_dev == NULL) {
        ALOGE("Invalid handle");
        ret = -1;
        goto exit;
    }

    pthread_mutex_lock(&adnc_strm_dev->lock);

    if (adnc_strm_dev->pcm_buf) {
        free(adnc_strm_dev->pcm_buf);
    }

    if (adnc_strm_dev->unparsed_buf) {
        free(adnc_strm_dev->unparsed_buf);
    }

    ret = ia_disable_tunneling_source(adnc_strm_dev->tun_hdl,
                                    adnc_strm_dev->end_point,
                                    adnc_strm_dev->mode,
                                    adnc_strm_dev->encode);
    if (ret != 0) {
        ALOGE("Failed to disable the tunneling source");
    }

    ret = ia_stop_tunneling(adnc_strm_dev->tun_hdl);
    if (ret != 0) {
        ALOGE("Failed to stop tunneling");
    }

    pthread_mutex_unlock(&adnc_strm_dev->lock);

    if (adnc_strm_dev) {
        free(adnc_strm_dev);
    }

exit:
    return ret;
}

