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
 * ISO7816 T=1 interface.
 *
 */

#ifndef ISO7816_T1_H
#define ISO7816_T1_H

#include <stdint.h>

struct t1_state {
    int spi_fd; /* File descriptor for transport */

    struct {
        /* Ordered by decreasing priority */
        unsigned halt    : 1;   /* Halt dispatch loop             */
        unsigned request : 1;   /* Build a SBLOCK request         */
        unsigned reqresp : 1;   /* Build a SBLOCK response        */
        unsigned badcrc  : 1;   /* Build a RBLOCK for CRC error   */
        unsigned timeout : 1;   /* Resend S-BLOCK or send R-BLOCK */
        unsigned aborted : 1;   /* Abort was requested            */
    } state;

    uint8_t ifsc; /* IFS for card        */
    uint8_t ifsd; /* IFS for device      */
    uint8_t nad;  /* NAD byte for device */
    uint8_t nadc; /* NAD byte for card   */
    uint8_t wtx;  /* Read timeout scaler */

    unsigned bwt; /* Block Waiting Timeout */

    uint8_t chk_algo; /* One of CHECKSUM_LRC or CHECKSUM_CRC                */
    uint8_t retries;  /* Remaining retries in case of incorrect block       */
    uint8_t request;  /* Current pending request, valid only during request */

    int wtx_rounds;     /* Limit number of WTX round from card    */
    int wtx_max_rounds; /* Maximum number of WTX rounds from card */
    int wtx_max_value;  /* Maximum value of WTX supported by host */

    uint8_t need_reset; /* Need to send a reset on first start            */
    uint8_t need_resync; /* Need to send a reset on first start            */
	uint8_t need_ifsd_sync; /* Need to send a IFSD request after RESET            */
    uint8_t atr[32];    /* ISO7816 defines ATR with a maximum of 32 bytes */
    uint8_t atr_length; /* Never over 32                                  */

    /* Emission window */
    struct t1_send {
        const uint8_t *start;
        const uint8_t *end;
        uint8_t        next; /* N(S) */
    } send;

    /* Reception window */
    struct t1_recv {
        uint8_t *start;
        uint8_t *end;
        uint8_t  next; /* N(R) */
        size_t   size; /* Maximum window size */
    } recv;

    size_t recv_max;  /* Maximum number of expected bytes on reception */
    size_t recv_size; /* Received number of bytes so far */

    /* Max size is:
     *  - 3 bytes header,
     *  - 254 bytes data,
     *  - 2 bytes CRC
     *
     * Use 255 bytes data in case of invalid block length of 255.
     */
    uint8_t buf[3 + 255 + 2];
};

enum { CHECKSUM_LRC, CHECKSUM_CRC };

void isot1_init(struct t1_state *t1);
void isot1_release(struct t1_state *t1);
void isot1_bind(struct t1_state *t1, int src, int dst);
int isot1_transceive(struct t1_state *t1, const void *snd_buf,
                     size_t snd_len, void *rcv_buf, size_t rcv_len);
int isot1_negotiate_ifsd(struct t1_state *t1, int ifsd);
int isot1_reset(struct t1_state *t1);
int isot1_resync(struct t1_state *t1);
int isot1_get_atr(struct t1_state *t1, void *atr, size_t n);

/* for check.c */
typedef struct t1_state t1_state_t;

#ifndef EREMOTEIO
# define EREMOTEIO EIO
#endif

#endif /* ISO7816_T1_H */
