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

#ifndef _TUNNEL_H_
#define _TUNNEL_H_

#if __cplusplus
extern "C"
{
#endif

struct ia_tunneling_hal;

/**
 * Opens up the tunnel port and sets it up to start the tunneling of data
 *
 * Input  - buffering size
 *          If buffering size is non-zero, then set Non Buffering Flag
 * Output - Handle to Tunneling HAL
 *        - NULL on failure, non null on success
 */
struct ia_tunneling_hal* ia_start_tunneling(int buffering_size);

/**
 * Enable the tunneling of the data for a particular tunnel id and a source id
 *
 * Input  - tun_hdl - Handle to the Tunneling HAL.
 *          src_id - Source system ID for this tunnel
 *          tunl_mode - tunnel out in sync or async mode.
 *          tunl_encode -tunnel encoding format.
 *
 * Output - Zero on success, errno on failure.
 */
int ia_enable_tunneling_source(struct ia_tunneling_hal *tun_hdl,
                               unsigned int src_id,
                               unsigned int tunl_mode,
                               unsigned int tunl_encode);

/**
 * Disable the tunneling of the data for a particular tunnel id and a source id
 *
 * Input  - tun_hdl - Handle to the Tunneling HAL.
 *          src_id - Source system ID for this tunnel
 *          tunl_mode - tunnel out in sync or async mode.
 *          tunl_encode -tunnel encoding format.
 *
 * Output - Zero on success, errno on failure.
 */
int ia_disable_tunneling_source(struct ia_tunneling_hal *tun_hdl,
                               unsigned int src_id,
                               unsigned int tunl_mode,
                               unsigned int tunl_encode);


/**
 * Get the tunneled data. This data is an interleaved data from all the tunnels that were
 * enabled.
 *
 * Input  - tun_hdl - Handle to the Tunneling HAL.
 *          buf - buffer in which the data will be filled.
 *          buf_size - Size of the buffer buf
 * Output - Number of bytes filled into the buffer
 */
int ia_read_tunnel_data(struct ia_tunneling_hal *tun_hdl, void *buf, int buf_size);

/**
 * Set the output buffer threshold for the event generation.
 *
 * Input  - threshold
 *
 * Output - Zero on success, errno on failure.
 */
int ia_set_tunnel_out_buf_threshold(struct ia_tunneling_hal *thdl,
                                    uint32_t threshold);

/**
 * Closes tunneling port
 *
 * Input  - tun_hdl - Handle to the Tunneling HAL.
 * Output - Zero on success, errno on failure.
 */
int ia_stop_tunneling(struct ia_tunneling_hal *tun_hdl);

#if __cplusplus
} // extern "C"
#endif

#endif
