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

// Number of temporary memory slots, see ldm/stm instructions.
#define MEMORY_ITEMS 16
// Upon program execution, some temporary memory slots are prefilled:

typedef union {
  struct {
    u32 pad[9];               // 0..8
    u32 filter_age_16384ths;  // 9:  Age since filter installed in 1/16384 seconds.
    u32 tx_buf_offset;        // 10: Offset in tx_buf where next byte will be written
    u32 program_size;         // 11: Size of program (in bytes)
    u32 ram_len;              // 12: Total size of program + data, ie. ram_len
    u32 ipv4_header_size;     // 13: 4*([APF_FRAME_HEADER_SIZE]&15)
    u32 packet_size;          // 14: Size of packet in bytes.
    u32 filter_age;           // 15: Age since filter installed in seconds.
  } named;
  u32 slot[MEMORY_ITEMS];
} memory_type;

/* ---------------------------------------------------------------------------------------------- */

// Standard opcodes.

/* Unconditionally pass (if R=0) or drop (if R=1) packet and optionally increment counter.
 * An optional non-zero unsigned immediate value can be provided to encode the counter number.
 * The counter is located (-4 * counter number) bytes from the end of the data region.
 * It is a U32 big-endian value and is always incremented by 1.
 * This is more or less equivalent to: lddw R0, -4*N; add R0, 1; stdw R0, -4*N; {pass,drop}
 * e.g. "pass", "pass 1", "drop", "drop 1"
 */
#define PASSDROP_OPCODE 0

#define LDB_OPCODE 1    // Load 1 byte  from immediate offset, e.g. "ldb R0, [5]"
#define LDH_OPCODE 2    // Load 2 bytes from immediate offset, e.g. "ldh R0, [5]"
#define LDW_OPCODE 3    // Load 4 bytes from immediate offset, e.g. "ldw R0, [5]"
#define LDBX_OPCODE 4   // Load 1 byte  from immediate offset plus register, e.g. "ldbx R0, [5+R0]"
#define LDHX_OPCODE 5   // Load 2 bytes from immediate offset plus register, e.g. "ldhx R0, [5+R0]"
#define LDWX_OPCODE 6   // Load 4 bytes from immediate offset plus register, e.g. "ldwx R0, [5+R0]"
#define ADD_OPCODE 7    // Add, e.g. "add R0,5"
#define MUL_OPCODE 8    // Multiply, e.g. "mul R0,5"
#define DIV_OPCODE 9    // Divide, e.g. "div R0,5"
#define AND_OPCODE 10   // And, e.g. "and R0,5"
#define OR_OPCODE 11    // Or, e.g. "or R0,5"
#define SH_OPCODE 12    // Left shift, e.g. "sh R0, 5" or "sh R0, -5" (shifts right)
#define LI_OPCODE 13    // Load signed immediate, e.g. "li R0,5"
#define JMP_OPCODE 14   // Unconditional jump, e.g. "jmp label"
#define JEQ_OPCODE 15   // Compare equal and branch, e.g. "jeq R0,5,label"
#define JNE_OPCODE 16   // Compare not equal and branch, e.g. "jne R0,5,label"
#define JGT_OPCODE 17   // Compare greater than and branch, e.g. "jgt R0,5,label"
#define JLT_OPCODE 18   // Compare less than and branch, e.g. "jlt R0,5,label"
#define JSET_OPCODE 19  // Compare any bits set and branch, e.g. "jset R0,5,label"
#define JBSMATCH_OPCODE 20 // Compare byte sequence [R=0 not] equal, e.g. "jbsne R0,2,label,0x1122"
#define EXT_OPCODE 21   // Immediate value is one of *_EXT_OPCODE
#define LDDW_OPCODE 22  // Load 4 bytes from data address (register + signed imm): "lddw R0, [5+R1]"
#define STDW_OPCODE 23  // Store 4 bytes to data address (register + signed imm): "stdw R0, [5+R1]"

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

// Extended opcodes.
// These all have an opcode of EXT_OPCODE and specify the actual opcode in the immediate field.

#define LDM_EXT_OPCODE 0   // Load from temporary memory, e.g. "ldm R0,5"
  // Values 0-15 represent loading the different temporary memory slots.
#define STM_EXT_OPCODE 16  // Store to temporary memory, e.g. "stm R0,5"
  // Values 16-31 represent storing to the different temporary memory slots.
#define NOT_EXT_OPCODE 32  // Not, e.g. "not R0"
#define NEG_EXT_OPCODE 33  // Negate, e.g. "neg R0"
#define SWAP_EXT_OPCODE 34 // Swap, e.g. "swap R0,R1"
#define MOV_EXT_OPCODE 35  // Move, e.g. "move R0,R1"

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

// This extended opcode is used to implement PKTDATACOPY_OPCODE
#define PKTDATACOPYIMM_EXT_OPCODE 65536

#define EXTRACT_OPCODE(i) (((i) >> 3) & 31)
#define EXTRACT_REGISTER(i) ((i) & 1)
#define EXTRACT_IMM_LENGTH(i) (((i) >> 1) & 3)

#endif  // ANDROID_APF_APF_H
