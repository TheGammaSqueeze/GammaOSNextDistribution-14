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
 *  This is the interface file for pan call-in functions.
 *
 ******************************************************************************/
#ifndef BTA_PAN_CI_H
#define BTA_PAN_CI_H

#include <cstdint>

#include "bta/include/bta_pan_api.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Function Declarations
 ****************************************************************************/
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
void bta_pan_ci_tx_ready(uint16_t handle);

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
void bta_pan_ci_rx_ready(uint16_t handle);

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
void bta_pan_ci_tx_flow(uint16_t handle, bool enable);

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
 * Returns          true if flow enabled
 *
 ******************************************************************************/
void bta_pan_ci_rx_writebuf(uint16_t handle, const RawAddress& src,
                            const RawAddress& dst, uint16_t protocol,
                            BT_HDR* p_buf, bool ext);

/*******************************************************************************
 *
 * Function         bta_pan_ci_readbuf
 *
 * Description      This function is called by the phone to read data from PAN
 *                  when the TX path is configured to use a pull interface.
 *                  The caller must free the buffer when it is through
 *                  processing the buffer.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
BT_HDR* bta_pan_ci_readbuf(uint16_t handle, RawAddress& src, RawAddress& dst,
                           uint16_t* p_protocol, bool* p_ext, bool* p_forward);

#endif /* BTA_PAN_CI_H */
