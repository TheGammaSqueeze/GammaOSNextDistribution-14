/*
 * Copyright 2012 Daniel Drown
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
 * clatd.c - tun interface setup and main event loop
 */
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <linux/filter.h>
#include <linux/if.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/if_tun.h>
#include <linux/virtio_net.h>
#include <net/if.h>
#include <sys/uio.h>

#include "clatd.h"
#include "checksum.h"
#include "config.h"
#include "dump.h"
#include "logging.h"
#include "translate.h"

struct clat_config Global_Clatd_Config;

volatile sig_atomic_t running = 1;

// reads IPv6 packet from AF_PACKET socket, translates to IPv4, writes to tun
void process_packet_6_to_4(struct tun_data *tunnel) {
  // ethernet header is 14 bytes, plus 4 for a normal VLAN tag or 8 for Q-in-Q
  // we don't really support vlans (or especially Q-in-Q)...
  // but a few bytes of extra buffer space doesn't hurt...
  struct {
    struct virtio_net_hdr vnet;
    uint8_t payload[22 + MAXMTU];
    char pad; // +1 to make packet truncation obvious
  } buf;
  struct iovec iov = {
    .iov_base = &buf,
    .iov_len = sizeof(buf),
  };
  char cmsg_buf[CMSG_SPACE(sizeof(struct tpacket_auxdata))];
  struct msghdr msgh = {
    .msg_iov = &iov,
    .msg_iovlen = 1,
    .msg_control = cmsg_buf,
    .msg_controllen = sizeof(cmsg_buf),
  };
  ssize_t readlen = recvmsg(tunnel->read_fd6, &msgh, /*flags*/ 0);

  if (readlen < 0) {
    if (errno != EAGAIN) {
      logmsg(ANDROID_LOG_WARN, "%s: read error: %s", __func__, strerror(errno));
    }
    return;
  } else if (readlen == 0) {
    logmsg(ANDROID_LOG_WARN, "%s: packet socket removed?", __func__);
    running = 0;
    return;
  } else if (readlen >= sizeof(buf)) {
    logmsg(ANDROID_LOG_WARN, "%s: read truncation - ignoring pkt", __func__);
    return;
  }

  bool ok = false;
  __u32 tp_status = 0;
  __u16 tp_net = 0;

  for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msgh); cmsg != NULL; cmsg = CMSG_NXTHDR(&msgh,cmsg)) {
    if (cmsg->cmsg_level == SOL_PACKET && cmsg->cmsg_type == PACKET_AUXDATA) {
      struct tpacket_auxdata *aux = (struct tpacket_auxdata *)CMSG_DATA(cmsg);
      ok = true;
      tp_status = aux->tp_status;
      tp_net = aux->tp_net;
      break;
    }
  }

  if (!ok) {
    // theoretically this should not happen...
    static bool logged = false;
    if (!logged) {
      logmsg(ANDROID_LOG_ERROR, "%s: failed to fetch tpacket_auxdata cmsg", __func__);
      logged = true;
    }
  }

  const int payload_offset = offsetof(typeof(buf), payload);
  if (readlen < payload_offset + tp_net) {
    logmsg(ANDROID_LOG_WARN, "%s: ignoring %zd byte pkt shorter than %d+%u L2 header",
           __func__, readlen, payload_offset, tp_net);
    return;
  }

  const int pkt_len = readlen - payload_offset;

  // This will detect a skb->ip_summed == CHECKSUM_PARTIAL packet with non-final L4 checksum
  if (tp_status & TP_STATUS_CSUMNOTREADY) {
    static bool logged = false;
    if (!logged) {
      logmsg(ANDROID_LOG_WARN, "%s: L4 checksum calculation required", __func__);
      logged = true;
    }

    // These are non-negative by virtue of csum_start/offset being u16
    const int cs_start = buf.vnet.csum_start;
    const int cs_offset = cs_start + buf.vnet.csum_offset;
    if (cs_start > pkt_len) {
      logmsg(ANDROID_LOG_ERROR, "%s: out of range - checksum start %d > %d",
             __func__, cs_start, pkt_len);
    } else if (cs_offset + 1 >= pkt_len) {
      logmsg(ANDROID_LOG_ERROR, "%s: out of range - checksum offset %d + 1 >= %d",
             __func__, cs_offset, pkt_len);
    } else {
      uint16_t csum = ip_checksum(buf.payload + cs_start, pkt_len - cs_start);
      if (!csum) csum = 0xFFFF;  // required fixup for UDP, TCP must live with it
      buf.payload[cs_offset] = csum & 0xFF;
      buf.payload[cs_offset + 1] = csum >> 8;
    }
  }

  translate_packet(tunnel->fd4, 0 /* to_ipv6 */, buf.payload + tp_net, pkt_len - tp_net);
}

// reads TUN_PI + L3 IPv4 packet from tun, translates to IPv6, writes to AF_INET6/RAW socket
void process_packet_4_to_6(struct tun_data *tunnel) {
  struct {
    struct tun_pi pi;
    uint8_t payload[MAXMTU];
    char pad; // +1 byte to make packet truncation obvious
  } buf;
  ssize_t readlen = read(tunnel->fd4, &buf, sizeof(buf));

  if (readlen < 0) {
    if (errno != EAGAIN) {
      logmsg(ANDROID_LOG_WARN, "%s: read error: %s", __func__, strerror(errno));
    }
    return;
  } else if (readlen == 0) {
    logmsg(ANDROID_LOG_WARN, "%s: tun interface removed", __func__);
    running = 0;
    return;
  } else if (readlen >= sizeof(buf)) {
    logmsg(ANDROID_LOG_WARN, "%s: read truncation - ignoring pkt", __func__);
    return;
  }

  const int payload_offset = offsetof(typeof(buf), payload);

  if (readlen < payload_offset) {
    logmsg(ANDROID_LOG_WARN, "%s: short read: got %ld bytes", __func__, readlen);
    return;
  }

  const int pkt_len = readlen - payload_offset;

  uint16_t proto = ntohs(buf.pi.proto);
  if (proto != ETH_P_IP) {
    logmsg(ANDROID_LOG_WARN, "%s: unknown packet type = 0x%x", __func__, proto);
    return;
  }

  if (buf.pi.flags != 0) {
    logmsg(ANDROID_LOG_WARN, "%s: unexpected flags = %d", __func__, buf.pi.flags);
  }

  translate_packet(tunnel->write_fd6, 1 /* to_ipv6 */, buf.payload, pkt_len);
}

// IPv6 DAD packet format:
//   Ethernet header (if needed) will be added by the kernel:
//     u8[6] src_mac; u8[6] dst_mac '33:33:ff:XX:XX:XX'; be16 ethertype '0x86DD'
//   IPv6 header:
//     be32 0x60000000 - ipv6, tclass 0, flowlabel 0
//     be16 payload_length '32'; u8 nxt_hdr ICMPv6 '58'; u8 hop limit '255'
//     u128 src_ip6 '::'
//     u128 dst_ip6 'ff02::1:ffXX:XXXX'
//   ICMPv6 header:
//     u8 type '135'; u8 code '0'; u16 icmp6 checksum; u32 reserved '0'
//   ICMPv6 neighbour solicitation payload:
//     u128 tgt_ip6
//   ICMPv6 ND options:
//     u8 opt nr '14'; u8 length '1'; u8[6] nonce '6 random bytes'
void send_dad(int fd, const struct in6_addr* tgt) {
  struct {
    struct ip6_hdr ip6h;
    struct nd_neighbor_solicit ns;
    uint8_t ns_opt_nr;
    uint8_t ns_opt_len;
    uint8_t ns_opt_nonce[6];
  } dad_pkt = {
    .ip6h = {
      .ip6_flow = htonl(6 << 28),  // v6, 0 tclass, 0 flowlabel
      .ip6_plen = htons(sizeof(dad_pkt) - sizeof(struct ip6_hdr)),  // payload length, ie. 32
      .ip6_nxt = IPPROTO_ICMPV6,  // 58
      .ip6_hlim = 255,
      .ip6_src = {},  // ::
      .ip6_dst.s6_addr = {
        0xFF, 0x02, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 1,
        0xFF, tgt->s6_addr[13], tgt->s6_addr[14], tgt->s6_addr[15],
      },  // ff02::1:ffXX:XXXX - multicast group address derived from bottom 24-bits of tgt
    },
    .ns = {
      .nd_ns_type = ND_NEIGHBOR_SOLICIT,  // 135
      .nd_ns_code = 0,
      .nd_ns_cksum = 0,  // will be calculated later
      .nd_ns_reserved = 0,
      .nd_ns_target = *tgt,
    },
    .ns_opt_nr = 14,  // icmp6 option 'nonce' from RFC3971
    .ns_opt_len = 1,  // in units of 8 bytes, including option nr and len
    .ns_opt_nonce = {},  // opt_len *8 - sizeof u8(opt_nr) - sizeof u8(opt_len) = 6 ranodmized bytes
  };
  arc4random_buf(&dad_pkt.ns_opt_nonce, sizeof(dad_pkt.ns_opt_nonce));

  // 40 byte IPv6 header + 8 byte ICMPv6 header + 16 byte ipv6 target address + 8 byte nonce option
  _Static_assert(sizeof(dad_pkt) == 40 + 8 + 16 + 8, "sizeof dad packet != 72");

  // IPv6 header checksum is standard negated 16-bit one's complement sum over the icmpv6 pseudo
  // header (which includes payload length, nextheader, and src/dst ip) and the icmpv6 payload.
  //
  // Src/dst ip immediately prefix the icmpv6 header itself, so can be handled along
  // with the payload.  We thus only need to manually account for payload len & next header.
  //
  // The magic '8' is simply the offset of the ip6_src field in the ipv6 header,
  // ie. we're skipping over the ipv6 version, tclass, flowlabel, payload length, next header
  // and hop limit fields, because they're not quite where we want them to be.
  //
  // ip6_plen is already in network order, while ip6_nxt is a single byte and thus needs htons().
  uint32_t csum = dad_pkt.ip6h.ip6_plen + htons(dad_pkt.ip6h.ip6_nxt);
  csum = ip_checksum_add(csum, &dad_pkt.ip6h.ip6_src, sizeof(dad_pkt) - 8);
  dad_pkt.ns.nd_ns_cksum = ip_checksum_finish(csum);

  const struct sockaddr_in6 dst = {
    .sin6_family = AF_INET6,
    .sin6_addr = dad_pkt.ip6h.ip6_dst,
    .sin6_scope_id = if_nametoindex(Global_Clatd_Config.native_ipv6_interface),
  };

  sendto(fd, &dad_pkt, sizeof(dad_pkt), 0 /*flags*/, (const struct sockaddr *)&dst, sizeof(dst));
}

/* function: event_loop
 * reads packets from the tun network interface and passes them down the stack
 *   tunnel - tun device data
 */
void event_loop(struct tun_data *tunnel) {
  // Apparently some network gear will refuse to perform NS for IPs that aren't DAD'ed,
  // this would then result in an ipv6-only network with working native ipv6, working
  // IPv4 via DNS64, but non-functioning IPv4 via CLAT (ie. IPv4 literals + IPv4 only apps).
  // The kernel itself doesn't do DAD for anycast ips (but does handle IPV6 MLD and handle ND).
  // So we'll spoof dad here, and yeah, we really should check for a response and in
  // case of failure pick a different IP.  Seeing as 48-bits of the IP are utterly random
  // (with the other 16 chosen to guarantee checksum neutrality) this seems like a remote
  // concern...
  // TODO: actually perform true DAD
  send_dad(tunnel->write_fd6, &Global_Clatd_Config.ipv6_local_subnet);

  struct pollfd wait_fd[] = {
    { tunnel->read_fd6, POLLIN, 0 },
    { tunnel->fd4, POLLIN, 0 },
  };

  while (running) {
    if (poll(wait_fd, ARRAY_SIZE(wait_fd), -1) == -1) {
      if (errno != EINTR) {
        logmsg(ANDROID_LOG_WARN, "event_loop/poll returned an error: %s", strerror(errno));
      }
    } else {
      // Call process_packet if the socket has data to be read, but also if an
      // error is waiting. If we don't call read() after getting POLLERR, a
      // subsequent poll() will return immediately with POLLERR again,
      // causing this code to spin in a loop. Calling read() will clear the
      // socket error flag instead.
      if (wait_fd[0].revents) process_packet_6_to_4(tunnel);
      if (wait_fd[1].revents) process_packet_4_to_6(tunnel);
    }
  }
}
