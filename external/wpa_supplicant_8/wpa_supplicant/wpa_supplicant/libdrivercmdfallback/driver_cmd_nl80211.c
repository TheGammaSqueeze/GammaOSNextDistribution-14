/*
 * Copyright (C) 2023 The Android Open Source Project
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
/*
 * Driver interaction with extended Linux CFG8021
 */

#include "includes.h"

#include "common.h"

int wpa_driver_nl80211_driver_cmd(void* priv, char* cmd, char* buf,
                                  size_t buf_len) {
  return 0;
}

int wpa_driver_set_p2p_noa(void* priv, u8 count, int start, int duration) {
  return 0;
}

int wpa_driver_get_p2p_noa(void* priv, u8* buf, size_t len) {
  return 0;
}

int wpa_driver_set_p2p_ps(void* priv, int legacy_ps, int opp_ps, int ctwindow) {
  return -1;
}

int wpa_driver_set_ap_wps_p2p_ie(void* priv, const struct wpabuf* beacon,
                                 const struct wpabuf* proberesp,
                                 const struct wpabuf* assocresp) {
  return 0;
}
