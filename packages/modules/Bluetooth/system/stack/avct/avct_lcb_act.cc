/******************************************************************************
 *
 *  Copyright 2003-2012 Broadcom Corporation
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
 *  This module contains action functions of the link control state machine.
 *
 ******************************************************************************/
#include <android_bluetooth_sysprop.h>
#include <bluetooth/log.h>
#include <string.h>

#include "avct_api.h"
#include "avct_int.h"
#include "bta/include/bta_sec_api.h"
#include "device/include/device_iot_config.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/avct/avct_defs.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"

using namespace bluetooth;

/* packet header length lookup table */
const uint8_t avct_lcb_pkt_type_len[] = {AVCT_HDR_LEN_SINGLE,
                                         AVCT_HDR_LEN_START, AVCT_HDR_LEN_CONT,
                                         AVCT_HDR_LEN_END};

/*******************************************************************************
 *
 * Function         avct_lcb_msg_asmbl
 *
 * Description      Reassemble incoming message.
 *
 *
 * Returns          Pointer to reassembled message;  NULL if no message
 *                  available.
 *
 ******************************************************************************/
static BT_HDR* avct_lcb_msg_asmbl(tAVCT_LCB* p_lcb, BT_HDR* p_buf) {
  uint8_t* p;
  uint8_t pkt_type;
  BT_HDR* p_ret;

  if (p_buf->len < 1) {
    osi_free(p_buf);
    p_ret = NULL;
    return p_ret;
  }

  /* parse the message header */
  p = (uint8_t*)(p_buf + 1) + p_buf->offset;
  pkt_type = AVCT_PKT_TYPE(p);

  /* quick sanity check on length */
  if (p_buf->len < avct_lcb_pkt_type_len[pkt_type] ||
      (sizeof(BT_HDR) + p_buf->offset + p_buf->len) > BT_DEFAULT_BUFFER_SIZE) {
    osi_free(p_buf);
    log::warn("Bad length during reassembly");
    p_ret = NULL;
  }
  /* single packet */
  else if (pkt_type == AVCT_PKT_TYPE_SINGLE) {
    /* if reassembly in progress drop message and process new single */
    if (p_lcb->p_rx_msg != NULL) log::warn("Got single during reassembly");

    osi_free_and_reset((void**)&p_lcb->p_rx_msg);

    p_ret = p_buf;
  }
  /* start packet */
  else if (pkt_type == AVCT_PKT_TYPE_START) {
    /* if reassembly in progress drop message and process new start */
    if (p_lcb->p_rx_msg != NULL) log::warn("Got start during reassembly");

    osi_free_and_reset((void**)&p_lcb->p_rx_msg);

    /*
     * Allocate bigger buffer for reassembly. As lower layers are
     * not aware of possible packet size after reassembly, they
     * would have allocated smaller buffer.
     */
    if (sizeof(BT_HDR) + p_buf->offset + p_buf->len > BT_DEFAULT_BUFFER_SIZE) {
      osi_free(p_buf);
      p_ret = NULL;
      return p_ret;
    }
    p_lcb->p_rx_msg = (BT_HDR*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
    memcpy(p_lcb->p_rx_msg, p_buf, sizeof(BT_HDR) + p_buf->offset + p_buf->len);

    /* Free original buffer */
    osi_free(p_buf);

    /* update p to point to new buffer */
    p = (uint8_t*)(p_lcb->p_rx_msg + 1) + p_lcb->p_rx_msg->offset;

    /* copy first header byte over nosp */
    *(p + 1) = *p;

    /* set offset to point to where to copy next */
    p_lcb->p_rx_msg->offset += p_lcb->p_rx_msg->len;

    /* adjust length for packet header */
    p_lcb->p_rx_msg->len -= 1;

    p_ret = NULL;
  }
  /* continue or end */
  else {
    /* if no reassembly in progress drop message */
    if (p_lcb->p_rx_msg == NULL) {
      osi_free(p_buf);
      log::warn("Pkt type={} out of order", pkt_type);
      p_ret = NULL;
    } else {
      /* get size of buffer holding assembled message */
      /*
       * NOTE: The buffer is allocated above at the beginning of the
       * reassembly, and is always of size BT_DEFAULT_BUFFER_SIZE.
       */
      uint16_t buf_len = BT_DEFAULT_BUFFER_SIZE - sizeof(BT_HDR);

      /* adjust offset and len of fragment for header byte */
      p_buf->offset += AVCT_HDR_LEN_CONT;
      p_buf->len -= AVCT_HDR_LEN_CONT;

      /* verify length */
      if ((p_lcb->p_rx_msg->offset + p_buf->len) > buf_len) {
        /* won't fit; free everything */
        log::warn("Fragmented message too big!");
        osi_free_and_reset((void**)&p_lcb->p_rx_msg);
        osi_free(p_buf);
        p_ret = NULL;
      } else {
        /* copy contents of p_buf to p_rx_msg */
        memcpy((uint8_t*)(p_lcb->p_rx_msg + 1) + p_lcb->p_rx_msg->offset,
               (uint8_t*)(p_buf + 1) + p_buf->offset, p_buf->len);

        if (pkt_type == AVCT_PKT_TYPE_END) {
          p_lcb->p_rx_msg->offset -= p_lcb->p_rx_msg->len;
          p_lcb->p_rx_msg->len += p_buf->len;
          p_ret = p_lcb->p_rx_msg;
          p_lcb->p_rx_msg = NULL;
        } else {
          p_lcb->p_rx_msg->offset += p_buf->len;
          p_lcb->p_rx_msg->len += p_buf->len;
          p_ret = NULL;
        }
        osi_free(p_buf);
      }
    }
  }
  return p_ret;
}

/*******************************************************************************
 *
 * Function         avct_lcb_chnl_open
 *
 * Description      Open L2CAP channel to peer
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_chnl_open(tAVCT_LCB* p_lcb, UNUSED_ATTR tAVCT_LCB_EVT* p_data) {
  uint16_t result = AVCT_RESULT_FAIL;

  p_lcb->ch_state = AVCT_CH_CONN;
  p_lcb->ch_lcid =
      L2CA_ConnectReq2(AVCT_PSM, p_lcb->peer_addr,
                       BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT);
  if (p_lcb->ch_lcid == 0) {
    /* if connect req failed, send ourselves close event */
    tAVCT_LCB_EVT avct_lcb_evt;
    avct_lcb_evt.result = result;
    avct_lcb_event(p_lcb, AVCT_LCB_LL_CLOSE_EVT, &avct_lcb_evt);
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_unbind_disc
 *
 * Description      Deallocate ccb and call callback with disconnect event.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_unbind_disc(UNUSED_ATTR tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  avct_ccb_dealloc(p_data->p_ccb, AVCT_DISCONNECT_CFM_EVT, 0, NULL);
}

/*******************************************************************************
 *
 * Function         avct_lcb_open_ind
 *
 * Description      Handle an LL_OPEN event.  For each allocated ccb already
 *                  bound to this lcb, send a connect event.  For each
 *                  unbound ccb with a new PID, bind that ccb to this lcb and
 *                  send a connect event.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_open_ind(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  int i;
  bool bind = false;

  if (GET_SYSPROP(A2dp, src_sink_coexist, false)) {
    bool is_originater = false;

    for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
      if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb) &&
          p_ccb->cc.role == AVCT_INT) {
        log::verbose("find int handle {}", i);
        is_originater = true;
      }
    }

    p_ccb = &avct_cb.ccb[0];
    for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
      /* if ccb allocated and */
      /** M: to avoid avctp collision, make sure the collision can be checked @{
       */
      log::verbose("{} ccb to lcb, alloc {}, lcb {}, role {}, pid 0x{:x}", i,
                   p_ccb->allocated, fmt::ptr(p_ccb->p_lcb), p_ccb->cc.role,
                   p_ccb->cc.pid);
      if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
        /* if bound to this lcb send connect confirm event */
        if (p_ccb->cc.role == AVCT_INT) {
          /** @} */
          bind = true;
          L2CA_SetTxPriority(p_lcb->ch_lcid, L2CAP_CHNL_PRIORITY_HIGH);
          p_ccb->cc.p_ctrl_cback(avct_ccb_to_idx(p_ccb), AVCT_CONNECT_CFM_EVT,
                                 0, &p_lcb->peer_addr);
        }
        /* if unbound acceptor and lcb doesn't already have a ccb for this PID
         */
        /** M: to avoid avctp collision, make sure the collision can be checked
           @{ */
        else if ((p_ccb->cc.role == AVCT_ACP) &&
                 avct_lcb_has_pid(p_lcb, p_ccb->cc.pid)) {
          /* bind ccb to lcb and send connect ind event  */
          if (is_originater) {
            log::error("int exist, unbind acp handle:{}", i);
            p_ccb->p_lcb = NULL;
          } else {
            bind = true;
            p_ccb->p_lcb = p_lcb;
            L2CA_SetTxPriority(p_lcb->ch_lcid, L2CAP_CHNL_PRIORITY_HIGH);
            p_ccb->cc.p_ctrl_cback(avct_ccb_to_idx(p_ccb), AVCT_CONNECT_IND_EVT,
                                   0, &p_lcb->peer_addr);
          }
        }
      }
    }
  } else {
    for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
      /* if ccb allocated and */
      if (p_ccb->allocated) {
        /* if bound to this lcb send connect confirm event */
        if (p_ccb->p_lcb == p_lcb) {
          bind = true;
          L2CA_SetTxPriority(p_lcb->ch_lcid, L2CAP_CHNL_PRIORITY_HIGH);
          p_ccb->cc.p_ctrl_cback(avct_ccb_to_idx(p_ccb), AVCT_CONNECT_CFM_EVT,
                                 0, &p_lcb->peer_addr);
        }
        /* if unbound acceptor and lcb doesn't already have a ccb for this PID
         */
        else if ((p_ccb->p_lcb == NULL) && (p_ccb->cc.role == AVCT_ACP) &&
                 (avct_lcb_has_pid(p_lcb, p_ccb->cc.pid) == NULL)) {
          /* bind ccb to lcb and send connect ind event */
          bind = true;
          p_ccb->p_lcb = p_lcb;
          L2CA_SetTxPriority(p_lcb->ch_lcid, L2CAP_CHNL_PRIORITY_HIGH);
          p_ccb->cc.p_ctrl_cback(avct_ccb_to_idx(p_ccb), AVCT_CONNECT_IND_EVT,
                                 0, &p_lcb->peer_addr);
        }
      }
    }
  }

  /* if no ccbs bound to this lcb, disconnect */
  if (!bind) {
    DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(p_lcb->peer_addr,
                                       IOT_CONF_KEY_AVRCP_CONN_FAIL_COUNT);
    avct_lcb_event(p_lcb, AVCT_LCB_INT_CLOSE_EVT, p_data);
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_open_fail
 *
 * Description      L2CAP channel open attempt failed.  Deallocate any ccbs
 *                  on this lcb and send connect confirm event with failure.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_open_fail(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  int i;

  for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
      avct_ccb_dealloc(p_ccb, AVCT_CONNECT_CFM_EVT, p_data->result,
                       &p_lcb->peer_addr);
      DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(p_lcb->peer_addr,
                                         IOT_CONF_KEY_AVRCP_CONN_FAIL_COUNT);
    }
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_close_ind
 *
 * Description      L2CAP channel closed by peer.  Deallocate any initiator
 *                  ccbs on this lcb and send disconnect ind event.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_close_ind(tAVCT_LCB* p_lcb, UNUSED_ATTR tAVCT_LCB_EVT* p_data) {
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  int i;

  for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
      if (p_ccb->cc.role == AVCT_INT) {
        avct_ccb_dealloc(p_ccb, AVCT_DISCONNECT_IND_EVT, 0, &p_lcb->peer_addr);
      } else {
        p_ccb->p_lcb = NULL;
        (*p_ccb->cc.p_ctrl_cback)(avct_ccb_to_idx(p_ccb),
                                  AVCT_DISCONNECT_IND_EVT, 0,
                                  &p_lcb->peer_addr);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_close_cfm
 *
 * Description      L2CAP channel closed by us.  Deallocate any initiator
 *                  ccbs on this lcb and send disconnect ind or cfm event.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_close_cfm(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  int i;
  uint8_t event;

  for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
      /* if this ccb initiated close send disconnect cfm otherwise ind */
      if (p_ccb->ch_close) {
        p_ccb->ch_close = false;
        event = AVCT_DISCONNECT_CFM_EVT;
      } else {
        event = AVCT_DISCONNECT_IND_EVT;
      }

      if (p_ccb->cc.role == AVCT_INT) {
        avct_ccb_dealloc(p_ccb, event, p_data->result, &p_lcb->peer_addr);
      } else {
        p_ccb->p_lcb = NULL;
        (*p_ccb->cc.p_ctrl_cback)(avct_ccb_to_idx(p_ccb), event, p_data->result,
                                  &p_lcb->peer_addr);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_bind_conn
 *
 * Description      Bind ccb to lcb and send connect cfm event.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_bind_conn(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  p_data->p_ccb->p_lcb = p_lcb;
  (*p_data->p_ccb->cc.p_ctrl_cback)(avct_ccb_to_idx(p_data->p_ccb),
                                    AVCT_CONNECT_CFM_EVT, 0, &p_lcb->peer_addr);
}

/*******************************************************************************
 *
 * Function         avct_lcb_chk_disc
 *
 * Description      A ccb wants to close; if it is the last ccb on this lcb,
 *                  close channel.  Otherwise just deallocate and call
 *                  callback.
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_chk_disc(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  avct_close_bcb(p_lcb, p_data);
  if (avct_lcb_last_ccb(p_lcb, p_data->p_ccb)) {
    log::info("Closing last avct channel to device");
    p_data->p_ccb->ch_close = true;
    avct_lcb_event(p_lcb, AVCT_LCB_INT_CLOSE_EVT, p_data);
  } else {
    log::info("Closing avct channel with active remaining channels");
    avct_lcb_unbind_disc(p_lcb, p_data);
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_chnl_disc
 *
 * Description      Disconnect L2CAP channel.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_chnl_disc(tAVCT_LCB* p_lcb, UNUSED_ATTR tAVCT_LCB_EVT* p_data) {
  avct_l2c_disconnect(p_lcb->ch_lcid, 0);
}

/*******************************************************************************
 *
 * Function         avct_lcb_bind_fail
 *
 * Description      Deallocate ccb and call callback with connect event
 *                  with failure result.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_bind_fail(UNUSED_ATTR tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  avct_ccb_dealloc(p_data->p_ccb, AVCT_CONNECT_CFM_EVT, AVCT_RESULT_FAIL, NULL);
  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(p_lcb->peer_addr,
                                     IOT_CONF_KEY_AVRCP_CONN_FAIL_COUNT);
}

/*******************************************************************************
 *
 * Function         avct_lcb_cong_ind
 *
 * Description      Handle congestion indication from L2CAP.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_cong_ind(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  int i;
  uint8_t event;
  BT_HDR* p_buf;

  /* set event */
  event = (p_data->cong) ? AVCT_CONG_IND_EVT : AVCT_UNCONG_IND_EVT;
  p_lcb->cong = p_data->cong;
  if (!p_lcb->cong && !fixed_queue_is_empty(p_lcb->tx_q)) {
    while (!p_lcb->cong &&
           (p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_lcb->tx_q)) != NULL) {
      if (L2CA_DataWrite(p_lcb->ch_lcid, p_buf) == L2CAP_DW_CONGESTED) {
        p_lcb->cong = true;
      }
    }
  }

  /* send event to all ccbs on this lcb */
  for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
      (*p_ccb->cc.p_ctrl_cback)(avct_ccb_to_idx(p_ccb), event, 0,
                                &p_lcb->peer_addr);
    }
  }
}

/*******************************************************************************
 *
 * Function         avct_lcb_discard_msg
 *
 * Description      Discard a message sent in from the API.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_discard_msg(UNUSED_ATTR tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  log::warn("Dropping message");
  osi_free_and_reset((void**)&p_data->ul_msg.p_buf);
}

/*******************************************************************************
 *
 * Function         avct_lcb_send_msg
 *
 * Description      Build and send an AVCTP message.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_send_msg(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  uint16_t curr_msg_len;
  uint8_t pkt_type;
  uint8_t hdr_len;
  uint8_t* p;
  uint8_t nosp = 0; /* number of subsequent packets */
  uint16_t temp;
  uint16_t buf_size = p_lcb->peer_mtu + L2CAP_MIN_OFFSET + BT_HDR_SIZE;

  /* store msg len */
  curr_msg_len = p_data->ul_msg.p_buf->len;

  /* initialize packet type and other stuff */
  if (curr_msg_len <= (p_lcb->peer_mtu - AVCT_HDR_LEN_SINGLE)) {
    pkt_type = AVCT_PKT_TYPE_SINGLE;
  } else {
    pkt_type = AVCT_PKT_TYPE_START;
    temp = (curr_msg_len + AVCT_HDR_LEN_START - p_lcb->peer_mtu);
    nosp = temp / (p_lcb->peer_mtu - 1) + 1;
    if ((temp % (p_lcb->peer_mtu - 1)) != 0) nosp++;
  }

  /* while we haven't sent all packets */
  while (curr_msg_len != 0) {
    BT_HDR* p_buf;

    /* set header len */
    hdr_len = avct_lcb_pkt_type_len[pkt_type];

    /* if remaining msg must be fragmented */
    if (p_data->ul_msg.p_buf->len > (p_lcb->peer_mtu - hdr_len)) {
      /* get a new buffer for fragment we are sending */
      p_buf = (BT_HDR*)osi_malloc(buf_size);

      /* copy portion of data from current message to new buffer */
      p_buf->offset = L2CAP_MIN_OFFSET + hdr_len;
      p_buf->len = p_lcb->peer_mtu - hdr_len;

      memcpy(
          (uint8_t*)(p_buf + 1) + p_buf->offset,
          (uint8_t*)(p_data->ul_msg.p_buf + 1) + p_data->ul_msg.p_buf->offset,
          p_buf->len);

      p_data->ul_msg.p_buf->offset += p_buf->len;
      p_data->ul_msg.p_buf->len -= p_buf->len;
    } else {
      p_buf = p_data->ul_msg.p_buf;
    }

    curr_msg_len -= p_buf->len;

    /* set up to build header */
    p_buf->len += hdr_len;
    p_buf->offset -= hdr_len;
    p = (uint8_t*)(p_buf + 1) + p_buf->offset;

    /* build header */
    AVCT_BUILD_HDR(p, p_data->ul_msg.label, pkt_type, p_data->ul_msg.cr);
    if (pkt_type == AVCT_PKT_TYPE_START) {
      UINT8_TO_STREAM(p, nosp);
    }
    if ((pkt_type == AVCT_PKT_TYPE_START) ||
        (pkt_type == AVCT_PKT_TYPE_SINGLE)) {
      UINT16_TO_BE_STREAM(p, p_data->ul_msg.p_ccb->cc.pid);
    }

    if (p_lcb->cong) {
      fixed_queue_enqueue(p_lcb->tx_q, p_buf);
    }

    /* send message to L2CAP */
    else {
      if (L2CA_DataWrite(p_lcb->ch_lcid, p_buf) == L2CAP_DW_CONGESTED) {
        p_lcb->cong = true;
      }
    }

    /* update pkt type for next packet */
    if (curr_msg_len > (p_lcb->peer_mtu - AVCT_HDR_LEN_END)) {
      pkt_type = AVCT_PKT_TYPE_CONT;
    } else {
      pkt_type = AVCT_PKT_TYPE_END;
    }
  }
  log::verbose("tx_q_count:{}", fixed_queue_length(p_lcb->tx_q));
  return;
}

/*******************************************************************************
 *
 * Function         avct_lcb_free_msg_ind
 *
 * Description      Discard an incoming AVCTP message.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_free_msg_ind(UNUSED_ATTR tAVCT_LCB* p_lcb,
                           tAVCT_LCB_EVT* p_data) {
  if (p_data == NULL) return;

  osi_free_and_reset((void**)&p_data->p_buf);
}

/*******************************************************************************
 *
 * Function         avct_lcb_msg_ind
 *
 * Description      Handle an incoming AVCTP message.
 *
 *
 * Returns          Nothing.
 *
 ******************************************************************************/
void avct_lcb_msg_ind(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data) {
  uint8_t* p;
  uint8_t label, type, cr_ipid;
  uint16_t pid;
  tAVCT_CCB* p_ccb;

  /* this p_buf is to be reported through p_msg_cback. The layer_specific
   * needs to be set properly to indicate that it is received through
   * control channel */
  p_data->p_buf->layer_specific = AVCT_DATA_CTRL;

  /* reassemble message; if no message available (we received a fragment) return
   */
  p_data->p_buf = avct_lcb_msg_asmbl(p_lcb, p_data->p_buf);
  if (p_data->p_buf == NULL) {
    return;
  }

  p = (uint8_t*)(p_data->p_buf + 1) + p_data->p_buf->offset;

  /* parse header byte */
  AVCT_PARSE_HDR(p, label, type, cr_ipid);
  /* parse PID */
  BE_STREAM_TO_UINT16(pid, p);

  /* check for invalid cr_ipid */
  if (cr_ipid == AVCT_CR_IPID_INVALID) {
    log::warn("Invalid cr_ipid {}", cr_ipid);
    osi_free_and_reset((void**)&p_data->p_buf);
    return;
  }

  bool bind = false;
  if (GET_SYSPROP(A2dp, src_sink_coexist, false)) {
    bind = avct_msg_ind_for_src_sink_coexist(p_lcb, p_data, label, cr_ipid);
    osi_free_and_reset((void**)&p_data->p_buf);
    if (bind) return;
  } else {
    /* lookup PID */
    p_ccb = avct_lcb_has_pid(p_lcb, pid);
    if (p_ccb) {
      /* PID found; send msg up, adjust bt hdr and call msg callback */
      p_data->p_buf->offset += AVCT_HDR_LEN_SINGLE;
      p_data->p_buf->len -= AVCT_HDR_LEN_SINGLE;
      (*p_ccb->cc.p_msg_cback)(avct_ccb_to_idx(p_ccb), label, cr_ipid,
                               p_data->p_buf);
      return;
    }
  }

  /* PID not found; drop message */
  log::warn("No ccb for PID={:x}", pid);
  osi_free_and_reset((void**)&p_data->p_buf);

  /* if command send reject */
  if (cr_ipid == AVCT_CMD) {
    BT_HDR* p_buf = (BT_HDR*)osi_malloc(AVCT_CMD_BUF_SIZE);
    p_buf->len = AVCT_HDR_LEN_SINGLE;
    p_buf->offset = AVCT_MSG_OFFSET - AVCT_HDR_LEN_SINGLE;
    p = (uint8_t*)(p_buf + 1) + p_buf->offset;
    AVCT_BUILD_HDR(p, label, AVCT_PKT_TYPE_SINGLE, AVCT_REJ);
    UINT16_TO_BE_STREAM(p, pid);
    L2CA_DataWrite(p_lcb->ch_lcid, p_buf);
  }
}

bool avct_msg_ind_for_src_sink_coexist(tAVCT_LCB* p_lcb, tAVCT_LCB_EVT* p_data,
                                       uint8_t label, uint8_t cr_ipid) {
  bool bind = false;
  tAVCT_CCB* p_ccb;
  int p_buf_len;
  uint8_t* p;
  uint16_t pid;

  p = (uint8_t*)(p_data->p_buf + 1) + p_data->p_buf->offset;

  BE_STREAM_TO_UINT16(pid, p);

  p_ccb = &avct_cb.ccb[0];
  p_data->p_buf->offset += AVCT_HDR_LEN_SINGLE;
  p_data->p_buf->len -= AVCT_HDR_LEN_SINGLE;
  p_buf_len = BT_HDR_SIZE + p_data->p_buf->offset + p_data->p_buf->len;

  for (int i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb) && (p_ccb->cc.pid == pid)) {
      /* PID found; send msg up, adjust bt hdr and call msg callback */
      bind = true;
      BT_HDR* p_tmp_buf = (BT_HDR*)osi_malloc(p_buf_len);
      memcpy(p_tmp_buf, p_data->p_buf, p_buf_len);
      (*p_ccb->cc.p_msg_cback)(avct_ccb_to_idx(p_ccb), label, cr_ipid,
                               p_tmp_buf);
    }
  }

  return bind;
}
