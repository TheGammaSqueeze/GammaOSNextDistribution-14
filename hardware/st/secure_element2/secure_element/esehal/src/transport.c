/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/

/**
 * @file
 * $Author$
 * $Revision$
 * $Date$
 *
 * eSE Gemalto kernel driver transport.
 *
 */

#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <time.h>

#include "iso7816_t1.h"
#include "transport.h"
#include "spi.h"

#define NSEC_PER_SEC  1000000000L
#define NSEC_PER_MSEC 1000000L

#define ESE_NAD 0x21

/* < 0 if t1 < t2,
 * > 0 if t1 > t2,
 *   0 if t1 == t2.
 */
static int
ts_compare(const struct timespec *t1, const struct timespec *t2)
{
    if (t1->tv_sec < t2->tv_sec)
        return -1;
    else if (t1->tv_sec > t2->tv_sec)
        return 1;
    else
        return t1->tv_nsec - t2->tv_nsec;
}

static uint32_t
div_uint64_rem(uint64_t dividend, uint32_t divisor, uint64_t *remainder)
{
    uint32_t r    = 0;

    /* Compiler will optimize to modulo on capable platform */
    while (dividend >= divisor)
        dividend -= divisor, r++;

    *remainder = dividend;
    return r;
}

static struct timespec
ts_add_ns(const struct timespec ta, uint64_t ns)
{
    time_t sec = ta.tv_sec +
                 div_uint64_rem(ta.tv_nsec + ns, NSEC_PER_SEC, &ns);
    struct timespec ts = { sec, ns };

    return ts;
}

static int
crc_length(struct t1_state *t1)
{
    int n = 0;

    switch (t1->chk_algo) {
        case CHECKSUM_LRC:
            n = 1;
            break;

        case CHECKSUM_CRC:
            n = 2;
            break;
    }
    return n;
}

int
block_send(struct t1_state *t1, const void *block, size_t n)
{
    if (n < 4)
        return -EINVAL;

    return spi_write(t1->spi_fd, block, n);
}

int
block_recv(struct t1_state *t1, void *block, size_t n)
{
    uint8_t  c;
    int      fd;
    uint8_t *s, i;
    int      len, max;
    long     bwt;

    struct timespec ts, ts_timeout;

    if (n < 4)
        return -EINVAL;

    fd = t1->spi_fd;
    s  = block;

    clock_gettime(CLOCK_MONOTONIC, &ts);
    bwt     = t1->bwt * (t1->wtx ? t1->wtx : 1);
    t1->wtx = 1;

    ts_timeout = ts_add_ns(ts, bwt * NSEC_PER_MSEC);

    /* Pull every 2ms */
    i = 0;
    do {
        /* Wait for 2ms */
        ts = ts_add_ns(ts, 2 * NSEC_PER_MSEC);
        while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, NULL))
            if  (errno != EINTR)
                break;

        len = spi_read(fd, &c, 1);
        if (len < 0)
            return len;

        if (ts_compare(&ts, &ts_timeout) >= 0)
            return -ETIMEDOUT;
    } while (c != ESE_NAD);

    s[i++] = c;

    /* Minimal length is 3 + sizeof(checksum) */
    max = 2 + crc_length(t1);
    len = spi_read(fd, s + 1, max);
    if (len < 0)
        return len;

    i += len;

    /* verify that buffer is large enough. */
    max += s[2];
    if ((size_t)max > n)
        return -ENOMEM;

    /* get block remaining if present */
    if (s[2]) {
        len = spi_read(fd, s + 4, s[2]);
        if (len < 0)
            return len;
    }

    return max + 1;
}
