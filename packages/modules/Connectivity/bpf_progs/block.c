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

#include <linux/types.h>
#include <linux/bpf.h>
#include <netinet/in.h>
#include <stdint.h>

// The resulting .o needs to load on the Android T bpfloader
#define BPFLOADER_MIN_VER BPFLOADER_T_VERSION

#include "bpf_helpers.h"

static const int ALLOW = 1;
static const int DISALLOW = 0;

DEFINE_BPF_MAP_GRW(blocked_ports_map, ARRAY, int, uint64_t,
        1024 /* 64K ports -> 1024 u64s */, AID_SYSTEM)

static inline __always_inline int block_port(struct bpf_sock_addr *ctx) {
    if (!ctx->user_port) return ALLOW;

    switch (ctx->protocol) {
        case IPPROTO_TCP:
        case IPPROTO_MPTCP:
        case IPPROTO_UDP:
        case IPPROTO_UDPLITE:
        case IPPROTO_DCCP:
        case IPPROTO_SCTP:
            break;
        default:
            return ALLOW; // unknown protocols are allowed
    }

    int key = ctx->user_port >> 6;
    int shift = ctx->user_port & 63;

    uint64_t *val = bpf_blocked_ports_map_lookup_elem(&key);
    // Lookup should never fail in reality, but if it does return here to keep the
    // BPF verifier happy.
    if (!val) return ALLOW;

    if ((*val >> shift) & 1) return DISALLOW;
    return ALLOW;
}

// the program need to be accessible/loadable by netd (from netd updatable plugin)
#define DEFINE_NETD_RO_BPF_PROG(SECTION_NAME, the_prog, min_kver) \
    DEFINE_BPF_PROG_EXT(SECTION_NAME, AID_ROOT, AID_ROOT, the_prog, min_kver, KVER_INF,  \
                        BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, MANDATORY, \
                        "", "netd_readonly/", LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

DEFINE_NETD_RO_BPF_PROG("bind4/block_port", bind4_block_port, KVER_4_19)
(struct bpf_sock_addr *ctx) {
    return block_port(ctx);
}

DEFINE_NETD_RO_BPF_PROG("bind6/block_port", bind6_block_port, KVER_4_19)
(struct bpf_sock_addr *ctx) {
    return block_port(ctx);
}

LICENSE("Apache 2.0");
CRITICAL("ConnectivityNative");
DISABLE_BTF_ON_USER_BUILDS();
