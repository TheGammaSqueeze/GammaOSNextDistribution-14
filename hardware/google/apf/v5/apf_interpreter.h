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

#ifndef APF_INTERPRETER_V5_H_
#define APF_INTERPRETER_V5_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Returns the max version of the APF instruction set supported by apf_run().
 * APFv6 is a superset of APFv4. APFv6 interpreters are able to run APFv4 code.
 */
uint32_t apf_version(void);

/**
 * Allocates a buffer for the APF program to build a reply packet.
 *
 * Unless in a critical low memory state, the firmware must allow allocating at
 * least one 1514 byte buffer for every call to apf_run(). The interpreter will
 * have at most one active allocation at any given time, and will always either
 * transmit or deallocate the buffer before apf_run() returns.
 *
 * It is OK if the firmware decides to limit allocations to at most one per
 * apf_run() invocation. This allows the firmware to delay transmitting
 * the buffer until after apf_run() has returned (by keeping track of whether
 * a buffer was allocated/deallocated/scheduled for transmit) and may
 * allow the use of a single statically allocated 1514+ byte buffer.
 *
 * The firmware MAY choose to allocate a larger buffer than requested, and
 * give the apf_interpreter a pointer to the middle of the buffer. This will
 * allow firmware to later (during or after apf_transmit_buffer call) populate
 * any required headers, trailers, etc.
 *
 * @param ctx - unmodified ctx pointer passed into apf_run().
 * @param size - the minimum size of buffer to allocate
 * @return the pointer to the allocated region. The function can return NULL to
 *         indicate allocation failure, for example if too many buffers are
 *         pending transmit. Returning NULL will most likely result in
 *         apf_run() returning PASS.
 */
uint8_t* apf_allocate_buffer(void* ctx, uint32_t size);

/**
 * Transmits the allocated buffer and deallocates it.
 *
 * The apf_interpreter will not read/write from/to the buffer once it calls
 * this function.
 *
 * The content of the buffer between [ptr, ptr + len) are the bytes to be
 * transmitted, starting from the ethernet header and not including any
 * ethernet CRC bytes at the end.
 *
 * The firmware is expected to make its best effort to transmit. If it
 * exhausts retries, or if there is no channel for too long and the transmit
 * queue is full, then it is OK for the packet to be dropped. The firmware should
 * prefer to fail allocation if it can predict transmit will fail.
 *
 * apf_transmit_buffer() may be asynchronous, which means the actual packet
 * transmission can happen sometime after the function returns.
 *
 * @param ctx - unmodified ctx pointer passed into apf_run().
 * @param ptr - pointer to the transmit buffer, must have been previously
 *              returned by apf_allocate_buffer() and not deallocated.
 * @param len - the number of bytes to be transmitted (possibly less than
 *              the allocated buffer), 0 means don't transmit the buffer
 *              but only deallocate it.
 * @param dscp - value in [0..63] - the upper 6 bits of the TOS field in
 *               the IPv4 header or traffic class field in the IPv6 header.
 * @return non-zero if the firmware *knows* the transmit will fail, zero if
 *         the transmit succeeded or the firmware thinks it will succeed.
 *         Returning an error will likely result in apf_run() returning PASS.
 */
int apf_transmit_buffer(void* ctx, uint8_t* ptr, uint32_t len, uint8_t dscp);

/**
 * Runs an APF program over a packet.
 *
 * The return value of apf_run indicates whether the packet should
 * be passed or dropped. As a part of apf_run() execution, the APF
 * program can call apf_allocate_buffer()/apf_transmit_buffer() to construct
 * a reply packet and transmit it.
 *
 * The text section containing the program instructions starts at address
 * program and stops at + program_len - 1, and the writable data section
 * begins at program + program_len and ends at program + ram_len - 1,
 * as described in the following diagram:
 *
 *     program         program + program_len    program + ram_len
 *        |    text section    |      data section      |
 *        +--------------------+------------------------+
 *
 * @param ctx - pointer to any additional context required for allocation and transmit.
 *              May be NULL if no such context is required. This is opaque to
 *              the interpreter and will be passed through unmodified
 *              to apf_allocate_buffer() and apf_transmit_buffer() calls.
 * @param program - the program bytecode, followed by the writable data region.
 *                  Note: this *MUST* be a 4 byte aligned region.
 * @param program_len - the length in bytes of the read-only portion of the APF
 *                    buffer pointed to by {@code program}.
 *                    This is determined by the size of the loaded APF program.
 * @param ram_len - total length of the APF buffer pointed to by
 *                  {@code program}, including the read-only bytecode
 *                  portion and the read-write data portion.
 *                  This is expected to be a constant which doesn't change
 *                  value even when a new APF program is loaded.
 *                  Note: this *MUST* be a multiple of 4.
 * @param packet - the packet bytes, starting from the ethernet header.
 * @param packet_len - the length of {@code packet} in bytes, not
 *                     including trailers/CRC.
 * @param filter_age_16384ths - the number of 1/16384 seconds since the filter
 *                     was programmed.
 *
 * @return non-zero if packet should be passed,
 *         zero if packet should be dropped.
 *
 * NOTE: How to calculate filter_age_16384ths:
 *
 * - if you have a u64 clock source counting nanoseconds:
 *     u64 nanoseconds = current_nanosecond_time_u64() - filter_installation_nanosecond_time_u64;
 *     u32 filter_age_16384ths = (u32)((nanoseconds << 5) / 1953125);
 *
 * - if you have a u64 clock source counting microseconds:
 *     u64 microseconds = current_microsecond_time_u64() - filter_installation_microsecond_time_u64;
 *     u32 filter_age_16384ths = (u32)((microseconds << 8) / 15625);
 *
 * - if you have a u64 clock source counting milliseconds:
 *     u64 milliseconds = current_millisecond_time_u64() - filter_installation_millisecond_time_u64;
 *     u32 filter_age_16384ths = (u32)((milliseconds << 11) / 125);
 *
 * - if you have a u32 clock source counting milliseconds and cannot use 64-bit arithmetic:
 *     u32 milliseconds = current_millisecond_time_u32() - filter_installation_millisecond_time_u32;
 *     u32 filter_age_16384ths = ((((((milliseconds << 4) / 5) << 2) / 5) << 2) / 5) << 3;
 *   or the less precise:
 *     u32 filter_age_16384ths = ((milliseconds << 4) / 125) << 7;
 *
 * - if you have a u32 clock source counting seconds:
 *     u32 seconds = current_second_time_u32() - filter_installation_second_time_u32;
 *     u32 filter_age_16384ths = seconds << 14;
 */
int apf_run(void* ctx, uint32_t* const program, const uint32_t program_len,
            const uint32_t ram_len, const uint8_t* const packet,
            const uint32_t packet_len, const uint32_t filter_age_16384ths);

#ifdef __cplusplus
}
#endif

#endif  /* APF_INTERPRETER_V5_H_ */
