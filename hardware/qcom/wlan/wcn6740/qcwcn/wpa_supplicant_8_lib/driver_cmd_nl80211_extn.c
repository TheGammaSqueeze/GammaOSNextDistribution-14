/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

#include <netlink/object-api.h>
#include <linux/pkt_sched.h>
#include <dlfcn.h>
#include <dirent.h>
#include <string.h>
#include "common.h"
#include "driver_cmd_nl80211_extn.h"

#define QCA_NL80211_VENDOR_SUBCMD_DIAG_DATA 201
#define MAX_OEM_LIBS 5
#define MAX_LIB_NAME_SIZE 30
#define CB_SUFFIX "_cb"
static wpa_driver_oem_cb_table_t oem_cb_array[MAX_OEM_LIBS + 1];

void wpa_msg_handler(struct wpa_driver_nl80211_data *drv, char *msg, u32 subcmd) {
    if (subcmd == QCA_NL80211_VENDOR_SUBCMD_CONFIG_TWT) {
	wpa_msg(drv->ctx, MSG_INFO, "%s", msg);
    }
}

int wpa_driver_oem_initialize(wpa_driver_oem_cb_table_t **oem_cb_table)
{
	wpa_driver_oem_get_cb_table_t *get_oem_table;
	wpa_driver_oem_cb_table_t *oem_cb_table_local;
	struct dirent *entry;
	void *oem_handle_n;
	char cb_sym_name[MAX_LIB_NAME_SIZE], *tmp;
	DIR *oem_lib_dir;
	unsigned int lib_n;
#ifdef ANDROID
#if __WORDSIZE == 64
	char *oem_lib_path = "/vendor/lib64/";
#else
	char *oem_lib_path  = "/vendor/lib/";
#endif
#else
	char *oem_lib_path  = "/usr/lib/";
#endif
	/* Return the callback table if it is already initialized*/
	if (*oem_cb_table)
		return WPA_DRIVER_OEM_STATUS_SUCCESS;

	for (lib_n = 0; lib_n < MAX_OEM_LIBS; lib_n++) {
		oem_cb_array[lib_n].wpa_driver_driver_cmd_oem_cb = NULL;
		oem_cb_array[lib_n].wpa_driver_nl80211_driver_oem_event = NULL;
		oem_cb_array[lib_n].wpa_driver_oem_feature_check_cb = NULL;
	}

	oem_lib_dir = opendir(oem_lib_path);
	if (!oem_lib_dir) {
		wpa_printf(MSG_ERROR, "%s: Unable to open %s", __FUNCTION__, oem_lib_path);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	lib_n = 0;
	while((entry = readdir(oem_lib_dir)) != NULL) {
		if (strncmp(entry->d_name, "libwpa_drv_oem", 14))
			continue;

		wpa_printf(MSG_DEBUG, "%s: Opening lib %s", __FUNCTION__, entry->d_name);
		oem_handle_n = dlopen(entry->d_name, RTLD_NOW);

		if (!oem_handle_n) {
			wpa_printf(MSG_ERROR, "%s: Could not load %s", __FUNCTION__, entry->d_name);
			/* let's not worry much, continue with others */
			continue;
		}

		if (strlen(entry->d_name)  >= (sizeof(cb_sym_name) - sizeof(CB_SUFFIX))) {
			wpa_printf(MSG_ERROR, "%s: libname (%s) too lengthy", __FUNCTION__, entry->d_name);
			continue;
		}

		os_strlcpy(cb_sym_name, entry->d_name, sizeof(cb_sym_name));
		tmp = strchr(cb_sym_name, '.');
		if (!tmp) {
			wpa_printf(MSG_ERROR, "%s: libname (%s) incorrect?", __FUNCTION__, entry->d_name);
			continue;
		}

		os_strlcpy(tmp, CB_SUFFIX, sizeof(CB_SUFFIX));
		wpa_printf(MSG_DEBUG, "%s: Loading sym %s", __FUNCTION__, cb_sym_name);

		/* Get the lib's function table callback */
		get_oem_table = (wpa_driver_oem_get_cb_table_t *)dlsym(oem_handle_n,
				cb_sym_name);

		if (!get_oem_table) {
			wpa_printf(MSG_ERROR, "%s: Could not get sym table", __FUNCTION__);
			continue;
		}

		oem_cb_table_local = get_oem_table();

		oem_cb_array[lib_n].wpa_driver_driver_cmd_oem_cb =
			oem_cb_table_local->wpa_driver_driver_cmd_oem_cb;
		oem_cb_array[lib_n].wpa_driver_nl80211_driver_oem_event =
			oem_cb_table_local->wpa_driver_nl80211_driver_oem_event;
		oem_cb_array[lib_n].wpa_driver_driver_wpa_msg_oem_cb =
			oem_cb_table_local->wpa_driver_driver_wpa_msg_oem_cb;
		oem_cb_array[lib_n].wpa_driver_oem_feature_check_cb =
			oem_cb_table_local->wpa_driver_oem_feature_check_cb;

		/* Register wpa message callback with the oem library */
		if(oem_cb_array[lib_n].wpa_driver_driver_wpa_msg_oem_cb) {
			oem_cb_array[lib_n].wpa_driver_driver_wpa_msg_oem_cb(wpa_msg_handler);
		}

		lib_n++;

		if (lib_n == MAX_OEM_LIBS) {
			wpa_printf(MSG_DEBUG, "%s: Exceeded max libs %d", __FUNCTION__, lib_n);
			break;
		}
	}

	oem_cb_array[lib_n].wpa_driver_driver_cmd_oem_cb = NULL;
	*oem_cb_table = oem_cb_array;
	wpa_printf(MSG_DEBUG, "%s: OEM lib initialized\n", __func__);
	closedir(oem_lib_dir);

	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}
