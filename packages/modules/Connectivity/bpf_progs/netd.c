/*
 * Copyright (C) 2018 The Android Open Source Project
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

// The resulting .o needs to load on the Android T bpfloader
#define BPFLOADER_MIN_VER BPFLOADER_T_VERSION

#include <bpf_helpers.h>
#include <linux/bpf.h>
#include <linux/if.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/in.h>
#include <linux/in6.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/pkt_cls.h>
#include <linux/tcp.h>
#include <stdbool.h>
#include <stdint.h>
#include "bpf_net_helpers.h"
#include "netd.h"

// This is defined for cgroup bpf filter only.
static const int DROP = 0;
static const int PASS = 1;
static const int DROP_UNLESS_DNS = 2;  // internal to our program

// This is used for xt_bpf program only.
static const int BPF_NOMATCH = 0;
static const int BPF_MATCH = 1;

// Used for 'bool enable_tracing'
static const bool TRACE_ON = true;
static const bool TRACE_OFF = false;

// offsetof(struct iphdr, ihl) -- but that's a bitfield
#define IPPROTO_IHL_OFF 0

// This is offsetof(struct tcphdr, "32 bit tcp flag field")
// The tcp flags are after be16 source, dest & be32 seq, ack_seq, hence 12 bytes in.
//
// Note that TCP_FLAG_{ACK,PSH,RST,SYN,FIN} are htonl(0x00{10,08,04,02,01}0000)
// see include/uapi/linux/tcp.h
#define TCP_FLAG32_OFF 12

#define TCP_FLAG8_OFF (TCP_FLAG32_OFF + 1)

// For maps netd does not need to access
#define DEFINE_BPF_MAP_NO_NETD(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries) \
    DEFINE_BPF_MAP_EXT(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries,         \
                       AID_ROOT, AID_NET_BW_ACCT, 0060, "fs_bpf_net_shared", "",   \
                       PRIVATE, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER,              \
                       LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

// For maps netd only needs read only access to
#define DEFINE_BPF_MAP_RO_NETD(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries)  \
    DEFINE_BPF_MAP_EXT(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries,          \
                       AID_ROOT, AID_NET_BW_ACCT, 0460, "fs_bpf_netd_readonly", "", \
                       PRIVATE, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER,               \
                       LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

// For maps netd needs to be able to read and write
#define DEFINE_BPF_MAP_RW_NETD(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, \
                       AID_ROOT, AID_NET_BW_ACCT, 0660)

// Bpf map arrays on creation are preinitialized to 0 and do not support deletion of a key,
// see: kernel/bpf/arraymap.c array_map_delete_elem() returns -EINVAL (from both syscall and ebpf)
// Additionally on newer kernels the bpf jit can optimize out the lookups.
// only valid indexes are [0..CONFIGURATION_MAP_SIZE-1]
DEFINE_BPF_MAP_RO_NETD(configuration_map, ARRAY, uint32_t, uint32_t, CONFIGURATION_MAP_SIZE)

// TODO: consider whether we can merge some of these maps
// for example it might be possible to merge 2 or 3 of:
//   uid_counterset_map + uid_owner_map + uid_permission_map
DEFINE_BPF_MAP_RW_NETD(cookie_tag_map, HASH, uint64_t, UidTagValue, COOKIE_UID_MAP_SIZE)
DEFINE_BPF_MAP_NO_NETD(uid_counterset_map, HASH, uint32_t, uint8_t, UID_COUNTERSET_MAP_SIZE)
DEFINE_BPF_MAP_NO_NETD(app_uid_stats_map, HASH, uint32_t, StatsValue, APP_STATS_MAP_SIZE)
DEFINE_BPF_MAP_RO_NETD(stats_map_A, HASH, StatsKey, StatsValue, STATS_MAP_SIZE)
DEFINE_BPF_MAP_RO_NETD(stats_map_B, HASH, StatsKey, StatsValue, STATS_MAP_SIZE)
DEFINE_BPF_MAP_NO_NETD(iface_stats_map, HASH, uint32_t, StatsValue, IFACE_STATS_MAP_SIZE)
DEFINE_BPF_MAP_RO_NETD(uid_owner_map, HASH, uint32_t, UidOwnerValue, UID_OWNER_MAP_SIZE)
DEFINE_BPF_MAP_RO_NETD(uid_permission_map, HASH, uint32_t, uint8_t, UID_OWNER_MAP_SIZE)
DEFINE_BPF_MAP_NO_NETD(ingress_discard_map, HASH, IngressDiscardKey, IngressDiscardValue,
                       INGRESS_DISCARD_MAP_SIZE)

/* never actually used from ebpf */
DEFINE_BPF_MAP_NO_NETD(iface_index_name_map, HASH, uint32_t, IfaceValue, IFACE_INDEX_NAME_MAP_SIZE)

// A single-element configuration array, packet tracing is enabled when 'true'.
DEFINE_BPF_MAP_EXT(packet_trace_enabled_map, ARRAY, uint32_t, bool, 1,
                   AID_ROOT, AID_SYSTEM, 0060, "fs_bpf_net_shared", "", PRIVATE,
                   BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, LOAD_ON_ENG,
                   LOAD_ON_USER, LOAD_ON_USERDEBUG)

// A ring buffer on which packet information is pushed.
DEFINE_BPF_RINGBUF_EXT(packet_trace_ringbuf, PacketTrace, PACKET_TRACE_BUF_SIZE,
                       AID_ROOT, AID_SYSTEM, 0060, "fs_bpf_net_shared", "", PRIVATE,
                       BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, LOAD_ON_ENG,
                       LOAD_ON_USER, LOAD_ON_USERDEBUG);

DEFINE_BPF_MAP_RO_NETD(data_saver_enabled_map, ARRAY, uint32_t, bool,
                       DATA_SAVER_ENABLED_MAP_SIZE)

// iptables xt_bpf programs need to be usable by both netd and netutils_wrappers
// selinux contexts, because even non-xt_bpf iptables mutations are implemented as
// a full table dump, followed by an update in userspace, and then a reload into the kernel,
// where any already in-use xt_bpf matchers are serialized as the path to the pinned
// program (see XT_BPF_MODE_PATH_PINNED) and then the iptables binary (or rather
// the kernel acting on behalf of it) must be able to retrieve the pinned program
// for the reload to succeed
#define DEFINE_XTBPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog) \
    DEFINE_BPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog)

// programs that need to be usable by netd, but not by netutils_wrappers
// (this is because these are currently attached by the mainline provided libnetd_updatable .so
// which is loaded into netd and thus runs as netd uid/gid/selinux context)
#define DEFINE_NETD_BPF_PROG_KVER_RANGE(SECTION_NAME, prog_uid, prog_gid, the_prog, minKV, maxKV) \
    DEFINE_BPF_PROG_EXT(SECTION_NAME, prog_uid, prog_gid, the_prog,                               \
                        minKV, maxKV, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, MANDATORY,            \
                        "fs_bpf_netd_readonly", "", LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

#define DEFINE_NETD_BPF_PROG_KVER(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv) \
    DEFINE_NETD_BPF_PROG_KVER_RANGE(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, KVER_INF)

#define DEFINE_NETD_BPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog) \
    DEFINE_NETD_BPF_PROG_KVER(SECTION_NAME, prog_uid, prog_gid, the_prog, KVER_NONE)

// programs that only need to be usable by the system server
#define DEFINE_SYS_BPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog) \
    DEFINE_BPF_PROG_EXT(SECTION_NAME, prog_uid, prog_gid, the_prog, KVER_NONE, KVER_INF,  \
                        BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, MANDATORY, \
                        "fs_bpf_net_shared", "", LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

/*
 * Note: this blindly assumes an MTU of 1500, and that packets > MTU are always TCP,
 * and that TCP is using the Linux default settings with TCP timestamp option enabled
 * which uses 12 TCP option bytes per frame.
 *
 * These are not unreasonable assumptions:
 *
 * The internet does not really support MTUs greater than 1500, so most TCP traffic will
 * be at that MTU, or slightly below it (worst case our upwards adjustment is too small).
 *
 * The chance our traffic isn't IP at all is basically zero, so the IP overhead correction
 * is bound to be needed.
 *
 * Furthermore, the likelyhood that we're having to deal with GSO (ie. > MTU) packets that
 * are not IP/TCP is pretty small (few other things are supported by Linux) and worse case
 * our extra overhead will be slightly off, but probably still better than assuming none.
 *
 * Most servers are also Linux and thus support/default to using TCP timestamp option
 * (and indeed TCP timestamp option comes from RFC 1323 titled "TCP Extensions for High
 * Performance" which also defined TCP window scaling and are thus absolutely ancient...).
 *
 * All together this should be more correct than if we simply ignored GSO frames
 * (ie. counted them as single packets with no extra overhead)
 *
 * Especially since the number of packets is important for any future clat offload correction.
 * (which adjusts upward by 20 bytes per packet to account for ipv4 -> ipv6 header conversion)
 */
#define DEFINE_UPDATE_STATS(the_stats_map, TypeOfKey)                                            \
    static __always_inline inline void update_##the_stats_map(const struct __sk_buff* const skb, \
                                                              const TypeOfKey* const key,        \
                                                              const struct egress_bool egress,   \
                                                              const struct kver_uint kver) {     \
        StatsValue* value = bpf_##the_stats_map##_lookup_elem(key);                              \
        if (!value) {                                                                            \
            StatsValue newValue = {};                                                            \
            bpf_##the_stats_map##_update_elem(key, &newValue, BPF_NOEXIST);                      \
            value = bpf_##the_stats_map##_lookup_elem(key);                                      \
        }                                                                                        \
        if (value) {                                                                             \
            const int mtu = 1500;                                                                \
            uint64_t packets = 1;                                                                \
            uint64_t bytes = skb->len;                                                           \
            if (bytes > mtu) {                                                                   \
                bool is_ipv6 = (skb->protocol == htons(ETH_P_IPV6));                             \
                int ip_overhead = (is_ipv6 ? sizeof(struct ipv6hdr) : sizeof(struct iphdr));     \
                int tcp_overhead = ip_overhead + sizeof(struct tcphdr) + 12;                     \
                int mss = mtu - tcp_overhead;                                                    \
                uint64_t payload = bytes - tcp_overhead;                                         \
                packets = (payload + mss - 1) / mss;                                             \
                bytes = tcp_overhead * packets + payload;                                        \
            }                                                                                    \
            if (egress.egress) {                                                                 \
                __sync_fetch_and_add(&value->txPackets, packets);                                \
                __sync_fetch_and_add(&value->txBytes, bytes);                                    \
            } else {                                                                             \
                __sync_fetch_and_add(&value->rxPackets, packets);                                \
                __sync_fetch_and_add(&value->rxBytes, bytes);                                    \
            }                                                                                    \
        }                                                                                        \
    }

DEFINE_UPDATE_STATS(app_uid_stats_map, uint32_t)
DEFINE_UPDATE_STATS(iface_stats_map, uint32_t)
DEFINE_UPDATE_STATS(stats_map_A, StatsKey)
DEFINE_UPDATE_STATS(stats_map_B, StatsKey)

// both of these return 0 on success or -EFAULT on failure (and zero out the buffer)
static __always_inline inline int bpf_skb_load_bytes_net(const struct __sk_buff* const skb,
                                                         const int L3_off,
                                                         void* const to,
                                                         const int len,
                                                         const struct kver_uint kver) {
    // 'kver' (here and throughout) is the compile time guaranteed minimum kernel version,
    // ie. we're building (a version of) the bpf program for kver (or newer!) kernels.
    //
    // 4.19+ kernels support the 'bpf_skb_load_bytes_relative()' bpf helper function,
    // so we can use it.  On pre-4.19 kernels we cannot use the relative load helper,
    // and thus will simply get things wrong if there's any L2 (ethernet) header in the skb.
    //
    // Luckily, for cellular traffic, there likely isn't any, as cell is usually 'rawip'.
    //
    // However, this does mean that wifi (and ethernet) on 4.14 is basically a lost cause:
    // we'll be making decisions based on the *wrong* bytes (fetched from the wrong offset),
    // because the 'L3_off' passed to bpf_skb_load_bytes() should be increased by l2_header_size,
    // which for ethernet is 14 and not 0 like it is for rawip.
    //
    // For similar reasons this will fail with non-offloaded VLAN tags on < 4.19 kernels,
    // since those extend the ethernet header from 14 to 18 bytes.
    return KVER_IS_AT_LEAST(kver, 4, 19, 0)
        ? bpf_skb_load_bytes_relative(skb, L3_off, to, len, BPF_HDR_START_NET)
        : bpf_skb_load_bytes(skb, L3_off, to, len);
}

static __always_inline inline void do_packet_tracing(
        const struct __sk_buff* const skb, const struct egress_bool egress, const uint32_t uid,
        const uint32_t tag, const bool enable_tracing, const struct kver_uint kver) {
    if (!enable_tracing) return;
    if (!KVER_IS_AT_LEAST(kver, 5, 8, 0)) return;

    uint32_t mapKey = 0;
    bool* traceConfig = bpf_packet_trace_enabled_map_lookup_elem(&mapKey);
    if (traceConfig == NULL) return;
    if (*traceConfig == false) return;

    PacketTrace* pkt = bpf_packet_trace_ringbuf_reserve();
    if (pkt == NULL) return;

    // Errors from bpf_skb_load_bytes_net are ignored to favor returning something
    // over returning nothing. In the event of an error, the kernel will fill in
    // zero for the destination memory. Do not change the default '= 0' below.

    uint8_t proto = 0;
    uint8_t L4_off = 0;
    uint8_t ipVersion = 0;
    if (skb->protocol == htons(ETH_P_IP)) {
        (void)bpf_skb_load_bytes_net(skb, IP4_OFFSET(protocol), &proto, sizeof(proto), kver);
        (void)bpf_skb_load_bytes_net(skb, IPPROTO_IHL_OFF, &L4_off, sizeof(L4_off), kver);
        L4_off = (L4_off & 0x0F) * 4;  // IHL calculation.
        ipVersion = 4;
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        (void)bpf_skb_load_bytes_net(skb, IP6_OFFSET(nexthdr), &proto, sizeof(proto), kver);
        L4_off = sizeof(struct ipv6hdr);
        ipVersion = 6;
        // skip over a *single* HOPOPTS or DSTOPTS extension header (if present)
        if (proto == IPPROTO_HOPOPTS || proto == IPPROTO_DSTOPTS) {
            struct {
                uint8_t proto, len;
            } ext_hdr;
            if (!bpf_skb_load_bytes_net(skb, L4_off, &ext_hdr, sizeof(ext_hdr), kver)) {
                proto = ext_hdr.proto;
                L4_off += (ext_hdr.len + 1) * 8;
            }
        }
    }

    uint8_t flags = 0;
    __be16 sport = 0, dport = 0;
    if (L4_off >= 20) {
      switch (proto) {
        case IPPROTO_TCP:
          (void)bpf_skb_load_bytes_net(skb, L4_off + TCP_FLAG8_OFF, &flags, sizeof(flags), kver);
          // fallthrough
        case IPPROTO_DCCP:
        case IPPROTO_UDP:
        case IPPROTO_UDPLITE:
        case IPPROTO_SCTP:
          // all of these L4 protocols start with be16 src & dst port
          (void)bpf_skb_load_bytes_net(skb, L4_off + 0, &sport, sizeof(sport), kver);
          (void)bpf_skb_load_bytes_net(skb, L4_off + 2, &dport, sizeof(dport), kver);
          break;
        case IPPROTO_ICMP:
        case IPPROTO_ICMPV6:
          // Both IPv4 and IPv6 icmp start with u8 type & code, which we store in the bottom
          // (ie. second) byte of sport/dport (which are be16s), the top byte is already zero.
          (void)bpf_skb_load_bytes_net(skb, L4_off + 0, (char *)&sport + 1, 1, kver); //type
          (void)bpf_skb_load_bytes_net(skb, L4_off + 1, (char *)&dport + 1, 1, kver); //code
          break;
      }
    }

    pkt->timestampNs = bpf_ktime_get_boot_ns();
    pkt->ifindex = skb->ifindex;
    pkt->length = skb->len;

    pkt->uid = uid;
    pkt->tag = tag;
    pkt->sport = sport;
    pkt->dport = dport;

    pkt->egress = egress.egress;
    pkt->wakeup = !egress.egress && (skb->mark & 0x80000000);  // Fwmark.ingress_cpu_wakeup
    pkt->ipProto = proto;
    pkt->tcpFlags = flags;
    pkt->ipVersion = ipVersion;

    bpf_packet_trace_ringbuf_submit(pkt);
}

static __always_inline inline bool skip_owner_match(struct __sk_buff* skb,
                                                    const struct egress_bool egress,
                                                    const struct kver_uint kver) {
    uint32_t flag = 0;
    if (skb->protocol == htons(ETH_P_IP)) {
        uint8_t proto;
        // no need to check for success, proto will be zeroed if bpf_skb_load_bytes_net() fails
        (void)bpf_skb_load_bytes_net(skb, IP4_OFFSET(protocol), &proto, sizeof(proto), kver);
        if (proto == IPPROTO_ESP) return true;
        if (proto != IPPROTO_TCP) return false;  // handles read failure above
        uint8_t ihl;
        // we don't check for success, as this cannot fail, as it is earlier in the packet than
        // proto, the reading of which must have succeeded, additionally the next read
        // (a little bit deeper in the packet in spite of ihl being zeroed) of the tcp flags
        // field will also fail, and that failure we already handle correctly
        // (we also don't check that ihl in [0x45,0x4F] nor that ipv4 header checksum is correct)
        (void)bpf_skb_load_bytes_net(skb, IPPROTO_IHL_OFF, &ihl, sizeof(ihl), kver);
        // if the read below fails, we'll just assume no TCP flags are set, which is fine.
        (void)bpf_skb_load_bytes_net(skb, (ihl & 0xF) * 4 + TCP_FLAG32_OFF,
                                     &flag, sizeof(flag), kver);
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        uint8_t proto;
        // no need to check for success, proto will be zeroed if bpf_skb_load_bytes_net() fails
        (void)bpf_skb_load_bytes_net(skb, IP6_OFFSET(nexthdr), &proto, sizeof(proto), kver);
        if (proto == IPPROTO_ESP) return true;
        if (proto != IPPROTO_TCP) return false;  // handles read failure above
        // if the read below fails, we'll just assume no TCP flags are set, which is fine.
        (void)bpf_skb_load_bytes_net(skb, sizeof(struct ipv6hdr) + TCP_FLAG32_OFF,
                                     &flag, sizeof(flag), kver);
    } else {
        return false;
    }
    // Always allow RST's, and additionally allow ingress FINs
    return flag & (TCP_FLAG_RST | (egress.egress ? 0 : TCP_FLAG_FIN));  // false on read failure
}

static __always_inline inline BpfConfig getConfig(uint32_t configKey) {
    uint32_t mapSettingKey = configKey;
    BpfConfig* config = bpf_configuration_map_lookup_elem(&mapSettingKey);
    if (!config) {
        // Couldn't read configuration entry. Assume everything is disabled.
        return DEFAULT_CONFIG;
    }
    return *config;
}

static __always_inline inline bool ingress_should_discard(struct __sk_buff* skb,
                                                          const struct kver_uint kver) {
    // Require 4.19, since earlier kernels don't have bpf_skb_load_bytes_relative() which
    // provides relative to L3 header reads.  Without that we could fetch the wrong bytes.
    // Additionally earlier bpf verifiers are much harder to please.
    if (!KVER_IS_AT_LEAST(kver, 4, 19, 0)) return false;

    IngressDiscardKey k = {};
    if (skb->protocol == htons(ETH_P_IP)) {
        k.daddr.s6_addr32[2] = htonl(0xFFFF);
        (void)bpf_skb_load_bytes_net(skb, IP4_OFFSET(daddr), &k.daddr.s6_addr32[3], 4, kver);
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        (void)bpf_skb_load_bytes_net(skb, IP6_OFFSET(daddr), &k.daddr, sizeof(k.daddr), kver);
    } else {
        return false; // non IPv4/IPv6, so no IP to match on
    }

    // we didn't check for load success, because destination bytes will be zeroed if
    // bpf_skb_load_bytes_net() fails, instead we rely on daddr of '::' and '::ffff:0.0.0.0'
    // never being present in the map itself

    IngressDiscardValue* v = bpf_ingress_discard_map_lookup_elem(&k);
    if (!v) return false;  // lookup failure -> no protection in place -> allow
    // if (skb->ifindex == 1) return false;  // allow 'lo', but can't happen - see callsite
    if (skb->ifindex == v->iif[0]) return false;  // allowed interface
    if (skb->ifindex == v->iif[1]) return false;  // allowed interface
    return true;  // disallowed interface
}

static __always_inline inline int bpf_owner_firewall_match(uint32_t uid) {
    if (is_system_uid(uid)) return PASS;

    const BpfConfig enabledRules = getConfig(UID_RULES_CONFIGURATION_KEY);
    const UidOwnerValue* uidEntry = bpf_uid_owner_map_lookup_elem(&uid);
    const uint32_t uidRules = uidEntry ? uidEntry->rule : 0;

    if (enabledRules & (FIREWALL_DROP_IF_SET | FIREWALL_DROP_IF_UNSET)
            & (uidRules ^ FIREWALL_DROP_IF_UNSET)) {
        return DROP;
    }

    return PASS;
}

static __always_inline inline int bpf_owner_match(struct __sk_buff* skb, uint32_t uid,
                                                  const struct egress_bool egress,
                                                  const struct kver_uint kver) {
    if (is_system_uid(uid)) return PASS;

    if (skip_owner_match(skb, egress, kver)) return PASS;

    BpfConfig enabledRules = getConfig(UID_RULES_CONFIGURATION_KEY);

    UidOwnerValue* uidEntry = bpf_uid_owner_map_lookup_elem(&uid);
    uint32_t uidRules = uidEntry ? uidEntry->rule : 0;
    uint32_t allowed_iif = uidEntry ? uidEntry->iif : 0;

    if (isBlockedByUidRules(enabledRules, uidRules)) return DROP;

    if (!egress.egress && skb->ifindex != 1) {
        if (ingress_should_discard(skb, kver)) return DROP;
        if (uidRules & IIF_MATCH) {
            if (allowed_iif && skb->ifindex != allowed_iif) {
                // Drops packets not coming from lo nor the allowed interface
                // allowed interface=0 is a wildcard and does not drop packets
                return DROP_UNLESS_DNS;
            }
        } else if (uidRules & LOCKDOWN_VPN_MATCH) {
            // Drops packets not coming from lo and rule does not have IIF_MATCH but has
            // LOCKDOWN_VPN_MATCH
            return DROP_UNLESS_DNS;
        }
    }
    return PASS;
}

static __always_inline inline void update_stats_with_config(const uint32_t selectedMap,
                                                            const struct __sk_buff* const skb,
                                                            const StatsKey* const key,
                                                            const struct egress_bool egress,
                                                            const struct kver_uint kver) {
    if (selectedMap == SELECT_MAP_A) {
        update_stats_map_A(skb, key, egress, kver);
    } else {
        update_stats_map_B(skb, key, egress, kver);
    }
}

static __always_inline inline int bpf_traffic_account(struct __sk_buff* skb,
                                                      const struct egress_bool egress,
                                                      const bool enable_tracing,
                                                      const struct kver_uint kver) {
    // sock_uid will be 'overflowuid' if !sk_fullsock(sk_to_full_sk(skb->sk))
    uint32_t sock_uid = bpf_get_socket_uid(skb);

    // kernel's DEFAULT_OVERFLOWUID is 65534, this is the overflow 'nobody' uid,
    // usually this being returned means that skb->sk is NULL during RX
    // (early decap socket lookup failure), which commonly happens for incoming
    // packets to an unconnected udp socket.
    // But it can also happen for egress from a timewait socket.
    // Let's treat such cases as 'root' which is_system_uid()
    if (sock_uid == 65534) sock_uid = 0;

    uint64_t cookie = bpf_get_socket_cookie(skb);  // 0 iff !skb->sk
    UidTagValue* utag = bpf_cookie_tag_map_lookup_elem(&cookie);
    uint32_t uid, tag;
    if (utag) {
        uid = utag->uid;
        tag = utag->tag;
    } else {
        uid = sock_uid;
        tag = 0;
    }

    // Always allow and never count clat traffic. Only the IPv4 traffic on the stacked
    // interface is accounted for and subject to usage restrictions.
    // CLAT IPv6 TX sockets are *always* tagged with CLAT uid, see tagSocketAsClat()
    // CLAT daemon receives via an untagged AF_PACKET socket.
    if (egress.egress && uid == AID_CLAT) return PASS;

    int match = bpf_owner_match(skb, sock_uid, egress, kver);

// Workaround for secureVPN with VpnIsolation enabled, refer to b/159994981 for details.
// Keep TAG_SYSTEM_DNS in sync with DnsResolver/include/netd_resolv/resolv.h
// and TrafficStatsConstants.java
#define TAG_SYSTEM_DNS 0xFFFFFF82
    if (tag == TAG_SYSTEM_DNS && uid == AID_DNS) {
        uid = sock_uid;
        if (match == DROP_UNLESS_DNS) match = PASS;
    } else {
        if (match == DROP_UNLESS_DNS) match = DROP;
    }

    // If an outbound packet is going to be dropped, we do not count that traffic.
    if (egress.egress && (match == DROP)) return DROP;

    StatsKey key = {.uid = uid, .tag = tag, .counterSet = 0, .ifaceIndex = skb->ifindex};

    uint8_t* counterSet = bpf_uid_counterset_map_lookup_elem(&uid);
    if (counterSet) key.counterSet = (uint32_t)*counterSet;

    uint32_t mapSettingKey = CURRENT_STATS_MAP_CONFIGURATION_KEY;
    uint32_t* selectedMap = bpf_configuration_map_lookup_elem(&mapSettingKey);

    if (!selectedMap) return PASS;  // cannot happen, needed to keep bpf verifier happy

    do_packet_tracing(skb, egress, uid, tag, enable_tracing, kver);
    update_stats_with_config(*selectedMap, skb, &key, egress, kver);
    update_app_uid_stats_map(skb, &uid, egress, kver);

    // We've already handled DROP_UNLESS_DNS up above, thus when we reach here the only
    // possible values of match are DROP(0) or PASS(1), however we need to use
    // "match &= 1" before 'return match' to help the kernel's bpf verifier,
    // so that it can be 100% certain that the returned value is always 0 or 1.
    // We use assembly so that it cannot be optimized out by a too smart compiler.
    asm("%0 &= 1" : "+r"(match));
    return match;
}

// This program is optional, and enables tracing on Android U+, 5.8+ on user builds.
DEFINE_BPF_PROG_EXT("cgroupskb/ingress/stats$trace_user", AID_ROOT, AID_SYSTEM,
                    bpf_cgroup_ingress_trace_user, KVER_5_8, KVER_INF,
                    BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, OPTIONAL,
                    "fs_bpf_netd_readonly", "",
                    IGNORE_ON_ENG, LOAD_ON_USER, IGNORE_ON_USERDEBUG)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, INGRESS, TRACE_ON, KVER_5_8);
}

// This program is required, and enables tracing on Android U+, 5.8+, userdebug/eng.
DEFINE_BPF_PROG_EXT("cgroupskb/ingress/stats$trace", AID_ROOT, AID_SYSTEM,
                    bpf_cgroup_ingress_trace, KVER_5_8, KVER_INF,
                    BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, MANDATORY,
                    "fs_bpf_netd_readonly", "",
                    LOAD_ON_ENG, IGNORE_ON_USER, LOAD_ON_USERDEBUG)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, INGRESS, TRACE_ON, KVER_5_8);
}

DEFINE_NETD_BPF_PROG_KVER_RANGE("cgroupskb/ingress/stats$4_19", AID_ROOT, AID_SYSTEM,
                                bpf_cgroup_ingress_4_19, KVER_4_19, KVER_INF)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, INGRESS, TRACE_OFF, KVER_4_19);
}

DEFINE_NETD_BPF_PROG_KVER_RANGE("cgroupskb/ingress/stats$4_14", AID_ROOT, AID_SYSTEM,
                                bpf_cgroup_ingress_4_14, KVER_NONE, KVER_4_19)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, INGRESS, TRACE_OFF, KVER_NONE);
}

// This program is optional, and enables tracing on Android U+, 5.8+ on user builds.
DEFINE_BPF_PROG_EXT("cgroupskb/egress/stats$trace_user", AID_ROOT, AID_SYSTEM,
                    bpf_cgroup_egress_trace_user, KVER_5_8, KVER_INF,
                    BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, OPTIONAL,
                    "fs_bpf_netd_readonly", "",
                    IGNORE_ON_ENG, LOAD_ON_USER, IGNORE_ON_USERDEBUG)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, EGRESS, TRACE_ON, KVER_5_8);
}

// This program is required, and enables tracing on Android U+, 5.8+, userdebug/eng.
DEFINE_BPF_PROG_EXT("cgroupskb/egress/stats$trace", AID_ROOT, AID_SYSTEM,
                    bpf_cgroup_egress_trace, KVER_5_8, KVER_INF,
                    BPFLOADER_IGNORED_ON_VERSION, BPFLOADER_MAX_VER, MANDATORY,
                    "fs_bpf_netd_readonly", "",
                    LOAD_ON_ENG, IGNORE_ON_USER, LOAD_ON_USERDEBUG)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, EGRESS, TRACE_ON, KVER_5_8);
}

DEFINE_NETD_BPF_PROG_KVER_RANGE("cgroupskb/egress/stats$4_19", AID_ROOT, AID_SYSTEM,
                                bpf_cgroup_egress_4_19, KVER_4_19, KVER_INF)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, EGRESS, TRACE_OFF, KVER_4_19);
}

DEFINE_NETD_BPF_PROG_KVER_RANGE("cgroupskb/egress/stats$4_14", AID_ROOT, AID_SYSTEM,
                                bpf_cgroup_egress_4_14, KVER_NONE, KVER_4_19)
(struct __sk_buff* skb) {
    return bpf_traffic_account(skb, EGRESS, TRACE_OFF, KVER_NONE);
}

// WARNING: Android T's non-updatable netd depends on the name of this program.
DEFINE_XTBPF_PROG("skfilter/egress/xtbpf", AID_ROOT, AID_NET_ADMIN, xt_bpf_egress_prog)
(struct __sk_buff* skb) {
    // Clat daemon does not generate new traffic, all its traffic is accounted for already
    // on the v4-* interfaces (except for the 20 (or 28) extra bytes of IPv6 vs IPv4 overhead,
    // but that can be corrected for later when merging v4-foo stats into interface foo's).
    // CLAT sockets are created by system server and tagged as uid CLAT, see tagSocketAsClat()
    uint32_t sock_uid = bpf_get_socket_uid(skb);
    if (sock_uid == AID_SYSTEM) {
        uint64_t cookie = bpf_get_socket_cookie(skb);
        UidTagValue* utag = bpf_cookie_tag_map_lookup_elem(&cookie);
        if (utag && utag->uid == AID_CLAT) return BPF_NOMATCH;
    }

    uint32_t key = skb->ifindex;
    update_iface_stats_map(skb, &key, EGRESS, KVER_NONE);
    return BPF_MATCH;
}

// WARNING: Android T's non-updatable netd depends on the name of this program.
DEFINE_XTBPF_PROG("skfilter/ingress/xtbpf", AID_ROOT, AID_NET_ADMIN, xt_bpf_ingress_prog)
(struct __sk_buff* skb) {
    // Clat daemon traffic is not accounted by virtue of iptables raw prerouting drop rule
    // (in clat_raw_PREROUTING chain), which triggers before this (in bw_raw_PREROUTING chain).
    // It will be accounted for on the v4-* clat interface instead.
    // Keep that in mind when moving this out of iptables xt_bpf and into tc ingress (or xdp).

    uint32_t key = skb->ifindex;
    update_iface_stats_map(skb, &key, INGRESS, KVER_NONE);
    return BPF_MATCH;
}

DEFINE_SYS_BPF_PROG("schedact/ingress/account", AID_ROOT, AID_NET_ADMIN,
                    tc_bpf_ingress_account_prog)
(struct __sk_buff* skb) {
    if (is_received_skb(skb)) {
        // Account for ingress traffic before tc drops it.
        uint32_t key = skb->ifindex;
        update_iface_stats_map(skb, &key, INGRESS, KVER_NONE);
    }
    return TC_ACT_UNSPEC;
}

// WARNING: Android T's non-updatable netd depends on the name of this program.
DEFINE_XTBPF_PROG("skfilter/allowlist/xtbpf", AID_ROOT, AID_NET_ADMIN, xt_bpf_allowlist_prog)
(struct __sk_buff* skb) {
    uint32_t sock_uid = bpf_get_socket_uid(skb);
    if (is_system_uid(sock_uid)) return BPF_MATCH;

    // kernel's DEFAULT_OVERFLOWUID is 65534, this is the overflow 'nobody' uid,
    // usually this being returned means that skb->sk is NULL during RX
    // (early decap socket lookup failure), which commonly happens for incoming
    // packets to an unconnected udp socket.
    // But it can also happen for egress from a timewait socket.
    // Let's treat such cases as 'root' which is_system_uid()
    if (sock_uid == 65534) return BPF_MATCH;

    UidOwnerValue* allowlistMatch = bpf_uid_owner_map_lookup_elem(&sock_uid);
    if (allowlistMatch) return allowlistMatch->rule & HAPPY_BOX_MATCH ? BPF_MATCH : BPF_NOMATCH;
    return BPF_NOMATCH;
}

// WARNING: Android T's non-updatable netd depends on the name of this program.
DEFINE_XTBPF_PROG("skfilter/denylist/xtbpf", AID_ROOT, AID_NET_ADMIN, xt_bpf_denylist_prog)
(struct __sk_buff* skb) {
    uint32_t sock_uid = bpf_get_socket_uid(skb);
    UidOwnerValue* denylistMatch = bpf_uid_owner_map_lookup_elem(&sock_uid);
    if (denylistMatch) return denylistMatch->rule & PENALTY_BOX_MATCH ? BPF_MATCH : BPF_NOMATCH;
    return BPF_NOMATCH;
}

static __always_inline inline uint8_t get_app_permissions(uint32_t uid) {
    /*
     * A given app is guaranteed to have the same app ID in all the profiles in
     * which it is installed, and install permission is granted to app for all
     * user at install time so we only check the appId part of a request uid at
     * run time. See UserHandle#isSameApp for detail.
     */
    uint32_t appId = uid % AID_USER_OFFSET;  // == PER_USER_RANGE == 100000
    uint8_t* permissions = bpf_uid_permission_map_lookup_elem(&appId);
    // if UID not in map, then default to just INTERNET permission.
    return permissions ? *permissions : BPF_PERMISSION_INTERNET;
}

DEFINE_NETD_BPF_PROG_KVER("cgroupsock/inet/create", AID_ROOT, AID_ROOT, inet_socket_create,
                          KVER_4_14)
(struct bpf_sock* sk) {
    uint64_t uid = bpf_get_current_uid_gid() & 0xffffffff;
    // A return value of 1 means allow, everything else means deny.
    if (get_app_permissions(uid) & BPF_PERMISSION_INTERNET) {
      return bpf_owner_firewall_match(uid) == PASS ? 1 : 0;
    } else {
      return 0;
    }
}

LICENSE("Apache 2.0");
CRITICAL("Connectivity and netd");
DISABLE_BTF_ON_USER_BUILDS();
