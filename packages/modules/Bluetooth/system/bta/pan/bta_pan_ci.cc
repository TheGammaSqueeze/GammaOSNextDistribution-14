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
 *  This is the implementation file for data gateway call-in functions.
 *
 ******************************************************************************/

#include "bta/pan/bta_pan_int.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

void bta_pan_sm_execute(tBTA_PAN_SCB* p_scb, uint16_t event,
                        tBTA_PAN_DATA* p_data);

/*******************************************************************************
 *
 * Function         bta_pan_ci_tx_ready
 *
 * Description      This function sends an event to PAN indicating the phone is
 *                  ready for more data and PAN should call
 *                  bta_pan_co_tx_path().
 *                  This function is used when the TX data path is configured
 *                  to use a pull interface.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_pan_ci_tx_ready(uint16_t handle) {
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(sizeof(BT_HDR));

  p_buf->layer_specific = handle;
  p_buf->event = BTA_PAN_CI_TX_READY_EVT;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_pan_ci_rx_ready
 *
 * Description      This function sends an event to PAN indicating the phone
 *                  has data available to send to PAN and PAN should call
 *                  bta_pan_co_rx_path().  This function is used when the RX
 *                  data path is configured to use a pull interface.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_pan_ci_rx_ready(uint16_t handle) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  p_buf->layer_specific = handle;
  p_buf->event = BTA_PAN_CI_RX_READY_EVT;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_pan_ci_tx_flow
 *
 * Description      This function is called to enable or disable data flow on
 *                  the TX path.  The phone should call this function to
 *                  disable data flow when it is congested and cannot handle
 *                  any more data sent by bta_pan_co_tx_write().
 *                  This function is used when the
 *                  TX data path is configured to use a push interface.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_pan_ci_tx_flow(uint16_t handle, bool enable) {
  tBTA_PAN_CI_TX_FLOW* p_buf =
      (tBTA_PAN_CI_TX_FLOW*)osi_malloc(sizeof(tBTA_PAN_CI_TX_FLOW));

  p_buf->hdr.layer_specific = handle;
  p_buf->hdr.event = BTA_PAN_CI_TX_FLOW_EVT;
  p_buf->enable = enable;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_pan_ci_rx_write
 *
 * Description      This function is called to send data to PAN when the RX path
 *                  is configured to use a push interface.  The function copies
 *                  data to an event buffer and sends it to PAN.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_pan_ci_rx_write(uint16_t handle, const RawAddress& dst,
                         const RawAddress& src, uint16_t protocol,
                         uint8_t* p_data, uint16_t len, bool ext) {
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(PAN_BUF_SIZE);

  p_buf->offset = PAN_MINIMUM_OFFSET;

  /* copy all other params before the data */
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->src = src;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->dst = dst;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->protocol = protocol;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->ext = ext;
  p_buf->len = len;

  /* copy data */
  memcpy((uint8_t*)(p_buf + 1) + p_buf->offset, p_data, len);

  p_buf->layer_specific = handle;
  p_buf->event = BTA_PAN_CI_RX_WRITEBUF_EVT;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_pan_ci_rx_writebuf
 *
 * Description      This function is called to send data to the phone when
 *                  the RX path is configured to use a push interface with
 *                  zero copy.  The function sends an event to PAN containing
 *                  the data buffer. The buffer will be freed by BTA; the
 *                  phone must not free the buffer.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_pan_ci_rx_writebuf(uint16_t handle, const RawAddress& dst,
                            const RawAddress& src, uint16_t protocol,
                            BT_HDR* p_buf, bool ext) {
  /* copy all other params before the data */
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->src = src;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->dst = dst;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->protocol = protocol;
  ((tBTA_PAN_DATA_PARAMS*)p_buf)->ext = ext;

  p_buf->layer_specific = handle;
  p_buf->event = BTA_PAN_CI_RX_WRITEBUF_EVT;
  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_pan_ci_readbuf
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
BT_HDR* bta_pan_ci_readbuf(uint16_t handle, RawAddress& src, RawAddress& dst,
                           uint16_t* p_protocol, bool* p_ext, bool* p_forward) {
  tBTA_PAN_SCB* p_scb = bta_pan_scb_by_handle(handle);
  BT_HDR* p_buf;

  if (p_scb == NULL) return NULL;

  p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_scb->data_queue);
  if (p_buf != NULL) {
    src = ((tBTA_PAN_DATA_PARAMS*)p_buf)->src;
    dst = ((tBTA_PAN_DATA_PARAMS*)p_buf)->dst;
    *p_protocol = ((tBTA_PAN_DATA_PARAMS*)p_buf)->protocol;
    *p_ext = ((tBTA_PAN_DATA_PARAMS*)p_buf)->ext;
    *p_forward = ((tBTA_PAN_DATA_PARAMS*)p_buf)->forward;
  }

  return p_buf;
}
