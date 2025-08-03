/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <linux/if_ether.h>
#include <linux/in.h>
#include <linux/ip.h>

#ifdef BTF
// BTF is incompatible with bpfloaders < v0.10, hence for S (v0.2) we must
// ship a different file than for later versions, but we need bpfloader v0.25+
// for obj@ver.o support
#define BPFLOADER_MIN_VER BPFLOADER_OBJ_AT_VER_VERSION
#else /* BTF */
// The resulting .o needs to load on the Android S bpfloader
#define BPFLOADER_MIN_VER BPFLOADER_S_VERSION
#define BPFLOADER_MAX_VER BPFLOADER_OBJ_AT_VER_VERSION
#endif /* BTF */

// Warning: values other than AID_ROOT don't work for map uid on BpfLoader < v0.21
#define TETHERING_UID AID_ROOT

#define TETHERING_GID AID_NETWORK_STACK

// This is non production code, only used for testing
// Needed because the bitmap array definition is non-kosher for pre-T OS devices.
#define THIS_BPF_PROGRAM_IS_FOR_TEST_PURPOSES_ONLY

#include "bpf_helpers.h"
#include "bpf_net_helpers.h"
#include "offload.h"

// Used only by TetheringPrivilegedTests, not by production code.
DEFINE_BPF_MAP_GRW(tether_downstream6_map, HASH, TetherDownstream6Key, Tether6Value, 16,
                   TETHERING_GID)
// Used only by BpfBitmapTest, not by production code.
DEFINE_BPF_MAP_GRW(bitmap, ARRAY, int, uint64_t, 2, TETHERING_GID)

DEFINE_BPF_PROG_KVER("xdp/drop_ipv4_udp_ether", TETHERING_UID, TETHERING_GID,
                      xdp_test, KVER_5_9)
(struct xdp_md *ctx) {
    void *data = (void *)(long)ctx->data;
    void *data_end = (void *)(long)ctx->data_end;

    struct ethhdr *eth = data;
    int hsize = sizeof(*eth);

    struct iphdr *ip = data + hsize;
    hsize += sizeof(struct iphdr);

    if (data + hsize > data_end) return XDP_PASS;
    if (eth->h_proto != htons(ETH_P_IP)) return XDP_PASS;
    if (ip->protocol == IPPROTO_UDP) return XDP_DROP;
    return XDP_PASS;
}

LICENSE("Apache 2.0");
DISABLE_BTF_ON_USER_BUILDS();
