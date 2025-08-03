#include <stdint.h>
#include <gtest/gtest.h>
#include <arpa/inet.h>
#include "apf_defs.h"
#include "apf_utils.h"
#include "apf_dns.h"

namespace apf {

TEST(ApfDnsTest, MatchSingleNameWithNoNameCompression) {
    const uint8_t needle_match[] = {
        0x04, '_', 'N', 'M', 'T',
        0x04, '_', 'T', 'C', 'P',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00 // needle = _NMT._TCP.LOCAL
    };
    const uint8_t udp_payload[] = {
        0x00, 0x00, 0x00, 0x00, // tid = 0x00, flags = 0x00,
        0x00, 0x01, // qdcount = 1
        0x00, 0x00, // ancount = 0
        0x00, 0x00, // nscount = 0
        0x00, 0x00, // arcount = 0
        0x04, '_', 'n', 'm', 't',
        0x04, '_', 't', 'c', 'p',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00, // qname1 = _nmt._tcp.local
        0x00, 0x0c, 0x00, 0x01  // type = PTR, class = 0x0001
    };
    u32 ofs = 12;
    EXPECT_EQ(match_single_name(needle_match, needle_match + sizeof(needle_match), udp_payload, sizeof(udp_payload), &ofs), match);
    EXPECT_EQ(ofs, 29);
    const uint8_t needle_match_star[] = {
        0x04, '_', 'N', 'M', 'T',
        0xff,
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00 // needle = _NMT.*.LOCAL
    };
    ofs = 12;
    EXPECT_EQ(match_single_name(needle_match_star, needle_match_star + sizeof(needle_match_star), udp_payload, sizeof(udp_payload), &ofs), match);
    EXPECT_EQ(ofs, 29);
    const uint8_t needle_nomatch[] = {
        0x04, '_', 'M', 'M', 'M',
        0x04, '_', 't', 'c', 'p',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00 // needle = _MMM._tcp.local
    };
    ofs = 12;
    EXPECT_EQ(match_single_name(needle_nomatch, needle_nomatch + sizeof(needle_nomatch), udp_payload, sizeof(udp_payload), &ofs), nomatch);
    EXPECT_EQ(ofs, 29);
    const uint8_t needle_nomatch_star[] = {
        0xff,
        0x04, '_', 'u', 'd', 'p',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00 // needle = *._udp.local
    };
    ofs = 12;
    EXPECT_EQ(match_single_name(needle_nomatch_star, needle_nomatch_star + sizeof(needle_nomatch_star), udp_payload, sizeof(udp_payload), &ofs), nomatch);
    EXPECT_EQ(ofs, 29);
}

TEST(ApfDnsTest, MatchSingleNameWithoutNameCompression) {
    const uint8_t needle_match[] = {
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00 // needle = B.LOCAL
    };
    const uint8_t udp_payload[] = {
        0x00, 0x00, 0x00, 0x00, // tid = 0x00, flags = 0x00,
        0x00, 0x02, // qdcount = 2
        0x00, 0x00, // ancount = 0
        0x00, 0x00, // nscount = 0
        0x00, 0x00, // arcount = 0
        0x01, 'a',
        0x01, 'b',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00, // qname1 = a.b.local
        0x00, 0x01, 0x00, 0x01,  // type = A, class = 0x0001
        0xc0, 0x0e, // qname2 = b.local (name compression)
        0x00, 0x01, 0x00, 0x01 // type = A, class = 0x0001
    };
    u32 ofs = 27;
    EXPECT_EQ(match_single_name(needle_match, needle_match + sizeof(needle_match), udp_payload, sizeof(udp_payload), &ofs), match);
    EXPECT_EQ(ofs, 29);
    const uint8_t needle_match_star[] = {
        0x01, 'B',
        0xff,
        0x00 // needle = B.*
    };
    ofs = 27;
    EXPECT_EQ(match_single_name(needle_match_star, needle_match_star + sizeof(needle_match_star), udp_payload, sizeof(udp_payload), &ofs), match);
    EXPECT_EQ(ofs, 29);
}

TEST(ApfDnsTest, MatchSingleNameWithInfiniteloop) {
    const uint8_t needle_match[] = {
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00 // needle = B.LOCAL
    };
    const uint8_t udp_payload[] = {
        0x00, 0x00, 0x00, 0x00, // tid = 0x00, flags = 0x00,
        0x00, 0x02, // qdcount = 2
        0x00, 0x00, // ancount = 0
        0x00, 0x00, // nscount = 0
        0x00, 0x00, // arcount = 0
        0x01, 'a',
        0x01, 'b',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00, // qname1 = a.b.local
        0x00, 0x01, 0x00, 0x01,  // type = A, class = 0x0001
        0xc0, 0x1b, // corrupted pointer cause infinite loop
        0x00, 0x01, 0x00, 0x01 // type = A, class = 0x0001
    };
    u32 ofs = 27;
    EXPECT_EQ(match_single_name(needle_match, needle_match + sizeof(needle_match), udp_payload, sizeof(udp_payload), &ofs), error_packet);
    const uint8_t needle_match_star[] = {
        0x01, 'B',
        0xff,
        0x00 // needle = B.*
    };
    ofs = 27;
    EXPECT_EQ(match_single_name(needle_match_star, needle_match_star + sizeof(needle_match_star), udp_payload, sizeof(udp_payload), &ofs), error_packet);
}

TEST(ApfDnsTest, MatchNamesInQuestions) {
    // needles = { A.B.LOCAL }
    const uint8_t needles_match1[] = {
        0x01, 'A',
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    const uint8_t udp_payload[] = {
        0x00, 0x00, 0x00, 0x00, // tid = 0x00, flags = 0x00,
        0x00, 0x02, // qdcount = 2
        0x00, 0x00, // ancount = 0
        0x00, 0x00, // nscount = 0
        0x00, 0x00, // arcount = 0
        0x01, 'a',
        0x01, 'b',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00, // qname1 = a.b.local
        0x00, 0x01, 0x00, 0x01,// type = A, class = 0x0001
        0xc0, 0x0e, // qname2 = b.local (name compression)
        0x00, 0x01, 0x00, 0x01 // type = A, class = 0x0001
    };
    EXPECT_EQ(match_names(needles_match1, needles_match1 + sizeof(needles_match1),  udp_payload, sizeof(udp_payload), 0x01), match);
    // needles = { A, B.LOCAL }
    const uint8_t needles_match2[] = {
        0x01, 'A',
        0x00,
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_match2, needles_match2 + sizeof(needles_match2), udp_payload, sizeof(udp_payload), 0x01), match);
    // needles = { *, B.* }
    const uint8_t needles_match2_star[] = {
        0xff,
        0x00,
        0x01, 'B',
        0xff,
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_match2_star, needles_match2_star + sizeof(needles_match2_star), udp_payload, sizeof(udp_payload), 0x01), match);
    // needles = { C.LOCAL }
    const uint8_t needles_nomatch[] = {
        0x01, 'C',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_nomatch, needles_nomatch + sizeof(needles_nomatch), udp_payload, sizeof(udp_payload), 0x01), nomatch);
    // needles = { C.* }
    const uint8_t needles_nomatch_star[] = {
        0x01, 'C',
        0xff,
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_nomatch_star, needles_nomatch_star + sizeof(needles_nomatch_star), udp_payload, sizeof(udp_payload), 0x01), nomatch);
}

TEST(ApfDnsTest, MatchNamesInAnswers) {
    // needles = { A.B.LOCAL }
    const uint8_t needles_match1[] = {
        0x01, 'A',
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    const uint8_t udp_payload[] = {
        0x00, 0x00, 0x84, 0x00, // tid = 0x00, flags = 0x8400,
        0x00, 0x00, // qdcount = 0
        0x00, 0x02, // ancount = 2
        0x00, 0x00, // nscount = 0
        0x00, 0x00, // arcount = 0
        0x01, 'a',
        0x01, 'b',
        0x05, 'l', 'o', 'c', 'a', 'l',
        0x00, // name1 = a.b.local
        0x00, 0x01, 0x80, 0x01, // type = A, class = 0x8001
        0x00, 0x00, 0x00, 0x78, // ttl = 120
        0x00, 0x04, 0xc0, 0xa8, 0x01, 0x09, // rdlengh = 4, rdata = 192.168.1.9
        0xc0, 0x0e, // name2 = b.local (name compression)
        0x00, 0x01, 0x80, 0x01, // type = A, class = 0x8001
        0x00, 0x00, 0x00, 0x78, // ttl = 120
        0x00, 0x04, 0xc0, 0xa8, 0x01, 0x09 // rdlengh = 4, rdata = 192.168.1.9
    };
    EXPECT_EQ(match_names(needles_match1, needles_match1 + sizeof(needles_match1), udp_payload, sizeof(udp_payload), -1), match);
    // needles = { A, B.LOCAL }
    const uint8_t needles_match2[] = {
        0x01, 'A', 0x00,
        0x01, 'B',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_match2, needles_match2 + sizeof(needles_match2), udp_payload, sizeof(udp_payload), -1), match);
    // needles = { *, B.* }
    const uint8_t needles_match2_star[] = {
        0xff,
        0x01, 'B',
        0xff,
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_match2_star, needles_match2_star + sizeof(needles_match2_star), udp_payload, sizeof(udp_payload), -1), match);
    // needles = { C.LOCAL }
    const uint8_t needles_nomatch[] = {
        0x01, 'C',
        0x05, 'L', 'O', 'C', 'A', 'L',
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_nomatch, needles_nomatch + sizeof(needles_nomatch), udp_payload, sizeof(udp_payload), -1), nomatch);
    // needles = { C.* }
    const uint8_t needles_nomatch_star[] = {
        0x01, 'C',
        0xff,
        0x00,
        0x00
    };
    EXPECT_EQ(match_names(needles_nomatch_star, needles_nomatch_star + sizeof(needles_nomatch_star), udp_payload, sizeof(udp_payload), -1), nomatch);
}

} // namespace apf
