/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <linux/bpf.h>
#include <linux/if_packet.h>
#include <stdbool.h>
#include <stdint.h>

// bionic kernel uapi linux/udp.h header is munged...
#define __kernel_udphdr udphdr
#include <linux/udp.h>

// Offsets from beginning of L4 (TCP/UDP) header
#define TCP_OFFSET(field) offsetof(struct tcphdr, field)
#define UDP_OFFSET(field) offsetof(struct udphdr, field)

// Offsets from beginning of L3 (IPv4/IPv6) header
#define IP4_OFFSET(field) offsetof(struct iphdr, field)
#define IP6_OFFSET(field) offsetof(struct ipv6hdr, field)

// this returns 0 iff skb->sk is NULL
static uint64_t (*bpf_get_socket_cookie)(struct __sk_buff* skb) = (void*)BPF_FUNC_get_socket_cookie;

static uint32_t (*bpf_get_socket_uid)(struct __sk_buff* skb) = (void*)BPF_FUNC_get_socket_uid;

static int (*bpf_skb_pull_data)(struct __sk_buff* skb, __u32 len) = (void*)BPF_FUNC_skb_pull_data;

static int (*bpf_skb_load_bytes)(const struct __sk_buff* skb, int off, void* to,
                                 int len) = (void*)BPF_FUNC_skb_load_bytes;

static int (*bpf_skb_load_bytes_relative)(const struct __sk_buff* skb, int off, void* to, int len,
                                          int start_hdr) = (void*)BPF_FUNC_skb_load_bytes_relative;

static int (*bpf_skb_store_bytes)(struct __sk_buff* skb, __u32 offset, const void* from, __u32 len,
                                  __u64 flags) = (void*)BPF_FUNC_skb_store_bytes;

static int64_t (*bpf_csum_diff)(__be32* from, __u32 from_size, __be32* to, __u32 to_size,
                                __wsum seed) = (void*)BPF_FUNC_csum_diff;

static int64_t (*bpf_csum_update)(struct __sk_buff* skb, __wsum csum) = (void*)BPF_FUNC_csum_update;

static int (*bpf_skb_change_proto)(struct __sk_buff* skb, __be16 proto,
                                   __u64 flags) = (void*)BPF_FUNC_skb_change_proto;
static int (*bpf_l3_csum_replace)(struct __sk_buff* skb, __u32 offset, __u64 from, __u64 to,
                                  __u64 flags) = (void*)BPF_FUNC_l3_csum_replace;
static int (*bpf_l4_csum_replace)(struct __sk_buff* skb, __u32 offset, __u64 from, __u64 to,
                                  __u64 flags) = (void*)BPF_FUNC_l4_csum_replace;
static int (*bpf_redirect)(__u32 ifindex, __u64 flags) = (void*)BPF_FUNC_redirect;
static int (*bpf_redirect_map)(const struct bpf_map_def* map, __u32 key,
                               __u64 flags) = (void*)BPF_FUNC_redirect_map;

static int (*bpf_skb_change_head)(struct __sk_buff* skb, __u32 head_room,
                                  __u64 flags) = (void*)BPF_FUNC_skb_change_head;
static int (*bpf_skb_adjust_room)(struct __sk_buff* skb, __s32 len_diff, __u32 mode,
                                  __u64 flags) = (void*)BPF_FUNC_skb_adjust_room;

// Android only supports little endian architectures
#define htons(x) (__builtin_constant_p(x) ? ___constant_swab16(x) : __builtin_bswap16(x))
#define htonl(x) (__builtin_constant_p(x) ? ___constant_swab32(x) : __builtin_bswap32(x))
#define ntohs(x) htons(x)
#define ntohl(x) htonl(x)

static inline __always_inline __unused bool is_received_skb(struct __sk_buff* skb) {
    return skb->pkt_type == PACKET_HOST || skb->pkt_type == PACKET_BROADCAST ||
           skb->pkt_type == PACKET_MULTICAST;
}

// try to make the first 'len' header bytes readable/writable via direct packet access
// (note: AFAIK there is no way to ask for only direct packet read without also getting write)
static inline __always_inline void try_make_writable(struct __sk_buff* skb, int len) {
    if (len > skb->len) len = skb->len;
    if (skb->data_end - skb->data < len) bpf_skb_pull_data(skb, len);
}

struct egress_bool { bool egress; };
#define INGRESS ((struct egress_bool){ .egress = false })
#define EGRESS ((struct egress_bool){ .egress = true })

struct stream_bool { bool down; };
#define UPSTREAM ((struct stream_bool){ .down = false })
#define DOWNSTREAM ((struct stream_bool){ .down = true })

struct rawip_bool { bool rawip; };
#define ETHER ((struct rawip_bool){ .rawip = false })
#define RAWIP ((struct rawip_bool){ .rawip = true })

struct updatetime_bool { bool updatetime; };
#define NO_UPDATETIME ((struct updatetime_bool){ .updatetime = false })
#define UPDATETIME ((struct updatetime_bool){ .updatetime = true })
