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

#pragma once

// Accept the full packet
#define BPF_ACCEPT BPF_STMT(BPF_RET | BPF_K, 0xFFFFFFFF)

// Reject the packet
#define BPF_REJECT BPF_STMT(BPF_RET | BPF_K, 0)

// Note arguments to BPF_JUMP(opcode, operand, true_offset, false_offset)

// If not equal, jump over count instructions
#define BPF_JUMP_IF_NOT_EQUAL(v, count) \
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (v), 0, (count))

// *TWO* instructions: compare and if not equal jump over the accept statement
#define BPF2_ACCEPT_IF_EQUAL(v) \
	BPF_JUMP_IF_NOT_EQUAL((v), 1), \
	BPF_ACCEPT

// *TWO* instructions: compare and if equal jump over the reject statement
#define BPF2_REJECT_IF_NOT_EQUAL(v) \
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (v), 1, 0), \
	BPF_REJECT

// *TWO* instructions: compare and if greater or equal jump over the reject statement
#define BPF2_REJECT_IF_LESS_THAN(v) \
	BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, (v), 1, 0), \
	BPF_REJECT

// *TWO* instructions: compare and if *NOT* greater jump over the reject statement
#define BPF2_REJECT_IF_GREATER_THAN(v) \
	BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, (v), 0, 1), \
	BPF_REJECT

// *THREE* instructions: compare and if *NOT* in range [lo, hi], jump over the reject statement
#define BPF3_REJECT_IF_NOT_IN_RANGE(lo, hi) \
	BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, (lo), 0, 1), \
	BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, (hi), 0, 1), \
	BPF_REJECT

// *TWO* instructions: compare and if none of the bits are set jump over the reject statement
#define BPF2_REJECT_IF_ANY_MASKED_BITS_SET(v) \
	BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, (v), 0, 1), \
	BPF_REJECT

// loads skb->protocol
#define BPF_LOAD_SKB_PROTOCOL \
	BPF_STMT(BPF_LD | BPF_H | BPF_ABS, (__u32)SKF_AD_OFF + SKF_AD_PROTOCOL)

// 8-bit load relative to start of link layer (mac/ethernet) header.
#define BPF_LOAD_MAC_RELATIVE_U8(ofs) \
	BPF_STMT(BPF_LD | BPF_B | BPF_ABS, (__u32)SKF_LL_OFF + (ofs))

// Big/Network Endian 16-bit load relative to start of link layer (mac/ethernet) header.
#define BPF_LOAD_MAC_RELATIVE_BE16(ofs) \
	BPF_STMT(BPF_LD | BPF_H | BPF_ABS, (__u32)SKF_LL_OFF + (ofs))

// Big/Network Endian 32-bit load relative to start of link layer (mac/ethernet) header.
#define BPF_LOAD_MAC_RELATIVE_BE32(ofs) \
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, (__u32)SKF_LL_OFF + (ofs))

// 8-bit load relative to start of network (IPv4/IPv6) header.
#define BPF_LOAD_NET_RELATIVE_U8(ofs) \
	BPF_STMT(BPF_LD | BPF_B | BPF_ABS, (__u32)SKF_NET_OFF + (ofs))

// Big/Network Endian 16-bit load relative to start of network (IPv4/IPv6) header.
#define BPF_LOAD_NET_RELATIVE_BE16(ofs) \
	BPF_STMT(BPF_LD | BPF_H | BPF_ABS, (__u32)SKF_NET_OFF + (ofs))

// Big/Network Endian 32-bit load relative to start of network (IPv4/IPv6) header.
#define BPF_LOAD_NET_RELATIVE_BE32(ofs) \
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, (__u32)SKF_NET_OFF + (ofs))

#define field_sizeof(struct_type,field) sizeof(((struct_type *)0)->field)

// 8-bit load from IPv4 header field.
#define BPF_LOAD_IPV4_U8(field) \
	BPF_LOAD_NET_RELATIVE_U8(({ \
	  _Static_assert(field_sizeof(struct iphdr, field) == 1, "field of wrong size"); \
	  offsetof(iphdr, field); \
	}))

// Big/Network Endian 16-bit load from IPv4 header field.
#define BPF_LOAD_IPV4_BE16(field) \
	BPF_LOAD_NET_RELATIVE_BE16(({ \
	  _Static_assert(field_sizeof(struct iphdr, field) == 2, "field of wrong size"); \
	  offsetof(iphdr, field); \
	}))

// Big/Network Endian 32-bit load from IPv4 header field.
#define BPF_LOAD_IPV4_BE32(field) \
	BPF_LOAD_NET_RELATIVE_BE32(({ \
	  _Static_assert(field_sizeof(struct iphdr, field) == 4, "field of wrong size"); \
	  offsetof(iphdr, field); \
	}))

// 8-bit load from IPv6 header field.
#define BPF_LOAD_IPV6_U8(field) \
	BPF_LOAD_NET_RELATIVE_U8(({ \
	  _Static_assert(field_sizeof(struct ipv6hdr, field) == 1, "field of wrong size"); \
	  offsetof(ipv6hdr, field); \
	}))

// Big/Network Endian 16-bit load from IPv6 header field.
#define BPF_LOAD_IPV6_BE16(field) \
	BPF_LOAD_NET_RELATIVE_BE16(({ \
	  _Static_assert(field_sizeof(struct ipv6hdr, field) == 2, "field of wrong size"); \
	  offsetof(ipv6hdr, field); \
	}))

// Big/Network Endian 32-bit load from IPv6 header field.
#define BPF_LOAD_IPV6_BE32(field) \
	BPF_LOAD_NET_RELATIVE_BE32(({ \
	  _Static_assert(field_sizeof(struct ipv6hdr, field) == 4, "field of wrong size"); \
	  offsetof(ipv6hdr, field); \
	}))

// Load the length of the IPv4 header into X index register.
// ie. X := 4 * IPv4.IHL, where IPv4.IHL is the bottom nibble
// of the first byte of the IPv4 (aka network layer) header.
#define BPF_LOADX_NET_RELATIVE_IPV4_HLEN \
    BPF_STMT(BPF_LDX | BPF_B | BPF_MSH, (__u32)SKF_NET_OFF)

// Blindly assumes no IPv6 extension headers, just does X := 40
// You may later adjust this as you parse through IPv6 ext hdrs.
#define BPF_LOADX_CONSTANT_IPV6_HLEN \
    BPF_STMT(BPF_LDX | BPF_W | BPF_IMM, sizeof(struct ipv6hdr))

// NOTE: all the following require X to be setup correctly (v4: 20+, v6: 40+)

// 8-bit load from L4 (TCP/UDP/...) header
#define BPF_LOAD_NETX_RELATIVE_L4_U8(ofs) \
    BPF_STMT(BPF_LD | BPF_B | BPF_IND, (__u32)SKF_NET_OFF + (ofs))

// Big/Network Endian 16-bit load from L4 (TCP/UDP/...) header
#define BPF_LOAD_NETX_RELATIVE_L4_BE16(ofs) \
    BPF_STMT(BPF_LD | BPF_H | BPF_IND, (__u32)SKF_NET_OFF + (ofs))

// Big/Network Endian 32-bit load from L4 (TCP/UDP/...) header
#define BPF_LOAD_NETX_RELATIVE_L4_BE32(ofs) \
    BPF_STMT(BPF_LD | BPF_W | BPF_IND, (__u32)SKF_NET_OFF + (ofs))

// Both ICMPv4 and ICMPv6 start with u8 type, u8 code
#define BPF_LOAD_NETX_RELATIVE_ICMP_TYPE BPF_LOAD_NETX_RELATIVE_L4_U8(0)
#define BPF_LOAD_NETX_RELATIVE_ICMP_CODE BPF_LOAD_NETX_RELATIVE_L4_U8(1)

// IPv6 extension headers (HOPOPTS, DSTOPS, FRAG) begin with a u8 nexthdr
#define BPF_LOAD_NETX_RELATIVE_V6EXTHDR_NEXTHDR BPF_LOAD_NETX_RELATIVE_L4_U8(0)

// IPv6 fragment header is always exactly 8 bytes long
#define BPF_LOAD_CONSTANT_V6FRAGHDR_LEN \
    BPF_STMT(BPF_LD | BPF_IMM, 8)

// HOPOPTS/DSTOPS follow up with 'u8 len', counting 8 byte units, (0->8, 1->16)
// *THREE* instructions
#define BPF3_LOAD_NETX_RELATIVE_V6EXTHDR_LEN \
    BPF_LOAD_NETX_RELATIVE_L4_U8(1), \
    BPF_STMT(BPF_ALU | BPF_ADD | BPF_K, 1), \
    BPF_STMT(BPF_ALU | BPF_LSH | BPF_K, 3)

// *TWO* instructions: A += X; X := A
#define BPF2_ADD_A_TO_X \
    BPF_STMT(BPF_ALU | BPF_ADD | BPF_X, 0), \
    BPF_STMT(BPF_MISC | BPF_TAX, 0)

// UDP/UDPLITE/TCP/SCTP/DCCP all start with be16 srcport, dstport
#define BPF_LOAD_NETX_RELATIVE_SRC_PORT BPF_LOAD_NETX_RELATIVE_L4_BE16(0)
#define BPF_LOAD_NETX_RELATIVE_DST_PORT BPF_LOAD_NETX_RELATIVE_L4_BE16(2)
