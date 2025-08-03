/* Copyright (c) 2018-2019, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
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

#ifndef __WIFI_HAL_TCP_PARAM_UPDATE_COMMAND_H__
#define __WIFI_HAL_TCP_PARAM_UPDATE_COMMAND_H__

#include "cpp_bindings.h"
#include "common.h"
#include <errno.h>

#define SIZE_TCP_PARAM	100
#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

/* Indicates the direction of throughput in which the change is being
 * reported. u8 attribute. Value is 0 for TX and 1 for RX.
 */
typedef enum {
	TP_CHANGE_TX = 0,
	TP_CHANGE_RX = 1
} TpChangeDirection;

wifi_error wlan_service_update_sys_param(const char *path, const char *str);
wifi_error wlan_service_read_sys_param(const char *path, char *str, size_t max_size);
void cleanupTCPParamCommand(hal_info *info);

class TCPParamCommand: public WifiVendorCommand
{
private:
	TCPParamCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
	int wlan_service_set_tcp_adv_win_scale(char *str);
	int wlan_service_set_tcp_use_userconfig(const char *str);
	int wlan_service_set_tcp_delack_seg(char *str);
	int wlan_service_set_tcp_limit_output_bytes(char *str);

public:
	virtual ~TCPParamCommand();
	static TCPParamCommand *instance(wifi_handle handle,
					 wifi_request_id id);
	virtual int handleEvent(WifiEvent &event);
	char def_tcp_limit_output_bytes[SIZE_TCP_PARAM];
	bool def_tcp_limit_output_bytes_valid;
	char def_tcp_adv_win_scale[SIZE_TCP_PARAM];
	bool def_tcp_adv_win_scale_valid;
};

/* Used to clean TCPParam object*/
typedef struct tcp_param_cmd_handler_s {
	TCPParamCommand *tcpParamCommand;
} tcp_param_cmd_handler;
#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
