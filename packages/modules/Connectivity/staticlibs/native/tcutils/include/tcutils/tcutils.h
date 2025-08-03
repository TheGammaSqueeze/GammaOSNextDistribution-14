/*
 * Copyright (C) 2022 The Android Open Source Project
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

#pragma once

#include <cstdint>
#include <linux/rtnetlink.h>

namespace android {

int isEthernet(const char *iface, bool &isEthernet);

int doTcQdiscClsact(int ifIndex, uint16_t nlMsgType, uint16_t nlMsgFlags);

static inline int tcAddQdiscClsact(int ifIndex) {
  return doTcQdiscClsact(ifIndex, RTM_NEWQDISC, NLM_F_EXCL | NLM_F_CREATE);
}

static inline int tcReplaceQdiscClsact(int ifIndex) {
  return doTcQdiscClsact(ifIndex, RTM_NEWQDISC, NLM_F_CREATE | NLM_F_REPLACE);
}

static inline int tcDeleteQdiscClsact(int ifIndex) {
  return doTcQdiscClsact(ifIndex, RTM_DELQDISC, 0);
}

int tcAddBpfFilter(int ifIndex, bool ingress, uint16_t prio, uint16_t proto,
                   const char *bpfProgPath);
int tcAddIngressPoliceFilter(int ifIndex, uint16_t prio, uint16_t proto,
                             unsigned rateInBytesPerSec,
                             const char *bpfProgPath);
int tcDeleteFilter(int ifIndex, bool ingress, uint16_t prio, uint16_t proto);

} // namespace android
