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
 * DO *NOT* *EVER* CHANGE THIS CONSTANT
 *
 * This is aidl::android::net::INetd::CLAT_MARK but we can't use that from
 * pure C code (ie. the eBPF clat program).
 *
 * It must match the iptables rules setup by netd on Android T.
 *
 * This mark value is used by the eBPF clatd program to mark ingress non-offloaded clat
 * packets for later dropping in ip6tables bw_raw_PREROUTING.
 * They need to be dropped *after* the clat daemon (via receive on an AF_PACKET socket)
 * sees them and thus cannot be dropped from the bpf program itself.
 */
static const uint32_t CLAT_MARK = 0xDEADC1A7;
