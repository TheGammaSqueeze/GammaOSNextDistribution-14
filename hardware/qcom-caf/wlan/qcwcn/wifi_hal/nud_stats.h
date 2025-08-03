/*
 * Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdint.h>
#include <fcntl.h>
#include <inttypes.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>

#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>
#include "wifi_hal.h"
#include <bits/in_addr.h>

#define INET6_ADDRSTRLEN 46

extern "C" {
	const char* inet_ntop(int __af, const void* __src, char* __dst, socklen_t __size);
}

typedef struct {
    uint16_t arp_req_count_from_netdev;
    uint16_t arp_req_count_to_lower_mac;
    uint16_t arp_req_rx_count_by_lower_mac;
    uint16_t arp_req_count_tx_success;
    uint16_t arp_rsp_rx_count_by_lower_mac;
    uint16_t arp_rsp_rx_count_by_upper_mac;
    uint16_t arp_rsp_count_to_netdev;
    uint16_t arp_rsp_count_out_of_order_drop;
    uint8_t ap_link_active;
    uint8_t is_duplicate_addr_detection;
} nud_stats;

typedef struct {
    uint16_t pkt_req_count_from_netdev;
    uint16_t pkt_req_count_to_lower_mac;
    uint16_t pkt_req_rx_count_by_lower_mac;
    uint16_t pkt_req_count_tx_success;
    uint16_t pkt_rsp_rx_count_by_lower_mac;
    uint16_t pkt_rsp_rx_count_by_upper_mac;
    uint16_t pkt_rsp_count_to_netdev;
    uint16_t pkt_rsp_count_out_of_order_drop;
} pkt_stats;

typedef struct{
    u32 pkt_Type;
    char* domain_name;
    u32 src_port;
    u32 dst_port;
    struct in_addr ipv4_addr;
    u8 ipv6_addr[16];
    pkt_stats stats;
} cmdData;

/* callback for get NUD stats */
typedef struct {
  void (*on_pkt_stats_results) (nud_stats *stats,
         int mnumStats,  cmdData *pkt_stats);
} pkt_stats_result_handler;

wifi_error wifi_set_nud_stats(wifi_interface_handle iface, u32 gw_addr,
                              cmdData set_data);
wifi_error wifi_get_nud_stats(wifi_interface_handle iface,
                              pkt_stats_result_handler handler);
wifi_error wifi_clear_nud_stats(wifi_interface_handle iface,
                                cmdData set_data);
