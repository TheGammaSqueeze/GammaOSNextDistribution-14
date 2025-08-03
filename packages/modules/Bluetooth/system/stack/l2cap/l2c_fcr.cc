/******************************************************************************
 *
 *  Copyright 2004-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  This file contains the L2CAP 1.2 Flow Control and retransmissions
 *  functions
 *
 ******************************************************************************/

#include <base/logging.h>
#include <bluetooth/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "include/check.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/l2c_api.h"
#include "stack/include/l2cdefs.h"
#include "stack/l2cap/l2c_int.h"

/* Flag passed to retransmit_i_frames() when all packets should be retransmitted
 */
#define L2C_FCR_RETX_ALL_PKTS 0xFF

using namespace bluetooth;

/* this is the minimal offset required by OBX to process incoming packets */
static const uint16_t OBX_BUF_MIN_OFFSET = 4;

static const char* SAR_types[] = {"Unsegmented", "Start", "End",
                                  "Continuation"};
static const char* SUP_types[] = {"RR", "REJ", "RNR", "SREJ"};

/* Look-up table for the CRC calculation */
static const unsigned short crctab[256] = {
    0x0000, 0xc0c1, 0xc181, 0x0140, 0xc301, 0x03c0, 0x0280, 0xc241, 0xc601,
    0x06c0, 0x0780, 0xc741, 0x0500, 0xc5c1, 0xc481, 0x0440, 0xcc01, 0x0cc0,
    0x0d80, 0xcd41, 0x0f00, 0xcfc1, 0xce81, 0x0e40, 0x0a00, 0xcac1, 0xcb81,
    0x0b40, 0xc901, 0x09c0, 0x0880, 0xc841, 0xd801, 0x18c0, 0x1980, 0xd941,
    0x1b00, 0xdbc1, 0xda81, 0x1a40, 0x1e00, 0xdec1, 0xdf81, 0x1f40, 0xdd01,
    0x1dc0, 0x1c80, 0xdc41, 0x1400, 0xd4c1, 0xd581, 0x1540, 0xd701, 0x17c0,
    0x1680, 0xd641, 0xd201, 0x12c0, 0x1380, 0xd341, 0x1100, 0xd1c1, 0xd081,
    0x1040, 0xf001, 0x30c0, 0x3180, 0xf141, 0x3300, 0xf3c1, 0xf281, 0x3240,
    0x3600, 0xf6c1, 0xf781, 0x3740, 0xf501, 0x35c0, 0x3480, 0xf441, 0x3c00,
    0xfcc1, 0xfd81, 0x3d40, 0xff01, 0x3fc0, 0x3e80, 0xfe41, 0xfa01, 0x3ac0,
    0x3b80, 0xfb41, 0x3900, 0xf9c1, 0xf881, 0x3840, 0x2800, 0xe8c1, 0xe981,
    0x2940, 0xeb01, 0x2bc0, 0x2a80, 0xea41, 0xee01, 0x2ec0, 0x2f80, 0xef41,
    0x2d00, 0xedc1, 0xec81, 0x2c40, 0xe401, 0x24c0, 0x2580, 0xe541, 0x2700,
    0xe7c1, 0xe681, 0x2640, 0x2200, 0xe2c1, 0xe381, 0x2340, 0xe101, 0x21c0,
    0x2080, 0xe041, 0xa001, 0x60c0, 0x6180, 0xa141, 0x6300, 0xa3c1, 0xa281,
    0x6240, 0x6600, 0xa6c1, 0xa781, 0x6740, 0xa501, 0x65c0, 0x6480, 0xa441,
    0x6c00, 0xacc1, 0xad81, 0x6d40, 0xaf01, 0x6fc0, 0x6e80, 0xae41, 0xaa01,
    0x6ac0, 0x6b80, 0xab41, 0x6900, 0xa9c1, 0xa881, 0x6840, 0x7800, 0xb8c1,
    0xb981, 0x7940, 0xbb01, 0x7bc0, 0x7a80, 0xba41, 0xbe01, 0x7ec0, 0x7f80,
    0xbf41, 0x7d00, 0xbdc1, 0xbc81, 0x7c40, 0xb401, 0x74c0, 0x7580, 0xb541,
    0x7700, 0xb7c1, 0xb681, 0x7640, 0x7200, 0xb2c1, 0xb381, 0x7340, 0xb101,
    0x71c0, 0x7080, 0xb041, 0x5000, 0x90c1, 0x9181, 0x5140, 0x9301, 0x53c0,
    0x5280, 0x9241, 0x9601, 0x56c0, 0x5780, 0x9741, 0x5500, 0x95c1, 0x9481,
    0x5440, 0x9c01, 0x5cc0, 0x5d80, 0x9d41, 0x5f00, 0x9fc1, 0x9e81, 0x5e40,
    0x5a00, 0x9ac1, 0x9b81, 0x5b40, 0x9901, 0x59c0, 0x5880, 0x9841, 0x8801,
    0x48c0, 0x4980, 0x8941, 0x4b00, 0x8bc1, 0x8a81, 0x4a40, 0x4e00, 0x8ec1,
    0x8f81, 0x4f40, 0x8d01, 0x4dc0, 0x4c80, 0x8c41, 0x4400, 0x84c1, 0x8581,
    0x4540, 0x8701, 0x47c0, 0x4680, 0x8641, 0x8201, 0x42c0, 0x4380, 0x8341,
    0x4100, 0x81c1, 0x8081, 0x4040,
};

/*******************************************************************************
 *  Static local functions
*/
static bool process_reqseq(tL2C_CCB* p_ccb, uint16_t ctrl_word);
static void process_s_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf, uint16_t ctrl_word);
static void process_i_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf, uint16_t ctrl_word,
                            bool delay_ack);
static bool retransmit_i_frames(tL2C_CCB* p_ccb, uint8_t tx_seq);
static void prepare_I_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf,
                            bool is_retransmission);
static bool do_sar_reassembly(tL2C_CCB* p_ccb, BT_HDR* p_buf,
                              uint16_t ctrl_word);

/*******************************************************************************
 *
 * Function         l2c_fcr_updcrc
 *
 * Description      This function computes the CRC using the look-up table.
 *
 * Returns          CRC
 *
 ******************************************************************************/
static unsigned short l2c_fcr_updcrc(unsigned short icrc, unsigned char* icp,
                                     int icnt) {
  unsigned short crc = icrc;
  unsigned char* cp = icp;
  int cnt = icnt;

  while (cnt--) {
    crc = ((crc >> 8) & 0xff) ^ crctab[(crc & 0xff) ^ *cp++];
  }

  return (crc);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_tx_get_fcs
 *
 * Description      This function computes the CRC for a frame to be TXed.
 *
 * Returns          CRC
 *
 ******************************************************************************/
static uint16_t l2c_fcr_tx_get_fcs(BT_HDR* p_buf) {
  uint8_t* p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;

  return (l2c_fcr_updcrc(L2CAP_FCR_INIT_CRC, p, p_buf->len));
}

/*******************************************************************************
 *
 * Function         l2c_fcr_rx_get_fcs
 *
 * Description      This function computes the CRC for a received frame.
 *
 * Returns          CRC
 *
 ******************************************************************************/
static uint16_t l2c_fcr_rx_get_fcs(BT_HDR* p_buf) {
  uint8_t* p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;

  /* offset points past the L2CAP header, but the CRC check includes it */
  p -= L2CAP_PKT_OVERHEAD;

  return (
      l2c_fcr_updcrc(L2CAP_FCR_INIT_CRC, p, p_buf->len + L2CAP_PKT_OVERHEAD));
}

/*******************************************************************************
 *
 * Function         l2c_fcr_start_timer
 *
 * Description      This function starts the (monitor or retransmission) timer.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_start_timer(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  uint32_t tout;

  /* The timers which are in milliseconds */
  if (p_ccb->fcrb.wait_ack) {
    tout = (uint32_t)p_ccb->our_cfg.fcr.mon_tout;
  } else {
    tout = (uint32_t)p_ccb->our_cfg.fcr.rtrans_tout;
  }

  /* Only start a timer that was not started */
  if (!alarm_is_scheduled(p_ccb->fcrb.mon_retrans_timer)) {
    alarm_set_on_mloop(p_ccb->fcrb.mon_retrans_timer, tout,
                       l2c_ccb_timer_timeout, p_ccb);
  }
}

/*******************************************************************************
 *
 * Function         l2c_fcr_stop_timer
 *
 * Description      This function stops the (monitor or transmission) timer.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_stop_timer(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  alarm_cancel(p_ccb->fcrb.mon_retrans_timer);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_cleanup
 *
 * Description      This function cleans up the variable used for
 *                  flow-control/retrans.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_cleanup(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;

  alarm_free(p_fcrb->mon_retrans_timer);
  p_fcrb->mon_retrans_timer = NULL;
  alarm_free(p_fcrb->ack_timer);
  p_fcrb->ack_timer = NULL;

  osi_free_and_reset((void**)&p_fcrb->p_rx_sdu);

  fixed_queue_free(p_fcrb->waiting_for_ack_q, osi_free);
  p_fcrb->waiting_for_ack_q = NULL;

  fixed_queue_free(p_fcrb->srej_rcv_hold_q, osi_free);
  p_fcrb->srej_rcv_hold_q = NULL;

  fixed_queue_free(p_fcrb->retrans_q, osi_free);
  p_fcrb->retrans_q = NULL;

  memset(p_fcrb, 0, sizeof(tL2C_FCRB));
}

/*******************************************************************************
 *
 * Function         l2c_fcr_clone_buf
 *
 * Description      This function allocates and copies requested part of a
 *                  buffer at a new-offset.
 *
 * Returns          pointer to new buffer
 *
 ******************************************************************************/
BT_HDR* l2c_fcr_clone_buf(BT_HDR* p_buf, uint16_t new_offset,
                          uint16_t no_of_bytes) {
  CHECK(p_buf != NULL);
  /*
   * NOTE: We allocate extra L2CAP_FCS_LEN octets, in case we need to put
   * the FCS (Frame Check Sequence) at the end of the buffer.
   */
  uint16_t buf_size = no_of_bytes + sizeof(BT_HDR) + new_offset + L2CAP_FCS_LEN;
  BT_HDR* p_buf2 = (BT_HDR*)osi_malloc(buf_size);

  p_buf2->offset = new_offset;
  p_buf2->len = no_of_bytes;
  memcpy(((uint8_t*)(p_buf2 + 1)) + p_buf2->offset,
         ((uint8_t*)(p_buf + 1)) + p_buf->offset, no_of_bytes);

  return (p_buf2);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_is_flow_controlled
 *
 * Description      This function checks if the CCB is flow controlled by peer.
 *
 * Returns          The control word
 *
 ******************************************************************************/
bool l2c_fcr_is_flow_controlled(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  if (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) {
    /* Check if remote side flowed us off or the transmit window is full */
    if ((p_ccb->fcrb.remote_busy) ||
        (fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q) >=
         p_ccb->peer_cfg.fcr.tx_win_sz)) {
      return (true);
    }
  }
  return (false);
}

/*******************************************************************************
 *
 * Function         prepare_I_frame
 *
 * Description      This function sets the FCR variables in an I-frame that is
 *                  about to be sent to HCI for transmission. This may be the
 *                  first time the I-frame is sent, or a retransmission
 *
 * Returns          -
 *
 ******************************************************************************/
static void prepare_I_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf,
                            bool is_retransmission) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);
  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;
  uint8_t* p;
  uint16_t fcs;
  uint16_t ctrl_word;
  bool set_f_bit = p_fcrb->send_f_rsp;

  p_fcrb->send_f_rsp = false;

  if (is_retransmission) {
    /* Get the old control word and clear out the old req_seq and F bits */
    p = ((uint8_t*)(p_buf + 1)) + p_buf->offset + L2CAP_PKT_OVERHEAD;

    STREAM_TO_UINT16(ctrl_word, p);

    ctrl_word &= ~(L2CAP_FCR_REQ_SEQ_BITS + L2CAP_FCR_F_BIT);
  } else {
    ctrl_word = p_buf->layer_specific & L2CAP_FCR_SEG_BITS; /* SAR bits */
    ctrl_word |=
        (p_fcrb->next_tx_seq << L2CAP_FCR_TX_SEQ_BITS_SHIFT); /* Tx Seq */

    p_fcrb->next_tx_seq = (p_fcrb->next_tx_seq + 1) & L2CAP_FCR_SEQ_MODULO;
  }

  /* Set the F-bit and reqseq only if using re-transmission mode */
  if (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) {
    if (set_f_bit) ctrl_word |= L2CAP_FCR_F_BIT;

    ctrl_word |= (p_fcrb->next_seq_expected) << L2CAP_FCR_REQ_SEQ_BITS_SHIFT;

    p_fcrb->last_ack_sent = p_ccb->fcrb.next_seq_expected;

    alarm_cancel(p_ccb->fcrb.ack_timer);
  }

  /* Set the control word */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset + L2CAP_PKT_OVERHEAD;

  UINT16_TO_STREAM(p, ctrl_word);

  /* Compute the FCS and add to the end of the buffer if not bypassed */
  /* length field in l2cap header has to include FCS length */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;
  UINT16_TO_STREAM(p, p_buf->len + L2CAP_FCS_LEN - L2CAP_PKT_OVERHEAD);

  /* Calculate the FCS */
  fcs = l2c_fcr_tx_get_fcs(p_buf);

  /* Point to the end of the buffer and put the FCS there */
  /*
   * NOTE: Here we assume the allocated buffer is large enough
   * to include extra L2CAP_FCS_LEN octets at the end.
   */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset + p_buf->len;

  UINT16_TO_STREAM(p, fcs);

  p_buf->len += L2CAP_FCS_LEN;

  if (is_retransmission) {
    log::verbose(
        "L2CAP eRTM ReTx I-frame  CID: 0x{:04x}  Len: {}  SAR: {}  TxSeq: {}  "
        "ReqSeq: {}  F: {}",
        p_ccb->local_cid, p_buf->len,
        SAR_types[(ctrl_word & L2CAP_FCR_SAR_BITS) >> L2CAP_FCR_SAR_BITS_SHIFT],
        (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
  } else {
    log::verbose(
        "L2CAP eRTM Tx I-frame CID: 0x{:04x}  Len: {}  SAR: {:<12s}  TxSeq: {} "
        " ReqSeq: {}  F: {}",
        p_ccb->local_cid, p_buf->len,
        SAR_types[(ctrl_word & L2CAP_FCR_SAR_BITS) >> L2CAP_FCR_SAR_BITS_SHIFT],
        (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
  }

  /* Start the retransmission timer if not already running */
  if (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE)
    l2c_fcr_start_timer(p_ccb);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_send_S_frame
 *
 * Description      This function formats and sends an S-frame for transmission.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_send_S_frame(tL2C_CCB* p_ccb, uint16_t function_code,
                          uint16_t pf_bit) {
  CHECK(p_ccb != NULL);
  uint8_t* p;
  uint16_t ctrl_word;
  uint16_t fcs;

  if ((!p_ccb->in_use) || (p_ccb->chnl_state != CST_OPEN)) return;

  if (pf_bit == L2CAP_FCR_P_BIT) {
    p_ccb->fcrb.wait_ack = true;

    l2c_fcr_stop_timer(p_ccb); /* Restart the monitor timer */
    l2c_fcr_start_timer(p_ccb);
  }

  /* Create the control word to use */
  ctrl_word = (function_code << L2CAP_FCR_SUP_SHIFT) | L2CAP_FCR_S_FRAME_BIT;
  ctrl_word |= (p_ccb->fcrb.next_seq_expected << L2CAP_FCR_REQ_SEQ_BITS_SHIFT);
  ctrl_word |= pf_bit;

  BT_HDR* p_buf = (BT_HDR*)osi_malloc(L2CAP_CMD_BUF_SIZE);
  p_buf->offset = HCI_DATA_PREAMBLE_SIZE;
  p_buf->len = L2CAP_PKT_OVERHEAD + L2CAP_FCR_OVERHEAD;

  /* Set the pointer to the beginning of the data */
  p = (uint8_t*)(p_buf + 1) + p_buf->offset;

  /* Put in the L2CAP header */
  UINT16_TO_STREAM(p, L2CAP_FCR_OVERHEAD + L2CAP_FCS_LEN);
  UINT16_TO_STREAM(p, p_ccb->remote_cid);
  UINT16_TO_STREAM(p, ctrl_word);

  /* Compute the FCS and add to the end of the buffer if not bypassed */
  fcs = l2c_fcr_tx_get_fcs(p_buf);

  UINT16_TO_STREAM(p, fcs);
  p_buf->len += L2CAP_FCS_LEN;

  /* Now, the HCI transport header */
  p_buf->layer_specific = L2CAP_NON_FLUSHABLE_PKT;
  l2cu_set_acl_hci_header(p_buf, p_ccb);

  if ((((ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT) == 1) ||
      (((ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT) == 3)) {
    log::warn(
        "L2CAP eRTM Tx S-frame  CID: 0x{:04x}  ctrlword: 0x{:04x}  Type: {}  "
        "ReqSeq: {}  P: {}  F: {}",
        p_ccb->local_cid, ctrl_word,
        SUP_types[(ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT],
        (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_P_BIT) >> L2CAP_FCR_P_BIT_SHIFT,
        (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
    log::warn("Buf Len: {}", p_buf->len);
  } else {
    log::verbose(
        "L2CAP eRTM Tx S-frame  CID: 0x{:04x}  ctrlword: 0x{:04x}  Type: {}  "
        "ReqSeq: {}  P: {}  F: {}",
        p_ccb->local_cid, ctrl_word,
        SUP_types[(ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT],
        (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_P_BIT) >> L2CAP_FCR_P_BIT_SHIFT,
        (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
    log::verbose("Buf Len: {}", p_buf->len);
  }

  l2c_link_check_send_pkts(p_ccb->p_lcb, 0, p_buf);

  p_ccb->fcrb.last_ack_sent = p_ccb->fcrb.next_seq_expected;

  alarm_cancel(p_ccb->fcrb.ack_timer);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_proc_pdu
 *
 * Description      This function is the entry point for processing of a
 *                  received PDU when in flow control and/or retransmission
 *                  modes.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_proc_pdu(tL2C_CCB* p_ccb, BT_HDR* p_buf) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);
  uint8_t* p;
  uint16_t fcs;
  uint16_t min_pdu_len;
  uint16_t ctrl_word;

  /* Check the length */
  min_pdu_len = (uint16_t)(L2CAP_FCS_LEN + L2CAP_FCR_OVERHEAD);

  if (p_buf->len < min_pdu_len) {
    log::warn("Rx L2CAP PDU: CID: 0x{:04x}  Len too short: {}",
              p_ccb->local_cid, p_buf->len);
    osi_free(p_buf);
    return;
  }

  /* Get the control word */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;
  STREAM_TO_UINT16(ctrl_word, p);

  if (ctrl_word & L2CAP_FCR_S_FRAME_BIT) {
    if ((((ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT) == 1) ||
        (((ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT) == 3)) {
      /* REJ or SREJ */
      log::warn(
          "L2CAP eRTM Rx S-frame: cid: 0x{:04x}  Len: {}  Type: {}  ReqSeq: {} "
          " P: {}  F: {}",
          p_ccb->local_cid, p_buf->len,
          SUP_types[(ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT],
          (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
          (ctrl_word & L2CAP_FCR_P_BIT) >> L2CAP_FCR_P_BIT_SHIFT,
          (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
    } else {
      log::verbose(
          "L2CAP eRTM Rx S-frame: cid: 0x{:04x}  Len: {}  Type: {}  ReqSeq: {} "
          " P: {}  F: {}",
          p_ccb->local_cid, p_buf->len,
          SUP_types[(ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT],
          (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
          (ctrl_word & L2CAP_FCR_P_BIT) >> L2CAP_FCR_P_BIT_SHIFT,
          (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
    }
  } else {
    log::verbose(
        "L2CAP eRTM Rx I-frame: cid: 0x{:04x}  Len: {}  SAR: {:<12s}  TxSeq: "
        "{}  ReqSeq: {}  F: {}",
        p_ccb->local_cid, p_buf->len,
        SAR_types[(ctrl_word & L2CAP_FCR_SAR_BITS) >> L2CAP_FCR_SAR_BITS_SHIFT],
        (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT,
        (ctrl_word & L2CAP_FCR_F_BIT) >> L2CAP_FCR_F_BIT_SHIFT);
  }

  log::verbose(
      "eRTM Rx Nxt_tx_seq {}, Lst_rx_ack {}, Nxt_seq_exp {}, Lst_ack_snt {}, "
      "wt_q.cnt {}, tries {}",
      p_ccb->fcrb.next_tx_seq, p_ccb->fcrb.last_rx_ack,
      p_ccb->fcrb.next_seq_expected, p_ccb->fcrb.last_ack_sent,
      fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q), p_ccb->fcrb.num_tries);

  /* Verify FCS if using */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset + p_buf->len - L2CAP_FCS_LEN;

  /* Extract and drop the FCS from the packet */
  STREAM_TO_UINT16(fcs, p);
  p_buf->len -= L2CAP_FCS_LEN;

  if (l2c_fcr_rx_get_fcs(p_buf) != fcs) {
    log::warn("Rx L2CAP PDU: CID: 0x{:04x}  BAD FCS", p_ccb->local_cid);
    osi_free(p_buf);
    return;
  }

  /* Get the control word */
  p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;

  STREAM_TO_UINT16(ctrl_word, p);

  p_buf->len -= L2CAP_FCR_OVERHEAD;
  p_buf->offset += L2CAP_FCR_OVERHEAD;

  /* If we had a poll bit outstanding, check if we got a final response */
  if (p_ccb->fcrb.wait_ack) {
    /* If final bit not set, ignore the frame unless it is a polled S-frame */
    if (!(ctrl_word & L2CAP_FCR_F_BIT)) {
      if ((ctrl_word & L2CAP_FCR_P_BIT) &&
          (ctrl_word & L2CAP_FCR_S_FRAME_BIT)) {
        if (p_ccb->fcrb.srej_sent)
          l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_SREJ, L2CAP_FCR_F_BIT);
        else
          l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, L2CAP_FCR_F_BIT);

        /* Got a poll while in wait_ack state, so re-start our timer with
         * 1-second */
        /* This is a small optimization... the monitor timer is 12 secs, but we
         * saw */
        /* that if the other side sends us a poll when we are waiting for a
         * final,  */
        /* then it speeds up recovery significantly if we poll it back soon
         * after its poll. */
        alarm_set_on_mloop(p_ccb->fcrb.mon_retrans_timer, BT_1SEC_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
      }
      osi_free(p_buf);
      return;
    }

    p_ccb->fcrb.wait_ack = false;

    /* P and F are mutually exclusive */
    if (ctrl_word & L2CAP_FCR_S_FRAME_BIT) ctrl_word &= ~L2CAP_FCR_P_BIT;

    if (fixed_queue_is_empty(p_ccb->fcrb.waiting_for_ack_q))
      p_ccb->fcrb.num_tries = 0;

    l2c_fcr_stop_timer(p_ccb);
  } else {
    /* Otherwise, ensure the final bit is ignored */
    ctrl_word &= ~L2CAP_FCR_F_BIT;
  }

  /* Process receive sequence number */
  if (!process_reqseq(p_ccb, ctrl_word)) {
    osi_free(p_buf);
    return;
  }

  /* Process based on whether it is an S-frame or an I-frame */
  if (ctrl_word & L2CAP_FCR_S_FRAME_BIT)
    process_s_frame(p_ccb, p_buf, ctrl_word);
  else
    process_i_frame(p_ccb, p_buf, ctrl_word, false);

  /* Return if the channel got disconnected by a bad packet or max
   * retransmissions */
  if ((!p_ccb->in_use) || (p_ccb->chnl_state != CST_OPEN)) return;

  /* If we have some buffers held while doing SREJ, and SREJ has cleared,
   * process them now */
  if ((!p_ccb->fcrb.srej_sent) &&
      (!fixed_queue_is_empty(p_ccb->fcrb.srej_rcv_hold_q))) {
    fixed_queue_t* temp_q = p_ccb->fcrb.srej_rcv_hold_q;
    p_ccb->fcrb.srej_rcv_hold_q = fixed_queue_new(SIZE_MAX);

    while ((p_buf = (BT_HDR*)fixed_queue_try_dequeue(temp_q)) != NULL) {
      if (p_ccb->in_use && (p_ccb->chnl_state == CST_OPEN)) {
        /* Get the control word */
        p = ((uint8_t*)(p_buf + 1)) + p_buf->offset - L2CAP_FCR_OVERHEAD;

        STREAM_TO_UINT16(ctrl_word, p);

        log::verbose(
            "l2c_fcr_proc_pdu() CID: 0x{:04x}  Process Buffer from SREJ_Hold_Q "
            "  TxSeq: {}  Expected_Seq: {}",
            p_ccb->local_cid,
            (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT,
            p_ccb->fcrb.next_seq_expected);

        /* Process the SREJ held I-frame, but do not send an RR for each
         * individual frame */
        process_i_frame(p_ccb, p_buf, ctrl_word, true);
      } else
        osi_free(p_buf);

      /* If more frames were lost during SREJ, send a REJ */
      if (p_ccb->fcrb.rej_after_srej) {
        p_ccb->fcrb.rej_after_srej = false;
        p_ccb->fcrb.rej_sent = true;

        l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_REJ, 0);
      }
    }
    fixed_queue_free(temp_q, NULL);

    /* Now, if needed, send one RR for the whole held queue */
    if ((!p_ccb->fcrb.rej_sent) && (!p_ccb->fcrb.srej_sent) &&
        (p_ccb->fcrb.next_seq_expected != p_ccb->fcrb.last_ack_sent))
      l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, 0);
    else {
      log::verbose(
          "l2c_fcr_proc_pdu() not sending RR CID: 0x{:04x}  local_busy:{} "
          "rej_sent:{} srej_sent:{} Expected_Seq:{} Last_Ack:{}",
          p_ccb->local_cid, 0, p_ccb->fcrb.rej_sent, p_ccb->fcrb.srej_sent,
          p_ccb->fcrb.next_seq_expected, p_ccb->fcrb.last_ack_sent);
    }
  }

  /* If a window has opened, check if we can send any more packets */
  if ((!fixed_queue_is_empty(p_ccb->fcrb.retrans_q) ||
       !fixed_queue_is_empty(p_ccb->xmit_hold_q)) &&
      (!p_ccb->fcrb.wait_ack) && (!l2c_fcr_is_flow_controlled(p_ccb))) {
    l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);
  }
}

/*******************************************************************************
 *
 * Function         l2c_lcc_proc_pdu
 *
 * Description      This function is the entry point for processing of a
 *                  received PDU when in LE Coc flow control modes.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_lcc_proc_pdu(tL2C_CCB* p_ccb, BT_HDR* p_buf) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);
  uint8_t* p = (uint8_t*)(p_buf + 1) + p_buf->offset;
  uint16_t sdu_length;
  BT_HDR* p_data = NULL;

  /* Buffer length should not exceed local mps */
  if (p_buf->len > p_ccb->local_conn_cfg.mps) {
    log::error("buffer length={} exceeds local mps={}. Drop and disconnect.",
               p_buf->len, p_ccb->local_conn_cfg.mps);

    /* Discard the buffer and disconnect*/
    osi_free(p_buf);
    l2cu_disconnect_chnl(p_ccb);
    return;
  }

  if (p_ccb->is_first_seg) {
    if (p_buf->len < sizeof(sdu_length)) {
      log::error("buffer length={} too small. Need at least 2.", p_buf->len);
      /* Discard the buffer */
      osi_free(p_buf);
      return;
    }
    STREAM_TO_UINT16(sdu_length, p);

    /* Check the SDU Length with local MTU size */
    if (sdu_length > p_ccb->local_conn_cfg.mtu) {
      log::error("sdu length={} exceeds local mtu={}. Drop and disconnect.",
                 sdu_length, p_ccb->local_conn_cfg.mtu);
      /* Discard the buffer and disconnect*/
      osi_free(p_buf);
      l2cu_disconnect_chnl(p_ccb);
      return;
    }

    p_buf->len -= sizeof(sdu_length);
    p_buf->offset += sizeof(sdu_length);

    if (sdu_length < p_buf->len) {
      log::error("Invalid sdu_length: {}", sdu_length);
      /* Discard the buffer */
      osi_free(p_buf);
      return;
    }

    p_data = (BT_HDR*)osi_malloc(BT_HDR_SIZE + sdu_length);
    if (p_data == NULL) {
      osi_free(p_buf);
      return;
    }

    p_ccb->ble_sdu = p_data;
    p_data->len = 0;
    p_ccb->ble_sdu_length = sdu_length;
    log::verbose("SDU Length = {}", sdu_length);
    p_data->offset = 0;

  } else {
    p_data = p_ccb->ble_sdu;
    if (p_data == NULL) {
      osi_free(p_buf);
      return;
    }
    if (p_buf->len > (p_ccb->ble_sdu_length - p_data->len)) {
      log::error("buffer length={} too big. max={}. Dropped", p_data->len,
                 (p_ccb->ble_sdu_length - p_data->len));
      osi_free(p_buf);

      /* Throw away all pending fragments and disconnects */
      p_ccb->is_first_seg = true;
      osi_free(p_ccb->ble_sdu);
      p_ccb->ble_sdu = NULL;
      p_ccb->ble_sdu_length = 0;
      l2cu_disconnect_chnl(p_ccb);
      return;
    }
  }

  memcpy((uint8_t*)(p_data + 1) + p_data->offset + p_data->len,
         (uint8_t*)(p_buf + 1) + p_buf->offset, p_buf->len);
  p_data->len += p_buf->len;
  p = (uint8_t*)(p_data + 1) + p_data->offset;
  if (p_data->len == p_ccb->ble_sdu_length) {
    l2c_csm_execute(p_ccb, L2CEVT_L2CAP_DATA, p_data);
    p_ccb->is_first_seg = true;
    p_ccb->ble_sdu = NULL;
    p_ccb->ble_sdu_length = 0;
  } else if (p_data->len < p_ccb->ble_sdu_length) {
    p_ccb->is_first_seg = false;
  }

  osi_free(p_buf);
  return;
}

/*******************************************************************************
 *
 * Function         l2c_fcr_proc_tout
 *
 * Description      Handle a timeout. We should be in error recovery state.
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_proc_tout(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  log::verbose(
      "l2c_fcr_proc_tout:  CID: 0x{:04x}  num_tries: {} (max: {})  wait_ack: "
      "{}  ack_q_count: {}",
      p_ccb->local_cid, p_ccb->fcrb.num_tries, p_ccb->peer_cfg.fcr.max_transmit,
      p_ccb->fcrb.wait_ack, fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q));

  if ((p_ccb->peer_cfg.fcr.max_transmit != 0) &&
      (++p_ccb->fcrb.num_tries > p_ccb->peer_cfg.fcr.max_transmit)) {
    l2cu_disconnect_chnl(p_ccb);
  } else {
    if (!p_ccb->fcrb.srej_sent && !p_ccb->fcrb.rej_sent) {
      l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, L2CAP_FCR_P_BIT);
    }
  }
}

/*******************************************************************************
 *
 * Function         l2c_fcr_proc_ack_tout
 *
 * Description      Send RR/RNR if we have not acked I frame
 *
 * Returns          -
 *
 ******************************************************************************/
void l2c_fcr_proc_ack_tout(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);
  log::verbose(
      "l2c_fcr_proc_ack_tout:  CID: 0x{:04x} State: {}  Wack:{}  Rq:{}  "
      "Acked:{}",
      p_ccb->local_cid, p_ccb->chnl_state, p_ccb->fcrb.wait_ack,
      p_ccb->fcrb.next_seq_expected, p_ccb->fcrb.last_ack_sent);

  if ((p_ccb->chnl_state == CST_OPEN) && (!p_ccb->fcrb.wait_ack) &&
      (p_ccb->fcrb.last_ack_sent != p_ccb->fcrb.next_seq_expected)) {
    l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, 0);
  }
}

/*******************************************************************************
 *
 * Function         process_reqseq
 *
 * Description      Handle receive sequence number
 *
 * Returns          -
 *
 ******************************************************************************/
static bool process_reqseq(tL2C_CCB* p_ccb, uint16_t ctrl_word) {
  CHECK(p_ccb != NULL);
  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;
  uint8_t req_seq, num_bufs_acked, xx;
  uint16_t ls;
  uint16_t full_sdus_xmitted;

  /* Receive sequence number does not ack anything for SREJ with P-bit set to
   * zero */
  if ((ctrl_word & L2CAP_FCR_S_FRAME_BIT) &&
      ((ctrl_word & L2CAP_FCR_SUP_BITS) ==
       (L2CAP_FCR_SUP_SREJ << L2CAP_FCR_SUP_SHIFT)) &&
      ((ctrl_word & L2CAP_FCR_P_BIT) == 0)) {
    /* If anything still waiting for ack, restart the timer if it was stopped */
    if (!fixed_queue_is_empty(p_fcrb->waiting_for_ack_q))
      l2c_fcr_start_timer(p_ccb);

    return (true);
  }

  /* Extract the receive sequence number from the control word */
  req_seq =
      (ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >> L2CAP_FCR_REQ_SEQ_BITS_SHIFT;

  num_bufs_acked = (req_seq - p_fcrb->last_rx_ack) & L2CAP_FCR_SEQ_MODULO;

  /* Verify the request sequence is in range before proceeding */
  if (num_bufs_acked > fixed_queue_length(p_fcrb->waiting_for_ack_q)) {
    /* The channel is closed if ReqSeq is not in range */
    log::warn(
        "L2CAP eRTM Frame BAD Req_Seq - ctrl_word: 0x{:04x}  req_seq 0x{:02x}  "
        "last_rx_ack: 0x{:02x}  QCount: {}",
        ctrl_word, req_seq, p_fcrb->last_rx_ack,
        fixed_queue_length(p_fcrb->waiting_for_ack_q));

    l2cu_disconnect_chnl(p_ccb);
    return (false);
  }

  p_fcrb->last_rx_ack = req_seq;

  /* Now we can release all acknowledged frames, and restart the retransmission
   * timer if needed */
  if (num_bufs_acked != 0) {
    p_fcrb->num_tries = 0;
    full_sdus_xmitted = 0;

    for (xx = 0; xx < num_bufs_acked; xx++) {
      BT_HDR* p_tmp =
          (BT_HDR*)fixed_queue_try_dequeue(p_fcrb->waiting_for_ack_q);
      ls = p_tmp->layer_specific & L2CAP_FCR_SAR_BITS;

      if ((ls == L2CAP_FCR_UNSEG_SDU) || (ls == L2CAP_FCR_END_SDU))
        full_sdus_xmitted++;

      osi_free(p_tmp);
    }

    /* If we are still in a wait_ack state, do not mess with the timer */
    if (!p_ccb->fcrb.wait_ack) l2c_fcr_stop_timer(p_ccb);

    /* Check if we need to call the "packet_sent" callback */
    if ((p_ccb->p_rcb) && (p_ccb->p_rcb->api.pL2CA_TxComplete_Cb) &&
        (full_sdus_xmitted)) {
      /* Special case for eRTM, if all packets sent, send 0xFFFF */
      if (fixed_queue_is_empty(p_fcrb->waiting_for_ack_q) &&
          fixed_queue_is_empty(p_ccb->xmit_hold_q)) {
        full_sdus_xmitted = 0xFFFF;
      }

      (*p_ccb->p_rcb->api.pL2CA_TxComplete_Cb)(p_ccb->local_cid,
                                               full_sdus_xmitted);
    }
  }

  /* If anything still waiting for ack, restart the timer if it was stopped */
  if (!fixed_queue_is_empty(p_fcrb->waiting_for_ack_q))
    l2c_fcr_start_timer(p_ccb);
  return (true);
}

/*******************************************************************************
 *
 * Function         process_s_frame
 *
 * Description      Process an S frame
 *
 * Returns          -
 *
 ******************************************************************************/
static void process_s_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf,
                            uint16_t ctrl_word) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);

  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;
  uint16_t s_frame_type =
      (ctrl_word & L2CAP_FCR_SUP_BITS) >> L2CAP_FCR_SUP_SHIFT;
  bool remote_was_busy;
  bool all_ok = true;

  if (p_buf->len != 0) {
    log::warn("Incorrect S-frame Length ({})", p_buf->len);
  }

  log::verbose("process_s_frame ctrl_word 0x{:04x} fcrb_remote_busy:{}",
               ctrl_word, p_fcrb->remote_busy);

  if (ctrl_word & L2CAP_FCR_P_BIT) {
    p_fcrb->rej_sent = false;  /* After checkpoint, we can send anoher REJ */
    p_fcrb->send_f_rsp = true; /* Set a flag in case an I-frame is pending */
  }

  switch (s_frame_type) {
    case L2CAP_FCR_SUP_RR:
      remote_was_busy = p_fcrb->remote_busy;
      p_fcrb->remote_busy = false;

      if ((ctrl_word & L2CAP_FCR_F_BIT) || (remote_was_busy))
        all_ok = retransmit_i_frames(p_ccb, L2C_FCR_RETX_ALL_PKTS);
      break;

    case L2CAP_FCR_SUP_REJ:
      p_fcrb->remote_busy = false;
      all_ok = retransmit_i_frames(p_ccb, L2C_FCR_RETX_ALL_PKTS);
      break;

    case L2CAP_FCR_SUP_RNR:
      p_fcrb->remote_busy = true;
      l2c_fcr_stop_timer(p_ccb);
      break;

    case L2CAP_FCR_SUP_SREJ:
      p_fcrb->remote_busy = false;
      all_ok = retransmit_i_frames(
          p_ccb, (uint8_t)((ctrl_word & L2CAP_FCR_REQ_SEQ_BITS) >>
                           L2CAP_FCR_REQ_SEQ_BITS_SHIFT));
      break;
  }

  if (all_ok) {
    /* If polled, we need to respond with F-bit. Note, we may have sent a
     * I-frame with the F-bit */
    if (p_fcrb->send_f_rsp) {
      if (p_fcrb->srej_sent)
        l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_SREJ, L2CAP_FCR_F_BIT);
      else
        l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, L2CAP_FCR_F_BIT);

      p_fcrb->send_f_rsp = false;
    }
  } else {
    log::verbose("process_s_frame hit_max_retries");
  }

  osi_free(p_buf);
}

/*******************************************************************************
 *
 * Function         process_i_frame
 *
 * Description      Process an I frame
 *
 * Returns          -
 *
 ******************************************************************************/
static void process_i_frame(tL2C_CCB* p_ccb, BT_HDR* p_buf, uint16_t ctrl_word,
                            bool delay_ack) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);

  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;
  uint8_t tx_seq, num_lost, num_to_ack, next_srej;

  /* If we were doing checkpoint recovery, first retransmit all unacked I-frames
   */
  if (ctrl_word & L2CAP_FCR_F_BIT) {
    if (!retransmit_i_frames(p_ccb, L2C_FCR_RETX_ALL_PKTS)) {
      osi_free(p_buf);
      return;
    }
  }

  /* Extract the sequence number */
  tx_seq = (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT;

  /* Check if tx-sequence is the expected one */
  if (tx_seq != p_fcrb->next_seq_expected) {
    num_lost = (tx_seq - p_fcrb->next_seq_expected) & L2CAP_FCR_SEQ_MODULO;

    /* Is the frame a duplicate ? If so, just drop it */
    if (num_lost >= p_ccb->our_cfg.fcr.tx_win_sz) {
      /* Duplicate - simply drop it */
      log::warn(
          "process_i_frame() Dropping Duplicate Frame tx_seq:{}  ExpectedTxSeq "
          "{}",
          tx_seq, p_fcrb->next_seq_expected);
      osi_free(p_buf);
    } else {
      log::warn(
          "process_i_frame() CID: 0x{:04x}  Lost: {}  tx_seq:{}  ExpTxSeq {}  "
          "Rej: {}  SRej: {}",
          p_ccb->local_cid, num_lost, tx_seq, p_fcrb->next_seq_expected,
          p_fcrb->rej_sent, p_fcrb->srej_sent);

      if (p_fcrb->srej_sent) {
        /* If SREJ sent, save the frame for later processing as long as it is in
         * sequence */
        next_srej =
            (((BT_HDR*)fixed_queue_try_peek_last(p_fcrb->srej_rcv_hold_q))
                 ->layer_specific +
             1) &
            L2CAP_FCR_SEQ_MODULO;

        if ((tx_seq == next_srej) &&
            (fixed_queue_length(p_fcrb->srej_rcv_hold_q) <
             p_ccb->our_cfg.fcr.tx_win_sz)) {
          log::verbose(
              "process_i_frame() Lost: {}  tx_seq:{}  ExpTxSeq {}  Rej: {}  "
              "SRej1",
              num_lost, tx_seq, p_fcrb->next_seq_expected, p_fcrb->rej_sent);

          p_buf->layer_specific = tx_seq;
          fixed_queue_enqueue(p_fcrb->srej_rcv_hold_q, p_buf);
        } else {
          log::warn(
              "process_i_frame() CID: 0x{:04x}  frame dropped in Srej Sent "
              "next_srej:{}  hold_q.count:{}  win_sz:{}",
              p_ccb->local_cid, next_srej,
              fixed_queue_length(p_fcrb->srej_rcv_hold_q),
              p_ccb->our_cfg.fcr.tx_win_sz);

          p_fcrb->rej_after_srej = true;
          osi_free(p_buf);
        }
      } else if (p_fcrb->rej_sent) {
        log::warn(
            "process_i_frame() CID: 0x{:04x}  Lost: {}  tx_seq:{}  ExpTxSeq {} "
            " Rej: 1  SRej: {}",
            p_ccb->local_cid, num_lost, tx_seq, p_fcrb->next_seq_expected,
            p_fcrb->srej_sent);

        /* If REJ sent, just drop the frame */
        osi_free(p_buf);
      } else {
        log::verbose(
            "process_i_frame() CID: 0x{:04x}  tx_seq:{}  ExpTxSeq {}  Rej: {}",
            p_ccb->local_cid, tx_seq, p_fcrb->next_seq_expected,
            p_fcrb->rej_sent);

        /* If only one lost, we will send SREJ, otherwise we will send REJ */
        if (num_lost > 1) {
          osi_free(p_buf);
          p_fcrb->rej_sent = true;
          l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_REJ, 0);
        } else {
          if (!fixed_queue_is_empty(p_fcrb->srej_rcv_hold_q)) {
            log::error(
                "process_i_frame() CID: 0x{:04x}  sending SREJ tx_seq:{} "
                "hold_q.count:{}",
                p_ccb->local_cid, tx_seq,
                fixed_queue_length(p_fcrb->srej_rcv_hold_q));
          }
          p_buf->layer_specific = tx_seq;
          fixed_queue_enqueue(p_fcrb->srej_rcv_hold_q, p_buf);
          p_fcrb->srej_sent = true;
          l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_SREJ, 0);
        }
        alarm_cancel(p_ccb->fcrb.ack_timer);
      }
    }
    return;
  }

  /* Seq number is the next expected. Clear possible reject exception in case it
   * occured */
  p_fcrb->rej_sent = p_fcrb->srej_sent = false;

  /* Adjust the next_seq, so that if the upper layer sends more data in the
     callback
     context, the received frame is acked by an I-frame. */
  p_fcrb->next_seq_expected = (tx_seq + 1) & L2CAP_FCR_SEQ_MODULO;

  /* If any SAR problem in eRTM mode, spec says disconnect. */
  if (!do_sar_reassembly(p_ccb, p_buf, ctrl_word)) {
    log::warn("process_i_frame() CID: 0x{:04x}  reassembly failed",
              p_ccb->local_cid);
    l2cu_disconnect_chnl(p_ccb);
    return;
  }

  /* RR optimization - if peer can still send us more, then start an ACK timer
   */
  num_to_ack = (p_fcrb->next_seq_expected - p_fcrb->last_ack_sent) &
               L2CAP_FCR_SEQ_MODULO;

  if (num_to_ack < p_ccb->fcrb.max_held_acks) delay_ack = true;

  /* We should neve never ack frame if we are not in OPEN state */
  if ((num_to_ack != 0) && p_ccb->in_use && (p_ccb->chnl_state == CST_OPEN)) {
    /* If no frames are awaiting transmission or are held, send an RR or RNR
     * S-frame for ack */
    if (delay_ack) {
      /* If it is the first I frame we did not ack, start ack timer */
      if (!alarm_is_scheduled(p_ccb->fcrb.ack_timer)) {
        alarm_set_on_mloop(p_ccb->fcrb.ack_timer, L2CAP_FCR_ACK_TIMEOUT_MS,
                           l2c_fcrb_ack_timer_timeout, p_ccb);
      }
    } else if ((fixed_queue_is_empty(p_ccb->xmit_hold_q) ||
                l2c_fcr_is_flow_controlled(p_ccb)) &&
               fixed_queue_is_empty(p_ccb->fcrb.srej_rcv_hold_q)) {
      l2c_fcr_send_S_frame(p_ccb, L2CAP_FCR_SUP_RR, 0);
    }
  }
}

/*******************************************************************************
 *
 * Function         do_sar_reassembly
 *
 * Description      Process SAR bits and re-assemble frame
 *
 * Returns          true if all OK, else false
 *
 ******************************************************************************/
static bool do_sar_reassembly(tL2C_CCB* p_ccb, BT_HDR* p_buf,
                              uint16_t ctrl_word) {
  CHECK(p_ccb != NULL);
  CHECK(p_buf != NULL);

  tL2C_FCRB* p_fcrb = &p_ccb->fcrb;
  uint16_t sar_type = ctrl_word & L2CAP_FCR_SEG_BITS;
  bool packet_ok = true;
  uint8_t* p;

  /* Check if the SAR state is correct */
  if ((sar_type == L2CAP_FCR_UNSEG_SDU) || (sar_type == L2CAP_FCR_START_SDU)) {
    if (p_fcrb->p_rx_sdu != NULL) {
      log::warn(
          "SAR - got unexpected unsegmented or start SDU  Expected len: {}  "
          "Got so far: {}",
          p_fcrb->rx_sdu_len, p_fcrb->p_rx_sdu->len);

      packet_ok = false;
    }
    /* Check the length of the packet */
    if ((sar_type == L2CAP_FCR_START_SDU) &&
        (p_buf->len < L2CAP_SDU_LEN_OVERHEAD)) {
      log::warn("SAR start packet too short: {}", p_buf->len);
      packet_ok = false;
    }
  } else {
    if (p_fcrb->p_rx_sdu == NULL) {
      log::warn("SAR - got unexpected cont or end SDU");
      packet_ok = false;
    }
  }

  if ((packet_ok) && (sar_type != L2CAP_FCR_UNSEG_SDU)) {
    p = ((uint8_t*)(p_buf + 1)) + p_buf->offset;

    /* For start SDU packet, extract the SDU length */
    if (sar_type == L2CAP_FCR_START_SDU) {
      /* Get the SDU length */
      STREAM_TO_UINT16(p_fcrb->rx_sdu_len, p);
      p_buf->offset += 2;
      p_buf->len -= 2;

      if (p_fcrb->rx_sdu_len > p_ccb->max_rx_mtu) {
        log::warn("SAR - SDU len: {}  larger than MTU: {}", p_fcrb->rx_sdu_len,
                  p_ccb->max_rx_mtu);
        packet_ok = false;
      } else {
        p_fcrb->p_rx_sdu = (BT_HDR*)osi_malloc(
            BT_HDR_SIZE + OBX_BUF_MIN_OFFSET + p_fcrb->rx_sdu_len);
        p_fcrb->p_rx_sdu->offset = OBX_BUF_MIN_OFFSET;
        p_fcrb->p_rx_sdu->len = 0;
      }
    }

    if (packet_ok) {
      if ((p_fcrb->p_rx_sdu->len + p_buf->len) > p_fcrb->rx_sdu_len) {
        log::error("SAR - SDU len exceeded  Type: {}   Lengths: {} {} {}",
                   sar_type, p_fcrb->p_rx_sdu->len, p_buf->len,
                   p_fcrb->rx_sdu_len);
        packet_ok = false;
      } else if ((sar_type == L2CAP_FCR_END_SDU) &&
                 ((p_fcrb->p_rx_sdu->len + p_buf->len) != p_fcrb->rx_sdu_len)) {
        log::warn("SAR - SDU end rcvd but SDU incomplete: {} {} {}",
                  p_fcrb->p_rx_sdu->len, p_buf->len, p_fcrb->rx_sdu_len);
        packet_ok = false;
      } else {
        memcpy(((uint8_t*)(p_fcrb->p_rx_sdu + 1)) + p_fcrb->p_rx_sdu->offset +
                   p_fcrb->p_rx_sdu->len,
               p, p_buf->len);

        p_fcrb->p_rx_sdu->len += p_buf->len;

        osi_free(p_buf);
        p_buf = NULL;

        if (sar_type == L2CAP_FCR_END_SDU) {
          p_buf = p_fcrb->p_rx_sdu;
          p_fcrb->p_rx_sdu = NULL;
        }
      }
    }
  }

  if (!packet_ok) {
    osi_free(p_buf);
  } else if (p_buf != NULL) {
    if (p_ccb->local_cid < L2CAP_BASE_APPL_CID &&
        (p_ccb->local_cid >= L2CAP_FIRST_FIXED_CHNL &&
         p_ccb->local_cid <= L2CAP_LAST_FIXED_CHNL)) {
      if (l2cb.fixed_reg[p_ccb->local_cid - L2CAP_FIRST_FIXED_CHNL]
              .pL2CA_FixedData_Cb)
        (*l2cb.fixed_reg[p_ccb->local_cid - L2CAP_FIRST_FIXED_CHNL]
              .pL2CA_FixedData_Cb)(p_ccb->local_cid,
                                   p_ccb->p_lcb->remote_bd_addr, p_buf);
    } else
      l2c_csm_execute(p_ccb, L2CEVT_L2CAP_DATA, p_buf);
  }

  return (packet_ok);
}

/*******************************************************************************
 *
 * Function         retransmit_i_frames
 *
 * Description      This function retransmits i-frames awaiting acks.
 *
 * Returns          bool    - true if retransmitted
 *
 ******************************************************************************/
static bool retransmit_i_frames(tL2C_CCB* p_ccb, uint8_t tx_seq) {
  CHECK(p_ccb != NULL);

  BT_HDR* p_buf = NULL;
  uint8_t* p;
  uint8_t buf_seq;
  uint16_t ctrl_word;

  if ((!fixed_queue_is_empty(p_ccb->fcrb.waiting_for_ack_q)) &&
      (p_ccb->peer_cfg.fcr.max_transmit != 0) &&
      (p_ccb->fcrb.num_tries >= p_ccb->peer_cfg.fcr.max_transmit)) {
    log::verbose(
        "Max Tries Exceeded:  (last_acq: {}  CID: 0x{:04x}  num_tries: {} "
        "(max: {}) ack_q_count: {}",
        p_ccb->fcrb.last_rx_ack, p_ccb->local_cid, p_ccb->fcrb.num_tries,
        p_ccb->peer_cfg.fcr.max_transmit,
        fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q));

    l2cu_disconnect_chnl(p_ccb);
    return (false);
  }

  /* tx_seq indicates whether to retransmit a specific sequence or all (if ==
   * L2C_FCR_RETX_ALL_PKTS) */
  list_t* list_ack = NULL;
  const list_node_t* node_ack = NULL;
  if (!fixed_queue_is_empty(p_ccb->fcrb.waiting_for_ack_q)) {
    list_ack = fixed_queue_get_list(p_ccb->fcrb.waiting_for_ack_q);
    node_ack = list_begin(list_ack);
  }
  if (tx_seq != L2C_FCR_RETX_ALL_PKTS) {
    /* If sending only one, the sequence number tells us which one. Look for it.
    */
    if (list_ack != NULL) {
      for (; node_ack != list_end(list_ack); node_ack = list_next(node_ack)) {
        p_buf = (BT_HDR*)list_node(node_ack);
        /* Get the old control word */
        p = ((uint8_t*)(p_buf + 1)) + p_buf->offset + L2CAP_PKT_OVERHEAD;

        STREAM_TO_UINT16(ctrl_word, p);

        buf_seq =
            (ctrl_word & L2CAP_FCR_TX_SEQ_BITS) >> L2CAP_FCR_TX_SEQ_BITS_SHIFT;

        log::verbose("retransmit_i_frames()   cur seq: {}  looking for: {}",
                     buf_seq, tx_seq);

        if (tx_seq == buf_seq) break;
      }
    }

    if (!p_buf) {
      log::error("retransmit_i_frames() UNKNOWN seq: {}  q_count: {}", tx_seq,
                 fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q));
      return (true);
    }
  } else {
    // Iterate though list and flush the amount requested from
    // the transmit data queue that satisfy the layer and event conditions.
    for (list_node_t* node_tmp = list_begin(p_ccb->p_lcb->link_xmit_data_q);
         node_tmp != list_end(p_ccb->p_lcb->link_xmit_data_q);) {
      BT_HDR* p_tmp = (BT_HDR*)list_node(node_tmp);
      node_tmp = list_next(node_tmp);

      /* Do not flush other CIDs or partial segments */
      if ((p_tmp->layer_specific == 0) && (p_tmp->event == p_ccb->local_cid)) {
        list_remove(p_ccb->p_lcb->link_xmit_data_q, p_tmp);
        osi_free(p_tmp);
      }
    }

    /* Also flush our retransmission queue */
    while (!fixed_queue_is_empty(p_ccb->fcrb.retrans_q))
      osi_free(fixed_queue_try_dequeue(p_ccb->fcrb.retrans_q));

    if (list_ack != NULL) node_ack = list_begin(list_ack);
  }

  if (list_ack != NULL) {
    while (node_ack != list_end(list_ack)) {
      p_buf = (BT_HDR*)list_node(node_ack);
      node_ack = list_next(node_ack);

      BT_HDR* p_buf2 = l2c_fcr_clone_buf(p_buf, p_buf->offset, p_buf->len);
      if (p_buf2) {
        p_buf2->layer_specific = p_buf->layer_specific;

        fixed_queue_enqueue(p_ccb->fcrb.retrans_q, p_buf2);
      }

      if ((tx_seq != L2C_FCR_RETX_ALL_PKTS) || (p_buf2 == NULL)) break;
    }
  }

  l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);

  if (fixed_queue_length(p_ccb->fcrb.waiting_for_ack_q)) {
    p_ccb->fcrb.num_tries++;
    l2c_fcr_start_timer(p_ccb);
  }

  return (true);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_get_next_xmit_sdu_seg
 *
 * Description      Get the next SDU segment to transmit.
 *
 * Returns          pointer to buffer with segment or NULL
 *
 ******************************************************************************/
BT_HDR* l2c_fcr_get_next_xmit_sdu_seg(tL2C_CCB* p_ccb,
                                      uint16_t max_packet_length) {
  CHECK(p_ccb != NULL);

  bool first_seg = false, /* The segment is the first part of data  */
      mid_seg = false,    /* The segment is the middle part of data */
      last_seg = false;   /* The segment is the last part of data   */
  uint16_t sdu_len = 0;
  BT_HDR *p_buf, *p_xmit;
  uint8_t* p;
  uint16_t max_pdu = p_ccb->tx_mps /* Needed? - L2CAP_MAX_HEADER_FCS*/;

  /* If there is anything in the retransmit queue, that goes first
  */
  p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_ccb->fcrb.retrans_q);
  if (p_buf != NULL) {
    /* Update Rx Seq and FCS if we acked some packets while this one was queued
     */
    prepare_I_frame(p_ccb, p_buf, true);

    p_buf->event = p_ccb->local_cid;

    return (p_buf);
  }

  /* For BD/EDR controller, max_packet_length is set to 0             */
  /* For AMP controller, max_packet_length is set by available blocks */
  if ((max_packet_length > L2CAP_MAX_HEADER_FCS) &&
      (max_pdu + L2CAP_MAX_HEADER_FCS > max_packet_length)) {
    max_pdu = max_packet_length - L2CAP_MAX_HEADER_FCS;
  }

  p_buf = (BT_HDR*)fixed_queue_try_peek_first(p_ccb->xmit_hold_q);

  /* If there is more data than the MPS, it requires segmentation */
  if (p_buf->len > max_pdu) {
    /* We are using the "event" field to tell is if we already started
     * segmentation */
    if (p_buf->event == 0) {
      first_seg = true;
      sdu_len = p_buf->len;
    } else
      mid_seg = true;

    /* Get a new buffer and copy the data that can be sent in a PDU */
    p_xmit = l2c_fcr_clone_buf(p_buf, L2CAP_MIN_OFFSET + L2CAP_SDU_LEN_OFFSET,
                               max_pdu);

    if (p_xmit != NULL) {
      p_buf->event = p_ccb->local_cid;
      p_xmit->event = p_ccb->local_cid;

      p_buf->len -= max_pdu;
      p_buf->offset += max_pdu;

      /* copy PBF setting */
      p_xmit->layer_specific = p_buf->layer_specific;
    } else /* Should never happen if the application has configured buffers
              correctly */
    {
      log::error("L2CAP - cannot get buffer for segmentation, max_pdu: {}",
                 max_pdu);
      return (NULL);
    }
  } else /* Use the original buffer if no segmentation, or the last segment */
  {
    p_xmit = (BT_HDR*)fixed_queue_try_dequeue(p_ccb->xmit_hold_q);

    if (p_xmit->event != 0) last_seg = true;

    p_xmit->event = p_ccb->local_cid;
  }

  /* Step back to add the L2CAP headers */
  p_xmit->offset -= (L2CAP_PKT_OVERHEAD + L2CAP_FCR_OVERHEAD);
  p_xmit->len += L2CAP_PKT_OVERHEAD + L2CAP_FCR_OVERHEAD;

  if (first_seg) {
    p_xmit->offset -= L2CAP_SDU_LEN_OVERHEAD;
    p_xmit->len += L2CAP_SDU_LEN_OVERHEAD;
  }

  /* Set the pointer to the beginning of the data */
  p = (uint8_t*)(p_xmit + 1) + p_xmit->offset;

  /* Now the L2CAP header */

  /* Note: if FCS has to be included then the length is recalculated later */
  UINT16_TO_STREAM(p, p_xmit->len - L2CAP_PKT_OVERHEAD);

  UINT16_TO_STREAM(p, p_ccb->remote_cid);

  if (first_seg) {
    /* Skip control word and add SDU length */
    p += 2;
    UINT16_TO_STREAM(p, sdu_len);

    /* We will store the SAR type in layer-specific */
    /* layer_specific is shared with flushable flag(bits 0-1), don't clear it */
    p_xmit->layer_specific |= L2CAP_FCR_START_SDU;

    first_seg = false;
  } else if (mid_seg)
    p_xmit->layer_specific |= L2CAP_FCR_CONT_SDU;
  else if (last_seg)
    p_xmit->layer_specific |= L2CAP_FCR_END_SDU;
  else
    p_xmit->layer_specific |= L2CAP_FCR_UNSEG_SDU;

  prepare_I_frame(p_ccb, p_xmit, false);

  if (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) {
    BT_HDR* p_wack =
        l2c_fcr_clone_buf(p_xmit, HCI_DATA_PREAMBLE_SIZE, p_xmit->len);

    if (!p_wack) {
      log::error(
          "L2CAP - no buffer for xmit cloning, CID: 0x{:04x}  Length: {}",
          p_ccb->local_cid, p_xmit->len);

      /* We will not save the FCS in case we reconfigure and change options */
      p_xmit->len -= L2CAP_FCS_LEN;

      /* Pretend we sent it and it got lost */
      fixed_queue_enqueue(p_ccb->fcrb.waiting_for_ack_q, p_xmit);
      return (NULL);
    } else {
      /* We will not save the FCS in case we reconfigure and change options */
      p_wack->len -= L2CAP_FCS_LEN;

      p_wack->layer_specific = p_xmit->layer_specific;
      fixed_queue_enqueue(p_ccb->fcrb.waiting_for_ack_q, p_wack);
    }

  }

  return (p_xmit);
}

/** Get the next PDU to transmit for LE connection oriented channel. Returns
 * pointer to buffer with PDU. |last_piece_of_sdu| will be set to true, if
 * returned PDU is last piece from this SDU.*/
BT_HDR* l2c_lcc_get_next_xmit_sdu_seg(tL2C_CCB* p_ccb,
                                      bool* last_piece_of_sdu) {
  uint16_t max_pdu = p_ccb->peer_conn_cfg.mps - 4 /* Length and CID */;

  BT_HDR* p_buf = (BT_HDR*)fixed_queue_try_peek_first(p_ccb->xmit_hold_q);
  bool first_pdu = (p_buf->event == 0) ? true : false;

  uint16_t no_of_bytes_to_send = std::min(
      p_buf->len,
      (uint16_t)(first_pdu ? (max_pdu - L2CAP_LCC_SDU_LENGTH) : max_pdu));
  bool last_pdu = (no_of_bytes_to_send == p_buf->len);

  /* Get a new buffer and copy the data that can be sent in a PDU */
  BT_HDR* p_xmit =
      l2c_fcr_clone_buf(p_buf, first_pdu ? L2CAP_LCC_OFFSET : L2CAP_MIN_OFFSET,
                        no_of_bytes_to_send);

  p_buf->event = p_ccb->local_cid;
  p_xmit->event = p_ccb->local_cid;

  if (first_pdu) {
    p_xmit->offset -= L2CAP_LCC_SDU_LENGTH; /* for writing the SDU length. */
    uint8_t* p = (uint8_t*)(p_xmit + 1) + p_xmit->offset;
    UINT16_TO_STREAM(p, p_buf->len);
    p_xmit->len += L2CAP_LCC_SDU_LENGTH;
  }

  p_buf->len -= no_of_bytes_to_send;
  p_buf->offset += no_of_bytes_to_send;

  /* copy PBF setting */
  p_xmit->layer_specific = p_buf->layer_specific;

  if (last_piece_of_sdu) *last_piece_of_sdu = last_pdu;

  if (last_pdu) {
    p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_ccb->xmit_hold_q);
    osi_free(p_buf);
  }

  /* Step back to add the L2CAP headers */
  p_xmit->offset -= L2CAP_PKT_OVERHEAD;
  p_xmit->len += L2CAP_PKT_OVERHEAD;

  /* Set the pointer to the beginning of the data */
  uint8_t* p = (uint8_t*)(p_xmit + 1) + p_xmit->offset;

  /* Note: if FCS has to be included then the length is recalculated later */
  UINT16_TO_STREAM(p, p_xmit->len - L2CAP_PKT_OVERHEAD);
  UINT16_TO_STREAM(p, p_ccb->remote_cid);
  return (p_xmit);
}

/*******************************************************************************
 * Configuration negotiation functions
 *
 * The following functions are used in negotiating channel modes during
 * configuration
 ******************************************************************************/

/*******************************************************************************
 *
 * Function         l2c_fcr_chk_chan_modes
 *
 * Description      Validates and adjusts if necessary, the FCR options
 *                  based on remote EXT features.
 *
 *                  Note: This assumes peer EXT Features have been received.
 *                      Basic mode is used if FCR Options have not been received
 *
 * Returns          uint8_t - nonzero if can continue, '0' if no compatible
 *                            channels
 *
 ******************************************************************************/
uint8_t l2c_fcr_chk_chan_modes(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);

  /* Remove nonbasic options that the peer does not support */
  if (!(p_ccb->p_lcb->peer_ext_fea & L2CAP_EXTFEA_ENH_RETRANS) &&
      p_ccb->p_rcb->ertm_info.preferred_mode == L2CAP_FCR_ERTM_MODE) {
    log::warn("L2CAP - Peer does not support our desired channel types");
    p_ccb->p_rcb->ertm_info.preferred_mode = 0;
    return false;
  }
  return true;
}

/*******************************************************************************
 *
 * Function         l2c_fcr_adj_monitor_retran_timeout
 *
 * Description      Overrides monitor/retrans timer value based on controller
 *
 * Returns          None
 *
 ******************************************************************************/
void l2c_fcr_adj_monitor_retran_timeout(tL2C_CCB* p_ccb) {
  CHECK(p_ccb != NULL);

  /* adjust our monitor/retran timeout */
  if (p_ccb->out_cfg_fcr_present) {
    /*
    ** if we requestd ERTM or accepted ERTM
    ** We may accept ERTM even if we didn't request ERTM, in case of requesting
    *STREAM
    */
    if ((p_ccb->our_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) ||
        (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE)) {
      /* upper layer setting is ignored */
      p_ccb->our_cfg.fcr.mon_tout = L2CAP_MIN_MONITOR_TOUT;
      p_ccb->our_cfg.fcr.rtrans_tout = L2CAP_MIN_RETRANS_TOUT;
    } else {
      p_ccb->our_cfg.fcr.mon_tout = 0;
      p_ccb->our_cfg.fcr.rtrans_tout = 0;
    }

    log::verbose(
        "l2c_fcr_adj_monitor_retran_timeout: mon_tout:{}, rtrans_tout:{}",
        p_ccb->our_cfg.fcr.mon_tout, p_ccb->our_cfg.fcr.rtrans_tout);
  }
}
/*******************************************************************************
 *
 * Function         l2c_fcr_adj_our_rsp_options
 *
 * Description      Overrides any neccesary FCR options passed in from
 *                  L2CA_ConfigRsp based on our FCR options.
 *                  Only makes adjustments if channel is in ERTM mode.
 *
 * Returns          None
 *
 ******************************************************************************/
void l2c_fcr_adj_our_rsp_options(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg) {
  CHECK(p_ccb != NULL);
  CHECK(p_cfg != NULL);

  /* adjust our monitor/retran timeout */
  l2c_fcr_adj_monitor_retran_timeout(p_ccb);

  p_cfg->fcr_present = p_ccb->out_cfg_fcr_present;

  if (p_cfg->fcr_present) {
    /* Temporary - until a better algorithm is implemented */
    /* If peer's tx_wnd_sz requires too many buffers for us to support, then
     * adjust it. For now, respond with our own tx_wnd_sz. */
    /* Note: peer is not guaranteed to obey our adjustment */
    if (p_ccb->peer_cfg.fcr.tx_win_sz > p_ccb->our_cfg.fcr.tx_win_sz) {
      log::verbose("adjusting requested tx_win_sz from {} to {}",
                   p_ccb->peer_cfg.fcr.tx_win_sz, p_ccb->our_cfg.fcr.tx_win_sz);
      p_ccb->peer_cfg.fcr.tx_win_sz = p_ccb->our_cfg.fcr.tx_win_sz;
    }

    p_cfg->fcr.mode = p_ccb->peer_cfg.fcr.mode;
    p_cfg->fcr.tx_win_sz = p_ccb->peer_cfg.fcr.tx_win_sz;
    p_cfg->fcr.max_transmit = p_ccb->peer_cfg.fcr.max_transmit;
    p_cfg->fcr.mps = p_ccb->peer_cfg.fcr.mps;
    p_cfg->fcr.rtrans_tout = p_ccb->our_cfg.fcr.rtrans_tout;
    p_cfg->fcr.mon_tout = p_ccb->our_cfg.fcr.mon_tout;
  }
}

/*******************************************************************************
 *
 * Function         l2c_fcr_renegotiate_chan
 *
 * Description      Called upon unsuccessful peer response to config request.
 *                  If the error is because of the channel mode, it will try
 *                  to resend using another supported optional channel.
 *
 * Returns          true if resent configuration, False if channel matches or
 *                  cannot match.
 *
 ******************************************************************************/
bool l2c_fcr_renegotiate_chan(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg) {
  CHECK(p_ccb != NULL);
  CHECK(p_cfg != NULL);

  uint8_t peer_mode = p_ccb->our_cfg.fcr.mode;
  bool can_renegotiate;

  /* Skip if this is a reconfiguration from OPEN STATE or if FCR is not returned
   */
  if (!p_cfg->fcr_present || (p_ccb->config_done & RECONFIG_FLAG))
    return (false);

  /* Only retry if there are more channel options to try */
  if (p_cfg->result == L2CAP_CFG_UNACCEPTABLE_PARAMS) {
    peer_mode = (p_cfg->fcr_present) ? p_cfg->fcr.mode : L2CAP_FCR_BASIC_MODE;

    if (p_ccb->our_cfg.fcr.mode != peer_mode) {
      if ((--p_ccb->fcr_cfg_tries) == 0) {
        p_cfg->result = L2CAP_CFG_FAILED_NO_REASON;
        log::warn("l2c_fcr_renegotiate_chan (Max retries exceeded)");
      }

      can_renegotiate = false;

      /* Try another supported mode if available based on our last attempted
       * channel */
      switch (p_ccb->our_cfg.fcr.mode) {
        case L2CAP_FCR_ERTM_MODE:
          /* We can try basic for any other peer mode because it's always
           * supported */
          log::verbose("(Trying Basic)");
          can_renegotiate = true;
          p_ccb->our_cfg.fcr.mode = L2CAP_FCR_BASIC_MODE;
          break;

        default:
          /* All other scenarios cannot be renegotiated */
          break;
      }

      if (can_renegotiate) {
        p_ccb->our_cfg.fcr_present = true;

        if (p_ccb->our_cfg.fcr.mode == L2CAP_FCR_BASIC_MODE) {
          p_ccb->our_cfg.fcs_present = false;
          p_ccb->our_cfg.ext_flow_spec_present = false;

          /* Basic Mode uses ACL Data Pool, make sure the MTU fits */
          if ((p_cfg->mtu_present) && (p_cfg->mtu > L2CAP_MTU_SIZE)) {
            log::warn("L2CAP - adjust MTU: {} too large", p_cfg->mtu);
            p_cfg->mtu = L2CAP_MTU_SIZE;
          }
        }

        l2cu_process_our_cfg_req(p_ccb, &p_ccb->our_cfg);
        l2cu_send_peer_config_req(p_ccb, &p_ccb->our_cfg);
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
        return (true);
      }
    }
  }

  /* Disconnect if the channels do not match */
  if (p_ccb->our_cfg.fcr.mode != peer_mode) {
    log::warn("L2C CFG:  Channels incompatible (local {}, peer {})",
              p_ccb->our_cfg.fcr.mode, peer_mode);
    l2cu_disconnect_chnl(p_ccb);
  }

  return (false);
}

/*******************************************************************************
 *
 * Function         l2c_fcr_process_peer_cfg_req
 *
 * Description      This function is called to process the FCR options passed
 *                  in the peer's configuration request.
 *
 * Returns          uint8_t - L2CAP_PEER_CFG_OK, L2CAP_PEER_CFG_UNACCEPTABLE,
 *                          or L2CAP_PEER_CFG_DISCONNECT.
 *
 ******************************************************************************/
uint8_t l2c_fcr_process_peer_cfg_req(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg) {
  CHECK(p_ccb != NULL);
  CHECK(p_cfg != NULL);

  uint16_t max_retrans_size;
  uint8_t fcr_ok = L2CAP_PEER_CFG_OK;

  p_ccb->p_lcb->w4_info_rsp =
      false; /* Handles T61x SonyEricsson Bug in Info Request */

  log::verbose(
      "l2c_fcr_process_peer_cfg_req() CFG fcr_present:{} fcr.mode:{} CCB FCR "
      "mode:{} preferred: {}",
      p_cfg->fcr_present, p_cfg->fcr.mode, p_ccb->our_cfg.fcr.mode,
      p_ccb->p_rcb->ertm_info.preferred_mode);

  /* Need to negotiate if our modes are not the same */
  if (p_cfg->fcr.mode != p_ccb->p_rcb->ertm_info.preferred_mode) {
    /* If peer wants a mode that we don't support then retry our mode (ex.
     *rtx/flc), OR
     ** If we want ERTM and they want non-basic mode, retry our mode.
     ** Note: If we have already determined they support our mode previously
     **       from their EXF mask.
     */
    if ((((1 << p_cfg->fcr.mode) & L2CAP_FCR_CHAN_OPT_ALL_MASK) == 0) ||
        ((p_ccb->p_rcb->ertm_info.preferred_mode == L2CAP_FCR_ERTM_MODE) &&
         (p_cfg->fcr.mode != L2CAP_FCR_BASIC_MODE))) {
      p_cfg->fcr.mode = p_ccb->our_cfg.fcr.mode;
      p_cfg->fcr.tx_win_sz = p_ccb->our_cfg.fcr.tx_win_sz;
      p_cfg->fcr.max_transmit = p_ccb->our_cfg.fcr.max_transmit;
      fcr_ok = L2CAP_PEER_CFG_UNACCEPTABLE;
    }

    /* If we wanted basic, then try to renegotiate it */
    else if (p_ccb->p_rcb->ertm_info.preferred_mode == L2CAP_FCR_BASIC_MODE) {
      p_cfg->fcr.mode = L2CAP_FCR_BASIC_MODE;
      p_cfg->fcr.max_transmit = p_cfg->fcr.tx_win_sz = 0;
      p_cfg->fcr.rtrans_tout = p_cfg->fcr.mon_tout = p_cfg->fcr.mps = 0;
      p_ccb->our_cfg.fcr.rtrans_tout = p_ccb->our_cfg.fcr.mon_tout =
          p_ccb->our_cfg.fcr.mps = 0;
      fcr_ok = L2CAP_PEER_CFG_UNACCEPTABLE;
    }
  }

  /* Configuration for FCR channels so make any adjustments and fwd to upper
   * layer */
  if (fcr_ok == L2CAP_PEER_CFG_OK) {
    /* by default don't need to send params in the response */
    p_ccb->out_cfg_fcr_present = false;

    /* Make any needed adjustments for the response to the peer */
    if (p_cfg->fcr_present && p_cfg->fcr.mode != L2CAP_FCR_BASIC_MODE) {
      /* Peer desires to bypass FCS check, and streaming or ERTM mode */
      if (p_cfg->fcs_present) {
        p_ccb->peer_cfg.fcs = p_cfg->fcs;
      }

      max_retrans_size = BT_DEFAULT_BUFFER_SIZE - sizeof(BT_HDR) -
                         L2CAP_MIN_OFFSET - L2CAP_SDU_LEN_OFFSET -
                         L2CAP_FCS_LEN;

      /* Ensure the MPS is not bigger than the MTU */
      if ((p_cfg->fcr.mps == 0) || (p_cfg->fcr.mps > p_ccb->peer_cfg.mtu)) {
        p_cfg->fcr.mps = p_ccb->peer_cfg.mtu;
        p_ccb->out_cfg_fcr_present = true;
      }

      /* Ensure the MPS is not bigger than our retransmission buffer */
      if (p_cfg->fcr.mps > max_retrans_size) {
        log::verbose("CFG: Overriding MPS to {} (orig {})", max_retrans_size,
                     p_cfg->fcr.mps);

        p_cfg->fcr.mps = max_retrans_size;
        p_ccb->out_cfg_fcr_present = true;
      }

      if (p_cfg->fcr.mode == L2CAP_FCR_ERTM_MODE) {
        /* Always respond with FCR ERTM parameters */
        p_ccb->out_cfg_fcr_present = true;
      }
    }

    /* Everything ok, so save the peer's adjusted fcr options */
    p_ccb->peer_cfg.fcr = p_cfg->fcr;

  } else if (fcr_ok == L2CAP_PEER_CFG_UNACCEPTABLE) {
    /* Allow peer only one retry for mode */
    if (p_ccb->peer_cfg_already_rejected)
      fcr_ok = L2CAP_PEER_CFG_DISCONNECT;
    else
      p_ccb->peer_cfg_already_rejected = true;
  }

  return (fcr_ok);
}
