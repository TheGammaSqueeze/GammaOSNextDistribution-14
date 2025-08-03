/*
 * aidl interface for wpa_supplicant daemon
 * Copyright (c) 2004-2018, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2004-2018, Roshan Pius <rpius@google.com>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#pragma once

#ifdef __cplusplus
extern "C"
{
#endif  // _cplusplus
#include "ap/hostapd.h"

/**
 * This is the aidl RPC interface entry point to the hostapd core.
 * This initializes the aidl driver & IHostapd instance.
 */
int hostapd_aidl_init(struct hapd_interfaces *interfaces);
void hostapd_aidl_deinit(struct hapd_interfaces *interfaces);

#ifdef __cplusplus
}
#endif  // _cplusplus
