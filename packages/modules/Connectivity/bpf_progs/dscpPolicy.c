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

#include <linux/bpf.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/pkt_cls.h>
#include <linux/tcp.h>
#include <linux/types.h>
#include <netinet/in.h>
#include <netinet/udp.h>
#include <stdint.h>
#include <string.h>

// The resulting .o needs to load on the Android T bpfloader
#define BPFLOADER_MIN_VER BPFLOADER_T_VERSION

#include "bpf_helpers.h"
#include "dscpPolicy.h"

#define ECN_MASK 3
#define IP4_OFFSET(field, header) ((header) + offsetof(struct iphdr, field))
#define UPDATE_TOS(dscp, tos) ((dscp) << 2) | ((tos) & ECN_MASK)

DEFINE_BPF_MAP_GRW(socket_policy_cache_map, HASH, uint64_t, RuleEntry, CACHE_MAP_SIZE, AID_SYSTEM)

DEFINE_BPF_MAP_GRW(ipv4_dscp_policies_map, ARRAY, uint32_t, DscpPolicy, MAX_POLICIES, AID_SYSTEM)
DEFINE_BPF_MAP_GRW(ipv6_dscp_policies_map, ARRAY, uint32_t, DscpPolicy, MAX_POLICIES, AID_SYSTEM)

static inline __always_inline void match_policy(struct __sk_buff* skb, bool ipv4) {
    void* data = (void*)(long)skb->data;
    const void* data_end = (void*)(long)skb->data_end;

    const int l2_header_size = sizeof(struct ethhdr);
    struct ethhdr* eth = data;

    if (data + l2_header_size > data_end) return;

    int hdr_size = 0;

    // used for map lookup
    uint64_t cookie = bpf_get_socket_cookie(skb);
    if (!cookie) return;

    __be16 sport = 0;
    uint16_t dport = 0;
    uint8_t protocol = 0;  // TODO: Use are reserved value? Or int (-1) and cast to uint below?
    struct in6_addr src_ip = {};
    struct in6_addr dst_ip = {};
    uint8_t tos = 0;            // Only used for IPv4
    __be32 old_first_be32 = 0;  // Only used for IPv6
    if (ipv4) {
        const struct iphdr* const iph = (void*)(eth + 1);
        hdr_size = l2_header_size + sizeof(struct iphdr);
        // Must have ipv4 header
        if (data + hdr_size > data_end) return;

        // IP version must be 4
        if (iph->version != 4) return;

        // We cannot handle IP options, just standard 20 byte == 5 dword minimal IPv4 header
        if (iph->ihl != 5) return;

        // V4 mapped address in in6_addr sets 10/11 position to 0xff.
        src_ip.s6_addr32[2] = htonl(0x0000ffff);
        dst_ip.s6_addr32[2] = htonl(0x0000ffff);

        // Copy IPv4 address into in6_addr for easy comparison below.
        src_ip.s6_addr32[3] = iph->saddr;
        dst_ip.s6_addr32[3] = iph->daddr;
        protocol = iph->protocol;
        tos = iph->tos;
    } else {
        struct ipv6hdr* ip6h = (void*)(eth + 1);
        hdr_size = l2_header_size + sizeof(struct ipv6hdr);
        // Must have ipv6 header
        if (data + hdr_size > data_end) return;

        if (ip6h->version != 6) return;

        src_ip = ip6h->saddr;
        dst_ip = ip6h->daddr;
        protocol = ip6h->nexthdr;
        old_first_be32 = *(__be32*)ip6h;
    }

    switch (protocol) {
        case IPPROTO_UDP:
        case IPPROTO_UDPLITE: {
            struct udphdr* udp;
            udp = data + hdr_size;
            if ((void*)(udp + 1) > data_end) return;
            sport = udp->source;
            dport = ntohs(udp->dest);
        } break;
        case IPPROTO_TCP: {
            struct tcphdr* tcp;
            tcp = data + hdr_size;
            if ((void*)(tcp + 1) > data_end) return;
            sport = tcp->source;
            dport = ntohs(tcp->dest);
        } break;
        default:
            return;
    }

    RuleEntry* existing_rule = bpf_socket_policy_cache_map_lookup_elem(&cookie);

    if (existing_rule &&
        v6_equal(src_ip, existing_rule->src_ip) &&
        v6_equal(dst_ip, existing_rule->dst_ip) &&
        skb->ifindex == existing_rule->ifindex &&
        sport == existing_rule->src_port &&
        dport == existing_rule->dst_port &&
        protocol == existing_rule->proto) {
        if (existing_rule->dscp_val < 0) return;
        if (ipv4) {
            uint8_t newTos = UPDATE_TOS(existing_rule->dscp_val, tos);
            bpf_l3_csum_replace(skb, IP4_OFFSET(check, l2_header_size), htons(tos), htons(newTos),
                                sizeof(uint16_t));
            bpf_skb_store_bytes(skb, IP4_OFFSET(tos, l2_header_size), &newTos, sizeof(newTos), 0);
        } else {
            __be32 new_first_be32 =
                htonl(ntohl(old_first_be32) & 0xF03FFFFF | (existing_rule->dscp_val << 22));
            bpf_skb_store_bytes(skb, l2_header_size, &new_first_be32, sizeof(__be32),
                BPF_F_RECOMPUTE_CSUM);
        }
        return;
    }

    // Linear scan ipv4_dscp_policies_map since no stored params match skb.
    int best_score = 0;
    int8_t new_dscp = -1;

    for (register uint64_t i = 0; i < MAX_POLICIES; i++) {
        // Using a uint64 in for loop prevents infinite loop during BPF load,
        // but the key is uint32, so convert back.
        uint32_t key = i;

        DscpPolicy* policy;
        if (ipv4) {
            policy = bpf_ipv4_dscp_policies_map_lookup_elem(&key);
        } else {
            policy = bpf_ipv6_dscp_policies_map_lookup_elem(&key);
        }

        // If the policy lookup failed, just continue (this should not ever happen)
        if (!policy) continue;

        // If policy iface index does not match skb, then skip to next policy.
        if (policy->ifindex != skb->ifindex) continue;

        int score = 0;

        if (policy->present_fields & PROTO_MASK_FLAG) {
            if (protocol != policy->proto) continue;
            score += 0xFFFF;
        }
        if (policy->present_fields & SRC_IP_MASK_FLAG) {
            if (v6_not_equal(src_ip, policy->src_ip)) continue;
            score += 0xFFFF;
        }
        if (policy->present_fields & DST_IP_MASK_FLAG) {
            if (v6_not_equal(dst_ip, policy->dst_ip)) continue;
            score += 0xFFFF;
        }
        if (policy->present_fields & SRC_PORT_MASK_FLAG) {
            if (sport != policy->src_port) continue;
            score += 0xFFFF;
        }
        if (dport < policy->dst_port_start) continue;
        if (dport > policy->dst_port_end) continue;
        score += 0xFFFF + policy->dst_port_start - policy->dst_port_end;

        if (score > best_score) {
            best_score = score;
            new_dscp = policy->dscp_val;
        }
    }

    RuleEntry value = {
        .src_ip = src_ip,
        .dst_ip = dst_ip,
        .ifindex = skb->ifindex,
        .src_port = sport,
        .dst_port = dport,
        .proto = protocol,
        .dscp_val = new_dscp,
    };

    // Update cache with found policy.
    bpf_socket_policy_cache_map_update_elem(&cookie, &value, BPF_ANY);

    if (new_dscp < 0) return;

    // Need to store bytes after updating map or program will not load.
    if (ipv4) {
        uint8_t new_tos = UPDATE_TOS(new_dscp, tos);
        bpf_l3_csum_replace(skb, IP4_OFFSET(check, l2_header_size), htons(tos), htons(new_tos), 2);
        bpf_skb_store_bytes(skb, IP4_OFFSET(tos, l2_header_size), &new_tos, sizeof(new_tos), 0);
    } else {
        __be32 new_first_be32 = htonl(ntohl(old_first_be32) & 0xF03FFFFF | (new_dscp << 22));
        bpf_skb_store_bytes(skb, l2_header_size, &new_first_be32, sizeof(__be32),
            BPF_F_RECOMPUTE_CSUM);
    }
    return;
}

DEFINE_BPF_PROG_KVER("schedcls/set_dscp_ether", AID_ROOT, AID_SYSTEM, schedcls_set_dscp_ether,
                     KVER_5_15)
(struct __sk_buff* skb) {
    if (skb->pkt_type != PACKET_HOST) return TC_ACT_PIPE;

    if (skb->protocol == htons(ETH_P_IP)) {
        match_policy(skb, true);
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        match_policy(skb, false);
    }

    // Always return TC_ACT_PIPE
    return TC_ACT_PIPE;
}

LICENSE("Apache 2.0");
CRITICAL("Connectivity");
DISABLE_BTF_ON_USER_BUILDS();
