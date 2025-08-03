/*
 * Driver interaction with extended Linux CFG8021
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * Alternatively, this software may be distributed under the terms of BSD
 * license.
 */

#include "includes.h"

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <netinet/in.h>
#include <linux/if.h>

#include "common.h"
#include "wpa_supplicant_i.h"
#include "driver_nl80211.h"
#include "config.h"
#include "android_drv.h"
#include "linux_ioctl.h"

#define UNUSED_ARG __attribute__((__unused__))

int wpa_driver_nl80211_driver_cmd(
    void* priv, char* cmd, char* buf, size_t buf_len) {
  struct i802_bss* bss = priv;
  struct wpa_driver_nl80211_data* drv = bss->drv;
  int ret = 0;

  if (os_strcasecmp(cmd, "STOP") == 0) {
    linux_set_iface_flags(drv->global->ioctl_sock, bss->ifname, 0);
    wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STOPPED");
  } else if (os_strcasecmp(cmd, "START") == 0) {
    linux_set_iface_flags(drv->global->ioctl_sock, bss->ifname, 1);
    wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STARTED");
  } else if (os_strcasecmp(cmd, "MACADDR") == 0) {
    u8 macaddr[ETH_ALEN] = {};

    ret = linux_get_ifhwaddr(drv->global->ioctl_sock, bss->ifname, macaddr);
    if (!ret)
      ret = os_snprintf(
          buf, buf_len, "Macaddr = " MACSTR "\n", MAC2STR(macaddr));
  } else if (os_strcasecmp(cmd, "RELOAD") == 0) {
    wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "HANGED");
  } else {  // Use private command
    return 0;
  }
  return ret;
}

int wpa_driver_set_p2p_noa(
        UNUSED_ARG void* priv,
        UNUSED_ARG u8 count,
        UNUSED_ARG int start,
        UNUSED_ARG int duration) {
  return 0;
}

int wpa_driver_get_p2p_noa(
        UNUSED_ARG void* priv,
        UNUSED_ARG u8* buf,
        UNUSED_ARG size_t len) {
  return 0;
}

int wpa_driver_set_p2p_ps(
        UNUSED_ARG void* priv,
        UNUSED_ARG int legacy_ps,
        UNUSED_ARG int opp_ps,
        UNUSED_ARG int ctwindow) {
  return -1;
}

int wpa_driver_set_ap_wps_p2p_ie(
        UNUSED_ARG void* priv,
        UNUSED_ARG const struct wpabuf* beacon,
        UNUSED_ARG const struct wpabuf* proberesp,
        UNUSED_ARG const struct wpabuf* assocresp) {
  return 0;
}
