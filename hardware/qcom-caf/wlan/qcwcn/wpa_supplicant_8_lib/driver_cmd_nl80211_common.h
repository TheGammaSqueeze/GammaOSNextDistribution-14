/*
 * Driver interaction with extended Linux CFG80211
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * Alternatively, this software may be distributed under the terms of BSD
 * license.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *
 *    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <net/if.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/ctrl.h>
#include <netlink/object-api.h>
#include <linux/pkt_sched.h>

#define OBSS_PD_THRESHOLD_MIN -82
#define OBSS_PD_THRESHOLD_MAX -62

struct wpa_driver_nl80211_data *drv;
struct i802_bss *bss;
struct nl_msg *prepare_vendor_nlmsg(struct wpa_driver_nl80211_data *drv,
		                    char *ifname, int subcmd);

int send_nlmsg(struct nl_sock *cmd_sock, struct nl_msg *nlmsg,
	       nl_recvmsg_msg_cb_t customer_cb, void *arg);

char *result_copy_to_buf(char *src, char *dst_buf, int *dst_len);
int wpa_driver_sr_cmd(struct i802_bss *bss, char *cmd, char *buf, size_t buf_len);
int sr_response_handler(struct resp_info *info, struct nlattr *vendata, int datalen);
int response_handler(struct nl_msg *msg, void *arg);
int wpa_driver_sr_event(struct wpa_driver_nl80211_data *drv,
		        u32 vendor_id, u32 subcmd, u8 *data, size_t len);
char *skip_white_space(char *cmd);
char *get_next_arg(char *cmd);
s32 get_s32_from_string(char *cmd_string, int *ret);
