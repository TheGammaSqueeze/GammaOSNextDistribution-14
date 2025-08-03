/*
 * Copyright 2011 Daniel Drown
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
 *
 * clatd.h - main routines used by clatd
 */
#ifndef __CLATD_H__
#define __CLATD_H__

#include <signal.h>
#include <stdlib.h>
#include <sys/uio.h>

struct tun_data;

// IPv4 header has a u16 total length field, for maximum L3 mtu of 0xFFFF.
//
// Translating IPv4 to IPv6 requires removing the IPv4 header (20) and adding
// an IPv6 header (40), possibly with an extra ipv6 fragment extension header (8).
//
// As such the maximum IPv4 L3 mtu size is 0xFFFF (by u16 tot_len field)
// and the maximum IPv6 L3 mtu size is 0xFFFF + 28 (which is larger)
//
// A received non-jumbogram IPv6 frame could potentially be u16 payload_len = 0xFFFF
// + sizeof ipv6 header = 40, bytes in size.  But such a packet cannot be meaningfully
// converted to IPv4 (it's too large).  As such the restriction is the same: 0xFFFF + 28
//
// (since there's no jumbogram support in IPv4, IPv6 jumbograms cannot be meaningfully
// converted to IPv4 anyway, and are thus entirely unsupported)
#define MAXMTU (0xFFFF + 28)

// logcat_hexdump() maximum binary data length, this is the maximum packet size
// plus some extra space for various headers:
//   struct tun_pi (4 bytes)
//   struct virtio_net_hdr (10 bytes)
//   ethernet (14 bytes), potentially including vlan tag (4) or tags (8 or 12)
// plus some extra just-in-case headroom, because it doesn't hurt.
#define MAXDUMPLEN (64 + MAXMTU)

#define CLATD_VERSION "1.7"

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

extern volatile sig_atomic_t running;

void event_loop(struct tun_data *tunnel);

/* function: parse_int
 * parses a string as a decimal/hex/octal signed integer
 *   str - the string to parse
 *   out - the signed integer to write to, gets clobbered on failure
 */
static inline int parse_int(const char *str, int *out) {
  char *end_ptr;
  *out = strtol(str, &end_ptr, 0);
  return *str && !*end_ptr;
}

/* function: parse_unsigned
 * parses a string as a decimal/hex/octal unsigned integer
 *   str - the string to parse
 *   out - the unsigned integer to write to, gets clobbered on failure
 */
static inline int parse_unsigned(const char *str, unsigned *out) {
  char *end_ptr;
  *out = strtoul(str, &end_ptr, 0);
  return *str && !*end_ptr;
}

#endif /* __CLATD_H__ */
