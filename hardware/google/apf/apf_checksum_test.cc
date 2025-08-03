#include <cstdint>
#include <cstddef>
#include <gtest/gtest.h>
#include <linux/icmpv6.h>
#include <linux/if_ether.h>
#include <linux/in6.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/udp.h>
#include "apf_defs.h"
#include "apf_utils.h"
#include "apf_checksum.h"

namespace apf {

#define htons(x) __builtin_bswap16(x)
#define packed __attribute__((packed))



TEST(ApfChecksumTest, CalcIPv4UDPChecksum) {
    // An IPv4 UDP packet with IPv4 header checksum and UDP checksum set to 0
    union packed {
        uint8_t data[77];
        struct packed {
          struct ethhdr ethhdr;
          struct iphdr iphdr;
          struct udphdr udphdr;
          uint8_t udp_payload[];
        } pkt;
    } ether_ipv4_udp_pkt = {{
        0x01, 0x00, 0x5e, 0x00, 0x00, 0xfb,
        0x38, 0xca, 0x84, 0xb7, 0x7f, 0x16,
        0x08, 0x00, // end of ethernet header
        0x45,
        0x04,
        0x00, 0x3f,
        0x43, 0xcd,
        0x40, 0x00,
        0xff,
        0x11,
        0x00, 0x00,
        0xc0, 0xa8, 0x01, 0x03,
        0xe0, 0x00, 0x00, 0xfb, // end of ipv4 header
        0x14, 0xe9,
        0x14, 0xe9,
        0x00, 0x2b,
        0x00, 0x00, // end of udp header
        0x00, 0x00, 0x84, 0x00, 0x00, 0x00,
        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x62, 0x05, 0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x00, 0x00,
        0x01, 0x80, 0x01, 0x00, 0x00, 0x00, 0x78, 0x00, 0x04, 0xc0, 0xa8, 0x01, 0x09,
    }};
    // Reset IPv4 header checksum to 0
    ether_ipv4_udp_pkt.pkt.iphdr.check = 0;
    // Set the UDP checksum to UDP payload size
    ether_ipv4_udp_pkt.pkt.udphdr.check = htons(sizeof(ether_ipv4_udp_pkt) - IPV4_HLEN - ETH_HLEN);
    uint8_t dscp = csum_and_return_dscp((uint8_t *)&ether_ipv4_udp_pkt, sizeof(ether_ipv4_udp_pkt),
                                ETH_HLEN /* ip_ofs */, IPPROTO_UDP /* partial_csum */,
                                ETH_HLEN + offsetof(iphdr, saddr) /* csum_start */,
                                ETH_HLEN + IPV4_HLEN + offsetof(udphdr, check) /* csum_ofs */,
                                true /* udp */);
    EXPECT_EQ(dscp, 1);
    // Verify IPv4 header checksum
    EXPECT_EQ(read_be16((uint8_t *)&ether_ipv4_udp_pkt.pkt.iphdr.check), 0x9535);
    EXPECT_EQ(read_be16((uint8_t *)&ether_ipv4_udp_pkt.pkt.udphdr.check), 0xa73d);
}

TEST(ApfChecksumTest, CalcIPv6UDPChecksum) {
    // An IPv6 UDP packet with UDP checksum set to 0
    union packed {
        uint8_t data[97];
        struct packed {
          struct ethhdr ethhdr;
          struct ipv6hdr ipv6hdr;
          struct udphdr udphdr;
          uint8_t udp_payload[];
        } pkt;
    } ether_ipv6_udp_pkt = {{
        0x33, 0x33, 0x00, 0x00, 0x00, 0xfb,
        0x38, 0xca, 0x84, 0xb7, 0x7f, 0x16,
        0x86, 0xdd, // end of ethernet header
        0x61, 0x89, 0xf4, 0x6b,
        0x00, 0x2b,
        0x11,
        0xff,
        0x24, 0x0d, 0x00, 0x1a, 0x03, 0xa6, 0xc4, 0x00, 0xb7, 0x5a, 0xb4, 0x85, 0x28, 0x10, 0xad, 0x6b,
        0xff, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfb, // end of ipv6 header
        0x14, 0xe9,
        0x14, 0xe9,
        0x00, 0x2b,
        0x00, 0x00, // end of udp header
        0x00, 0x00, 0x84, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x62, 0x05, 0x6c,
        0x6f, 0x63, 0x61, 0x6c, 0x00, 0x00, 0x01, 0x80, 0x01, 0x00, 0x00, 0x00, 0x78, 0x00, 0x04, 0xc0,
        0xa8, 0x01, 0x09
    }};
    // Set the UDP checksum to UDP payload size
    ether_ipv6_udp_pkt.pkt.udphdr.check = htons(sizeof(ether_ipv6_udp_pkt) - IPV6_HLEN - ETH_HLEN);
    uint8_t dscp = csum_and_return_dscp((uint8_t *)&ether_ipv6_udp_pkt, sizeof(ether_ipv6_udp_pkt),
                                ETH_HLEN /* ip_ofs */, IPPROTO_UDP /* partial_csum */,
                                ETH_HLEN + offsetof(ipv6hdr, saddr) /* csum_start */,
                                ETH_HLEN + IPV6_HLEN + offsetof(udphdr, check) /* csum_ofs */,
                                true /* udp */);
    EXPECT_EQ(dscp, 6);
    // verify UDP checksum
    EXPECT_EQ(read_be16((uint8_t *)&ether_ipv6_udp_pkt.pkt.udphdr.check), 0x1cbd);
}

TEST(ApfChecksumTest, CalcICMPv6Checksum) {
    // An ICMPv6 packet with checksum field set to 0
    union packed {
        uint8_t data[78];
        struct packed {
          struct ethhdr ethhdr;
          struct ipv6hdr ipv6hdr;
          struct icmp6hdr icmp6hdr;
          uint8_t icmpv6_payload[];
        } pkt;
    } ether_ipv6_icmp6_pkt = {{
        0xcc, 0x1a, 0xfa, 0xc7, 0xd2, 0xd8,
        0xbc, 0xd0, 0x74, 0x58, 0xf1, 0x4f,
        0x86, 0xdd, // end of ethernet header
        0x61, 0x80, 0x00, 0x00,
        0x00, 0x18,
        0x3a,
        0xff,
        0xfe, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x12, 0x11, 0x2c, 0xdc, 0x04, 0x35, 0x11,
        0xfe, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // end of ipv6 header
        0x88,
        0x00,
        0x00, 0x00, // end of icmpv6 header
        0x40, 0x00, 0x00, 0x00, 0x24, 0x0d, 0x00, 0x1a, 0x03, 0xa6, 0xc4, 0x00, 0xfd, 0x3d, 0x12, 0xb7,
        0x90, 0xb6, 0xe9, 0xd2
    }};
    // Set the ICMPv6 checksum to ICMPv6 payload size
    ether_ipv6_icmp6_pkt.pkt.icmp6hdr.icmp6_cksum = htons(sizeof(ether_ipv6_icmp6_pkt) - IPV6_HLEN - ETH_HLEN);
    uint8_t dscp = csum_and_return_dscp((uint8_t *)&ether_ipv6_icmp6_pkt, sizeof(ether_ipv6_icmp6_pkt),
                                ETH_HLEN /* ip_ofs */, IPPROTO_ICMPV6 /* partial_csum */,
                                ETH_HLEN + offsetof(ipv6hdr, saddr) /* csum_start */,
                                ETH_HLEN + IPV6_HLEN + offsetof(icmp6hdr, icmp6_cksum) /* csum_ofs */,
                                false /* udp */);
    EXPECT_EQ(dscp, 6);
    // verify layer 4 checksum
    EXPECT_EQ(read_be16((uint8_t *)&ether_ipv6_icmp6_pkt.pkt.icmp6hdr.icmp6_cksum), 0x8a09);
}

TEST(ApfChecksumTest, CalcICMPv6ChecksumWithHopByHopOption) {
    // An ICMPv6 packet(including hop-by-hop option) with checksum field set to 0
    union packed {
        uint8_t data[90];
        struct packed {
          struct ethhdr ethhdr;
          struct ipv6hdr ipv6hdr;
          uint8_t hopopts[8];
          struct icmp6hdr icmp6hdr;
          uint8_t icmpv6_payload[];
        } pkt;
    } ether_ipv6_hopopts_icmp6_pkt = {{
        0x33, 0x33, 0x00, 0x00, 0x00, 0x16,
        0xe0, 0x4f, 0x43, 0xe6, 0xfb, 0xcf,
        0x86, 0xdd, // end of ethernet header
        0x60, 0x00, 0x00, 0x00,
        0x00, 0x24,
        0x00,
        0x01,
        0xfe, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x71, 0x6b, 0xe2, 0xfe, 0xd6, 0x53, 0x4e, 0xe0,
        0xff, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x16, // end of ipv6 header
        0x3a, 0x00, 0x05, 0x02, 0x00, 0x00, 0x01, 0x00, // end of hop-by-hop option
        0x8f,
        0x00,
        0x00, 0x00, // end of icmpv6 header
        0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x00, 0x00, 0xff, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0c
    }};

    // Set the ICMPv6 checksum to ICMPv6 (header + payload) size + ~{16-bit sum of hop-by-hop header}
    ether_ipv6_hopopts_icmp6_pkt.pkt.icmp6hdr.icmp6_cksum =
        htons(sizeof(ether_ipv6_hopopts_icmp6_pkt) - IPV6_HLEN - ETH_HLEN
              - sizeof(ether_ipv6_hopopts_icmp6_pkt.pkt.hopopts)
              + 0xbffd); // 0xffff - (0x3a00 + 0x0502 + 0x0000 + 0x0100) = 0xbffd
    uint8_t dscp = csum_and_return_dscp((uint8_t *)&ether_ipv6_hopopts_icmp6_pkt,
                                        sizeof(ether_ipv6_hopopts_icmp6_pkt),
                                        ETH_HLEN /* ip_ofs */, IPPROTO_ICMPV6 /* partial_csum */,
                                        ETH_HLEN + offsetof(ipv6hdr, saddr) /* csum_start */,
                                        ETH_HLEN + IPV6_HLEN
                                        + sizeof(ether_ipv6_hopopts_icmp6_pkt.pkt.hopopts)
                                        + offsetof(icmp6hdr, icmp6_cksum) /* csum_ofs */,
                                        false /* udp */);
    EXPECT_EQ(dscp, 0);
    // verify layer 4 checksum
    EXPECT_EQ(read_be16((uint8_t *)&ether_ipv6_hopopts_icmp6_pkt.pkt.icmp6hdr.icmp6_cksum), 0xf760);
}

}  // namespace apf
