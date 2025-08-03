/*
 * Copyright 2024, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "apf_interpreter.h"

#include <string.h>  /* For memcmp, memcpy, memset */

#if __GNUC__ >= 7 || __clang__
#define FALLTHROUGH __attribute__((fallthrough))
#else
#define FALLTHROUGH
#endif

typedef enum { False, True } Boolean;

/* Begin include of apf_defs.h */
typedef int8_t s8;
typedef int16_t s16;
typedef int32_t s32;

typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;

typedef enum {
  error_program = -2,
  error_packet = -1,
  nomatch = False,
  match = True,
} match_result_type;

#define ETH_P_IP	0x0800
#define ETH_P_IPV6	0x86DD

#ifndef IPPROTO_ICMP
#define IPPROTO_ICMP	1
#endif

#ifndef IPPROTO_TCP
#define IPPROTO_TCP	6
#endif

#ifndef IPPROTO_UDP
#define IPPROTO_UDP	17
#endif

#ifndef IPPROTO_ICMPV6
#define IPPROTO_ICMPV6	58
#endif

#define ETH_HLEN	14
#define IPV4_HLEN	20
#define IPV6_HLEN	40
#define TCP_HLEN	20
#define UDP_HLEN	8

#define FUNC(x) x; x
/* End include of apf_defs.h */
/* Begin include of apf.h */
/*
 * Copyright 2024, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_APF_APF_H
#define ANDROID_APF_APF_H

/* A brief overview of APF:
 *
 * APF machine is composed of:
 *  1. A read-only program consisting of bytecodes as described below.
 *  2. Two 32-bit registers, called R0 and R1.
 *  3. Sixteen 32-bit temporary memory slots (cleared between packets).
 *  4. A read-only packet.
 *  5. An optional read-write transmit buffer.
 * The program is executed by the interpreter below and parses the packet
 * to determine if the application processor (AP) should be woken up to
 * handle the packet or if it can be dropped.  The program may also choose
 * to allocate/transmit/deallocate the transmit buffer.
 *
 * APF bytecode description:
 *
 * The APF interpreter uses big-endian byte order for loads from the packet
 * and for storing immediates in instructions.
 *
 * Each instruction starts with a byte composed of:
 *  Top 5 bits form "opcode" field, see *_OPCODE defines below.
 *  Next 2 bits form "size field", which indicates the length of an immediate
 *  value which follows the first byte.  Values in this field:
 *                 0 => immediate value is 0 and no bytes follow.
 *                 1 => immediate value is 1 byte big.
 *                 2 => immediate value is 2 bytes big.
 *                 3 => immediate value is 4 bytes big.
 *  Bottom bit forms "register" field, which (usually) indicates which register
 *  this instruction operates on.
 *
 *  There are four main categories of instructions:
 *  Load instructions
 *    These instructions load byte(s) of the packet into a register.
 *    They load either 1, 2 or 4 bytes, as determined by the "opcode" field.
 *    They load into the register specified by the "register" field.
 *    The immediate value that follows the first byte of the instruction is
 *    the byte offset from the beginning of the packet to load from.
 *    There are "indexing" loads which add the value in R1 to the byte offset
 *    to load from. The "opcode" field determines which loads are "indexing".
 *  Arithmetic instructions
 *    These instructions perform simple operations, like addition, on register
 *    values. The result of these instructions is always written into R0. One
 *    argument of the arithmetic operation is R0's value. The other argument
 *    of the arithmetic operation is determined by the "register" field:
 *            If the "register" field is 0 then the immediate value following
 *            the first byte of the instruction is used as the other argument
 *            to the arithmetic operation.
 *            If the "register" field is 1 then R1's value is used as the other
 *            argument to the arithmetic operation.
 *  Conditional jump instructions
 *    These instructions compare register R0's value with another value, and if
 *    the comparison succeeds, jump (i.e. adjust the program counter). The
 *    immediate value that follows the first byte of the instruction
 *    represents the jump target offset, i.e. the value added to the program
 *    counter if the comparison succeeds. The other value compared is
 *    determined by the "register" field:
 *            If the "register" field is 0 then another immediate value
 *            follows the jump target offset. This immediate value is of the
 *            same size as the jump target offset, and represents the value
 *            to compare against.
 *            If the "register" field is 1 then register R1's value is
 *            compared against.
 *    The type of comparison (e.g. equal to, greater than etc) is determined
 *    by the "opcode" field. The comparison interprets both values being
 *    compared as unsigned values.
 *  Miscellaneous instructions
 *    Instructions for:
 *      - allocating/transmitting/deallocating transmit buffer
 *      - building the transmit packet (copying bytes into it)
 *      - read/writing data section
 *
 *  Miscellaneous details:
 *
 *  Pre-filled temporary memory slot values
 *    When the APF program begins execution, six of the sixteen memory slots
 *    are pre-filled by the interpreter with values that may be useful for
 *    programs:
 *      #0 to #8 are zero initialized.
 *      Slot #9  this is slot #15 with greater resolution (1/16384ths of a second)
 *      Slot #10 starts at zero, implicitly used as tx buffer output pointer.
 *      Slot #11 contains the size (in bytes) of the APF program.
 *      Slot #12 contains the total size of the APF program + data.
 *      Slot #13 is filled with the IPv4 header length. This value is calculated
 *               by loading the first byte of the IPv4 header and taking the
 *               bottom 4 bits and multiplying their value by 4. This value is
 *               set to zero if the first 4 bits after the link layer header are
 *               not 4, indicating not IPv4.
 *      Slot #14 is filled with size of the packet in bytes, including the
 *               ethernet link-layer header.
 *      Slot #15 is filled with the filter age in seconds. This is the number of
 *               seconds since the host installed the program. This may
 *               be used by filters that should have a particular lifetime. For
 *               example, it can be used to rate-limit particular packets to one
 *               every N seconds.
 *  Special jump targets:
 *    When an APF program executes a jump to the byte immediately after the last
 *      byte of the progam (i.e., one byte past the end of the program), this
 *      signals the program has completed and determined the packet should be
 *      passed to the AP.
 *    When an APF program executes a jump two bytes past the end of the program,
 *      this signals the program has completed and determined the packet should
 *      be dropped.
 *  Jump if byte sequence doesn't match:
 *    This is a special instruction to facilitate matching long sequences of
 *    bytes in the packet. Initially it is encoded like a conditional jump
 *    instruction with two exceptions:
 *      The first byte of the instruction is always followed by two immediate
 *        fields: The first immediate field is the jump target offset like other
 *        conditional jump instructions. The second immediate field specifies the
 *        number of bytes to compare.
 *      These two immediate fields are followed by a sequence of bytes. These
 *        bytes are compared with the bytes in the packet starting from the
 *        position specified by the value of the register specified by the
 *        "register" field of the instruction.
 */

/* Number of temporary memory slots, see ldm/stm instructions. */
#define MEMORY_ITEMS 16
/* Upon program execution, some temporary memory slots are prefilled: */

typedef union {
  struct {
    u32 pad[9];               /* 0..8 */
    u32 filter_age_16384ths;  /* 9:  Age since filter installed in 1/16384 seconds. */
    u32 tx_buf_offset;        /* 10: Offset in tx_buf where next byte will be written */
    u32 program_size;         /* 11: Size of program (in bytes) */
    u32 ram_len;              /* 12: Total size of program + data, ie. ram_len */
    u32 ipv4_header_size;     /* 13: 4*([APF_FRAME_HEADER_SIZE]&15) */
    u32 packet_size;          /* 14: Size of packet in bytes. */
    u32 filter_age;           /* 15: Age since filter installed in seconds. */
  } named;
  u32 slot[MEMORY_ITEMS];
} memory_type;

/* ---------------------------------------------------------------------------------------------- */

/* Standard opcodes. */

/* Unconditionally pass (if R=0) or drop (if R=1) packet and optionally increment counter.
 * An optional non-zero unsigned immediate value can be provided to encode the counter number.
 * The counter is located (-4 * counter number) bytes from the end of the data region.
 * It is a U32 big-endian value and is always incremented by 1.
 * This is more or less equivalent to: lddw R0, -4*N; add R0, 1; stdw R0, -4*N; {pass,drop}
 * e.g. "pass", "pass 1", "drop", "drop 1"
 */
#define PASSDROP_OPCODE 0

#define LDB_OPCODE 1    /* Load 1 byte  from immediate offset, e.g. "ldb R0, [5]" */
#define LDH_OPCODE 2    /* Load 2 bytes from immediate offset, e.g. "ldh R0, [5]" */
#define LDW_OPCODE 3    /* Load 4 bytes from immediate offset, e.g. "ldw R0, [5]" */
#define LDBX_OPCODE 4   /* Load 1 byte  from immediate offset plus register, e.g. "ldbx R0, [5+R0]" */
#define LDHX_OPCODE 5   /* Load 2 bytes from immediate offset plus register, e.g. "ldhx R0, [5+R0]" */
#define LDWX_OPCODE 6   /* Load 4 bytes from immediate offset plus register, e.g. "ldwx R0, [5+R0]" */
#define ADD_OPCODE 7    /* Add, e.g. "add R0,5" */
#define MUL_OPCODE 8    /* Multiply, e.g. "mul R0,5" */
#define DIV_OPCODE 9    /* Divide, e.g. "div R0,5" */
#define AND_OPCODE 10   /* And, e.g. "and R0,5" */
#define OR_OPCODE 11    /* Or, e.g. "or R0,5" */
#define SH_OPCODE 12    /* Left shift, e.g. "sh R0, 5" or "sh R0, -5" (shifts right) */
#define LI_OPCODE 13    /* Load signed immediate, e.g. "li R0,5" */
#define JMP_OPCODE 14   /* Unconditional jump, e.g. "jmp label" */
#define JEQ_OPCODE 15   /* Compare equal and branch, e.g. "jeq R0,5,label" */
#define JNE_OPCODE 16   /* Compare not equal and branch, e.g. "jne R0,5,label" */
#define JGT_OPCODE 17   /* Compare greater than and branch, e.g. "jgt R0,5,label" */
#define JLT_OPCODE 18   /* Compare less than and branch, e.g. "jlt R0,5,label" */
#define JSET_OPCODE 19  /* Compare any bits set and branch, e.g. "jset R0,5,label" */
#define JBSMATCH_OPCODE 20 /* Compare byte sequence [R=0 not] equal, e.g. "jbsne R0,2,label,0x1122" */
#define EXT_OPCODE 21   /* Immediate value is one of *_EXT_OPCODE */
#define LDDW_OPCODE 22  /* Load 4 bytes from data address (register + signed imm): "lddw R0, [5+R1]" */
#define STDW_OPCODE 23  /* Store 4 bytes to data address (register + signed imm): "stdw R0, [5+R1]" */

/* Write 1, 2 or 4 byte immediate to the output buffer and auto-increment the output buffer pointer.
 * Immediate length field specifies size of write.  R must be 0.  imm_len != 0.
 * e.g. "write 5"
 */
#define WRITE_OPCODE 24

/* Copy bytes from input packet/APF program/data region to output buffer and
 * auto-increment the output buffer pointer.
 * Register bit is used to specify the source of data copy.
 * R=0 means copy from packet.
 * R=1 means copy from APF program/data region.
 * The source offset is stored in imm1, copy length is stored in u8 imm2.
 * e.g. "pktcopy 0, 16" or "datacopy 0, 16"
 */
#define PKTDATACOPY_OPCODE 25

/* ---------------------------------------------------------------------------------------------- */

/* Extended opcodes. */
/* These all have an opcode of EXT_OPCODE and specify the actual opcode in the immediate field. */

#define LDM_EXT_OPCODE 0   /* Load from temporary memory, e.g. "ldm R0,5" */
  /* Values 0-15 represent loading the different temporary memory slots. */
#define STM_EXT_OPCODE 16  /* Store to temporary memory, e.g. "stm R0,5" */
  /* Values 16-31 represent storing to the different temporary memory slots. */
#define NOT_EXT_OPCODE 32  /* Not, e.g. "not R0" */
#define NEG_EXT_OPCODE 33  /* Negate, e.g. "neg R0" */
#define SWAP_EXT_OPCODE 34 /* Swap, e.g. "swap R0,R1" */
#define MOV_EXT_OPCODE 35  /* Move, e.g. "move R0,R1" */

/* Allocate writable output buffer.
 * R=0: register R0 specifies the length
 * R=1: length provided in u16 imm2
 * e.g. "allocate R0" or "allocate 123"
 * On failure automatically executes 'pass 3'
 */
#define ALLOCATE_EXT_OPCODE 36
/* Transmit and deallocate the buffer (transmission can be delayed until the program
 * terminates).  Length of buffer is the output buffer pointer (0 means discard).
 * R=1 iff udp style L4 checksum
 * u8 imm2 - ip header offset from start of buffer (255 for non-ip packets)
 * u8 imm3 - offset from start of buffer to store L4 checksum (255 for no L4 checksum)
 * u8 imm4 - offset from start of buffer to begin L4 checksum calculation (present iff imm3 != 255)
 * u16 imm5 - partial checksum value to include in L4 checksum (present iff imm3 != 255)
 * "e.g. transmit"
 */
#define TRANSMIT_EXT_OPCODE 37
/* Write 1, 2 or 4 byte value from register to the output buffer and auto-increment the
 * output buffer pointer.
 * e.g. "ewrite1 r0" or "ewrite2 r1"
 */
#define EWRITE1_EXT_OPCODE 38
#define EWRITE2_EXT_OPCODE 39
#define EWRITE4_EXT_OPCODE 40

/* Copy bytes from input packet/APF program/data region to output buffer and
 * auto-increment the output buffer pointer.
 * Register bit is used to specify the source of data copy.
 * R=0 means copy from packet.
 * R=1 means copy from APF program/data region.
 * The source offset is stored in R0, copy length is stored in u8 imm2 or R1.
 * e.g. "epktcopy r0, 16", "edatacopy r0, 16", "epktcopy r0, r1", "edatacopy r0, r1"
 */
#define EPKTDATACOPYIMM_EXT_OPCODE 41
#define EPKTDATACOPYR1_EXT_OPCODE 42
/* Jumps if the UDP payload content (starting at R0) does [not] match one
 * of the specified QNAMEs in question records, applying case insensitivity.
 * SAFE version PASSES corrupt packets, while the other one DROPS.
 * R=0/1 meaning 'does not match'/'matches'
 * R0: Offset to UDP payload content
 * imm1: Extended opcode
 * imm2: Jump label offset
 * imm3(u8): Question type (PTR/SRV/TXT/A/AAAA)
 * imm4(bytes): null terminated list of null terminated LV-encoded QNAMEs
 * e.g.: "jdnsqeq R0,label,0xc,\002aa\005local\0\0", "jdnsqne R0,label,0xc,\002aa\005local\0\0"
 */
#define JDNSQMATCH_EXT_OPCODE 43
#define JDNSQMATCHSAFE_EXT_OPCODE 45
/* Jumps if the UDP payload content (starting at R0) does [not] match one
 * of the specified NAMEs in answers/authority/additional records, applying
 * case insensitivity.
 * SAFE version PASSES corrupt packets, while the other one DROPS.
 * R=0/1 meaning 'does not match'/'matches'
 * R0: Offset to UDP payload content
 * imm1: Extended opcode
 * imm2: Jump label offset
 * imm3(bytes): null terminated list of null terminated LV-encoded NAMEs
 * e.g.: "jdnsaeq R0,label,0xc,\002aa\005local\0\0", "jdnsane R0,label,0xc,\002aa\005local\0\0"
 */
#define JDNSAMATCH_EXT_OPCODE 44
#define JDNSAMATCHSAFE_EXT_OPCODE 46

/* This extended opcode is used to implement PKTDATACOPY_OPCODE */
#define PKTDATACOPYIMM_EXT_OPCODE 65536

#define EXTRACT_OPCODE(i) (((i) >> 3) & 31)
#define EXTRACT_REGISTER(i) ((i) & 1)
#define EXTRACT_IMM_LENGTH(i) (((i) >> 1) & 3)

#endif  /* ANDROID_APF_APF_H */
/* End include of apf.h */
/* Begin include of apf_utils.h */
static u32 read_be16(const u8* buf) {
    return buf[0] * 256u + buf[1];
}

static void store_be16(u8* const buf, const u16 v) {
    buf[0] = (u8)(v >> 8);
    buf[1] = (u8)v;
}

static u8 uppercase(u8 c) {
    return (c >= 'a') && (c <= 'z') ? c - ('a' - 'A') : c;
}
/* End include of apf_utils.h */
/* Begin include of apf_dns.h */
/**
 * Compares a (Q)NAME starting at udp[*ofs] with the target name.
 *
 * @param needle - non-NULL - pointer to DNS encoded target name to match against.
 *   example: [11]_googlecast[4]_tcp[5]local[0]  (where [11] is a byte with value 11)
 * @param needle_bound - non-NULL - points at first invalid byte past needle.
 * @param udp - non-NULL - pointer to the start of the UDP payload (DNS header).
 * @param udp_len - length of the UDP payload.
 * @param ofs - non-NULL - pointer to the offset of the beginning of the (Q)NAME.
 *   On non-error return will be updated to point to the first unread offset,
 *   ie. the next position after the (Q)NAME.
 *
 * @return 1 if matched, 0 if not matched, -1 if error in packet, -2 if error in program.
 */
FUNC(match_result_type apf_internal_match_single_name(const u8* needle,
                                    const u8* const needle_bound,
                                    const u8* const udp,
                                    const u32 udp_len,
                                    u32* const ofs)) {
    u32 first_unread_offset = *ofs;
    Boolean is_qname_match = True;
    int lvl;

    /* DNS names are <= 255 characters including terminating 0, since >= 1 char + '.' per level => max. 127 levels */
    for (lvl = 1; lvl <= 127; ++lvl) {
        if (*ofs >= udp_len) return error_packet;
        u8 v = udp[(*ofs)++];
        if (v >= 0xC0) { /* RFC 1035 4.1.4 - handle message compression */
            if (*ofs >= udp_len) return error_packet;
            u8 w = udp[(*ofs)++];
            if (*ofs > first_unread_offset) first_unread_offset = *ofs;
            u32 new_ofs = (v - 0xC0) * 256 + w;
            if (new_ofs >= *ofs) return error_packet;  /* RFC 1035 4.1.4 allows only backward pointers */
            *ofs = new_ofs;
        } else if (v > 63) {
            return error_packet;  /* RFC 1035 2.3.4 - label size is 1..63. */
        } else if (v) {
            u8 label_size = v;
            if (*ofs + label_size > udp_len) return error_packet;
            if (needle >= needle_bound) return error_program;
            if (is_qname_match) {
                u8 len = *needle++;
                if (len == label_size) {
                    if (needle + label_size > needle_bound) return error_program;
                    while (label_size--) {
                        u8 w = udp[(*ofs)++];
                        is_qname_match &= (uppercase(w) == *needle++);
                    }
                } else {
                    if (len != 0xFF) is_qname_match = False;
                    *ofs += label_size;
                }
            } else {
                is_qname_match = False;
                *ofs += label_size;
            }
        } else { /* reached the end of the name */
            if (first_unread_offset > *ofs) *ofs = first_unread_offset;
            return (is_qname_match && *needle == 0) ? match : nomatch;
        }
    }
    return error_packet;  /* too many dns domain name levels */
}

/**
 * Check if DNS packet contains any of the target names with the provided
 * question_type.
 *
 * @param needles - non-NULL - pointer to DNS encoded target nameS to match against.
 *   example: [3]foo[3]com[0][3]bar[3]net[0][0]  -- note ends with an extra NULL byte.
 * @param needle_bound - non-NULL - points at first invalid byte past needles.
 * @param udp - non-NULL - pointer to the start of the UDP payload (DNS header).
 * @param udp_len - length of the UDP payload.
 * @param question_type - question type to match against or -1 to match answers.
 *
 * @return 1 if matched, 0 if not matched, -1 if error in packet, -2 if error in program.
 */
FUNC(match_result_type apf_internal_match_names(const u8* needles,
                              const u8* const needle_bound,
                              const u8* const udp,
                              const u32 udp_len,
                              const int question_type)) {
    if (udp_len < 12) return error_packet;  /* lack of dns header */

    /* dns header: be16 tid, flags, num_{questions,answers,authority,additional} */
    u32 num_questions = read_be16(udp + 4);
    u32 num_answers = read_be16(udp + 6) + read_be16(udp + 8) + read_be16(udp + 10);

    /* loop until we hit final needle, which is a null byte */
    while (True) {
        if (needles >= needle_bound) return error_program;
        if (!*needles) return nomatch;  /* we've run out of needles without finding a match */
        u32 ofs = 12;  /* dns header is 12 bytes */
        u32 i;
        /* match questions */
        for (i = 0; i < num_questions; ++i) {
            match_result_type m = apf_internal_match_single_name(needles, needle_bound, udp, udp_len, &ofs);
            if (m < nomatch) return m;
            if (ofs + 2 > udp_len) return error_packet;
            int qtype = (int)read_be16(udp + ofs);
            ofs += 4; /* skip be16 qtype & qclass */
            if (question_type == -1) continue;
            if (m == nomatch) continue;
            if (qtype == 0xFF /* QTYPE_ANY */ || qtype == question_type) return match;
        }
        /* match answers */
        if (question_type == -1) for (i = 0; i < num_answers; ++i) {
            match_result_type m = apf_internal_match_single_name(needles, needle_bound, udp, udp_len, &ofs);
            if (m < nomatch) return m;
            ofs += 8; /* skip be16 type, class & be32 ttl */
            if (ofs + 2 > udp_len) return error_packet;
            ofs += 2 + read_be16(udp + ofs);  /* skip be16 rdata length field, plus length bytes */
            if (m == match) return match;
        }
        /* move needles pointer to the next needle. */
        do {
            u8 len = *needles++;
            if (len == 0xFF) continue;
            if (len > 63) return error_program;
            needles += len;
            if (needles >= needle_bound) return error_program;
        } while (*needles);
        needles++;  /* skip the NULL byte at the end of *a* DNS name */
    }
}
/* End include of apf_dns.h */
/* Begin include of apf_checksum.h */
/**
 * Calculate big endian 16-bit sum of a buffer (max 128kB),
 * then fold and negate it, producing a 16-bit result in [0..FFFE].
 */
FUNC(u16 apf_internal_calc_csum(u32 sum, const u8* const buf, const s32 len)) {
    s32 i;
    for (i = 0; i < len; ++i) sum += buf[i] * ((i & 1) ? 1 : 256);

    sum = (sum & 0xFFFF) + (sum >> 16);  /* max after this is 1FFFE */
    u16 csum = sum + (sum >> 16);
    return ~csum;  /* assuming sum > 0 on input, this is in [0..FFFE] */
}

static u16 fix_udp_csum(u16 csum) {
    return csum ? csum : 0xFFFF;
}

/**
 * Calculate and store packet checksums and return dscp.
 *
 * @param pkt - pointer to the very start of the to-be-transmitted packet,
 *              ie. the start of the ethernet header (if one is present)
 *     WARNING: at minimum 266 bytes of buffer pointed to by 'pkt' pointer
 *              *MUST* be writable.
 * (IPv4 header checksum is a 2 byte value, 10 bytes after ip_ofs,
 * which has a maximum value of 254.  Thus 254[ip_ofs] + 10 + 2[u16] = 266)
 *
 * @param len - length of the packet (this may be < 266).
 * @param ip_ofs - offset from beginning of pkt to IPv4 or IPv6 header:
 *                 IP version detected based on top nibble of this byte,
 *                 for IPv4 we will calculate and store IP header checksum,
 *                 but only for the first 20 bytes of the header,
 *                 prior to calling this the IPv4 header checksum field
 *                 must be initialized to the partial checksum of the IPv4
 *                 options (0 if none)
 *                 255 means there is no IP header (for example ARP)
 *                 DSCP will be retrieved from this IP header (0 if none).
 * @param partial_csum - additional value to include in L4 checksum
 * @param csum_start - offset from beginning of pkt to begin L4 checksum
 *                     calculation (until end of pkt specified by len)
 * @param csum_ofs - offset from beginning of pkt to store L4 checksum
 *                   255 means do not calculate/store L4 checksum
 * @param udp - True iff we should generate a UDP style L4 checksum (0 -> 0xFFFF)
 *
 * @return 6-bit DSCP value [0..63], garbage on parse error.
 */
FUNC(int apf_internal_csum_and_return_dscp(u8* const pkt, const s32 len, const u8 ip_ofs,
  const u16 partial_csum, const u8 csum_start, const u8 csum_ofs, const Boolean udp)) {
    if (csum_ofs < 255) {
        /* note that apf_internal_calc_csum() treats negative lengths as zero */
        u32 csum = apf_internal_calc_csum(partial_csum, pkt + csum_start, len - csum_start);
        if (udp) csum = fix_udp_csum(csum);
        store_be16(pkt + csum_ofs, csum);
    }
    if (ip_ofs < 255) {
        u8 ip = pkt[ip_ofs] >> 4;
        if (ip == 4) {
            store_be16(pkt + ip_ofs + 10, apf_internal_calc_csum(0, pkt + ip_ofs, IPV4_HLEN));
            return pkt[ip_ofs + 1] >> 2;  /* DSCP */
        } else if (ip == 6) {
            return (read_be16(pkt + ip_ofs) >> 6) & 0x3F;  /* DSCP */
        }
    }
    return 0;
}
/* End include of apf_checksum.h */

/* User hook for interpreter debug tracing. */
#ifdef APF_TRACE_HOOK
extern void APF_TRACE_HOOK(u32 pc, const u32* regs, const u8* program,
                           u32 program_len, const u8 *packet, u32 packet_len,
                           const u32* memory, u32 ram_len);
#else
#define APF_TRACE_HOOK(pc, regs, program, program_len, packet, packet_len, memory, memory_len) \
    do { /* nop*/                                                                              \
    } while (0)
#endif

/* Return code indicating "packet" should accepted. */
#define PASS_PACKET 1
/* Return code indicating "packet" should be dropped. */
#define DROP_PACKET 0
/* Verify an internal condition and accept packet if it fails. */
#define ASSERT_RETURN(c) if (!(c)) return PASS_PACKET
/* If "c" is of an unsigned type, generate a compile warning that gets promoted to an error. */
/* This makes bounds checking simpler because ">= 0" can be avoided. Otherwise adding */
/* superfluous ">= 0" with unsigned expressions generates compile warnings. */
#define ENFORCE_UNSIGNED(c) ((c)==(u32)(c))

u32 apf_version(void) {
    return 20240226;
}

typedef struct {
    void *caller_ctx;  /* Passed in to interpreter, passed through to alloc/transmit. */
    u8* tx_buf;        /* The output buffer pointer */
    u32 tx_buf_len;    /* The length of the output buffer */
    u8* program;       /* Pointer to program/data buffer */
    u32 program_len;   /* Length of the program */
    u32 ram_len;       /* Length of the entire apf program/data region */
    const u8* packet;  /* Pointer to input packet buffer */
    u32 packet_len;    /* Length of the input packet buffer */
/*  u8 err_code;       // */
    u8 v6;             /* Set to 1 by first jmpdata (APFv6+) instruction */
    u32 pc;            /* Program counter. */
    u32 R[2];          /* Register values. */
    memory_type mem;   /* Memory slot values. */
} apf_context;

FUNC(int apf_internal_do_transmit_buffer(apf_context* ctx, u32 pkt_len, u8 dscp)) {
    int ret = apf_transmit_buffer(ctx->caller_ctx, ctx->tx_buf, pkt_len, dscp);
    ctx->tx_buf = NULL;
    ctx->tx_buf_len = 0;
    return ret;
}

static int do_discard_buffer(apf_context* ctx) {
    return apf_internal_do_transmit_buffer(ctx, 0 /* pkt_len */, 0 /* dscp */);
}

/* Decode the imm length, does not do range checking. */
/* But note that program is at least 20 bytes shorter than ram, so first few */
/* immediates can always be safely decoded without exceeding ram buffer. */
static u32 decode_imm(apf_context* ctx, u32 length) {
    u32 i, v = 0;
    for (i = 0; i < length; ++i) v = (v << 8) | ctx->program[ctx->pc++];
    return v;
}

#define DECODE_U8() (ctx->program[ctx->pc++])

static u16 decode_be16(apf_context* ctx) {
    u16 v = ctx->program[ctx->pc++];
    v <<= 8;
    v |= ctx->program[ctx->pc++];
    return v;
}

static int do_apf_run(apf_context* ctx) {
/* Is offset within ram bounds? */
#define IN_RAM_BOUNDS(p) (ENFORCE_UNSIGNED(p) && (p) < ctx->ram_len)
/* Is offset within packet bounds? */
#define IN_PACKET_BOUNDS(p) (ENFORCE_UNSIGNED(p) && (p) < ctx->packet_len)
/* Is access to offset |p| length |size| within data bounds? */
#define IN_DATA_BOUNDS(p, size) (ENFORCE_UNSIGNED(p) && \
                                 ENFORCE_UNSIGNED(size) && \
                                 (p) + (size) <= ctx->ram_len && \
                                 (p) + (size) >= (p))  /* catch wraparounds */
/* Accept packet if not within ram bounds */
#define ASSERT_IN_RAM_BOUNDS(p) ASSERT_RETURN(IN_RAM_BOUNDS(p))
/* Accept packet if not within packet bounds */
#define ASSERT_IN_PACKET_BOUNDS(p) ASSERT_RETURN(IN_PACKET_BOUNDS(p))
/* Accept packet if not within data bounds */
#define ASSERT_IN_DATA_BOUNDS(p, size) ASSERT_RETURN(IN_DATA_BOUNDS(p, size))

  /* Counters start at end of RAM and count *backwards* so this array takes negative integers. */
  u32 *counter = (u32*)(ctx->program + ctx->ram_len);

  ASSERT_IN_PACKET_BOUNDS(ETH_HLEN);
  /* Only populate if IP version is IPv4. */
  if ((ctx->packet[ETH_HLEN] & 0xf0) == 0x40) {
      ctx->mem.named.ipv4_header_size = (ctx->packet[ETH_HLEN] & 15) * 4;
  }
  /* Count of instructions remaining to execute. This is done to ensure an */
  /* upper bound on execution time. It should never be hit and is only for */
  /* safety. Initialize to the number of bytes in the program which is an */
  /* upper bound on the number of instructions in the program. */
  u32 instructions_remaining = ctx->program_len;

/* Is access to offset |p| length |size| within output buffer bounds? */
#define IN_OUTPUT_BOUNDS(p, size) (ENFORCE_UNSIGNED(p) && \
                                 ENFORCE_UNSIGNED(size) && \
                                 (p) + (size) <= ctx->tx_buf_len && \
                                 (p) + (size) >= (p))
/* Accept packet if not write within allocated output buffer */
#define ASSERT_IN_OUTPUT_BOUNDS(p, size) ASSERT_RETURN(IN_OUTPUT_BOUNDS(p, size))

  do {
      APF_TRACE_HOOK(ctx->pc, ctx->R, ctx->program, ctx->program_len,
                     ctx->packet, ctx->packet_len, ctx->mem.slot, ctx->ram_len);
      if (ctx->pc == ctx->program_len + 1) return DROP_PACKET;
      if (ctx->pc >= ctx->program_len) return PASS_PACKET;

      const u8 bytecode = ctx->program[ctx->pc++];
      const u32 opcode = EXTRACT_OPCODE(bytecode);
      const u32 reg_num = EXTRACT_REGISTER(bytecode);
#define REG (ctx->R[reg_num])
#define OTHER_REG (ctx->R[reg_num ^ 1])
      /* All instructions have immediate fields, so load them now. */
      const u32 len_field = EXTRACT_IMM_LENGTH(bytecode);
      u32 imm = 0;
      s32 signed_imm = 0;
      if (len_field != 0) {
          const u32 imm_len = 1 << (len_field - 1);
          imm = decode_imm(ctx, imm_len); /* 1st imm, at worst bytes 1-4 past opcode/program_len */
          /* Sign extend imm into signed_imm. */
          signed_imm = (s32)(imm << ((4 - imm_len) * 8));
          signed_imm >>= (4 - imm_len) * 8;
      }

      u32 pktcopy_src_offset = 0;  /* used for various pktdatacopy opcodes */
      switch (opcode) {
          case PASSDROP_OPCODE: {
              if (len_field > 2) return PASS_PACKET;  /* max 64K counters (ie. imm < 64K) */
              if (imm) {
                  if (4 * imm > ctx->ram_len) return PASS_PACKET;
                  counter[-(s32)imm]++;
              }
              return reg_num ? DROP_PACKET : PASS_PACKET;
          }
          case LDB_OPCODE:
          case LDH_OPCODE:
          case LDW_OPCODE:
          case LDBX_OPCODE:
          case LDHX_OPCODE:
          case LDWX_OPCODE: {
              u32 offs = imm;
              /* Note: this can overflow and actually decrease offs. */
              if (opcode >= LDBX_OPCODE) offs += ctx->R[1];
              ASSERT_IN_PACKET_BOUNDS(offs);
              u32 load_size = 0;
              switch (opcode) {
                  case LDB_OPCODE:
                  case LDBX_OPCODE:
                    load_size = 1;
                    break;
                  case LDH_OPCODE:
                  case LDHX_OPCODE:
                    load_size = 2;
                    break;
                  case LDW_OPCODE:
                  case LDWX_OPCODE:
                    load_size = 4;
                    break;
                  /* Immediately enclosing switch statement guarantees */
                  /* opcode cannot be any other value. */
              }
              const u32 end_offs = offs + (load_size - 1);
              /* Catch overflow/wrap-around. */
              ASSERT_RETURN(end_offs >= offs);
              ASSERT_IN_PACKET_BOUNDS(end_offs);
              u32 val = 0;
              while (load_size--) val = (val << 8) | ctx->packet[offs++];
              REG = val;
              break;
          }
          case JMP_OPCODE:
              if (reg_num && !ctx->v6) {
                /* First invocation of APFv6 jmpdata instruction */
                counter[-1] = 0x12345678;  /* endianness marker */
                counter[-2]++;  /* total packets ++ */
                ctx->v6 = (u8)True;
              }
              /* This can jump backwards. Infinite looping prevented by instructions_remaining. */
              ctx->pc += imm;
              break;
          case JEQ_OPCODE:
          case JNE_OPCODE:
          case JGT_OPCODE:
          case JLT_OPCODE:
          case JSET_OPCODE:
          case JBSMATCH_OPCODE: {
              /* Load second immediate field. */
              u32 cmp_imm = 0;
              if (reg_num == 1) {
                  cmp_imm = ctx->R[1];
              } else if (len_field != 0) {
                  u32 cmp_imm_len = 1 << (len_field - 1);
                  cmp_imm = decode_imm(ctx, cmp_imm_len); /* 2nd imm, at worst 8 bytes past prog_len */
              }
              switch (opcode) {
                  case JEQ_OPCODE:  if (ctx->R[0] == cmp_imm) ctx->pc += imm; break;
                  case JNE_OPCODE:  if (ctx->R[0] != cmp_imm) ctx->pc += imm; break;
                  case JGT_OPCODE:  if (ctx->R[0] >  cmp_imm) ctx->pc += imm; break;
                  case JLT_OPCODE:  if (ctx->R[0] <  cmp_imm) ctx->pc += imm; break;
                  case JSET_OPCODE: if (ctx->R[0] &  cmp_imm) ctx->pc += imm; break;
                  case JBSMATCH_OPCODE: {
                      /* cmp_imm is size in bytes of data to compare. */
                      /* pc is offset of program bytes to compare. */
                      /* imm is jump target offset. */
                      /* REG is offset of packet bytes to compare. */
                      if (len_field > 2) return PASS_PACKET; /* guarantees cmp_imm <= 0xFFFF */
                      /* pc < program_len < ram_len < 2GiB, thus pc + cmp_imm cannot wrap */
                      if (!IN_RAM_BOUNDS(ctx->pc + cmp_imm - 1)) return PASS_PACKET;
                      ASSERT_IN_PACKET_BOUNDS(REG);
                      const u32 last_packet_offs = REG + cmp_imm - 1;
                      ASSERT_RETURN(last_packet_offs >= REG);
                      ASSERT_IN_PACKET_BOUNDS(last_packet_offs);
                      if (memcmp(ctx->program + ctx->pc, ctx->packet + REG, cmp_imm))
                          ctx->pc += imm;
                      /* skip past comparison bytes */
                      ctx->pc += cmp_imm;
                      break;
                  }
              }
              break;
          }
          case ADD_OPCODE: ctx->R[0] += reg_num ? ctx->R[1] : imm; break;
          case MUL_OPCODE: ctx->R[0] *= reg_num ? ctx->R[1] : imm; break;
          case AND_OPCODE: ctx->R[0] &= reg_num ? ctx->R[1] : imm; break;
          case OR_OPCODE:  ctx->R[0] |= reg_num ? ctx->R[1] : imm; break;
          case DIV_OPCODE: {
              const u32 div_operand = reg_num ? ctx->R[1] : imm;
              ASSERT_RETURN(div_operand);
              ctx->R[0] /= div_operand;
              break;
          }
          case SH_OPCODE: {
              const s32 shift_val = reg_num ? (s32)ctx->R[1] : signed_imm;
              if (shift_val > 0)
                  ctx->R[0] <<= shift_val;
              else
                  ctx->R[0] >>= -shift_val;
              break;
          }
          case LI_OPCODE:
              REG = (u32)signed_imm;
              break;
          case PKTDATACOPY_OPCODE:
              pktcopy_src_offset = imm;
              imm = PKTDATACOPYIMM_EXT_OPCODE;
              FALLTHROUGH;
          case EXT_OPCODE:
              if (/* imm >= LDM_EXT_OPCODE &&  -- but note imm is u32 and LDM_EXT_OPCODE is 0 */
                  imm < (LDM_EXT_OPCODE + MEMORY_ITEMS)) {
                REG = ctx->mem.slot[imm - LDM_EXT_OPCODE];
              } else if (imm >= STM_EXT_OPCODE && imm < (STM_EXT_OPCODE + MEMORY_ITEMS)) {
                ctx->mem.slot[imm - STM_EXT_OPCODE] = REG;
              } else switch (imm) {
                  case NOT_EXT_OPCODE: REG = ~REG;      break;
                  case NEG_EXT_OPCODE: REG = -REG;      break;
                  case MOV_EXT_OPCODE: REG = OTHER_REG; break;
                  case SWAP_EXT_OPCODE: {
                    u32 tmp = REG;
                    REG = OTHER_REG;
                    OTHER_REG = tmp;
                    break;
                  }
                  case ALLOCATE_EXT_OPCODE:
                    ASSERT_RETURN(ctx->tx_buf == NULL);
                    if (reg_num == 0) {
                        ctx->tx_buf_len = REG;
                    } else {
                        ctx->tx_buf_len = decode_be16(ctx); /* 2nd imm, at worst 6 B past prog_len */
                    }
                    /* checksumming functions requires minimum 266 byte buffer for correctness */
                    if (ctx->tx_buf_len < 266) ctx->tx_buf_len = 266;
                    ctx->tx_buf = apf_allocate_buffer(ctx->caller_ctx, ctx->tx_buf_len);
                    if (!ctx->tx_buf) {  /* allocate failure */
                        ctx->tx_buf_len = 0;
                        counter[-3]++;
                        return PASS_PACKET;
                    }
                    memset(ctx->tx_buf, 0, ctx->tx_buf_len);
                    ctx->mem.named.tx_buf_offset = 0;
                    break;
                  case TRANSMIT_EXT_OPCODE:
                    ASSERT_RETURN(ctx->tx_buf);
                    u32 pkt_len = ctx->mem.named.tx_buf_offset;
                    /* If pkt_len > allocate_buffer_len, it means sth. wrong */
                    /* happened and the tx_buf should be deallocated. */
                    if (pkt_len > ctx->tx_buf_len) {
                        do_discard_buffer(ctx);
                        return PASS_PACKET;
                    }
                    /* tx_buf_len cannot be large because we'd run out of RAM, */
                    /* so the above unsigned comparison effectively guarantees casting pkt_len */
                    /* to a signed value does not result in it going negative. */
                    u8 ip_ofs = DECODE_U8();              /* 2nd imm, at worst 5 B past prog_len */
                    u8 csum_ofs = DECODE_U8();            /* 3rd imm, at worst 6 B past prog_len */
                    u8 csum_start = 0;
                    u16 partial_csum = 0;
                    if (csum_ofs < 255) {
                        csum_start = DECODE_U8();         /* 4th imm, at worst 7 B past prog_len */
                        partial_csum = decode_be16(ctx);  /* 5th imm, at worst 9 B past prog_len */
                    }
                    int dscp = apf_internal_csum_and_return_dscp(ctx->tx_buf, (s32)pkt_len, ip_ofs,
                                                    partial_csum, csum_start, csum_ofs,
                                                    (Boolean)reg_num);
                    int ret = apf_internal_do_transmit_buffer(ctx, pkt_len, dscp);
                    if (ret) { counter[-4]++; return PASS_PACKET; } /* transmit failure */
                    break;
                  case EPKTDATACOPYIMM_EXT_OPCODE:  /* 41 */
                  case EPKTDATACOPYR1_EXT_OPCODE:   /* 42 */
                    pktcopy_src_offset = ctx->R[0];
                    FALLTHROUGH;
                  case PKTDATACOPYIMM_EXT_OPCODE: { /* 65536 */
                    u32 copy_len = ctx->R[1];
                    if (imm != EPKTDATACOPYR1_EXT_OPCODE) {
                        copy_len = DECODE_U8();  /* 2nd imm, at worst 8 bytes past prog_len */
                    }
                    ASSERT_RETURN(ctx->tx_buf);
                    u32 dst_offs = ctx->mem.named.tx_buf_offset;
                    ASSERT_IN_OUTPUT_BOUNDS(dst_offs, copy_len);
                    if (reg_num == 0) {  /* copy from packet */
                        ASSERT_IN_PACKET_BOUNDS(pktcopy_src_offset);
                        const u32 last_packet_offs = pktcopy_src_offset + copy_len - 1;
                        ASSERT_RETURN(last_packet_offs >= pktcopy_src_offset);
                        ASSERT_IN_PACKET_BOUNDS(last_packet_offs);
                        memcpy(ctx->tx_buf + dst_offs, ctx->packet + pktcopy_src_offset, copy_len);
                    } else {  /* copy from data */
                        ASSERT_IN_RAM_BOUNDS(pktcopy_src_offset + copy_len - 1);
                        memcpy(ctx->tx_buf + dst_offs, ctx->program + pktcopy_src_offset, copy_len);
                    }
                    dst_offs += copy_len;
                    ctx->mem.named.tx_buf_offset = dst_offs;
                    break;
                  }
                  case JDNSQMATCH_EXT_OPCODE:       /* 43 */
                  case JDNSAMATCH_EXT_OPCODE:       /* 44 */
                  case JDNSQMATCHSAFE_EXT_OPCODE:   /* 45 */
                  case JDNSAMATCHSAFE_EXT_OPCODE: { /* 46 */
                    const u32 imm_len = 1 << (len_field - 1);
                    u32 jump_offs = decode_imm(ctx, imm_len); /* 2nd imm, at worst 8 B past prog_len */
                    int qtype = -1;
                    if (imm & 1) { /* JDNSQMATCH & JDNSQMATCHSAFE are *odd* extended opcodes */
                        qtype = DECODE_U8();  /* 3rd imm, at worst 9 bytes past prog_len */
                    }
                    u32 udp_payload_offset = ctx->R[0];
                    match_result_type match_rst = apf_internal_match_names(ctx->program + ctx->pc,
                                                              ctx->program + ctx->program_len,
                                                              ctx->packet + udp_payload_offset,
                                                              ctx->packet_len - udp_payload_offset,
                                                              qtype);
                    if (match_rst == error_program) return PASS_PACKET;
                    if (match_rst == error_packet) {
                        counter[-5]++; /* increment error dns packet counter */
                        return (imm >= JDNSQMATCHSAFE_EXT_OPCODE) ? PASS_PACKET : DROP_PACKET;
                    }
                    while (ctx->pc + 1 < ctx->program_len &&
                           (ctx->program[ctx->pc] || ctx->program[ctx->pc + 1])) {
                        ctx->pc++;
                    }
                    ctx->pc += 2;  /* skip the final double 0 needle end */
                    /* relies on reg_num in {0,1} and match_rst being {False=0, True=1} */
                    if (!(reg_num ^ (u32)match_rst)) ctx->pc += jump_offs;
                    break;
                  }
                  case EWRITE1_EXT_OPCODE:
                  case EWRITE2_EXT_OPCODE:
                  case EWRITE4_EXT_OPCODE: {
                    ASSERT_RETURN(ctx->tx_buf);
                    const u32 write_len = 1 << (imm - EWRITE1_EXT_OPCODE);
                    ASSERT_IN_OUTPUT_BOUNDS(ctx->mem.named.tx_buf_offset, write_len);
                    u32 i;
                    for (i = 0; i < write_len; ++i) {
                        ctx->tx_buf[ctx->mem.named.tx_buf_offset++] =
                            (u8)(REG >> (write_len - 1 - i) * 8);
                    }
                    break;
                  }
                  default:  /* Unknown extended opcode */
                    return PASS_PACKET;  /* Bail out */
              }
              break;
          case LDDW_OPCODE: {
              u32 offs = OTHER_REG + (u32)signed_imm;
              u32 size = 4;
              u32 val = 0;
              /* Negative offsets wrap around the end of the address space. */
              /* This allows us to efficiently access the end of the */
              /* address space with one-byte immediates without using %=. */
              if (offs & 0x80000000) offs += ctx->ram_len;  /* unsigned overflow intended */
              ASSERT_IN_DATA_BOUNDS(offs, size);
              while (size--) val = (val << 8) | ctx->program[offs++];
              REG = val;
              break;
          }
          case STDW_OPCODE: {
              u32 offs = OTHER_REG + (u32)signed_imm;
              u32 size = 4;
              u32 val = REG;
              /* Negative offsets wrap around the end of the address space. */
              /* This allows us to efficiently access the end of the */
              /* address space with one-byte immediates without using %=. */
              if (offs & 0x80000000) offs += ctx->ram_len;  /* unsigned overflow intended */
              ASSERT_IN_DATA_BOUNDS(offs, size);
              while (size--) {
                  ctx->program[offs++] = (val >> 24);
                  val <<= 8;
              }
              break;
          }
          case WRITE_OPCODE: {
              ASSERT_RETURN(ctx->tx_buf);
              ASSERT_RETURN(len_field);
              const u32 write_len = 1 << (len_field - 1);
              ASSERT_IN_OUTPUT_BOUNDS(ctx->mem.named.tx_buf_offset, write_len);
              u32 i;
              for (i = 0; i < write_len; ++i) {
                  ctx->tx_buf[ctx->mem.named.tx_buf_offset++] =
                      (u8)(imm >> (write_len - 1 - i) * 8);
              }
              break;
          }
          default:  /* Unknown opcode */
              return PASS_PACKET;  /* Bail out */
      }
  } while (instructions_remaining--);
  return PASS_PACKET;
}

int apf_run(void* ctx, u32* const program, const u32 program_len,
            const u32 ram_len, const u8* const packet,
            const u32 packet_len, const u32 filter_age_16384ths) {
  /* Due to direct 32-bit read/write access to counters at end of ram */
  /* APFv6 interpreter requires program & ram_len to be 4 byte aligned. */
  if (3 & (uintptr_t)program) return PASS_PACKET;
  if (3 & ram_len) return PASS_PACKET;

  /* We rely on ram_len + 65536 not overflowing, so require ram_len < 2GiB */
  /* Similarly LDDW/STDW have special meaning for negative ram offsets. */
  /* We also don't want garbage like program_len == 0xFFFFFFFF */
  if ((program_len | ram_len) >> 31) return PASS_PACKET;

  /* APFv6 requires at least 5 u32 counters at the end of ram, this makes counter[-5]++ valid */
  /* This cannot wrap due to previous check. */
  if (program_len + 20 > ram_len) return PASS_PACKET;

  apf_context apf_ctx = {};
  apf_ctx.caller_ctx = ctx;
  apf_ctx.program = (u8*)program;
  apf_ctx.program_len = program_len;
  apf_ctx.ram_len = ram_len;
  apf_ctx.packet = packet;
  apf_ctx.packet_len = packet_len;
  /* Fill in pre-filled memory slot values. */
  apf_ctx.mem.named.program_size = program_len;
  apf_ctx.mem.named.ram_len = ram_len;
  apf_ctx.mem.named.packet_size = packet_len;
  apf_ctx.mem.named.filter_age = filter_age_16384ths >> 14;
  apf_ctx.mem.named.filter_age_16384ths = filter_age_16384ths;

  int ret = do_apf_run(&apf_ctx);
  if (apf_ctx.tx_buf) do_discard_buffer(&apf_ctx);
  return ret;
}
