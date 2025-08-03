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

/* -=-=-=-=-= WARNING -=-=-=-=-=-
 *
 * DO *NOT* *EVER* CHANGE THESE - they *MUST* match what the Tethering mainline module provides!
 *
 * You cannot even change them in sync, since newer module must work on older Android T releases.
 *
 * You could with difficulty, uprevs of the bpfloader, api detection logic in mainline, etc,
 * change this in Android U or later, but even that is a very bad idea and not worth the hassle.
 *
 *
 * Mainline Tethering module on T+ is expected to make available to netd (for use by
 * BandwidthController iptables initialization code) four xt_bpf programs at the following
 * locations:
 */
#define XT_BPF_NETD(NAME) "/sys/fs/bpf/netd_shared/prog_netd_skfilter_" NAME "_xtbpf"
#define XT_BPF_ALLOWLIST_PROG_PATH XT_BPF_NETD("allowlist")
#define XT_BPF_DENYLIST_PROG_PATH  XT_BPF_NETD("denylist")
#define XT_BPF_EGRESS_PROG_PATH    XT_BPF_NETD("egress")
#define XT_BPF_INGRESS_PROG_PATH   XT_BPF_NETD("ingress")
